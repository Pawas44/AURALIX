import Foundation

struct UserPlaylist: Identifiable, Hashable {
    let id: String
    let name: String
    let thumbnailUrl: String
    let videoCount: Int
    var isPrivate: Bool = false
    var videos: [VideoItem] = []
}

