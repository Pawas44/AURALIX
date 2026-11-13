import Foundation
import Combine

struct DownloadTask: Identifiable, Equatable {
    var id: String { videoId }
    let videoId: String
    let title: String
    let progress: Int
    let bytesText: String
    let isComplete: Bool
}

class UserDataRepository: ObservableObject {
    static let shared = UserDataRepository()

    private let keyHistory = "vg_history_json"
    private let keyPlaylists = "vg_playlists_json"
    private let keySubscriptions = "vg_subscriptions_json"
    private let keySearchHistory = "vg_search_history_json"
    private let keyOfflineTracks = "vg_offline_tracks_json"

    @Published var watchHistory: [VideoItem] = []
    @Published var playlists: [UserPlaylist] = []
    @Published var subscribedChannels: Set<String> = []
    @Published var searchHistory: [String] = []
    @Published var dislikedVideos: Set<String> = []
    @Published var offlineTracks: [VideoItem] = []
    @Published var currentDownload: DownloadTask? = nil

    private init() {
        loadAll()
    }

    private func loadAll() {
        let defaults = UserDefaults.standard

        if let data = defaults.data(forKey: keyHistory),
           let decoded = try? JSONDecoder().decode([VideoItem].self, from: data) {
            self.watchHistory = decoded
        }

        if let data = defaults.data(forKey: keyPlaylists),
           let decoded = try? JSONDecoder().decode([UserPlaylist].self, from: data),
           !decoded.isEmpty {
            self.playlists = decoded
        } else {
            self.playlists = [
                UserPlaylist(
                    id: "liked_videos",
                    title: "Liked videos",
                    description: "Videos you've liked",
                    videos: [],
                    isSystem: true,
                    iconType: "like"
                ),
                UserPlaylist(
                    id: "watch_later",
                    title: "Watch later",
                    description: "Saved for later viewing",
                    videos: [],
                    isSystem: true,
                    iconType: "watch_later"
                ),
                UserPlaylist(
                    id: "favorites",
                    title: "Favorites",
                    description: "Personal favorite tracks & clips",
                    videos: [],
                    isSystem: false,
                    iconType: "playlist"
                )
            ]
            savePlaylists()
        }

        if let subs = defaults.stringArray(forKey: keySubscriptions) {
            self.subscribedChannels = Set(subs)
        } else {
            self.subscribedChannels = ["The Weeknd", "Dua Lipa"]
            saveSubscriptions()
        }

        if let sh = defaults.stringArray(forKey: keySearchHistory) {
            self.searchHistory = sh
        }

        if let data = defaults.data(forKey: keyOfflineTracks),
           let decoded = try? JSONDecoder().decode([VideoItem].self, from: data) {
            self.offlineTracks = decoded
        }
    }

    // MARK: - Offline Tracks
    func isDownloaded(id: String) -> Bool {
        offlineTracks.contains { $0.id == id }
    }

    func startDownload(video: VideoItem) {
        if isDownloaded(id: video.id) {
            if let idx = offlineTracks.firstIndex(where: { $0.id == video.id }) {
                offlineTracks.remove(at: idx)
                saveOfflineTracks()
            }
            if let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first {
                let fileUrl = docs.appendingPathComponent("downloads/\(video.id).m4a")
                try? FileManager.default.removeItem(at: fileUrl)
            }
            return
        }

        Task { @MainActor in
            let totalBytes = 6_800_000.0
            for p in stride(from: 15, through: 100, by: 15) {
                let written = totalBytes * (Double(p) / 100.0)
                let mbNow = String(format: "%.1f", written / (1024.0 * 1024.0))
                let mbTotal = String(format: "%.1f", totalBytes / (1024.0 * 1024.0))
                self.currentDownload = DownloadTask(
                    videoId: video.id,
                    title: video.title,
                    progress: p,
                    bytesText: "\(mbNow) MB / \(mbTotal) MB",
                    isComplete: p >= 100
                )
                try? await Task.sleep(nanoseconds: 180_000_000)
            }

            if let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first {
                let dir = docs.appendingPathComponent("downloads")
                try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
                let fileUrl = dir.appendingPathComponent("\(video.id).m4a")
                self.writeValidWavAudio(to: fileUrl, durationSec: 180)
            }

            self.offlineTracks.removeAll { $0.id == video.id }
            self.offlineTracks.insert(video, at: 0)
            self.saveOfflineTracks()

            try? await Task.sleep(nanoseconds: 1_500_000_000)
            self.currentDownload = nil
        }
    }

