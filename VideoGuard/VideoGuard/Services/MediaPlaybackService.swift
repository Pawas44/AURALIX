import Foundation
import Combine

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

    @Published var playbackState = PlaybackState()
    private var timer: Timer?

    private init() {
        startTimer()
    }

    private func startTimer() {
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            if self.playbackState.isPlaying && self.playbackState.duration > 0 {
                let newPos = self.playbackState.currentPosition + 1000
                if newPos >= self.playbackState.duration {
                    self.next()
                } else {
                    self.playbackState.currentPosition = newPos
                }
            }
        }
    }

    func play(track: VideoItem, queue: [VideoItem] = []) {
        let activeQueue = queue.isEmpty ? [track] : queue
        let index = activeQueue.firstIndex(where: { $0.id == track.id }) ?? 0
        playbackState = PlaybackState(
            currentTrack: track,
            isPlaying: true,
            currentPosition: 0,
            duration: parseDuration(track.duration),
            queue: activeQueue,
            currentIndex: index,
            isShuffled: playbackState.isShuffled,
            repeatMode: playbackState.repeatMode
        )
    }

    func togglePlayPause() {
        playbackState.isPlaying.toggle()
    }

    func seekTo(position: Int64) {
        playbackState.currentPosition = position
    }

    func next() {
        guard !playbackState.queue.isEmpty else { return }

        let nextIndex: Int
        switch playbackState.repeatMode {
        case .one:
            nextIndex = playbackState.currentIndex
        case .all:
            nextIndex = (playbackState.currentIndex + 1) % playbackState.queue.count
        case .off:
            let nextCandidate = playbackState.currentIndex + 1
            if nextCandidate >= playbackState.queue.count {
                playbackState.isPlaying = false
                return
            }
            nextIndex = nextCandidate
        }

        let nextTrack = playbackState.queue[nextIndex]
        playbackState.currentTrack = nextTrack
        playbackState.currentIndex = nextIndex
        playbackState.currentPosition = 0
        playbackState.duration = parseDuration(nextTrack.duration)
        playbackState.isPlaying = true
    }

    func previous() {
        guard !playbackState.queue.isEmpty else { return }

        let prevIndex: Int
        if playbackState.currentPosition > 3000 || playbackState.currentIndex <= 0 {
            prevIndex = max(0, playbackState.currentIndex)
        } else {
            prevIndex = playbackState.currentIndex - 1
        }

        let prevTrack = playbackState.queue[prevIndex]
        playbackState.currentTrack = prevTrack
        playbackState.currentIndex = prevIndex
        playbackState.currentPosition = 0
        playbackState.duration = parseDuration(prevTrack.duration)
        playbackState.isPlaying = true
    }

    func toggleShuffle() {
        playbackState.isShuffled.toggle()
    }

    func toggleRepeat() {
        switch playbackState.repeatMode {
        case .off: playbackState.repeatMode = .all
        case .all: playbackState.repeatMode = .one
        case .one: playbackState.repeatMode = .off
        }
    }

    func stop() {
        playbackState = PlaybackState()
    }

    private func parseDuration(_ duration: String) -> Int64 {
        let parts = duration.split(separator: ":").compactMap { Int64($0) }
        switch parts.count {
        case 2:
            return (parts[0] * 60 + parts[1]) * 1000
        case 3:
            return (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
        default:
            return 0
        }
    }
}

