import SwiftUI

struct MainView: View {
    @State private var appMode: AppMode = .youtube
    @State private var selectedYTTab: YouTubeTab = .home
    @State private var selectedMusicTab: MusicTab = .home
    @State private var homeScrollToTopTrigger: Int = 0

    @State private var isFullscreen: Bool = false
    @State private var isPlayerExpanded: Bool = false
    @State private var isSearchActive: Bool = false
    @State private var isSupportActive: Bool = false
    @State private var showAccountSheet: Bool = false
    @State private var showGoogleAuthSheet: Bool = false

    @State private var activeChannelVideo: VideoItem? = nil
    @State private var activePlaylist: UserPlaylist? = nil

    @ObservedObject var playbackService = MediaPlaybackService.shared

    var body: some View {
        let isMusic = appMode == .youtubeMusic
        let showVideoInYT = isPlayerExpanded && appMode == .youtube

        ZStack(alignment: .top) {
            // Liquid Glass Background
            (isMusic ? LiquidGlassDefaults.liquidMusicCanvas : LiquidGlassDefaults.liquidCanvas)
                .ignoresSafeArea()

            // Main Persistent Shell
            VStack(spacing: 0) {
                if !isSearchActive && !isSupportActive && activeChannelVideo == nil && activePlaylist == nil {
                    if (appMode == .youtube && selectedYTTab != .you) ||
                       (appMode == .youtubeMusic && selectedMusicTab != .library) {
                        YouTubeTopBar(
                            appMode: appMode,
                            onModeChanged: { newMode in
                                appMode = newMode
                            },
                            onTitleClick: {
                                let alreadyHome = appMode == .youtube ? (selectedYTTab == .home) : (selectedMusicTab == .home)
                                if alreadyHome {
                                    homeScrollToTopTrigger += 1
                                } else {
                                    if appMode == .youtube { selectedYTTab = .home }
                                    else { selectedMusicTab = .home }
                                }
                            },
                            onSearchClick: { isSearchActive = true },
                            isBackgroundPlay: playbackService.isBackgroundPlayEnabled,
                            onToggleBackgroundPlay: { playbackService.toggleBackgroundPlay() },
                            onProfileClick: {
                                if appMode == .youtube {
                                    selectedYTTab = .you
                                } else {
                                    showAccountSheet = true
                                }
                            }
                        )
                    }
                }

                // Content Area
                ZStack {
                    if isSearchActive {
                        SearchScreenView(
                            appMode: appMode,
                            onBack: { isSearchActive = false },
                            onVideoSelected: { video, queue in
                                playbackService.playTrack(video, queue: queue)
                                isPlayerExpanded = true
                                isSearchActive = false
                            },
                            onChannelClick: { video in
                                activeChannelVideo = video
                                isSearchActive = false
                            }
                        )
                    } else if isSupportActive {
                        SupportView()
                    } else if let pl = activePlaylist {
                        PlaylistDetailView(
                            playlistId: pl.id,
                            initialPlaylist: pl,
                            appMode: appMode,
                            onBack: { activePlaylist = nil },
                            onVideoSelected: { video, queue in
                                playbackService.playTrack(video, queue: queue)
                                isPlayerExpanded = true
                            }
                        )
                    } else if let chVid = activeChannelVideo {
                        ChannelView(
                            channelName: chVid.channelName,
                            channelAvatarUrl: chVid.channelAvatarUrl,
                            subscriberCount: chVid.subscriberCount.isEmpty ? "10M subscribers" : chVid.subscriberCount,
                            onBack: { activeChannelVideo = nil },
                            onVideoSelected: { video, queue in
                                playbackService.playTrack(video, queue: queue)
                                isPlayerExpanded = true
                            },
                            onPlaylistClick: { pl in
                                activePlaylist = pl
                            }
                        )
                    } else {
                        if appMode == .youtube {
                            if selectedYTTab == .you {
                                YouView(
                                    onVideoSelected: { video, queue in
                                        playbackService.playTrack(video, queue: queue)
                                        isPlayerExpanded = true
                                    },
                                    onPlaylistClick: { pl in
                                        activePlaylist = pl
                                    },
                                    onOpenSearch: { isSearchActive = true },
                                    onOpenAccountSheet: { showAccountSheet = true },
                                    onSignInClick: { showGoogleAuthSheet = true }
                                )
                            } else {
                                YouTubeFeedView(
                                    onVideoSelected: { video, queue in
                                        playbackService.playTrack(video, queue: queue)
                                        isPlayerExpanded = true
                                    },
                                    onSwitchToMusic: {
                                        appMode = .youtubeMusic
                                    },
                                    onChannelClick: { video in
                                        activeChannelVideo = video
                                    },
                                    scrollToTopTrigger: homeScrollToTopTrigger
                                )
                            }
                        } else {
                            if selectedMusicTab == .library {
                                MusicLibraryView(
                                    onTrackSelected: { track, queue in
                                        playbackService.playTrack(track, queue: queue)
                                        isPlayerExpanded = true
                                    },
                                    onPlaylistClick: { pl in
                                        activePlaylist = pl
                                    },
                                    onOpenSearch: { isSearchActive = true },
                                    onOpenAccountSheet: { showAccountSheet = true }
                                )
                            } else {
                                YouTubeMusicHomeView(
                                    onTrackSelected: { track, queue in
                                        playbackService.playTrack(track, queue: queue)
                                        isPlayerExpanded = true
                                    },
                                    scrollToTopTrigger: homeScrollToTopTrigger
                                )
                            }
                        }
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }

                // Floating Mini Player Bar & Liquid Glass Bottom Nav Docked at Bottom
                if !isPlayerExpanded {
                    VStack(spacing: 0) {
                        Spacer()
                        if playbackService.currentTrack != nil {
                            MiniPlayer(appMode: appMode) {
                                isPlayerExpanded = true
                            }
                        }

                        let isHomeSelected = !isSearchActive && !isSupportActive && activeChannelVideo == nil && activePlaylist == nil && (appMode == .youtube ? selectedYTTab == .home : selectedMusicTab == .home)
                        let isAccountSelected = !isSearchActive && !isSupportActive && activeChannelVideo == nil && activePlaylist == nil && (appMode == .youtube ? selectedYTTab == .you : selectedMusicTab == .library)

                        LiquidBottomNavBar(
                            isHomeSelected: isHomeSelected,
                            isSearchSelected: isSearchActive,
                            isAccountSelected: isAccountSelected,
                            isSupportSelected: !isSearchActive && isSupportActive,
                            onHomeClick: {
                                let alreadyOnHome = isHomeSelected
                                activeChannelVideo = nil
                                activePlaylist = nil
                                isSearchActive = false
                                isSupportActive = false
                                if alreadyOnHome {
                                    homeScrollToTopTrigger += 1
                                }
                                if appMode == .youtube { selectedYTTab = .home }
                                else { selectedMusicTab = .home }
                            },
                            onSearchClick: {
                                activeChannelVideo = nil
                                activePlaylist = nil
                                isSupportActive = false
                                isSearchActive = true
                            },
                            onAccountClick: {
                                activeChannelVideo = nil
                                activePlaylist = nil
                                isSearchActive = false
                                isSupportActive = false
                                if appMode == .youtube {
                                    if selectedYTTab == .you { showAccountSheet = true }
                                    else { selectedYTTab = .you }
                                } else {
                                    if selectedMusicTab == .library { showAccountSheet = true }
                                    else { selectedMusicTab = .library }
                                }
                            },
                            onSupportClick: {
                                activeChannelVideo = nil
                                activePlaylist = nil
                                isSearchActive = false
                                isSupportActive = true
                            }
                        )
                    }
                    .zIndex(5)
                }

            // Expanded Detail / Music Player Overlay
            if isPlayerExpanded, let video = playbackService.currentTrack {
                Group {
                    if appMode == .youtube {
                        VideoDetailView(
                            video: video,
                            isPlaying: playbackService.isPlaying,
                            onPlayPauseToggle: { playbackService.togglePlayPause() },
                            onCollapse: { isPlayerExpanded = false },
                            onSwitchToMusicMode: { appMode = .youtubeMusic },
                            onVideoSelected: { newVideo in
                                let q = playbackService.playbackQueue.isEmpty ? [video] : playbackService.playbackQueue
                                playbackService.playTrack(newVideo, queue: q)
                            },
                            onChannelClick: { chVideo in
                                activeChannelVideo = chVideo
                                isPlayerExpanded = false
                            },
                            isBackgroundPlay: playbackService.isBackgroundPlayEnabled,
                            onToggleBackgroundPlay: { playbackService.toggleBackgroundPlay() }
                        )
                    } else {
                        MusicPlayerView(
                            track: video,
                            isPlaying: playbackService.isPlaying,
                            onPlayPauseToggle: { playbackService.togglePlayPause() },
                            onCollapse: { isPlayerExpanded = false },
                            onNextTrack: { playbackService.nextTrack() },
                            onPrevTrack: { playbackService.prevTrack() },
                            onTrackSelected: { newTrack in
                                let q = playbackService.playbackQueue.isEmpty ? [video] : playbackService.playbackQueue
                                playbackService.playTrack(newTrack, queue: q)
                            },
                            isBackgroundPlay: playbackService.isBackgroundPlayEnabled,
                            onToggleBackgroundPlay: { playbackService.toggleBackgroundPlay() },
                            onSwitchToVideoMode: { appMode = .youtube }
                        )
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .zIndex(10)
            }

            // Persistent YouTube Video Player (Stays alive across all screens & background!)
            if let track = playbackService.currentTrack {
                let playerWidth: CGFloat? = (isFullscreen || showVideoInYT) ? nil : 240
                let playerHeight: CGFloat? = (isFullscreen || showVideoInYT) ? nil : 135

                ZStack {
                    YouTubeVideoPlayer(
                        videoId: track.id,
                        isPlaying: playbackService.isPlaying
                    )
                    .background(Color.black)

                    if showVideoInYT || isFullscreen {
                        PlayerControlsOverlay(
                            isPlaying: playbackService.isPlaying,
                            currentTime: playbackService.currentTimeSeconds,
                            duration: playbackService.durationSeconds,
                            onPlayPauseToggle: { playbackService.togglePlayPause() },
                            onNext: { playbackService.nextTrack() },
                            onPrev: { playbackService.prevTrack() },
                            onSeekBy: { playbackService.seekBy(seconds: $0) },
                            onSeekTo: { playbackService.seekTo(seconds: $0) },
                            onCollapse: {
                                if isFullscreen {
                                    isFullscreen = false
                                } else {
                                    isPlayerExpanded = false
                                }
                            },
                            isFullscreen: isFullscreen,
                            onToggleFullscreen: { isFullscreen.toggle() }
                        )
                    }
                }
                .frame(width: playerWidth, height: playerHeight)
                .frame(
                    maxWidth: (isFullscreen || showVideoInYT) ? .infinity : nil,
                    maxHeight: isFullscreen ? .infinity : nil
                )
                .aspectRatio(showVideoInYT && !isFullscreen ? 16/9 : nil, contentMode: .fit)
                .opacity((showVideoInYT || isFullscreen) ? 1.0 : 0.03)
                .allowsHitTesting(showVideoInYT || isFullscreen)
                .ignoresSafeArea(edges: isFullscreen ? .all : [])
                .zIndex(isFullscreen ? 50 : (showVideoInYT ? 15 : -10))
            }
        }
        .statusBarHidden(isFullscreen)
        .onAppear {
            AccountRepository.shared.syncIfCookiesExist()
        }
        .onChange(of: isPlayerExpanded) { expanded in
            if !expanded && isFullscreen { isFullscreen = false }
        }
        .onChange(of: appMode) { mode in
            if mode != .youtube && isFullscreen { isFullscreen = false }
        }
        .onChange(of: isFullscreen) { full in
            updateOrientation(fullscreen: full)
        }
        .sheet(isPresented: $showAccountSheet) {
            AccountSheet(
                appMode: appMode,
                onDismiss: { showAccountSheet = false },
                onModeSwitch: {
                    appMode = appMode == .youtube ? .youtubeMusic : .youtube
                },
                onSignInClick: {
                    showGoogleAuthSheet = true
                }
            )
            .presentationDetents([.fraction(0.60), .large])
        }
        .fullScreenCover(isPresented: $showGoogleAuthSheet) {
            GoogleAuthSheet(
                onDismiss: { showGoogleAuthSheet = false },
                onLoginSuccess: { showGoogleAuthSheet = false }
            )
        }
    }

    private func updateOrientation(fullscreen: Bool) {
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene else { return }
        if #available(iOS 16.0, *) {
            let mask: UIInterfaceOrientationMask = fullscreen ? .landscapeRight : .portrait
            windowScene.requestGeometryUpdate(.iOS(interfaceOrientations: mask)) { _ in }
        }
    }
}
