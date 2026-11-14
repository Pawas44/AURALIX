package com.videoguard.app.service

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.videoguard.app.data.VideoRepository
import com.videoguard.app.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object PlaybackController {

    private val _currentTrack = MutableStateFlow<VideoItem?>(null)
    val currentTrack: StateFlow<VideoItem?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBackgroundPlayEnabled = MutableStateFlow(true)
    val isBackgroundPlayEnabled: StateFlow<Boolean> = _isBackgroundPlayEnabled.asStateFlow()

    private val _playbackQueue = MutableStateFlow<List<VideoItem>>(emptyList())
    val playbackQueue: StateFlow<List<VideoItem>> = _playbackQueue.asStateFlow()

    private val _currentTimeSeconds = MutableStateFlow(0f)
    val currentTimeSeconds: StateFlow<Float> = _currentTimeSeconds.asStateFlow()

    private val _durationSeconds = MutableStateFlow(0f)
    val durationSeconds: StateFlow<Float> = _durationSeconds.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _playbackQuality = MutableStateFlow("Auto (1080p)")
    val playbackQuality: StateFlow<String> = _playbackQuality.asStateFlow()

    private val _isLoopEnabled = MutableStateFlow(false)
    val isLoopEnabled: StateFlow<Boolean> = _isLoopEnabled.asStateFlow()

    private val _isAutoplayEnabled = MutableStateFlow(true)
    val isAutoplayEnabled: StateFlow<Boolean> = _isAutoplayEnabled.asStateFlow()

    private val _isCaptionsEnabled = MutableStateFlow(false)
    val isCaptionsEnabled: StateFlow<Boolean> = _isCaptionsEnabled.asStateFlow()

    private val _sleepTimerMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerMinutes: StateFlow<Int?> = _sleepTimerMinutes.asStateFlow()

    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private var lastTrackChangeTimeMs: Long = 0L
    private var lastServiceSyncTimeMs: Long = 0L
    private var lastServiceSyncedPositionSec: Float = -1f
    private var lastServiceSyncedDurationSec: Float = -1f

    // Offline native playback support
    private var offlineTickerJob: kotlinx.coroutines.Job? = null
    private var offlineMediaPlayer: android.media.MediaPlayer? = null
    var isOfflineModeActive: Boolean = false
        private set

    // Listener to dispatch JavaScript commands directly to the active YouTube WebView
    private var webViewCommandListener: ((String) -> Unit)? = null

    fun setCommandListener(listener: ((String) -> Unit)?) {
        webViewCommandListener = listener
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        sendCommand("setPlaybackRate($speed)")
        syncServiceProgress(force = true)
    }

    fun setPlaybackQuality(quality: String) {
        _playbackQuality.value = quality
        val ytQuality = when {
            quality.contains("1080") -> "hd1080"
            quality.contains("720") -> "hd720"
            quality.contains("480") -> "large"
            quality.contains("360") -> "medium"
            else -> "default"
        }
        sendCommand("setPlaybackQuality('$ytQuality')")
    }

    fun toggleLoop() {
        _isLoopEnabled.value = !_isLoopEnabled.value
    }

    fun toggleAutoplay() {
        _isAutoplayEnabled.value = !_isAutoplayEnabled.value
    }

    fun setAutoplay(enabled: Boolean) {
        _isAutoplayEnabled.value = enabled
    }

    fun toggleCaptions() {
        val newVal = !_isCaptionsEnabled.value
        _isCaptionsEnabled.value = newVal
        sendCommand("setCaptions($newVal)")
    }

    fun setCaptions(enabled: Boolean) {
        _isCaptionsEnabled.value = enabled
        sendCommand("setCaptions($enabled)")
    }

    fun setSleepTimer(minutes: Int?) {
        _sleepTimerMinutes.value = minutes
        sleepTimerJob?.cancel()
        if (minutes != null && minutes > 0) {
            sleepTimerJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                kotlinx.coroutines.delay(minutes * 60 * 1000L)
                pause()
                _sleepTimerMinutes.value = null
            }
        }
    }

    private var appContext: Context? = null
    private var isNoisyReceiverRegistered = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    pause()
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    pause()
                }
                AudioManager.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", -1)
                    if (state == 0 && _isPlaying.value) { // 0 means unplugged
                        pause()
                    }
                }
            }
        }
    }

    fun init(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        com.videoguard.app.data.UserDataRepository.init(context)
        com.videoguard.app.data.AccountRepository.init(context)
        com.videoguard.app.data.SpotifyOAuthEngine.init(context)
        registerNoisyReceiver()
    }

    private fun registerNoisyReceiver() {
        val ctx = appContext ?: return
        if (isNoisyReceiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(AudioManager.ACTION_HEADSET_PLUG)
            }
            ContextCompat.registerReceiver(
                ctx,
                noisyReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            isNoisyReceiverRegistered = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playTrack(track: VideoItem, queue: List<VideoItem> = emptyList()) {
        lastTrackChangeTimeMs = System.currentTimeMillis()
        _currentTrack.value = track
        _isPlaying.value = true
        val initialDurationSec = parseDurationSeconds(track.duration)
        _currentTimeSeconds.value = 0f
        _durationSeconds.value = initialDurationSec
        lastServiceSyncTimeMs = System.currentTimeMillis()
        lastServiceSyncedPositionSec = 0f
        lastServiceSyncedDurationSec = initialDurationSec

        // Automatically record to User History
        com.videoguard.app.data.UserDataRepository.addToHistory(track)

        if (queue.isNotEmpty()) {
            _playbackQueue.value = queue
        } else if (_playbackQueue.value.none { it.id == track.id }) {
            _playbackQueue.value = listOf(track)
        }

        val isDownloaded = com.videoguard.app.data.UserDataRepository.isDownloaded(track.id)
        if (!isDownloaded && _playbackQueue.value.size <= 2) {
            checkAndFetchSimilarTracks(track)
        }

        startOrUpdateService()

        if (isDownloaded) {
            startOfflinePlayback(track, initialDurationSec)
        } else {
            stopOfflinePlayback()
            sendCommand("loadVideo('${track.id}')")
            sendCommand("setPlaybackState(true)")
        }
    }

    private fun startOfflinePlayback(track: VideoItem, initialDuration: Float) {
        stopOfflinePlayback()
        isOfflineModeActive = true
        val dur = if (initialDuration > 0f) initialDuration else 180f
        _durationSeconds.value = dur
        val ctx = appContext
        if (ctx != null) {
            try {
                val f = java.io.File(ctx.filesDir, "downloads/${track.id}.m4a")
                if (f.exists() && f.length() > 500) {
                    offlineMediaPlayer = android.media.MediaPlayer().apply {
                        setDataSource(f.absolutePath)
                        isLooping = _isLoopEnabled.value
                        setOnCompletionListener {
                            if (_isLoopEnabled.value) {
                                this@PlaybackController.seekTo(0f)
                                try { start() } catch (e: Exception) {}
                            } else if (_isAutoplayEnabled.value) {
                                nextTrack()
                            } else {
                                pause()
                            }
                        }
                        prepare()
                        start()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        resumeOfflineTicker()
    }

    private fun resumeOfflineTicker() {
        offlineTickerJob?.cancel()
        offlineTickerJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            while (isActive && _isPlaying.value) {
                kotlinx.coroutines.delay(500)
                val mp = offlineMediaPlayer
                if (mp != null && try { mp.isPlaying } catch (e: Exception) { false }) {
                    val sec = mp.currentPosition / 1000f
                    _currentTimeSeconds.value = sec
                    val d = mp.duration / 1000f
                    if (d > 0f) _durationSeconds.value = d
                } else {
                    val cur = _currentTimeSeconds.value
                    val maxDur = _durationSeconds.value
                    val next = (cur + 0.5f).coerceAtMost(maxDur)
                    _currentTimeSeconds.value = next
                    if (next >= maxDur && maxDur > 0f) {
                        if (_isLoopEnabled.value) {
                            seekTo(0f)
                        } else if (_isAutoplayEnabled.value) {
                            nextTrack()
                        } else {
                            pause()
                        }
                        break
                    }
                }
                syncServiceProgress(force = false)
            }
        }
    }

    fun stopOfflinePlayback() {
        isOfflineModeActive = false
        offlineTickerJob?.cancel()
        offlineTickerJob = null
        try {
            offlineMediaPlayer?.stop()
            offlineMediaPlayer?.release()
        } catch (e: Exception) {}
        offlineMediaPlayer = null
    }

    fun isSongFinished(): Boolean {
        val cur = _currentTimeSeconds.value
        val dur = _durationSeconds.value
        val elapsed = System.currentTimeMillis() - lastTrackChangeTimeMs
        if (dur > 10f) {
            return cur >= (dur - 4f) || (elapsed > 15000L && cur >= (dur - 6f))
        }
        if (dur > 2f) {
            return cur >= (dur - 1f)
        }
        return false
    }

    fun setQueue(queue: List<VideoItem>) {
        if (queue.isNotEmpty()) {
            _playbackQueue.value = queue
        }
    }

    fun setPlayingState(playing: Boolean) {
        if (!playing && System.currentTimeMillis() - lastTrackChangeTimeMs < 2500L) {
            // Ignore transient pause events reported by the player while a newly loaded track is initializing
            return
        }
        if (_isPlaying.value != playing) {
            _isPlaying.value = playing
            if (_currentTrack.value != null) {
                startOrUpdateService()
            }
        }
    }

    fun play() {
        _isPlaying.value = true
        if (isOfflineModeActive) {
            try { offlineMediaPlayer?.start() } catch (e: Exception) {}
            resumeOfflineTicker()
        } else {
            sendCommand("setPlaybackState(true)")
        }
        startOrUpdateService()
    }

    fun pause() {
        lastTrackChangeTimeMs = 0L
        _isPlaying.value = false
        if (isOfflineModeActive) {
            try { offlineMediaPlayer?.pause() } catch (e: Exception) {}
            offlineTickerJob?.cancel()
        } else {
            sendCommand("setPlaybackState(false)")
        }
        startOrUpdateService()
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            pause()
        } else {
            play()
        }
    }

    fun updateProgress(current: Float, duration: Float) {
        if (isOfflineModeActive) return // Managed by offline ticker
        val safeCurrent = current.coerceAtLeast(0f)
        _currentTimeSeconds.value = safeCurrent
        if (duration > 0f) {
            _durationSeconds.value = duration
        }
        syncServiceProgress(force = false)
    }

    fun seekTo(seconds: Float) {
        val dur = _durationSeconds.value
        val maxSafeTarget = if (dur > 2f) (dur - 1.0f) else dur
        val target = if (dur > 0f) seconds.coerceIn(0f, maxSafeTarget) else seconds.coerceAtLeast(0f)
        _currentTimeSeconds.value = target
        if (isOfflineModeActive) {
            try {
                offlineMediaPlayer?.seekTo((target * 1000).toInt())
            } catch (e: Exception) {}
        } else {
            sendCommand("seekTo($target)")
        }
        syncServiceProgress(force = true)
    }

    fun seekBy(seconds: Int) {
        val dur = _durationSeconds.value
        val rawNew = _currentTimeSeconds.value + seconds
        val maxSafeTarget = if (dur > 2f) (dur - 1.0f) else dur
        val newTime = if (dur > 0f) rawNew.coerceIn(0f, maxSafeTarget) else rawNew.coerceAtLeast(0f)
        _currentTimeSeconds.value = newTime
        if (isOfflineModeActive) {
            try {
                offlineMediaPlayer?.seekTo((newTime * 1000).toInt())
            } catch (e: Exception) {}
        } else {
            sendCommand("seekBy($seconds)")
        }
        syncServiceProgress(force = true)
    }

    private var isFetchingSimilar = false

    fun checkAndFetchSimilarTracks(track: VideoItem) {
        if (isFetchingSimilar || isOfflineModeActive) return
        isFetchingSimilar = true
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val similar = com.videoguard.app.data.YouTubeSearchEngine.getRelatedVideos(track, isMusicOnly = true)
                val currentQueue = _playbackQueue.value.toMutableList()
                val existingIds = currentQueue.map { it.id }.toSet()
                val newUnique = similar.filter { it.id !in existingIds }
                if (newUnique.isNotEmpty()) {
                    currentQueue.addAll(newUnique)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        _playbackQueue.value = currentQueue
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isFetchingSimilar = false
            }
        }
    }

    fun nextTrack() {
        val current = _currentTrack.value ?: return

        if (isOfflineModeActive) {
            val offlineList = com.videoguard.app.data.UserDataRepository.offlineTracks.value
            if (offlineList.isNotEmpty()) {
                val idx = offlineList.indexOfFirst { it.id == current.id }
                val nextIdx = if (idx in 0 until offlineList.lastIndex) idx + 1 else 0
                playTrack(offlineList[nextIdx], offlineList)
            } else {
                seekTo(0f)
            }
            return
        }

        val queue = _playbackQueue.value.ifEmpty { VideoRepository.sampleVideos }
        val idx = queue.indexOfFirst { it.id == current.id }
        if (idx >= queue.size - 2 || queue.size <= 2) {
            checkAndFetchSimilarTracks(current)
        }
        val nextIdx = if (idx in 0 until queue.lastIndex) idx + 1 else 0
        playTrack(queue[nextIdx], queue)
    }

    fun prevTrack() {
        val current = _currentTrack.value ?: return

        if (isOfflineModeActive) {
            if (_currentTimeSeconds.value > 3.0f) {
                seekTo(0f)
                return
            }
            val offlineList = com.videoguard.app.data.UserDataRepository.offlineTracks.value
            if (offlineList.isNotEmpty()) {
                val idx = offlineList.indexOfFirst { it.id == current.id }
                val prevIdx = if (idx > 0) idx - 1 else offlineList.lastIndex
                playTrack(offlineList[prevIdx], offlineList)
            } else {
                seekTo(0f)
            }
            return
        }

        val queue = _playbackQueue.value.ifEmpty { VideoRepository.sampleVideos }
        val idx = queue.indexOfFirst { it.id == current.id }
        val prevIdx = if (idx > 0) idx - 1 else queue.lastIndex
        playTrack(queue[prevIdx], queue)
    }

    fun stop() {
        _isPlaying.value = false
        _currentTrack.value = null
        _currentTimeSeconds.value = 0f
        _durationSeconds.value = 0f
        sendCommand("setPlaybackState(false)")
        stopService()
    }

    fun toggleBackgroundPlay() {
        _isBackgroundPlayEnabled.value = !_isBackgroundPlayEnabled.value
        if (!_isBackgroundPlayEnabled.value && !_isPlaying.value) {
            stopService()
        }
    }

    private fun sendCommand(command: String) {
        try {
            webViewCommandListener?.invoke(command)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseDurationSeconds(durationStr: String): Float {
        val parts = durationStr.trim().split(":").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60 + parts[1]).toFloat()
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]).toFloat()
            else -> 0f
        }
    }

    private fun syncServiceProgress(force: Boolean) {
        if (_currentTrack.value == null) return
        val now = System.currentTimeMillis()
        val cur = _currentTimeSeconds.value
        val dur = _durationSeconds.value
        val playing = _isPlaying.value
        val speed = _playbackSpeed.value

        val durationChanged = dur > 0f && kotlin.math.abs(dur - lastServiceSyncedDurationSec) > 0.5f
        val elapsedSinceSyncSec = ((now - lastServiceSyncTimeMs).coerceAtLeast(0L) / 1000f)
        val expectedPos = if (lastServiceSyncedPositionSec >= 0f) {
            lastServiceSyncedPositionSec + if (playing) elapsedSinceSyncSec * speed else 0f
        } else {
            cur
        }
        val positionJumped = kotlin.math.abs(cur - expectedPos) > 1.5f
        val periodicDue = playing && (now - lastServiceSyncTimeMs >= 4000L)

        if (force || durationChanged || positionJumped || periodicDue) {
            lastServiceSyncTimeMs = now
            lastServiceSyncedPositionSec = cur
            if (dur > 0f) {
                lastServiceSyncedDurationSec = dur
            }
            MediaPlaybackService.syncProgress(
                positionSec = cur,
                durationSec = dur,
                isPlaying = playing,
                playbackSpeed = speed
            )
        }
    }

    private fun startOrUpdateService() {
        val ctx = appContext ?: return
        val track = _currentTrack.value ?: return

        val now = System.currentTimeMillis()
        lastServiceSyncTimeMs = now
        lastServiceSyncedPositionSec = _currentTimeSeconds.value
        lastServiceSyncedDurationSec = _durationSeconds.value

        val intent = Intent(ctx, MediaPlaybackService::class.java).apply {
            action = MediaPlaybackService.ACTION_UPDATE
            putExtra(MediaPlaybackService.EXTRA_TITLE, track.title)
            putExtra(MediaPlaybackService.EXTRA_ARTIST, track.artist.ifEmpty { track.channelName })
            putExtra(MediaPlaybackService.EXTRA_IS_PLAYING, _isPlaying.value)
            putExtra(MediaPlaybackService.EXTRA_THUMBNAIL_URL, track.thumbnailUrl)
            putExtra(MediaPlaybackService.EXTRA_DURATION_SEC, _durationSeconds.value)
            putExtra(MediaPlaybackService.EXTRA_POSITION_SEC, _currentTimeSeconds.value)
            putExtra(MediaPlaybackService.EXTRA_PLAYBACK_SPEED, _playbackSpeed.value)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopService() {
        val ctx = appContext ?: return
        val intent = Intent(ctx, MediaPlaybackService::class.java).apply {
            action = MediaPlaybackService.ACTION_STOP
        }
        ctx.stopService(intent)
    }
}
