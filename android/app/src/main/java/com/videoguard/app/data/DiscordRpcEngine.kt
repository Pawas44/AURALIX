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
 * Default is ON with no off option, and links to Telegram: https://t.me/auralix_music
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

        // Observe playback state changes and immediately push rich presence
        scope.launch {
            PlaybackController.currentTrack.collect { track ->
                if (_isConnected.value) {
                    updatePresence(track, PlaybackController.isPlaying.value)
                }
            }
        }

        scope.launch {
            PlaybackController.isPlaying.collect { isPlaying ->
                if (_isConnected.value) {
                    updatePresence(PlaybackController.currentTrack.value, isPlaying)
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
                    if (track != null && isPlaying) {
                        val activity = JSONObject().apply {
                            put("type", 2) // Listening to
                            put("details", track.title.take(128))
                            put("state", "by " + track.artist.ifBlank { track.channelName }.take(128))

                            val curSec = PlaybackController.currentTimeSeconds.value
                            val startMs = System.currentTimeMillis() - (curSec * 1000).toLong()
                            put("timestamps", JSONObject().apply {
                                put("start", startMs)
                            })

                            val thumb = if (track.thumbnailUrl.isNotBlank() && !track.thumbnailUrl.contains("default")) {
                                track.thumbnailUrl
                            } else {
                                "https://img.youtube.com/vi/${track.id}/hqdefault.jpg"
                            }

                            put("assets", JSONObject().apply {
                                put("large_image", thumb)
                                put("large_text", "AURALIX Music")
                                put("small_image", "play")
                                put("small_text", "Listening on AURALIX")
                            })

                            // Telegram link button as requested
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
            Log.d(TAG, "Sent SET_ACTIVITY for ${track?.title}")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating Discord RPC activity: ${e.message}")
        }
    }
}
