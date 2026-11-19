import Foundation
import Combine

struct RoomParticipant: Identifiable, Hashable, Codable {
    var id: String { userId }
    let userId: String
    let username: String
    let isHost: Bool
    var isConnected: Bool = true
}

struct JamChatMessage: Identifiable, Hashable, Codable {
    let id: String
    let userId: String
    let username: String
    let message: String
    let timestamp: Int64

    init(id: String = UUID().uuidString, userId: String, username: String, message: String, timestamp: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) {
        self.id = id
        self.userId = userId
        self.username = username
        self.message = message
        self.timestamp = timestamp
    }
}

enum JamConnectionState: String {
    case disconnected
    case connecting
    case connected
    case error
}

class ListenTogetherEngine: NSObject, ObservableObject, URLSessionWebSocketDelegate {
    static let shared = ListenTogetherEngine()

    private let defaultServerUrl = "wss://api.auramusic.site/ws"
    private let prefsUsernameKey = "auralix_jam_saved_username"

    @Published var connectionState: JamConnectionState = .disconnected
    @Published var isInRoom: Bool = false
    @Published var isHost: Bool = false
    @Published var roomCode: String? = nil
    @Published var hostUsername: String? = nil
    @Published var participants: [RoomParticipant] = []
    @Published var chatMessages: [JamChatMessage] = []
    @Published var myUsername: String = "Auralix Fan"
    @Published var myUserId: String? = nil
    @Published var autoApprove: Bool = true
    @Published var errorMessage: String? = nil

    private var urlSession: URLSession?
    private var webSocketTask: URLSessionWebSocketTask?
    private var pingTimer: Timer?

    private var isApplyingRemoteSync: Bool = false
    private var pendingAction: PendingJamAction?

    private enum PendingJamAction {
        case createRoom(username: String)
        case joinRoom(code: String, username: String)
    }

    private override init() {
        super.init()
        if let savedName = UserDefaults.standard.string(forKey: prefsUsernameKey), !savedName.isEmpty {
            self.myUsername = savedName
        } else if let account = AccountRepository.shared.currentAccount, !account.name.isEmpty {
            self.myUsername = account.name
        }
    }

    func setUsername(_ name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty {
            self.myUsername = trimmed
            UserDefaults.standard.set(trimmed, forKey: prefsUsernameKey)
        }
    }

    // MARK: - Room Management API

    func createRoom(username: String) {
        errorMessage = nil
        let cleanName = username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? myUsername : username.trimmingCharacters(in: .whitespacesAndNewlines)
        myUsername = cleanName
        pendingAction = .createRoom(username: cleanName)
        connectAndExecute()
    }

    func joinRoom(code: String, username: String) {
        errorMessage = nil
        let cleanCode = code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let cleanName = username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? myUsername : username.trimmingCharacters(in: .whitespacesAndNewlines)
        myUsername = cleanName
        pendingAction = .joinRoom(code: cleanCode, username: cleanName)
        connectAndExecute()
    }

    func leaveRoom() {
        if isInRoom && webSocketTask != nil {
            let leaveDict: [String: Any] = [
                "type": "leave_room",
                "payload": [:]
            ]
            sendJson(leaveDict)
        }
        resetRoomState()
        disconnectSocket()
    }

    func sendChatMessage(_ message: String) {
        let trimmed = message.trimmingCharacters(in: .whitespacesAndNewlines)
        guard isInRoom, webSocketTask != nil, !trimmed.isEmpty else { return }

        let chatDict: [String: Any] = [
            "type": "chat",
            "payload": [
                "message": trimmed
            ]
        ]
        sendJson(chatDict)
    }

    func approveJoin(userId: String) {
        guard isHost, webSocketTask != nil else { return }
        let approveDict: [String: Any] = [
            "type": "approve_join",
            "payload": [
                "user_id": userId
            ]
        ]
        sendJson(approveDict)
    }

    func kickUser(userId: String) {
        guard isHost, webSocketTask != nil else { return }
        let kickDict: [String: Any] = [
            "type": "kick_user",
            "payload": [
                "user_id": userId
            ]
        ]
        sendJson(kickDict)
    }

    // MARK: - Host Playback Notifications -> WebSocket Broadcast

    func onHostTrackChanged(_ track: VideoItem) {
        guard isInRoom, isHost, !isApplyingRemoteSync, webSocketTask != nil else { return }

        let durMs = parseDurationMs(track.duration)
        let msgDict: [String: Any] = [
            "type": "playback_action",
            "payload": [
                "action": "change_track",
                "track_id": track.id,
                "track_info": [
                    "id": track.id,
                    "title": track.title,
                    "artist": track.channelName.isEmpty ? track.artist : track.channelName,
                    "album": track.album,
                    "duration": durMs,
                    "thumbnail": track.thumbnailUrl
                ]
            ]
        ]
        sendJson(msgDict)
    }

