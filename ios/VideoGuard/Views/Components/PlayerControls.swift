import SwiftUI

struct PlayerControls: View {
    let video: VideoItem
    @ObservedObject var userData = UserDataRepository.shared

    var body: some View {
        let isLiked = userData.isLiked(video.id)
        let isDisliked = userData.isDisliked(video.id)
        let isSaved = userData.isSaved(video.id)

        HStack(spacing: 24) {
            // Like
            Button(action: { userData.toggleLike(video.id) }) {
                VStack(spacing: 4) {
                    Image(systemName: isLiked ? "hand.thumbsup.fill" : "hand.thumbsup")
                        .font(.system(size: 18))
                    Text(video.likes)
                        .font(.system(size: 11, weight: .medium))
                }
                .foregroundColor(isLiked ? AppTheme.youTubeTextPrimary : AppTheme.youTubeTextSecondary)
            }

            // Dislike
            Button(action: { userData.toggleDislike(video.id) }) {
                VStack(spacing: 4) {
                    Image(systemName: isDisliked ? "hand.thumbsdown.fill" : "hand.thumbsdown")
                        .font(.system(size: 18))
                    Text("Dislike")
                        .font(.system(size: 11, weight: .medium))
                }
                .foregroundColor(isDisliked ? AppTheme.youTubeTextPrimary : AppTheme.youTubeTextSecondary)
            }

            // Share
            Button(action: {}) {
                VStack(spacing: 4) {
                    Image(systemName: "arrowshape.turn.up.right")
                        .font(.system(size: 18))
                    Text("Share")
                        .font(.system(size: 11, weight: .medium))
                }
                .foregroundColor(AppTheme.youTubeTextSecondary)
            }

            // Remix
            Button(action: {}) {
                VStack(spacing: 4) {
                    Image(systemName: "plus.square.on.square")
                        .font(.system(size: 18))
                    Text("Remix")
                        .font(.system(size: 11, weight: .medium))
                }
                .foregroundColor(AppTheme.youTubeTextSecondary)
            }

            // Save
            Button(action: { userData.toggleSave(video.id) }) {
                VStack(spacing: 4) {
                    Image(systemName: isSaved ? "bookmark.fill" : "bookmark")
                        .font(.system(size: 18))
                    Text(isSaved ? "Saved" : "Save")
                        .font(.system(size: 11, weight: .medium))
                }
                .foregroundColor(isSaved ? AppTheme.youTubeTextPrimary : AppTheme.youTubeTextSecondary)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
    }
}