    @discardableResult
    func toggleDownload(video: VideoItem) -> Bool {
        if let idx = offlineTracks.firstIndex(where: { $0.id == video.id }) {
            offlineTracks.remove(at: idx)
            saveOfflineTracks()
            return false
        } else {
            offlineTracks.insert(video, at: 0)
            saveOfflineTracks()
            return true
        }
    }

    private func saveOfflineTracks() {
        if let data = try? JSONEncoder().encode(offlineTracks) {
            UserDefaults.standard.set(data, forKey: keyOfflineTracks)
        }
    }

    private func writeValidWavAudio(to url: URL, durationSec: Int = 180) {
        let sampleRate: UInt32 = 44100
        let channels: UInt16 = 2
        let bitsPerSample: UInt16 = 16
        let byteRate = sampleRate * UInt32(channels) * UInt32(bitsPerSample) / 8
        let blockAlign = channels * bitsPerSample / 8
        let numSamples = sampleRate * UInt32(max(30, durationSec))
        let dataSize = numSamples * UInt32(channels) * UInt32(bitsPerSample / 8)
        let chunkSize = 36 + dataSize

        var wav = Data()
        func appendLE<T>(_ value: T) {
            var v = value
            withUnsafeBytes(of: &v) { wav.append(contentsOf: $0) }
        }

        wav.append(contentsOf: "RIFF".utf8)
        appendLE(chunkSize)
        wav.append(contentsOf: "WAVE".utf8)
        wav.append(contentsOf: "fmt ".utf8)
        appendLE(UInt32(16))
        appendLE(UInt16(1)) // PCM
        appendLE(channels)
        appendLE(sampleRate)
        appendLE(byteRate)
        appendLE(blockAlign)
        appendLE(bitsPerSample)
        wav.append(contentsOf: "data".utf8)
        appendLE(dataSize)

        let bufferSize = 4096
        var buffer = [UInt8](repeating: 0, count: bufferSize)
        var written: UInt32 = 0
        let baseFreq = 440.0
        while written < dataSize {
            let toWrite = min(UInt32(bufferSize), dataSize - written)
            for i in stride(from: 0, to: Int(toWrite), by: 4) {
                let sampleIdx = (written + UInt32(i)) / 4
                let time = Double(sampleIdx) / Double(sampleRate)
                let sampleVal = Int16(((sin(2.0 * .pi * baseFreq * time) + 0.5 * sin(2.0 * .pi * (baseFreq * 1.5) * time)) * 3200.0))
                let uVal = UInt16(bitPattern: sampleVal)
                buffer[i] = UInt8(uVal & 0xFF)
                buffer[i + 1] = UInt8((uVal >> 8) & 0xFF)
                buffer[i + 2] = UInt8(uVal & 0xFF)
                buffer[i + 3] = UInt8((uVal >> 8) & 0xFF)
            }
            wav.append(buffer, count: Int(toWrite))
            written += toWrite
        }

        try? wav.write(to: url)
    }

    // MARK: - Search History
    func addSearchQuery(_ query: String) {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        DispatchQueue.main.async {
            var current = self.searchHistory.filter { $0.caseInsensitiveCompare(trimmed) != .orderedSame }
            current.insert(trimmed, at: 0)
            self.searchHistory = Array(current.prefix(50))
            UserDefaults.standard.set(self.searchHistory, forKey: self.keySearchHistory)
        }
    }

    func removeSearchQuery(_ query: String) {
        DispatchQueue.main.async {
            self.searchHistory.removeAll { $0.caseInsensitiveCompare(query) == .orderedSame }
            UserDefaults.standard.set(self.searchHistory, forKey: self.keySearchHistory)
        }
    }

    func clearSearchHistory() {
        DispatchQueue.main.async {
            self.searchHistory = []
            UserDefaults.standard.set([], forKey: self.keySearchHistory)
        }
    }

    // MARK: - Watch History
    func addToHistory(_ video: VideoItem) {
        if AccountRepository.shared.isIncognito { return }
        DispatchQueue.main.async {
            var current = self.watchHistory.filter { $0.id != video.id }
            current.insert(video, at: 0)
            if current.count > 100 {
                current = Array(current.prefix(100))
            }
            self.watchHistory = current
            self.saveHistory()
        }
    }

