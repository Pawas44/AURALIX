import SwiftUI

struct PlayerControlsOverlay: View {
    let isPlaying: Bool
    let currentTime: Double
    let duration: Double
    let onPlayPauseToggle: () -> Void
    let onNext: () -> Void
    let onPrev: () -> Void
    let onSeekBy: (Int) -> Void
    let onSeekTo: (Double) -> Void
    let onCollapse: () -> Void
    var isFullscreen: Bool = false
    var onToggleFullscreen: () -> Void = {}

    @State private var controlsVisible = true
    @State private var isUserScrubbing = false
    @State private var scrubPosition: Double = 0
    enum ActiveVideoSheet: String, Identifiable {
        case settings
        case jam

        var id: String { rawValue }
    }

    @State private var activeSheet: ActiveVideoSheet? = nil
    @State private var doubleTapSide: String? = nil
    @State private var hideTask: DispatchWorkItem? = nil

    var body: some View {
        GeometryReader { geo in
            ZStack {
                // Transparent tap/double-tap surface
                HStack(spacing: 0) {
                    Color.clear
                        .contentShape(Rectangle())
                        .onTapGesture(count: 2) {
                            onSeekBy(-10)
                            triggerDoubleTap("left")
                        }
                        .onTapGesture(count: 1) {
                            toggleControls()
                        }

                    Color.clear
                        .contentShape(Rectangle())
                        .onTapGesture(count: 2) {
                            onSeekBy(10)
                            triggerDoubleTap("right")
                        }
                        .onTapGesture(count: 1) {
                            toggleControls()
                        }
                }

                // Double Tap Feedback Left / Right
                HStack(spacing: 0) {
                    if doubleTapSide == "left" {
                        ZStack {
                            Color.white.opacity(0.15)
                            VStack(spacing: 4) {
                                Image(systemName: "gobackward.10")
                                    .font(.system(size: 32))
                                    .foregroundColor(.white)
                                Text("10 seconds")
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundColor(.white)
                            }
                        }
                        .frame(width: geo.size.width * 0.5, height: geo.size.height)
                    } else {
                        Spacer().frame(width: geo.size.width * 0.5)
                    }

                    if doubleTapSide == "right" {
                        ZStack {
                            Color.white.opacity(0.15)
                            VStack(spacing: 4) {
                                Image(systemName: "goforward.10")
                                    .font(.system(size: 32))
                                    .foregroundColor(.white)
                                Text("10 seconds")
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundColor(.white)
                            }
                        }
                        .frame(width: geo.size.width * 0.5, height: geo.size.height)
                    } else {
                        Spacer().frame(width: geo.size.width * 0.5)
                    }
                }
                .allowsHitTesting(false)

                // Visible Controls Overlay
                if controlsVisible {
                    ZStack {
                        Color.black.opacity(0.45)
                            .allowsHitTesting(false)

                        VStack {
                            // Top Bar: Collapse & Settings
                            HStack {
                                Button(action: onCollapse) {
                                    Image(systemName: isFullscreen ? "chevron.down" : "chevron.down")
                                        .font(.system(size: 18, weight: .bold))
                                        .foregroundColor(.white)
                                        .padding(10)
                                        .background(Color.black.opacity(0.4))
                                        .clipShape(Circle())
                                }

                                Spacer()

                                Button(action: { activeSheet = .settings }) {
                                    Image(systemName: "gearshape.fill")
                                        .font(.system(size: 18))
                                        .foregroundColor(.white)
                                        .padding(10)
                                        .background(Color.black.opacity(0.4))
                                        .clipShape(Circle())
                                }
                            }
                            .padding(.horizontal, 12)
                            .padding(.top, 8)

                            Spacer()

                            // Center Transport Controls
                            HStack(spacing: 36) {
                                Button(action: {
                                    onPrev()
                                    scheduleAutoHide()
                                }) {
                                    Image(systemName: "backward.end.fill")
                                        .font(.system(size: 26))
                                        .foregroundColor(.white)
                                }

                                Button(action: {
                                    onPlayPauseToggle()
                                    scheduleAutoHide()
                                }) {
                                    Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                                        .font(.system(size: 34))
                                        .foregroundColor(.white)
                                        .frame(width: 58, height: 58)
                                        .background(Color.black.opacity(0.55))
                                        .clipShape(Circle())
                                }

                                Button(action: {
                                    onNext()
                                    scheduleAutoHide()
                                }) {
                                    Image(systemName: "forward.end.fill")
                                        .font(.system(size: 26))
                                        .foregroundColor(.white)
                                }
                            }

                            Spacer()

                            // Bottom Scrubber & Fullscreen Button
                            let maxDur = max(duration, 1.0)
                            let displayTime = isUserScrubbing ? scrubPosition : currentTime

                            VStack(spacing: 4) {
                                HStack(spacing: 8) {
                                    Text("\(formatTime(displayTime)) / \(formatTime(maxDur))")
                                        .font(.system(size: 11, weight: .medium))
                                        .foregroundColor(.white)

                                    Spacer()

                                    Button(action: onToggleFullscreen) {
                                        Image(systemName: isFullscreen ? "arrow.down.right.and.arrow.up.left" : "arrow.up.left.and.arrow.down.right")
                                            .font(.system(size: 16, weight: .semibold))
                                            .foregroundColor(.white)
                                            .padding(6)
                                    }
                                }

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
                                            onSeekTo(scrubPosition)
                                            isUserScrubbing = false
                                            scheduleAutoHide()
                                        }
                                    }
                                )
                                .tint(AppTheme.youTubeRed)
                            }
                            .padding(.horizontal, 12)
                            .padding(.bottom, 8)
                        }
                    }
                }
            }
        }
        .onAppear {
            scheduleAutoHide()
        }
        .onChange(of: isPlaying) { _ in
            scheduleAutoHide()
        }
        .sheet(item: $activeSheet) { sheet in
            switch sheet {
            case .settings:
                PlayerBottomSheet(
                    appMode: .youtube,
                    onDismiss: { activeSheet = nil },
                    onOpenJam: { activeSheet = .jam }
                )
                .presentationDetents([.medium, .large])
            case .jam:
                ListenTogetherSheet()
            }
        }
    }

    private func toggleControls() {
        withAnimation(.easeInOut(duration: 0.2)) {
            controlsVisible.toggle()
        }
        if controlsVisible {
            scheduleAutoHide()
        }
    }

    private func scheduleAutoHide() {
        hideTask?.cancel()
        guard isPlaying && !isUserScrubbing && activeSheet == nil else { return }
        let task = DispatchWorkItem {
            withAnimation(.easeOut(duration: 0.25)) {
                controlsVisible = false
            }
        }
        hideTask = task
        DispatchQueue.main.asyncAfter(deadline: .now() + 3.5, execute: task)
    }

    private func triggerDoubleTap(_ side: String) {
        withAnimation(.easeInOut(duration: 0.15)) {
            doubleTapSide = side
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.65) {
            withAnimation(.easeOut(duration: 0.2)) {
                if doubleTapSide == side {
                    doubleTapSide = nil
                }
            }
        }
    }

    private func formatTime(_ seconds: Double) -> String {
        let total = max(0, Int(seconds))
        let h = total / 3600
        let m = (total % 3600) / 60
        let s = total % 60
        if h > 0 {
            return String(format: "%d:%02d:%02d", h, m, s)
        }
        return String(format: "%d:%02d", m, s)
    }
}
