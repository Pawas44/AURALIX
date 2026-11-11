import SwiftUI

struct MiniPlayer: View {
    let appMode: AppMode
    let onClick: () -> Void

    @ObservedObject var playbackService = MediaPlaybackService.shared

    var body: some View {
        if let track = playbackService.currentTrack {
            let isMusic = appMode == .youtubeMusic
            let accentColor = isMusic ? AppTheme.ytMusicRedAccent : AppTheme.youTubeRed
            let progress: Double = playbackService.durationSeconds > 0
                ? min(1.0, max(0.0, playbackService.currentTimeSeconds / playbackService.durationSeconds))
                : 0.0

            VStack(spacing: 0) {
                HStack(spacing: 10) {
                    AsyncImage(url: URL(string: track.thumbnailUrl)) { image in
                        image.resizable().aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Rectangle().fill(Color.gray.opacity(0.3))
                    }
                    .frame(width: isMusic ? 44 : 52, height: isMusic ? 44 : 34)
                    .cornerRadius(10)
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .strokeBorder(Color.white.opacity(0.25), lineWidth: 1)
                    )
                    .clipped()

                    VStack(alignment: .leading, spacing: 2) {
                        Text(track.title)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.white)
                            .lineLimit(1)

                        Text(isMusic && !track.artist.isEmpty ? track.artist : track.channelName)
                            .font(.system(size: 11))
                            .foregroundColor(Color(hex: 0xB0B0C2))
                            .lineLimit(1)
                    }

                    Spacer()

                    // Play / Pause Glass Circle
                    Button(action: { playbackService.togglePlayPause() }) {
                        Image(systemName: playbackService.isPlaying ? "pause.fill" : "play.fill")
                            .font(.system(size: 15))
                            .foregroundColor(.white)
                            .frame(width: 34, height: 34)
                            .background(Color.white.opacity(0.10))
                            .clipShape(Circle())
                            .overlay(Circle().strokeBorder(Color.white.opacity(0.25), lineWidth: 1))
                    }

                    // Skip Next Glass Circle
                    Button(action: { playbackService.nextTrack() }) {
                        Image(systemName: "forward.end.fill")
                            .font(.system(size: 14))
                            .foregroundColor(.white)
                            .frame(width: 34, height: 34)
                            .background(Color.white.opacity(0.08))
                            .clipShape(Circle())
                            .overlay(Circle().strokeBorder(Color.white.opacity(0.18), lineWidth: 1))
                    }

                    // Close Button
                    Button(action: { playbackService.stop() }) {
                        Image(systemName: "xmark")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(AppTheme.youTubeTextSecondary)
                            .frame(width: 30, height: 30)
                    }
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 7)
                .contentShape(Rectangle())
                .onTapGesture {
                    onClick()
                }

                // Glowing progress bar at bottom of glass capsule
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Rectangle()
                            .fill(Color.white.opacity(0.12))
                            .frame(width: geo.size.width, height: 2.5)
                        Rectangle()
                            .fill(accentColor)
                            .frame(width: geo.size.width * CGFloat(progress), height: 2.5)
                    }
                }
                .frame(height: 2.5)
            }
            .background(
                LinearGradient(
                    colors: [Color(hex: 0x202235, alpha: 0.92), Color(hex: 0x121422, alpha: 0.88)],
                    startPoint: .top,
                    endPoint: .bottom
                )
            )
            .clipShape(RoundedRectangle(cornerRadius: 20))
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .strokeBorder(LiquidGlassDefaults.borderSpecular, lineWidth: 1)
            )
            .shadow(color: Color.black.opacity(0.55), radius: 14, x: 0, y: 6)
            .padding(.horizontal, 10)
            .padding(.bottom, 4)
        }
    }
}
