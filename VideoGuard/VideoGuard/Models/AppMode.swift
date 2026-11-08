import Foundation

enum AppMode: String, CaseIterable, Identifiable {
    case youtube = "YouTube"
    case youtubeMusic = "Music"
    
    var id: String { rawValue }
}

