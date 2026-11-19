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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
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

enum class JamConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

object ListenTogetherEngine {
    private const val TAG = "ListenTogetherEngine"
    private const val DEFAULT_SERVER_URL = "wss://api.auramusic.site/ws"
    private const val PREFS_NAME = "auralix_jam_prefs"
    private const val KEY_SAVED_USERNAME = "jam_saved_username"

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

    private val _myUsername = MutableStateFlow("Auralix Fan")
    val myUsername: StateFlow<String> = _myUsername.asStateFlow()

    private val _myUserId = MutableStateFlow<String?>(null)
    val myUserId: StateFlow<String?> = _myUserId.asStateFlow()

    private val _sessionToken = MutableStateFlow<String?>(null)
    val sessionToken: StateFlow<String?> = _sessionToken.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _autoApprove = MutableStateFlow(true)
    val autoApprove: StateFlow<Boolean> = _autoApprove.asStateFlow()

    private val _serverUrl = MutableStateFlow(DEFAULT_SERVER_URL)
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    // Internal lock flag to avoid playback update echo loops
    @Volatile
    private var isApplyingRemoteSync = false

    private var okHttpClient: OkHttpClient? = null
    private var activeWebSocket: WebSocket? = null
    private var pingJob: Job? = null

    private var pendingAction: PendingAction? = null

    private sealed class PendingAction {
        data class CreateRoom(val username: String) : PendingAction()
        data class JoinRoom(val roomCode: String, val username: String) : PendingAction()
    }

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
            .pingInterval(25, TimeUnit.SECONDS)
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
     * Create a new Listen Together room as Host
     */
    fun createRoom(username: String) {
        _errorMessage.value = null
        val cleanName = username.trim().ifBlank { _myUsername.value }
        _myUsername.value = cleanName
        pendingAction = PendingAction.CreateRoom(cleanName)
        connectAndExecute()
    }

    /**
     * Join an existing Listen Together room with 8-character code
     */
    fun joinRoom(code: String, username: String) {
        _errorMessage.value = null
        val cleanCode = code.trim().uppercase()
        val cleanName = username.trim().ifBlank { _myUsername.value }
        _myUsername.value = cleanName
        pendingAction = PendingAction.JoinRoom(cleanCode, cleanName)
        connectAndExecute()
    }

