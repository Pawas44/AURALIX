import SwiftUI

struct YouTubeTopBar: View {
    let appMode: AppMode
    let onModeChanged: (AppMode) -> Void
    let onTitleClick: () -> Void
    let onSearchClick: () -> Void
    let isBackgroundPlay: Bool
    let onToggleBackgroundPlay: () -> Void
    let onProfileClick: () -> Void

    @ObservedObject var accountRepo = AccountRepository.shared

    var body: some View {
        HStack(spacing: 10) {
            // App Logo + Title
            Button(action: onTitleClick) {
                HStack(spacing: 6) {
                    if appMode == .youtube {
                        Image(systemName: "play.rectangle.fill")
                            .font(.system(size: 24))
                            .foregroundColor(AppTheme.youTubeRed)
                        Text("YouTube")
                            .font(.system(size: 19, weight: .bold))
                            .foregroundColor(AppTheme.youTubeTextPrimary)
                    } else {
                        Image(systemName: "music.note")
                            .font(.system(size: 22, weight: .bold))
                            .foregroundColor(AppTheme.ytMusicRedAccent)
                        Text("Music")
                            .font(.system(size: 19, weight: .bold))
                            .foregroundColor(AppTheme.ytMusicTextPrimary)
                    }
                }
            }

            Spacer()

            // Mode Toggle Pill (YouTube <-> Music)
            ModeToggle(currentMode: appMode, onModeChanged: onModeChanged)

            // Background Audio Play Toggle
            Button(action: onToggleBackgroundPlay) {
                Image(systemName: "headphones")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(isBackgroundPlay ? Color(hex: 0x4CAF50) : AppTheme.youTubeTextSecondary)
                    .padding(7)
                    .background(isBackgroundPlay ? Color(hex: 0x4CAF50).opacity(0.18) : Color.white.opacity(0.06))
                    .clipShape(Circle())
            }

            // Search Icon
            Button(action: onSearchClick) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundColor(appMode == .youtube ? AppTheme.youTubeTextPrimary : AppTheme.ytMusicTextPrimary)
            }

            // Account Avatar / Sign-in Button
            Button(action: onProfileClick) {
                if accountRepo.isIncognito {
                    Image(systemName: "eye.slash.fill")
                        .font(.system(size: 14))
                        .foregroundColor(.white)
                        .frame(width: 28, height: 28)
                        .background(Color(hex: 0x333333))
                        .clipShape(Circle())
                } else if let acc = accountRepo.currentAccount {
                    if let urlStr = acc.avatarUrl, let url = URL(string: urlStr) {
                        AsyncImage(url: url) { image in
                            image.resizable().aspectRatio(contentMode: .fill)
                        } placeholder: {
                            Text(acc.initial)
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(.white)
                                .frame(width: 28, height: 28)
                                .background(Color(argbHex: acc.avatarBgColorHex))
                        }
                        .frame(width: 28, height: 28)
                        .clipShape(Circle())
                    } else {
                        Text(acc.initial)
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(.white)
                            .frame(width: 28, height: 28)
                            .background(Color(argbHex: acc.avatarBgColorHex))
                            .clipShape(Circle())
                    }
                } else {
                    Image(systemName: "person.crop.circle")
                        .font(.system(size: 22))
                        .foregroundColor(appMode == .youtube ? AppTheme.youTubeTextPrimary : AppTheme.ytMusicTextPrimary)
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
    }
}
