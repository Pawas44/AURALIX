import SwiftUI

struct YouTubeFeedView: View {
    let onVideoSelected: (VideoItem, [VideoItem]) -> Void
    let onSwitchToMusic: () -> Void
    let onChannelClick: (VideoItem) -> Void
    let scrollToTopTrigger: Int

    @ObservedObject var syncEngine = YouTubeSyncEngine.shared
    @ObservedObject var accountRepo = AccountRepository.shared

    @State private var selectedCategory: String = "All"
    @State private var feedVideos: [VideoItem] = VideoRepository.sampleVideos
    @State private var isLoadingFeed = false
    @State private var isLoadingMore = false

    private let categories = VideoRepository.youtubeCategories

    var body: some View {
        VStack(spacing: 0) {
            // Category Chips Carousel
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(categories, id: \.self) { category in
                        let isSelected = category == selectedCategory
                        Button(action: { selectedCategory = category }) {
                            Text(category)
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(isSelected ? AppTheme.youTubeChipSelectedText : AppTheme.youTubeTextPrimary)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 7)
                                .background(
                                    RoundedRectangle(cornerRadius: 10)
                                        .fill(isSelected ? AppTheme.youTubeChipSelectedBg : Color.white.opacity(0.08))
                                )
                        }
                    }

                    Button(action: onSwitchToMusic) {
                        HStack(spacing: 4) {
                            Image(systemName: "music.note")
                                .font(.system(size: 12))
                            Text("Music Mode")
                                .font(.system(size: 13, weight: .medium))
                        }
                        .foregroundColor(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .liquidGlassPill(isSelected: true, activeColor: AppTheme.ytMusicRedAccent)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
            }

            if isLoadingFeed && feedVideos.isEmpty {
                Spacer()
                ProgressView("Loading feed...")
                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                    .foregroundColor(AppTheme.youTubeTextSecondary)
                Spacer()
            } else {
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing: 14) {
                            Color.clear.frame(height: 1).id("FEED_TOP")

                            ForEach(Array(feedVideos.enumerated()), id: \.element.id) { index, video in
                                YouTubeVideoCard(
                                    video: video,
                                    onClick: {
                                        onVideoSelected(video, feedVideos)
                                    },
                                    onChannelClick: {
                                        onChannelClick(video)
                                    }
                                )
                                .onAppear {
                                    if index >= feedVideos.count - 3 {
                                        loadMoreFeed()
                                    }
                                }
                            }

                            if isLoadingMore {
                                HStack {
                                    Spacer()
                                    ProgressView()
                                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                        .padding()
                                    Spacer()
                                }
                            }
                        }
                        .padding(.bottom, 80)
                    }
                    .refreshable {
                        let online = await syncEngine.fetchHomeFeed(category: selectedCategory)
                        if !online.isEmpty {
                            feedVideos = online
                        }
                    }
                    .onChange(of: scrollToTopTrigger) { _ in
                        withAnimation {
                            proxy.scrollTo("FEED_TOP", anchor: .top)
                        }
                    }
                }
            }
        }
        .task(id: "\(selectedCategory)_\(accountRepo.currentAccount?.id ?? "guest")") {
            isLoadingFeed = true
            let online = await syncEngine.fetchHomeFeed(category: selectedCategory)
            if !online.isEmpty {
                feedVideos = online
            } else {
                feedVideos = VideoRepository.getVideosByCategory(selectedCategory)
            }
            isLoadingFeed = false
        }
        .onChange(of: syncEngine.homeFeed) { updated in
            if !updated.isEmpty && selectedCategory == "All" {
                feedVideos = updated
            }
        }
    }

    private func loadMoreFeed() {
        guard !isLoadingFeed && !isLoadingMore else { return }
        isLoadingMore = true
        Task {
            let loaded = await syncEngine.fetchMoreHomeFeed()
            if loaded && !syncEngine.homeFeed.isEmpty {
                await MainActor.run {
                    feedVideos = syncEngine.homeFeed
                }
            }
            await MainActor.run {
                isLoadingMore = false
            }
        }
    }
}
