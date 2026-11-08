import SwiftUI

struct MiniPlayer: View {
    let appMode: AppMode
    let onClick: () -> Void

    @ObservedObject var playbackService = MediaPlaybackService.shared

    var body: some View {
        if let track = playbackService.playbackState.currentTrack {
            let bgColor = appMode == .youtube ? AppTheme.youTubeCardBg : AppTheme.ytMusicCardBg
            let accentColor = appMode == .youtube ? AppTheme.youTubeRed : AppTheme.ytMusicRedAccent
            let progress: Double = playbackService.playbackState.duration > 0
                ? Double(playbackService.playbackState.currentPosition) / Double(playbackService.playbackState.duration)
                : 0.0

            VStack(spacing: 0) {
                // Progress bar
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Rectangle()
                            .fill(accentColor.opacity(0.2))
                            .frame(width: geo.size.width, height: 2)
                        Rectangle()
                            .fill(accentColor)
                            .frame(width: max(0, min(geo.size.width, geo.size.width * CGFloat(progress))), height: 2)
                    }
                }
                .frame(height: 2)

                HStack(spacing: 10) {
                    AsyncImage(url: URL(string: track.thumbnailUrl)) { image in
                        image.resizable().aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Rectangle().fill(Color.gray.opacity(0.3))
                    }
                    .frame(width: 44, height: 44)
                    .cornerRadius(4)
                    .clipped()

                    VStack(alignment: .leading, spacing: 2) {
                        Text(track.title)
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(appMode == .youtube ? AppTheme.youTubeTextPrimary : AppTheme.ytMusicTextPrimary)
                            .lineLimit(1)

                        Text(track.isMusicTrack && !track.artist.isEmpty ? track.artist : track.channelName)
                            .font(.system(size: 12))
                            .foregroundColor(appMode == .youtube ? AppTheme.youTubeTextSecondary : AppTheme.ytMusicTextSecondary)
                            .lineLimit(1)
                    }

                    Spacer()

                    Button(action: {
                        playbackService.togglePlayPause()
                    }) {
                        Image(systemName: playbackService.playbackState.isPlaying ? "pause.fill" : "play.fill")
                            .font(.system(size: 20))
                            .foregroundColor(appMode == .youtube ? AppTheme.youTubeTextPrimary : AppTheme.ytMusicTextPrimary)
                            .frame(width: 36, height: 36)
                    }

                    Button(action: {
                        playbackService.stop()
                    }) {
                        Image(systemName: "xmark")
                            .font(.system(size: 16))
                            .foregroundColor(AppTheme.youTubeTextSecondary)
                            .frame(width: 36, height: 36)
                    }
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .contentShape(Rectangle())
                .onTapGesture {
                    onClick()
                }
            }
            .background(bgColor)
        }
    }
}

