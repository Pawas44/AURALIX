import Foundation

struct UserPlaylist: Identifiable, Hashable, Codable {
    let id: String
    var title: String
    var description: String = ""
    var videos: [VideoItem] = []
    var isSystem: Bool = false
    var iconType: String = "playlist"
    var customCoverUrl: String? = nil
    var totalVideosCount: Int? = nil

    var name: String { title }
    var videoCount: Int { totalVideosCount ?? videos.count }
    var thumbnailUrl: String {
        customCoverUrl ?? videos.first?.thumbnailUrl ?? ""
    }
    var isPrivate: Bool { isSystem }
}
