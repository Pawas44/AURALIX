import SwiftUI

struct YouTubeVideoCard: View {
    let video: VideoItem
    let onClick: () -> Void
    var onChannelClick: (() -> Void)? = nil

    var body: some View {
        VStack(spacing: 0) {
            Button(action: onClick) {
                ZStack(alignment: .bottomTrailing) {
                    AsyncImage(url: URL(string: video.thumbnailUrl)) { image in
                        image.resizable().aspectRatio(16/9, contentMode: .fill)
                    } placeholder: {
                        Rectangle()
                            .fill(AppTheme.youTubeCardBg)
                            .aspectRatio(16/9, contentMode: .fit)
                    }
                    .frame(maxWidth: .infinity)
                    .aspectRatio(16/9, contentMode: .fit)
                    .clipped()

                    if !video.duration.isEmpty {
                        Text(video.duration)
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.black.opacity(0.82))
                            .cornerRadius(4)
                            .padding(8)
                    }
                }
            }

            HStack(alignment: .top, spacing: 12) {
                Button(action: {
                    if let onChannelClick = onChannelClick {
                        onChannelClick()
                    } else {
                        onClick()
                    }
                }) {
                    AsyncImage(url: URL(string: video.channelAvatarUrl)) { image in
                        image.resizable().aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Circle().fill(Color.gray.opacity(0.4))
                    }
                    .frame(width: 36, height: 36)
                    .clipShape(Circle())
                }

                Button(action: onClick) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(video.title)
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(AppTheme.youTubeTextPrimary)
                            .lineLimit(2)
                            .multilineTextAlignment(.leading)

                        Text("\(video.channelName) • \(video.views) • \(video.uploadTime)")
                            .font(.system(size: 12))
                            .foregroundColor(AppTheme.youTubeTextSecondary)
                            .lineLimit(1)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                Menu {
                    Button(action: { UserDataRepository.shared.toggleWatchLater(video) }) {
                        Label(
                            UserDataRepository.shared.isWatchLater(video.id) ? "Remove from Watch later" : "Save to Watch later",
                            systemImage: "clock"
                        )
                    }
                    Button(action: { UserDataRepository.shared.toggleLike(video) }) {
                        Label(
                            UserDataRepository.shared.isLiked(video.id) ? "Unlike" : "Like video",
                            systemImage: "hand.thumbsup"
                        )
                    }
                } label: {
                    Image(systemName: "ellipsis")
                        .rotationEffect(.degrees(90))
                        .font(.system(size: 14))
                        .foregroundColor(AppTheme.youTubeTextSecondary)
                        .padding(6)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
        }
    }
}
