import SwiftUI

enum MusicSheetTab: String, CaseIterable {
    case upNext = "UP NEXT"
    case lyrics = "LYRICS"
    case related = "RELATED"
}

struct MusicPlayerView: View {
    let track: VideoItem
    let isPlaying: Bool
    let onPlayPauseToggle: () -> Void
    let onCollapse: () -> Void
    let onNextTrack: () -> Void
    let onPrevTrack: () -> Void
    let onTrackSelected: (VideoItem) -> Void
    let isBackgroundPlay: Bool
    let onToggleBackgroundPlay: () -> Void
    let onSwitchToVideoMode: () -> Void

    @ObservedObject var playbackService = MediaPlaybackService.shared
    @ObservedObject var userData = UserDataRepository.shared

    @State private var activeTab: MusicSheetTab = .upNext
    @State private var isSheetExpanded = false
    @State private var isUserScrubbing = false
    @State private var scrubPosition: Double = 0
    @State private var showSettingsSheet = false
    @State private var dynamicRelated: [VideoItem] = []
    @State private var isLoadingRelated = false
    @State private var wavePhase = false

    private var queueTracks: [VideoItem] {
        let queue = playbackService.playbackQueue
        if queue.count > 1 {
            if let idx = queue.firstIndex(where: { $0.id == track.id }) {
                let upcoming = Array(queue.dropFirst(idx + 1))
                let previous = Array(queue.prefix(idx))
                return upcoming + previous
            }
            return queue.filter { $0.id != track.id }
        } else if !dynamicRelated.isEmpty {
            return dynamicRelated
        } else {
            return VideoRepository.getMusicTracks().filter { $0.id != track.id }
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            // Top Bar: Glass Collapse | Liquid Glass "Video" Pill | Glass Options
            ZStack {
                HStack {
                    Button(action: onCollapse) {
                        Image(systemName: "chevron.down")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(.white)
                            .frame(width: 38, height: 38)
                            .background(Color.white.opacity(0.12))
                            .clipShape(Circle())
                            .overlay(Circle().strokeBorder(LiquidGlassDefaults.borderSpecular, lineWidth: 1))
                            .shadow(color: Color.black.opacity(0.45), radius: 6, x: 0, y: 3)
                    }

                    Spacer()

                    Button(action: { showSettingsSheet = true }) {
                        Image(systemName: "ellipsis")
                            .rotationEffect(.degrees(90))
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.white)
                            .frame(width: 38, height: 38)
                            .background(Color.white.opacity(0.12))
                            .clipShape(Circle())
                            .overlay(Circle().strokeBorder(LiquidGlassDefaults.borderSpecular, lineWidth: 1))
                            .shadow(color: Color.black.opacity(0.45), radius: 6, x: 0, y: 3)
                    }
                }

                // Center Video Switcher Pill
                Button(action: onSwitchToVideoMode) {
                    HStack(spacing: 6) {
                        Image(systemName: "play.fill")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(AppTheme.youTubeRed)
                        Text("Video")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.white)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 7)
                    .background(
                        LinearGradient(
                            colors: [Color.white.opacity(0.16), Color.white.opacity(0.05)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 20))
                    .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(LiquidGlassDefaults.borderSpecular, lineWidth: 1))
                    .shadow(color: Color.black.opacity(0.45), radius: 6, x: 0, y: 3)
                }
            }
            .padding(.horizontal, 14)
            .padding(.top, 8)

            if !isSheetExpanded {
                Spacer(minLength: 10)

                VStack(spacing: 16) {
                    // Square Album Artwork with Red Glow Shadow (Matches Android MusicPlayerScreen)
                    AsyncImage(url: URL(string: track.thumbnailUrl)) { image in
                        image.resizable().aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Rectangle().fill(AppTheme.ytMusicCardBg)
                    }
                    .frame(width: min(UIScreen.main.bounds.width * 0.84, 320), height: min(UIScreen.main.bounds.width * 0.84, 320))
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .shadow(color: Color(hex: 0xFF1744, alpha: 0.40), radius: 22, x: 0, y: 10)

                    // Audio Visualizer Bar
                    if isPlaying {
                        HStack(spacing: 4) {
                            let activeHeights: [CGFloat] = [16, 8, 18, 10, 15]
                            let restHeights: [CGFloat] = [6, 17, 7, 16, 8]
                            ForEach(0..<5, id: \.self) { idx in
                                RoundedRectangle(cornerRadius: 2)
                                    .fill(AppTheme.ytMusicRedAccent)
                                    .frame(width: 3.5, height: wavePhase ? activeHeights[idx] : restHeights[idx])
                            }
                            Text("High Quality Audio")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(Color(hex: 0xAAAAAA))
                                .padding(.leading, 6)
                        }
                        .frame(height: 18)
                        .animation(.easeInOut(duration: 0.45).repeatForever(autoreverses: true), value: wavePhase)
                    }
                }

                Spacer(minLength: 8)
            }

