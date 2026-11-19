package com.videoguard.app.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.videoguard.app.model.VideoItem
import com.videoguard.app.service.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

data class RoomParticipant(
    val userId: String,
    val username: String,
    val isHost: Boolean,
    val isConnected: Boolean = true
)

data class JamChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val username: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class FloatingReaction(
    val id: String = UUID.randomUUID().toString(),
    val emoji: String,
    val startXFraction: Float = (15..85).random() / 100f
)

enum class JamConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

object ListenTogetherEngine {
    private const val TAG = "ListenTogetherEngine"
    private const val PREFS_NAME = "auralix_jam_prefs"
    private const val KEY_SAVED_USERNAME = "jam_saved_username"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _connectionState = MutableStateFlow(JamConnectionState.DISCONNECTED)
    val connectionState: StateFlow<JamConnectionState> = _connectionState.asStateFlow()

    private val _isInRoom = MutableStateFlow(false)
    val isInRoom: StateFlow<Boolean> = _isInRoom.asStateFlow()

    private val _isHost = MutableStateFlow(false)
    val isHost: StateFlow<Boolean> = _isHost.asStateFlow()

    private val _roomCode = MutableStateFlow<String?>(null)
    val roomCode: StateFlow<String?> = _roomCode.asStateFlow()

    private val _hostUsername = MutableStateFlow<String?>(null)
    val hostUsername: StateFlow<String?> = _hostUsername.asStateFlow()

    private val _participants = MutableStateFlow<List<RoomParticipant>>(emptyList())
    val participants: StateFlow<List<RoomParticipant>> = _participants.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<JamChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<JamChatMessage>> = _chatMessages.asStateFlow()

    private val _floatingReactions = MutableStateFlow<List<FloatingReaction>>(emptyList())
    val floatingReactions: StateFlow<List<FloatingReaction>> = _floatingReactions.asStateFlow()

    private val _myUsername = MutableStateFlow("Auralix Fan")
    val myUsername: StateFlow<String> = _myUsername.asStateFlow()

    private val _myUserId = MutableStateFlow<String?>(null)
    val myUserId: StateFlow<String?> = _myUserId.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _autoApprove = MutableStateFlow(true)
    val autoApprove: StateFlow<Boolean> = _autoApprove.asStateFlow()

    // Internal lock flag to avoid playback update echo loops
    @Volatile
    private var isApplyingRemoteSync = false

    private var okHttpClient: OkHttpClient? = null
    private var activeWebSocket: WebSocket? = null
    private var pingJob: Job? = null

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedName = prefs.getString(KEY_SAVED_USERNAME, null)
        if (!savedName.isNullOrBlank()) {
            _myUsername.value = savedName
        } else {
            val account = AccountRepository.currentAccount.value
            if (account != null && account.name.isNotBlank()) {
                _myUsername.value = account.name
            }
        }

