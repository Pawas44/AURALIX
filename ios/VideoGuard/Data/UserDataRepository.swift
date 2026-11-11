import Foundation
import Combine

class UserDataRepository: ObservableObject {
    static let shared = UserDataRepository()

    private let keyHistory = "vg_history_json"
    private let keyPlaylists = "vg_playlists_json"
    private let keySubscriptions = "vg_subscriptions_json"
    private let keySearchHistory = "vg_search_history_json"

    @Published var watchHistory: [VideoItem] = []
    @Published var playlists: [UserPlaylist] = []
    @Published var subscribedChannels: Set<String> = []
    @Published var searchHistory: [String] = []
    @Published var dislikedVideos: Set<String> = []

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

    private func savePlaylists() {
        if let data = try? JSONEncoder().encode(playlists) {
            UserDefaults.standard.set(data, forKey: keyPlaylists)
        }
    }

    private func saveSubscriptions() {
        UserDefaults.standard.set(Array(subscribedChannels), forKey: keySubscriptions)
    }
}
