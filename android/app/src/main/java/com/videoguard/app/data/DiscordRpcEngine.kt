package com.videoguard.app.data

import android.content.Context
import android.os.Handler
import android.os.Looper
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

object DiscordRpcEngine {
    private const val TAG = "DiscordRpcEngine"
    private const val PREFS_NAME = "auralix_discord_prefs"
    private const val KEY_ENABLED = "discord_enabled"
    private const val KEY_TOKEN = "discord_token"

    private const val GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _userToken = MutableStateFlow("")
    val userToken: StateFlow<String> = _userToken.asStateFlow()

    private var okHttpClient: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var lastSequenceNumber: Int? = null
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedEnabled = prefs.getBoolean(KEY_ENABLED, false)
        val savedToken = prefs.getString(KEY_TOKEN, "") ?: ""

        _userToken.value = savedToken
        _isEnabled.value = savedEnabled

        okHttpClient = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()

        // Observe playback state changes and push rich presence
        scope.launch {
            PlaybackController.currentTrack.collect { track ->
                if (_isEnabled.value && _isConnected.value) {
                    updatePresence(track, PlaybackController.isPlaying.value)
                }
            }
        }

        scope.launch {
            PlaybackController.isPlaying.collect { isPlaying ->
                if (_isEnabled.value && _isConnected.value) {
                    updatePresence(PlaybackController.currentTrack.value, isPlaying)
                }
            }
        }

        if (savedEnabled && savedToken.isNotBlank()) {
            connectGateway()
        }
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        _isEnabled.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()

        if (enabled) {
            connectGateway()
        } else {
            disconnectGateway()
        }
    }

    fun setToken(context: Context, token: String) {
        val clean = token.trim()
        _userToken.value = clean
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, clean)
            .apply()

        if (_isEnabled.value) {
            disconnectGateway()
            if (clean.isNotBlank()) {
                connectGateway()
            }
        }
    }

    private fun connectGateway() {
        val token = _userToken.value
        if (token.isBlank()) {
            _isConnected.value = false
            return
        }

        disconnectGateway()

        val client = okHttpClient ?: OkHttpClient()
        val request = Request.Builder()
            .url(GATEWAY_URL)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "Discord Gateway WebSocket connection established")
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleGatewayMessage(ws, text)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Discord Gateway closed: $code / $reason")
                _isConnected.value = false
                heartbeatJob?.cancel()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Discord Gateway error: ${t.message}")
                _isConnected.value = false
                heartbeatJob?.cancel()

                // Auto-reconnect if enabled after brief backoff
                scope.launch {
                    delay(5000L)
                    if (_isEnabled.value && _userToken.value.isNotBlank() && !_isConnected.value) {
                        connectGateway()
                    }
                }
            }
        })
    }

    private fun disconnectGateway() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        try {
            webSocket?.close(1000, "Normal Closure")
        } catch (e: Exception) {}
        webSocket = null
        _isConnected.value = false
    }

    private fun handleGatewayMessage(ws: WebSocket, raw: String) {
        try {
            val json = JSONObject(raw)
            val op = json.optInt("op", -1)
            val s = if (json.has("s") && !json.isNull("s")) json.optInt("s") else null
            if (s != null) lastSequenceNumber = s

            when (op) {
                10 -> { // Hello -> extract heartbeat interval and start heartbeats + identify
                    val d = json.optJSONObject("d")
                    val interval = d?.optLong("heartbeat_interval", 41250L) ?: 41250L
                    startHeartbeat(ws, interval)
                    sendIdentify(ws)
                }
                1 -> { // Server requested immediate heartbeat
                    sendHeartbeat(ws)
                }
                0 -> { // Dispatch
                    val t = json.optString("t", "")
                    if (t == "READY") {
                        _isConnected.value = true
                        Log.d(TAG, "Discord Gateway Identified and Ready! User connected.")
                        updatePresence(PlaybackController.currentTrack.value, PlaybackController.isPlaying.value)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling gateway message", e)
        }
    }

    private fun startHeartbeat(ws: WebSocket, intervalMs: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(intervalMs)
                sendHeartbeat(ws)
            }
        }
    }

    private fun sendHeartbeat(ws: WebSocket) {
        try {
            val payload = JSONObject().apply {
                put("op", 1)
                put("d", if (lastSequenceNumber != null) lastSequenceNumber else JSONObject.NULL)
            }
            ws.send(payload.toString())
        } catch (e: Exception) {}
    }

    private fun sendIdentify(ws: WebSocket) {
        try {
            val token = _userToken.value
            val identify = JSONObject().apply {
                put("op", 2)
                put("d", JSONObject().apply {
                    put("token", token)
                    put("capabilities", 16381)
                    put("properties", JSONObject().apply {
                        put("os", "Android")
                        put("browser", "AURALIX Music")
                        put("device", "AURALIX App")
                    })
                    put("presence", buildPresenceData(
                        PlaybackController.currentTrack.value,
                        PlaybackController.isPlaying.value
                    ))
                })
            }
            ws.send(identify.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending identify to Discord", e)
        }
    }

    private fun buildPresenceData(track: VideoItem?, isPlaying: Boolean): JSONObject {
        val root = JSONObject().apply {
            put("status", "online")
            put("afk", false)
            put("since", JSONObject.NULL)
        }

        if (track != null && isPlaying) {
            val activities = JSONArray()
            val activity = JSONObject().apply {
                put("name", "AURALIX")
                put("type", 2) // 2 = Listening to
                put("details", track.title)
                put("state", "by " + track.artist.ifBlank { track.channelName })

                val curSec = PlaybackController.currentTimeSeconds.value
                val startMs = System.currentTimeMillis() - (curSec * 1000).toLong()
                put("timestamps", JSONObject().apply {
                    put("start", startMs)
                })

                val thumb = track.thumbnailUrl.ifBlank { "https://img.youtube.com/vi/${track.id}/hqdefault.jpg" }
                put("assets", JSONObject().apply {
                    put("large_image", thumb)
                    put("large_text", "AURALIX Full HD")
                })
            }
            activities.put(activity)
            root.put("activities", activities)
        } else {
            root.put("activities", JSONArray())
        }

        return root
    }

    private fun updatePresence(track: VideoItem?, isPlaying: Boolean) {
        val ws = webSocket ?: return
        if (!_isConnected.value) return

        try {
            val presenceUpdate = JSONObject().apply {
                put("op", 3)
                put("d", buildPresenceData(track, isPlaying))
            }
            ws.send(presenceUpdate.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error updating Discord presence", e)
        }
    }
}
