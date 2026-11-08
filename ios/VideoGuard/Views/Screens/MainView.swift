import SwiftUI

enum ScreenDestination {
    case youtubeFeed
    case search
    case videoDetail
    case channel
    case you
    case musicHome
    case musicPlayer
    case musicLibrary
    case musicExplore
    case playlistDetail
}

struct MainView: View {
    @State private var appMode: AppMode = .youtube
    @State private var currentScreen: ScreenDestination = .youtubeFeed
    @State private var selectedYouTubeTab: YouTubeTab = .home
    @State private var selectedMusicTab: MusicTab = .home
    @State private var selectedVideo: VideoItem? = nil
    @State private var selectedChannel: String = ""
    @State private var selectedPlaylistId: String = ""
    @State private var showAccountSheet: Bool = false
    @State private var showPlayerSheet: Bool = false

    @ObservedObject var playbackService = MediaPlaybackService.shared

    var body: some View {
        let bgColor = appMode == .youtube ? AppTheme.youTubeDarkBg : AppTheme.ytMusicBlack

        ZStack(alignment: .bottom) {
            bgColor.ignoresSafeArea()

            VStack(spacing: 0) {
                // Main Active Screen Content
                ZStack {
                    switch currentScreen {
                    case .youtubeFeed:
                        YouTubeFeedView(
                            appMode: appMode,
                            onSearchClick: { currentScreen = .search },
                            onProfileClick: { showAccountSheet = true },
                            onVideoClick: { video in
                                selectedVideo = video
                                currentScreen = .videoDetail
                            }
                        )
                    case .search:
                        SearchScreenView(
                            appMode: appMode,
                            onBack: {
                                currentScreen = appMode == .youtube ? .youtubeFeed : .musicHome
                            },
                            onVideoClick: { video in
                                selectedVideo = video
                                currentScreen = appMode == .youtube ? .videoDetail : .musicPlayer
                            }
                        )
                    case .videoDetail:
                        if let video = selectedVideo {
                            VideoDetailView(
                                video: video,
                                onBack: { currentScreen = .youtubeFeed },
                                onChannelClick: { channel in
                                    selectedChannel = channel
                                    currentScreen = .channel
                                },
                                onVideoClick: { nextVideo in
                                    selectedVideo = nextVideo
                                }
                            )
                        }
                    case .channel:
                        ChannelView(
                            channelName: selectedChannel,
                            onBack: {
                                currentScreen = selectedVideo != nil ? .videoDetail : .youtubeFeed
                            },
                            onVideoClick: { video in
                                selectedVideo = video
                                currentScreen = .videoDetail
                            }
                        )
                    case .you:
                        YouView(
                            onBack: { currentScreen = .youtubeFeed },
                            onVideoClick: { video in
                                selectedVideo = video
                                currentScreen = .videoDetail
                            },
                            onPlaylistClick: { playlistId in
                                selectedPlaylistId = playlistId
                                currentScreen = .playlistDetail
                            }
                        )
                    case .musicHome:
                        YouTubeMusicHomeView(
                            onSearchClick: { currentScreen = .search },
                            onProfileClick: { showAccountSheet = true },
                            onTrackClick: { track in
                                selectedVideo = track
                                currentScreen = .musicPlayer
                            }
                        )
                    case .musicPlayer:
                        if let track = selectedVideo {
                            MusicPlayerView(
                                track: track,
                                onBack: { currentScreen = .musicHome },
                                onVideoClick: { nextTrack in
                                    selectedVideo = nextTrack
                                }
                            )
                        }
                    case .musicLibrary:
                        MusicLibraryView(
                            onBack: { currentScreen = .musicHome },
                            onTrackClick: { track in
                                selectedVideo = track
                                currentScreen = .musicPlayer
                            },
                            onPlaylistClick: { playlistId in
                                selectedPlaylistId = playlistId
                                currentScreen = .playlistDetail
                            }
                        )
                    case .musicExplore:
                        YouTubeMusicHomeView(
                            onSearchClick: { currentScreen = .search },
                            onProfileClick: { showAccountSheet = true },
                            onTrackClick: { track in
                                selectedVideo = track
                                currentScreen = .musicPlayer
                            }
                        )
                    case .playlistDetail:
                        PlaylistDetailView(
                            playlistId: selectedPlaylistId,
                            appMode: appMode,
                            onBack: {
                                currentScreen = appMode == .youtube ? .you : .musicLibrary
                            },
                            onVideoClick: { video in
                                selectedVideo = video
                                currentScreen = appMode == .youtube ? .videoDetail : .musicPlayer
                            }
                        )
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)

                // Mini Player Bar
                if playbackService.playbackState.currentTrack != nil &&
                    currentScreen != .musicPlayer &&
                    currentScreen != .videoDetail {
                    MiniPlayer(appMode: appMode) {
                        if appMode == .youtube {
                            showPlayerSheet = true
                        } else {
                            selectedVideo = playbackService.playbackState.currentTrack
                            currentScreen = .musicPlayer
                        }
                    }
                }

                // Bottom Tab Bar
                if currentScreen != .videoDetail &&
                    currentScreen != .musicPlayer &&
                    currentScreen != .channel &&
                    currentScreen != .search {
                    if appMode == .youtube {
                        YouTubeBottomNavBar(selectedTab: $selectedYouTubeTab)
                            .onChange(of: selectedYouTubeTab) { newTab in
                                switch newTab {
                                case .home: currentScreen = .youtubeFeed
                                case .you: currentScreen = .you
                                default: currentScreen = .youtubeFeed
                                }
                            }
                    } else {
                        MusicBottomNavBar(selectedTab: $selectedMusicTab)
                            .onChange(of: selectedMusicTab) { newTab in
                                switch newTab {
                                case .home: currentScreen = .musicHome
                                case .library: currentScreen = .musicLibrary
                                case .explore: currentScreen = .musicExplore
                                case .samples: currentScreen = .musicHome
                                }
                            }
                    }
                }
            }
        }
        .sheet(isPresented: $showAccountSheet) {
            AccountSheet(
                appMode: appMode,
                onDismiss: { showAccountSheet = false },
                onModeSwitch: {
                    appMode = appMode == .youtube ? .youtubeMusic : .youtube
                    currentScreen = appMode == .youtube ? .youtubeFeed : .musicHome
                }
            )
            .presentationDetents([.fraction(0.55), .large])
        }
        .sheet(isPresented: $showPlayerSheet) {
            PlayerBottomSheet(
                appMode: appMode,
                onDismiss: { showPlayerSheet = false }
            )
        }
    }
}

