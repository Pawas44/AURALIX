import SwiftUI

struct YouView: View {
    let onVideoSelected: (VideoItem, [VideoItem]) -> Void
    let onPlaylistClick: (UserPlaylist) -> Void
    let onOpenSearch: () -> Void
    let onOpenAccountSheet: () -> Void
    let onSignInClick: () -> Void

    @ObservedObject var accountRepo = AccountRepository.shared
    @ObservedObject var userData = UserDataRepository.shared

    @State private var showNewPlaylistAlert = false
    @State private var newPlaylistTitle = ""
    @State private var showLogoutConfirm = false
    @State private var isSyncing = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                // Header Top Actions
                HStack {
                    Spacer()
                    Button(action: {
                        isSyncing = true
                        Task {
                            await YouTubeSyncEngine.shared.syncAllUserData()
                            await MainActor.run { isSyncing = false }
                        }
                    }) {
                        Image(systemName: isSyncing ? "arrow.triangle.2.circlepath.circle.fill" : "arrow.triangle.2.circlepath")
                            .font(.system(size: 19))
                            .foregroundColor(.white)
                    }
                    Button(action: onOpenSearch) {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 19))
                            .foregroundColor(.white)
                            .padding(.leading, 12)
                    }
                    Button(action: onOpenAccountSheet) {
                        Image(systemName: "gearshape")
                            .font(.system(size: 19))
                            .foregroundColor(.white)
                            .padding(.leading, 12)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)

                // Profile Card
                if accountRepo.isIncognito {
                    HStack(spacing: 14) {
                        Image(systemName: "eye.slash.fill")
                            .font(.system(size: 32))
                            .foregroundColor(.white)
                            .frame(width: 64, height: 64)
                            .background(Color(hex: 0x333333))
                            .clipShape(Circle())

                        VStack(alignment: .leading, spacing: 4) {
                            Text("You're incognito")
                                .font(.system(size: 19, weight: .bold))
                                .foregroundColor(.white)
                            Text("Watch history is paused")
                                .font(.system(size: 13))
                                .foregroundColor(AppTheme.youTubeTextSecondary)
                        }
                        Spacer()
                        Button("Turn off") {
                            accountRepo.toggleIncognito()
                        }
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(Color(hex: 0x3EA6FF))
                    }
                    .padding(.horizontal, 16)
                } else if let user = accountRepo.currentAccount {
                    HStack(spacing: 16) {
                        if let avatarUrl = user.avatarUrl, let url = URL(string: avatarUrl) {
                            AsyncImage(url: url) { img in
                                img.resizable().aspectRatio(contentMode: .fill)
                            } placeholder: {
                                Circle().fill(Color(argbHex: user.avatarBgColorHex))
                            }
                            .frame(width: 68, height: 68)
                            .clipShape(Circle())
                        } else {
                            Text(user.initial)
                                .font(.system(size: 26, weight: .bold))
                                .foregroundColor(.white)
                                .frame(width: 68, height: 68)
                                .background(Color(argbHex: user.avatarBgColorHex))
                                .clipShape(Circle())
                        }

                        VStack(alignment: .leading, spacing: 4) {
                            Text(user.name)
                                .font(.system(size: 20, weight: .bold))
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
                    .padding(.horizontal, 16)

                    // Quick Action Pills
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            Button(action: onOpenAccountSheet) {
                                Label("Switch account", systemImage: "person.2")
                                    .font(.system(size: 12, weight: .medium))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 8)
                                    .liquidGlassPill()
                            }

                            Button(action: { accountRepo.toggleIncognito() }) {
                                Label("Turn on Incognito", systemImage: "eye.slash")
                                    .font(.system(size: 12, weight: .medium))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 8)
                                    .liquidGlassPill()
                            }

                            Button(action: { showLogoutConfirm = true }) {
                                Label("Log out", systemImage: "rectangle.portrait.and.arrow.right")
                                    .font(.system(size: 12, weight: .medium))
                                    .foregroundColor(Color(hex: 0xFF5252))
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 8)
                                    .liquidGlassPill()
                            }
                        }
                        .padding(.horizontal, 16)
                    }
                } else {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Sign in to YouTube")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(.white)
                        Text("Access your real watch history, liked videos, and saved playlists.")
                            .font(.system(size: 13))
                            .foregroundColor(AppTheme.youTubeTextSecondary)

                        Button(action: onSignInClick) {
                            HStack {
                                Image(systemName: "person.badge.key.fill")
                                Text("Sign in with Google")
                                    .fontWeight(.bold)
                            }
                            .foregroundColor(.black)
                            .padding(.horizontal, 20)
                            .padding(.vertical, 10)
                            .background(Color(hex: 0x3EA6FF))
                            .cornerRadius(22)
                        }
                    }
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .liquidGlass(cornerRadius: 16)
                    .padding(.horizontal, 16)
                }

                Divider().background(Color.white.opacity(0.1))

                // History Section
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text("History")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(.white)
                        Spacer()
                        if !userData.watchHistory.isEmpty {
                            Button("Clear") {
                                userData.clearHistory()
                            }
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(AppTheme.youTubeTextSecondary)
                        }
                    }
                    .padding(.horizontal, 16)

                    if userData.watchHistory.isEmpty {
                        Text("Videos you watch will appear here.")
                            .font(.system(size: 13))
                            .foregroundColor(AppTheme.youTubeTextSecondary)
                            .padding(.horizontal, 16)
                    } else {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(userData.watchHistory.prefix(15)) { video in
                                    Button(action: { onVideoSelected(video, userData.watchHistory) }) {
                                        VStack(alignment: .leading, spacing: 6) {
                                            ZStack(alignment: .bottomTrailing) {
                                                AsyncImage(url: URL(string: video.thumbnailUrl)) { img in
                                                    img.resizable().aspectRatio(16/9, contentMode: .fill)
                                                } placeholder: {
                                                    Rectangle().fill(AppTheme.youTubeCardBg)
                                                }
                                                .frame(width: 155, height: 88)
                                                .cornerRadius(8)
                                                .clipped()

                                                Text(video.duration)
                                                    .font(.system(size: 10, weight: .bold))
                                                    .foregroundColor(.white)
                                                    .padding(.horizontal, 4)
                                                    .padding(.vertical, 2)
                                                    .background(Color.black.opacity(0.8))
                                                    .cornerRadius(3)
                                                    .padding(4)
                                            }

                                            Text(video.title)
                                                .font(.system(size: 12, weight: .medium))
                                                .foregroundColor(.white)
                                                .lineLimit(2)
                                                .multilineTextAlignment(.leading)
                                                .frame(width: 155, alignment: .leading)
                                        }
                                    }
                                }
                            }
                            .padding(.horizontal, 16)
                        }
                    }
                }

                Divider().background(Color.white.opacity(0.1))

                // Playlists Section
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text("Playlists")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(.white)
                        Spacer()
                        Button(action: { showNewPlaylistAlert = true }) {
                            Image(systemName: "plus")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(.white)
                                .padding(6)
                        }
                    }
                    .padding(.horizontal, 16)

                    LazyVStack(spacing: 12) {
                        ForEach(userData.playlists) { playlist in
                            Button(action: { onPlaylistClick(playlist) }) {
                                HStack(spacing: 14) {
                                    if !playlist.thumbnailUrl.isEmpty {
                                        AsyncImage(url: URL(string: playlist.thumbnailUrl)) { img in
                                            img.resizable().aspectRatio(16/9, contentMode: .fill)
                                        } placeholder: {
                                            Rectangle().fill(AppTheme.youTubeCardBg)
                                        }
                                        .frame(width: 112, height: 64)
                                        .cornerRadius(8)
                                        .clipped()
                                    } else {
                                        ZStack {
                                            RoundedRectangle(cornerRadius: 8)
                                                .fill(Color.white.opacity(0.08))
                                            Image(systemName: playlist.iconType == "like" ? "hand.thumbsup.fill" : (playlist.iconType == "watch_later" ? "clock.fill" : "play.square.stack"))
                                                .font(.system(size: 22))
                                                .foregroundColor(.white)
                                        }
                                        .frame(width: 112, height: 64)
                                    }

                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(playlist.title)
                                            .font(.system(size: 15, weight: .semibold))
                                            .foregroundColor(.white)
                                        Text("\(playlist.videoCount) videos")
                                            .font(.system(size: 12))
                                            .foregroundColor(AppTheme.youTubeTextSecondary)
                                    }
                                    Spacer()
                                }
                                .padding(.horizontal, 16)
                            }
                        }
                    }
                }
                .padding(.bottom, 80)
            }
        }
        .task {
            await YouTubeSyncEngine.shared.syncAllUserData()
        }
        .alert("New Playlist", isPresented: $showNewPlaylistAlert) {
            TextField("Title", text: $newPlaylistTitle)
            Button("Create") {
                if !newPlaylistTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    userData.createPlaylist(title: newPlaylistTitle.trimmingCharacters(in: .whitespacesAndNewlines))
                    newPlaylistTitle = ""
                }
            }
            Button("Cancel", role: .cancel) { newPlaylistTitle = "" }
        }
        .alert("Log out?", isPresented: $showLogoutConfirm) {
            Button("Log out", role: .destructive) { accountRepo.signOut() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Are you sure you want to log out of your account?")
        }
    }
}
