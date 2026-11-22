package com.carmedia.clipplayer.player

import android.content.Context
import android.content.SharedPreferences

class PlaybackStateManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("car_player_state", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LAST_PATH = "last_video_path"
        private const val KEY_LAST_URI = "last_video_uri"
        private const val KEY_LAST_POSITION_MS = "last_position_ms"
        private const val KEY_LAST_FOLDER = "last_folder_path"
        private const val KEY_LAST_TITLE = "last_video_title"
    }

    fun savePlaybackState(path: String?, uriString: String?, positionMs: Long, folderPath: String?, title: String?) {
        prefs.edit()
            .putString(KEY_LAST_PATH, path)
            .putString(KEY_LAST_URI, uriString)
            .putLong(KEY_LAST_POSITION_MS, positionMs)
            .putString(KEY_LAST_FOLDER, folderPath)
            .putString(KEY_LAST_TITLE, title)
            .apply()
    }

    fun updatePosition(positionMs: Long) {
        prefs.edit().putLong(KEY_LAST_POSITION_MS, positionMs).apply()
    }

    val lastVideoPath: String?
        get() = prefs.getString(KEY_LAST_PATH, null)

    val lastVideoUri: String?
        get() = prefs.getString(KEY_LAST_URI, null)

    val lastPositionMs: Long
        get() = prefs.getLong(KEY_LAST_POSITION_MS, 0L)

    val lastFolderPath: String?
        get() = prefs.getString(KEY_LAST_FOLDER, null)

    val lastVideoTitle: String?
        get() = prefs.getString(KEY_LAST_TITLE, null)
}
