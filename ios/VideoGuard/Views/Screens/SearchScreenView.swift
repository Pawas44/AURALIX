import SwiftUI

struct SearchScreenView: View {
    let appMode: AppMode
    let onBack: () -> Void
    let onVideoSelected: (VideoItem, [VideoItem]) -> Void
    let onChannelClick: (VideoItem) -> Void
    var onPlaylistClick: ((UserPlaylist) -> Void)? = nil

    @ObservedObject var userData = UserDataRepository.shared

    @State private var searchQuery = ""
    @State private var isSearching = false
    @State private var searchResults: [VideoItem] = []
    @State private var albumResults: [AlbumItem] = []
    @State private var suggestionsList: [String] = []
    @State private var hasSearched = false
    @State private var selectedCategory: String = "All"
    @State private var selectedVideoForPlaylist: VideoItem? = nil
    @State private var searchPage = 0
    @State private var isLoadingMore = false
    @State private var seenIds = Set<String>()
    @State private var debounceTask: Task<Void, Never>? = nil

    private let presetChips = [
        "The Weeknd", "Lofi Beats", "Imagine Dragons",
        "Dua Lipa", "Taylor Swift", "Trending News", "Tech Review"
    ]

    private let musicCategories = ["All", "Songs", "Albums", "Mixtapes", "Artists"]

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
                            albumResults = []
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

