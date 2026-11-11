import Foundation

enum AppMode: String, CaseIterable, Identifiable {
    case youtube = "YouTube"
    case youtubeMusic = "Music"

    var id: String { rawValue }
}

enum YouTubeTab: String, CaseIterable {
    case home = "Home"
    case shorts = "Shorts"
    case subscriptions = "Subscriptions"
    case you = "You"

    var icon: String {
        switch self {
        case .home: return "house.fill"
        case .shorts: return "play.square.stack.fill"
        case .subscriptions: return "rectangle.stack.badge.play.fill"
        case .you: return "person.crop.circle.fill"
        }
    }
}

enum MusicTab: String, CaseIterable {
    case home = "Home"
    case samples = "Samples"
    case explore = "Explore"
    case library = "Library"

    var icon: String {
        switch self {
        case .home: return "house.fill"
        case .samples: return "sparkles"
        case .explore: return "safari.fill"
        case .library: return "play.square.stack"
        }
    }
}
