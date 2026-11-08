import SwiftUI

struct MusicPlayerView: View {
    let track: VideoItem
    let onBack: () -> Void
    let onVideoClick: (VideoItem) -> Void

    @State private var showLyrics = false
    @ObservedObject var playbackService = MediaPlaybackService.shared

    var body: some View {
        let state = playbackService.playbackState
        let upNext = VideoRepository.getMusicTracks().filter { $0.id != track.id }
        let progress = state.duration > 0 ? Double(state.currentPosition) / Double(state.duration) : 0.0

        ScrollView {
            VStack(spacing: 20) {
                // Header Bar
                HStack {
                    Button(action: onBack) {
                        Image(systemName: "chevron.down")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(AppTheme.ytMusicTextPrimary)
                    }
                    Spacer()
                    Text("Now Playing")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(AppTheme.ytMusicTextSecondary)
                    Spacer()
                    Button(action: {}) {
                        Image(systemName: "ellipsis")
                            .font(.system(size: 20))
                            .foregroundColor(AppTheme.ytMusicTextPrimary)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 10)

                // Large Album Art
                AsyncImage(url: URL(string: track.thumbnailUrl)) { image in
                    image.resizable().aspectRatio(contentMode: .fill)
                } placeholder: {
                    Rectangle().fill(AppTheme.ytMusicCardBg)
                }
                .frame(width: 300, height: 300)
                .cornerRadius(16)
                .clipped()
                .shadow(color: .black.opacity(0.6), radius: 12, x: 0, y: 6)
                .padding(.top, 10)

                // Track Info
                VStack(alignment: .leading, spacing: 4) {
                    Text(track.title)
                        .font(.system(size: 22, weight: .bold))
                        .foregroundColor(AppTheme.ytMusicTextPrimary)
                        .lineLimit(2)
                    Text(!track.artist.isEmpty ? "\(track.artist) • \(track.album)" : track.channelName)
                        .font(.system(size: 15))
                        .foregroundColor(AppTheme.ytMusicTextSecondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 24)

                // Progress Slider
                VStack(spacing: 6) {
                    Slider(value: Binding(
                        get: { progress },
                        set: { newVal in
                            playbackService.seekTo(position: Int64(newVal * Double(state.duration)))
                        }
                    ))
                    .accentColor(AppTheme.ytMusicRedAccent)

                    HStack {
                        Text(formatTime(state.currentPosition))
                            .font(.system(size: 12))
                            .foregroundColor(AppTheme.ytMusicTextSecondary)
                        Spacer()
                        Text(formatTime(state.duration))
                            .font(.system(size: 12))
                            .foregroundColor(AppTheme.ytMusicTextSecondary)
                    }
                }
                .padding(.horizontal, 24)

                // Controls
                HStack(spacing: 32) {
                    Button(action: { playbackService.toggleShuffle() }) {
                        Image(systemName: "shuffle")
                            .font(.system(size: 22))
                            .foregroundColor(state.isShuffled ? AppTheme.ytMusicRedAccent : AppTheme.ytMusicTextSecondary)
                    }

                    Button(action: { playbackService.previous() }) {
                        Image(systemName: "backward.fill")
                            .font(.system(size: 28))
                            .foregroundColor(AppTheme.ytMusicTextPrimary)
                    }

                    Button(action: { playbackService.togglePlayPause() }) {
                        ZStack {
                            Circle()
                                .fill(AppTheme.ytMusicTextPrimary)
                                .frame(width: 68, height: 68)
                            Image(systemName: state.isPlaying ? "pause.fill" : "play.fill")
                                .font(.system(size: 28))
                                .foregroundColor(AppTheme.ytMusicBlack)
                        }
                    }

                    Button(action: { playbackService.next() }) {
                        Image(systemName: "forward.fill")
                            .font(.system(size: 28))
                            .foregroundColor(AppTheme.ytMusicTextPrimary)
                    }

                    Button(action: { playbackService.toggleRepeat() }) {
                        Image(systemName: state.repeatMode == .one ? "repeat.1" : "repeat")
                            .font(.system(size: 22))
                            .foregroundColor(state.repeatMode != .off ? AppTheme.ytMusicRedAccent : AppTheme.ytMusicTextSecondary)
                    }
                }
                .padding(.vertical, 8)

                // Lyrics Box Toggle
                if !track.lyrics.isEmpty {
                    VStack(alignment: .leading, spacing: 12) {
                        Button(action: { withAnimation { showLyrics.toggle() } }) {
                            HStack {
                                Image(systemName: "quote.bubble.fill")
                                    .foregroundColor(AppTheme.ytMusicTextPrimary)
                                Text("Lyrics")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(AppTheme.ytMusicTextPrimary)
                                Spacer()
                                Image(systemName: showLyrics ? "chevron.up" : "chevron.down")
                                    .foregroundColor(AppTheme.ytMusicTextSecondary)
                            }
                            .padding(16)
                            .background(AppTheme.ytMusicCardBg)
                            .cornerRadius(12)
                        }

                        if showLyrics {
                            Text(track.lyrics)
                                .font(.system(size: 15, weight: .medium))
                                .foregroundColor(AppTheme.ytMusicTextPrimary.opacity(0.9))
                                .lineSpacing(8)
                                .padding(20)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(AppTheme.ytMusicCardBg)
                                .cornerRadius(12)
                        }
                    }
                    .padding(.horizontal, 24)
                }

                // Up Next Section
                VStack(alignment: .leading, spacing: 12) {
                    Text("Up Next")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(AppTheme.ytMusicTextPrimary)
                        .padding(.horizontal, 24)

                    VStack(spacing: 8) {
                        ForEach(upNext) { item in
                            MusicTrackRow(track: item) {
                                onVideoClick(item)
                            }
                        }
                    }
                }
                .padding(.top, 10)
                .padding(.bottom, 60)
            }
        }
        .background(AppTheme.ytMusicBlack.ignoresSafeArea())
        .onAppear {
            playbackService.play(track: track, queue: VideoRepository.getMusicTracks())
        }
    }

    private func formatTime(_ ms: Int64) -> String {
        let totalSeconds = ms / 1000
        let minutes = totalSeconds / 60
        let seconds = totalSeconds % 60
        return String(format: "%d:%02d", minutes, seconds)
    }
}

