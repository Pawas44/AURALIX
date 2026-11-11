import SwiftUI

struct MiniPlayer: View {
    let appMode: AppMode
    let onClick: () -> Void

    @ObservedObject var playbackService = MediaPlaybackService.shared

    var body: some View {
        if let track = playbackService.currentTrack {
            let accentColor = appMode == .youtube ? AppTheme.youTubeRed : AppTheme.ytMusicRedAccent
            let progress: Double = playbackService.durationSeconds > 0
                ? min(1.0, max(0.0, playbackService.currentTimeSeconds / playbackService.durationSeconds))
                : 0.0

            VStack(spacing: 0) {
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Rectangle()
                            .fill(accentColor.opacity(0.25))
                            .frame(width: geo.size.width, height: 2)
                        Rectangle()
                            .fill(accentColor)
                            .frame(width: geo.size.width * CGFloat(progress), height: 2)
                    }
                }
                .frame(height: 2)

                HStack(spacing: 10) {
                    AsyncImage(url: URL(string: track.thumbnailUrl)) { image in
                        image.resizable().aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Rectangle().fill(Color.gray.opacity(0.3))
                    }
                    .frame(width: 48, height: 40)
                    .cornerRadius(6)
                    .clipped()

                    VStack(alignment: .leading, spacing: 2) {
                        Text(track.title)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.white)
                            .lineLimit(1)

                        Text(track.isMusicTrack && !track.artist.isEmpty ? track.artist : track.channelName)
                            .font(.system(size: 11))
                            .foregroundColor(AppTheme.youTubeTextSecondary)
                            .lineLimit(1)
                    }

                    Spacer()

                    Button(action: { playbackService.togglePlayPause() }) {
                        Image(systemName: playbackService.isPlaying ? "pause.fill" : "play.fill")
                            .font(.system(size: 20))
                            .foregroundColor(.white)
                            .frame(width: 36, height: 36)
                    }

                    Button(action: { playbackService.stop() }) {
                        Image(systemName: "xmark")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(AppTheme.youTubeTextSecondary)
                            .frame(width: 34, height: 34)
                    }
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .contentShape(Rectangle())
                .onTapGesture {
                    onClick()
                }
            }
            .liquidGlass(cornerRadius: 12, backgroundColor: Color(hex: 0x151624, alpha: 0.92), borderWidth: 0.8)
            .padding(.horizontal, 6)
            .padding(.bottom, 2)
        }
    }
}
