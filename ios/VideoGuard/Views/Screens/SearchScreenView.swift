import SwiftUI

struct SearchScreenView: View {
    let appMode: AppMode
    let onBack: () -> Void
    let onVideoClick: (VideoItem) -> Void

    @State private var query = ""
    @State private var hasSearched = false

    var body: some View {
        let bgColor = appMode == .youtube ? AppTheme.youTubeDarkBg : AppTheme.ytMusicBlack
        let suggestions = YouTubeSearchEngine.getSuggestions(query: query)
        let results = appMode == .youtube ? YouTubeSearchEngine.search(query: query).videos : YouTubeSearchEngine.searchMusic(query: query).videos

        VStack(spacing: 0) {
            // Search Input Top Bar
            HStack(spacing: 12) {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(AppTheme.youTubeTextPrimary)
                }

                HStack {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(AppTheme.youTubeTextSecondary)

                    TextField(appMode == .youtube ? "Search YouTube" : "Search songs, albums...", text: $query)
                        .foregroundColor(AppTheme.youTubeTextPrimary)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)
                        .onSubmit {
                            hasSearched = true
                        }

                    if !query.isEmpty {
                        Button(action: {
                            query = ""
                            hasSearched = false
                        }) {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundColor(AppTheme.youTubeTextSecondary)
                        }
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(AppTheme.youTubeChipBg)
                .cornerRadius(10)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)

            Divider().background(AppTheme.youTubeBorder)

            // Content: Suggestions or Results
            ScrollView {
                if !hasSearched && query.isEmpty {
                    // Trending
                    VStack(alignment: .leading, spacing: 0) {
                        ForEach(YouTubeSearchEngine.getTrendingSearches(), id: \.self) { item in
                            Button(action: {
                                query = item
                                hasSearched = true
                            }) {
                                HStack(spacing: 16) {
                                    Image(systemName: "arrow.up.right")
                                        .font(.system(size: 14))
                                        .foregroundColor(AppTheme.youTubeTextSecondary)
                                    Text(item)
                                        .font(.system(size: 15))
                                        .foregroundColor(AppTheme.youTubeTextPrimary)
                                    Spacer()
                                }
                                .padding(.horizontal, 16)
                                .padding(.vertical, 14)
                            }
                        }
                    }
                } else if !hasSearched {
                    // Live Suggestions
                    VStack(alignment: .leading, spacing: 0) {
                        ForEach(suggestions, id: \.self) { item in
                            Button(action: {
                                query = item
                                hasSearched = true
                            }) {
                                HStack(spacing: 16) {
                                    Image(systemName: "magnifyingglass")
                                        .font(.system(size: 14))
                                        .foregroundColor(AppTheme.youTubeTextSecondary)
                                    Text(item)
                                        .font(.system(size: 15))
                                        .foregroundColor(AppTheme.youTubeTextPrimary)
                                    Spacer()
                                }
                                .padding(.horizontal, 16)
                                .padding(.vertical, 14)
                            }
                        }
                    }
                } else {
                    // Search Results
                    if results.isEmpty {
                        VStack(spacing: 12) {
                            Spacer().frame(height: 60)
                            Image(systemName: "magnifyingglass")
                                .font(.system(size: 40))
                                .foregroundColor(AppTheme.youTubeTextSecondary)
                            Text("No results found for \"\(query)\"")
                                .font(.system(size: 15))
                                .foregroundColor(AppTheme.youTubeTextSecondary)
                        }
                    } else {
                        LazyVStack(spacing: 14) {
                            ForEach(results) { video in
                                Button(action: { onVideoClick(video) }) {
                                    HStack(spacing: 12) {
                                        ZStack(alignment: .bottomTrailing) {
                                            AsyncImage(url: URL(string: video.thumbnailUrl)) { image in
                                                image.resizable().aspectRatio(16/9, contentMode: .fit)
                                            } placeholder: {
                                                Rectangle().fill(AppTheme.youTubeCardBg).aspectRatio(16/9, contentMode: .fit)
                                            }
                                            .frame(width: 140)
                                            .cornerRadius(8)
                                            .clipped()

                                            Text(video.duration)
                                                .font(.system(size: 10, weight: .bold))
                                                .foregroundColor(.white)
                                                .padding(.horizontal, 4)
                                                .padding(.vertical, 2)
                                                .background(Color.black.opacity(0.8))
                                                .cornerRadius(3)
                                                .padding(4)
                                        }

                                        VStack(alignment: .leading, spacing: 4) {
                                            Text(video.title)
                                                .font(.system(size: 14, weight: .medium))
                                                .foregroundColor(AppTheme.youTubeTextPrimary)
                                                .lineLimit(2)
                                                .multilineTextAlignment(.leading)

                                            Text("\(video.channelName) • \(video.views)")
                                                .font(.system(size: 12))
                                                .foregroundColor(AppTheme.youTubeTextSecondary)
                                                .lineLimit(1)
                                        }
                                        Spacer()
                                    }
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 4)
                                }
                            }
                        }
                        .padding(.top, 10)
                        .padding(.bottom, 60)
                    }
                }
            }
        }
        .background(bgColor.ignoresSafeArea())
    }
}

