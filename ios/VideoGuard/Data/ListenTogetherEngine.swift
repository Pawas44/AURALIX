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

struct FloatingReactionItem: Identifiable, Hashable {
    let id: String = UUID().uuidString
    let emoji: String
    let startXFraction: CGFloat = CGFloat.random(in: 0.15...0.85)
}

enum JamConnectionState: String {
    case disconnected
    case connecting
    case connected
    case error
}

class ListenTogetherEngine: NSObject, ObservableObject, URLSessionWebSocketDelegate {
    static let shared = ListenTogetherEngine()

    private let prefsUsernameKey = "auralix_jam_saved_username"

    @Published var connectionState: JamConnectionState = .disconnected
    @Published var isInRoom: Bool = false
    @Published var isHost: Bool = false
    @Published var roomCode: String? = nil
    @Published var hostUsername: String? = nil
    @Published var participants: [RoomParticipant] = []
    @Published var chatMessages: [JamChatMessage] = []
    @Published var floatingReactions: [FloatingReactionItem] = []
    @Published var myUsername: String = "Auralix Fan"
    @Published var myUserId: String? = nil
    @Published var autoApprove: Bool = true
    @Published var errorMessage: String? = nil

    private var urlSession: URLSession?
    private var webSocketTask: URLSessionWebSocketTask?
    private var pingTimer: Timer?

    private var isApplyingRemoteSync: Bool = false

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

    private func generateRoomCode() -> String {
        let chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        return String((0..<6).compactMap { _ in chars.randomElement() })
    }

    private func getTopic(for code: String) -> String {
        return "auralix_jam_\(code.lowercased().trimmingCharacters(in: .whitespacesAndNewlines))"
    }

    // MARK: - Room Management API

    func createRoom(username: String) {
        errorMessage = nil
        let cleanName = username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? myUsername : username.trimmingCharacters(in: .whitespacesAndNewlines)
        myUsername = cleanName

        let code = generateRoomCode()
        let uid = "usr_" + String(UUID().uuidString.prefix(8))

        self.roomCode = code
        self.myUserId = uid
        self.isInRoom = true
        self.isHost = true
        self.hostUsername = cleanName
        self.participants = [
            RoomParticipant(userId: uid, username: cleanName, isHost: true, isConnected: true)
        ]
        self.chatMessages = [
            JamChatMessage(
                userId: "system",
                username: "Auralix Jam",
                message: "🚀 Room created! Code: \(code). Share this code with friends to listen together!"
            )
        ]

        connectToRoom(code: code, isHostJoin: true)
    }

    func joinRoom(code: String, username: String) {
        errorMessage = nil
        let cleanCode = code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard cleanCode.count >= 3 else {
            errorMessage = "Please enter a valid room code"
            return
        }
        let cleanName = username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? myUsername : username.trimmingCharacters(in: .whitespacesAndNewlines)
        myUsername = cleanName

        let uid = "usr_" + String(UUID().uuidString.prefix(8))
        self.roomCode = cleanCode
        self.myUserId = uid
        self.isInRoom = true
        self.isHost = false
        self.participants = [
            RoomParticipant(userId: uid, username: cleanName, isHost: false, isConnected: true)
        ]
        self.chatMessages = [
            JamChatMessage(
                userId: "system",
                username: "Auralix Jam",
                message: "Connecting to Jam \(cleanCode)..."
            )
        ]

        connectToRoom(code: cleanCode, isHostJoin: false)
    }

    func leaveRoom() {
        if isInRoom {
            publishEvent(type: "user_left", payload: [
                "userId": myUserId ?? "",
                "username": myUsername
            ])
        }
        resetRoomState()
        disconnectSocket()
    }

    func sendChatMessage(_ message: String) {
        let trimmed = message.trimmingCharacters(in: .whitespacesAndNewlines)
        guard isInRoom, !trimmed.isEmpty else { return }

        let myId = myUserId ?? "me"
        let chatObj = JamChatMessage(userId: myId, username: myUsername, message: trimmed)
        DispatchQueue.main.async {
            self.chatMessages.append(chatObj)
        }

        publishEvent(type: "chat", payload: [
            "message": trimmed,
            "timestamp": chatObj.timestamp
        ])
    }

