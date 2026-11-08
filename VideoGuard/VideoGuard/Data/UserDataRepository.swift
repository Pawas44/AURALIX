import Foundation
import Combine

class UserDataRepository: ObservableObject {
    static let shared = UserDataRepository()

    @Published var watchHistory: [VideoItem] = []
    @Published var likedVideos: Set<String> = []
    @Published var dislikedVideos: Set<String> = []
    @Published var savedVideos: Set<String> = []
    @Published var playlists: [UserPlaylist] = [
        UserPlaylist(
            id: "WL",
            name: "Watch Later",
            thumbnailUrl: "https://images.unsplash.com/photo-1611162616305-c69b3fa7fbe0?w=400",
            videoCount: 12,
            isPrivate: true
        ),
        UserPlaylist(
            id: "liked",
            name: "Liked Videos",
            thumbnailUrl: "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=400",
            videoCount: 847,
            isPrivate: true
        ),
        UserPlaylist(
            id: "workout",
            name: "Workout Mix 🏋️",
            thumbnailUrl: "https://images.unsplash.com/photo-1571019614242-c5c5dee9f50b?w=400",
            videoCount: 35,
            isPrivate: false
        ),
        UserPlaylist(
            id: "chill",
            name: "Chill Vibes 🌊",
            thumbnailUrl: "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=400",
            videoCount: 52,
            isPrivate: false
        ),
        UserPlaylist(
            id: "coding",
            name: "Coding Focus 💻",
            thumbnailUrl: "https://images.unsplash.com/photo-1555066931-4365d14bab8c?w=400",
            videoCount: 28,
            isPrivate: true
        )
    ]

    private init() {}

    func addToHistory(_ video: VideoItem) {
        var current = watchHistory.filter { $0.id != video.id }
        current.insert(video, at: 0)
        if current.count > 100 {
            current.removeLast()
        }
        watchHistory = current
    }

    func toggleLike(_ videoId: String) {
        dislikedVideos.remove(videoId)
        if likedVideos.contains(videoId) {
            likedVideos.remove(videoId)
        } else {
            likedVideos.insert(videoId)
        }
    }

    func toggleDislike(_ videoId: String) {
        likedVideos.remove(videoId)
        if dislikedVideos.contains(videoId) {
            dislikedVideos.remove(videoId)
        } else {
            dislikedVideos.insert(videoId)
        }
    }

    func isLiked(_ videoId: String) -> Bool {
        return likedVideos.contains(videoId)
    }

    func isDisliked(_ videoId: String) -> Bool {
        return dislikedVideos.contains(videoId)
    }

    func toggleSave(_ videoId: String) {
        if savedVideos.contains(videoId) {
            savedVideos.remove(videoId)
        } else {
            savedVideos.insert(videoId)
        }
    }

    func isSaved(_ videoId: String) -> Bool {
        return savedVideos.contains(videoId)
    }
}