    func onHostPlayStateChanged(isPlaying: Bool, positionMs: Int64) {
        guard isInRoom, isHost, !isApplyingRemoteSync, webSocketTask != nil else { return }

        var payload: [String: Any] = [
            "action": isPlaying ? "play" : "pause",
            "position": positionMs
        ]
        if let current = MediaPlaybackService.shared.currentTrack {
            payload["track_id"] = current.id
        }

        let msgDict: [String: Any] = [
            "type": "playback_action",
            "payload": payload
        ]
        sendJson(msgDict)
    }

    func onHostSeek(positionMs: Int64) {
        guard isInRoom, isHost, !isApplyingRemoteSync, webSocketTask != nil else { return }

        var payload: [String: Any] = [
            "action": "seek",
            "position": positionMs
        ]
        if let current = MediaPlaybackService.shared.currentTrack {
            payload["track_id"] = current.id
        }

        let msgDict: [String: Any] = [
            "type": "playback_action",
            "payload": payload
        ]
        sendJson(msgDict)
    }

    // MARK: - Connection & Message Handling

    private func connectAndExecute() {
        disconnectSocket()
        connectionState = .connecting

        guard let url = URL(string: defaultServerUrl) else { return }
        let session = URLSession(configuration: .default, delegate: self, delegateQueue: OperationQueue.main)
        self.urlSession = session
        let task = session.webSocketTask(with: url)
        self.webSocketTask = task
        task.resume()

        listenForMessages()
        startPingTimer()
    }

