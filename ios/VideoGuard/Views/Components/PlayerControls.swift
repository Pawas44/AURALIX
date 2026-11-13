import SwiftUI

struct PlayerControls: View {
    let video: VideoItem
    var onSwitchToMusic: (() -> Void)? = nil
    @ObservedObject var userData = UserDataRepository.shared
    @State private var showPlaylistPicker = false
    @State private var isDownloaded = false

    var body: some View {
        let isLiked = userData.isLiked(video.id)
        let isDisliked = userData.isDisliked(video.id)
        let isSaved = userData.isWatchLater(video.id)

        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                // Like / Dislike Combined Pill
                HStack(spacing: 0) {
                    Button(action: { userData.toggleLike(video) }) {
                        HStack(spacing: 5) {
                            Image(systemName: isLiked ? "hand.thumbsup.fill" : "hand.thumbsup")
                                .font(.system(size: 14))
                            Text(video.likes.isEmpty ? "Like" : video.likes)
                                .font(.system(size: 12, weight: .medium))
                        }
                        .foregroundColor(isLiked ? AppTheme.youTubeRed : .white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                    }

                    Rectangle()
                        .fill(Color.white.opacity(0.2))
                        .frame(width: 1, height: 18)

                    Button(action: { userData.toggleDislike(video.id) }) {
                        Image(systemName: isDisliked ? "hand.thumbsdown.fill" : "hand.thumbsdown")
                            .font(.system(size: 14))
                            .foregroundColor(isDisliked ? AppTheme.youTubeRed : .white)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                    }
                }
                .liquidGlassPill(isSelected: isLiked || isDisliked)

                // Share
                if let url = URL(string: "https://www.youtube.com/watch?v=\(video.id)") {
                    ShareLink(item: url, subject: Text(video.title)) {
                        HStack(spacing: 5) {
                            Image(systemName: "arrowshape.turn.up.right")
                                .font(.system(size: 14))
                            Text("Share")
                                .font(.system(size: 12, weight: .medium))
                        }
                        .foregroundColor(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .liquidGlassPill()
                    }
                }

                // Save to Watch Later
                Button(action: { userData.toggleWatchLater(video) }) {
                    HStack(spacing: 5) {
                        Image(systemName: isSaved ? "bookmark.fill" : "bookmark")
                            .font(.system(size: 14))
                        Text(isSaved ? "Saved" : "Save")
                            .font(.system(size: 12, weight: .medium))
                    }
                    .foregroundColor(isSaved ? AppTheme.youTubeRed : .white)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .liquidGlassPill(isSelected: isSaved)
                }

                // Download
                Button(action: { isDownloaded = true }) {
                    HStack(spacing: 5) {
                        Image(systemName: isDownloaded ? "checkmark.circle.fill" : "arrow.down.circle")
                            .font(.system(size: 14))
                        Text(isDownloaded ? "Downloaded" : "Download")
                            .font(.system(size: 12, weight: .medium))
                    }
                    .foregroundColor(isDownloaded ? AppTheme.youTubeRed : .white)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .liquidGlassPill(isSelected: isDownloaded)
                }

                // Add to Playlist
                Button(action: { showPlaylistPicker = true }) {
                    HStack(spacing: 5) {
                        Image(systemName: "text.badge.plus")
                            .font(.system(size: 14))
                        Text("Playlist")
                            .font(.system(size: 12, weight: .medium))
                    }
                    .foregroundColor(.white)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .liquidGlassPill()
                }

                // Music Mode Switch
                if let onSwitchToMusic = onSwitchToMusic {
                    Button(action: onSwitchToMusic) {
                        HStack(spacing: 5) {
                            Image(systemName: "music.note")
                                .font(.system(size: 14))
                            Text("Listen")
                                .font(.system(size: 12, weight: .medium))
                        }
                        .foregroundColor(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .liquidGlassPill()
                    }
                }
            }
            .padding(.vertical, 6)
        }
        .confirmationDialog("Add to Playlist", isPresented: $showPlaylistPicker, titleVisibility: .visible) {
            ForEach(userData.playlists.filter { !$0.isSystem }) { pl in
                Button(pl.title) {
                    userData.addVideoToPlaylist(playlistId: pl.id, video: video)
                }
            }
            Button("Watch later") {
                userData.toggleWatchLater(video)
            }
            Button("Cancel", role: .cancel) {}
        }
    }
}
