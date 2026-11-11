import Foundation
import Combine
import AVFoundation
import MediaPlayer

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

    // Compatibility wrapper for views referencing playbackState
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
    private var cachedArtworkImage: UIImage?
    private var cachedArtworkVideoId: String?

    private init() {
        setupAudioSession()
        setupRemoteCommands()
    }

    // MARK: - Audio Session & Lock Screen Controls
    private func setupAudioSession() {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default, options: [])
            try session.setActive(true)
        } catch {
            // Audio session failure is non-fatal in simulator
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

    private func updateNowPlayingInfo() {
        guard let track = currentTrack else {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
            return
        }

        var info: [String: Any] = [
            MPMediaItemPropertyTitle: track.title,
            MPMediaItemPropertyArtist: track.artist.isEmpty ? track.channelName : track.artist,
            MPMediaItemPropertyPlaybackDuration: durationSeconds > 0 ? durationSeconds : Double(parseDuration(track.duration)) / 1000.0,
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

    func playTrack(_ track: VideoItem, queue: [VideoItem] = []) {
        DispatchQueue.main.async {
            self.setupAudioSession()
            self.lastTrackChangeTime = Date()
            self.currentTrack = track
            self.isPlaying = true
            self.currentTimeSeconds = 0
            self.durationSeconds = Double(self.parseDuration(track.duration)) / 1000.0

            UserDataRepository.shared.addToHistory(track)

            if !queue.isEmpty {
                self.playbackQueue = queue
            } else if !self.playbackQueue.contains(where: { $0.id == track.id }) {
                self.playbackQueue = [track]
            }

            self.sendCommand("loadVideo('\(track.id)')")
            self.sendCommand("setPlaybackState(true)")
            self.updateNowPlayingInfo()
        }
    }

    func setQueue(_ queue: [VideoItem]) {
        guard !queue.isEmpty else { return }
        DispatchQueue.main.async {
            self.playbackQueue = queue
        }
    }

    func setPlayingState(_ playing: Bool) {
        if !playing && Date().timeIntervalSince(lastTrackChangeTime) < 2.5 {
            return
        }
        DispatchQueue.main.async {
            if self.isPlaying != playing {
                self.isPlaying = playing
                self.updateNowPlayingInfo()
            }
        }
    }

    func play() {
        DispatchQueue.main.async {
            self.setupAudioSession()
            self.isPlaying = true
            self.sendCommand("setPlaybackState(true)")
            self.updateNowPlayingInfo()
        }
    }

    func pause() {
        DispatchQueue.main.async {
            self.lastTrackChangeTime = .distantPast
            self.isPlaying = false
            self.sendCommand("setPlaybackState(false)")
            self.updateNowPlayingInfo()
        }
    }

    func togglePlayPause() {
        if isPlaying { pause() } else { play() }
    }

    func updateProgress(current: Double, duration: Double) {
        DispatchQueue.main.async {
            self.currentTimeSeconds = current
            if duration > 0 {
                self.durationSeconds = duration
            }
            self.updateNowPlayingInfo()
        }
    }

    func seekTo(seconds: Double) {
        DispatchQueue.main.async {
            self.currentTimeSeconds = max(0, seconds)
            self.sendCommand("seekTo(\(seconds))")
            self.updateNowPlayingInfo()
        }
    }

    func seekTo(position: Int64) {
        seekTo(seconds: Double(position) / 1000.0)
    }

    func seekBy(seconds: Int) {
        let newTime = max(0, currentTimeSeconds + Double(seconds))
        DispatchQueue.main.async {
            self.currentTimeSeconds = newTime
            self.sendCommand("seekBy(\(seconds))")
            self.updateNowPlayingInfo()
        }
    }

    func next() { nextTrack() }
    func previous() { prevTrack() }

    func nextTrack() {
        guard let current = currentTrack else { return }
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
        let nextIdx = (idx >= 0 && idx < queue.count - 1) ? idx + 1 : 0
        playTrack(queue[nextIdx], queue: queue)
    }

    func prevTrack() {
        guard let current = currentTrack else { return }
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
            self.sendCommand("setPlaybackState(false)")
            self.updateNowPlayingInfo()
        }
    }

    func toggleBackgroundPlay() {
        DispatchQueue.main.async {
            self.isBackgroundPlayEnabled.toggle()
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
            self.updateNowPlayingInfo()
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
