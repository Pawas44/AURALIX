import SwiftUI

struct YouTubeMusicHomeView: View {
    let onTrackSelected: (VideoItem, [VideoItem]) -> Void
    var onPlaylistClick: ((UserPlaylist) -> Void)? = nil
    let scrollToTopTrigger: Int

    @ObservedObject var syncEngine = YouTubeSyncEngine.shared
    @ObservedObject var userData = UserDataRepository.shared
    @ObservedObject var recEngine = RecommendationEngine.shared

    @State private var selectedMood: String = ""
    @State private var isRefreshing = false

    private let moods = VideoRepository.musicCategories

    private var smartFeed: SmartHomeFeed {
        recEngine.feedState
    }

    private var madeForYouList: [VideoItem] {
        if !smartFeed.madeForYou.isEmpty {
            return SongDeduplicationEngine.shared.ensureNoConsecutiveDuplicates(smartFeed.madeForYou)
        }
        return quickPicks
    }

    private var quickPicks: [VideoItem] {
        syncEngine.musicFeed.quickPicks.isEmpty ? VideoRepository.getMusicTracks() : syncEngine.musicFeed.quickPicks
    }

    private var listenAgain: [VideoItem] {
        syncEngine.musicFeed.listenAgain.isEmpty ? VideoRepository.getMusicTracks() : syncEngine.musicFeed.listenAgain
    }

    private var albumsList: [AlbumItem] {
        AlbumDataRepository.shared.getAllAlbums()
    }

    private var allMusicTracks: [VideoItem] {
        SongDeduplicationEngine.shared.deduplicate(madeForYouList + quickPicks + listenAgain + syncEngine.musicFeed.trendingCharts)
    }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    Color.clear.frame(height: 1).id("MUSIC_TOP")

