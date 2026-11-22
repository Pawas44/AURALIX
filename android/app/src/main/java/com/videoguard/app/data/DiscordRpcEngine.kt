package com.videoguard.app.data

import android.content.Context
import android.os.Process
import android.util.Log
import com.videoguard.app.model.VideoItem
import com.videoguard.app.service.PlaybackController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tokenless Discord Rich Presence (RPC) Engine.
 * Uses Discord PC RPC Protocol (Localhost WebSocket ports 6463-6472)
 * without requiring any user account token.
 * Shows Spotify-style Rich Presence:
 * - "Listening to AURALIX"
 * - Live Song Title
 * - Artist Name & Album
 * - High-Resolution Song Cover Art
 * - Live animated progress bar with start & end timestamps (e.g. 0:45 ───●───── 3:30)
 * - Telegram channel buttons: https://t.me/auralix_music
 */
object DiscordRpcEngine {
    private const val TAG = "DiscordRpcEngine"
    const val CLIENT_ID = "1358372087017767073"
    const val TELEGRAM_URL = "https://t.me/auralix_music"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // RPC is ON by default with no off option
    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // Compatibility stub for any legacy references
    private val _userToken = MutableStateFlow("")
    val userToken: StateFlow<String> = _userToken.asStateFlow()

    private var okHttpClient: OkHttpClient? = null
    private var activeWebSocket: WebSocket? = null
    private var connectionLoopJob: Job? = null
    private val nonceCounter = AtomicInteger(1)
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        _isEnabled.value = true

        okHttpClient = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .connectTimeout(2, TimeUnit.SECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .build()

        // 1. Observe track changes
        scope.launch {
            PlaybackController.currentTrack.collect { track ->
                if (_isConnected.value) {
                    updatePresence(track, PlaybackController.isPlaying.value)
                }
            }
        }

        // 2. Observe play/pause state
        scope.launch {
            PlaybackController.isPlaying.collect { isPlaying ->
                if (_isConnected.value) {
                    updatePresence(PlaybackController.currentTrack.value, isPlaying)
                }
            }
        }

        // 3. Observe duration changes (when ExoPlayer resolves true audio duration)
        scope.launch {
            PlaybackController.durationSeconds.collect { dur ->
                if (_isConnected.value && dur > 0f) {
                    updatePresence(PlaybackController.currentTrack.value, PlaybackController.isPlaying.value)
                }
            }
        }

        // 4. Observe seek / position scrubbing jumps
        scope.launch {
            var lastTrackId = ""
            var lastPos = 0f
            var lastSysTime = System.currentTimeMillis()
            PlaybackController.currentTimeSeconds.collect { pos ->
                val track = PlaybackController.currentTrack.value
                val trackId = track?.id ?: ""
                val now = System.currentTimeMillis()
                val isPlaying = PlaybackController.isPlaying.value

                if (trackId != lastTrackId) {
                    lastTrackId = trackId
                    lastPos = pos
                    lastSysTime = now
                    return@collect
                }

                if (isPlaying) {
                    val expectedPos = lastPos + (now - lastSysTime) / 1000f
                    // If user seeked / jumped backward or forward by more than 2.5 seconds
                    if (kotlin.math.abs(pos - expectedPos) > 2.5f) {
                        lastPos = pos
                        lastSysTime = now
                        if (_isConnected.value) {
                            updatePresence(track, true)
                        }
                    }
                }
            }
        }

        startRpcConnectionLoop()
    }

    // Compatibility stubs
    fun setEnabled(context: Context, enabled: Boolean) {
        _isEnabled.value = true // Always remains enabled
    }

    fun setToken(context: Context, token: String) {
        // No token required for PC Discord RPC
    }

    private fun startRpcConnectionLoop() {
        connectionLoopJob?.cancel()
        connectionLoopJob = scope.launch {
            while (isActive) {
                if (!_isConnected.value || activeWebSocket == null) {
                    connectLocalRpc()
                }
                delay(8000L)
            }
        }
    }

    private fun connectLocalRpc() {
        val client = okHttpClient ?: return
        // Discord RPC listens on local ports 6463 to 6472
        for (port in 6463..6472) {
            if (_isConnected.value) break
            try {
                val url = "ws://127.0.0.1:$port/?v=1&client_id=$CLIENT_ID&encoding=json"
                val request = Request.Builder()
                    .url(url)
                    .header("Origin", "https://discord.com")
                    .build()

                val latch = java.util.concurrent.CountDownLatch(1)
                var connectedOnPort = false

                val ws = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.d(TAG, "Connected to Discord RPC on port $port")
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            val json = JSONObject(text)
                            val cmd = json.optString("cmd", "")
                            val evt = json.optString("evt", "")
                            if (cmd == "DISPATCH" && evt == "READY") {
                                Log.d(TAG, "Discord RPC Handshake successful on port $port!")
                                activeWebSocket = webSocket
                                _isConnected.value = true
                                connectedOnPort = true
                                latch.countDown()
                                updatePresence(PlaybackController.currentTrack.value, PlaybackController.isPlaying.value)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing RPC message: ${e.message}")
                        }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        if (activeWebSocket == webSocket) {
                            activeWebSocket = null
                            _isConnected.value = false
                        }
                        latch.countDown()
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        if (activeWebSocket == webSocket) {
                            activeWebSocket = null
                            _isConnected.value = false
                        }
                        latch.countDown()
                    }
                })

