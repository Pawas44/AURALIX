import SwiftUI

struct YouTubeTopBar: View {
    let appMode: AppMode
    let onSearchClick: () -> Void
    let onProfileClick: () -> Void

    let user = AccountRepository.getCurrentUser()

    var body: some View {
        HStack(spacing: 12) {
            switch appMode {
            case .youtube:
                HStack(spacing: 6) {
                    Image(systemName: "play.rectangle.fill")
                        .font(.system(size: 26))
                        .foregroundColor(AppTheme.youTubeRed)
                    Text("YouTube")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(AppTheme.youTubeTextPrimary)
                }
            case .youtubeMusic:
                HStack(spacing: 6) {
                    Image(systemName: "music.note")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(AppTheme.ytMusicRedAccent)
                    Text("Music")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(AppTheme.ytMusicTextPrimary)
                }
            }

            Spacer()

            GoogleCastButton(tint: appMode == .youtube ? AppTheme.youTubeTextPrimary : AppTheme.ytMusicTextPrimary)

            Button(action: {}) {
                Image(systemName: "bell")
                    .font(.system(size: 20))
                    .foregroundColor(appMode == .youtube ? AppTheme.youTubeTextPrimary : AppTheme.ytMusicTextPrimary)
            }

            Button(action: onSearchClick) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 20))
                    .foregroundColor(appMode == .youtube ? AppTheme.youTubeTextPrimary : AppTheme.ytMusicTextPrimary)
            }

            Button(action: onProfileClick) {
                AsyncImage(url: URL(string: user.avatarUrl)) { image in
                    image.resizable()
                        .aspectRatio(contentMode: .fill)
                } placeholder: {
                    Circle().fill(Color.gray)
                }
                .frame(width: 28, height: 28)
                .clipShape(Circle())
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
    }
}