                    // Mood / Activity Filter Chips in Liquid Glass Style
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(moods, id: \.self) { mood in
                                let isSelected = mood == selectedMood
                                Button(action: {
                                    selectedMood = isSelected ? "" : mood
                                }) {
                                    Text(mood)
                                        .font(.system(size: 13, weight: isSelected ? .bold : .medium))
                                        .foregroundColor(isSelected ? AppTheme.ytMusicChipActiveText : .white)
                                        .padding(.horizontal, 14)
                                        .padding(.vertical, 8)
                                        .liquidGlassPill(isSelected: isSelected, activeColor: .white)
                                }
                            }
                        }
                        .padding(.horizontal, 14)
                    }

                    if isRefreshing {
                        HStack {
                            Spacer()
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: AppTheme.ytMusicRedAccent))
                            Spacer()
                        }
                        .padding(.vertical, 4)
                    }

                    // Section 1: Made For You (Personalized Quick Picks - strictly deduplicated)
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(!selectedMood.isEmpty ? selectedMood.uppercased() : "MADE FOR YOU")
                                    .font(.system(size: 11, weight: .bold))
                                    .tracking(0.8)
                                    .foregroundColor(Color(hex: 0x909090))
                                Text(!selectedMood.isEmpty ? "\(selectedMood) Mix" : "Made For You")
                                    .font(.system(size: 22, weight: .bold))
                                    .foregroundColor(.white)
                            }
                            Spacer()
                            HStack(spacing: 8) {
                                Button(action: {
                                    guard !isRefreshing else { return }
                                    Task {
                                        isRefreshing = true
                                        await recEngine.refreshHomeFeed(selectedMood: selectedMood.isEmpty ? nil : selectedMood, forceRefresh: true)
                                        isRefreshing = false
                                    }
                                }) {
                                    HStack(spacing: 5) {
                                        Image(systemName: "arrow.clockwise")
                                            .font(.system(size: 11, weight: .semibold))
                                        Text("Refresh")
                                            .font(.system(size: 12, weight: .semibold))
                                    }
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 6)
                                    .background(Color.white.opacity(0.10))
                                    .clipShape(RoundedRectangle(cornerRadius: 16))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 16)
                                            .strokeBorder(LiquidGlassDefaults.borderSpecular, lineWidth: 1)
                                    )
                                }

                                Button(action: {
                                    if let first = madeForYouList.first {
                                        onTrackSelected(first, madeForYouList)
                                    }
                                }) {
                                    Text("Play all")
                                        .font(.system(size: 12, weight: .semibold))
                                        .foregroundColor(.white)
                                        .padding(.horizontal, 14)
                                        .padding(.vertical, 6)
                                        .background(Color.white.opacity(0.10))
                                        .clipShape(RoundedRectangle(cornerRadius: 16))
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 16)
                                                .strokeBorder(LiquidGlassDefaults.borderSpecular, lineWidth: 1)
                                        )
                                }
                            }
                        }
                        .padding(.horizontal, 14)

                        LazyVStack(spacing: 10) {
                            ForEach(madeForYouList.prefix(8)) { track in
                                Button(action: { onTrackSelected(track, madeForYouList) }) {
                                    HStack(spacing: 12) {
                                        AsyncImage(url: URL(string: track.thumbnailUrl)) { image in
                                            image.resizable().aspectRatio(contentMode: .fill)
                                        } placeholder: {
                                            Rectangle().fill(AppTheme.ytMusicCardBg)
                                        }
                                        .frame(width: 52, height: 52)
                                        .cornerRadius(6)
                                        .clipped()

                                        VStack(alignment: .leading, spacing: 3) {
                                            Text(track.title)
                                                .font(.system(size: 14, weight: .medium))
                                                .foregroundColor(.white)
                                                .lineLimit(1)
                                            Text("\(track.artist.isEmpty ? track.channelName : track.artist) • \(track.duration)")
                                                .font(.system(size: 12))
                                                .foregroundColor(AppTheme.ytMusicTextSecondary)
                                                .lineLimit(1)
                                        }

                                        Spacer()

                                        Image(systemName: "play.circle")
                                            .font(.system(size: 20))
                                            .foregroundColor(AppTheme.ytMusicTextSecondary)
                                    }
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 4)
                                }
                            }
                        }
                    }

                    // Section 2: Albums & Mixtapes (Curated + User releases)
                    if !albumsList.isEmpty {
                        VStack(alignment: .leading, spacing: 12) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("POPULAR RELEASES")
                                    .font(.system(size: 11, weight: .bold))
                                    .tracking(0.8)
                                    .foregroundColor(Color(hex: 0x909090))
                                Text("Albums & Mixtapes")
                                    .font(.system(size: 20, weight: .bold))
                                    .foregroundColor(.white)
                            }
                            .padding(.horizontal, 14)

                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 14) {
                                    ForEach(albumsList) { album in
                                        Button(action: {
                                            let playlist = AlbumDataRepository.shared.toUserPlaylist(album: album)
                                            onPlaylistClick?(playlist)
                                        }) {
                                            VStack(alignment: .leading, spacing: 6) {
                                                AsyncImage(url: URL(string: album.artworkUrl)) { image in
                                                    image.resizable().aspectRatio(contentMode: .fill)
                                                } placeholder: {
                                                    Rectangle().fill(Color(hex: 0x1E1E24))
                                                }
                                                .frame(width: 140, height: 140)
                                                .cornerRadius(10)
                                                .clipped()
                                                .overlay(
                                                    RoundedRectangle(cornerRadius: 10)
                                                        .stroke(Color.white.opacity(0.08), lineWidth: 1)
                                                )

                                                Text(album.title)
                                                    .font(.system(size: 13, weight: .semibold))
                                                    .foregroundColor(.white)
                                                    .lineLimit(1)
                                                    .frame(width: 140, alignment: .leading)

                                                Text("\(album.artist) • \(album.year)")
                                                    .font(.system(size: 11))
                                                    .foregroundColor(AppTheme.ytMusicTextSecondary)
                                                    .lineLimit(1)
                                                    .frame(width: 140, alignment: .leading)
                                            }
                                        }
                                    }
                                }
                                .padding(.horizontal, 14)
                            }
                        }
                    }

                    // Section 3: Daily Mixes (Mix 1 to Mix 5)
                    if !smartFeed.dailyMixes.isEmpty {
                        VStack(alignment: .leading, spacing: 12) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("YOUR PERSONAL SOUNDTRACK")
                                    .font(.system(size: 11, weight: .bold))
                                    .tracking(0.8)
                                    .foregroundColor(Color(hex: 0x909090))
                                Text("Daily Mixes")
                                    .font(.system(size: 20, weight: .bold))
                                    .foregroundColor(.white)
                            }
                            .padding(.horizontal, 14)

                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 14) {
                                    ForEach(smartFeed.dailyMixes) { mix in
                                        Button(action: {
                                            let pl = UserPlaylist(
                                                id: mix.id,
                                                title: mix.title,
                                                description: mix.description,
                                                videos: mix.tracks,
                                                isSystem: false,
                                                customCoverUrl: mix.coverUrl
                                            )
                                            onPlaylistClick?(pl)
                                        }) {
                                            VStack(alignment: .leading, spacing: 6) {
                                                if let cover = mix.coverUrl, !cover.isEmpty {
                                                    AsyncImage(url: URL(string: cover)) { img in
                                                        img.resizable().aspectRatio(contentMode: .fill)
                                                    } placeholder: {
                                                        Rectangle().fill(Color(hex: 0x1E1E24))
                                                    }
                                                    .frame(width: 140, height: 140)
                                                    .cornerRadius(10)
                                                    .clipped()
                                                } else {
                                                    ZStack {
                                                        RoundedRectangle(cornerRadius: 10)
                                                            .fill(LinearGradient(
                                                                colors: [Color(hex: 0x6D28D9), Color(hex: 0x311042)],
                                                                startPoint: .topLeading,
                                                                endPoint: .bottomTrailing
                                                            ))
                                                        Image(systemName: "music.note.list")
                                                            .font(.system(size: 32))
                                                            .foregroundColor(.white)
                                                    }
                                                    .frame(width: 140, height: 140)
                                                }

                                                Text(mix.title)
                                                    .font(.system(size: 13, weight: .bold))
                                                    .foregroundColor(.white)
                                                    .lineLimit(1)
                                                    .frame(width: 140, alignment: .leading)

                                                Text(mix.subtitle)
                                                    .font(.system(size: 11))
                                                    .foregroundColor(AppTheme.ytMusicTextSecondary)
                                                    .lineLimit(2)
                                                    .frame(width: 140, alignment: .leading)
                                            }
                                        }
                                    }
                                }
                                .padding(.horizontal, 14)
                            }
                        }
                    }

                    // Section 4: Because You Listened To...
                    if let because = smartFeed.becauseYouListenedTo, !because.tracks.isEmpty {
                        VStack(alignment: .leading, spacing: 12) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("SIMILAR TO YOUR TASTE")
                                    .font(.system(size: 11, weight: .bold))
                                    .tracking(0.8)
                                    .foregroundColor(Color(hex: 0x909090))
                                Text("Because you listened to \(because.artist)")
                                    .font(.system(size: 20, weight: .bold))
                                    .foregroundColor(.white)
                            }
                            .padding(.horizontal, 14)

                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 12) {
                                    ForEach(because.tracks) { track in
                                        Button(action: { onTrackSelected(track, because.tracks) }) {
                                            VStack(alignment: .leading, spacing: 6) {
                                                AsyncImage(url: URL(string: track.thumbnailUrl)) { image in
                                                    image.resizable().aspectRatio(contentMode: .fill)
                                                } placeholder: {
                                                    Rectangle().fill(AppTheme.ytMusicCardBg)
                                                }
                                                .frame(width: 130, height: 130)
                                                .cornerRadius(10)
                                                .clipped()

                                                Text(track.title)
                                                    .font(.system(size: 13, weight: .medium))
                                                    .foregroundColor(.white)
                                                    .lineLimit(1)
                                                    .frame(width: 130, alignment: .leading)

                                                Text(track.artist.isEmpty ? track.channelName : track.artist)
                                                    .font(.system(size: 11))
                                                    .foregroundColor(AppTheme.ytMusicTextSecondary)
                                                    .lineLimit(1)
                                                    .frame(width: 130, alignment: .leading)
                                            }
                                        }
                                    }
                                }
                                .padding(.horizontal, 14)
                            }
                        }
                    }

                    // Section 5: Listen Again
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Listen again")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 14)

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(listenAgain) { track in
                                    Button(action: { onTrackSelected(track, allMusicTracks) }) {
                                        VStack(alignment: .leading, spacing: 6) {
                                            AsyncImage(url: URL(string: track.thumbnailUrl)) { image in
                                                image.resizable().aspectRatio(contentMode: .fill)
                                            } placeholder: {
                                                Rectangle().fill(AppTheme.ytMusicCardBg)
                                            }
                                            .frame(width: 130, height: 130)
                                            .cornerRadius(10)
                                            .clipped()

                                            Text(track.title)
                                                .font(.system(size: 13, weight: .medium))
                                                .foregroundColor(.white)
                                                .lineLimit(1)
                                                .frame(width: 130, alignment: .leading)

                                            Text(track.artist.isEmpty ? track.channelName : track.artist)
                                                .font(.system(size: 11))
                                                .foregroundColor(AppTheme.ytMusicTextSecondary)
                                                .lineLimit(1)
                                                .frame(width: 130, alignment: .leading)
                                        }
                                    }
                                }
                            }
                            .padding(.horizontal, 14)
                        }
                    }

                    // Section 6: Hidden Gems
                    if !smartFeed.hiddenGems.isEmpty {
                        VStack(alignment: .leading, spacing: 12) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("DEEP CUTS")
                                    .font(.system(size: 11, weight: .bold))
                                    .tracking(0.8)
                                    .foregroundColor(Color(hex: 0x909090))
                                Text("Hidden Gems")
                                    .font(.system(size: 20, weight: .bold))
                                    .foregroundColor(.white)
                            }
                            .padding(.horizontal, 14)

                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 12) {
                                    ForEach(smartFeed.hiddenGems) { track in
                                        Button(action: { onTrackSelected(track, smartFeed.hiddenGems) }) {
                                            VStack(alignment: .leading, spacing: 6) {
                                                AsyncImage(url: URL(string: track.thumbnailUrl)) { image in
                                                    image.resizable().aspectRatio(contentMode: .fill)
                                                } placeholder: {
                                                    Rectangle().fill(AppTheme.ytMusicCardBg)
                                                }
                                                .frame(width: 130, height: 130)
                                                .cornerRadius(10)
                                                .clipped()

                                                Text(track.title)
                                                    .font(.system(size: 13, weight: .medium))
                                                    .foregroundColor(.white)
                                                    .lineLimit(1)
                                                    .frame(width: 130, alignment: .leading)

                                                Text(track.artist.isEmpty ? track.channelName : track.artist)
                                                    .font(.system(size: 11))
                                                    .foregroundColor(AppTheme.ytMusicTextSecondary)
                                                    .lineLimit(1)
                                                    .frame(width: 130, alignment: .leading)
                                            }
                                        }
                                    }
                                }
                                .padding(.horizontal, 14)
                            }
                        }
                    }

                    // Section 7: Trending & Charts
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Trending & Charts")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 14)

                        LazyVStack(spacing: 10) {
                            ForEach(Array(syncEngine.musicFeed.trendingCharts.prefix(8).enumerated()), id: \.element.id) { idx, track in
                                Button(action: { onTrackSelected(track, allMusicTracks) }) {
                                    HStack(spacing: 12) {
                                        Text("\(idx + 1)")
                                            .font(.system(size: 15, weight: .bold))
                                            .foregroundColor(AppTheme.ytMusicTextSecondary)
                                            .frame(width: 24)

                                        AsyncImage(url: URL(string: track.thumbnailUrl)) { image in
                                            image.resizable().aspectRatio(contentMode: .fill)
                                        } placeholder: {
                                            Rectangle().fill(AppTheme.ytMusicCardBg)
                                        }
                                        .frame(width: 48, height: 48)
                                        .cornerRadius(6)
                                        .clipped()

                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(track.title)
                                                .font(.system(size: 14, weight: .medium))
                                                .foregroundColor(.white)
                                                .lineLimit(1)
                                            Text(track.artist.isEmpty ? track.channelName : track.artist)
                                                .font(.system(size: 12))
                                                .foregroundColor(AppTheme.ytMusicTextSecondary)
                                                .lineLimit(1)
                                        }

                                        Spacer()
                                    }
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 4)
                                }
                            }
                        }
                    }
                    .padding(.bottom, 120)
                }
                .padding(.top, 8)
            }
            .refreshable {
                _ = await syncEngine.fetchMusicHomeFeed(mood: selectedMood.isEmpty ? nil : selectedMood)
                _ = await recEngine.refreshHomeFeed(selectedMood: selectedMood.isEmpty ? nil : selectedMood, forceRefresh: true)
            }
            .onChange(of: scrollToTopTrigger) { _ in
                withAnimation {
                    proxy.scrollTo("MUSIC_TOP", anchor: .top)
                }
            }
        }
        .task(id: selectedMood) {
            isRefreshing = true
            _ = await syncEngine.fetchMusicHomeFeed(mood: selectedMood.isEmpty ? nil : selectedMood)
            _ = await recEngine.refreshHomeFeed(selectedMood: selectedMood.isEmpty ? nil : selectedMood)
            isRefreshing = false
        }
    }
}
