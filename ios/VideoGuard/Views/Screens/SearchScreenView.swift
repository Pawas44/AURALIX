import SwiftUI

struct SearchScreenView: View {
    let appMode: AppMode
    let onBack: () -> Void
    let onVideoSelected: (VideoItem, [VideoItem]) -> Void
    let onChannelClick: (VideoItem) -> Void

    @ObservedObject var userData = UserDataRepository.shared

    @State private var searchQuery = ""
    @State private var isSearching = false
    @State private var searchResults: [VideoItem] = []
    @State private var suggestionsList: [String] = []
    @State private var hasSearched = false
    @State private var selectedVideoForPlaylist: VideoItem? = nil
    @State private var searchPage = 0
    @State private var isLoadingMore = false
    @State private var seenIds = Set<String>()
    @State private var debounceTask: Task<Void, Never>? = nil

    private let presetChips = [
        "The Weeknd", "Lofi Beats", "Imagine Dragons",
        "Dua Lipa", "Taylor Swift", "Trending News", "Tech Review"
    ]

    var body: some View {
        let isMusic = appMode == .youtubeMusic

        VStack(spacing: 0) {
            // Top Search Input Bar
            HStack(spacing: 10) {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                        .padding(6)
                }

                HStack {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(AppTheme.youTubeTextSecondary)

                    TextField(isMusic ? "Search songs, albums, artists..." : "Search YouTube", text: $searchQuery)
                        .foregroundColor(.white)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)
                        .submitLabel(.search)
                        .onSubmit {
                            performSearch(searchQuery)
                        }
                        .onChange(of: searchQuery) { newValue in
                            handleQueryChange(newValue)
                        }

                    if !searchQuery.isEmpty {
                        Button(action: {
                            searchQuery = ""
                            suggestionsList = []
                            hasSearched = false
                            searchResults = []
                        }) {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundColor(AppTheme.youTubeTextSecondary)
                        }
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Color.white.opacity(0.09))
                .cornerRadius(20)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)

            // Preset Chips
            if !hasSearched {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(presetChips, id: \.self) { chip in
                            Button(action: { performSearch(chip) }) {
                                Text(chip)
                                    .font(.system(size: 12, weight: .medium))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 6)
                                    .liquidGlassPill()
                            }
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                }
            }

            Divider().background(Color.white.opacity(0.1))

            // Main Content
            if isSearching {
                Spacer()
                ProgressView("Searching YouTube...")
                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                    .foregroundColor(AppTheme.youTubeTextSecondary)
                Spacer()
            } else if hasSearched {
                ScrollView {
                    LazyVStack(spacing: 14) {
                        if searchResults.isEmpty {
                            Text("No results found for \"\(searchQuery)\"")
                                .foregroundColor(AppTheme.youTubeTextSecondary)
                                .padding(.top, 40)
                        } else {
                            ForEach(Array(searchResults.enumerated()), id: \.element.id) { index, item in
                                if item.category == "Channel" {
                                    Button(action: { onChannelClick(item) }) {
                                        HStack(spacing: 14) {
                                            AsyncImage(url: URL(string: item.channelAvatarUrl)) { img in
                                                img.resizable().aspectRatio(contentMode: .fill)
                                            } placeholder: {
                                                Circle().fill(Color.gray.opacity(0.4))
                                            }
                                            .frame(width: 60, height: 60)
                                            .clipShape(Circle())

                                            VStack(alignment: .leading, spacing: 4) {
                                                Text(item.channelName)
                                                    .font(.system(size: 16, weight: .bold))
                                                    .foregroundColor(.white)
                                                Text(item.subscriberCount)
                                                    .font(.system(size: 13))
                                                    .foregroundColor(AppTheme.youTubeTextSecondary)
                                            }
                                            Spacer()
                                            Text("View")
                                                .font(.system(size: 13, weight: .semibold))
                                                .foregroundColor(.black)
                                                .padding(.horizontal, 14)
                                                .padding(.vertical, 7)
                                                .background(Color.white)
                                                .cornerRadius(18)
                                        }
                                        .padding(.horizontal, 14)
                                        .padding(.vertical, 8)
                                    }
                                } else {
                                    YouTubeVideoCard(
                                        video: item,
                                        onClick: {
                                            let queue = searchResults.filter { $0.category != "Channel" }
                                            onVideoSelected(item, queue)
                                        },
                                        onChannelClick: {
                                            onChannelClick(item)
                                        },
                                        onSaveToPlaylist: {
                                            selectedVideoForPlaylist = item
                                        }
                                    )
                                }

                                if index >= searchResults.count - 3 {
                                    Color.clear.frame(height: 1).onAppear {
                                        loadMoreSearchResults()
                                    }
                                }
                            }

                            if isLoadingMore {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                    .padding()
                            }
                        }
                    }
                    .padding(.vertical, 10)
                    .padding(.bottom, 80)
                }
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        // Live Suggestions
                        if !suggestionsList.isEmpty {
                            ForEach(suggestionsList, id: \.self) { suggestion in
                                Button(action: { performSearch(suggestion) }) {
                                    HStack(spacing: 14) {
                                        Image(systemName: "magnifyingglass")
                                            .font(.system(size: 15))
                                            .foregroundColor(AppTheme.youTubeTextSecondary)
                                        Text(suggestion)
                                            .font(.system(size: 15))
                                            .foregroundColor(.white)
                                        Spacer()
                                        Image(systemName: "arrow.up.left")
                                            .font(.system(size: 13))
                                            .foregroundColor(AppTheme.youTubeTextSecondary)
                                    }
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 13)
                                }
                            }
                        } else {
                            // Search History + Trending
                            if !userData.searchHistory.isEmpty {
                                HStack {
                                    Text("Recent searches")
                                        .font(.system(size: 13, weight: .semibold))
                                        .foregroundColor(AppTheme.youTubeTextSecondary)
                                    Spacer()
                                    Button("Clear") {
                                        userData.clearSearchHistory()
                                    }
                                    .font(.system(size: 12))
                                    .foregroundColor(Color(hex: 0x3EA6FF))
                                }
                                .padding(.horizontal, 16)
                                .padding(.top, 12)
                                .padding(.bottom, 4)

                                ForEach(userData.searchHistory, id: \.self) { item in
                                    HStack(spacing: 14) {
                                        Button(action: { performSearch(item) }) {
                                            HStack(spacing: 14) {
                                                Image(systemName: "clock.arrow.circlepath")
                                                    .font(.system(size: 15))
                                                    .foregroundColor(AppTheme.youTubeTextSecondary)
                                                Text(item)
                                                    .font(.system(size: 15))
                                                    .foregroundColor(.white)
                                                Spacer()
                                            }
                                        }
                                        Button(action: { userData.removeSearchQuery(item) }) {
                                            Image(systemName: "xmark")
                                                .font(.system(size: 12))
                                                .foregroundColor(AppTheme.youTubeTextSecondary)
                                                .padding(4)
                                        }
                                    }
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 11)
                                }
                            }