    func sendReaction(_ emoji: String) {
        guard isInRoom, !emoji.isEmpty else { return }

        triggerFloatingReaction(emoji)

        publishEvent(type: "reaction", payload: [
            "emoji": emoji,
            "timestamp": Int64(Date().timeIntervalSince1970 * 1000)
        ])
    }

    func triggerFloatingReaction(_ emoji: String) {
        let item = FloatingReactionItem(emoji: emoji)
        DispatchQueue.main.async {
            self.floatingReactions.append(item)
        }
    }

    func removeFloatingReaction(id: String) {
        DispatchQueue.main.async {
            self.floatingReactions.removeAll { $0.id == id }
        }
    }

    func approveJoin(userId: String) {}

    func kickUser(userId: String) {
        guard isHost else { return }
        participants.removeAll { $0.userId == userId }
    }

    // MARK: - Collaborative Playback: Any Joined User Can Control Playback!

    func onHostTrackChanged(_ track: VideoItem) {
        guard isInRoom, !isApplyingRemoteSync else { return }

        let durMs = parseDurationMs(track.duration)
        let payload: [String: Any] = [
            "track": [
                "id": track.id,
                "title": track.title,
                "artist": track.channelName.isEmpty ? track.artist : track.channelName,
                "album": track.album,
                "duration": durMs,
                "thumbnail": track.thumbnailUrl
            ]
        ]
        publishEvent(type: "change_track", payload: payload)
    }

    func onHostPlayStateChanged(isPlaying: Bool, positionMs: Int64) {
        guard isInRoom, !isApplyingRemoteSync else { return }

        var payload: [String: Any] = [
            "isPlaying": isPlaying,
            "positionMs": positionMs
        ]
        if let current = MediaPlaybackService.shared.currentTrack {
            payload["track_id"] = current.id
        }
        publishEvent(type: "play_state", payload: payload)
    }

    func onHostSeek(positionMs: Int64) {
        guard isInRoom, !isApplyingRemoteSync else { return }

        var payload: [String: Any] = [
            "positionMs": positionMs
        ]
        if let current = MediaPlaybackService.shared.currentTrack {
            payload["track_id"] = current.id
        }
        publishEvent(type: "seek", payload: payload)
    }

    // MARK: - Pub/Sub Transport

    private func publishEvent(type: String, payload: [String: Any]) {
        guard let code = roomCode else { return }
        let topic = getTopic(for: code)
        guard let url = URL(string: "https://ntfy.sh/\(topic)") else { return }

        let msgDict: [String: Any] = [
            "type": type,
            "userId": myUserId ?? "usr",
            "username": myUsername,
            "payload": payload,
            "timestamp": Int64(Date().timeIntervalSince1970 * 1000)
        ]

        guard let jsonData = try? JSONSerialization.data(withJSONObject: msgDict) else { return }

        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        req.httpBody = jsonData

        URLSession.shared.dataTask(with: req).resume()
    }