        okHttpClient = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // infinite for websocket
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    fun setUsername(context: Context, name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotBlank()) {
            _myUsername.value = trimmed
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SAVED_USERNAME, trimmed)
                .apply()
        }
    }

    fun toggleAutoApprove(enabled: Boolean) {
        _autoApprove.value = enabled
    }

    /**
     * Generate 6-char alphanumeric room code
     */
    private fun generateRoomCode(): String {
        val chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        return (1..6).map { chars.random() }.joinToString("")
    }

    /**
     * Create a new Listen Together room as Host
     */
    fun createRoom(username: String) {
        _errorMessage.value = null
        val cleanName = username.trim().ifBlank { _myUsername.value }
        _myUsername.value = cleanName

        val newCode = generateRoomCode()
        val newUserId = "usr_" + UUID.randomUUID().toString().take(8)

        _roomCode.value = newCode
        _myUserId.value = newUserId
        _isHost.value = true
        _isInRoom.value = true
        _hostUsername.value = cleanName
        _participants.value = listOf(
            RoomParticipant(
                userId = newUserId,
                username = cleanName,
                isHost = true,
                isConnected = true
            )
        )
        _chatMessages.value = listOf(
            JamChatMessage(
                userId = "system",
                username = "Auralix Jam",
                message = "🚀 Room created! Code: $newCode. Share this code with friends to listen together!"
            )
        )

        connectToRoom(newCode, isHostJoin = true)
    }

    /**
     * Join an existing Listen Together room with 6-character code
     */
    fun joinRoom(code: String, username: String) {
        _errorMessage.value = null
        val cleanCode = code.trim().uppercase()
        if (cleanCode.length < 3) {
            _errorMessage.value = "Please enter a valid room code"
            return
        }
        val cleanName = username.trim().ifBlank { _myUsername.value }
        _myUsername.value = cleanName

        val newUserId = "usr_" + UUID.randomUUID().toString().take(8)
        _roomCode.value = cleanCode
        _myUserId.value = newUserId
        _isHost.value = false
        _isInRoom.value = true
        _participants.value = listOf(
            RoomParticipant(
                userId = newUserId,
                username = cleanName,
                isHost = false,
                isConnected = true
            )
        )
        _chatMessages.value = listOf(
            JamChatMessage(
                userId = "system",
                username = "Auralix Jam",
                message = "Connecting to Jam $cleanCode..."
            )
        )

        connectToRoom(cleanCode, isHostJoin = false)
    }

    /**
     * Leave current room and disconnect
     */
    fun leaveRoom() {
        try {
            if (_isInRoom.value) {
                publishEvent("user_left", JSONObject().apply {
                    put("userId", _myUserId.value)
                    put("username", _myUsername.value)
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error leaving room", e)
        }
        resetRoomState()
        disconnectSocket()
    }

    /**
     * Send real-time chat message to the room
     */
    fun sendChatMessage(message: String) {
        val trimmed = message.trim()
        if (!_isInRoom.value || trimmed.isBlank()) return

        val myId = _myUserId.value ?: "me"
        val myName = _myUsername.value
        val chatMsg = JamChatMessage(
            userId = myId,
            username = myName,
            message = trimmed,
            timestamp = System.currentTimeMillis()
        )
        // Add to local chat immediately
        _chatMessages.value = _chatMessages.value + chatMsg

        // Publish to room
        try {
            val payload = JSONObject().apply {
                put("message", trimmed)
                put("timestamp", chatMsg.timestamp)
            }
            publishEvent("chat", payload)
        } catch (e: Exception) {
            Log.e(TAG, "Error publishing chat message", e)
        }
    }

    /**
     * Send emoji reaction that triggers floating animation across all devices
     */
    fun sendReaction(emoji: String) {
        if (!_isInRoom.value || emoji.isBlank()) return

        // 1. Trigger local floating animation
        triggerFloatingReaction(emoji)

        // 2. Publish reaction to all peers in room
        try {
            val payload = JSONObject().apply {
                put("emoji", emoji)
                put("timestamp", System.currentTimeMillis())
            }
            publishEvent("reaction", payload)
        } catch (e: Exception) {
            Log.e(TAG, "Error publishing reaction", e)
        }
    }

    /**
     * Trigger floating reaction animation locally
     */
    fun triggerFloatingReaction(emoji: String) {
        val reaction = FloatingReaction(emoji = emoji)
        _floatingReactions.value = _floatingReactions.value + reaction
    }

    /**
     * Remove floating reaction once animation completes
     */
    fun removeFloatingReaction(id: String) {
        _floatingReactions.value = _floatingReactions.value.filter { it.id != id }
    }

    fun approveJoin(userId: String) {
        // Auto-approved in open collaborative Jam
    }

    fun kickUser(userId: String) {
        if (!_isHost.value) return
        _participants.value = _participants.value.filter { it.userId != userId }
    }

    // =========================================================================
    // Collaborative Playback: Any Joined User Can Control Playback!
    // =========================================================================

    fun onHostTrackChanged(track: VideoItem) {
        // Any joined user in the room can broadcast track changes
        if (!_isInRoom.value || isApplyingRemoteSync) return
        try {
            val durationMs = parseDurationMs(track.duration)
            val payload = JSONObject().apply {
                put("track", JSONObject().apply {
                    put("id", track.id)
                    put("title", track.title)
                    put("artist", track.channelName.ifBlank { track.artist })
                    put("album", track.album)
                    put("duration", durationMs)
                    put("thumbnail", track.thumbnailUrl)
                })
            }
            publishEvent("change_track", payload)
            Log.d(TAG, "Jam user broadcasted change_track: ${track.title} (${track.id})")
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting track change", e)
        }
    }

    fun onHostPlayStateChanged(isPlaying: Boolean, positionMs: Long) {
        if (!_isInRoom.value || isApplyingRemoteSync) return
        try {
            val track = PlaybackController.currentTrack.value
            val payload = JSONObject().apply {
                put("isPlaying", isPlaying)
                put("positionMs", positionMs)
                if (track != null) put("track_id", track.id)
            }
            publishEvent("play_state", payload)
            Log.d(TAG, "Jam user broadcasted play_state ($isPlaying) at $positionMs ms")
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting play state", e)
        }
    }

    fun onHostSeek(positionMs: Long) {
        if (!_isInRoom.value || isApplyingRemoteSync) return
        try {
            val track = PlaybackController.currentTrack.value
            val payload = JSONObject().apply {
                put("positionMs", positionMs)
                if (track != null) put("track_id", track.id)
            }
            publishEvent("seek", payload)
            Log.d(TAG, "Jam user broadcasted seek to $positionMs ms")
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting seek", e)
        }
    }

    // =========================================================================
    // WebSocket & HTTP Pub/Sub Transport
    // =========================================================================

    private fun getTopicForCode(code: String): String {
        return "auralix_jam_${code.lowercase().trim()}"
    }

    private fun publishEvent(type: String, payload: JSONObject) {
        val code = _roomCode.value ?: return
        val topic = getTopicForCode(code)
        val myId = _myUserId.value ?: "usr"
        val myName = _myUsername.value

        val root = JSONObject().apply {
            put("type", type)
            put("userId", myId)
            put("username", myName)
            put("payload", payload)
            put("timestamp", System.currentTimeMillis())
        }

        val client = okHttpClient ?: OkHttpClient()
        val body = root.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("https://ntfy.sh/$topic")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to publish $type to ntfy topic $topic", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }

    private fun connectToRoom(code: String, isHostJoin: Boolean) {
        disconnectSocket()
        _connectionState.value = JamConnectionState.CONNECTING

        val topic = getTopicForCode(code)
        val wsUrl = "wss://ntfy.sh/$topic/ws"

        val client = okHttpClient ?: OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build().also { okHttpClient = it }

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        activeWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected successfully to Jam topic: $topic")
                _connectionState.value = JamConnectionState.CONNECTED
                startHeartbeat(webSocket)

                if (!isHostJoin) {
                    // Send user_joined event so room knows we're here
                    val myId = _myUserId.value ?: "usr"
                    val myName = _myUsername.value
                    publishEvent("user_joined", JSONObject().apply {
                        put("userId", myId)
                        put("username", myName)
                    })
                } else {
                    // If Host already has a track playing, broadcast it immediately!
                    val current = PlaybackController.currentTrack.value
                    if (current != null) {
                        mainHandler.postDelayed({
                            onHostTrackChanged(current)
                            if (PlaybackController.isPlaying.value) {
                                val posMs = (PlaybackController.currentTimeSeconds.value * 1000).toLong()
                                onHostPlayStateChanged(true, posMs)
                            }
                        }, 500)
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code / $reason")
                _connectionState.value = JamConnectionState.DISCONNECTED
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                _connectionState.value = JamConnectionState.ERROR
                _errorMessage.value = "Jam connection issue: ${t.localizedMessage ?: "Network error"}"
            }
        })
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val root = JSONObject(text)
            val event = root.optString("event")

            // ntfy.sh pushes: { "event": "message", "message": "<our_json_string>" }
            val rawMessage = if (event == "message") {
                root.optString("message")
            } else if (root.has("type")) {
                text
            } else {
                return
            }

            val msgJson = JSONObject(rawMessage)
            val type = msgJson.optString("type")
            val senderId = msgJson.optString("userId")
            val senderName = msgJson.optString("username")
            val payload = msgJson.optJSONObject("payload") ?: JSONObject()

            // Echo filter: ignore messages sent by self
            if (senderId == _myUserId.value) {
                return
            }

            when (type) {
                "change_track" -> {
                    val trackObj = payload.optJSONObject("track")
                    if (trackObj != null) {
                        val trackId = trackObj.optString("id")
                        val title = trackObj.optString("title", "Jam Track")
                        val artist = trackObj.optString("artist", "")
                        val thumb = trackObj.optString("thumbnail", "")
                        val durMs = trackObj.optLong("duration", 180000L)

                        val sysMsg = JamChatMessage(
                            userId = "system",
                            username = "Auralix Jam",
                            message = "🎵 $senderName changed track to: $title"
                        )
                        _chatMessages.value = _chatMessages.value + sysMsg

                        mainHandler.post {
                            applyGuestTrackSync(
                                trackId = trackId,
                                title = title,
                                artist = artist,
                                thumbnail = thumb,
                                durationMs = durMs,
                                targetPositionSec = 0f,
                                shouldPlay = true
                            )
                        }
                    }
                }

                "play_state" -> {
                    val isPlaying = payload.optBoolean("isPlaying", false)
                    val posMs = payload.optLong("positionMs", -1L)

                    mainHandler.post {
                        isApplyingRemoteSync = true
                        try {
                            if (posMs >= 0) {
                                val currentPos = PlaybackController.currentTimeSeconds.value
                                val targetSec = posMs / 1000f
                                if (kotlin.math.abs(currentPos - targetSec) > 2.0f) {
                                    PlaybackController.seekTo(targetSec)
                                }
                            }
                            if (isPlaying) {
                                PlaybackController.play()
                            } else {
                                PlaybackController.pause()
                            }
                        } finally {
                            mainHandler.postDelayed({ isApplyingRemoteSync = false }, 600)
                        }
                    }
                }

                "seek" -> {
                    val posMs = payload.optLong("positionMs", -1L)
                    if (posMs >= 0) {
                        val targetSec = posMs / 1000f
                        mainHandler.post {
                            isApplyingRemoteSync = true
                            try {
                                PlaybackController.seekTo(targetSec)
                            } finally {
                                mainHandler.postDelayed({ isApplyingRemoteSync = false }, 600)
                            }
                        }
                    }
                }

                "chat" -> {
                    val msgText = payload.optString("message")
                    val ts = payload.optLong("timestamp", System.currentTimeMillis())
                    if (msgText.isNotBlank()) {
                        val chatObj = JamChatMessage(
                            userId = senderId,
                            username = senderName,
                            message = msgText,
                            timestamp = ts
                        )
                        _chatMessages.value = _chatMessages.value + chatObj
                    }
                }

                "reaction" -> {
                    val emoji = payload.optString("emoji")
                    if (emoji.isNotBlank()) {
                        mainHandler.post {
                            triggerFloatingReaction(emoji)
                        }
                    }
                }

                "user_joined" -> {
                    val currentList = _participants.value.toMutableList()
                    if (currentList.none { it.userId == senderId }) {
                        currentList.add(RoomParticipant(senderId, senderName, isHost = false))
                        _participants.value = currentList
                    }

                    val sysMsg = JamChatMessage(
                        userId = "system",
                        username = "Auralix Jam",
                        message = "🎉 $senderName joined the Jam"
                    )
                    _chatMessages.value = _chatMessages.value + sysMsg

                    // Broadcast current room playback state so the new joiner syncs immediately
                    val current = PlaybackController.currentTrack.value
                    if (current != null) {
                        val durMs = parseDurationMs(current.duration)
                        val posMs = (PlaybackController.currentTimeSeconds.value * 1000).toLong()
                        val syncPayload = JSONObject().apply {
                            put("hostUsername", _hostUsername.value ?: _myUsername.value)
                            put("isPlaying", PlaybackController.isPlaying.value)
                            put("positionMs", posMs)
                            put("track", JSONObject().apply {
                                put("id", current.id)
                                put("title", current.title)
                                put("artist", current.channelName.ifBlank { current.artist })
                                put("album", current.album)
                                put("duration", durMs)
                                put("thumbnail", current.thumbnailUrl)
                            })
                            val pArray = JSONArray()
                            _participants.value.forEach { p ->
                                pArray.put(JSONObject().apply {
                                    put("userId", p.userId)
                                    put("username", p.username)
                                    put("isHost", p.isHost)
                                })
                            }
                            put("participants", pArray)
                        }
                        publishEvent("sync_state", syncPayload)
                    }
                }

                "sync_state" -> {
                    val hostName = payload.optString("hostUsername")
                    if (hostName.isNotBlank()) {
                        _hostUsername.value = hostName
                    }

                    val pArray = payload.optJSONArray("participants")
                    if (pArray != null && pArray.length() > 0) {
                        val currentList = _participants.value.toMutableList()
                        for (i in 0 until pArray.length()) {
                            val pObj = pArray.getJSONObject(i)
                            val uid = pObj.optString("userId")
                            val uname = pObj.optString("username")
                            val isH = pObj.optBoolean("isHost", false)
                            if (currentList.none { it.userId == uid }) {
                                currentList.add(RoomParticipant(uid, uname, isH))
                            }
                        }
                        _participants.value = currentList
                    }

                    val trackObj = payload.optJSONObject("track")
                    val isPlaying = payload.optBoolean("isPlaying", false)
                    val posMs = payload.optLong("positionMs", 0L)

                    if (trackObj != null) {
                        val trackId = trackObj.optString("id")
                        val title = trackObj.optString("title", "Jam Track")
                        val artist = trackObj.optString("artist", "")
                        val thumb = trackObj.optString("thumbnail", "")
                        val durMs = trackObj.optLong("duration", 180000L)
                        val targetSec = (posMs / 1000f).coerceAtLeast(0f)

                        mainHandler.post {
                            applyGuestTrackSync(
                                trackId = trackId,
                                title = title,
                                artist = artist,
                                thumbnail = thumb,
                                durationMs = durMs,
                                targetPositionSec = targetSec,
                                shouldPlay = isPlaying
                            )
                        }
                    }
                }

                "user_left" -> {
                    _participants.value = _participants.value.filter { it.userId != senderId }
                    val sysMsg = JamChatMessage(
                        userId = "system",
                        username = "Auralix Jam",
                        message = "$senderName left the Jam"
                    )
                    _chatMessages.value = _chatMessages.value + sysMsg
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming WS message: $text", e)
        }
    }

    private fun applyGuestTrackSync(
        trackId: String,
        title: String,
        artist: String,
        thumbnail: String,
        durationMs: Long,
        targetPositionSec: Float,
        shouldPlay: Boolean
    ) {
        if (trackId.isBlank()) return

        isApplyingRemoteSync = true
        try {
            val durString = formatDurationMs(durationMs)
            val thumbUrl = thumbnail.ifBlank { "https://i.ytimg.com/vi/$trackId/hqdefault.jpg" }
            val videoItem = VideoItem(
                id = trackId,
                title = title,
                channelName = artist,
                channelAvatarUrl = "",
                thumbnailUrl = thumbUrl,
                views = "",
                uploadTime = "",
                duration = durString,
                isMusicTrack = true,
                artist = artist
            )

            val current = PlaybackController.currentTrack.value
            if (current == null || current.id != trackId) {
                PlaybackController.playTrack(videoItem)
            }

            if (targetPositionSec > 0.5f) {
                mainHandler.postDelayed({
                    PlaybackController.seekTo(targetPositionSec)
                    if (shouldPlay) PlaybackController.play() else PlaybackController.pause()
                }, 600)
            } else {
                if (shouldPlay) PlaybackController.play() else PlaybackController.pause()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying guest track sync", e)
        } finally {
            mainHandler.postDelayed({ isApplyingRemoteSync = false }, 1000)
        }
    }

    private fun startHeartbeat(ws: WebSocket) {
        pingJob?.cancel()
        pingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(20000)
                try {
                    val pingMsg = JSONObject().apply {
                        put("type", "ping")
                    }
                    ws.send(pingMsg.toString())
                } catch (e: Exception) {
                    break
                }
            }
        }
    }

    private fun disconnectSocket() {
        pingJob?.cancel()
        pingJob = null
        try {
            activeWebSocket?.close(1000, "Leaving")
        } catch (e: Exception) {}
        activeWebSocket = null
    }

    private fun resetRoomState() {
        _isInRoom.value = false
        _isHost.value = false
        _roomCode.value = null
        _hostUsername.value = null
        _participants.value = emptyList()
        _chatMessages.value = emptyList()
        _floatingReactions.value = emptyList()
    }

    private fun parseDurationMs(durationStr: String): Long {
        if (durationStr.isBlank()) return 180000L
        return try {
            val parts = durationStr.split(":").map { it.trim().toLongOrNull() ?: 0L }
            when (parts.size) {
                2 -> (parts[0] * 60 + parts[1]) * 1000
                3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
                else -> 180000L
            }
        } catch (e: Exception) {
            180000L
        }
    }

    private fun formatDurationMs(ms: Long): String {
        if (ms <= 0) return "3:00"
        val totalSec = ms / 1000
        val mins = totalSec / 60
        val secs = totalSec % 60
        return "%d:%02d".format(mins, secs)
    }
}