                            Text("Trending searches")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(AppTheme.youTubeTextSecondary)
                                .padding(.horizontal, 16)
                                .padding(.top, 14)
                                .padding(.bottom, 4)

                            ForEach(YouTubeSearchEngine.getTrendingSearches(), id: \.self) { item in
                                Button(action: { performSearch(item) }) {
                                    HStack(spacing: 14) {
                                        Image(systemName: "flame")
                                            .font(.system(size: 15))
                                            .foregroundColor(AppTheme.youTubeRed)
                                        Text(item)
                                            .font(.system(size: 15))
                                            .foregroundColor(.white)
                                        Spacer()
                                    }
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 12)
                                }
                            }
                        }
                    }
                }
            }
        }
        .sheet(item: $selectedVideoForPlaylist) { vid in
            AddToPlaylistSheet(video: vid) {
                selectedVideoForPlaylist = nil
            }
        }
    }

    private func handleQueryChange(_ newValue: String) {
        hasSearched = false
        debounceTask?.cancel()
        let trimmed = newValue.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            suggestionsList = []
            return
        }
        debounceTask = Task {
            try? await Task.sleep(nanoseconds: 250_000_000)
            guard !Task.isCancelled else { return }
            let fetched = await YouTubeSearchEngine.getSuggestions(query: trimmed)
            await MainActor.run {
                self.suggestionsList = fetched
            }
        }
    }

    private func performSearch(_ rawQuery: String) {
        let trimmed = rawQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        searchQuery = trimmed
        suggestionsList = []
        isSearching = true
        hasSearched = true
        searchPage = 0
        seenIds = []
        userData.addSearchQuery(trimmed)

        Task {
            let results = await YouTubeSearchEngine.searchVideos(
                query: trimmed,
                isMusicOnly: appMode == .youtubeMusic,
                page: 0
            )
            await MainActor.run {
                self.searchResults = results
                self.seenIds = Set(results.map { $0.id })
                self.isSearching = false
            }
        }
    }

    private func loadMoreSearchResults() {
        guard !isLoadingMore && !searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        isLoadingMore = true
        searchPage += 1
        let pageToFetch = searchPage
        let q = searchQuery
        let isMusic = appMode == .youtubeMusic

        Task {
            let more = await YouTubeSearchEngine.searchVideos(query: q, isMusicOnly: isMusic, page: pageToFetch)
            let newItems = more.filter { !seenIds.contains($0.id) }
            await MainActor.run {
                if !newItems.isEmpty {
                    seenIds.formUnion(newItems.map { $0.id })
                    searchResults.append(contentsOf: newItems)
                }
                isLoadingMore = false
            }
        }
    }
}
