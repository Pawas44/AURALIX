import SwiftUI

struct AccountSheet: View {
    let appMode: AppMode
    let onDismiss: () -> Void
    let onModeSwitch: () -> Void

    let user = AccountRepository.getCurrentUser()

    var body: some View {
        let bgColor = appMode == .youtube ? AppTheme.youTubeCardBg : AppTheme.ytMusicCardBg
        let accentColor = appMode == .youtube ? AppTheme.youTubeRed : AppTheme.ytMusicRedAccent

        ZStack {
            bgColor.ignoresSafeArea()

            VStack(alignment: .leading, spacing: 20) {
                // Header User Profile
                HStack(spacing: 16) {
                    AsyncImage(url: URL(string: user.avatarUrl)) { image in
                        image.resizable().aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Circle().fill(Color.gray)
                    }
                    .frame(width: 56, height: 56)
                    .clipShape(Circle())

                    VStack(alignment: .leading, spacing: 4) {
                        Text(user.name)
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(AppTheme.youTubeTextPrimary)
                        Text(user.channelHandle)
                            .font(.system(size: 14))
                            .foregroundColor(AppTheme.youTubeTextSecondary)
                    }
                    Spacer()
                }

                // Premium Badge
                if user.isPremium {
                    HStack(spacing: 10) {
                        Image(systemName: "crown.fill")
                            .foregroundColor(accentColor)
                        Text(appMode == .youtube ? "YouTube Premium Member" : "YouTube Music Premium")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(accentColor)
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(accentColor.opacity(0.12))
                    .cornerRadius(10)
                }

                Divider().background(AppTheme.youTubeBorder)

                // Menu items
                VStack(spacing: 16) {
                    AccountMenuRow(icon: "person.crop.circle", title: "Your channel")
                    AccountMenuRow(icon: "gearshape", title: "Settings")
                    AccountMenuRow(icon: "clock.arrow.circlepath", title: "Your data in YouTube")
                    AccountMenuRow(icon: "questionmark.circle", title: "Help & feedback")

                    // Switch Mode row
                    Button(action: {
                        onModeSwitch()
                        onDismiss()
                    }) {
                        HStack(spacing: 16) {
                            Image(systemName: appMode == .youtube ? "music.note" : "play.rectangle")
                                .font(.system(size: 20))
                                .foregroundColor(accentColor)
                            Text(appMode == .youtube ? "Switch to YouTube Music" : "Switch to YouTube")
                                .font(.system(size: 15, weight: .medium))
                                .foregroundColor(accentColor)
                            Spacer()
                        }
                        .padding(.vertical, 6)
                    }
                }

                Spacer()
            }
            .padding(20)
        }
    }
}

struct AccountMenuRow: View {
    let icon: String
    let title: String

    var body: some View {
        HStack(spacing: 16) {
            Image(systemName: icon)
                .font(.system(size: 20))
                .foregroundColor(AppTheme.youTubeTextPrimary)
                .frame(width: 24)
            Text(title)
                .font(.system(size: 15))
                .foregroundColor(AppTheme.youTubeTextPrimary)
            Spacer()
        }
        .padding(.vertical, 6)
    }
}

