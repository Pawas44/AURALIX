import SwiftUI

struct AddToPlaylistSheet: View {
    let video: VideoItem
    let onDismiss: () -> Void

    @ObservedObject var userData = UserDataRepository.shared
    @State private var showCreateAlert = false
    @State private var newPlaylistTitle = ""
    @State private var feedbackText: String? = nil

    var body: some View {
        NavigationView {
            VStack(alignment: .leading, spacing: 14) {
                // Video Header
                HStack(spacing: 12) {
                    AsyncImage(url: URL(string: video.thumbnailUrl)) { img in
                        img.resizable().aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Rectangle().fill(Color.white.opacity(0.08))
                    }
                    .frame(width: 50, height: 50)
                    .cornerRadius(8)
                    .clipped()

                    VStack(alignment: .leading, spacing: 3) {
                        Text("Save to playlist")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(.white)
                        Text(video.title)
                            .font(.system(size: 12))
                            .foregroundColor(Color(hex: 0xAAAAAA))
                            .lineLimit(1)
                    }
                    Spacer()
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)

                if let msg = feedbackText {
                    Text(msg)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(AppTheme.ytMusicRedAccent.opacity(0.3))
                        .cornerRadius(8)
                        .padding(.horizontal, 16)
                }

                Divider().background(Color.white.opacity(0.1))

                // Playlists List
                List {
                    ForEach(userData.playlists) { playlist in
                        let isIncluded = playlist.videos.contains(where: { $0.id == video.id })
                        Button(action: {
                            if isIncluded {
                                userData.removeVideoFromPlaylist(playlistId: playlist.id, videoId: video.id)
                                feedbackText = "Removed from \(playlist.title)"
                            } else {
                                userData.addVideoToPlaylist(playlistId: playlist.id, video: video)
                                feedbackText = "Added to \(playlist.title) ✓"
                            }
                        }) {
                            HStack(spacing: 14) {
                                Image(systemName: playlist.iconType == "like" ? "heart.fill" : "play.square.stack")
                                    .font(.system(size: 18))
                                    .foregroundColor(playlist.iconType == "like" ? AppTheme.ytMusicRedAccent : .white)
                                    .frame(width: 28)

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(playlist.title)
                                        .font(.system(size: 15, weight: .semibold))
                                        .foregroundColor(.white)
                                    Text("\(playlist.videoCount) tracks")
                                        .font(.system(size: 11))
                                        .foregroundColor(Color(hex: 0xAAAAAA))
                                }

                                Spacer()

                                Image(systemName: isIncluded ? "checkmark.circle.fill" : "circle")
                                    .font(.system(size: 20))
                                    .foregroundColor(isIncluded ? AppTheme.ytMusicRedAccent : Color.gray)
                            }
                            .padding(.vertical, 4)
                        }
                        .listRowBackground(Color.clear)
                    }

                    // New Playlist Row
                    Button(action: { showCreateAlert = true }) {
                        HStack(spacing: 12) {
                            Image(systemName: "plus.circle.fill")
                                .font(.system(size: 20))
                                .foregroundColor(AppTheme.ytMusicRedAccent)
                            Text("New playlist")
                                .font(.system(size: 15, weight: .bold))
                                .foregroundColor(.white)
                        }
                        .padding(.vertical, 6)
                    }
                    .listRowBackground(Color.clear)
                }
                .listStyle(.plain)
            }
            .background(Color(hex: 0x161824).ignoresSafeArea())
            .navigationTitle("Save to...")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done", action: onDismiss)
                        .foregroundColor(.white)
                }
            }
            .alert("New Playlist", isPresented: $showCreateAlert) {
                TextField("Playlist Title", text: $newPlaylistTitle)
                Button("Create & Add") {
                    let trimmed = newPlaylistTitle.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !trimmed.isEmpty {
                        let created = userData.createPlaylist(title: trimmed)
                        userData.addVideoToPlaylist(playlistId: created.id, video: video)
                        newPlaylistTitle = ""
                        feedbackText = "Added to \(trimmed) ✓"
                    }
                }
                Button("Cancel", role: .cancel) { newPlaylistTitle = "" }
            }
        }
    }
}