    func removeFromHistory(_ videoId: String) {
        DispatchQueue.main.async {
            self.watchHistory.removeAll { $0.id == videoId }
            self.saveHistory()
        }
    }

    func clearHistory() {
        DispatchQueue.main.async {
            self.watchHistory = []
            self.saveHistory()
        }
    }

    func setOnlineHistory(_ onlineVideos: [VideoItem]) {
        guard !onlineVideos.isEmpty else { return }
        DispatchQueue.main.async {
            self.watchHistory = Array(onlineVideos.prefix(100))
            self.saveHistory()
        }
    }

    // MARK: - Playlists
    func setOnlinePlaylists(_ onlinePlaylists: [UserPlaylist]) {
        guard !onlinePlaylists.isEmpty else { return }
        DispatchQueue.main.async {
            let systemPlaylists = self.playlists.filter { $0.isSystem }
            let localCustom = self.playlists.filter { $0.id.hasPrefix("playlist_") && !$0.isSystem }
            let existingVideosMap = Dictionary(uniqueKeysWithValues: self.playlists.map { ($0.id, $0.videos) })

            let mergedOnline: [UserPlaylist] = onlinePlaylists.map { pl in
                var updated = pl
                if updated.videos.isEmpty, let cached = existingVideosMap[pl.id], !cached.isEmpty {
                    updated.videos = cached
                    if updated.customCoverUrl == nil {
                        updated.customCoverUrl = cached.first?.thumbnailUrl
                    }
                }
                return updated
            }

            self.playlists = systemPlaylists + mergedOnline + localCustom
            self.savePlaylists()
        }
    }

    func updatePlaylistVideos(playlistId: String, videos: [VideoItem]) {
        guard !videos.isEmpty else { return }
        if playlistId == "liked_videos" {
            updateLikedVideos(videos)
            return
        }
        if playlistId == "watch_later" {
            updateWatchLaterVideos(videos)
            return
        }
        DispatchQueue.main.async {
            if let idx = self.playlists.firstIndex(where: { $0.id == playlistId }) {
                var pl = self.playlists[idx]
                pl.videos = videos
                if pl.customCoverUrl == nil {
                    pl.customCoverUrl = videos.first?.thumbnailUrl
                }
                self.playlists[idx] = pl
                self.savePlaylists()
            }
        }
    }

    func updateWatchLaterVideos(_ videos: [VideoItem]) {
        guard !videos.isEmpty else { return }
        DispatchQueue.main.async {
            if let idx = self.playlists.firstIndex(where: { $0.id == "watch_later" }) {
                self.playlists[idx].videos = videos
            } else {
                self.playlists.append(UserPlaylist(
                    id: "watch_later",
                    title: "Watch later",
                    description: "Saved for later viewing",
                    videos: videos,
                    isSystem: true,
                    iconType: "watch_later"
                ))
            }
            self.savePlaylists()
        }
    }

    func updateLikedVideos(_ videos: [VideoItem]) {
        guard !videos.isEmpty else { return }
        DispatchQueue.main.async {
            if let idx = self.playlists.firstIndex(where: { $0.id == "liked_videos" }) {
                self.playlists[idx].videos = videos
            } else {
                self.playlists.insert(UserPlaylist(
                    id: "liked_videos",
                    title: "Liked videos",
                    description: "Videos you've liked",
                    videos: videos,
                    isSystem: true,
                    iconType: "like"
                ), at: 0)
            }
            self.savePlaylists()
        }
    }

    func isLiked(_ videoId: String) -> Bool {
        return playlists.first(where: { $0.id == "liked_videos" })?.videos.contains(where: { $0.id == videoId }) ?? false
    }

    func toggleLike(_ video: VideoItem) {
        DispatchQueue.main.async {
            self.dislikedVideos.remove(video.id)
            if let idx = self.playlists.firstIndex(where: { $0.id == "liked_videos" }) {
                var vids = self.playlists[idx].videos
                if vids.contains(where: { $0.id == video.id }) {
                    vids.removeAll { $0.id == video.id }
                } else {
                    vids.insert(video, at: 0)
                }
                self.playlists[idx].videos = vids
                self.savePlaylists()
            }
        }
    }

    func toggleLike(_ videoId: String) {
        if let match = watchHistory.first(where: { $0.id == videoId }) ??
            VideoRepository.sampleVideos.first(where: { $0.id == videoId }) ??
            MediaPlaybackService.shared.currentTrack {
            toggleLike(match)
        }
    }

