import SwiftUI

struct AccountSheet: View {
    let appMode: AppMode
    let onDismiss: () -> Void
    let onModeSwitch: () -> Void
    var onSignInClick: () -> Void = {}
    var onOpenJam: (() -> Void)? = nil

    @ObservedObject var accountRepo = AccountRepository.shared

    var body: some View {
        let bgColor = appMode == .youtube ? AppTheme.youTubeCardBg : AppTheme.ytMusicCardBg
        let accentColor = appMode == .youtube ? AppTheme.youTubeRed : AppTheme.ytMusicRedAccent

        ZStack {
            bgColor.ignoresSafeArea()

            VStack(alignment: .leading, spacing: 18) {
                if let user = accountRepo.currentAccount {
                    HStack(spacing: 16) {
                        if let avatarUrl = user.avatarUrl, let url = URL(string: avatarUrl) {
                            AsyncImage(url: url) { image in
                                image.resizable().aspectRatio(contentMode: .fill)
                            } placeholder: {
                                Circle().fill(Color(argbHex: user.avatarBgColorHex))
                            }
                            .frame(width: 54, height: 54)
                            .clipShape(Circle())
                        } else {
                            Text(user.initial)
                                .font(.system(size: 22, weight: .bold))
                                .foregroundColor(.white)
                                .frame(width: 54, height: 54)
                                .background(Color(argbHex: user.avatarBgColorHex))
                                .clipShape(Circle())
                        }

                        VStack(alignment: .leading, spacing: 3) {
                            Text(user.name)
                                .font(.system(size: 18, weight: .bold))
                                .foregroundColor(.white)
                            Text(user.handle)
                                .font(.system(size: 13))
                                .foregroundColor(AppTheme.youTubeTextSecondary)
                            if !user.email.isEmpty {
                                Text(user.email)
                                    .font(.system(size: 12))
                                    .foregroundColor(AppTheme.youTubeTextSecondary)
                            }
                        }
                        Spacer()
                    }
                } else {
                    HStack(spacing: 14) {
                        Image(systemName: "person.crop.circle.badge.plus")
                            .font(.system(size: 44))
                            .foregroundColor(Color(hex: 0x3EA6FF))

                        VStack(alignment: .leading, spacing: 4) {
                            Text("Not signed in")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundColor(.white)
                            Text("Sign in with Google to sync history & playlists")
                                .font(.system(size: 12))
                                .foregroundColor(AppTheme.youTubeTextSecondary)
                        }
                    }

                    Button(action: {
                        onDismiss()
                        onSignInClick()
                    }) {
                        HStack {
                            Image(systemName: "person.badge.key.fill")
                            Text("Sign in with Google")
                                .fontWeight(.semibold)
                        }
                        .foregroundColor(.black)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(Color(hex: 0x3EA6FF))
                        .cornerRadius(20)
                    }
                }

                Divider().background(AppTheme.youTubeBorder)

                VStack(spacing: 12) {
                    Button(action: {
                        onDismiss()
                        onOpenJam?()
                    }) {
                        AccountMenuRow(icon: "person.2.fill", title: "Listen Together (Live Jam)")
                    }

                    Button(action: {
                        accountRepo.toggleIncognito()
                        onDismiss()
                    }) {
                        AccountMenuRow(
                            icon: accountRepo.isIncognito ? "eye.fill" : "eye.slash.fill",
                            title: accountRepo.isIncognito ? "Turn off Incognito" : "Turn on Incognito"
                        )
                    }

                    Button(action: {
                        Task { await YouTubeSyncEngine.shared.syncAllUserData() }
                        onDismiss()
                    }) {
                        AccountMenuRow(icon: "arrow.triangle.2.circlepath", title: "Sync YouTube data now")
                    }

                    Button(action: {
                        onModeSwitch()
                        onDismiss()
                    }) {
                        HStack(spacing: 16) {
                            Image(systemName: appMode == .youtube ? "music.note" : "play.rectangle")
                                .font(.system(size: 20))
                                .foregroundColor(accentColor)
                                .frame(width: 24)
                            Text(appMode == .youtube ? "Switch to YouTube Music" : "Switch to YouTube")
                                .font(.system(size: 15, weight: .medium))
                                .foregroundColor(accentColor)
                            Spacer()
                        }
                        .padding(.vertical, 6)
                    }

                    if accountRepo.isSignedIn {
                        Button(action: {
                            accountRepo.signOut()
                            onDismiss()
                        }) {
                            HStack(spacing: 16) {
                                Image(systemName: "rectangle.portrait.and.arrow.right")
                                    .font(.system(size: 19))
                                    .foregroundColor(Color(hex: 0xFF5252))
                                    .frame(width: 24)
                                Text("Sign out")
                                    .font(.system(size: 15, weight: .medium))
                                    .foregroundColor(Color(hex: 0xFF5252))
                                Spacer()
                            }
                            .padding(.vertical, 6)
                        }
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
                .foregroundColor(.white)
                .frame(width: 24)
            Text(title)
                .font(.system(size: 15))
                .foregroundColor(.white)
            Spacer()
        }
        .padding(.vertical, 6)
    }
}