            // Track Info & Like / Dislike
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(track.title)
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(.white)
                        .lineLimit(1)
                    Text(track.artist.isEmpty ? track.channelName : track.artist)
                        .font(.system(size: 14))
                        .foregroundColor(Color(hex: 0xAAAAAA))
                        .lineLimit(1)
                }

                Spacer()

                Button(action: { userData.toggleDislike(track.id) }) {
                    Image(systemName: userData.isDisliked(track.id) ? "hand.thumbsdown.fill" : "hand.thumbsdown")
                        .font(.system(size: 20))
                        .foregroundColor(userData.isDisliked(track.id) ? AppTheme.ytMusicRedAccent : .white)
                        .padding(8)
                }

                Button(action: { userData.toggleLike(track) }) {
                    Image(systemName: userData.isLiked(track.id) ? "hand.thumbsup.fill" : "hand.thumbsup")
                        .font(.system(size: 20))
                        .foregroundColor(userData.isLiked(track.id) ? AppTheme.ytMusicRedAccent : .white)
                        .padding(8)
                }
            }
            .padding(.horizontal, 24)

            // Red YTMusic Slider
            let maxDur = max(playbackService.durationSeconds, 1.0)
            let displayTime = isUserScrubbing ? scrubPosition : playbackService.currentTimeSeconds

            VStack(spacing: 2) {
                Slider(
                    value: Binding(
                        get: { min(displayTime, maxDur) },
                        set: { newVal in
                            isUserScrubbing = true
                            scrubPosition = newVal
                        }
                    ),
                    in: 0...maxDur,
                    onEditingChanged: { editing in
                        if !editing {
                            playbackService.seekTo(seconds: scrubPosition)
                            isUserScrubbing = false
                        }
                    }
                )
                .tint(AppTheme.ytMusicRedAccent)

                HStack {
                    Text(formatTime(displayTime))
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(Color(hex: 0xAAAAAA))
                    Spacer()
                    Text(playbackService.durationSeconds > 0 ? formatTime(maxDur) : track.duration)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(Color(hex: 0xAAAAAA))
                }
            }
            .padding(.horizontal, 24)
            .padding(.top, 8)

            // Transport Controls
            HStack {
                Button(action: { playbackService.toggleShuffle() }) {
                    Image(systemName: "shuffle")
                        .font(.system(size: 20))
                        .foregroundColor(playbackService.isShuffled ? AppTheme.ytMusicRedAccent : Color(hex: 0xAAAAAA))
                        .frame(width: 42, height: 42)
                }

                Spacer()

                Button(action: onPrevTrack) {
                    Image(systemName: "backward.end.fill")
                        .font(.system(size: 28))
                        .foregroundColor(.white)
                        .frame(width: 48, height: 48)
                }

                Spacer()

                Button(action: onPlayPauseToggle) {
                    ZStack {
                        Circle()
                            .fill(Color.white)
                            .frame(width: 68, height: 68)
                        Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                            .font(.system(size: 30))
                            .foregroundColor(.black)
                    }
                }

                Spacer()

                Button(action: onNextTrack) {
                    Image(systemName: "forward.end.fill")
                        .font(.system(size: 28))
                        .foregroundColor(.white)
                        .frame(width: 48, height: 48)
                }

                Spacer()

                Button(action: { playbackService.toggleRepeat() }) {
                    Image(systemName: playbackService.repeatMode == .one ? "repeat.1" : "repeat")
                        .font(.system(size: 20))
                        .foregroundColor(playbackService.repeatMode != .off ? AppTheme.ytMusicRedAccent : Color(hex: 0xAAAAAA))
                        .frame(width: 42, height: 42)
                }
            }
            .padding(.horizontal, 22)
            .padding(.vertical, 10)

            // Liquid Glass Bottom Dock: [ UP NEXT | LYRICS | RELATED ]
            VStack(spacing: 0) {
                Capsule()
                    .fill(Color.white.opacity(0.35))
                    .frame(width: 38, height: 4)
                    .padding(.top, 8)
                    .padding(.bottom, 6)

                HStack {
                    HStack(spacing: 6) {
                        ForEach(MusicSheetTab.allCases, id: \.self) { tab in
                            let isActive = activeTab == tab
                            Button(action: {
                                activeTab = tab
                                if !isSheetExpanded {
                                    withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
                                        isSheetExpanded = true
                                    }
                                }
                            }) {
                                Text(tab.rawValue)
                                    .font(.system(size: 11, weight: isActive ? .bold : .medium))
                                    .foregroundColor(isActive ? .white : Color(hex: 0x9E9EAF))
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 6)
                                    .background(isActive ? Color.white.opacity(0.16) : Color.white.opacity(0.04))
                                    .clipShape(RoundedRectangle(cornerRadius: 16))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 16)
                                            .strokeBorder(isActive ? LiquidGlassDefaults.borderSpecular : LiquidGlassDefaults.borderSubtle, lineWidth: 1)
                                    )
                            }
                        }
                    }

                    Spacer()

                    Button(action: {
                        withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
                            isSheetExpanded.toggle()
                        }
                    }) {
                        Image(systemName: isSheetExpanded ? "chevron.down" : "chevron.up")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(Color(hex: 0xAAAAAA))
                            .padding(6)
                    }
                }
                .padding(.horizontal, 14)
                .padding(.bottom, 6)

                Divider().background(Color(hex: 0x333333))

                ScrollView {
                    switch activeTab {
                    case .upNext:
                        LazyVStack(spacing: 8) {
                            if isLoadingRelated && queueTracks.isEmpty {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                    .padding(24)
                            } else {
                                ForEach(queueTracks) { item in
                                    Button(action: { onTrackSelected(item) }) {
                                        HStack(spacing: 12) {
                                            AsyncImage(url: URL(string: item.thumbnailUrl)) { img in
                                                img.resizable().aspectRatio(contentMode: .fill)
                                            } placeholder: {
                                                Rectangle().fill(AppTheme.ytMusicCardBg)
                                            }
                                            .frame(width: 40, height: 40)
                                            .clipShape(RoundedRectangle(cornerRadius: 6))

                                            VStack(alignment: .leading, spacing: 2) {
                                                Text(item.title)
                                                    .font(.system(size: 13, weight: .medium))
                                                    .foregroundColor(.white)
                                                    .lineLimit(1)
                                                Text(item.artist.isEmpty ? item.channelName : item.artist)
                                                    .font(.system(size: 11))
                                                    .foregroundColor(Color(hex: 0xAAAAAA))
                                                    .lineLimit(1)
                                            }
                                            Spacer()
                                            Text(item.duration)
                                                .font(.system(size: 12))
                                                .foregroundColor(Color(hex: 0x888888))
                                        }
                                        .padding(.horizontal, 14)
                                        .padding(.vertical, 5)
                                    }
                                }
                            }
                        }
                        .padding(.vertical, 6)

                    case .lyrics:
                        Text(track.lyrics.isEmpty ? "Lyrics are not currently available for this track." : track.lyrics)
                            .font(.system(size: 15))
                            .foregroundColor(Color(hex: 0xEEEEEE))
                            .lineSpacing(6)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(16)

                    case .related:
                        VStack(alignment: .leading, spacing: 6) {
                            Text("About the Artist")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.white)
                            Text("\(track.artist.isEmpty ? track.channelName : track.artist) has over \(track.views) across YouTube and YouTube Music. Album: \(track.album.isEmpty ? "Single" : track.album).")
                                .font(.system(size: 12))
                                .foregroundColor(Color(hex: 0xCCCCCC))
                                .lineSpacing(4)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16)
                    }
                }
            }
            .frame(height: isSheetExpanded ? 360 : 175)
            .background(Color(hex: 0x161726, alpha: 0.95))
            .clipShape(RoundedCornerShape(radius: 24, corners: [.topLeft, .topRight]))
            .overlay(
                RoundedCornerShape(radius: 24, corners: [.topLeft, .topRight])
                    .stroke(LiquidGlassDefaults.borderSpecular, lineWidth: 1)
            )
        }
        .background(
            LinearGradient(
                colors: [Color(hex: 0x2B0C16), Color(hex: 0x14060B), Color(hex: 0x070707)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()
        )
        .onAppear { wavePhase = true }
        .sheet(isPresented: $showSettingsSheet) {
            PlayerBottomSheet(appMode: .youtubeMusic, onDismiss: { showSettingsSheet = false })
                .presentationDetents([.medium, .large])
        }
        .task(id: track.id) {
            if playbackService.playbackQueue.count <= 1 {
                isLoadingRelated = true
                let results = await YouTubeSearchEngine.getRelatedVideos(video: track, isMusicOnly: true)
                if !results.isEmpty {
                    dynamicRelated = results
                    playbackService.setQueue([track] + results)
                }
                isLoadingRelated = false
            }
        }
    }

    private func formatTime(_ seconds: Double) -> String {
        let total = max(0, Int(seconds))
        let m = total / 60
        let s = total % 60
        return String(format: "%d:%02d", m, s)
    }
}

private extension View {
    func cornerRadius(_ radius: CGFloat, corners: UIRectCorner) -> some View {
        clipShape(RoundedCornerShape(radius: radius, corners: corners))
    }
}

private struct RoundedCornerShape: Shape {
    var radius: CGFloat = .infinity
    var corners: UIRectCorner = .allCorners

    func path(in rect: CGRect) -> Path {
        let path = UIBezierPath(
            roundedRect: rect,
            byRoundingCorners: corners,
            cornerRadii: CGSize(width: radius, height: radius)
        )
        return Path(path.cgPath)
    }
}