    func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didOpenWithProtocol protocol: String?) {
        DispatchQueue.main.async {
            self.connectionState = .connected
            self.executePendingAction()
        }
    }

    func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didCloseWith closeCode: URLSessionWebSocketTask.CloseCode, reason: Data?) {
        DispatchQueue.main.async {
            self.connectionState = .disconnected
            if self.isInRoom {
                self.resetRoomState()
            }
        }
    }

    private func executePendingAction() {
        guard let action = pendingAction else { return }
        pendingAction = nil

        switch action {
        case .createRoom(let username):
            let dict: [String: Any] = [
                "type": "create_room",
                "payload": [
                    "username": username
                ]
            ]
            sendJson(dict)

        case .joinRoom(let code, let username):
            let dict: [String: Any] = [
                "type": "join_room",
                "payload": [
                    "room_code": code,
                    "username": username
                ]
            ]
            sendJson(dict)
        }
    }

    private func listenForMessages() {
        webSocketTask?.receive { [weak self] result in
            guard let self = self else { return }
            switch result {
            case .failure(let error):
                DispatchQueue.main.async {
                    self.connectionState = .error
                    self.errorMessage = "Connection error: \(error.localizedDescription)"
                }
            case .success(let message):
                switch message {
                case .string(let text):
                    self.handleIncomingText(text)
                case .data(let data):
                    if let str = String(data: data, encoding: .utf8) {
                        self.handleIncomingText(str)
                    }
                @unknown default:
                    break
                }
                self.listenForMessages()
            }
        }
    }

    private func handleIncomingText(_ text: String) {
        guard let data = text.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let type = json["type"] as? String else {
            return
        }

        let payload = json["payload"] as? [String: Any] ?? [:]

        DispatchQueue.main.async {
            switch type {
            case "room_created":
                let code = payload["room_code"] as? String ?? ""
                let uid = payload["user_id"] as? String ?? ""
                self.roomCode = code
                self.myUserId = uid
                self.isInRoom = true
                self.isHost = true
                self.hostUsername = self.myUsername
                self.participants = [
                    RoomParticipant(userId: uid, username: self.myUsername, isHost: true, isConnected: true)
                ]

                // Broadcast currently playing track if any
                if let current = MediaPlaybackService.shared.currentTrack {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                        self.onHostTrackChanged(current)
                        if MediaPlaybackService.shared.isPlaying {
                            let posMs = Int64(MediaPlaybackService.shared.currentTimeSeconds * 1000)
                            self.onHostPlayStateChanged(isPlaying: true, positionMs: posMs)
                        }
                    }
                }

            case "join_request":
                let guestId = payload["user_id"] as? String ?? ""
                if self.autoApprove && self.isHost {
                    self.approveJoin(userId: guestId)
                }

            case "join_approved":
                let code = payload["room_code"] as? String ?? ""
                let uid = payload["user_id"] as? String ?? ""
                self.roomCode = code
                self.myUserId = uid
                self.isInRoom = true
                self.isHost = false

                if let stateObj = payload["state"] as? [String: Any] {
                    self.parseAndApplyRoomState(stateObj)
                }

            case "join_rejected":
                let reason = payload["reason"] as? String ?? "Join request was declined"
                self.errorMessage = reason
                self.leaveRoom()

            case "user_joined":
                let uid = payload["user_id"] as? String ?? ""
                let name = payload["username"] as? String ?? "Friend"
                if !self.participants.contains(where: { $0.userId == uid }) {
                    self.participants.append(RoomParticipant(userId: uid, username: name, isHost: false))
                }
                self.chatMessages.append(JamChatMessage(userId: "system", username: "Jam", message: "🎉 \(name) joined the room"))

                if self.isHost, let current = MediaPlaybackService.shared.currentTrack {
                    self.onHostTrackChanged(current)
                    if MediaPlaybackService.shared.isPlaying {
                        let posMs = Int64(MediaPlaybackService.shared.currentTimeSeconds * 1000)
                        self.onHostPlayStateChanged(isPlaying: true, positionMs: posMs)
                    }
                }

            case "user_left", "user_disconnected":
                let uid = payload["user_id"] as? String ?? ""
                let name = payload["username"] as? String ?? "Friend"
                self.participants.removeAll { $0.userId == uid }
                self.chatMessages.append(JamChatMessage(userId: "system", username: "Jam", message: "\(name) left the room"))

            case "sync_playback":
                self.handleSyncPlayback(payload)

            case "chat":
                let uid = payload["user_id"] as? String ?? ""
                let name = payload["username"] as? String ?? ""
                let msg = payload["message"] as? String ?? ""
                let ts = payload["timestamp"] as? Int64 ?? Int64(Date().timeIntervalSince1970 * 1000)
                self.chatMessages.append(JamChatMessage(userId: uid, username: name, message: msg, timestamp: ts))

            case "kicked":
                let reason = payload["reason"] as? String ?? "Removed from room"
                self.errorMessage = reason
                self.leaveRoom()

            case "error":
                let msg = payload["message"] as? String ?? "Error occurred"
                self.errorMessage = msg

            default:
                break
            }
        }
    }

    private func parseAndApplyRoomState(_ state: [String: Any]) {
        if let usersRaw = state["users"] as? [[String: Any]] {
            var list: [RoomParticipant] = []
            for u in usersRaw {
                let uid = u["user_id"] as? String ?? ""
                let uname = u["username"] as? String ?? ""
                let host = u["is_host"] as? Bool ?? false
                let conn = u["is_connected"] as? Bool ?? true
                list.append(RoomParticipant(userId: uid, username: uname, isHost: host, isConnected: conn))
                if host { self.hostUsername = uname }
            }
            self.participants = list
        }

        let isPlaying = state["is_playing"] as? Bool ?? false
        let posMs = (state["position"] as? NSNumber)?.int64Value ?? 0
        let lastUpdate = (state["last_update"] as? NSNumber)?.int64Value ?? 0

        if let trackObj = state["current_track"] as? [String: Any] {
            let trackId = trackObj["id"] as? String ?? ""
            let title = trackObj["title"] as? String ?? "Jam Track"
            let artist = trackObj["artist"] as? String ?? ""
            let thumb = trackObj["thumbnail"] as? String ?? ""
            let dur = (trackObj["duration"] as? NSNumber)?.int64Value ?? 180000

            let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
            let elapsed = (lastUpdate > 0 && isPlaying) ? max(0, min(10000, nowMs - lastUpdate)) : 0
            let targetSec = Double(posMs + elapsed) / 1000.0

            applyGuestTrackSync(trackId: trackId, title: title, artist: artist, thumbnail: thumb, durationMs: dur, targetPositionSec: targetSec, shouldPlay: isPlaying)
        }
    }

    private func handleSyncPlayback(_ payload: [String: Any]) {
        guard !isHost else { return }

        let action = payload["action"] as? String ?? ""
        let trackId = payload["track_id"] as? String ?? ""
        let posMs = (payload["position"] as? NSNumber)?.int64Value ?? -1
        let serverTime = (payload["server_time"] as? NSNumber)?.int64Value ?? 0
        let trackInfo = payload["track_info"] as? [String: Any]

        switch action {
        case "change_track":
            let realId = !trackId.isEmpty ? trackId : (trackInfo?["id"] as? String ?? "")
            let title = trackInfo?["title"] as? String ?? "Jam Track"
            let artist = trackInfo?["artist"] as? String ?? ""
            let thumb = trackInfo?["thumbnail"] as? String ?? ""
            let dur = (trackInfo?["duration"] as? NSNumber)?.int64Value ?? 180000
            applyGuestTrackSync(trackId: realId, title: title, artist: artist, thumbnail: thumb, durationMs: dur, targetPositionSec: 0, shouldPlay: true)

        case "play":
            let current = MediaPlaybackService.shared.currentTrack
            if !trackId.isEmpty && (current == nil || current?.id != trackId) {
                let title = trackInfo?["title"] as? String ?? "Jam Track"
                let artist = trackInfo?["artist"] as? String ?? ""
                let thumb = trackInfo?["thumbnail"] as? String ?? ""
                let dur = (trackInfo?["duration"] as? NSNumber)?.int64Value ?? 180000
                let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
                let latency = serverTime > 0 ? max(0, min(5000, nowMs - serverTime)) : 0
                let targetSec = posMs >= 0 ? Double(posMs + latency) / 1000.0 : 0.0
                applyGuestTrackSync(trackId: trackId, title: title, artist: artist, thumbnail: thumb, durationMs: dur, targetPositionSec: targetSec, shouldPlay: true)
            } else {
                let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
                let latency = serverTime > 0 ? max(0, min(5000, nowMs - serverTime)) : 0
                let targetSec = posMs >= 0 ? Double(posMs + latency) / 1000.0 : nil

                isApplyingRemoteSync = true
                if let target = targetSec {
                    let currentPos = MediaPlaybackService.shared.currentTimeSeconds
                    if abs(currentPos - target) > 1.8 {
                        MediaPlaybackService.shared.seekTo(seconds: target)
                    }
                }
                MediaPlaybackService.shared.play()
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    self.isApplyingRemoteSync = false
                }
            }

        case "pause":
            isApplyingRemoteSync = true
            if posMs >= 0 {
                MediaPlaybackService.shared.seekTo(seconds: Double(posMs) / 1000.0)
            }
            MediaPlaybackService.shared.pause()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                self.isApplyingRemoteSync = false
            }

        case "seek":
            if posMs >= 0 {
                isApplyingRemoteSync = true
                MediaPlaybackService.shared.seekTo(seconds: Double(posMs) / 1000.0)
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    self.isApplyingRemoteSync = false
                }
            }

        default:
            break
        }
    }

    private func applyGuestTrackSync(trackId: String, title: String, artist: String, thumbnail: String, durationMs: Int64, targetPositionSec: Double, shouldPlay: Bool) {
        guard !trackId.isEmpty else { return }

        isApplyingRemoteSync = true
        let thumbUrl = !thumbnail.isEmpty ? thumbnail : "https://i.ytimg.com/vi/\(trackId)/hqdefault.jpg"
        let durStr = formatDuration(durationMs)
        let video = VideoItem(
            id: trackId,
            title: title,
            channelName: artist,
            thumbnailUrl: thumbUrl,
            views: "",
            duration: durStr,
            isMusicTrack: true,
            artist: artist
        )

        let current = MediaPlaybackService.shared.currentTrack
        if current == nil || current?.id != trackId {
            MediaPlaybackService.shared.play(track: video)
        }

        if targetPositionSec > 0.5 {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
                MediaPlaybackService.shared.seekTo(seconds: targetPositionSec)
                if shouldPlay { MediaPlaybackService.shared.play() } else { MediaPlaybackService.shared.pause() }
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
                    self.isApplyingRemoteSync = false
                }
            }
        } else {
            if shouldPlay { MediaPlaybackService.shared.play() } else { MediaPlaybackService.shared.pause() }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
                self.isApplyingRemoteSync = false
            }
        }
    }

    private func sendJson(_ dict: [String: Any]) {
        guard let data = try? JSONSerialization.data(withJSONObject: dict),
              let str = String(data: data, encoding: .utf8) else {
            return
        }
        webSocketTask?.send(.string(str)) { error in
            if let error = error {
                print("ListenTogether WebSocket send error: \(error.localizedDescription)")
            }
        }
    }

    private func startPingTimer() {
        pingTimer?.invalidate()
        pingTimer = Timer.scheduledTimer(withTimeInterval: 25.0, repeats: true) { [weak self] _ in
            guard let self = self, self.webSocketTask != nil else { return }
            let pingDict: [String: Any] = ["type": "ping"]
            self.sendJson(pingDict)
        }
    }

    private func disconnectSocket() {
        pingTimer?.invalidate()
        pingTimer = nil
        webSocketTask?.cancel(with: .normalClosure, reason: nil)
        webSocketTask = nil
        urlSession = nil
    }

    private func resetRoomState() {
        isInRoom = false
        isHost = false
        roomCode = nil
        hostUsername = nil
        participants = []
        chatMessages = []
    }

    private func parseDurationMs(_ str: String) -> Int64 {
        let parts = str.split(separator: ":").compactMap { Int64($0) }
        switch parts.count {
        case 2: return (parts[0] * 60 + parts[1]) * 1000
        case 3: return (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
        default: return 180000
        }
    }

    private func formatDuration(_ ms: Int64) -> String {
        let total = ms / 1000
        let m = total / 60
        let s = total % 60
        return String(format: "%d:%02d", m, s)
    }
}