            // Category Chips when in Music mode and has searched
            if isMusic && hasSearched {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(musicCategories, id: \.self) { cat in
                            let isSelected = selectedCategory == cat
                            Button(action: { selectedCategory = cat }) {
                                Text(cat)
                                    .font(.system(size: 12, weight: isSelected ? .bold : .medium))
                                    .foregroundColor(isSelected ? .black : .white)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 6)
                                    .background(isSelected ? Color.white : Color(hex: 0x1E1E24))
                                    .cornerRadius(16)
                            }
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                }
            } else if !hasSearched {
                // Preset Chips
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
                ProgressView(isMusic ? "Searching music..." : "Searching YouTube...")
                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                    .foregroundColor(AppTheme.youTubeTextSecondary)
                Spacer()
            } else if hasSearched {
                ScrollView {
                    LazyVStack(spacing: 14) {
                        // Albums / Mixtapes Row if available and category is All, Albums, or Mixtapes
                        if isMusic && (selectedCategory == "All" || selectedCategory == "Albums" || selectedCategory == "Mixtapes") && !albumResults.isEmpty {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Albums & Singles")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 14)

                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: 12) {
                                        ForEach(albumResults) { album in
                                            Button(action: {
                                                let pl = AlbumDataRepository.shared.toUserPlaylist(album: album)
                                                onPlaylistClick?(pl)
                                            }) {
                                                VStack(alignment: .leading, spacing: 6) {
                                                    AsyncImage(url: URL(string: album.artworkUrl)) { img in
                                                        img.resizable().aspectRatio(contentMode: .fill)
                                                    } placeholder: {
                                                        Rectangle().fill(Color(hex: 0x1E1E24))
                                                    }
                                                    .frame(width: 120, height: 120)
                                                    .cornerRadius(10)
                                                    .clipped()

                                                    Text(album.title)
                                                        .font(.system(size: 13, weight: .semibold))
                                                        .foregroundColor(.white)
                                                        .lineLimit(1)
                                                        .frame(width: 120, alignment: .leading)

                                                    Text("\(album.artist) • \(album.year)")
                                                        .font(.system(size: 11))
                                                        .foregroundColor(AppTheme.ytMusicTextSecondary)
                                                        .lineLimit(1)
                                                        .frame(width: 120, alignment: .leading)
                                                }
                                            }
                                        }
                                    }
                                    .padding(.horizontal, 14)
                                }
                            }
                            .padding(.top, 6)
                        }

                        // Songs list
                        if selectedCategory != "Albums" && selectedCategory != "Mixtapes" {
                            let filteredSongs = searchResults.filter { item in
                                if selectedCategory == "Artists" {
                                    return item.category == "Channel"
                                } else if selectedCategory == "Songs" {
                                    return item.category != "Channel"
                                }
                                return true
                            }

                            if filteredSongs.isEmpty && albumResults.isEmpty {
                                Text("No results found for \"\(searchQuery)\"")
                                    .foregroundColor(AppTheme.youTubeTextSecondary)
                                    .padding(.top, 40)
                            } else {
                                if isMusic {
                                    // YouTube Music Mode Search Results
                                    // 1. Top Result Card
                                    if selectedCategory == "All", let topMatch = filteredSongs.first {
                                        VStack(alignment: .leading, spacing: 8) {
                                            Text("TOP RESULT")
                                                .font(.system(size: 11, weight: .bold))
                                                .foregroundColor(AppTheme.ytMusicTextSecondary)
                                                .tracking(0.8)
                                                .padding(.horizontal, 14)

                                            Button(action: {
                                                if topMatch.category == "Album" || topMatch.id.hasPrefix("OLAK5uy_") || topMatch.id.hasPrefix("MPREb_") {
                                                    let album = AlbumItem(
                                                        id: topMatch.id,
                                                        title: topMatch.title,
                                                        artist: topMatch.artist.isEmpty ? topMatch.channelName : topMatch.artist,
                                                        coverUrl: topMatch.thumbnailUrl,
                                                        year: "Album",
                                                        trackCount: 0
                                                    )
                                                    onPlaylistClick(AlbumDataRepository.toUserPlaylist(album))
                                                } else {
                                                    onVideoSelected(topMatch, filteredSongs)
                                                }
                                            }) {
                                                let artistName = !topMatch.artist.isEmpty ? topMatch.artist : topMatch.channelName
                                                let resolvedUrl = ArtworkUrlHelper.getHighResArtworkUrl(topMatch.thumbnailUrl, videoId: topMatch.id)
                                                HStack(spacing: 14) {
                                                    AsyncImage(url: URL(string: resolvedUrl)) { phase in
                                                        switch phase {
                                                        case .success(let img):
                                                            img.resizable().aspectRatio(contentMode: .fill)
                                                        case .failure:
                                                            AsyncImage(url: URL(string: "https://i.ytimg.com/vi/\(topMatch.id)/hqdefault.jpg")) { fbPhase in
                                                                if let fbImg = fbPhase.image {
                                                                    fbImg.resizable().aspectRatio(contentMode: .fill)
                                                                } else {
                                                                    ZStack {
                                                                        Rectangle().fill(AppTheme.ytMusicCardBg)
                                                                        Image(systemName: "music.note")
                                                                            .font(.system(size: 22))
                                                                            .foregroundColor(.white.opacity(0.4))
                                                                    }
                                                                }
                                                            }
                                                        case .empty:
                                                            Rectangle().fill(AppTheme.ytMusicCardBg)
                                                        @unknown default:
                                                            Rectangle().fill(AppTheme.ytMusicCardBg)
                                                        }
                                                    }
                                                    .frame(width: 64, height: 64)
                                                    .cornerRadius(8)
                                                    .clipped()

                                                    VStack(alignment: .leading, spacing: 4) {
                                                        Text(topMatch.title)
                                                            .font(.system(size: 15, weight: .bold))
                                                            .foregroundColor(.white)
                                                            .lineLimit(1)
                                                        Text("\(topMatch.category == "Album" ? "Album" : "Song") • \(artistName) • \(topMatch.duration)")
                                                            .font(.system(size: 12))
                                                            .foregroundColor(AppTheme.ytMusicTextSecondary)
                                                            .lineLimit(1)
                                                    }

                                                    Spacer()

                                                    ZStack {
                                                        Circle()
                                                            .fill(Color.white)
                                                            .frame(width: 38, height: 38)
                                                        Image(systemName: topMatch.category == "Album" ? "square.stack.fill" : "play.fill")
                                                            .font(.system(size: 15))
                                                            .foregroundColor(.black)
                                                            .offset(x: topMatch.category == "Album" ? 0 : 1)
                                                    }
                                                }
                                                .padding(12)
                                                .background(Color.white.opacity(0.08))
                                                .cornerRadius(12)
                                                .padding(.horizontal, 14)
                                            }
                                        }
                                        .padding(.bottom, 6)
                                    }

                                    // 2. Songs Section Header
                                    if selectedCategory == "All" && filteredSongs.count > 1 {
                                        HStack {
                                            Text("SONGS")
                                                .font(.system(size: 11, weight: .bold))
                                                .foregroundColor(AppTheme.ytMusicTextSecondary)
                                                .tracking(0.8)
                                            Spacer()
                                        }
                                        .padding(.horizontal, 14)
                                        .padding(.top, 6)
                                    }

                                    // 3. Songs Rows
                                    let songsToDisplay = (selectedCategory == "All" && filteredSongs.count > 1) ? Array(filteredSongs.dropFirst()) : filteredSongs

                                    ForEach(Array(songsToDisplay.enumerated()), id: \.element.id) { index, item in
                                        if item.category == "Channel" {
                                            Button(action: { onChannelClick(item) }) {
                                                HStack(spacing: 14) {
                                                    AsyncImage(url: URL(string: item.channelAvatarUrl)) { img in
                                                        img.resizable().aspectRatio(contentMode: .fill)
                                                    } placeholder: {
                                                        Circle().fill(Color.gray.opacity(0.4))
                                                    }
                                                    .frame(width: 52, height: 52)
                                                    .clipShape(Circle())

                                                    VStack(alignment: .leading, spacing: 3) {
                                                        Text(item.channelName)
                                                            .font(.system(size: 15, weight: .semibold))
                                                            .foregroundColor(.white)
                                                        Text("Artist • \(item.subscriberCount)")
                                                            .font(.system(size: 12))
                                                            .foregroundColor(AppTheme.ytMusicTextSecondary)
                                                    }
                                                    Spacer()
                                                    Image(systemName: "chevron.right")
                                                        .font(.system(size: 13))
                                                        .foregroundColor(AppTheme.ytMusicTextSecondary)
                                                }
                                                .padding(.horizontal, 14)
                                                .padding(.vertical, 6)
                                            }
                                        } else {
                                            Button(action: { onVideoSelected(item, filteredSongs) }) {
                                                let artistName = !item.artist.isEmpty ? item.artist : item.channelName
                                                let rowThumbUrl = ArtworkUrlHelper.getHighResArtworkUrl(item.thumbnailUrl, videoId: item.id)
                                                HStack(spacing: 12) {
                                                    AsyncImage(url: URL(string: rowThumbUrl)) { phase in
                                                        if let img = phase.image {
                                                            img.resizable().aspectRatio(contentMode: .fill)
                                                        } else if phase.error != nil {
                                                            AsyncImage(url: URL(string: "https://i.ytimg.com/vi/\(item.id)/hqdefault.jpg")) { fbPhase in
                                                                if let fb = fbPhase.image {
                                                                    fb.resizable().aspectRatio(contentMode: .fill)
                                                                } else {
                                                                    ZStack {
                                                                        Rectangle().fill(AppTheme.ytMusicCardBg)
                                                                        Image(systemName: "music.note")
                                                                            .font(.system(size: 18))
                                                                            .foregroundColor(.white.opacity(0.3))
                                                                    }
                                                                }
                                                            }
                                                        } else {
                                                            Rectangle().fill(AppTheme.ytMusicCardBg)
                                                        }
                                                    }
                                                    .frame(width: 52, height: 52)
                                                    .cornerRadius(6)
                                                    .clipped()

                                                    VStack(alignment: .leading, spacing: 3) {
                                                        Text(item.title)
                                                            .font(.system(size: 14, weight: .medium))
                                                            .foregroundColor(.white)
                                                            .lineLimit(1)
                                                            .multilineTextAlignment(.leading)

                                                        Text("Song • \(artistName) • \(item.duration)")
                                                            .font(.system(size: 12))
                                                            .foregroundColor(AppTheme.ytMusicTextSecondary)
                                                            .lineLimit(1)
                                                    }

                                                    Spacer()

                                                    Button(action: { selectedVideoForPlaylist = item }) {
                                                        Image(systemName: "ellipsis")
                                                            .font(.system(size: 16))
                                                            .foregroundColor(AppTheme.ytMusicTextSecondary)
                                                            .padding(8)
                                                    }
                                                }
                                                .padding(.horizontal, 14)
                                                .padding(.vertical, 6)
                                            }
                                            .onAppear {
                                                if index == songsToDisplay.count - 3 {
                                                    loadMoreSearchResults()
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Regular YouTube Video Mode Search Results
                                    ForEach(Array(filteredSongs.enumerated()), id: \.element.id) { index, item in
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
                                                    Image(systemName: "chevron.right")
                                                        .font(.system(size: 14))
                                                        .foregroundColor(AppTheme.youTubeTextSecondary)
                                                }
                                                .padding(.horizontal, 14)
                                                .padding(.vertical, 6)
                                            }
                                        } else {
                                            Button(action: { onVideoSelected(item, filteredSongs) }) {
                                                HStack(spacing: 12) {
                                                    AsyncImage(url: URL(string: item.thumbnailUrl)) { img in
                                                        img.resizable().aspectRatio(16/9, contentMode: .fill)
                                                    } placeholder: {
                                                        Rectangle().fill(AppTheme.youTubeCardBg)
                                                    }
                                                    .frame(width: 120, height: 68)
                                                    .cornerRadius(8)
                                                    .clipped()

                                                    VStack(alignment: .leading, spacing: 4) {
                                                        Text(item.title)
                                                            .font(.system(size: 14, weight: .medium))
                                                            .foregroundColor(.white)
                                                            .lineLimit(2)
                                                            .multilineTextAlignment(.leading)

                                                        Text("\(!item.artist.isEmpty ? item.artist : item.channelName) • \(item.duration)")
                                                            .font(.system(size: 12))
                                                            .foregroundColor(AppTheme.youTubeTextSecondary)
                                                            .lineLimit(1)
                                                    }
                                                    Spacer()

                                                    Button(action: { selectedVideoForPlaylist = item }) {
                                                        Image(systemName: "plus.circle")
                                                            .font(.system(size: 18))
                                                            .foregroundColor(AppTheme.youTubeTextSecondary)
                                                            .padding(6)
                                                    }
                                                }
                                                .padding(.horizontal, 14)
                                                .padding(.vertical, 4)
                                            }
                                            .onAppear {
                                                if index == filteredSongs.count - 3 {
                                                    loadMoreSearchResults()
                                                }
                                            }
                                        }
                                    }
                                }

                                if isLoadingMore {
                                    ProgressView()
                                        .tint(.white)
                                        .padding(.vertical, 16)
                                }
                            }
                        }
                    }
                    .padding(.top, 10)
                    .padding(.bottom, 80)
                }
            } else {
                // Suggestions and search history
                List {
                    if !suggestionsList.isEmpty {
                        Section("Suggestions") {
                            ForEach(suggestionsList, id: \.self) { suggestion in
                                Button(action: {
                                    searchQuery = suggestion
                                    performSearch(suggestion)
                                }) {
                                    HStack {
                                        Image(systemName: "magnifyingglass")
                                            .foregroundColor(AppTheme.youTubeTextSecondary)
                                        Text(suggestion)
                                            .foregroundColor(.white)
                                        Spacer()
                                        Image(systemName: "arrow.up.left")
                                            .foregroundColor(AppTheme.youTubeTextSecondary)
                                    }
                                }
                                .listRowBackground(Color.clear)
                            }
                        }
                    }

                    if !userData.searchHistory.isEmpty {
                        Section("Recent searches") {
                            ForEach(userData.searchHistory, id: \.self) { term in
                                HStack {
                                    Button(action: {
                                        searchQuery = term
                                        performSearch(term)
                                    }) {
                                        HStack {
                                            Image(systemName: "clock")
                                                .foregroundColor(AppTheme.youTubeTextSecondary)
                                            Text(term)
                                                .foregroundColor(.white)
                                            Spacer()
                                        }
                                    }
                                    Button(action: { userData.removeSearchQuery(term) }) {
                                        Image(systemName: "xmark")
                                            .foregroundColor(AppTheme.youTubeTextSecondary)
                                            .padding(4)
                                    }
                                }
                                .listRowBackground(Color.clear)
                            }
                        }
                    }
                }
                .listStyle(.plain)
            }
        }
        .background(LiquidGlassDefaults.liquidCanvas.ignoresSafeArea())
        .sheet(item: $selectedVideoForPlaylist) { vid in
            AddToPlaylistSheet(video: vid) {
                selectedVideoForPlaylist = nil
            }
            .presentationDetents([.medium, .large])
        }
    }

    private func handleQueryChange(_ text: String) {
        debounceTask?.cancel()
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            suggestionsList = []
            return
        }
        debounceTask = Task {
            try? await Task.sleep(nanoseconds: 300_000_000)
            guard !Task.isCancelled else { return }
            let list = await YouTubeSearchEngine.fetchSearchSuggestions(query: trimmed)
            await MainActor.run {
                self.suggestionsList = list
            }
        }
    }

    private func performSearch(_ query: String) {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        searchQuery = trimmed
        suggestionsList = []
        isSearching = true
        hasSearched = true
        searchPage = 0
        seenIds = []
        userData.addSearchQuery(trimmed)

        Task {
            let isMusic = appMode == .youtubeMusic
            let results = await YouTubeSearchEngine.searchVideos(
                query: trimmed,
                isMusicOnly: isMusic,
                page: 0
            )

            var albums: [AlbumItem] = []
            if isMusic {
                albums = await AlbumDataRepository.shared.searchAlbums(query: trimmed)
            }

            await MainActor.run {
                self.searchResults = results
                self.albumResults = albums
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
