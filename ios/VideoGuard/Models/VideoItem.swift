import Foundation

struct VideoItem: Identifiable, Hashable, Codable {
    let id: String
    let title: String
    let channelName: String
    var channelAvatarUrl: String = ""
    var subscriberCount: String = ""
    let thumbnailUrl: String
    let views: String
    var uploadTime: String = ""
    let duration: String
    var description: String = ""
    var likes: String = ""
    var isMusicTrack: Bool = false
    var artist: String = ""
    var album: String = ""
    var lyrics: String = ""
    var category: String = ""
    var mood: String = ""
}
