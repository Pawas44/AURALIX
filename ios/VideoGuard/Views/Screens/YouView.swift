import SwiftUI

struct YouView: View {
    let onBack: () -> Void
    let onVideoClick: (VideoItem) -> Void
    let onPlaylistClick: (String) -> Void

    let user = AccountRepository.getCurrentUser()
    @ObservedObject var userData = UserDataRepository.shared

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                // Profile Header
                HStack(spacing: 16) {
                    AsyncImage(url: URL(string: user.avatarUrl)) { image in
                        image.resizable().aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Circle().fill(Color.gray)
                    }
                    .frame(width: 72, height: 72)
                    .clipShape(Circle())

                    VStack(alignment: .leading, spacing: 4) {
                        Text(user.name)
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(AppTheme.youTubeTextPrimary)
                        Text(user.channelHandle)
                            .font(.system(size: 14))
                            .foregroundColor(AppTheme.youTubeTextSecondary)
                        Text("Member since \(user.memberSince)")
                            .font(.system(size: 12))
                            .foregroundColor(AppTheme.youTubeTextSecondary)
                    }
                    Spacer()
                }
                .padding(.horizontal, 16)
                .padding(.top, 16)

                // Quick Links
                HStack(spacing: 10) {
                    YouQuickCard(icon: "clock.arrow.circlepath", title: "History")
                    YouQuickCard(icon: "play.square.stack", title: "Playlists")
                    YouQuickCard(icon: "video", title: "Your videos")
                    YouQuickCard(icon: "arrow.down.circle", title: "Downloads")
                }
                .padding(.horizontal, 16)

                Divider().background(AppTheme.youTubeBorder)

                // History Section
                if !userData.watchHistory.isEmpty {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Text("History")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundColor(AppTheme.youTubeTextPrimary)
                            Spacer()
                            Text("View all")
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(AppTheme.youTubeTextSecondary)
                        }
                        .padding(.horizontal, 16)

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(userData.watchHistory.prefix(8)) { video in
                                    Button(action: { onVideoClick(video) }) {
                                        VStack(alignment: .leading, spacing: 6) {
                                            ZStack(alignment: .bottomTrailing) {
                                                AsyncImage(url: URL(string: video.thumbnailUrl)) { image in
                                                    image.resizable().aspectRatio(16/9, contentMode: .fit)
                                                } placeholder: {
                                                    Rectangle().fill(AppTheme.youTubeCardBg)
                                                }
                                                .frame(width: 150)
                                                .cornerRadius(8)
                                                .clipped()

                                                Text(video.duration)
                                                    .font(.system(size: 10, weight: .bold))
                                                    .foregroundColor(.white)
                                                    .padding(2)
                                                    .background(Color.black.opacity(0.8))
                                                    .cornerRadius(3)
                                                    .padding(4)
                                            }

                                            Text(video.title)
                                                .font(.system(size: 12, weight: .medium))
                                                .foregroundColor(AppTheme.youTubeTextPrimary)
                                                .lineLimit(2)
                                                .frame(width: 150, alignment: .leading)
                                        }
                                    }
                                }
                            }
                            .padding(.horizontal, 16)
                        }
                    }

                    Divider().background(AppTheme.youTubeBorder)
                }

                // Playlists Section
                VStack(alignment: .leading, spacing: 14) {
                    HStack {
                        Text("Playlists")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(AppTheme.youTubeTextPrimary)
                        Spacer()
                        Image(systemName: "plus")
                            .font(.system(size: 18))
                            .foregroundColor(AppTheme.youTubeTextSecondary)
                    }
                    .padding(.horizontal, 16)

                    VStack(spacing: 12) {
                        ForEach(userData.playlists) { playlist in
                            Button(action: { onPlaylistClick(playlist.id) }) {
                                HStack(spacing: 14) {
                                    AsyncImage(url: URL(string: playlist.thumbnailUrl)) { image in
                                        image.resizable().aspectRatio(contentMode: .fill)
                                    } placeholder: {
                                        RoundedRectangle(cornerRadius: 8).fill(AppTheme.youTubeCardBg)
                                    }
                                    .frame(width: 56, height: 56)
                                    .cornerRadius(8)
                                    .clipped()

                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(playlist.name)
                                            .font(.system(size: 15, weight: .medium))
                                            .foregroundColor(AppTheme.youTubeTextPrimary)
                                        Text("\(playlist.videoCount) videos\(playlist.isPrivate ? " • Private" : "")")
                                            .font(.system(size: 13))
                                            .foregroundColor(AppTheme.youTubeTextSecondary)
                                    }
                                    Spacer()
                                }
                                .padding(.horizontal, 16)
                            }
                        }
                    }
                }
            }
            .padding(.bottom, 100)
        }
        .background(AppTheme.youTubeDarkBg.ignoresSafeArea())
    }
}

struct YouQuickCard: View {
    let icon: String
    let title: String

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 20))
                .foregroundColor(AppTheme.youTubeTextPrimary)
            Text(title)
                .font(.system(size: 11, weight: .medium))
                .foregroundColor(AppTheme.youTubeTextPrimary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .background(AppTheme.youTubeCardBg)
        .cornerRadius(10)
    }
}

