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
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .strokeBorder(Color.white.opacity(0.12), lineWidth: 1)
                    )

                    if !video.duration.isEmpty {
                        Text(video.duration)
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 7)
                            .padding(.vertical, 3)
                            .background(Color.black.opacity(0.75))
                            .clipShape(RoundedRectangle(cornerRadius: 6))
                            .overlay(
                                RoundedRectangle(cornerRadius: 6)
                                    .strokeBorder(Color.white.opacity(0.28), lineWidth: 0.8)
                            )
                            .padding(8)
                    }
                }
                .padding(.horizontal, 8)
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
                    .frame(width: 38, height: 38)
                    .clipShape(Circle())
                    .overlay(Circle().strokeBorder(Color.white.opacity(0.20), lineWidth: 1))
                }

                Button(action: onClick) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(video.title)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(Color(hex: 0xF1F1F1))
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
                        .foregroundColor(.white)
                        .padding(6)
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
        }
    }
}
