import SwiftUI

struct VideoDetailView: View {
    let video: VideoItem
    let isPlaying: Bool
    let onPlayPauseToggle: () -> Void
    let onCollapse: () -> Void
    let onSwitchToMusicMode: () -> Void
    let onVideoSelected: (VideoItem) -> Void
    let onChannelClick: (VideoItem) -> Void
    let isBackgroundPlay: Bool
    let onToggleBackgroundPlay: () -> Void

    @ObservedObject var playbackService = MediaPlaybackService.shared
    @ObservedObject var userData = UserDataRepository.shared

    @State private var isDescriptionExpanded = false
    @State private var dynamicRelated: [VideoItem] = []
    @State private var isLoadingRelated = false

    private var displayVideos: [VideoItem] {
        let queue = playbackService.playbackQueue
        if queue.count > 1 {
            if let idx = queue.firstIndex(where: { $0.id == video.id }) {
                let upcoming = Array(queue.dropFirst(idx + 1))
                let previous = Array(queue.prefix(idx))
                return upcoming + previous
            }
            return queue.filter { $0.id != video.id }
        } else if !dynamicRelated.isEmpty {
            return dynamicRelated
        } else {
            return VideoRepository.sampleVideos.filter { $0.id != video.id }
        }
    }

    var body: some View {
        let liveQueue = playbackService.playbackQueue
        let currentIdx = liveQueue.firstIndex(where: { $0.id == video.id })
        let queueText = (liveQueue.count > 1 && currentIdx != nil)
            ? "Queue (\(currentIdx! + 1)/\(liveQueue.count))"
            : "Up next"

        VStack(spacing: 0) {
            // Top Player Spacer (Pinned persistent YouTubeVideoPlayer sits above this in MainView)
            Color.clear
                .frame(maxWidth: .infinity)
                .aspectRatio(16/9, contentMode: .fit)

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    // Title & Stats + Collapse
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(video.title)
                                .font(.system(size: 17, weight: .bold))
                                .foregroundColor(.white)
                                .lineLimit(3)

                            Text("\(video.views) • \(video.uploadTime)")
                                .font(.system(size: 12))
                                .foregroundColor(AppTheme.youTubeTextSecondary)
                        }

                        Spacer()

                        Button(action: onCollapse) {
                            Image(systemName: "chevron.down")
                                .font(.system(size: 15, weight: .bold))
                                .foregroundColor(.white)
                                .frame(width: 34, height: 34)
                                .background(Color.white.opacity(0.12))
                                .clipShape(Circle())
                                .overlay(Circle().strokeBorder(LiquidGlassDefaults.borderSpecular, lineWidth: 1))
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.top, 10)

                    // Action Pills
                    PlayerControls(video: video)
                        .padding(.horizontal, 12)
                        .padding(.top, 8)

                    // Channel Bar + Verified + Liquid Glass Music Switcher Pill (Matches Android VideoDetailScreen)
                    HStack(spacing: 10) {
                        Button(action: { onChannelClick(video) }) {
                            HStack(spacing: 10) {
                                AsyncImage(url: URL(string: video.channelAvatarUrl)) { image in
                                    image.resizable().aspectRatio(contentMode: .fill)
                                } placeholder: {
                                    Circle().fill(Color(hex: 0x333333))
                                }
                                .frame(width: 38, height: 38)
                                .clipShape(Circle())

                                HStack(spacing: 4) {
                                    Text(video.channelName)
                                        .font(.system(size: 15, weight: .semibold))
                                        .foregroundColor(.white)
                                        .lineLimit(1)

                                    Image(systemName: "checkmark.circle.fill")
                                        .font(.system(size: 12))
                                        .foregroundColor(AppTheme.youTubeTextSecondary)
                                }
                            }
                        }

                        Spacer()

                        // Liquid Glass Music Mode Switcher Pill
                        Button(action: onSwitchToMusicMode) {
                            HStack(spacing: 6) {
                                Image(systemName: "music.note")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(AppTheme.ytMusicRedAccent)
                                Text("Music")
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(.white)
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 7)
                            .background(
                                LinearGradient(
                                    colors: [Color.white.opacity(0.16), Color.white.opacity(0.05)],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                            .clipShape(RoundedRectangle(cornerRadius: 20))
                            .overlay(
                                RoundedRectangle(cornerRadius: 20)
                                    .strokeBorder(LiquidGlassDefaults.borderSpecular, lineWidth: 1)
                            )
                            .shadow(color: Color.black.opacity(0.45), radius: 6, x: 0, y: 3)
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.top, 10)

                    // Expandable Description
                    if !video.description.isEmpty {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(video.description)
                                .font(.system(size: 12))
                                .foregroundColor(AppTheme.youTubeTextSecondary)
                                .lineLimit(isDescriptionExpanded ? nil : 2)

                            Button(action: { isDescriptionExpanded.toggle() }) {
                                Text(isDescriptionExpanded ? "Show less" : "Show more")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundColor(.white)
                            }
                        }
                        .padding(10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.white.opacity(0.06))
                        .cornerRadius(10)
                        .padding(.horizontal, 12)
                        .padding(.top, 10)
                    }

                    // Up Next / Queue Header
                    HStack {
                        Text(queueText)
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(.white)
                        Spacer()
                        if liveQueue.count > 1 {
                            Text("Auto-play queue")
                                .font(.system(size: 12))
                                .foregroundColor(AppTheme.youTubeTextSecondary)
                        } else if isLoadingRelated {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                .scaleEffect(0.7)
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.top, 14)
                    .padding(.bottom, 8)

                    // Compact Horizontal Up Next Queue Rows (Matches Android 120x68 rows)
                    LazyVStack(spacing: 12) {
                        ForEach(displayVideos) { item in
                            Button(action: { onVideoSelected(item) }) {
                                HStack(spacing: 12) {
                                    ZStack(alignment: .bottomTrailing) {
                                        AsyncImage(url: URL(string: item.thumbnailUrl)) { img in
                                            img.resizable().aspectRatio(16/9, contentMode: .fill)
                                        } placeholder: {
                                            Rectangle().fill(Color(hex: 0x222222))
                                        }
                                        .frame(width: 120, height: 68)
                                        .clipShape(RoundedRectangle(cornerRadius: 10))

                                        if !item.duration.isEmpty {
                                            Text(item.duration)
                                                .font(.system(size: 10, weight: .bold))
                                                .foregroundColor(.white)
                                                .padding(.horizontal, 5)
                                                .padding(.vertical, 2)
                                                .background(Color.black.opacity(0.85))
                                                .clipShape(RoundedRectangle(cornerRadius: 4))
                                                .padding(4)
                                        }
                                    }

                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(item.title)
                                            .font(.system(size: 13, weight: .semibold))
                                            .foregroundColor(.white)
                                            .lineLimit(2)
                                            .multilineTextAlignment(.leading)

                                        Text("\(item.channelName) • \(item.views)")
                                            .font(.system(size: 11))
                                            .foregroundColor(AppTheme.youTubeTextSecondary)
                                            .lineLimit(1)
                                    }
                                    .frame(maxWidth: .infinity, alignment: .leading)

                                    Image(systemName: "ellipsis")
                                        .rotationEffect(.degrees(90))
                                        .font(.system(size: 14))
                                        .foregroundColor(AppTheme.youTubeTextSecondary)
                                }
                                .padding(.horizontal, 12)
                            }
                        }
                    }
                    .padding(.bottom, 50)
                }
            }
        }
        .background(LiquidGlassDefaults.liquidCanvas.ignoresSafeArea())
        .task(id: video.id) {
            if playbackService.playbackQueue.count <= 1 {
                isLoadingRelated = true
                let related = await YouTubeSearchEngine.getRelatedVideos(video: video, isMusicOnly: false)
                if !related.isEmpty {
                    dynamicRelated = related
                    playbackService.setQueue([video] + related)
                }
                isLoadingRelated = false
            }
        }
    }
}
