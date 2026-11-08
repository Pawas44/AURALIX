import SwiftUI

struct VideoDetailView: View {
    let video: VideoItem
    let onBack: () -> Void
    let onChannelClick: (String) -> Void
    let onVideoClick: (VideoItem) -> Void

    @State private var isDescriptionExpanded = false
    @ObservedObject var playbackService = MediaPlaybackService.shared
    @ObservedObject var userData = UserDataRepository.shared

    var body: some View {
        let relatedVideos = VideoRepository.sampleVideos.filter { $0.id != video.id }

        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                // Video Player Area
                ZStack(alignment: .topLeading) {
                    AsyncImage(url: URL(string: video.thumbnailUrl)) { image in
                        image.resizable().aspectRatio(16/9, contentMode: .fit)
                    } placeholder: {
                        Rectangle().fill(AppTheme.youTubeCardBg).aspectRatio(16/9, contentMode: .fit)
                    }
                    .frame(maxWidth: .infinity)

                    // Play icon overlay
                    Button(action: {
                        playbackService.togglePlayPause()
                    }) {
                        Image(systemName: playbackService.playbackState.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                            .font(.system(size: 60))
                            .foregroundColor(.white.opacity(0.85))
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .aspectRatio(16/9, contentMode: .fit)

                    // Back button overlay
                    Button(action: onBack) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(.white)
                            .padding(12)
                            .background(Color.black.opacity(0.4))
                            .clipShape(Circle())
                    }
                    .padding(8)
                }

                // Title & stats
                VStack(alignment: .leading, spacing: 6) {
                    Text(video.title)
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(AppTheme.youTubeTextPrimary)
                        .lineLimit(2)

                    Text("\(video.views) • \(video.uploadTime)")
                        .font(.system(size: 13))
                        .foregroundColor(AppTheme.youTubeTextSecondary)
                }
                .padding(.horizontal, 14)
                .padding(.top, 12)

                // Actions (Like, Dislike, Share, Save)
                PlayerControls(video: video)
                    .padding(.horizontal, 10)
                    .padding(.top, 6)

                Divider().background(AppTheme.youTubeBorder).padding(.vertical, 8)

                // Channel Info Bar
                HStack(spacing: 12) {
                    Button(action: { onChannelClick(video.channelName) }) {
                        HStack(spacing: 12) {
                            AsyncImage(url: URL(string: video.channelAvatarUrl)) { image in
                                image.resizable().aspectRatio(contentMode: .fill)
                            } placeholder: {
                                Circle().fill(Color.gray)
                            }
                            .frame(width: 40, height: 40)
                            .clipShape(Circle())

                            VStack(alignment: .leading, spacing: 2) {
                                Text(video.channelName)
                                    .font(.system(size: 15, weight: .semibold))
                                    .foregroundColor(AppTheme.youTubeTextPrimary)
                                Text(video.subscriberCount)
                                    .font(.system(size: 12))
                                    .foregroundColor(AppTheme.youTubeTextSecondary)
                            }
                        }
                    }

                    Spacer()

                    Button(action: {}) {
                        Text("Subscribe")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(AppTheme.youTubeDarkBg)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                            .background(AppTheme.youTubeTextPrimary)
                            .cornerRadius(20)
                    }
                }
                .padding(.horizontal, 14)

                Divider().background(AppTheme.youTubeBorder).padding(.vertical, 8)

                // Description Box
                if !video.description.isEmpty {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(video.description)
                            .font(.system(size: 13))
                            .foregroundColor(AppTheme.youTubeTextSecondary)
                            .lineLimit(isDescriptionExpanded ? nil : 3)

                        Button(action: { isDescriptionExpanded.toggle() }) {
                            Text(isDescriptionExpanded ? "Show less" : "Show more")
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(AppTheme.youTubeTextPrimary)
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 4)

                    Divider().background(AppTheme.youTubeBorder).padding(.vertical, 8)
                }

                // Related Videos Section
                Text("Related Videos")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(AppTheme.youTubeTextPrimary)
                    .padding(.horizontal, 14)
                    .padding(.top, 4)

                LazyVStack(spacing: 16) {
                    ForEach(relatedVideos) { item in
                        YouTubeVideoCard(video: item) {
                            onVideoClick(item)
                        }
                    }
                }
                .padding(.top, 8)
                .padding(.bottom, 60)
            }
        }
        .background(AppTheme.youTubeDarkBg.ignoresSafeArea())
        .onAppear {
            userData.addToHistory(video)
            playbackService.play(track: video)
        }
    }
}

