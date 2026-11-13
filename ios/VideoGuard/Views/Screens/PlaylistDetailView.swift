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
    @State private var showAddSongsSheet = false
    @State private var addSongQuery = ""
    @State private var addSongResults: [VideoItem] = []
    @State private var isSearchingAdd = false

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

                        Button(action: { showAddSongsSheet = true }) {
                            HStack(spacing: 6) {
                                Image(systemName: "plus")
                                Text("Add songs to this playlist")
                                    .fontWeight(.semibold)
                            }
                            .font(.system(size: 13))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(Color.white.opacity(0.08))
                            .cornerRadius(20)
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

                                        Button(action: {
                                            userData.removeVideoFromPlaylist(playlistId: playlist.id, videoId: vid.id)
                                            currentVideos.removeAll { $0.id == vid.id }
                                        }) {
                                            Image(systemName: "xmark")
                                                .font(.system(size: 13, weight: .semibold))
                                                .foregroundColor(Color.gray)
                                                .padding(8)
                                        }
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
        .sheet(isPresented: $showAddSongsSheet) {
            NavigationView {
                VStack(alignment: .leading, spacing: 14) {
                    HStack {
                        Image(systemName: "magnifyingglass")
                            .foregroundColor(.gray)
                        TextField("Search song or artist...", text: $addSongQuery)
                            .foregroundColor(.white)
                            .onChange(of: addSongQuery) { q in
                                let trimmed = q.trimmingCharacters(in: .whitespacesAndNewlines)
                                guard trimmed.count >= 2 else { return }
                                Task {
                                    isSearchingAdd = true
                                    let res = await YouTubeSearchEngine.searchVideos(query: trimmed, isMusicOnly: true)
                                    await MainActor.run {
                                        addSongResults = res
                                        isSearchingAdd = false
                                    }
                                }
                            }
                    }
                    .padding(10)
                    .background(Color.white.opacity(0.08))
                    .cornerRadius(10)
                    .padding(.horizontal, 16)
                    .padding(.top, 10)

                    if isSearchingAdd {
                        HStack {
                            Spacer()
                            ProgressView()
                                .tint(.white)
                                .padding()
                            Spacer()
                        }
                    } else {
                        List(addSongResults) { item in
                            let alreadyIn = currentVideos.contains(where: { $0.id == item.id })
                            HStack(spacing: 12) {
                                AsyncImage(url: URL(string: item.thumbnailUrl)) { img in
                                    img.resizable().aspectRatio(contentMode: .fill)
                                } placeholder: {
                                    Rectangle().fill(Color.white.opacity(0.08))
                                }
                                .frame(width: 44, height: 44)
                                .cornerRadius(6)
                                .clipped()

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(item.title)
                                        .font(.system(size: 13, weight: .medium))
                                        .foregroundColor(.white)
                                        .lineLimit(1)
                                    Text(item.channelName)
                                        .font(.system(size: 11))
                                        .foregroundColor(Color.gray)
                                }

                                Spacer()

                                Button(action: {
                                    if alreadyIn {
                                        userData.removeVideoFromPlaylist(playlistId: playlist.id, videoId: item.id)
                                        currentVideos.removeAll { $0.id == item.id }
                                    } else {
                                        userData.addVideoToPlaylist(playlistId: playlist.id, video: item)
                                        currentVideos.insert(item, at: 0)
                                    }
                                }) {
                                    Image(systemName: alreadyIn ? "checkmark.circle.fill" : "plus.circle")
                                        .font(.system(size: 20))
                                        .foregroundColor(alreadyIn ? AppTheme.ytMusicRedAccent : .white)
                                }
                            }
                            .listRowBackground(Color.clear)
                        }
                        .listStyle(.plain)
                    }
                }
                .background(Color(hex: 0x161824).ignoresSafeArea())
                .navigationTitle("Add to \(playlist.title)")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done") { showAddSongsSheet = false }
                            .foregroundColor(.white)
                    }
                }
            }
        }
    }

    private func refreshVideos() async {
        guard playlistId.hasPrefix("PL") || playlistId == "liked_videos" || playlistId == "watch_later" else {
            await MainActor.run { isLoading = false }
            return
        }
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