    private func connectToRoom(code: String, isHostJoin: Bool) {
        disconnectSocket()
        connectionState = .connecting

        let topic = getTopic(for: code)
        guard let wsUrl = URL(string: "wss://ntfy.sh/\(topic)/ws") else { return }

        let session = URLSession(configuration: .default, delegate: self, delegateQueue: OperationQueue.main)
        self.urlSession = session
        let task = session.webSocketTask(with: wsUrl)
        self.webSocketTask = task
        task.resume()

        listenForMessages()
        startPingTimer()

        if !isHostJoin {
            publishEvent(type: "user_joined", payload: [
                "userId": myUserId ?? "usr",
                "username": myUsername
            ])
        } else {
            if let current = MediaPlaybackService.shared.currentTrack {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
                    self.onHostTrackChanged(current)
                    if MediaPlaybackService.shared.isPlaying {
                        let posMs = Int64(MediaPlaybackService.shared.currentTimeSeconds * 1000)
                        self.onHostPlayStateChanged(isPlaying: true, positionMs: posMs)
                    }
                }
            }
        }
    }

    func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didOpenWithProtocol protocol: String?) {
        DispatchQueue.main.async {
            self.connectionState = .connected
        }
    }

    func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didCloseWith closeCode: URLSessionWebSocketTask.CloseCode, reason: Data?) {
        DispatchQueue.main.async {
            self.connectionState = .disconnected
        }
    }

    private func listenForMessages() {
        webSocketTask?.receive { [weak self] result in
            guard let self = self else { return }
            switch result {
            case .failure(let error):
                DispatchQueue.main.async {
                    self.connectionState = .error
                    self.errorMessage = "Jam connection error: \(error.localizedDescription)"
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
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return
        }

        let event = root["event"] as? String ?? ""
        var innerJson: [String: Any] = [:]

        if event == "message", let innerStr = root["message"] as? String,
           let innerData = innerStr.data(using: .utf8),
           let parsed = try? JSONSerialization.jsonObject(with: innerData) as? [String: Any] {
            innerJson = parsed
        } else if root["type"] != nil {
            innerJson = root
        } else {
            return
        }

        let type = innerJson["type"] as? String ?? ""
        let senderId = innerJson["userId"] as? String ?? ""
        let senderName = innerJson["username"] as? String ?? "Friend"
        let payload = innerJson["payload"] as? [String: Any] ?? [:]

        // Echo filter
        if senderId == myUserId {
            return
        }

        DispatchQueue.main.async {
            switch type {
            case "change_track":
                if let trackObj = payload["track"] as? [String: Any] {
                    let trackId = trackObj["id"] as? String ?? ""
                    let title = trackObj["title"] as? String ?? "Jam Track"
                    let artist = trackObj["artist"] as? String ?? ""
                    let thumb = trackObj["thumbnail"] as? String ?? ""
                    let dur = (trackObj["duration"] as? NSNumber)?.int64Value ?? 180000

                    self.chatMessages.append(
                        JamChatMessage(userId: "system", username: "Auralix Jam", message: "🎵 \(senderName) changed track to: \(title)")
                    )

                    self.applyGuestTrackSync(
                        trackId: trackId,
                        title: title,
                        artist: artist,
                        thumbnail: thumb,
                        durationMs: dur,
                        targetPositionSec: 0,
                        shouldPlay: true
                    )
                }

            case "play_state":
                let isPlaying = payload["isPlaying"] as? Bool ?? false
                let posMs = (payload["positionMs"] as? NSNumber)?.int64Value ?? -1

                self.isApplyingRemoteSync = true
                if posMs >= 0 {
                    let targetSec = Double(posMs) / 1000.0
                    let currentPos = MediaPlaybackService.shared.currentTimeSeconds
                    if abs(currentPos - targetSec) > 2.0 {
                        MediaPlaybackService.shared.seekTo(seconds: targetSec)
                    }
                }
                if isPlaying {
                    MediaPlaybackService.shared.play()
                } else {
                    MediaPlaybackService.shared.pause()
                }
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
                    self.isApplyingRemoteSync = false
                }

            case "seek":
                if let posMs = (payload["positionMs"] as? NSNumber)?.int64Value, posMs >= 0 {
                    self.isApplyingRemoteSync = true
                    MediaPlaybackService.shared.seekTo(seconds: Double(posMs) / 1000.0)
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
                        self.isApplyingRemoteSync = false
                    }
                }

            case "chat":
                let msg = payload["message"] as? String ?? ""
                let ts = payload["timestamp"] as? Int64 ?? Int64(Date().timeIntervalSince1970 * 1000)
                if !msg.isEmpty {
                    self.chatMessages.append(
                        JamChatMessage(userId: senderId, username: senderName, message: msg, timestamp: ts)
                    )
                }

            case "reaction":
                if let emoji = payload["emoji"] as? String, !emoji.isEmpty {
                    self.triggerFloatingReaction(emoji)
                }

            case "user_joined":
                if !self.participants.contains(where: { $0.userId == senderId }) {
                    self.participants.append(RoomParticipant(userId: senderId, username: senderName, isHost: false))
                }
                self.chatMessages.append(
                    JamChatMessage(userId: "system", username: "Auralix Jam", message: "🎉 \(senderName) joined the Jam")
                )

                // Sync current track to new joiner
                if let current = MediaPlaybackService.shared.currentTrack {
                    let durMs = self.parseDurationMs(current.duration)
                    let posMs = Int64(MediaPlaybackService.shared.currentTimeSeconds * 1000)
                    let syncPayload: [String: Any] = [
                        "hostUsername": self.hostUsername ?? self.myUsername,
                        "isPlaying": MediaPlaybackService.shared.isPlaying,
                        "positionMs": posMs,
                        "track": [
                            "id": current.id,
                            "title": current.title,
                            "artist": current.channelName.isEmpty ? current.artist : current.channelName,
                            "album": current.album,
                            "duration": durMs,
                            "thumbnail": current.thumbnailUrl
                        ]
                    ]
                    self.publishEvent(type: "sync_state", payload: syncPayload)
                }

            case "sync_state":
                if let hostName = payload["hostUsername"] as? String, !hostName.isEmpty {
                    self.hostUsername = hostName
                }

                if let trackObj = payload["track"] as? [String: Any] {
                    let trackId = trackObj["id"] as? String ?? ""
                    let title = trackObj["title"] as? String ?? "Jam Track"
                    let artist = trackObj["artist"] as? String ?? ""
                    let thumb = trackObj["thumbnail"] as? String ?? ""
                    let dur = (trackObj["duration"] as? NSNumber)?.int64Value ?? 180000
                    let isPlaying = payload["isPlaying"] as? Bool ?? false
                    let posMs = (payload["positionMs"] as? NSNumber)?.int64Value ?? 0
                    let targetSec = Double(posMs) / 1000.0

                    self.applyGuestTrackSync(
                        trackId: trackId,
                        title: title,
                        artist: artist,
                        thumbnail: thumb,
                        durationMs: dur,
                        targetPositionSec: targetSec,
                        shouldPlay: isPlaying
                    )
                }

            case "user_left":
                self.participants.removeAll { $0.userId == senderId }
                self.chatMessages.append(
                    JamChatMessage(userId: "system", username: "Auralix Jam", message: "\(senderName) left the Jam")
                )

            default:
                break
            }
        }
    }

    private func applyGuestTrackSync(trackId: String, title: String, artist: String, thumbnail: String, durationMs: Int64, targetPositionSec: Double, shouldPlay: Bool) {
        guard !trackId.isEmpty else { return }

        isApplyingRemoteSync = true
        let durStr = formatDurationMs(durationMs)
        let thumbUrl = thumbnail.isEmpty ? "https://i.ytimg.com/vi/\(trackId)/hqdefault.jpg" : thumbnail

        let item = VideoItem(
            id: trackId,
            title: title,
            channelName: artist,
            channelAvatarUrl: "",
            thumbnailUrl: thumbUrl,
            views: "",
            uploadTime: "",
            duration: durStr,
            isMusicTrack: true,
            artist: artist
        )

        let current = MediaPlaybackService.shared.currentTrack
        if current == nil || current?.id != trackId {
            MediaPlaybackService.shared.playTrack(item)
        }

        if targetPositionSec > 0.5 {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
                MediaPlaybackService.shared.seekTo(seconds: targetPositionSec)
                if shouldPlay { MediaPlaybackService.shared.play() } else { MediaPlaybackService.shared.pause() }
            }
        } else {
            if shouldPlay { MediaPlaybackService.shared.play() } else { MediaPlaybackService.shared.pause() }
        }

        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
            self.isApplyingRemoteSync = false
        }
    }

    private func startPingTimer() {
        pingTimer?.invalidate()
        pingTimer = Timer.scheduledTimer(withTimeInterval: 20.0, repeats: true) { [weak self] _ in
            guard let self = self, self.connectionState == .connected else { return }
            self.webSocketTask?.sendPing { _ in }
        }
    }

    private func disconnectSocket() {
        pingTimer?.invalidate()
        pingTimer = nil
        webSocketTask?.cancel(with: .normalClosure, reason: nil)
        webSocketTask = nil
        urlSession?.invalidateAndCancel()
        urlSession = nil
    }

    private func resetRoomState() {
        isInRoom = false
        isHost = false
        roomCode = nil
        hostUsername = nil
        participants = []
        chatMessages = []
        floatingReactions = []
    }

    private func parseDurationMs(_ dur: String) -> Int64 {
        if dur.isEmpty { return 180000 }
        let parts = dur.split(separator: ":").compactMap { Int64($0.trimmingCharacters(in: .whitespaces)) }
        switch parts.count {
        case 2:
            return (parts[0] * 60 + parts[1]) * 1000
        case 3:
            return (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
        default:
            return 180000
        }
    }

    private func formatDurationMs(_ ms: Int64) -> String {
        guard ms > 0 else { return "3:00" }
        let totalSec = ms / 1000
        let mins = totalSec / 60
        let secs = totalSec % 60
        return String(format: "%d:%02d", mins, secs)
    }
}
