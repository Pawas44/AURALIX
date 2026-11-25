import Foundation

struct VideoItem: Identifiable, Hashable, Codable {
    var id: String
    var title: String
    var channelName: String
    var channelAvatarUrl: String = ""
    var subscriberCount: String = "5.2M subscribers"
    var thumbnailUrl: String
    var views: String
    var uploadTime: String = ""
    var duration: String
    var description: String = ""
    var likes: String = "45K"
    var isMusicTrack: Bool = false
    var artist: String = ""
    var album: String = ""
    var lyrics: String = ""
    var category: String = "All"
    var mood: String = "Energize"
    var channelId: String = ""
    var originalTitle: String = ""
    var originalArtist: String = ""
    var originalArtworkUrl: String = ""
    var spotifyTrackId: String = ""
    var matchedYouTubeId: String = ""
    var matchedYouTubeTitle: String = ""
}
