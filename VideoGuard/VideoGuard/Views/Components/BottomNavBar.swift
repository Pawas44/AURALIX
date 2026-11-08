import SwiftUI

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

struct YouTubeBottomNavBar: View {
    @Binding var selectedTab: YouTubeTab

    var body: some View {
        HStack {
            ForEach(YouTubeTab.allCases, id: \.self) { tab in
                Spacer()
                Button(action: { selectedTab = tab }) {
                    VStack(spacing: 3) {
                        Image(systemName: tab.icon)
                            .font(.system(size: 20))
                        Text(tab.rawValue)
                            .font(.system(size: 10, weight: selectedTab == tab ? .semibold : .regular))
                    }
                    .foregroundColor(selectedTab == tab ? AppTheme.youTubeTextPrimary : AppTheme.youTubeTextSecondary)
                }
                Spacer()
            }
        }
        .padding(.vertical, 8)
        .background(AppTheme.youTubeDarkBg)
    }
}

struct MusicBottomNavBar: View {
    @Binding var selectedTab: MusicTab

    var body: some View {
        HStack {
            ForEach(MusicTab.allCases, id: \.self) { tab in
                Spacer()
                Button(action: { selectedTab = tab }) {
                    VStack(spacing: 3) {
                        Image(systemName: tab.icon)
                            .font(.system(size: 20))
                        Text(tab.rawValue)
                            .font(.system(size: 10, weight: selectedTab == tab ? .semibold : .regular))
                    }
                    .foregroundColor(selectedTab == tab ? AppTheme.ytMusicTextPrimary : AppTheme.ytMusicTextSecondary)
                }
                Spacer()
            }
        }
        .padding(.vertical, 8)
        .background(AppTheme.ytMusicBlack)
    }
}

