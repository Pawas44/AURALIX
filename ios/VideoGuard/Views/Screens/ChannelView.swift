import SwiftUI

struct ChannelView: View {
    let channelName: String
    let onBack: () -> Void
    let onVideoClick: (VideoItem) -> Void

    var body: some View {
        let channelVideos = VideoRepository.sampleVideos.filter {
            $0.channelName.caseInsensitiveCompare(channelName) == .orderedSame
        }
        let channelInfo = channelVideos.first

        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                // Banner & back button
                ZStack(alignment: .topLeading) {
                    Rectangle()
                        .fill(AppTheme.youTubeRed.opacity(0.3))
                        .frame(height: 110)

                    Button(action: onBack) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(AppTheme.youTubeTextPrimary)
                            .padding(12)
                    }
                }

                // Channel Info Header
                HStack(spacing: 16) {
                    AsyncImage(url: URL(string: channelInfo?.channelAvatarUrl ?? "")) { image in
                        image.resizable().aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Circle().fill(Color.gray)
                    }
                    .frame(width: 72, height: 72)
                    .clipShape(Circle())

                    VStack(alignment: .leading, spacing: 4) {
                        Text(channelName)
                            .font(.system(size: 22, weight: .bold))
                            .foregroundColor(AppTheme.youTubeTextPrimary)
                        Text(channelInfo?.subscriberCount ?? "")
                            .font(.system(size: 14))
                            .foregroundColor(AppTheme.youTubeTextSecondary)
                        Text("\(channelVideos.count) videos")
                            .font(.system(size: 14))
                            .foregroundColor(AppTheme.youTubeTextSecondary)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 16)

                // Subscribe Button
                Button(action: {}) {
                    Text("Subscribe")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(AppTheme.youTubeRed)
                        .cornerRadius(20)
                }
                .padding(.horizontal, 16)
                .padding(.top, 16)

                Divider().background(AppTheme.youTubeBorder).padding(.vertical, 16)

                // Videos
                LazyVStack(spacing: 16) {
                    ForEach(channelVideos) { video in
                        YouTubeVideoCard(video: video) {
                            onVideoClick(video)
                        }
                    }
                }
                .padding(.bottom, 60)
            }
        }
        .background(AppTheme.youTubeDarkBg.ignoresSafeArea())
    }
}

