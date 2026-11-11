import SwiftUI

struct PlaylistDetailView: View {
    let playlistId: String
    var initialPlaylist: UserPlaylist? = nil
    let appMode: AppMode
    let onBack: () -> Void
    let onVideoSelected: (VideoItem, [VideoItem]) -> Void

    @ObservedObject var userData = UserDataRepository.shared
    @State private var currentVideos: [VideoItem] = []
    @State private var isLoading = false

    private var playlist: UserPlaylist {
        userData.playlists.first(where: { $0.id == playlistId })
        ?? initialPlaylist
        ?? UserPlaylist(id: playlistId, title: "Playlist", videos: [])
    }

    var body: some View {
        let accentColor = appMode == .youtube ? AppTheme.youTubeRed : AppTheme.ytMusicRedAccent

        VStack(spacing: 0) {
            HStack(spacing: 12) {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                        .padding(8)
                }

                Text(playlist.title)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
                    .lineLimit(1)

                Spacer()
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 6)

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // Hero Header
                    VStack(alignment: .leading, spacing: 14) {
                        HStack(spacing: 14) {
                            let coverUrl = playlist.thumbnailUrl.isEmpty ? (currentVideos.first?.thumbnailUrl ?? "") : playlist.thumbnailUrl
                            if !coverUrl.isEmpty {
                                AsyncImage(url: URL(string: coverUrl)) { img in
                                    img.resizable().aspectRatio(contentMode: .fill)
                                } placeholder: {
                                    Rectangle().fill(Color.white.opacity(0.08))
                                }
                                .frame(width: 110, height: 110)
                                .cornerRadius(10)
                                .clipped()
                            } else {
                                ZStack {
                                    RoundedRectangle(cornerRadius: 10)
                                        .fill(Color.white.opacity(0.08))
                                    Image(systemName: playlist.iconType == "like" ? "hand.thumbsup.fill" : "music.note.list")
                                        .font(.system(size: 36))
                                        .foregroundColor(.white.opacity(0.7))
                                }
                                .frame(width: 110, height: 110)
                            }

                            VStack(alignment: .leading, spacing: 6) {
                                Text(playlist.title)
                                    .font(.system(size: 20, weight: .bold))
                                    .foregroundColor(.white)
                                Text(playlist.description.isEmpty ? "Playlist" : playlist.description)
                                    .font(.system(size: 13))
                                    .foregroundColor(AppTheme.youTubeTextSecondary)
                                Text("\(currentVideos.count) videos")
                                    .font(.system(size: 12, weight: .medium))
                                    .foregroundColor(AppTheme.youTubeTextSecondary)
                            }
                        }

                        if !currentVideos.isEmpty {
                            HStack(spacing: 12) {
                                Button(action: {
                                    if let first = currentVideos.first {
                                        onVideoSelected(first, currentVideos)
                                    }
                                }) {
                                    HStack {
                                        Image(systemName: "play.fill")
                                        Text("Play all")
                                            .fontWeight(.semibold)
                                    }
                                    .foregroundColor(.black)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                                    .background(Color.white)
                                    .cornerRadius(22)
                                }

                                Button(action: {
                                    let shuffled = currentVideos.shuffled()
                                    if let first = shuffled.first {
                                        onVideoSelected(first, shuffled)
                                    }
                                }) {
                                    HStack {
                                        Image(systemName: "shuffle")
                                        Text("Shuffle")
                                            .fontWeight(.semibold)
                                    }
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                                    .background(accentColor.opacity(0.28))
                                    .cornerRadius(22)
                                }
                            }
                        }
                    }
                    .padding(16)
                    .background(
                        LinearGradient(
                            colors: [Color(hex: 0x2C3440), Color.clear],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )

                    if isLoading && currentVideos.isEmpty {
                        HStack {
                            Spacer()
                            ProgressView("Loading playlist...")
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                .foregroundColor(AppTheme.youTubeTextSecondary)
                                .padding(40)
                            Spacer()
                        }
                    } else if currentVideos.isEmpty {
                        Text("This playlist is empty.")
                            .foregroundColor(AppTheme.youTubeTextSecondary)
                            .padding(32)
                            .frame(maxWidth: .infinity)
                    } else {
                        LazyVStack(spacing: 12) {
                            ForEach(Array(currentVideos.enumerated()), id: \.element.id) { idx, vid in
                                Button(action: { onVideoSelected(vid, currentVideos) }) {
                                    HStack(spacing: 12) {
                                        Text("\(idx + 1)")
                                            .font(.system(size: 13))
                                            .foregroundColor(AppTheme.youTubeTextSecondary)
                                            .frame(width: 24)

                                        AsyncImage(url: URL(string: vid.thumbnailUrl)) { img in
                                            img.resizable().aspectRatio(16/9, contentMode: .fill)
                                        } placeholder: {
                                            Rectangle().fill(AppTheme.youTubeCardBg)
                                        }
                                        .frame(width: 112, height: 64)
                                        .cornerRadius(8)
                                        .clipped()

                                        VStack(alignment: .leading, spacing: 4) {
                                            Text(vid.title)
                                                .font(.system(size: 14, weight: .medium))
                                                .foregroundColor(.white)
                                                .lineLimit(2)
                                                .multilineTextAlignment(.leading)

                                            Text("\(vid.channelName) • \(vid.duration)")
                                                .font(.system(size: 12))
                                                .foregroundColor(AppTheme.youTubeTextSecondary)
                                                .lineLimit(1)
                                        }
                                        Spacer()
                                    }
                                    .padding(.horizontal, 14)
                                }
                            }
                        }
                        .padding(.bottom, 80)
                    }
                }
            }
            .refreshable {
                await refreshVideos()
            }
        }
        .background(LiquidGlassDefaults.liquidCanvas.ignoresSafeArea())
        .task(id: playlistId) {
            currentVideos = playlist.videos
            if currentVideos.isEmpty { isLoading = true }
            await refreshVideos()
        }
    }

    private func refreshVideos() async {
        let online = await YouTubeSyncEngine.shared.fetchPlaylistVideos(playlistId: playlistId)
        await MainActor.run {
            if !online.isEmpty {
                currentVideos = online
                userData.updatePlaylistVideos(playlistId: playlistId, videos: online)
            }
            isLoading = false
        }
    }
}
