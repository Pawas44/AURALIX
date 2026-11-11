import SwiftUI

struct YouTubeBottomNavBar: View {
    @Binding var selectedTab: YouTubeTab

    var body: some View {
        HStack {
            ForEach(YouTubeTab.allCases, id: \.self) { tab in
                Spacer()
                Button(action: { selectedTab = tab }) {
                    VStack(spacing: 3) {
                        Image(systemName: tab.icon)
                            .font(.system(size: 19))
                        Text(tab.rawValue)
                            .font(.system(size: 10, weight: selectedTab == tab ? .semibold : .regular))
                    }
                    .foregroundColor(selectedTab == tab ? .white : AppTheme.youTubeTextSecondary)
                }
                Spacer()
            }
        }
        .padding(.vertical, 8)
        .background(Color(hex: 0x0A0B12, alpha: 0.96))
        .overlay(
            Rectangle()
                .frame(height: 0.5)
                .foregroundColor(Color.white.opacity(0.12)),
            alignment: .top
        )
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
                            .font(.system(size: 19))
                        Text(tab.rawValue)
                            .font(.system(size: 10, weight: selectedTab == tab ? .semibold : .regular))
                    }
                    .foregroundColor(selectedTab == tab ? .white : AppTheme.ytMusicTextSecondary)
                }
                Spacer()
            }
        }
        .padding(.vertical, 8)
        .background(Color(hex: 0x08050D, alpha: 0.96))
        .overlay(
            Rectangle()
                .frame(height: 0.5)
                .foregroundColor(Color.white.opacity(0.12)),
            alignment: .top
        )
    }
}
