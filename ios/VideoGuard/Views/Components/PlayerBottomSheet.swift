import SwiftUI

struct PlayerBottomSheet: View {
    let appMode: AppMode
    let onDismiss: () -> Void

    @ObservedObject var playbackService = MediaPlaybackService.shared

    var body: some View {
        let state = playbackService.playbackState
        let track = state.currentTrack
        let bgColor = appMode == .youtube ? AppTheme.youTubeDarkBg : AppTheme.ytMusicBlack
        let accentColor = appMode == .youtube ? AppTheme.youTubeRed : AppTheme.ytMusicRedAccent

        ZStack {
            bgColor.ignoresSafeArea()

            if let current = track {
                VStack(spacing: 20) {
                    // Header Drag Indicator & Down arrow
                    HStack {
                        Button(action: onDismiss) {
                            Image(systemName: "chevron.down")
                                .font(.system(size: 18, weight: .semibold))
                                .foregroundColor(AppTheme.youTubeTextPrimary)
                        }
                        Spacer()
                        Text("Now Playing")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(AppTheme.youTubeTextSecondary)
                        Spacer()
                        Button(action: {}) {
                            Image(systemName: "ellipsis")
                                .font(.system(size: 18))
                                .foregroundColor(AppTheme.youTubeTextPrimary)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 16)

                    Spacer()

                    // Big Artwork
                    AsyncImage(url: URL(string: current.thumbnailUrl)) { image in
                        image.resizable().aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Rectangle().fill(AppTheme.youTubeCardBg)
                    }
                    .frame(width: 280, height: 280)
                    .cornerRadius(16)
                    .shadow(color: Color.black.opacity(0.5), radius: 10, x: 0, y: 5)

                    Spacer()

                    // Title & Artist
                    VStack(spacing: 6) {
                        Text(current.title)
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(AppTheme.youTubeTextPrimary)
                            .multilineTextAlignment(.center)
                            .lineLimit(2)
                            .padding(.horizontal, 24)

                        Text(current.isMusicTrack && !current.artist.isEmpty ? current.artist : current.channelName)
                            .font(.system(size: 15))
                            .foregroundColor(AppTheme.youTubeTextSecondary)
                    }

                    // Progress Slider
                    VStack(spacing: 6) {
                        let progress = state.duration > 0 ? Double(state.currentPosition) / Double(state.duration) : 0.0

                        Slider(value: Binding(
                            get: { progress },
                            set: { newVal in
                                playbackService.seekTo(position: Int64(newVal * Double(state.duration)))
                            }
                        ))
                        .accentColor(accentColor)

                        HStack {
                            Text(formatTime(state.currentPosition))
                                .font(.system(size: 12))
                                .foregroundColor(AppTheme.youTubeTextSecondary)
                            Spacer()
                            Text(formatTime(state.duration))
                                .font(.system(size: 12))
                                .foregroundColor(AppTheme.youTubeTextSecondary)
                        }
                    }
                    .padding(.horizontal, 24)

                    // Control Buttons
                    HStack(spacing: 28) {
                        Button(action: { playbackService.toggleShuffle() }) {
                            Image(systemName: "shuffle")
                                .font(.system(size: 20))
                                .foregroundColor(state.isShuffled ? accentColor : AppTheme.youTubeTextSecondary)
                        }

                        Button(action: { playbackService.previous() }) {
                            Image(systemName: "backward.fill")
                                .font(.system(size: 26))
                                .foregroundColor(AppTheme.youTubeTextPrimary)
                        }

                        Button(action: { playbackService.togglePlayPause() }) {
                            ZStack {
                                Circle()
                                    .fill(accentColor)
                                    .frame(width: 64, height: 64)
                                Image(systemName: state.isPlaying ? "pause.fill" : "play.fill")
                                    .font(.system(size: 28))
                                    .foregroundColor(.white)
                            }
                        }

                        Button(action: { playbackService.next() }) {
                            Image(systemName: "forward.fill")
                                .font(.system(size: 26))
                                .foregroundColor(AppTheme.youTubeTextPrimary)
                        }

                        Button(action: { playbackService.toggleRepeat() }) {
                            Image(systemName: state.repeatMode == .one ? "repeat.1" : "repeat")
                                .font(.system(size: 20))
                                .foregroundColor(state.repeatMode != .off ? accentColor : AppTheme.youTubeTextSecondary)
                        }
                    }
                    .padding(.bottom, 40)
                }
            }
        }
    }

    private func formatTime(_ ms: Int64) -> String {
        let totalSeconds = ms / 1000
        let minutes = totalSeconds / 60
        let seconds = totalSeconds % 60
        return String(format: "%d:%02d", minutes, seconds)
    }
}

