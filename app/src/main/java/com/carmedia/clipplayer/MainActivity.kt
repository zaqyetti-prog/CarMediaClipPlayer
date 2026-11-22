package com.carmedia.clipplayer

import android.Manifest
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.carmedia.clipplayer.audio.CarEqualizerManager
import com.carmedia.clipplayer.databinding.ActivityMainBinding
import com.carmedia.clipplayer.model.FolderItem
import com.carmedia.clipplayer.model.VideoItem
import com.carmedia.clipplayer.player.PlaybackStateManager
import com.carmedia.clipplayer.storage.UsbMediaScanner
import com.carmedia.clipplayer.ui.EqBandAdapter
import com.carmedia.clipplayer.ui.FolderAdapter
import com.carmedia.clipplayer.ui.VideoAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var exoPlayer: ExoPlayer? = null
    private lateinit var playbackStateManager: PlaybackStateManager
    private lateinit var equalizerManager: CarEqualizerManager
    private lateinit var usbScanner: UsbMediaScanner

    private var allFolders: List<FolderItem> = emptyList()
    private var currentPlaylist: List<VideoItem> = emptyList()
    private var currentVideoIndex: Int = -1
    private var currentVideo: VideoItem? = null

    private var progressJob: Job? = null
    private val overlayHideHandler = Handler(Looper.getMainLooper())
    private val hideOverlayRunnable = Runnable { hideControlsOverlay() }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions.values.all { it }
            if (granted) {
                initStorageAndPlayback()
            } else {
                Toast.makeText(this, "Permissão de armazenamento necessária para ler o Pen Drive", Toast.LENGTH_LONG).show()
                initStorageAndPlayback()
            }
        }

    // USB Mount broadcast receiver
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (Intent.ACTION_MEDIA_MOUNTED == action || Intent.ACTION_MEDIA_UNMOUNTED == action) {
                rescanUsbStorage()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableImmersiveFullscreen()

        playbackStateManager = PlaybackStateManager(this)
        equalizerManager = CarEqualizerManager(this)
        usbScanner = UsbMediaScanner(this)

        initPlayer()
        setupListeners()
        checkPermissionsAndStart()

        // Register USB plug/unplug intent filter
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addDataScheme("file")
        }
        registerReceiver(usbReceiver, filter)
    }

    private fun enableImmersiveFullscreen() {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun initPlayer() {
        val player = ExoPlayer.Builder(this).build()
        exoPlayer = player
        binding.playerView.player = player

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        updatePlayPauseIcon(player.isPlaying)
                        updateTotalDuration(player.duration)
                        equalizerManager.attachAudioSession(player.audioSessionId)
                    }
                    Player.STATE_ENDED -> {
                        playNextVideo()
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayPauseIcon(isPlaying)
                if (isPlaying) {
                    startPeriodicProgressTracking()
                    scheduleHideOverlay()
                } else {
                    cancelHideOverlay()
                }
            }
        })
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            initStorageAndPlayback()
        } else {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun initStorageAndPlayback() {
        lifecycleScope.launch {
            binding.txtCurrentFolderPath.text = getString(R.string.scanning_usb)
            allFolders = usbScanner.scanMedia()

            val lastPath = playbackStateManager.lastVideoPath
            val lastPos = playbackStateManager.lastPositionMs

            if (!lastPath.isNullOrEmpty() && File(lastPath).exists()) {
                // Instantly resume saved video and saved position!
                showResumeBanner("Retomando vídeo anterior de onde parou...")
                val video = findVideoByPath(lastPath) ?: VideoItem(
                    id = lastPath.hashCode().toString(),
                    title = playbackStateManager.lastVideoTitle ?: File(lastPath).name,
                    path = lastPath,
                    uri = Uri.fromFile(File(lastPath)),
                    durationMs = 0L,
                    folderPath = playbackStateManager.lastFolderPath ?: "",
                    folderName = File(lastPath).parentFile?.name ?: "Pen Drive"
                )
                setupCurrentPlaylistFor(video)
                playVideo(video, lastPos)
            } else if (allFolders.isNotEmpty() && allFolders[0].videos.isNotEmpty()) {
                // Auto-play first video available on pen drive
                val firstFolder = allFolders[0]
                currentPlaylist = firstFolder.videos
                currentVideoIndex = 0
                val firstVideo = firstFolder.videos[0]
                showResumeBanner("Iniciando reprodução do Pen Drive...")
                playVideo(firstVideo, 0L)
            } else {
                binding.txtCurrentClipTitle.text = getString(R.string.app_name)
                binding.txtCurrentFolderPath.text = getString(R.string.no_usb_found)
                showFolderBrowserDialog()
            }
        }
    }

    private fun rescanUsbStorage() {
        lifecycleScope.launch {
            allFolders = usbScanner.scanMedia()
            Toast.makeText(this@MainActivity, "Pen Drive atualizado (${allFolders.size} pastas)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupCurrentPlaylistFor(video: VideoItem) {
        val folder = allFolders.find { it.path == video.folderPath }
        if (folder != null) {
            currentPlaylist = folder.videos
            currentVideoIndex = currentPlaylist.indexOfFirst { it.path == video.path }
        } else {
            currentPlaylist = listOf(video)
            currentVideoIndex = 0
        }
    }

    private fun findVideoByPath(path: String): VideoItem? {
        for (folder in allFolders) {
            val found = folder.videos.find { it.path == path }
            if (found != null) return found
        }
        return null
    }

    private fun playVideo(video: VideoItem, startPositionMs: Long = 0L) {
        currentVideo = video
        binding.txtCurrentClipTitle.text = video.title
        binding.txtCurrentFolderPath.text = "PASTA: ${video.folderName.uppercase()}"

        val player = exoPlayer ?: return
        val mediaItem = MediaItem.fromUri(video.uri)
        player.setMediaItem(mediaItem)
        player.prepare()

        if (startPositionMs > 0) {
            player.seekTo(startPositionMs)
        }

        player.playWhenReady = true

        playbackStateManager.savePlaybackState(
            path = video.path,
            uriString = video.uri.toString(),
            positionMs = startPositionMs,
            folderPath = video.folderPath,
            title = video.title
        )
    }

    private fun startPeriodicProgressTracking() {
        progressJob?.cancel()
        progressJob = lifecycleScope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    if (player.isPlaying) {
                        val currentPos = player.currentPosition
                        val total = player.duration
                        if (total > 0) {
                            val progress = ((currentPos * 1000) / total).toInt()
                            binding.seekBarProgress.progress = progress
                        }
                        binding.txtCurrentTime.text = formatTime(currentPos)
                        playbackStateManager.updatePosition(currentPos)
                    }
                }
                delay(1000)
            }
        }
    }

    private fun formatTime(millis: Long): String {
        val m = TimeUnit.MILLISECONDS.toMinutes(millis)
        val s = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(m)
        return String.format("%02d:%02d", m, s)
    }

    private fun updateTotalDuration(millis: Long) {
        if (millis > 0) {
            binding.txtTotalTime.text = formatTime(millis)
        }
    }

    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        binding.btnPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    private fun playNextVideo() {
        if (currentPlaylist.isNotEmpty()) {
            val nextIndex = (currentVideoIndex + 1) % currentPlaylist.size
            currentVideoIndex = nextIndex
            playVideo(currentPlaylist[nextIndex], 0L)
        }
    }

    private fun playPreviousVideo() {
        if (currentPlaylist.isNotEmpty()) {
            val prevIndex = if (currentVideoIndex - 1 < 0) currentPlaylist.size - 1 else currentVideoIndex - 1
            currentVideoIndex = prevIndex
            playVideo(currentPlaylist[prevIndex], 0L)
        }
    }

    private fun setupListeners() {
        // Tap screen to toggle overlay
        binding.rootView.setOnClickListener {
            toggleControlsOverlay()
        }

        binding.btnPlayPause.setOnClickListener {
            exoPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                } else {
                    player.play()
                }
            }
            scheduleHideOverlay()
        }

        binding.btnNext.setOnClickListener {
            playNextVideo()
            scheduleHideOverlay()
        }

        binding.btnPrev.setOnClickListener {
            playPreviousVideo()
            scheduleHideOverlay()
        }

        binding.btnOpenFolders.setOnClickListener {
            showFolderBrowserDialog()
        }

        binding.btnOpenEqualizer.setOnClickListener {
            showEqualizerDialog()
        }

        binding.btnRescanUsb.setOnClickListener {
            rescanUsbStorage()
        }

        binding.seekBarProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    exoPlayer?.let { player ->
                        val duration = player.duration
                        if (duration > 0) {
                            val newPosition = (duration * progress) / 1000
                            player.seekTo(newPosition)
                            binding.txtCurrentTime.text = formatTime(newPosition)
                        }
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                cancelHideOverlay()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                scheduleHideOverlay()
            }
        })
    }

    private fun toggleControlsOverlay() {
        if (binding.controlsOverlay.visibility == View.VISIBLE) {
            hideControlsOverlay()
        } else {
            showControlsOverlay()
        }
    }

    private fun showControlsOverlay() {
        binding.controlsOverlay.visibility = View.VISIBLE
        scheduleHideOverlay()
    }

    private fun hideControlsOverlay() {
        binding.controlsOverlay.visibility = View.GONE
    }

    private fun scheduleHideOverlay() {
        cancelHideOverlay()
        overlayHideHandler.postDelayed(hideOverlayRunnable, 4000)
    }

    private fun cancelHideOverlay() {
        overlayHideHandler.removeCallbacks(hideOverlayRunnable)
    }

    private fun showResumeBanner(message: String) {
        binding.txtToastBanner.text = message
        binding.toastBanner.visibility = View.VISIBLE
        Handler(Looper.getMainLooper()).postDelayed({
            binding.toastBanner.visibility = View.GONE
        }, 3000)
    }

    // --- BIG FOLDER BROWSER DIALOG FOR CAR TOUCHSCREENS ---
    private fun showFolderBrowserDialog() {
        val dialog = Dialog(this, R.style.DialogGlassTheme)
        dialog.setContentView(R.layout.dialog_folder_browser)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.94).toInt(),
            (resources.displayMetrics.heightPixels * 0.90).toInt()
        )
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val txtTitle = dialog.findViewById<TextView>(R.id.txtDialogTitle)
        val txtItemCount = dialog.findViewById<TextView>(R.id.txtItemCount)
        val btnClose = dialog.findViewById<ImageButton>(R.id.btnCloseBrowser)
        val btnBack = dialog.findViewById<ImageButton>(R.id.btnBackFolder)
        val rvFolders = dialog.findViewById<RecyclerView>(R.id.rvFolders)
        val rvVideos = dialog.findViewById<RecyclerView>(R.id.rvVideos)
        val txtEmpty = dialog.findViewById<TextView>(R.id.txtEmptyBrowser)

        rvFolders.layoutManager = GridLayoutManager(this, 2)
        rvVideos.layoutManager = LinearLayoutManager(this)

        if (allFolders.isEmpty()) {
            txtEmpty.visibility = View.VISIBLE
            rvFolders.visibility = View.GONE
        } else {
            txtEmpty.visibility = View.GONE
            rvFolders.visibility = View.VISIBLE
            txtItemCount.text = "${allFolders.size} Pastas"
        }

        val videoAdapter = VideoAdapter(emptyList(), currentVideo?.path) { selectedVideo, index ->
            currentVideoIndex = index
            playVideo(selectedVideo, 0L)
            dialog.dismiss()
        }
        rvVideos.adapter = videoAdapter

        val folderAdapter = FolderAdapter(allFolders) { clickedFolder ->
            // Open clicked folder to show its clips
            txtTitle.text = clickedFolder.name.uppercase()
            txtItemCount.text = "${clickedFolder.videoCount} Clipes"
            btnBack.visibility = View.VISIBLE
            rvFolders.visibility = View.GONE
            rvVideos.visibility = View.VISIBLE

            currentPlaylist = clickedFolder.videos
            videoAdapter.updateData(clickedFolder.videos, currentVideo?.path)
        }
        rvFolders.adapter = folderAdapter

        btnBack.setOnClickListener {
            txtTitle.text = getString(R.string.title_folders)
            txtItemCount.text = "${allFolders.size} Pastas"
            btnBack.visibility = View.GONE
            rvVideos.visibility = View.GONE
            rvFolders.visibility = View.VISIBLE
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // --- ADVANCED CAR EQUALIZER DIALOG ---
    private fun showEqualizerDialog() {
        val dialog = Dialog(this, R.style.DialogGlassTheme)
        dialog.setContentView(R.layout.dialog_equalizer)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.94).toInt(),
            (resources.displayMetrics.heightPixels * 0.92).toInt()
        )
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val switchEqEnabled = dialog.findViewById<SwitchCompat>(R.id.switchEqEnabled)
        val switchLowCut = dialog.findViewById<SwitchCompat>(R.id.switchLowCut)
        val switchHighCut = dialog.findViewById<SwitchCompat>(R.id.switchHighCut)
        val seekBassBoost = dialog.findViewById<SeekBar>(R.id.seekBassBoost)
        val txtBassBoost = dialog.findViewById<TextView>(R.id.txtBassBoostValue)
        val seekLoudness = dialog.findViewById<SeekBar>(R.id.seekLoudness)
        val txtLoudness = dialog.findViewById<TextView>(R.id.txtLoudnessValue)
        val btnClose = dialog.findViewById<ImageButton>(R.id.btnCloseEq)
        val rvBands = dialog.findViewById<RecyclerView>(R.id.rvEqBands)

        // Presets
        val btnFlat = dialog.findViewById<Button>(R.id.btnPresetFlat)
        val btnBass = dialog.findViewById<Button>(R.id.btnPresetBass)
        val btnVocal = dialog.findViewById<Button>(R.id.btnPresetVocal)
        val btnRock = dialog.findViewById<Button>(R.id.btnPresetRock)

        // Setup values
        switchEqEnabled.isChecked = equalizerManager.isEnabled
        switchLowCut.isChecked = equalizerManager.isLowCutEnabled
        switchHighCut.isChecked = equalizerManager.isHighCutEnabled

        val bassVal = equalizerManager.bassBoostStrength.toInt()
        seekBassBoost.progress = bassVal
        txtBassBoost.text = "${bassVal / 10}%"

        val loudVal = equalizerManager.loudnessGainMb
        seekLoudness.progress = loudVal
        txtLoudness.text = "+${loudVal / 100} dB"

        // EQ Bands RecyclerView
        val bands = equalizerManager.getBands()
        rvBands.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        val bandAdapter = EqBandAdapter(bands) { bandIndex, gainMb ->
            equalizerManager.setBandGain(bandIndex, gainMb)
        }
        rvBands.adapter = bandAdapter

        // Listeners
        switchEqEnabled.setOnCheckedChangeListener { _, isChecked ->
            equalizerManager.isEnabled = isChecked
        }

        switchLowCut.setOnCheckedChangeListener { _, isChecked ->
            equalizerManager.isLowCutEnabled = isChecked
            bandAdapter.updateBands(equalizerManager.getBands())
        }

        switchHighCut.setOnCheckedChangeListener { _, isChecked ->
            equalizerManager.isHighCutEnabled = isChecked
            bandAdapter.updateBands(equalizerManager.getBands())
        }

        seekBassBoost.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    equalizerManager.bassBoostStrength = progress.toShort()
                    txtBassBoost.text = "${progress / 10}%"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekLoudness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    equalizerManager.loudnessGainMb = progress
                    txtLoudness.text = "+${progress / 100} dB"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        fun refreshEqUi() {
            bandAdapter.updateBands(equalizerManager.getBands())
            seekBassBoost.progress = equalizerManager.bassBoostStrength.toInt()
            txtBassBoost.text = "${equalizerManager.bassBoostStrength / 10}%"
        }

        btnFlat.setOnClickListener {
            equalizerManager.applyPreset("FLAT")
            refreshEqUi()
        }
        btnBass.setOnClickListener {
            equalizerManager.applyPreset("CAR_BASS")
            refreshEqUi()
        }
        btnVocal.setOnClickListener {
            equalizerManager.applyPreset("VOCAL")
            refreshEqUi()
        }
        btnRock.setOnClickListener {
            equalizerManager.applyPreset("ROCK")
            refreshEqUi()
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                playbackStateManager.updatePosition(player.currentPosition)
                player.pause()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        progressJob?.cancel()
        cancelHideOverlay()
        try {
            unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        equalizerManager.release()
        exoPlayer?.release()
        exoPlayer = null
    }
}
