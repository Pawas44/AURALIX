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
import kotlinx.coroutines.async
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

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()
    private var unShuffledQueue: List<VideoItem> = emptyList()

    private val _isAutoplayEnabled = MutableStateFlow(true)
    val isAutoplayEnabled: StateFlow<Boolean> = _isAutoplayEnabled.asStateFlow()

    private val _isCaptionsEnabled = MutableStateFlow(false)
    val isCaptionsEnabled: StateFlow<Boolean> = _isCaptionsEnabled.asStateFlow()

    private val _sleepTimerMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerMinutes: StateFlow<Int?> = _sleepTimerMinutes.asStateFlow()

    private val _sleepTimerRemainingSeconds = MutableStateFlow<Int?>(null)
    val sleepTimerRemainingSeconds: StateFlow<Int?> = _sleepTimerRemainingSeconds.asStateFlow()

    private val _sleepTimerEndOnTrackFinish = MutableStateFlow(false)
    val sleepTimerEndOnTrackFinish: StateFlow<Boolean> = _sleepTimerEndOnTrackFinish.asStateFlow()

    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private var lastTrackChangeTimeMs: Long = 0L
    private var lastServiceSyncTimeMs: Long = 0L
    private var lastServiceSyncedPositionSec: Float = -1f
    private var lastServiceSyncedDurationSec: Float = -1f

    // Audio Engine Mutual Exclusion Lock
    private val audioEngineLock = Any()

    // Seek debouncing & progress sync trackers
    private var pendingSeekTarget: Float? = null
    private var lastSeekTimestampMs: Long = 0L

    // Continuous & Unlimited Playback queue trackers
    private val recentPlayedTrackIds = java.util.concurrent.ConcurrentLinkedDeque<String>()
    private var isReplenishingQueue = false

    // Offline native playback support
    private var offlineTickerJob: kotlinx.coroutines.Job? = null
    private var offlineMediaPlayer: android.media.MediaPlayer? = null
    private var offlineFileInputStream: java.io.FileInputStream? = null
    var isOfflineModeActive: Boolean = false
        private set

    // Fast in-memory cache for resolved Spotify tracks to ensure instantaneous (0ms) playback
    private val spotifyResolvedCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun getResolvedSpotifyId(id: String): String? {
        return spotifyResolvedCache[id]
    }

    fun cacheResolvedSpotifyId(spotifyId: String, realYtId: String, title: String? = null, artist: String? = null) {
        spotifyResolvedCache[spotifyId] = realYtId
        if (!title.isNullOrBlank()) {
            val cleanTitle = title.trim().lowercase()
            val cleanArtist = (artist?.split(",")?.firstOrNull()?.trim() ?: artist?.trim() ?: "").lowercase()
            spotifyResolvedCache["${cleanTitle}_${cleanArtist}"] = realYtId
        }
    }

    // Listener to dispatch JavaScript commands directly to the active YouTube WebView
    private var webViewCommandListener: ((String) -> Unit)? = null

    fun setCommandListener(listener: ((String) -> Unit)?) {
        webViewCommandListener = listener
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        if (isOfflineModeActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                offlineMediaPlayer?.let { mp ->
                    mp.playbackParams = mp.playbackParams.setSpeed(speed)
                }
            } catch (e: Exception) {}
        } else {
            sendCommand("setPlaybackRate($speed)")
        }
        syncServiceProgress(force = true)
    }

    fun setPlaybackQuality(quality: String) {
        _playbackQuality.value = quality
        val ytQuality = when {
            quality.contains("1080") -> "hd1080"
            quality.contains("720") -> "hd720"
            quality.contains("480") -> "large"
            quality.contains("360") -> "medium"
            else -> "hd1080"
        }
        sendCommand("setPlaybackQuality('$ytQuality')")
    }

    private var _currentVolumeMultiplier = 1.0f

    fun setVolumeLevel(multiplier: Float) {
        _currentVolumeMultiplier = multiplier
        val volPercent = (multiplier * 100).toInt().coerceIn(0, 100)
        sendCommand("setVolume($volPercent)")
        try {
            offlineMediaPlayer?.setVolume(multiplier.coerceIn(0f, 1f), multiplier.coerceIn(0f, 1f))
        } catch (e: Exception) {}
    }

    fun toggleLoop() {
        _isLoopEnabled.value = !_isLoopEnabled.value
    }

    fun toggleShuffle() {
        val newState = !_isShuffleEnabled.value
        _isShuffleEnabled.value = newState
        val cur = _currentTrack.value
        val queue = _playbackQueue.value
        if (newState) {
            unShuffledQueue = queue
            if (cur != null && queue.size > 1) {
                val others = queue.filter { it.id != cur.id }.shuffled()
                _playbackQueue.value = listOf(cur) + others
            } else {
                _playbackQueue.value = queue.shuffled()
            }
        } else {
            if (unShuffledQueue.isNotEmpty()) {
                val restored = unShuffledQueue
                unShuffledQueue = emptyList()
                _playbackQueue.value = restored
            }
        }
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

    fun setSleepTimer(minutes: Int?, endOnTrackFinish: Boolean = false) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerEndOnTrackFinish.value = endOnTrackFinish

        if (endOnTrackFinish) {
            val cur = _currentTimeSeconds.value
            val dur = _durationSeconds.value
            val remain = if (dur > cur) (dur - cur).toInt().coerceAtLeast(10) else 180
            _sleepTimerRemainingSeconds.value = remain
            _sleepTimerMinutes.value = (remain + 59) / 60
            sleepTimerJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                var currentSec = remain
                while (isActive && currentSec > 0) {
                    kotlinx.coroutines.delay(1000L)
                    currentSec--
                    _sleepTimerRemainingSeconds.value = currentSec
                    _sleepTimerMinutes.value = (currentSec + 59) / 60
                }
                pause()
                _sleepTimerRemainingSeconds.value = null
                _sleepTimerMinutes.value = null
                _sleepTimerEndOnTrackFinish.value = false
            }
            return
        }

        if (minutes != null && minutes > 0) {
            val totalSeconds = minutes * 60
            _sleepTimerMinutes.value = minutes
            _sleepTimerRemainingSeconds.value = totalSeconds
            sleepTimerJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                var currentSec = totalSeconds
                while (isActive && currentSec > 0) {
                    kotlinx.coroutines.delay(1000L)
                    currentSec--
                    _sleepTimerRemainingSeconds.value = currentSec
                    _sleepTimerMinutes.value = (currentSec + 59) / 60
                }
                pause()
                _sleepTimerRemainingSeconds.value = null
                _sleepTimerMinutes.value = null
            }
        } else {
            _sleepTimerRemainingSeconds.value = null
            _sleepTimerMinutes.value = null
        }
    }

    fun formatSleepTimerRemaining(seconds: Int?): String {
        if (seconds == null || seconds <= 0) return ""
        val m = seconds / 60
        val s = seconds % 60
        return if (m > 0) {
            String.format(java.util.Locale.US, "%dm %02ds", m, s)
        } else {
            String.format(java.util.Locale.US, "%ds", s)
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
        com.videoguard.app.data.AppSettingsRepository.init(context)
        com.videoguard.app.data.AccountRepository.init(context)
        com.videoguard.app.data.SpotifyConnectEngine.init(context)
        com.videoguard.app.recommendation.RecommendationEngine.init(context)
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

    private fun markTrackPlayed(id: String) {
        recentPlayedTrackIds.remove(id)
        recentPlayedTrackIds.addLast(id)
        while (recentPlayedTrackIds.size > 150) {
            recentPlayedTrackIds.pollFirst()
        }
    }

    /**
     * Strict Mutual Audio Exclusion:
     * Immediately stops, pauses, and releases both online WebView audio and offline MediaPlayer
     * ensuring that under no circumstances can two audio sources play simultaneously.
     */
    fun stopAllAudioSources() {
        synchronized(audioEngineLock) {
            // 1. Immediately stop and silence online WebView audio & any DOM video elements
            sendCommand("stopVideo()")
            sendCommand("setPlaybackState(false)")

            // 2. Immediately stop, reset and release offline MediaPlayer
            try {
                offlineTickerJob?.cancel()
                offlineTickerJob = null
                offlineMediaPlayer?.apply {
                    try {
                        if (isPlaying) stop()
                    } catch (e: Exception) {}
                    try { reset() } catch (e: Exception) {}
                    try { release() } catch (e: Exception) {}
                }
            } catch (e: Exception) {
                // Ignore
            } finally {
                offlineMediaPlayer = null
                try { offlineFileInputStream?.close() } catch (e: Exception) {}
                offlineFileInputStream = null
                isOfflineModeActive = false
            }
        }
    }

    fun playTrack(track: VideoItem, queue: List<VideoItem> = emptyList()) {
        val ctx = appContext
        if (ctx != null && !com.videoguard.app.data.AuthManager.verifySessionToken(ctx)) {
            com.videoguard.app.data.AuthManager.logout(ctx)
            return
        }
        val resolvedId = getResolvedSpotifyId(track.id)
        val isOffline = com.videoguard.app.data.UserDataRepository.isDownloaded(track.id) ||
                (resolvedId != null && com.videoguard.app.data.UserDataRepository.isDownloaded(resolvedId))

        if (!isOffline && (track.id.startsWith("spotify_") || track.id.startsWith("pending_") || track.id.length != 11)) {
            val cleanTitle = track.title.trim()
            val cleanArtist = track.artist.split(",").firstOrNull()?.trim() ?: track.artist.trim()
            val cacheKey = "${cleanTitle.lowercase()}_${cleanArtist.lowercase()}"
            val cachedId = resolvedId ?: spotifyResolvedCache[cacheKey]

            if (cachedId != null && cachedId.length == 11) {
                val targetTrack = track.copy(id = cachedId)
                val updatedQueue = if (queue.isNotEmpty()) queue.map { if (it.id == track.id) targetTrack else it } else emptyList()
                playTrack(targetTrack, updatedQueue)
                return
            }

            _currentTrack.value = track
            _isPlaying.value = true
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                val query = if (cleanArtist.isNotBlank()) "$cleanTitle $cleanArtist" else cleanTitle

                // High-precision resolution: MusicImportEngine validates title, artist, and duration
                val resolved = com.videoguard.app.data.MusicImportEngine.resolveSingleTrack(track.title, track.artist)
                    ?: run {
                        // Resilient fallback with strict title matching to prevent playing unrelated songs
                        val cleanTarget = cleanSongTitle(cleanTitle)
                        val d1 = async {
                            try {
                                com.videoguard.app.data.YouTubeSyncEngine.searchVideosInnerTube(query, filterToVideos = true)
                                    .firstOrNull { cand ->
                                        cand.id.length == 11 && !cand.id.startsWith("search_") &&
                                        (cleanSongTitle(cand.title).contains(cleanTarget) || cleanTarget.contains(cleanSongTitle(cand.title)))
                                    }
                            } catch (e: Exception) { null }
                        }
                        val d2 = async {
                            try {
                                com.videoguard.app.data.YouTubeSearchEngine.searchVideos(query, isMusicOnly = true)
                                    .firstOrNull { cand ->
                                        cand.id.length == 11 &&
                                        (cleanSongTitle(cand.title).contains(cleanTarget) || cleanTarget.contains(cleanSongTitle(cand.title)))
                                    }
                            } catch (e: Exception) { null }
                        }
                        d1.await() ?: d2.await()
                    }

                if (resolved != null && resolved.id.length == 11) {
                    spotifyResolvedCache[track.id] = resolved.id
                    spotifyResolvedCache[cacheKey] = resolved.id

                    val targetTrack = track.copy(
                        id = resolved.id,
                        duration = if (track.duration != "0:00" && track.duration.isNotBlank()) track.duration else resolved.duration,
                        thumbnailUrl = if (track.thumbnailUrl.isNotBlank() && !track.thumbnailUrl.contains("default")) track.thumbnailUrl else resolved.thumbnailUrl
                    )
                    try {
                        com.videoguard.app.data.UserDataRepository.updateTrackInPlaylists(track.id, targetTrack)
                    } catch (e: Exception) {}
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        val updatedQueue = if (queue.isNotEmpty()) queue.map { if (it.id == track.id) targetTrack else it } else emptyList()
                        playTrack(targetTrack, updatedQueue)
                    }
                }
            }
            return
        }

        synchronized(audioEngineLock) {
            // Guarantee previous audio source is 100% stopped and released before new playback starts
            stopAllAudioSources()

            lastTrackChangeTimeMs = System.currentTimeMillis()
            pendingSeekTarget = null
            lastSeekTimestampMs = 0L

            _currentTrack.value = track
            _isPlaying.value = true
            val initialDurationSec = parseDurationSeconds(track.duration)
            _currentTimeSeconds.value = 0f
            _durationSeconds.value = initialDurationSec
            lastServiceSyncTimeMs = System.currentTimeMillis()
            lastServiceSyncedPositionSec = 0f
            lastServiceSyncedDurationSec = initialDurationSec

            markTrackPlayed(track.id)

            // Automatically record to User History
            com.videoguard.app.data.UserDataRepository.addToHistory(track)
            com.videoguard.app.recommendation.ListeningHistoryManager.onTrackStarted(track, initialDurationSec)

            if (queue.isNotEmpty()) {
                if (_isShuffleEnabled.value && queue.size > 1) {
                    unShuffledQueue = queue
                    val trackIdx = queue.indexOfFirst { it === track || it.id == track.id }.coerceAtLeast(0)
                    val others = queue.filterIndexed { idx, _ -> idx != trackIdx }.shuffled()
                    _playbackQueue.value = listOf(queue[trackIdx]) + others
                } else {
                    unShuffledQueue = emptyList()
                    _playbackQueue.value = queue
                }
            } else if (_playbackQueue.value.none { it.id == track.id }) {
                _playbackQueue.value = listOf(track)
            }

            val isDownloaded = isOffline || com.videoguard.app.data.UserDataRepository.isDownloaded(track.id)

            startOrUpdateService()

            if (isDownloaded) {
                startOfflinePlayback(track, initialDurationSec)
            } else {
                sendCommand("loadVideo('${track.id}')")
                sendCommand("setPlaybackState(true)")
            }

            com.videoguard.app.data.ListenTogetherEngine.onHostTrackChanged(track)

            // Proactively check and replenish queue
            replenishQueueIfNeeded(track)

            // Prefetch and pre-resolve upcoming Spotify queue tracks so Next Track plays with 0ms delay!
            val upcomingSpotifyTracks = queue.dropWhile { it.id != track.id }.drop(1).take(3)
                .filter { it.id.startsWith("spotify_") || it.id.length != 11 }
            if (upcomingSpotifyTracks.isNotEmpty()) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    for (upNext in upcomingSpotifyTracks) {
                        val cTitle = upNext.title.trim()
                        val cArtist = upNext.artist.split(",").firstOrNull()?.trim() ?: upNext.artist.trim()
                        val k = "${cTitle.lowercase()}_${cArtist.lowercase()}"
                        if (!spotifyResolvedCache.containsKey(upNext.id) && !spotifyResolvedCache.containsKey(k)) {
                            val q = if (cArtist.isNotBlank()) "$cTitle $cArtist" else cTitle
                            try {
                                val found = com.videoguard.app.data.YouTubeSyncEngine.searchVideosInnerTube(q, filterToVideos = true)
                                    .firstOrNull { it.id.length == 11 && !it.id.startsWith("search_") }
                                    ?: com.videoguard.app.data.YouTubeSearchEngine.searchVideos(q, isMusicOnly = true).firstOrNull { it.id.length == 11 }
                                if (found != null && found.id.length == 11) {
                                    spotifyResolvedCache[upNext.id] = found.id
                                    spotifyResolvedCache[k] = found.id
                                    val resolved = upNext.copy(id = found.id)
                                    com.videoguard.app.data.UserDataRepository.updateTrackInPlaylists(upNext.id, resolved)
                                }
                            } catch (e: Exception) {}
                        }
                    }
                }
            }
        }
    }

    private fun startOfflinePlayback(track: VideoItem, initialDuration: Float) {
        synchronized(audioEngineLock) {
            // Double guarantee that online WebView player is stopped
            sendCommand("stopVideo()")
            sendCommand("setPlaybackState(false)")

            isOfflineModeActive = true
            val dur = if (initialDuration > 0f) initialDuration else 180f
            _durationSeconds.value = dur
            val ctx = appContext
            if (ctx != null) {
                val resolvedId = getResolvedSpotifyId(track.id)
                try {
                    val dir = java.io.File(ctx.filesDir, "downloads")
                    val candidateIds = listOfNotNull(track.id, resolvedId).distinct()
                    val f = candidateIds.flatMap { cid ->
                        listOf(
                            java.io.File(dir, "$cid.m4a"),
                            java.io.File(dir, "$cid.mp4"),
                            java.io.File(dir, "$cid.webm")
                        )
                    }.firstOrNull { it.exists() && it.length() > 20_000L }

                    if (f != null) {
                        try { offlineFileInputStream?.close() } catch (e: Exception) {}
                        val fis = java.io.FileInputStream(f)
                        offlineFileInputStream = fis

                        val mp = android.media.MediaPlayer()
                        offlineMediaPlayer = mp
                        mp.setAudioAttributes(
                            android.media.AudioAttributes.Builder()
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                .build()
                        )
                        // Use open FileDescriptor. DO NOT close fis while MediaPlayer is using the fd!
                        mp.setDataSource(fis.fd, 0L, f.length())

                        mp.isLooping = _isLoopEnabled.value
                        mp.setOnErrorListener { _, what, extra ->
                            android.util.Log.e("PlaybackController", "Offline MediaPlayer error: what=$what, extra=$extra")
                            stopOfflinePlayback()
                            true // Handled: suppress native crash dialog
                        }
                        mp.setOnCompletionListener {
                            if (_isLoopEnabled.value) {
                                this@PlaybackController.seekTo(0f)
                                try { mp.start() } catch (e: Exception) {}
                            } else if (_isAutoplayEnabled.value) {
                                nextTrack()
                            } else {
                                pause()
                            }
                        }
                        mp.setOnSeekCompleteListener { seekedMp ->
                            try {
                                val pos = seekedMp.currentPosition / 1000f
                                _currentTimeSeconds.value = pos
                                pendingSeekTarget = null
                                syncServiceProgress(force = true)
                            } catch (e: Exception) {}
                        }
                        mp.setOnPreparedListener { preparedMp ->
                            try {
                                preparedMp.start()
                                val trueDurSec = preparedMp.duration / 1000f
                                if (trueDurSec > 0f) {
                                    _durationSeconds.value = trueDurSec
                                }
                                _isPlaying.value = true
                                resumeOfflineTicker()
                                startOrUpdateService()
                            } catch (e: Exception) {
                                android.util.Log.e("PlaybackController", "Error starting prepared MediaPlayer: ${e.message}", e)
                            }
                        }
                        mp.prepareAsync()
                        isOfflineModeActive = true
                    } else {
                        // File missing or incomplete: fallback smoothly to online streaming
                        isOfflineModeActive = false
                        val fallbackId = resolvedId ?: track.id
                        if (fallbackId.length == 11) {
                            sendCommand("loadVideo('$fallbackId')")
                            sendCommand("setPlaybackState(true)")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PlaybackController", "Failed to start offline MediaPlayer: ${e.message}", e)
                    try { offlineFileInputStream?.close() } catch (ignored: Exception) {}
                    offlineFileInputStream = null
                    try { offlineMediaPlayer?.release() } catch (ignored: Exception) {}
                    offlineMediaPlayer = null
                    isOfflineModeActive = false
                    val fallbackId = resolvedId ?: track.id
                    if (fallbackId.length == 11) {
                        sendCommand("loadVideo('$fallbackId')")
                        sendCommand("setPlaybackState(true)")
                    }
                }
            }
        }
    }

    private fun resumeOfflineTicker() {
        offlineTickerJob?.cancel()
        if (!isOfflineModeActive || offlineMediaPlayer == null) return

        val handler = kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
            android.util.Log.e("PlaybackController", "Offline ticker exception", throwable)
        }
        offlineTickerJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + handler).launch {
            while (isActive && _isPlaying.value && isOfflineModeActive) {
                kotlinx.coroutines.delay(200L)
                val mp = offlineMediaPlayer ?: break
                try {
                    val pos = mp.currentPosition
                    if (pos >= 0) {
                        val sec = (pos / 1000f).coerceAtLeast(0f)
                        _currentTimeSeconds.value = sec
                    }
                    val dur = mp.duration
                    if (dur > 0) {
                        val d = dur / 1000f
                        if (kotlin.math.abs(_durationSeconds.value - d) > 0.5f) {
                            _durationSeconds.value = d
                        }
                    }
                    syncServiceProgress(force = false)
                } catch (e: Exception) {
                    // Handled safely
                }
            }
        }
    }

    fun stopOfflinePlayback() {
        synchronized(audioEngineLock) {
            isOfflineModeActive = false
            offlineTickerJob?.cancel()
            offlineTickerJob = null
            try {
                offlineMediaPlayer?.apply {
                    try { if (isPlaying) stop() } catch (e: Exception) {}
                    try { reset() } catch (e: Exception) {}
                    try { release() } catch (e: Exception) {}
                }
            } catch (e: Exception) {}
            offlineMediaPlayer = null
            try { offlineFileInputStream?.close() } catch (e: Exception) {}
            offlineFileInputStream = null
        }
    }

    fun isSongFinished(): Boolean {
        val cur = _currentTimeSeconds.value
        val dur = _durationSeconds.value
        val elapsed = System.currentTimeMillis() - lastTrackChangeTimeMs
        if (dur > 10f) {
            return cur >= (dur - 8f) || (cur >= dur * 0.90f) || (elapsed > 12000L && cur >= (dur - 12f))
        }
        if (dur > 2f) {
            return cur >= (dur - 1f)
        }
        return elapsed > 5000L && cur > 5f
    }

    fun setQueue(queue: List<VideoItem>) {
        if (queue.isNotEmpty()) {
            _playbackQueue.value = queue
        }
    }

    fun setPlayingState(playing: Boolean) {
        if (isOfflineModeActive) return // Suppress WebView state interference during offline playback!
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
        val ctx = appContext
        if (ctx != null && !com.videoguard.app.data.AuthManager.verifySessionToken(ctx)) {
            com.videoguard.app.data.AuthManager.logout(ctx)
            return
        }
        _isPlaying.value = true
        if (isOfflineModeActive) {
            try { offlineMediaPlayer?.start() } catch (e: Exception) {}
            resumeOfflineTicker()
        } else {
            sendCommand("setPlaybackState(true)")
        }
        startOrUpdateService()
        com.videoguard.app.data.ListenTogetherEngine.onHostPlayStateChanged(true, (_currentTimeSeconds.value * 1000).toLong())
    }

    fun pause() {
        lastTrackChangeTimeMs = 0L
        _isPlaying.value = false
        if (isOfflineModeActive) {
            try {
                offlineMediaPlayer?.let {
                    val cur = it.currentPosition
                    if (cur >= 0) {
                        _currentTimeSeconds.value = cur / 1000f
                    }
                    it.pause()
                }
            } catch (e: Exception) {}
            offlineTickerJob?.cancel()
        }
        // Always tell WebView to pause as well to guarantee no rogue online audio
        sendCommand("pauseVideo()")
        sendCommand("setPlaybackState(false)")
        startOrUpdateService()
        com.videoguard.app.data.ListenTogetherEngine.onHostPlayStateChanged(false, (_currentTimeSeconds.value * 1000).toLong())
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

        // Debounce stale progress updates during seeking
        val pending = pendingSeekTarget
        if (pending != null) {
            val elapsedSeek = System.currentTimeMillis() - lastSeekTimestampMs
            if (elapsedSeek < 1800L) {
                // If reported position has not reached within 2 seconds of target, discard stale pre-seek time
                if (kotlin.math.abs(current - pending) > 2.0f) {
                    return
                }
                pendingSeekTarget = null
            } else {
                pendingSeekTarget = null
            }
        }

        // Debounce stale progress updates immediately following a track change
        val elapsedTrackChange = System.currentTimeMillis() - lastTrackChangeTimeMs
        if (elapsedTrackChange < 1800L && current > 4.0f) {
            return
        }

        val safeCurrent = current.coerceAtLeast(0f)
        _currentTimeSeconds.value = safeCurrent
        if (duration > 0f) {
            _durationSeconds.value = duration
        }
        com.videoguard.app.recommendation.ListeningHistoryManager.onProgressUpdate(safeCurrent, duration)
        syncServiceProgress(force = false)
    }

    fun seekTo(seconds: Float) {
        val dur = _durationSeconds.value
        val maxSafeTarget = if (dur > 2f) (dur - 1.0f) else dur
        val target = if (dur > 0f) seconds.coerceIn(0f, maxSafeTarget) else seconds.coerceAtLeast(0f)
        
        pendingSeekTarget = target
        lastSeekTimestampMs = System.currentTimeMillis()
        _currentTimeSeconds.value = target

        if (isOfflineModeActive) {
            try {
                offlineMediaPlayer?.seekTo((target * 1000).toInt())
            } catch (e: Exception) {}
        } else {
            sendCommand("seekTo($target)")
        }
        syncServiceProgress(force = true)
        com.videoguard.app.data.ListenTogetherEngine.onHostSeek((target * 1000).toLong())
    }

    fun seekBy(seconds: Int) {
        val dur = _durationSeconds.value
        val rawNew = _currentTimeSeconds.value + seconds
        val maxSafeTarget = if (dur > 2f) (dur - 1.0f) else dur
        val newTime = if (dur > 0f) rawNew.coerceIn(0f, maxSafeTarget) else rawNew.coerceAtLeast(0f)
        
        pendingSeekTarget = newTime
        lastSeekTimestampMs = System.currentTimeMillis()
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

    private val sessionBlockedIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val sessionBlockedKeys = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun normalizeSongKey(title: String, artist: String): String {
        val cleanTitle = title.lowercase(java.util.Locale.ROOT)
            .replace(Regex("""\((?:official|video|audio|lyric|hd|4k|remix|version)[^)]*\)"""), "")
            .replace(Regex("""\[(?:official|video|audio|lyric|hd|4k|remix|version)[^\]]*\]"""), "")
            .replace(Regex("""[^a-z0-9]"""), "")
        val cleanArtist = artist.lowercase(java.util.Locale.ROOT)
            .replace(Regex("""[^a-z0-9]"""), "")
        return "$cleanTitle|$cleanArtist"
    }

    fun cleanSongTitle(raw: String): String {
        return raw.lowercase(java.util.Locale.ROOT)
            .replace(Regex("""\([^)]*\)"""), " ")
            .replace(Regex("""\[[^]]*\]"""), " ")
            .replace(Regex("""(?i)\b(official|video|audio|lyrics?|remix|hd|4k|song|full video|dance|dj|status|ringtone|slowed|reverb)\b"""), " ")
            .replace(Regex("""[^a-zA-Z0-9\u0900-\u097F]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * User explicitly marks a song as "Don't play this" (ByTune style).
     * The song and identical versions are blocked from current queue and future auto-play recommendations in this session.
     */
    fun dontPlayTrack(track: VideoItem) {
        sessionBlockedIds.add(track.id)
        val key = normalizeSongKey(track.title, track.artist)
        if (key.isNotBlank()) {
            sessionBlockedKeys.add(key)
        }

        // Remove from current active queue
        val currentQ = _playbackQueue.value.toMutableList()
        currentQ.removeAll { it.id == track.id || (key.isNotBlank() && normalizeSongKey(it.title, it.artist) == key) }
        _playbackQueue.value = currentQ

        if (unShuffledQueue.isNotEmpty()) {
            val unQ = unShuffledQueue.toMutableList()
            unQ.removeAll { it.id == track.id || (key.isNotBlank() && normalizeSongKey(it.title, it.artist) == key) }
            unShuffledQueue = unQ
        }

        // If the dismissed song is currently playing, jump to next song immediately
        if (_currentTrack.value?.id == track.id) {
            nextTrack()
        } else {
            val cur = _currentTrack.value
            if (cur != null) {
                replenishQueueIfNeeded(cur)
            }
        }
    }

    /**
     * Continuous AutoPlay That Never Runs Dry (ByTune Style)
     * When queue runs low (<= 4 remaining), songs that fit keep coming,
     * lined up a step ahead using YouTube Music's RDAMVM automix song radio with zero gap.
     */
    fun replenishQueueIfNeeded(currentTrack: VideoItem) {
        if (!_isAutoplayEnabled.value) return
        if (isReplenishingQueue) return
        val currentQueue = _playbackQueue.value
        val idx = currentQueue.indexOfFirst { it.id == currentTrack.id }
        // Playlist Queue Integrity Rule:
        // When user is playing a playlist, ONLY replenish when current track is at the very end of the playlist!
        // Never inject random radio/mix songs into the middle of the active playlist queue.
        if (idx >= 0 && idx == currentQueue.lastIndex) {
            triggerQueueReplenishment(currentTrack)
        }
    }

    private fun triggerQueueReplenishment(
        seedTrack: VideoItem,
        onComplete: ((List<VideoItem>) -> Unit)? = null
    ) {
        if (isReplenishingQueue) return
        isReplenishingQueue = true
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val currentQueue = _playbackQueue.value.toMutableList()
                val existingIds = currentQueue.map { it.id }.toMutableSet()
                existingIds.addAll(recentPlayedTrackIds)
                existingIds.addAll(sessionBlockedIds)

                // Pick best seed: the last song in queue if available, otherwise current song
                val effectiveSeed = currentQueue.lastOrNull() ?: seedTrack
                val rawCandidates = mutableListOf<VideoItem>()

                if (!isOfflineModeActive) {
                    // Tier 1 (ByTune): YouTube Music Native RDAMVM endless radio
                    try {
                        val radio = com.videoguard.app.data.YouTubeSearchEngine.getSongRadio(effectiveSeed.id)
                        rawCandidates.addAll(radio)
                    } catch (e: Exception) {}

                    // Tier 2: YouTube Related Videos (music-only)
                    if (rawCandidates.size < 8) {
                        try {
                            val related = com.videoguard.app.data.YouTubeSearchEngine.getRelatedVideos(effectiveSeed, isMusicOnly = true)
                            rawCandidates.addAll(related)
                        } catch (e: Exception) {}
                    }

                    // Tier 3: Personalized Feed
                    if (rawCandidates.size < 6) {
                        try {
                            val feed = com.videoguard.app.recommendation.RecommendationEngine.feedState.value
                            val feedTracks = (feed.recommendedForYou + feed.madeForYou + feed.discoverWeekly)
                            rawCandidates.addAll(feedTracks)
                        } catch (e: Exception) {}
                    }

                    // Tier 4: Artist Radio Search
                    val artist = effectiveSeed.artist.ifBlank { effectiveSeed.channelName }
                    if (rawCandidates.size < 6 && artist.isNotBlank() && artist != "YouTube Channel") {
                        try {
                            val artistSongs = com.videoguard.app.data.YouTubeSearchEngine.searchVideos(
                                "$artist songs",
                                isMusicOnly = true
                            )
                            rawCandidates.addAll(artistSongs)
                        } catch (e: Exception) {}
                    }

                    // Tier 5: Curated Music Pool
                    if (rawCandidates.size < 4) {
                        val musicTracks = VideoRepository.getMusicTracks()
                        rawCandidates.addAll(musicTracks)
                    }
                }

                // Tier 6: Local Offline Tracks Fallback (or if network failed)
                if (rawCandidates.isEmpty() || isOfflineModeActive) {
                    val offline = com.videoguard.app.data.UserDataRepository.offlineTracks.value
                    val unplayedOffline = offline.filter { it.id !in existingIds && it.id != effectiveSeed.id }
                    if (unplayedOffline.isNotEmpty()) {
                        rawCandidates.addAll(unplayedOffline)
                    } else if (offline.size > 1) {
                        rawCandidates.addAll(offline.filter { it.id != effectiveSeed.id }.shuffled())
                    } else if (offline.isNotEmpty()) {
                        rawCandidates.addAll(offline)
                    }
                }

                // Tier 7: Failsafe fallback across sample catalogue (online only)
                if (rawCandidates.isEmpty() && !isOfflineModeActive) {
                    val samplePool = VideoRepository.sampleVideos.filter { it.id != effectiveSeed.id }
                    rawCandidates.addAll(samplePool)
                }

                // ByTune Smart Filtering:
                // 1. Exclude blocked IDs and blocked song keys
                // 2. Diversity cap: max 2 songs per artist (3 for seed artist)
                // Deduplicate by title to ensure different, fresh songs are queued instead of variations/remixes of the same song!
                val existingTitles = currentQueue.map { cleanSongTitle(it.title) }.filter { it.length >= 3 }.toMutableSet()
                val seedTitleClean = cleanSongTitle(effectiveSeed.title)
                if (seedTitleClean.length >= 3) {
                    existingTitles.add(seedTitleClean)
                }

                val seedArtist = (effectiveSeed.artist.ifBlank { effectiveSeed.channelName }).lowercase(java.util.Locale.ROOT).trim()
                val artistCounts = mutableMapOf<String, Int>()
                val accepted = mutableListOf<VideoItem>()

                val pool = if (_isShuffleEnabled.value) rawCandidates.shuffled() else rawCandidates
                for (cand in pool) {
                    if (cand.id in existingIds) continue
                    val key = normalizeSongKey(cand.title, cand.artist)
                    if (key.isNotBlank() && key in sessionBlockedKeys) continue

                    // Diversity & Deduplication Check:
                    // Never play alternate covers/remixes/uploads of the exact same song that is already queued or played!
                    val candTitleClean = cleanSongTitle(cand.title)
                    if (candTitleClean.length >= 3) {
                        val isSameSong = existingTitles.any { existing ->
                            existing == candTitleClean ||
                            (candTitleClean.length >= 5 && existing.contains(candTitleClean)) ||
                            (existing.length >= 5 && candTitleClean.contains(existing))
                        }
                        if (isSameSong) continue
                    }

                    val candArtist = (cand.artist.ifBlank { cand.channelName }).lowercase(java.util.Locale.ROOT).trim()
                    val maxCap = if (candArtist.isNotBlank() && (candArtist.contains(seedArtist) || seedArtist.contains(candArtist))) 3 else 2
                    val currentCount = artistCounts.getOrDefault(candArtist, 0)
                    if (currentCount < maxCap) {
                        artistCounts[candArtist] = currentCount + 1
                        accepted.add(cand)
                        existingIds.add(cand.id)
                        if (candTitleClean.length >= 3) {
                            existingTitles.add(candTitleClean)
                        }
                        if (accepted.size >= 8) break
                    }
                }

                if (accepted.isNotEmpty()) {
                    currentQueue.addAll(accepted)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        _playbackQueue.value = currentQueue
                        onComplete?.invoke(currentQueue)
                    }
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onComplete?.invoke(currentQueue)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isReplenishingQueue = false
            }
        }
    }

    fun nextTrack() {
        val current = _currentTrack.value ?: return

        if (isSongFinished()) {
            com.videoguard.app.recommendation.ListeningHistoryManager.onTrackCompleted(current)
        } else {
            com.videoguard.app.recommendation.ListeningHistoryManager.onTrackSkipped(current)
        }
        markTrackPlayed(current.id)

        val queue = _playbackQueue.value.ifEmpty {
            if (isOfflineModeActive) com.videoguard.app.data.UserDataRepository.offlineTracks.value else VideoRepository.getMusicTracks()
        }
        val idx = queue.indexOfFirst { it.id == current.id }

        if (idx in 0 until queue.lastIndex) {
            val nextTrack = queue[idx + 1]
            playTrack(nextTrack, emptyList())
            if (idx + 1 == queue.lastIndex && _isAutoplayEnabled.value) {
                replenishQueueIfNeeded(nextTrack)
            }
        } else {
            // Reached the end of playlist queue!
            if (_isAutoplayEnabled.value) {
                triggerQueueReplenishment(current) { updatedQueue ->
                    val newIdx = updatedQueue.indexOfFirst { it.id == current.id }
                    if (newIdx in 0 until updatedQueue.lastIndex) {
                        playTrack(updatedQueue[newIdx + 1], emptyList())
                    } else if (updatedQueue.isNotEmpty()) {
                        val fallback = updatedQueue.firstOrNull { it.id != current.id } ?: updatedQueue.first()
                        playTrack(fallback, emptyList())
                    }
                }
            } else {
                _isPlaying.value = false
            }
        }
    }

    fun prevTrack() {
        val current = _currentTrack.value ?: return

        if (_currentTimeSeconds.value > 3.0f) {
            seekTo(0f)
            return
        }

        com.videoguard.app.recommendation.ListeningHistoryManager.onTrackSkipped(current)

        val queue = _playbackQueue.value.ifEmpty {
            if (isOfflineModeActive) com.videoguard.app.data.UserDataRepository.offlineTracks.value else VideoRepository.getMusicTracks()
        }
        val idx = queue.indexOfFirst { it.id == current.id }
        val prevIdx = if (idx > 0) idx - 1 else queue.lastIndex
        if (prevIdx in queue.indices) {
            playTrack(queue[prevIdx], emptyList())
        }
    }

    fun stop() {
        stopAllAudioSources()
        _isPlaying.value = false
        _currentTrack.value = null
        _currentTimeSeconds.value = 0f
        _durationSeconds.value = 0f
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
