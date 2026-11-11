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
        VStack(spacing: 0) {
            // Top Player Spacer (Pinned persistent YouTubeVideoPlayer sits above this in MainView)
            Color.clear
                .frame(maxWidth: .infinity)
                .aspectRatio(16/9, contentMode: .fit)

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    // Title & Stats
                    VStack(alignment: .leading, spacing: 6) {
                        Text(video.title)
                            .font(.system(size: 17, weight: .bold))
                            .foregroundColor(.white)
                            .lineLimit(3)

                        Text("\(video.views) • \(video.uploadTime)")
                            .font(.system(size: 12))
                            .foregroundColor(AppTheme.youTubeTextSecondary)
                    }
                    .padding(.horizontal, 14)
                    .padding(.top, 12)

                    // Action Pills
                    PlayerControls(video: video, onSwitchToMusic: onSwitchToMusicMode)
                        .padding(.horizontal, 14)
                        .padding(.top, 6)

                    Divider().background(Color.white.opacity(0.1)).padding(.vertical, 8)

                    // Channel Info Bar
                    HStack(spacing: 12) {
                        Button(action: { onChannelClick(video) }) {
                            HStack(spacing: 10) {
                                AsyncImage(url: URL(string: video.channelAvatarUrl)) { image in
                                    image.resizable().aspectRatio(contentMode: .fill)
                                } placeholder: {
                                    Circle().fill(Color.gray.opacity(0.4))
                                }
                                .frame(width: 40, height: 40)
                                .clipShape(Circle())

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(video.channelName)
                                        .font(.system(size: 15, weight: .semibold))
                                        .foregroundColor(.white)
                                        .lineLimit(1)
                                    Text(video.subscriberCount.isEmpty ? "Channel" : video.subscriberCount)
                                        .font(.system(size: 12))
                                        .foregroundColor(AppTheme.youTubeTextSecondary)
                                }
                            }
                        }

                        Spacer()

                        let subscribed = userData.isSubscribed(video.channelName)
                        Button(action: { userData.toggleSubscription(video.channelName) }) {
                            Text(subscribed ? "Subscribed" : "Subscribe")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(subscribed ? .white : .black)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                                .background(subscribed ? Color.white.opacity(0.15) : Color.white)
                                .cornerRadius(20)
                        }
                    }
                    .padding(.horizontal, 14)

                    // Background Audio Status Banner
                    HStack {
                        Image(systemName: "headphones")
                            .font(.system(size: 14))
                            .foregroundColor(isBackgroundPlay ? Color(hex: 0x4CAF50) : AppTheme.youTubeTextSecondary)
                        Text(isBackgroundPlay ? "Background audio play is ON" : "Background audio play is OFF")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.white)
                        Spacer()
                        Button(isBackgroundPlay ? "Disable" : "Enable") {
                            onToggleBackgroundPlay()
                        }
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(Color(hex: 0x3EA6FF))
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .liquidGlass(cornerRadius: 10)
                    .padding(.horizontal, 14)
                    .padding(.top, 10)

                    // Description Box
                    if !video.description.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(video.description)
                                .font(.system(size: 13))
                                .foregroundColor(AppTheme.youTubeTextSecondary)
                                .lineLimit(isDescriptionExpanded ? nil : 2)

                            Button(action: { isDescriptionExpanded.toggle() }) {
                                Text(isDescriptionExpanded ? "Show less" : "Show more")
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundColor(.white)
                            }
                        }
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.white.opacity(0.06))
                        .cornerRadius(10)
                        .padding(.horizontal, 14)
                        .padding(.top, 10)
                    }

                    // Related Videos
                    HStack {
                        Text("Up next")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.white)
                        Spacer()
                        if isLoadingRelated {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                .scaleEffect(0.7)
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.top, 16)
                    .padding(.bottom, 8)

                    LazyVStack(spacing: 14) {
                        ForEach(displayVideos) { item in
                            YouTubeVideoCard(
                                video: item,
                                onClick: { onVideoSelected(item) },
                                onChannelClick: { onChannelClick(item) }
                            )
                        }
                    }
                    .padding(.bottom, 60)
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
