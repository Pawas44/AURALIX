import Foundation

struct AlbumItem: Identifiable, Hashable, Codable {
    let id: String
    var title: String
    var artist: String
    var coverUrl: String
    var year: String = "2024"
    var trackCount: Int = 0
    var releaseType: String = "Album" // "Album", "Mixtape", "EP"
    var tracks: [VideoItem] = []
    var isCurated: Bool = false

    var artworkUrl: String {
        get { coverUrl }
        set { coverUrl = newValue }
    }
}
