import SwiftUI

struct YouTubeFeedView: View {
    let appMode: AppMode
    let onSearchClick: () -> Void
    let onProfileClick: () -> Void
    let onVideoClick: (VideoItem) -> Void

    @State private var selectedCategory: String = "All"

    var body: some View {
        let categories = VideoRepository.youtubeCategories
        let videos = VideoRepository.getVideosByCategory(selectedCategory)

        VStack(spacing: 0) {
            YouTubeTopBar(
                appMode: appMode,
                onSearchClick: onSearchClick,
                onProfileClick: onProfileClick
            )

            // Category Chips Carousel
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(categories, id: \.self) { category in
                        let isSelected = category == selectedCategory
                        Button(action: { selectedCategory = category }) {
                            Text(category)
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(isSelected ? AppTheme.youTubeChipSelectedText : AppTheme.youTubeTextPrimary)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 7)
                                .background(
                                    RoundedRectangle(cornerRadius: 8)
                                        .fill(isSelected ? AppTheme.youTubeChipSelectedBg : AppTheme.youTubeChipBg)
                                )
                        }
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
            }

            // Videos List
            ScrollView {
                LazyVStack(spacing: 16) {
                    ForEach(videos) { video in
                        YouTubeVideoCard(video: video) {
                            onVideoClick(video)
                        }
                    }
                }
                .padding(.bottom, 80)
            }
        }
        .background(AppTheme.youTubeDarkBg.ignoresSafeArea())
    }
}