    func isDisliked(_ videoId: String) -> Bool {
        return dislikedVideos.contains(videoId)
    }

    func toggleDislike(_ videoId: String) {
        DispatchQueue.main.async {
            if self.dislikedVideos.contains(videoId) {
                self.dislikedVideos.remove(videoId)
            } else {
                self.dislikedVideos.insert(videoId)
                if let idx = self.playlists.firstIndex(where: { $0.id == "liked_videos" }) {
                    self.playlists[idx].videos.removeAll { $0.id == videoId }
                    self.savePlaylists()
                }
            }
        }
    }

    func isWatchLater(_ videoId: String) -> Bool {
        return playlists.first(where: { $0.id == "watch_later" })?.videos.contains(where: { $0.id == videoId }) ?? false
    }

    func isSaved(_ videoId: String) -> Bool {
        return isWatchLater(videoId)
    }

    func toggleWatchLater(_ video: VideoItem) {
        DispatchQueue.main.async {
            if let idx = self.playlists.firstIndex(where: { $0.id == "watch_later" }) {
                var vids = self.playlists[idx].videos
                if vids.contains(where: { $0.id == video.id }) {
                    vids.removeAll { $0.id == video.id }
                } else {
                    vids.insert(video, at: 0)
                }
                self.playlists[idx].videos = vids
                self.savePlaylists()
            }
        }
    }

    func toggleSave(_ video: VideoItem) {
        toggleWatchLater(video)
    }

    @discardableResult
    func createPlaylist(title: String) -> UserPlaylist {
        let newPlaylist = UserPlaylist(
            id: "playlist_" + UUID().uuidString.prefix(8),
            title: title.isEmpty ? "New Playlist" : title,
            description: "Created playlist",
            videos: [],
            isSystem: false,
            iconType: "playlist"
        )
        DispatchQueue.main.async {
            self.playlists.append(newPlaylist)
            self.savePlaylists()
        }
        return newPlaylist
    }

    func addVideoToPlaylist(playlistId: String, video: VideoItem) {
        DispatchQueue.main.async {
            if let idx = self.playlists.firstIndex(where: { $0.id == playlistId }) {
                if !self.playlists[idx].videos.contains(where: { $0.id == video.id }) {
                    self.playlists[idx].videos.insert(video, at: 0)
                    self.savePlaylists()
                }
            }
        }
    }

    func removeVideoFromPlaylist(playlistId: String, videoId: String) {
        DispatchQueue.main.async {
            if let idx = self.playlists.firstIndex(where: { $0.id == playlistId }) {
                self.playlists[idx].videos.removeAll { $0.id == videoId }
                self.savePlaylists()
            }
        }
    }

    func deletePlaylist(playlistId: String) {
        DispatchQueue.main.async {
            self.playlists.removeAll { $0.id == playlistId && !$0.isSystem }
            self.savePlaylists()
        }
    }

    func clearPlaylist(playlistId: String) {
        DispatchQueue.main.async {
            if let idx = self.playlists.firstIndex(where: { $0.id == playlistId }) {
                self.playlists[idx].videos = []
                self.savePlaylists()
            }
        }
    }

    // MARK: - Subscriptions
    func setSubscribedChannels(_ channels: Set<String>) {
        guard !channels.isEmpty else { return }
        DispatchQueue.main.async {
            self.subscribedChannels = channels
            self.saveSubscriptions()
        }
    }

    func isSubscribed(_ channelName: String) -> Bool {
        return subscribedChannels.contains(channelName)
    }

    func toggleSubscription(_ channelName: String) {
        DispatchQueue.main.async {
            if self.subscribedChannels.contains(channelName) {
                self.subscribedChannels.remove(channelName)
            } else {
                self.subscribedChannels.insert(channelName)
            }
            self.saveSubscriptions()
        }
    }

    private func saveHistory() {
        if let data = try? JSONEncoder().encode(watchHistory) {
            UserDefaults.standard.set(data, forKey: keyHistory)
        }
    }

    func savePlaylists() {
        if let data = try? JSONEncoder().encode(playlists) {
            UserDefaults.standard.set(data, forKey: keyPlaylists)
        }
    }

    private func saveSubscriptions() {
        UserDefaults.standard.set(Array(subscribedChannels), forKey: keySubscriptions)
    }
}
