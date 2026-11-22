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
 * Universal Discord Rich Presence (RPC) Engine.
 * Dual-Mode Architecture:
 * 1. Local PC RPC (Tokenless): Scans 127.0.0.1 (PC/WSA) and 10.0.2.2 (Android Emulator to PC) across ports 6463-6472.
 * 2. Discord Gateway (for Android Phones): Connects directly to wss://gateway.discord.gg when token is saved.
 * Shows Spotify-style Rich Presence:
 * - "Listening to AURALIX"
 * - Song Title
 * - Artist Name & Album
 * - High-Resolution Song Cover Art
 * - Live animated progress bar with start & end timestamps (e.g. 0:45 ──●───── 3:30)
 * - Official Telegram link: https://t.me/auralix_music
 */
object DiscordRpcEngine {
    private const val TAG = "DiscordRpcEngine"
    const val CLIENT_ID = "1358372087017767073"
    const val TELEGRAM_URL = "https://t.me/auralix_music"
    private const val PREFS_NAME = "auralix_discord_prefs"
    private const val KEY_TOKEN = "discord_token"
    private const val GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // RPC is ON by default with no off option
    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectionMode = MutableStateFlow("Disconnected") // "Local PC RPC" or "Discord Gateway"
    val connectionMode: StateFlow<String> = _connectionMode.asStateFlow()

    private val _userToken = MutableStateFlow("")
    val userToken: StateFlow<String> = _userToken.asStateFlow()

    private var okHttpClient: OkHttpClient? = null
    private var localRpcWs: WebSocket? = null
    private var gatewayWs: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var lastGatewaySeq: Int? = null
    private var connectionLoopJob: Job? = null
    private val nonceCounter = AtomicInteger(1)
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedToken = prefs.getString(KEY_TOKEN, "") ?: ""
        _userToken.value = savedToken
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

        // 2. Observe play/pause
        scope.launch {
            PlaybackController.isPlaying.collect { isPlaying ->
                if (_isConnected.value) {
                    updatePresence(PlaybackController.currentTrack.value, isPlaying)
                }
            }
        }

        // 3. Observe duration changes
        scope.launch {
            PlaybackController.durationSeconds.collect { dur ->
                if (_isConnected.value && dur > 0f) {
                    updatePresence(PlaybackController.currentTrack.value, PlaybackController.isPlaying.value)
                }
            }
        }

        // 4. Observe seek / scrubbing jumps
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

