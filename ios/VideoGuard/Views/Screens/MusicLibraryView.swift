import SwiftUI

struct MusicLibraryView: View {
    let onTrackSelected: (VideoItem, [VideoItem]) -> Void
    let onPlaylistClick: (UserPlaylist) -> Void
    let onOpenSearch: () -> Void
    let onOpenAccountSheet: () -> Void

    @ObservedObject var userData = UserDataRepository.shared
    @State private var showNewPlaylistAlert = false
    @State private var newPlaylistTitle = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                HStack {
                    Text("Library")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(.white)
                    Spacer()
                    Button(action: onOpenSearch) {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 19))
                            .foregroundColor(.white)
                    }
                    Button(action: onOpenAccountSheet) {
                        Image(systemName: "person.crop.circle")
                            .font(.system(size: 21))
                            .foregroundColor(.white)
                            .padding(.leading, 12)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 10)

                // Recent Music Activity
                if !userData.watchHistory.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Recent activity")
                            .font(.system(size: 17, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 16)

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(userData.watchHistory.prefix(12)) { track in
                                    Button(action: { onTrackSelected(track, userData.watchHistory) }) {
                                        VStack(alignment: .leading, spacing: 6) {
                                            AsyncImage(url: URL(string: track.thumbnailUrl)) { img in
                                                img.resizable().aspectRatio(contentMode: .fill)
                                            } placeholder: {
                                                Rectangle().fill(AppTheme.ytMusicCardBg)
                                            }
                                            .frame(width: 110, height: 110)
                                            .cornerRadius(8)
                                            .clipped()

                                            Text(track.title)
                                                .font(.system(size: 12, weight: .medium))
                                                .foregroundColor(.white)
                                                .lineLimit(1)
                                                .frame(width: 110, alignment: .leading)
                                        }
                                    }
                                }
                            }
                            .padding(.horizontal, 16)
                        }
                    }
                }

                // Playlists List
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text("Playlists")
                            .font(.system(size: 17, weight: .bold))
                            .foregroundColor(.white)
                        Spacer()
                        Button(action: { showNewPlaylistAlert = true }) {
                            Label("New", systemImage: "plus")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(.white)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .liquidGlassPill()
                        }
                    }
                    .padding(.horizontal, 16)

                    LazyVStack(spacing: 12) {
                        ForEach(userData.playlists) { playlist in
                            Button(action: { onPlaylistClick(playlist) }) {
                                HStack(spacing: 14) {
                                    if !playlist.thumbnailUrl.isEmpty {
                                        AsyncImage(url: URL(string: playlist.thumbnailUrl)) { img in
                                            img.resizable().aspectRatio(contentMode: .fill)
                                        } placeholder: {
                                            Rectangle().fill(AppTheme.ytMusicCardBg)
                                        }
                                        .frame(width: 60, height: 60)
                                        .cornerRadius(8)
                                        .clipped()
                                    } else {
                                        ZStack {
                                            RoundedRectangle(cornerRadius: 8)
                                                .fill(Color.white.opacity(0.08))
                                            Image(systemName: playlist.iconType == "like" ? "hand.thumbsup.fill" : "music.note.list")
                                                .font(.system(size: 22))
                                                .foregroundColor(.white)
                                        }
                                        .frame(width: 60, height: 60)
                                    }

                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(playlist.title)
                                            .font(.system(size: 15, weight: .semibold))
                                            .foregroundColor(.white)
                                        Text("\(playlist.videoCount) tracks")
                                            .font(.system(size: 12))
                                            .foregroundColor(AppTheme.ytMusicTextSecondary)
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
    }
}