                // Wait up to 1.5 seconds for response on this port
                latch.await(1500, TimeUnit.MILLISECONDS)
                if (connectedOnPort) {
                    break
                } else {
                    try { ws.cancel() } catch (e: Exception) {}
                }
            } catch (e: Exception) {
                // Try next port
            }
        }
    }

    fun updatePresence(track: VideoItem?, isPlaying: Boolean) {
        val ws = activeWebSocket ?: return
        try {
            val nonce = nonceCounter.incrementAndGet().toString()
            val payload = JSONObject().apply {
                put("cmd", "SET_ACTIVITY")
                val args = JSONObject().apply {
                    put("pid", Process.myPid())
                    if (track != null) {
                        val activity = JSONObject().apply {
                            put("type", 2) // 2 = Listening to
                            put("details", track.title.take(128))

                            val artistName = track.artist.ifBlank { track.channelName }.take(120)
                            put("state", if (isPlaying) "by $artistName" else "by $artistName (Paused)")

                            // Spotify-style live scrubber timestamps (start and end)
                            val curSec = PlaybackController.currentTimeSeconds.value
                            var durSec = PlaybackController.durationSeconds.value
                            if (durSec <= 0f && track.duration.isNotBlank()) {
                                durSec = parseDurationSeconds(track.duration)
                            }

                            if (isPlaying) {
                                val now = System.currentTimeMillis()
                                val startMs = now - (curSec * 1000f).toLong()
                                val endMs = if (durSec > 0f) (startMs + (durSec * 1000f).toLong()) else null

                                val timestamps = JSONObject().apply {
                                    put("start", startMs)
                                    // Including 'end' renders the live Spotify progress bar (0:45 ──●───── 3:30)
                                    if (endMs != null && endMs > startMs) {
                                        put("end", endMs)
                                    }
                                }
                                put("timestamps", timestamps)
                            }

                            // High-Resolution Cover Art
                            val thumb = when {
                                track.thumbnailUrl.isNotBlank() && track.thumbnailUrl.startsWith("http") && !track.thumbnailUrl.contains("default.jpg") -> track.thumbnailUrl
                                track.id.length == 11 -> "https://i.ytimg.com/vi/${track.id}/hqdefault.jpg"
                                track.thumbnailUrl.isNotBlank() && track.thumbnailUrl.startsWith("http") -> track.thumbnailUrl
                                else -> "https://img.youtube.com/vi/${track.id}/hqdefault.jpg"
                            }

                            val albumOrSong = if (track.album.isNotBlank()) track.album else track.title
                            put("assets", JSONObject().apply {
                                put("large_image", thumb)
                                put("large_text", albumOrSong.take(128))
                                put("small_image", "https://raw.githubusercontent.com/Pawas44/AURALIX/main/android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png")
                                put("small_text", if (isPlaying) "Playing on AURALIX" else "Paused on AURALIX")
                            })

                            // Telegram link button
                            val buttons = JSONArray().apply {
                                put(JSONObject().apply {
                                    put("label", "Join Telegram")
                                    put("url", TELEGRAM_URL)
                                })
                                put(JSONObject().apply {
                                    put("label", "Listen on AURALIX")
                                    put("url", TELEGRAM_URL)
                                })
                            }
                            put("buttons", buttons)
                        }
                        put("activity", activity)
                    } else {
                        put("activity", JSONObject.NULL)
                    }
                }
                put("args", args)
                put("nonce", nonce)
            }

            ws.send(payload.toString())
            Log.d(TAG, "Sent Spotify-style SET_ACTIVITY for ${track?.title} (playing=$isPlaying)")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating Discord RPC activity: ${e.message}")
        }
    }

    private fun parseDurationSeconds(durationStr: String): Float {
        return try {
            val parts = durationStr.trim().split(":").mapNotNull { it.toIntOrNull() }
            when (parts.size) {
                3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]).toFloat()
                2 -> (parts[0] * 60 + parts[1]).toFloat()
                1 -> parts[0].toFloat()
                else -> 0f
            }
        } catch (e: Exception) {
            0f
        }
    }
}