        startConnectionLoop(context)
    }

    fun setToken(context: Context, token: String) {
        val clean = token.trim()
        _userToken.value = clean
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, clean)
            .apply()

        if (clean.isNotBlank()) {
            scope.launch {
                disconnectAll()
                connectGateway()
            }
        }
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        _isEnabled.value = true // Always remains enabled
    }

    private fun startConnectionLoop(context: Context) {
        connectionLoopJob?.cancel()
        connectionLoopJob = scope.launch {
            while (isActive) {
                if (!_isConnected.value) {
                    // 1. First attempt tokenless Local PC RPC (127.0.0.1 and 10.0.2.2 emulator host)
                    connectLocalRpc()

                    // 2. If local RPC failed and user has a token, connect directly to Discord Gateway
                    if (!_isConnected.value && _userToken.value.isNotBlank()) {
                        connectGateway()
                    }
                }
                delay(7000L)
            }
        }
    }

    private fun disconnectAll() {
        try { localRpcWs?.cancel(); localRpcWs = null } catch (e: Exception) {}
        try { gatewayWs?.cancel(); gatewayWs = null } catch (e: Exception) {}
        heartbeatJob?.cancel()
        heartbeatJob = null
        _isConnected.value = false
        _connectionMode.value = "Disconnected"
    }

    private fun connectLocalRpc() {
        val client = okHttpClient ?: return
        val targetHosts = listOf("127.0.0.1", "10.0.2.2")

        for (host in targetHosts) {
            if (_isConnected.value) break
            for (port in 6463..6472) {
                if (_isConnected.value) break
                try {
                    val url = "ws://$host:$port/?v=1&client_id=$CLIENT_ID&encoding=json"
                    val request = Request.Builder()
                        .url(url)
                        .header("Origin", "https://discord.com")
                        .build()

                    val latch = java.util.concurrent.CountDownLatch(1)
                    var connected = false

                    val ws = client.newWebSocket(request, object : WebSocketListener() {
                        override fun onMessage(webSocket: WebSocket, text: String) {
                            try {
                                val json = JSONObject(text)
                                val cmd = json.optString("cmd", "")
                                val evt = json.optString("evt", "")
                                if (cmd == "DISPATCH" && evt == "READY") {
                                    Log.d(TAG, "Local Discord RPC Ready on $host:$port!")
                                    localRpcWs = webSocket
                                    _isConnected.value = true
                                    _connectionMode.value = "Local PC RPC ($host)"
                                    connected = true
                                    latch.countDown()
                                    updatePresence(PlaybackController.currentTrack.value, PlaybackController.isPlaying.value)
                                }
                            } catch (e: Exception) {}
                        }

                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                            if (localRpcWs == webSocket) {
                                localRpcWs = null
                                _isConnected.value = false
                                _connectionMode.value = "Disconnected"
                            }
                            latch.countDown()
                        }

                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            if (localRpcWs == webSocket) {
                                localRpcWs = null
                                _isConnected.value = false
                                _connectionMode.value = "Disconnected"
                            }
                            latch.countDown()
                        }
                    })

                    latch.await(1000, TimeUnit.MILLISECONDS)
                    if (connected) {
                        break
                    } else {
                        try { ws.cancel() } catch (e: Exception) {}
                    }
                } catch (e: Exception) {}
            }
        }
    }

    private fun connectGateway() {
        val token = _userToken.value
        if (token.isBlank() || _isConnected.value) return

        val client = okHttpClient ?: return
        val request = Request.Builder().url(GATEWAY_URL).build()

        gatewayWs = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "Connected to Discord Gateway")
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val op = json.optInt("op", -1)
                    val s = if (json.has("s") && !json.isNull("s")) json.optInt("s") else null
                    if (s != null) lastGatewaySeq = s

                    when (op) {
                        10 -> { // Hello
                            val d = json.optJSONObject("d")
                            val interval = d?.optLong("heartbeat_interval", 41250L) ?: 41250L
                            startHeartbeat(ws, interval)
                            sendGatewayIdentify(ws)
                        }
                        1 -> sendGatewayHeartbeat(ws)
                        0 -> {
                            val t = json.optString("t", "")
                            if (t == "READY") {
                                Log.d(TAG, "Discord Gateway Identified! Connected worldwide.")
                                _isConnected.value = true
                                _connectionMode.value = "Discord Gateway (Mobile)"
                                updatePresence(PlaybackController.currentTrack.value, PlaybackController.isPlaying.value)
                            }
                        }
                    }
                } catch (e: Exception) {}
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (gatewayWs == ws) {
                    gatewayWs = null
                    _isConnected.value = false
                    _connectionMode.value = "Disconnected"
                }
                heartbeatJob?.cancel()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                if (gatewayWs == ws) {
                    gatewayWs = null
                    _isConnected.value = false
                    _connectionMode.value = "Disconnected"
                }
                heartbeatJob?.cancel()
            }
        })
    }

    private fun startHeartbeat(ws: WebSocket, intervalMs: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(intervalMs)
                sendGatewayHeartbeat(ws)
            }
        }
    }

    private fun sendGatewayHeartbeat(ws: WebSocket) {
        try {
            val payload = JSONObject().apply {
                put("op", 1)
                put("d", if (lastGatewaySeq != null) lastGatewaySeq else JSONObject.NULL)
            }
            ws.send(payload.toString())
        } catch (e: Exception) {}
    }

    private fun sendGatewayIdentify(ws: WebSocket) {
        try {
            val identify = JSONObject().apply {
                put("op", 2)
                put("d", JSONObject().apply {
                    put("token", _userToken.value)
                    put("capabilities", 16381)
                    put("properties", JSONObject().apply {
                        put("os", "Android")
                        put("browser", "AURALIX Music")
                        put("device", "AURALIX App")
                    })
                    put("presence", buildGatewayPresenceData(
                        PlaybackController.currentTrack.value,
                        PlaybackController.isPlaying.value
                    ))
                })
            }
            ws.send(identify.toString())
        } catch (e: Exception) {}
    }

    fun updatePresence(track: VideoItem?, isPlaying: Boolean) {
        if (!_isConnected.value) return

        // 1. Send via Local PC RPC if active
        val lws = localRpcWs
        if (lws != null) {
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
                                        if (endMs != null && endMs > startMs) {
                                            put("end", endMs)
                                        }
                                    }
                                    put("timestamps", timestamps)
                                }

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
                lws.send(payload.toString())
            } catch (e: Exception) {}
        }

        // 2. Send via Discord Gateway if active
        val gws = gatewayWs
        if (gws != null) {
            try {
                val presenceUpdate = JSONObject().apply {
                    put("op", 3)
                    put("d", buildGatewayPresenceData(track, isPlaying))
                }
                gws.send(presenceUpdate.toString())
            } catch (e: Exception) {}
        }
    }

    private fun buildGatewayPresenceData(track: VideoItem?, isPlaying: Boolean): JSONObject {
        val root = JSONObject().apply {
            put("status", "online")
            put("afk", false)
            put("since", JSONObject.NULL)
        }

        if (track != null) {
            val activities = JSONArray()
            val activity = JSONObject().apply {
                put("name", "AURALIX")
                put("type", 2) // 2 = Listening to
                put("details", track.title.take(128))
                val artistName = track.artist.ifBlank { track.channelName }.take(120)
                put("state", if (isPlaying) "by $artistName" else "by $artistName (Paused)")

                val curSec = PlaybackController.currentTimeSeconds.value
                var durSec = PlaybackController.durationSeconds.value
                if (durSec <= 0f && track.duration.isNotBlank()) {
                    durSec = parseDurationSeconds(track.duration)
                }

                if (isPlaying) {
                    val now = System.currentTimeMillis()
                    val startMs = now - (curSec * 1000f).toLong()
                    val endMs = if (durSec > 0f) (startMs + (durSec * 1000f).toLong()) else null

                    put("timestamps", JSONObject().apply {
                        put("start", startMs)
                        if (endMs != null && endMs > startMs) {
                            put("end", endMs)
                        }
                    })
                }

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

                val buttons = JSONArray().apply {
                    put("Join Telegram")
                    put("Listen on AURALIX")
                }
                put("buttons", buttons)

                put("metadata", JSONObject().apply {
                    val urls = JSONArray().apply {
                        put(TELEGRAM_URL)
                        put(TELEGRAM_URL)
                    }
                    put("button_urls", urls)
                })
            }
            activities.put(activity)
            root.put("activities", activities)
        } else {
            root.put("activities", JSONArray())
        }

        return root
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
