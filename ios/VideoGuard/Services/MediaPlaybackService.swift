import Foundation
import Combine
import AVFoundation
import MediaPlayer
import UIKit

enum RepeatMode {
    case off, one, all
}

struct PlaybackState {
    var currentTrack: VideoItem? = nil
    var isPlaying: Bool = false
    var currentPosition: Int64 = 0 // ms
    var duration: Int64 = 0 // ms
    var queue: [VideoItem] = []
    var currentIndex: Int = -1
    var isShuffled: Bool = false
    var repeatMode: RepeatMode = .off
}

class MediaPlaybackService: ObservableObject {
    static let shared = MediaPlaybackService()

    @Published var currentTrack: VideoItem? = nil
    @Published var isPlaying: Bool = false
    @Published var isBackgroundPlayEnabled: Bool = true
    @Published var playbackQueue: [VideoItem] = []
    @Published var currentTimeSeconds: Double = 0
    @Published var durationSeconds: Double = 0
    @Published var playbackSpeed: Float = 1.0
    @Published var playbackQuality: String = "Auto (1080p)"
    @Published var isLoopEnabled: Bool = false
    @Published var isAutoplayEnabled: Bool = true
    @Published var isCaptionsEnabled: Bool = false
    @Published var sleepTimerMinutes: Int? = nil
    @Published var isShuffled: Bool = false
    @Published var repeatMode: RepeatMode = .off

    var playbackState: PlaybackState {
        let idx = currentTrack.flatMap { track in playbackQueue.firstIndex(where: { $0.id == track.id }) } ?? -1
        return PlaybackState(
            currentTrack: currentTrack,
            isPlaying: isPlaying,
            currentPosition: Int64(currentTimeSeconds * 1000),
            duration: Int64(durationSeconds * 1000),
            queue: playbackQueue,
            currentIndex: idx,
            isShuffled: isShuffled,
            repeatMode: repeatMode
        )
    }

    private var commandListener: ((String) -> Void)?
    private var sleepTimerWorkItem: DispatchWorkItem?
    private var lastTrackChangeTime: Date = .distantPast
    private var pendingSeekTarget: Double? = nil
    private var lastSeekTimestamp: Date = .distantPast
    private var cachedArtworkImage: UIImage?
    private var cachedArtworkVideoId: String?
    private var silentAudioPlayer: AVAudioPlayer?
    private var lastNowPlayingUpdate: Date = .distantPast

    // Offline native playback support
    private var offlineTimer: Timer? = nil
    private var offlineAudioPlayer: AVAudioPlayer? = nil
    @Published var isOfflineModeActive: Bool = false

    private init() {
        activateAudioSessionIfNeeded()
        setupRemoteCommands()
        setupInterruptionObserver()
        prepareSilentAudioAnchor()
    }