    /**
     * Leave current room and disconnect
     */
    fun leaveRoom() {
        try {
            if (_isInRoom.value && activeWebSocket != null) {
                val leaveMsg = JSONObject().apply {
                    put("type", "leave_room")
                    put("payload", JSONObject())
                }
                activeWebSocket?.send(leaveMsg.toString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error leaving room", e)
        }
        resetRoomState()
        disconnectSocket()
    }

    /**
     * Send chat message or emoji reaction
     */
    fun sendChatMessage(message: String) {
        if (!_isInRoom.value || activeWebSocket == null || message.isBlank()) return
        try {
            val chatMsg = JSONObject().apply {
                put("type", "chat")
                put("payload", JSONObject().apply {
                    put("message", message.trim())
                })
            }
            activeWebSocket?.send(chatMsg.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending chat message", e)
        }
    }

    /**
     * Approve a pending user join request (Host only)
     */
    fun approveJoin(userId: String) {
        if (!_isHost.value || activeWebSocket == null) return
        try {
            val approveMsg = JSONObject().apply {
                put("type", "approve_join")
                put("payload", JSONObject().apply {
                    put("user_id", userId)
                })
            }
            activeWebSocket?.send(approveMsg.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error approving join", e)
        }
    }

    /**
     * Kick user from room (Host only)
     */
    fun kickUser(userId: String) {
        if (!_isHost.value || activeWebSocket == null) return
        try {
            val kickMsg = JSONObject().apply {
                put("type", "kick_user")
                put("payload", JSONObject().apply {
                    put("user_id", userId)
                })
            }
            activeWebSocket?.send(kickMsg.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error kicking user", e)
        }
    }

    // =========================================================================
    // Host Playback Events -> WebSocket Broadcast
    // =========================================================================

    fun onHostTrackChanged(track: VideoItem) {
        if (!_isInRoom.value || !_isHost.value || isApplyingRemoteSync || activeWebSocket == null) return
        try {
            val durationMs = parseDurationMs(track.duration)
            val actionMsg = JSONObject().apply {
                put("type", "playback_action")
                put("payload", JSONObject().apply {
                    put("action", "change_track")
                    put("track_id", track.id)
                    put("track_info", JSONObject().apply {
                        put("id", track.id)
                        put("title", track.title)
                        put("artist", track.channelName.ifBlank { track.artist })
                        put("album", track.album)
                        put("duration", durationMs)
                        put("thumbnail", track.thumbnailUrl)
                    })
                })
            }
            activeWebSocket?.send(actionMsg.toString())
            Log.d(TAG, "Host sent change_track: ${track.title} (${track.id})")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending track change action", e)
        }
    }

    fun onHostPlayStateChanged(isPlaying: Boolean, positionMs: Long) {
        if (!_isInRoom.value || !_isHost.value || isApplyingRemoteSync || activeWebSocket == null) return
        try {
            val track = PlaybackController.currentTrack.value
            val actionMsg = JSONObject().apply {
                put("type", "playback_action")
                put("payload", JSONObject().apply {
                    put("action", if (isPlaying) "play" else "pause")
                    if (track != null) put("track_id", track.id)
                    put("position", positionMs)
                })
            }
            activeWebSocket?.send(actionMsg.toString())
            Log.d(TAG, "Host sent play/pause ($isPlaying) at $positionMs ms")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending play/pause action", e)
        }
    }

    fun onHostSeek(positionMs: Long) {
        if (!_isInRoom.value || !_isHost.value || isApplyingRemoteSync || activeWebSocket == null) return
        try {
            val track = PlaybackController.currentTrack.value
            val actionMsg = JSONObject().apply {
                put("type", "playback_action")
                put("payload", JSONObject().apply {
                    put("action", "seek")
                    if (track != null) put("track_id", track.id)
                    put("position", positionMs)
                })
            }
            activeWebSocket?.send(actionMsg.toString())
            Log.d(TAG, "Host sent seek to $positionMs ms")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending seek action", e)
        }
    }

    // =========================================================================
    // WebSocket Connection & Dispatcher
    // =========================================================================

    private fun connectAndExecute() {
        disconnectSocket()
        _connectionState.value = JamConnectionState.CONNECTING

        val client = okHttpClient ?: OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .build().also { okHttpClient = it }

        val request = Request.Builder()
            .url(_serverUrl.value)
            .build()

        activeWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected successfully to ${_serverUrl.value}")
                _connectionState.value = JamConnectionState.CONNECTED
                startHeartbeat(webSocket)
                executePendingAction(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(webSocket, text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code / $reason")
                _connectionState.value = JamConnectionState.DISCONNECTED
                if (_isInRoom.value) {
                    resetRoomState()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                _connectionState.value = JamConnectionState.ERROR
                _errorMessage.value = "Connection lost: ${t.localizedMessage ?: "Unknown error"}"
            }
        })
    }

    private fun executePendingAction(ws: WebSocket) {
        val action = pendingAction ?: return
        pendingAction = null
        try {
            when (action) {
                is PendingAction.CreateRoom -> {
                    val createMsg = JSONObject().apply {
                        put("type", "create_room")
                        put("payload", JSONObject().apply {
                            put("username", action.username)
                        })
                    }
                    ws.send(createMsg.toString())
                    Log.d(TAG, "Sent create_room as ${action.username}")
                }
                is PendingAction.JoinRoom -> {
                    val joinMsg = JSONObject().apply {
                        put("type", "join_room")
                        put("payload", JSONObject().apply {
                            put("room_code", action.roomCode)
                            put("username", action.username)
                        })
                    }
                    ws.send(joinMsg.toString())
                    Log.d(TAG, "Sent join_room for code ${action.roomCode} as ${action.username}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing pending action", e)
        }
    }

    private fun handleIncomingMessage(ws: WebSocket, text: String) {
        try {
            val root = JSONObject(text)
            val type = root.optString("type")
            val payload = root.optJSONObject("payload") ?: JSONObject()

            when (type) {
                "room_created" -> {
                    val roomCode = payload.optString("room_code")
                    val userId = payload.optString("user_id")
                    val token = payload.optString("session_token")

                    _roomCode.value = roomCode
                    _myUserId.value = userId
                    _sessionToken.value = token
                    _isInRoom.value = true
                    _isHost.value = true
                    _hostUsername.value = _myUsername.value

                    _participants.value = listOf(
                        RoomParticipant(
                            userId = userId,
                            username = _myUsername.value,
                            isHost = true,
                            isConnected = true
                        )
                    )

                    // If Host already has a track playing, broadcast it immediately!
                    val current = PlaybackController.currentTrack.value
                    if (current != null) {
                        mainHandler.postDelayed({
                            onHostTrackChanged(current)
                            if (PlaybackController.isPlaying.value) {
                                val posMs = (PlaybackController.currentTimeSeconds.value * 1000).toLong()
                                onHostPlayStateChanged(true, posMs)
                            }
                        }, 300)
                    }
                }

                "join_request" -> {
                    val guestId = payload.optString("user_id")
                    val guestName = payload.optString("username")
                    Log.d(TAG, "Join request received from $guestName ($guestId)")

                    if (_autoApprove.value && _isHost.value) {
                        approveJoin(guestId)
                    }
                }

                "join_approved" -> {
                    val roomCode = payload.optString("room_code")
                    val userId = payload.optString("user_id")
                    val token = payload.optString("session_token")
                    val stateObj = payload.optJSONObject("state")

                    _roomCode.value = roomCode
                    _myUserId.value = userId
                    _sessionToken.value = token
                    _isInRoom.value = true
                    _isHost.value = false

                    if (stateObj != null) {
                        parseAndApplyRoomState(stateObj)
                    }
                }

                "join_rejected" -> {
                    val reason = payload.optString("reason", "Join request declined by host")
                    _errorMessage.value = reason
                    leaveRoom()
                }

                "user_joined" -> {
                    val userId = payload.optString("user_id")
                    val username = payload.optString("username")
                    val currentList = _participants.value.toMutableList()
                    if (currentList.none { it.userId == userId }) {
                        currentList.add(RoomParticipant(userId, username, isHost = false))
                        _participants.value = currentList
                    }

                    // Add system message
                    val sysMsg = JamChatMessage(
                        userId = "system",
                        username = "Auralix Jam",
                        message = "🎉 $username joined the room"
                    )
                    _chatMessages.value = _chatMessages.value + sysMsg

                    // If Host, re-broadcast current track & position for the new joiner!
                    if (_isHost.value) {
                        val current = PlaybackController.currentTrack.value
                        if (current != null) {
                            onHostTrackChanged(current)
                            if (PlaybackController.isPlaying.value) {
                                val posMs = (PlaybackController.currentTimeSeconds.value * 1000).toLong()
                                onHostPlayStateChanged(true, posMs)
                            }
                        }
                    }
                }

                "user_left", "user_disconnected" -> {
                    val userId = payload.optString("user_id")
                    val username = payload.optString("username")
                    _participants.value = _participants.value.filter { it.userId != userId }

                    val sysMsg = JamChatMessage(
                        userId = "system",
                        username = "Auralix Jam",
                        message = "$username left the room"
                    )
                    _chatMessages.value = _chatMessages.value + sysMsg
                }

                "sync_playback" -> {
                    handleSyncPlaybackPayload(payload)
                }

                "chat" -> {
                    val userId = payload.optString("user_id")
                    val username = payload.optString("username")
                    val message = payload.optString("message")
                    val timestamp = payload.optLong("timestamp", System.currentTimeMillis())

                    val chatObj = JamChatMessage(
                        userId = userId,
                        username = username,
                        message = message,
                        timestamp = timestamp
                    )
                    _chatMessages.value = _chatMessages.value + chatObj
                }

                "kicked" -> {
                    val reason = payload.optString("reason", "You were removed from the room")
                    _errorMessage.value = reason
                    leaveRoom()
                }

                "error" -> {
                    val msg = payload.optString("message", "An error occurred")
                    _errorMessage.value = msg
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming WS message: $text", e)
        }
    }

    private fun parseAndApplyRoomState(state: JSONObject) {
        try {
            val usersArray = state.optJSONArray("users")
            val usersList = mutableListOf<RoomParticipant>()
            var foundHostName: String? = null

            if (usersArray != null) {
                for (i in 0 until usersArray.length()) {
                    val u = usersArray.getJSONObject(i)
                    val uid = u.optString("user_id")
                    val name = u.optString("username")
                    val host = u.optBoolean("is_host", false)
                    val connected = u.optBoolean("is_connected", true)
                    usersList.add(RoomParticipant(uid, name, host, connected))
                    if (host) foundHostName = name
                }
            }
            _participants.value = usersList
            _hostUsername.value = foundHostName

            val currentTrackObj = state.optJSONObject("current_track")
            val isPlaying = state.optBoolean("is_playing", false)
            val positionMs = state.optLong("position", 0)
            val lastUpdate = state.optLong("last_update", 0)

            if (currentTrackObj != null) {
                val trackId = currentTrackObj.optString("id")
                val title = currentTrackObj.optString("title", "Unknown Track")
                val artist = currentTrackObj.optString("artist", "Various Artists")
                val thumb = currentTrackObj.optString("thumbnail", "")
                val durMs = currentTrackObj.optLong("duration", 0)

                val elapsedSinceUpdateMs = if (lastUpdate > 0 && isPlaying) {
                    (System.currentTimeMillis() - lastUpdate).coerceIn(0, 10000)
                } else 0L

                val targetSec = ((positionMs + elapsedSinceUpdateMs) / 1000f).coerceAtLeast(0f)

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
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing room state", e)
        }
    }

    private fun handleSyncPlaybackPayload(payload: JSONObject) {
        // If we are Host, we don't apply remote playback controls
        if (_isHost.value) return

        try {
            val action = payload.optString("action")
            val trackId = payload.optString("track_id")
            val positionMs = payload.optLong("position", -1)
            val serverTime = payload.optLong("server_time", 0)
            val trackInfo = payload.optJSONObject("track_info")

            Log.d(TAG, "Guest received sync_playback: $action (track=$trackId, pos=$positionMs)")

            when (action) {
                "change_track" -> {
                    if (trackInfo != null || trackId.isNotBlank()) {
                        val realId = trackId.ifBlank { trackInfo?.optString("id") ?: "" }
                        val title = trackInfo?.optString("title") ?: "Sync Track"
                        val artist = trackInfo?.optString("artist") ?: ""
                        val thumb = trackInfo?.optString("thumbnail") ?: ""
                        val durMs = trackInfo?.optLong("duration", 0) ?: 0L

                        mainHandler.post {
                            applyGuestTrackSync(
                                trackId = realId,
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

                "play" -> {
                    val current = PlaybackController.currentTrack.value
                    if (trackId.isNotBlank() && (current == null || current.id != trackId)) {
                        // Track differs: load and play
                        val title = trackInfo?.optString("title") ?: "Jam Song"
                        val artist = trackInfo?.optString("artist") ?: ""
                        val thumb = trackInfo?.optString("thumbnail") ?: ""
                        val durMs = trackInfo?.optLong("duration", 0) ?: 0L
                        val latency = if (serverTime > 0) (System.currentTimeMillis() - serverTime).coerceIn(0, 5000) else 0L
                        val targetSec = if (positionMs >= 0) ((positionMs + latency) / 1000f) else 0f

                        mainHandler.post {
                            applyGuestTrackSync(
                                trackId = trackId,
                                title = title,
                                artist = artist,
                                thumbnail = thumb,
                                durationMs = durMs,
                                targetPositionSec = targetSec,
                                shouldPlay = true
                            )
                        }
                    } else {
                        // Same track: sync position and resume
                        val latency = if (serverTime > 0) (System.currentTimeMillis() - serverTime).coerceIn(0, 5000) else 0L
                        val targetSec = if (positionMs >= 0) ((positionMs + latency) / 1000f) else null

                        mainHandler.post {
                            isApplyingRemoteSync = true
                            try {
                                if (targetSec != null) {
                                    val currentPos = PlaybackController.currentTimeSeconds.value
                                    if (kotlin.math.abs(currentPos - targetSec) > 1.8f) {
                                        PlaybackController.seekTo(targetSec)
                                    }
                                }
                                PlaybackController.play()
                            } finally {
                                mainHandler.postDelayed({ isApplyingRemoteSync = false }, 500)
                            }
                        }
                    }
                }

                "pause" -> {
                    mainHandler.post {
                        isApplyingRemoteSync = true
                        try {
                            if (positionMs >= 0) {
                                PlaybackController.seekTo(positionMs / 1000f)
                            }
                            PlaybackController.pause()
                        } finally {
                            mainHandler.postDelayed({ isApplyingRemoteSync = false }, 500)
                        }
                    }
                }

                "seek" -> {
                    if (positionMs >= 0) {
                        val targetSec = positionMs / 1000f
                        mainHandler.post {
                            isApplyingRemoteSync = true
                            try {
                                PlaybackController.seekTo(targetSec)
                            } finally {
                                mainHandler.postDelayed({ isApplyingRemoteSync = false }, 500)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling sync playback payload", e)
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
                delay(25000)
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
        _sessionToken.value = null
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
