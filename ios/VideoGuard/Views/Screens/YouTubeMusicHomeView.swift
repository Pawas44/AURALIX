import SwiftUI

struct YouTubeMusicHomeView: View {
    let onTrackSelected: (VideoItem, [VideoItem]) -> Void
    let scrollToTopTrigger: Int

    @ObservedObject var syncEngine = YouTubeSyncEngine.shared
    @ObservedObject var userData = UserDataRepository.shared

    @State private var selectedMood: String = ""
    @State private var isRefreshing = false

    private let moods = VideoRepository.musicCategories

    private var quickPicks: [VideoItem] {
        syncEngine.musicFeed.quickPicks.isEmpty ? VideoRepository.getMusicTracks() : syncEngine.musicFeed.quickPicks
    }

    private var listenAgain: [VideoItem] {
        syncEngine.musicFeed.listenAgain.isEmpty ? VideoRepository.getMusicTracks() : syncEngine.musicFeed.listenAgain
    }

    private var recommendedVideos: [VideoItem] {
        syncEngine.musicFeed.recommendedVideos.isEmpty ? quickPicks : syncEngine.musicFeed.recommendedVideos
    }

    private var trendingCharts: [VideoItem] {
        syncEngine.musicFeed.trendingCharts.isEmpty ? quickPicks : syncEngine.musicFeed.trendingCharts
    }

    private var allMusicTracks: [VideoItem] {
        YouTubeSyncEngine.distinctVideos(quickPicks + listenAgain + trendingCharts)
    }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    Color.clear.frame(height: 1).id("MUSIC_TOP")

                    // Mood Filter Chips
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(moods, id: \.self) { mood in
                                let isSelected = mood == selectedMood
                                Button(action: {
                                    selectedMood = isSelected ? "" : mood
                                }) {
                                    Text(mood)
                                        .font(.system(size: 13, weight: .medium))
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

                    // Listen Again
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

                    // Quick Picks
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("START RADIO FROM A SONG")
                                    .font(.system(size: 10, weight: .semibold))
                                    .foregroundColor(AppTheme.ytMusicTextSecondary)
                                Text("Quick picks")
                                    .font(.system(size: 20, weight: .bold))
                                    .foregroundColor(.white)
                            }
                            Spacer()
                        }
                        .padding(.horizontal, 14)

                        LazyVStack(spacing: 10) {
                            ForEach(quickPicks.prefix(10)) { track in
                                Button(action: { onTrackSelected(track, allMusicTracks) }) {
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
                                            Text("\(track.artist.isEmpty ? track.channelName : track.artist) • \(track.views)")
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

                    // Recommended Videos
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Recommended music videos")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 14)

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(recommendedVideos) { track in
                                    Button(action: { onTrackSelected(track, allMusicTracks) }) {
                                        VStack(alignment: .leading, spacing: 6) {
                                            AsyncImage(url: URL(string: track.thumbnailUrl)) { image in
                                                image.resizable().aspectRatio(16/9, contentMode: .fill)
                                            } placeholder: {
                                                Rectangle().fill(AppTheme.ytMusicCardBg)
                                            }
                                            .frame(width: 220, height: 124)
                                            .cornerRadius(10)
                                            .clipped()

                                            Text(track.title)
                                                .font(.system(size: 13, weight: .medium))
                                                .foregroundColor(.white)
                                                .lineLimit(2)
                                                .frame(width: 220, alignment: .leading)

                                            Text(track.artist.isEmpty ? track.channelName : track.artist)
                                                .font(.system(size: 11))
                                                .foregroundColor(AppTheme.ytMusicTextSecondary)
                                                .lineLimit(1)
                                        }
                                    }
                                }
                            }
                            .padding(.horizontal, 14)
                        }
                    }

                    // Trending Charts
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Trending & Charts")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 14)

                        LazyVStack(spacing: 10) {
                            ForEach(Array(trendingCharts.prefix(8).enumerated()), id: \.element.id) { idx, track in
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
                    .padding(.bottom, 80)
                }
                .padding(.top, 8)
            }
            .refreshable {
                _ = await syncEngine.fetchMusicHomeFeed(mood: selectedMood.isEmpty ? nil : selectedMood)
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
            isRefreshing = false
        }
    }
}