    // MARK: - Audio Session, Silent Background Anchor & Lock Screen Controls
    private func activateAudioSessionIfNeeded() {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(
                .playback,
                mode: .default,
                options: [.defaultToSpeaker, .allowBluetooth, .allowBluetoothA2DP, .allowAirPlay]
            )
            try session.setActive(true)
        } catch {}
    }

    /// Generates an in-memory 1-second silent PCM WAV file so iOS keeps the app
    /// alive in background & Lock Screen and preserves MPNowPlayingInfoCenter ownership.
    private func prepareSilentAudioAnchor() {
        let sampleRate: UInt32 = 8000
        let numSamples: UInt32 = 8000 // 1 second
        let dataSize = numSamples * 2
        let chunkSize = 36 + dataSize

        var wav = Data()
        func appendLE<T: FixedWidthInteger>(_ val: T) {
            var v = val.littleEndian
            withUnsafeBytes(of: &v) { wav.append(contentsOf: $0) }
        }

        wav.append(contentsOf: "RIFF".utf8)
        appendLE(chunkSize)
        wav.append(contentsOf: "WAVE".utf8)
        wav.append(contentsOf: "fmt ".utf8)
        appendLE(UInt32(16))
        appendLE(UInt16(1)) // PCM
        appendLE(UInt16(1)) // Mono
        appendLE(sampleRate)
        appendLE(sampleRate * 2)
        appendLE(UInt16(2))
        appendLE(UInt16(16))
        wav.append(contentsOf: "data".utf8)
        appendLE(dataSize)
        wav.append(Data(count: Int(dataSize)))

        do {
            let player = try AVAudioPlayer(data: wav)
            player.numberOfLoops = -1
            player.volume = 0.001
            player.prepareToPlay()
            self.silentAudioPlayer = player
        } catch {}
    }

    private func updateSilentAnchor() {
        if isPlaying && isBackgroundPlayEnabled {
            activateAudioSessionIfNeeded()
            if silentAudioPlayer?.isPlaying == false {
                silentAudioPlayer?.play()
            }
        } else {
            if silentAudioPlayer?.isPlaying == true {
                silentAudioPlayer?.pause()
            }
        }
    }

    private func setupInterruptionObserver() {
        NotificationCenter.default.addObserver(
            forName: AVAudioSession.interruptionNotification,
            object: AVAudioSession.sharedInstance(),
            queue: .main
        ) { [weak self] notification in
            guard let self = self,
                  let info = notification.userInfo,
                  let typeVal = info[AVAudioSessionInterruptionTypeKey] as? UInt,
                  let type = AVAudioSession.InterruptionType(rawValue: typeVal) else { return }

            if type == .began {
                // Incoming phone call, FaceTime, or system audio interruption -> auto pause
                self.pause()
            } else if type == .ended {
                let optionsVal = info[AVAudioSessionInterruptionOptionKey] as? UInt ?? 0
                let options = AVAudioSession.InterruptionOptions(rawValue: optionsVal)
                if options.contains(.shouldResume) && self.currentTrack != nil {
                    self.play()
                }
            }
        }
    }

    private func setupRemoteCommands() {
        let center = MPRemoteCommandCenter.shared()
        center.playCommand.isEnabled = true
        center.playCommand.addTarget { [weak self] _ in
            self?.play()
            return .success
        }
        center.pauseCommand.isEnabled = true
        center.pauseCommand.addTarget { [weak self] _ in
            self?.pause()
            return .success
        }
        center.togglePlayPauseCommand.isEnabled = true
        center.togglePlayPauseCommand.addTarget { [weak self] _ in
            self?.togglePlayPause()
            return .success
        }
        center.nextTrackCommand.isEnabled = true
        center.nextTrackCommand.addTarget { [weak self] _ in
            self?.nextTrack()
            return .success
        }
        center.previousTrackCommand.isEnabled = true
        center.previousTrackCommand.addTarget { [weak self] _ in
            self?.prevTrack()
            return .success
        }
        center.changePlaybackPositionCommand.isEnabled = true
        center.changePlaybackPositionCommand.addTarget { [weak self] event in
            if let posEvent = event as? MPChangePlaybackPositionCommandEvent {
                self?.seekTo(seconds: posEvent.positionTime)
                return .success
            }
            return .commandFailed
        }
    }

    private func updateNowPlayingInfo(force: Bool = false) {
        guard let track = currentTrack else {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
            return
        }

        if !force && Date().timeIntervalSince(lastNowPlayingUpdate) < 0.8 {
            return
        }
        lastNowPlayingUpdate = Date()

        let dur = durationSeconds > 0 ? durationSeconds : Double(parseDuration(track.duration)) / 1000.0
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: track.title,
            MPMediaItemPropertyArtist: track.artist.isEmpty ? track.channelName : track.artist,
            MPMediaItemPropertyAlbumTitle: "AURALIX",
            MPMediaItemPropertyPlaybackDuration: dur,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: currentTimeSeconds,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? Double(playbackSpeed) : 0.0
        ]

        if cachedArtworkVideoId == track.id, let img = cachedArtworkImage {
            info[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: img.size) { _ in img }
        } else if let url = URL(string: track.thumbnailUrl) {
            let vid = track.id
            URLSession.shared.dataTask(with: url) { [weak self] data, _, _ in
                guard let self = self, let data = data, let img = UIImage(data: data) else { return }
                DispatchQueue.main.async {
                    self.cachedArtworkVideoId = vid
                    self.cachedArtworkImage = img
                    if self.currentTrack?.id == vid {
                        var currentInfo = MPNowPlayingInfoCenter.default().nowPlayingInfo ?? [:]
                        currentInfo[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: img.size) { _ in img }
                        MPNowPlayingInfoCenter.default().nowPlayingInfo = currentInfo
                    }
                }
            }.resume()
        }

        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    // MARK: - WebView JS Command Listener
    func setCommandListener(_ listener: ((String) -> Void)?) {
        self.commandListener = listener
    }

    private func sendCommand(_ command: String) {
        DispatchQueue.main.async {
            self.commandListener?(command)
        }
    }

    // MARK: - Playback API
    func play(track: VideoItem, queue: [VideoItem] = []) {
        playTrack(track, queue: queue)
    }

    func stopAllAudioSources() {
        sendCommand("stopVideo()")
        sendCommand("setPlaybackState(false)")
        stopOfflinePlayback()
    }

    func playTrack(_ track: VideoItem, queue: [VideoItem] = []) {
        DispatchQueue.main.async {
            // Guarantee previous audio source is 100% stopped and released before new track starts
            self.stopAllAudioSources()

            self.lastTrackChangeTime = Date()
            self.pendingSeekTarget = nil
            self.lastSeekTimestamp = .distantPast
            self.currentTrack = track
            self.isPlaying = true
            self.currentTimeSeconds = 0
            self.durationSeconds = Double(self.parseDuration(track.duration)) / 1000.0

            UserDataRepository.shared.addToHistory(track)

            let isDownloaded = UserDataRepository.shared.isDownloaded(id: track.id)

            if !isDownloaded {
                if !queue.isEmpty {
                    self.playbackQueue = queue
                    if queue.count <= 2 {
                        self.checkAndFetchSimilarTracks(for: track)
                    }
                } else if !self.playbackQueue.contains(where: { $0.id == track.id }) {
                    self.playbackQueue = [track]
                    self.checkAndFetchSimilarTracks(for: track)
                }
            } else {
                if !queue.isEmpty {
                    self.playbackQueue = queue
                } else if !self.playbackQueue.contains(where: { $0.id == track.id }) {
                    self.playbackQueue = [track]
                }
            }

            self.updateSilentAnchor()

            if isDownloaded {
                self.startOfflinePlayback(for: track)
            } else {
                self.stopOfflinePlayback()
                self.sendCommand("loadVideo('\(track.id)')")
                self.sendCommand("setPlaybackState(true)")
            }
            self.updateNowPlayingInfo(force: true)
            ListenTogetherEngine.shared.onHostTrackChanged(track)
        }
    }

    private func startOfflinePlayback(for track: VideoItem) {
        stopOfflinePlayback()
        // Double guarantee online player is silenced
        sendCommand("stopVideo()")
        sendCommand("setPlaybackState(false)")

        isOfflineModeActive = true
        let dur = durationSeconds > 0 ? durationSeconds : Double(parseDuration(track.duration)) / 1000.0
        durationSeconds = dur > 0 ? dur : 180.0
        currentTimeSeconds = 0.0

        if let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first {
            let fileUrl = docs.appendingPathComponent("downloads/\(track.id).m4a")
            if FileManager.default.fileExists(atPath: fileUrl.path) {
                do {
                    activateAudioSessionIfNeeded()
                    let player = try AVAudioPlayer(contentsOf: fileUrl)
                    player.numberOfLoops = isLoopEnabled ? -1 : 0
                    player.prepareToPlay()
                    player.play()
                    self.offlineAudioPlayer = player
                } catch {
                    print("Offline player error: \(error)")
                }
            }
        }
        resumeOfflineTimer()
    }

    private func resumeOfflineTimer() {
        offlineTimer?.invalidate()
        offlineTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            guard let self = self, self.isPlaying else { return }
            if let player = self.offlineAudioPlayer, player.isPlaying {
                self.currentTimeSeconds = player.currentTime
                if player.duration > 0 {
                    self.durationSeconds = max(self.durationSeconds, player.duration)
                }
            } else {
                let next = min(self.currentTimeSeconds + 0.5, self.durationSeconds)
                self.currentTimeSeconds = next
                if next >= self.durationSeconds && self.durationSeconds > 0 {
                    if self.isLoopEnabled {
                        self.seekTo(seconds: 0)
                    } else if self.isAutoplayEnabled {
                        self.nextTrack()
                    } else {
                        self.pause()
                    }
                    return
                }
            }
            self.updateNowPlayingInfo(force: false)
        }
    }

    func stopOfflinePlayback() {
        isOfflineModeActive = false
        offlineTimer?.invalidate()
        offlineTimer = nil
        offlineAudioPlayer?.stop()
        offlineAudioPlayer = nil
    }

    func setQueue(_ queue: [VideoItem]) {
        guard !queue.isEmpty else { return }
        DispatchQueue.main.async {
            self.playbackQueue = queue
        }
    }

    func isSongFinished() -> Bool {
        let cur = currentTimeSeconds
        let dur = durationSeconds
        let elapsed = Date().timeIntervalSince(lastTrackChangeTime)
        if dur > 10.0 {
            return cur >= (dur - 4.0) || (elapsed > 15.0 && cur >= (dur - 6.0))
        }
        if dur > 2.0 {
            return cur >= (dur - 1.0)
        }
        return false
    }

    func setPlayingState(_ playing: Bool) {
        if !playing && Date().timeIntervalSince(lastTrackChangeTime) < 2.5 {
            return
        }
        DispatchQueue.main.async {
            if self.isPlaying != playing {
                self.isPlaying = playing
                self.updateSilentAnchor()
                self.updateNowPlayingInfo(force: true)
            }
        }
    }

    func play() {
        DispatchQueue.main.async {
            self.isPlaying = true
            self.updateSilentAnchor()
            if self.isOfflineModeActive {
                self.offlineAudioPlayer?.play()
                self.resumeOfflineTimer()
            } else {
                self.sendCommand("setPlaybackState(true)")
            }
            self.updateNowPlayingInfo(force: true)
            ListenTogetherEngine.shared.onHostPlayStateChanged(isPlaying: true, positionMs: Int64(self.currentTimeSeconds * 1000))
        }
    }

    func pause() {
        DispatchQueue.main.async {
            self.lastTrackChangeTime = .distantPast
            self.isPlaying = false
            self.updateSilentAnchor()
            if self.isOfflineModeActive {
                self.offlineAudioPlayer?.pause()
                self.offlineTimer?.invalidate()
            } else {
                self.sendCommand("setPlaybackState(false)")
            }
            self.updateNowPlayingInfo(force: true)
            ListenTogetherEngine.shared.onHostPlayStateChanged(isPlaying: false, positionMs: Int64(self.currentTimeSeconds * 1000))
        }
    }

    func togglePlayPause() {
        if isPlaying { pause() } else { play() }
    }

    func updateProgress(current: Double, duration: Double) {
        if isOfflineModeActive { return } // Managed by offline timer
        DispatchQueue.main.async {
            // Debounce stale progress updates during seeking
            if let pending = self.pendingSeekTarget {
                let elapsed = Date().timeIntervalSince(self.lastSeekTimestamp)
                if elapsed < 1.8 {
                    if abs(current - pending) > 2.0 {
                        return
                    }
                    self.pendingSeekTarget = nil
                } else {
                    self.pendingSeekTarget = nil
                }
            }

            // Debounce stale progress immediately after track change
            let elapsedTrack = Date().timeIntervalSince(self.lastTrackChangeTime)
            if elapsedTrack < 1.8 && current > 4.0 {
                return
            }

            self.currentTimeSeconds = current
            if duration > 0 {
                self.durationSeconds = duration
            }
            self.updateNowPlayingInfo(force: false)
        }
    }

    func seekTo(seconds: Double) {
        DispatchQueue.main.async {
            let dur = self.durationSeconds
            let maxSafe = dur > 2.0 ? (dur - 1.0) : dur
            let target = dur > 0 ? min(max(0, seconds), maxSafe) : max(0, seconds)
            self.pendingSeekTarget = target
            self.lastSeekTimestamp = Date()
            self.currentTimeSeconds = target
            if self.isOfflineModeActive {
                self.offlineAudioPlayer?.currentTime = target
            } else {
                self.sendCommand("seekTo(\(target))")
            }
            self.updateNowPlayingInfo(force: true)
            ListenTogetherEngine.shared.onHostSeek(positionMs: Int64(target * 1000))
        }
    }

    func seekTo(position: Int64) {
        seekTo(seconds: Double(position) / 1000.0)
    }

    func seekBy(seconds: Int) {
        let dur = self.durationSeconds
        let maxSafe = dur > 2.0 ? (dur - 1.0) : dur
        let rawNew = currentTimeSeconds + Double(seconds)
        let newTime = dur > 0 ? min(max(0, rawNew), maxSafe) : max(0, rawNew)
        DispatchQueue.main.async {
            self.currentTimeSeconds = newTime
            if self.isOfflineModeActive {
                self.offlineAudioPlayer?.currentTime = newTime
            } else {
                self.sendCommand("seekBy(\(seconds))")
            }
            self.updateNowPlayingInfo(force: true)
        }
    }

    func next() { nextTrack() }
    func previous() { prevTrack() }

    private var isFetchingSimilar = false

    func checkAndFetchSimilarTracks(for track: VideoItem) {
        guard !isFetchingSimilar && !isOfflineModeActive else { return }
        isFetchingSimilar = true
        Task {
            let similar = await YouTubeSearchEngine.getRelatedVideos(video: track, isMusicOnly: true)
            let existingIds = Set(self.playbackQueue.map { $0.id })
            let newUnique = similar.filter { !existingIds.contains($0.id) }
            if !newUnique.isEmpty {
                await MainActor.run {
                    self.playbackQueue.append(contentsOf: newUnique)
                }
            }
            self.isFetchingSimilar = false
        }
    }

    func nextTrack() {
        guard let current = currentTrack else { return }

        if isOfflineModeActive {
            let offlineList = UserDataRepository.shared.offlineTracks
            if !offlineList.isEmpty {
                let idx = offlineList.firstIndex(where: { $0.id == current.id }) ?? -1
                let nextIdx = (idx >= 0 && idx < offlineList.count - 1) ? idx + 1 : 0
                playTrack(offlineList[nextIdx], queue: offlineList)
            } else {
                seekTo(seconds: 0)
            }
            return
        }

        let queue = playbackQueue.isEmpty ? VideoRepository.sampleVideos : playbackQueue
        guard !queue.isEmpty else { return }
        if isShuffled && queue.count > 1 {
            let candidates = queue.filter { $0.id != current.id }
            if let randomTrack = candidates.randomElement() {
                playTrack(randomTrack, queue: queue)
                return
            }
        }
        let idx = queue.firstIndex(where: { $0.id == current.id }) ?? -1
        if idx >= queue.count - 2 || queue.count <= 2 {
            checkAndFetchSimilarTracks(for: current)
        }
        let nextIdx = (idx >= 0 && idx < queue.count - 1) ? idx + 1 : 0
        playTrack(queue[nextIdx], queue: queue)
    }

    func prevTrack() {
        guard let current = currentTrack else { return }

        if isOfflineModeActive {
            if currentTimeSeconds > 3.0 {
                seekTo(seconds: 0)
                return
            }
            let offlineList = UserDataRepository.shared.offlineTracks
            if !offlineList.isEmpty {
                let idx = offlineList.firstIndex(where: { $0.id == current.id }) ?? 0
                let prevIdx = idx > 0 ? idx - 1 : offlineList.count - 1
                playTrack(offlineList[prevIdx], queue: offlineList)
            } else {
                seekTo(seconds: 0)
            }
            return
        }

        if currentTimeSeconds > 3.0 {
            seekTo(seconds: 0)
            return
        }
        let queue = playbackQueue.isEmpty ? VideoRepository.sampleVideos : playbackQueue
        guard !queue.isEmpty else { return }
        let idx = queue.firstIndex(where: { $0.id == current.id }) ?? 0
        let prevIdx = idx > 0 ? idx - 1 : queue.count - 1
        playTrack(queue[prevIdx], queue: queue)
    }

    func stop() {
        DispatchQueue.main.async {
            self.isPlaying = false
            self.currentTrack = nil
            self.currentTimeSeconds = 0
            self.durationSeconds = 0
            self.updateSilentAnchor()
            self.sendCommand("setPlaybackState(false)")
            self.updateNowPlayingInfo(force: true)
        }
    }

    func toggleBackgroundPlay() {
        DispatchQueue.main.async {
            self.isBackgroundPlayEnabled.toggle()
            self.updateSilentAnchor()
        }
    }

    func toggleShuffle() {
        DispatchQueue.main.async {
            self.isShuffled.toggle()
        }
    }

    func toggleRepeat() {
        DispatchQueue.main.async {
            switch self.repeatMode {
            case .off:
                self.repeatMode = .all
                self.isLoopEnabled = false
            case .all:
                self.repeatMode = .one
                self.isLoopEnabled = true
            case .one:
                self.repeatMode = .off
                self.isLoopEnabled = false
            }
        }
    }

    func toggleLoop() {
        DispatchQueue.main.async {
            self.isLoopEnabled.toggle()
            self.repeatMode = self.isLoopEnabled ? .one : .off
        }
    }

    func toggleAutoplay() {
        DispatchQueue.main.async {
            self.isAutoplayEnabled.toggle()
        }
    }

    func toggleCaptions() {
        DispatchQueue.main.async {
            self.isCaptionsEnabled.toggle()
            self.sendCommand("setCaptions(\(self.isCaptionsEnabled))")
        }
    }

    func setPlaybackSpeed(_ speed: Float) {
        DispatchQueue.main.async {
            self.playbackSpeed = speed
            self.sendCommand("setPlaybackRate(\(speed))")
            self.updateNowPlayingInfo(force: true)
        }
    }

    func setPlaybackQuality(_ quality: String) {
        DispatchQueue.main.async {
            self.playbackQuality = quality
            let ytQuality: String
            if quality.contains("1080") { ytQuality = "hd1080" }
            else if quality.contains("720") { ytQuality = "hd720" }
            else if quality.contains("480") { ytQuality = "large" }
            else if quality.contains("360") { ytQuality = "medium" }
            else { ytQuality = "default" }
            self.sendCommand("setPlaybackQuality('\(ytQuality)')")
        }
    }

    func setSleepTimer(minutes: Int?) {
        sleepTimerWorkItem?.cancel()
        DispatchQueue.main.async {
            self.sleepTimerMinutes = minutes
            guard let mins = minutes, mins > 0 else { return }
            let item = DispatchWorkItem { [weak self] in
                self?.pause()
                DispatchQueue.main.async {
                    self?.sleepTimerMinutes = nil
                }
            }
            self.sleepTimerWorkItem = item
            DispatchQueue.main.asyncAfter(deadline: .now() + Double(mins) * 60.0, execute: item)
        }
    }

    private func parseDuration(_ duration: String) -> Int64 {
        let parts = duration.split(separator: ":").compactMap { Int64($0) }
        switch parts.count {
        case 2: return (parts[0] * 60 + parts[1]) * 1000
        case 3: return (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
        default: return 0
        }
    }
}
