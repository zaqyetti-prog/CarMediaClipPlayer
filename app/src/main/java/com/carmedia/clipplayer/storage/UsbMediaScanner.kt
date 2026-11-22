package com.carmedia.clipplayer.storage

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.carmedia.clipplayer.model.FolderItem
import com.carmedia.clipplayer.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class UsbMediaScanner(private val context: Context) {

    private val videoExtensions = setOf(
        "mp4", "mkv", "avi", "mov", "webm", "ts", "flv", "3gp", "wmv", "m4v"
    )

    suspend fun scanMedia(): List<FolderItem> = withContext(Dispatchers.IO) {
        val folderMap = mutableMapOf<String, FolderItem>()

        // 1. Scan via Android MediaStore (Very fast for indexed USBs & local storage)
        scanMediaStore(folderMap)

        // 2. Direct filesystem scan for car multimedia USB mount points (often unindexed by MediaStore on car units)
        scanCarUsbDirectories(folderMap)

        // Sort folders alphabetically and sort videos in each folder
        val result = folderMap.values.filter { it.videos.isNotEmpty() }.sortedBy { it.name.lowercase(Locale.ROOT) }
        result.forEach { folder ->
            folder.videos.sortBy { it.title.lowercase(Locale.ROOT) }
        }
        result
    }

    private fun scanMediaStore(folderMap: MutableMap<String, FolderItem>) {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION
        )

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DISPLAY_NAME} ASC"
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dataCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val durCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol).toString()
                    val title = it.getString(nameCol) ?: "Video"
                    val path = it.getString(dataCol) ?: continue
                    val duration = it.getLong(durCol)
                    val uri = Uri.fromFile(File(path))

                    val file = File(path)
                    if (file.exists() && isVideoFile(file)) {
                        val parentFolder = file.parentFile ?: continue
                        val folderPath = parentFolder.absolutePath
                        val folderName = parentFolder.name

                        val folder = folderMap.getOrPut(folderPath) {
                            FolderItem(name = folderName, path = folderPath)
                        }

                        if (folder.videos.none { v -> v.path == path }) {
                            folder.videos.add(
                                VideoItem(
                                    id = id,
                                    title = title,
                                    path = path,
                                    uri = uri,
                                    durationMs = duration,
                                    folderPath = folderPath,
                                    folderName = folderName
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun scanCarUsbDirectories(folderMap: MutableMap<String, FolderItem>) {
        val potentialRoots = mutableListOf<File>()

        // Standard Android external files dirs (includes USB drives on Android 7/8+)
        ContextCompat.getExternalFilesDirs(context, null).forEach { dir ->
            dir?.let {
                // Traverse up to find root of the mounted storage volume
                var root: File? = it
                while (root != null && root.parentFile != null && root.parentFile?.absolutePath != "/storage") {
                    root = root.parentFile
                }
                root?.let { r -> potentialRoots.add(r) }
            }
        }

        // Common Car Head Unit USB and storage paths
        val knownPaths = listOf(
            "/storage",
            "/mnt/media_rw",
            "/mnt/usb_storage",
            "/mnt/usb_storage/USB_DISK0",
            "/mnt/usb_storage/USB_DISK1",
            "/storage/usbotg",
            "/storage/udisk0",
            "/storage/udisk1",
            Environment.getExternalStorageDirectory().absolutePath
        )

        for (path in knownPaths) {
            val f = File(path)
            if (f.exists() && f.canRead()) {
                potentialRoots.add(f)
            }
        }

        // Recursively search up to 4 directory levels deep
        for (root in potentialRoots.distinctBy { it.absolutePath }) {
            scanDirectoryRecursively(root, folderMap, 0, 4)
        }
    }

    private fun scanDirectoryRecursively(
        dir: File,
        folderMap: MutableMap<String, FolderItem>,
        currentDepth: Int,
        maxDepth: Int
    ) {
        if (currentDepth > maxDepth || !dir.exists() || !dir.canRead()) return

        val files = dir.listFiles() ?: return
        val currentFolderVideos = mutableListOf<VideoItem>()

        for (file in files) {
            if (file.isDirectory && !file.name.startsWith(".")) {
                scanDirectoryRecursively(file, folderMap, currentDepth + 1, maxDepth)
            } else if (file.isFile && isVideoFile(file)) {
                val path = file.absolutePath
                val title = file.name
                val uri = Uri.fromFile(file)
                val folderPath = dir.absolutePath
                val folderName = dir.name

                currentFolderVideos.add(
                    VideoItem(
                        id = path.hashCode().toString(),
                        title = title,
                        path = path,
                        uri = uri,
                        durationMs = 0L,
                        folderPath = folderPath,
                        folderName = folderName
                    )
                )
            }
        }

        if (currentFolderVideos.isNotEmpty()) {
            val folder = folderMap.getOrPut(dir.absolutePath) {
                FolderItem(name = dir.name, path = dir.absolutePath)
            }
            for (vid in currentFolderVideos) {
                if (folder.videos.none { it.path == vid.path }) {
                    folder.videos.add(vid)
                }
            }
        }
    }

    private fun isVideoFile(file: File): Boolean {
        val ext = file.extension.lowercase(Locale.ROOT)
        return videoExtensions.contains(ext)
    }
}
