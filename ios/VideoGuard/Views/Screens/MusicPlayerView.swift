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
            // Top Bar: Collapse, Song/Video Switch, Settings
            HStack {
                Button(action: onCollapse) {
                    Image(systemName: "chevron.down")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                        .padding(10)
                }

                Spacer()

                HStack(spacing: 0) {
                    Text("Song")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(.black)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 6)
                        .background(Color.white)
                        .cornerRadius(16)

                    Button(action: onSwitchToVideoMode) {
                        Text("Video")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.white)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 6)
                    }
                }
                .padding(3)
                .background(Color.white.opacity(0.12))
                .cornerRadius(20)

                Spacer()

                Button(action: { showSettingsSheet = true }) {
                    Image(systemName: "ellipsis")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                        .padding(10)
                }
            }
            .padding(.horizontal, 12)
            .padding(.top, 8)

            if !isSheetExpanded {
                Spacer(minLength: 10)

                // Large Artwork
                AsyncImage(url: URL(string: track.thumbnailUrl)) { image in
                    image.resizable().aspectRatio(contentMode: .fill)
                } placeholder: {
                    Rectangle().fill(AppTheme.ytMusicCardBg)
                }
                .frame(width: 280, height: 280)
                .cornerRadius(16)
                .shadow(color: Color.black.opacity(0.6), radius: 18, x: 0, y: 8)
                .padding(.vertical, 12)

                Spacer(minLength: 8)
            }

            // Title, Artist, Like/Dislike
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(track.title)
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(.white)
                        .lineLimit(1)
                    Text(track.artist.isEmpty ? track.channelName : track.artist)
                        .font(.system(size: 15))
                        .foregroundColor(AppTheme.ytMusicTextSecondary)
                        .lineLimit(1)
                }

                Spacer()

                Button(action: { userData.toggleDislike(track.id) }) {
                    Image(systemName: userData.isDisliked(track.id) ? "hand.thumbsdown.fill" : "hand.thumbsdown")
                        .font(.system(size: 20))
                        .foregroundColor(.white)
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

            // Progress Slider
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
                .tint(.white)

                HStack {
                    Text(formatTime(displayTime))
                        .font(.system(size: 11))
                        .foregroundColor(AppTheme.ytMusicTextSecondary)
                    Spacer()
                    Text(formatTime(maxDur))
                        .font(.system(size: 11))
                        .foregroundColor(AppTheme.ytMusicTextSecondary)
                }
            }
            .padding(.horizontal, 24)
            .padding(.top, 10)

            // Transport Controls
            HStack(spacing: 28) {
                Button(action: { playbackService.toggleShuffle() }) {
                    Image(systemName: "shuffle")
                        .font(.system(size: 20))
                        .foregroundColor(playbackService.isShuffled ? AppTheme.ytMusicRedAccent : .white)
                }

                Button(action: onPrevTrack) {
                    Image(systemName: "backward.end.fill")
                        .font(.system(size: 28))
                        .foregroundColor(.white)
                }

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

                Button(action: onNextTrack) {
                    Image(systemName: "forward.end.fill")
                        .font(.system(size: 28))
                        .foregroundColor(.white)
                }

                Button(action: { playbackService.toggleRepeat() }) {
                    Image(systemName: playbackService.repeatMode == .one ? "repeat.1" : "repeat")
                        .font(.system(size: 20))
                        .foregroundColor(playbackService.repeatMode != .off ? AppTheme.ytMusicRedAccent : .white)
                }
            }
            .padding(.vertical, 16)

            // Bottom Drawer: Up Next / Lyrics / Related
            VStack(spacing: 0) {
                Button(action: {
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
                        isSheetExpanded.toggle()
                    }
                }) {
                    Capsule()
                        .fill(Color.white.opacity(0.3))
                        .frame(width: 38, height: 4)
                        .padding(.vertical, 8)
                }

                HStack {
                    ForEach(MusicSheetTab.allCases, id: \.self) { tab in
                        Button(action: {
                            activeTab = tab
                            if !isSheetExpanded {
                                withAnimation { isSheetExpanded = true }
                            }
                        }) {
                            VStack(spacing: 6) {
                                Text(tab.rawValue)
                                    .font(.system(size: 12, weight: activeTab == tab ? .bold : .medium))
                                    .foregroundColor(activeTab == tab ? .white : AppTheme.ytMusicTextSecondary)
                                Rectangle()
                                    .fill(activeTab == tab ? Color.white : Color.clear)
                                    .frame(height: 2)
                            }
                            .frame(maxWidth: .infinity)
                        }
                    }
                }
                .padding(.horizontal, 16)

                ScrollView {
                    switch activeTab {
                    case .upNext, .related:
                        LazyVStack(spacing: 10) {
                            if isLoadingRelated && queueTracks.isEmpty {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                    .padding()
                            } else {
                                ForEach(queueTracks) { item in
                                    Button(action: { onTrackSelected(item) }) {
                                        HStack(spacing: 12) {
                                            AsyncImage(url: URL(string: item.thumbnailUrl)) { img in
                                                img.resizable().aspectRatio(contentMode: .fill)
                                            } placeholder: {
                                                Rectangle().fill(AppTheme.ytMusicCardBg)
                                            }
                                            .frame(width: 48, height: 48)
                                            .cornerRadius(6)
                                            .clipped()

                                            VStack(alignment: .leading, spacing: 2) {
                                                Text(item.title)
                                                    .font(.system(size: 14, weight: .medium))
                                                    .foregroundColor(.white)
                                                    .lineLimit(1)
                                                Text("\(item.artist.isEmpty ? item.channelName : item.artist) • \(item.duration)")
                                                    .font(.system(size: 12))
                                                    .foregroundColor(AppTheme.ytMusicTextSecondary)
                                                    .lineLimit(1)
                                            }
                                            Spacer()
                                        }
                                        .padding(.horizontal, 16)
                                        .padding(.vertical, 4)
                                    }
                                }
                            }
                        }
                        .padding(.vertical, 8)
                    case .lyrics:
                        Text(track.lyrics.isEmpty ? "Lyrics unavailable for this track.\n\nPlaying: \(track.title)\nArtist: \(track.artist.isEmpty ? track.channelName : track.artist)" : track.lyrics)
                            .font(.system(size: 15))
                            .foregroundColor(.white.opacity(0.85))
                            .multilineTextAlignment(.center)
                            .padding(24)
                            .frame(maxWidth: .infinity)
                    }
                }
            }
            .frame(height: isSheetExpanded ? 360 : 170)
            .background(Color(hex: 0x15101D).opacity(0.96))
            .cornerRadius(20, corners: [.topLeft, .topRight])
        }
        .background(LiquidGlassDefaults.liquidMusicCanvas.ignoresSafeArea())
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
