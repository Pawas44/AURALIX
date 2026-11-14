import Foundation

struct VideoItem: Identifiable, Hashable, Codable {
    var id: String
    var title: String
    var channelName: String
    var channelAvatarUrl: String = ""
    var subscriberCount: String = ""
    var thumbnailUrl: String
    var views: String
    var uploadTime: String = ""
    var duration: String
    var description: String = ""
    var likes: String = ""
    var isMusicTrack: Bool = false
    var artist: String = ""
    var album: String = ""
    var lyrics: String = ""
    var category: String = ""
    var mood: String = ""
    var channelId: String = ""
}
