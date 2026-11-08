import SwiftUI

struct PlaylistDetailView: View {
    let playlistId: String
    let appMode: AppMode
    let onBack: () -> Void
    let onVideoClick: (VideoItem) -> Void

    @ObservedObject var userData = UserDataRepository.shared

    var body: some View {
        let playlist = userData.playlists.first(where: { $0.id == playlistId })
        let videos = Array(VideoRepository.sampleVideos.prefix(min(playlist?.videoCount ?? 5, 10)))
        let bgColor = appMode == .youtube ? AppTheme.youTubeDarkBg : AppTheme.ytMusicBlack
        let accentColor = appMode == .youtube ? AppTheme.youTubeRed : AppTheme.ytMusicRedAccent

        ScrollView {
            VStack(spacing: 20) {
                // Top navigation
                HStack {
                    Button(action: onBack) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(AppTheme.youTubeTextPrimary)
                    }
                    Spacer()
                    Button(action: {}) {
                        Image(systemName: "ellipsis")
                            .font(.system(size: 18))
                            .foregroundColor(AppTheme.youTubeTextPrimary)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 10)

                // Playlist Image
                AsyncImage(url: URL(string: playlist?.thumbnailUrl ?? "")) { image in
                    image.resizable().aspectRatio(contentMode: .fill)
                } placeholder: {
                    Rectangle().fill(AppTheme.youTubeCardBg)
                }
                .frame(width: 220, height: 220)
                .cornerRadius(12)
                .clipped()
                .shadow(color: .black.opacity(0.5), radius: 8, x: 0, y: 4)

                // Playlist title & details
                VStack(spacing: 6) {
                    Text(playlist?.name ?? "Playlist")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundColor(AppTheme.youTubeTextPrimary)

                    Text("\(playlist?.videoCount ?? 0) songs\(playlist?.isPrivate == true ? " • Private" : "")")
                        .font(.system(size: 14))
                        .foregroundColor(AppTheme.youTubeTextSecondary)
                }

                // Play / Shuffle buttons
                HStack(spacing: 16) {
                    Button(action: {
                        if let first = videos.first {
                            onVideoClick(first)
                        }
                    }) {
                        HStack {
                            Image(systemName: "play.fill")
                            Text("Play all")
                        }
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(accentColor)
                        .cornerRadius(24)
                    }

                    Button(action: {}) {
                        HStack {
                            Image(systemName: "shuffle")
                            Text("Shuffle")
                        }
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(AppTheme.youTubeTextPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(AppTheme.youTubeChipBg)
                        .cornerRadius(24)
                    }
                }
                .padding(.horizontal, 24)

                Divider().background(AppTheme.youTubeBorder)

                // Tracks List
                VStack(spacing: 12) {
                    ForEach(Array(videos.enumerated()), id: \.element.id) { index, item in
                        Button(action: { onVideoClick(item) }) {
                            HStack(spacing: 12) {
                                Text("\(index + 1)")
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundColor(AppTheme.youTubeTextSecondary)
                                    .frame(width: 20)

                                AsyncImage(url: URL(string: item.thumbnailUrl)) { image in
                                    image.resizable().aspectRatio(contentMode: .fill)
                                } placeholder: {
                                    RoundedRectangle(cornerRadius: 6).fill(AppTheme.youTubeCardBg)
                                }
                                .frame(width: 48, height: 48)
                                .cornerRadius(6)
                                .clipped()

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(item.title)
                                        .font(.system(size: 14, weight: .medium))
                                        .foregroundColor(AppTheme.youTubeTextPrimary)
                                        .lineLimit(1)
                                    Text(item.isMusicTrack && !item.artist.isEmpty ? item.artist : item.channelName)
                                        .font(.system(size: 12))
                                        .foregroundColor(AppTheme.youTubeTextSecondary)
                                        .lineLimit(1)
                                }

                                Spacer()

                                Text(item.duration)
                                    .font(.system(size: 12))
                                    .foregroundColor(AppTheme.youTubeTextSecondary)
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 4)
                        }
                    }
                }
                .padding(.bottom, 60)
            }
        }
        .background(bgColor.ignoresSafeArea())
    }
}

