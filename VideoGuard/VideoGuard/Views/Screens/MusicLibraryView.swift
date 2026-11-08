import SwiftUI

struct MusicLibraryView: View {
    let onBack: () -> Void
    let onTrackClick: (VideoItem) -> Void
    let onPlaylistClick: (String) -> Void

    @ObservedObject var userData = UserDataRepository.shared

    var body: some View {
        let playlists = userData.playlists
        let recentTracks = Array(VideoRepository.getMusicTracks().prefix(5))

        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Library")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(AppTheme.ytMusicTextPrimary)
                    .padding(.horizontal, 16)
                    .padding(.top, 16)

                // Quick Action Cards
                HStack(spacing: 12) {
                    QuickActionCard(icon: "clock.arrow.circlepath", title: "Recent")
                    QuickActionCard(icon: "heart.fill", title: "Liked")
                    QuickActionCard(icon: "arrow.down.circle.fill", title: "Downloads")
                }
                .padding(.horizontal, 16)

                // Playlists section
                Text("Playlists")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(AppTheme.ytMusicTextPrimary)
                    .padding(.horizontal, 16)
                    .padding(.top, 8)

                VStack(spacing: 12) {
                    ForEach(playlists) { playlist in
                        Button(action: { onPlaylistClick(playlist.id) }) {
                            HStack(spacing: 14) {
                                AsyncImage(url: URL(string: playlist.thumbnailUrl)) { image in
                                    image.resizable().aspectRatio(contentMode: .fill)
                                } placeholder: {
                                    RoundedRectangle(cornerRadius: 8).fill(AppTheme.ytMusicCardBg)
                                }
                                .frame(width: 56, height: 56)
                                .cornerRadius(8)
                                .clipped()

                                VStack(alignment: .leading, spacing: 4) {
                                    Text(playlist.name)
                                        .font(.system(size: 15, weight: .medium))
                                        .foregroundColor(AppTheme.ytMusicTextPrimary)
                                    Text("\(playlist.videoCount) songs\(playlist.isPrivate ? " • Private" : "")")
                                        .font(.system(size: 13))
                                        .foregroundColor(AppTheme.ytMusicTextSecondary)
                                }
                                Spacer()
                            }
                        }
                    }
                }
                .padding(.horizontal, 16)

                // Recently Played
                Text("Recently played")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(AppTheme.ytMusicTextPrimary)
                    .padding(.horizontal, 16)
                    .padding(.top, 8)

                VStack(spacing: 8) {
                    ForEach(recentTracks) { track in
                        MusicTrackRow(track: track) {
                            onTrackClick(track)
                        }
                    }
                }
            }
            .padding(.bottom, 100)
        }
        .background(AppTheme.ytMusicBlack.ignoresSafeArea())
    }
}

struct QuickActionCard: View {
    let icon: String
    let title: String

    var body: some View {
        Button(action: {}) {
            VStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.system(size: 24))
                    .foregroundColor(AppTheme.ytMusicTextPrimary)
                Text(title)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(AppTheme.ytMusicTextPrimary)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(AppTheme.ytMusicCardBg)
            .cornerRadius(12)
        }
    }
}

