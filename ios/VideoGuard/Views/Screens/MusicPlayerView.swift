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
    @State private var showAddToPlaylistSheet = false
    @State private var isDownloaded = false
    @State private var downloadToastText: String? = nil
    @State private var dynamicRelated: [VideoItem] = []
    @State private var isLoadingRelated = false
    @State private var wavePhase = false
    @State private var dragOffset: CGFloat = 0

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
        GeometryReader { geo in
            let safeTop = geo.safeAreaInsets.top
            let safeBottom = geo.safeAreaInsets.bottom
            let availableHeight = geo.size.height - safeTop - safeBottom
            // Dynamic artwork sizing so it fits comfortably on any iPhone (SE, 11, 14, 15 Pro Max)
            let artSize = min(geo.size.width * 0.74, max(180, (availableHeight - 330) * 0.88))

            ZStack(alignment: .bottom) {
                // Main Full-screen Player Body
                VStack(spacing: 0) {
                    // Pinned Top Bar (Guaranteed visible directly below notch/status bar)
                    topBar
                        .padding(.top, max(safeTop, 12))
                        .padding(.bottom, 4)

                    Spacer(minLength: 6)

                    // Square Artwork + High Quality Audio Visualizer
                    VStack(spacing: 12) {
                        AsyncImage(url: URL(string: track.thumbnailUrl)) { phase in
                            switch phase {
                            case .success(let image):
                                image
                                    .resizable()
                                    .aspectRatio(contentMode: .fill)
                            case .failure:
                                ZStack {
                                    Rectangle().fill(AppTheme.ytMusicCardBg)
                                    Image(systemName: "music.note")
                                        .font(.system(size: 40))
                                        .foregroundColor(.white.opacity(0.35))
                                }
                            case .empty:
                                Rectangle().fill(AppTheme.ytMusicCardBg)
                            @unknown default:
                                Rectangle().fill(AppTheme.ytMusicCardBg)
                            }
                        }
                        .frame(width: artSize, height: artSize)
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                .strokeBorder(Color.white.opacity(0.12), lineWidth: 1)
                        )
                        .shadow(color: Color(hex: 0xFF1744, alpha: 0.35), radius: 22, x: 0, y: 10)

                        // Animated Visualizer
                        if isPlaying {
                            HStack(spacing: 4) {
                                let activeHeights: [CGFloat] = [14, 7, 16, 9, 13]
                                let restHeights: [CGFloat] = [5, 14, 6, 13, 7]
                                ForEach(0..<5, id: \.self) { idx in
                                    RoundedRectangle(cornerRadius: 2)
                                        .fill(AppTheme.ytMusicRedAccent)
                                        .frame(width: 3, height: wavePhase ? activeHeights[idx] : restHeights[idx])
                                }
                                Text("High Quality Audio")
                                    .font(.system(size: 11, weight: .medium))
                                    .foregroundColor(Color(hex: 0xAAAAAA))
                                    .padding(.leading, 6)
                            }
                            .frame(height: 16)
                            .animation(.easeInOut(duration: 0.45).repeatForever(autoreverses: true), value: wavePhase)
                        } else {
                            Color.clear.frame(height: 16)
                        }
                    }

                    Spacer(minLength: 8)

                    // Title & Like / Dislike Section
                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(track.title)
                                .font(.system(size: 19, weight: .bold))
                                .foregroundColor(.white)
                                .lineLimit(1)

                            Text(track.artist.isEmpty ? track.channelName : track.artist)
                                .font(.system(size: 14))
                                .foregroundColor(Color(hex: 0xAAAAAA))
                                .lineLimit(1)
                        }

                        Spacer()

                        Button(action: { showAddToPlaylistSheet = true }) {
                            Image(systemName: "plus.circle")
                                .font(.system(size: 20))
                                .foregroundColor(.white)
                                .frame(width: 34, height: 34)
                        }

                        Button(action: {
                            isDownloaded = true
                            downloadToastText = "Downloading \"\(track.title)\" for offline playback..."
                        }) {
                            Image(systemName: isDownloaded ? "checkmark.circle.fill" : "arrow.down.circle")
                                .font(.system(size: 20))
                                .foregroundColor(isDownloaded ? AppTheme.ytMusicRedAccent : .white)
                                .frame(width: 34, height: 34)
                        }

                        Button(action: { userData.toggleDislike(track.id) }) {
                            Image(systemName: userData.isDisliked(track.id) ? "hand.thumbsdown.fill" : "hand.thumbsdown")
                                .font(.system(size: 20))
                                .foregroundColor(userData.isDisliked(track.id) ? AppTheme.ytMusicRedAccent : .white)
                                .frame(width: 34, height: 34)
                        }

                        Button(action: { userData.toggleLike(track) }) {
                            Image(systemName: userData.isLiked(track.id) ? "hand.thumbsup.fill" : "hand.thumbsup")
                                .font(.system(size: 20))
                                .foregroundColor(userData.isLiked(track.id) ? AppTheme.ytMusicRedAccent : .white)
                                .frame(width: 34, height: 34)
                        }
                    }
                    .padding(.horizontal, 22)

                    // Sleek YouTube Red Liquid Scrubber Bar
                    scrubberSection
                        .padding(.horizontal, 22)
                        .padding(.top, 10)

                    // Transport Controls
                    transportControlsSection
                        .padding(.horizontal, 20)
                        .padding(.top, 10)
                        .padding(.bottom, 12)

                    // Dock Clearance
                    Spacer().frame(height: 54 + max(safeBottom, 8))
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)

                // Liquid Glass Bottom Sheet Dock [ UP NEXT | LYRICS | RELATED ]
                bottomSheetDock(safeBottom: safeBottom, maxHeight: min(geo.size.height * 0.65, 480))
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(
                LinearGradient(
                    colors: [Color(hex: 0x240A13), Color(hex: 0x12050A), Color(hex: 0x060606)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea()
            )
            .offset(y: max(0, dragOffset))
            .gesture(
                DragGesture(minimumDistance: 15)
                    .onChanged { value in
                        if !isSheetExpanded && value.translation.height > 0 {
                            dragOffset = value.translation.height
                        }
                    }
                    .onEnded { value in
                        if !isSheetExpanded && (value.translation.height > 100 || value.predictedEndTranslation.height > 180) {
                            withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
                                onCollapse()
                            }
                        }
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                            dragOffset = 0
                        }
                    }
            )
        }
        .ignoresSafeArea()
        .onAppear { wavePhase = true }
        .sheet(isPresented: $showSettingsSheet) {
            PlayerBottomSheet(appMode: .youtubeMusic, onDismiss: { showSettingsSheet = false })
                .presentationDetents([.medium, .large])
        }
        .sheet(isPresented: $showAddToPlaylistSheet) {
            AddToPlaylistSheet(video: track) {
                showAddToPlaylistSheet = false
            }
        }
        .overlay(alignment: .top) {
            if let msg = downloadToastText {
                Text(msg)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(Color.black.opacity(0.85))
                    .cornerRadius(20)
                    .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.white.opacity(0.2), lineWidth: 1))
                    .padding(.top, 60)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .task {
                        try? await Task.sleep(nanoseconds: 2_500_000_000)
                        withAnimation { downloadToastText = nil }
                    }
            }
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

    // MARK: - Subviews

    private var topBar: some View {
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
                        .shadow(color: Color.black.opacity(0.35), radius: 6, x: 0, y: 3)
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
                        .shadow(color: Color.black.opacity(0.35), radius: 6, x: 0, y: 3)
                }
            }

            // Center Video Mode Switcher Pill
            Button(action: onSwitchToVideoMode) {
                HStack(spacing: 6) {
                    Image(systemName: "play.fill")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(AppTheme.youTubeRed)
                    Text("Video")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.white)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .background(
                    LinearGradient(
                        colors: [Color.white.opacity(0.18), Color.white.opacity(0.06)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .clipShape(Capsule())
                .overlay(Capsule().strokeBorder(LiquidGlassDefaults.borderSpecular, lineWidth: 1))
                .shadow(color: Color.black.opacity(0.35), radius: 6, x: 0, y: 3)
            }
        }
        .padding(.horizontal, 16)
    }

    private var scrubberSection: some View {
        let maxDur = max(playbackService.durationSeconds, 1.0)
        let displayTime = isUserScrubbing ? scrubPosition : playbackService.currentTimeSeconds

        return VStack(spacing: 4) {
            GeometryReader { trackGeo in
                let totalWidth = trackGeo.size.width
                let progressFraction = max(0.0, min(displayTime / maxDur, 1.0))
                let playedWidth = totalWidth * progressFraction

                ZStack(alignment: .leading) {
                    // Base Track
                    Capsule()
                        .fill(Color.white.opacity(0.18))
                        .frame(height: isUserScrubbing ? 5 : 3.5)

                    // Played Progress Track (YouTube Red)
                    Capsule()
                        .fill(
                            LinearGradient(
                                colors: [Color(hex: 0xFF1744), Color(hex: 0xFF4B4B)],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .frame(width: max(0, playedWidth), height: isUserScrubbing ? 5 : 3.5)

                    // Red Glowing Liquid Thumb
                    Circle()
                        .fill(Color(hex: 0xFF1744))
                        .frame(width: isUserScrubbing ? 16 : 11, height: isUserScrubbing ? 16 : 11)
                        .overlay(Circle().fill(Color.white).frame(width: 3.5, height: 3.5))
                        .shadow(color: Color(hex: 0xFF1744, alpha: 0.65), radius: isUserScrubbing ? 8 : 4)
                        .offset(x: max(0, min(playedWidth - (isUserScrubbing ? 8 : 5.5), totalWidth - (isUserScrubbing ? 16 : 11))))
                }
                .frame(height: 22)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { gesture in
                            isUserScrubbing = true
                            let boundedX = min(max(gesture.location.x, 0), totalWidth)
                            let percent = totalWidth > 0 ? (boundedX / totalWidth) : 0
                            scrubPosition = percent * maxDur
                        }
                        .onEnded { _ in
                            playbackService.seekTo(seconds: scrubPosition)
                            isUserScrubbing = false
                        }
                )
            }
            .frame(height: 22)

            HStack {
                Text(formatTime(displayTime))
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(Color(hex: 0xAAAAAA))
                Spacer()
                Text(playbackService.durationSeconds > 0 ? formatTime(maxDur) : track.duration)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(Color(hex: 0xAAAAAA))
            }
        }
    }

    private var transportControlsSection: some View {
        HStack {
            Button(action: { playbackService.toggleShuffle() }) {
                Image(systemName: "shuffle")
                    .font(.system(size: 18))
                    .foregroundColor(playbackService.isShuffled ? AppTheme.ytMusicRedAccent : Color(hex: 0xAAAAAA))
                    .frame(width: 42, height: 42)
            }

            Spacer()

            Button(action: onPrevTrack) {
                Image(systemName: "backward.end.fill")
                    .font(.system(size: 26))
                    .foregroundColor(.white)
                    .frame(width: 46, height: 46)
            }

            Spacer()

            Button(action: onPlayPauseToggle) {
                ZStack {
                    Circle()
                        .fill(Color.white)
                        .frame(width: 64, height: 64)
                        .shadow(color: Color.black.opacity(0.35), radius: 8, x: 0, y: 4)
                    Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                        .font(.system(size: 28))
                        .foregroundColor(.black)
                }
            }

            Spacer()

            Button(action: onNextTrack) {
                Image(systemName: "forward.end.fill")
                    .font(.system(size: 26))
                    .foregroundColor(.white)
                    .frame(width: 46, height: 46)
            }

            Spacer()

            Button(action: { playbackService.toggleRepeat() }) {
                Image(systemName: playbackService.repeatMode == .one ? "repeat.1" : "repeat")
                    .font(.system(size: 18))
                    .foregroundColor(playbackService.repeatMode != .off ? AppTheme.ytMusicRedAccent : Color(hex: 0xAAAAAA))
                    .frame(width: 42, height: 42)
            }
        }
    }

    private func bottomSheetDock(safeBottom: CGFloat, maxHeight: CGFloat) -> some View {
        VStack(spacing: 0) {
            // Drag / Handle Indicator
            Capsule()
                .fill(Color.white.opacity(0.35))
                .frame(width: 36, height: 4)
                .padding(.top, 8)
                .padding(.bottom, 6)

            // Dock Header with Tab Pills & Expand/Collapse Chevron
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
                                .background(isActive ? Color.white.opacity(0.18) : Color.white.opacity(0.04))
                                .clipShape(RoundedRectangle(cornerRadius: 14))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 14)
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
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(Color(hex: 0xAAAAAA))
                        .padding(6)
                }
            }
            .padding(.horizontal, 14)
            .padding(.bottom, 6)

            if isSheetExpanded {
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
            } else {
                Spacer().frame(height: max(safeBottom, 8))
            }
        }
        .frame(height: isSheetExpanded ? maxHeight : (52 + max(safeBottom, 8)))
        .background(
            LinearGradient(
                colors: [Color(hex: 0x18192A, alpha: 0.98), Color(hex: 0x10111D, alpha: 0.98)],
                startPoint: .top,
                endPoint: .bottom
            )
        )
        .clipShape(RoundedCornerShape(radius: 24, corners: [.topLeft, .topRight]))
        .overlay(
            RoundedCornerShape(radius: 24, corners: [.topLeft, .topRight])
                .stroke(LiquidGlassDefaults.borderSpecular, lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.45), radius: 12, x: 0, y: -4)
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
