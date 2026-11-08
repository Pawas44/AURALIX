import SwiftUI

struct YouTubeVideoCard: View {
    let video: VideoItem
    let onClick: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            // Video Thumbnail
            Button(action: onClick) {
                ZStack(alignment: .bottomTrailing) {
                    AsyncImage(url: URL(string: video.thumbnailUrl)) { image in
                        image.resizable().aspectRatio(16/9, contentMode: .fit)
                    } placeholder: {
                        Rectangle()
                            .fill(AppTheme.youTubeCardBg)
                            .aspectRatio(16/9, contentMode: .fit)
                    }

                    Text(video.duration)
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(AppTheme.youTubeTextPrimary)
                        .padding(.horizontal, 5)
                        .padding(.vertical, 2)
                        .background(Color.black.opacity(0.8))
                        .cornerRadius(4)
                        .padding(8)
                }
            }

            // Info bar
            HStack(alignment: .top, spacing: 12) {
                AsyncImage(url: URL(string: video.channelAvatarUrl)) { image in
                    image.resizable().aspectRatio(contentMode: .fill)
                } placeholder: {
                    Circle().fill(Color.gray.opacity(0.5))
                }
                .frame(width: 36, height: 36)
                .clipShape(Circle())

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

                Button(action: {}) {
                    Image(systemName: "ellipsis")
                        .rotationEffect(.degrees(90))
                        .font(.system(size: 14))
                        .foregroundColor(AppTheme.youTubeTextSecondary)
                        .padding(4)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
        }
        .background(AppTheme.youTubeDarkBg)
    }
}

