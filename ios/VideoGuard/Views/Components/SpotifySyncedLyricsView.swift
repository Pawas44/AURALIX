import SwiftUI

/**
 * Spotify-Inspired Real-Time Synchronized Lyrics View for iOS
 *
 * Features:
 * - Real-time audio time-synchronization following current playback.
 * - Bold, luminous active line highlighting with smooth spring animations.
 * - Past lyrics dimmed softly (~0.35 opacity) and upcoming lyrics subtly visible (~0.65 opacity).
 * - Tap any line to seek music playback with subtle haptic tap.
 * - Smart Spotify-style manual scroll detection: pauses auto-scroll while browsing so the view
 *   never wrestles with user gestures.
 * - Floating "Sync" snap button when scrolled away, plus auto-resume after 4s inactivity.
 * - Top and bottom gradient scrims for seamless edge fading.
 * - Multi-line selection support for lyrics sharing.
 * - Universal Romanization pronunciation support.
 * - Premium empty state with Retry, Manual Search, and Add Custom Lyrics actions.
 */
struct SpotifySyncedLyricsView: View {
    let lyrics: LyricsResult?
    let isLoading: Bool
    let track: VideoItem
    let currentTimeSeconds: Double
    let lyricsOffsetMs: Double
    let romanizeMode: LyricsRomanizeMode
    let forcePlainView: Bool
    let onSeek: (Double) -> Void
    let onRetry: () -> Void
    let onSearchManually: () -> Void
    let onAddLyrics: () -> Void
    let onOpenSettings: () -> Void
    var onClose: (() -> Void)? = nil

    @State private var isUserScrolling: Bool = false
    @State private var lastScrollDate: Date = Date()
    @State private var selectedLineIndices: Set<Int> = []

    private var isSelectionMode: Bool {
        !selectedLineIndices.isEmpty
    }

    var body: some View {
        GeometryReader { geo in
            let centerPadding = max(16, (geo.size.height / 2) - 30)

            ZStack {
                if isLoading {
                    VStack(spacing: 12) {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: Color(hex: 0x3EA6FF)))
                            .scaleEffect(1.2)
                        Text("Loading lyrics...")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(.white.opacity(0.65))
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if let lyrics = lyrics, !lyrics.lines.isEmpty {
                    lyricsContent(lyrics: lyrics, centerPadding: centerPadding)
                } else {
                    emptyStateView
                }
            }
        }
    }

    // MARK: - Lyrics List & Animations

    @ViewBuilder
    private func lyricsContent(lyrics: LyricsResult, centerPadding: CGFloat) -> some View {
        let adjustedTimeMs = (currentTimeSeconds * 1000.0) + lyricsOffsetMs
        let activeIdx: Int = {
            if lyrics.isSynced && !lyrics.lines.isEmpty {
                return lyrics.lines.lastIndex(where: { $0.timestampMs <= adjustedTimeMs }) ?? 0
            }
            return -1
        }()

        ZStack {
            ScrollViewReader { proxy in
                ScrollView(showsIndicators: false) {
                    LazyVStack(alignment: .leading, spacing: 14) {
                        // Top Padding
                        Spacer().frame(height: (lyrics.isSynced && !forcePlainView) ? centerPadding : 16)

                        ForEach(0..<lyrics.lines.count, id: \.self) { idx in
                            let line = lyrics.lines[idx]
                            let isActive = idx == activeIdx && lyrics.isSynced && !forcePlainView && !isSelectionMode
                            let isPast = idx < activeIdx && lyrics.isSynced && !forcePlainView && !isSelectionMode
                            let isUpcoming = idx > activeIdx && lyrics.isSynced && !forcePlainView && !isSelectionMode
                            let isSelected = selectedLineIndices.contains(idx)

                            lyricLineRow(
                                line: line,
                                index: idx,
                                isActive: isActive,
                                isPast: isPast,
                                isUpcoming: isUpcoming,
                                isSelected: isSelected,
                                isSynced: lyrics.isSynced && !forcePlainView,
                                proxy: proxy
                            )
                            .id(idx)
                        }

                        // Bottom Padding
                        Spacer().frame(height: isSelectionMode ? centerPadding + 60 : ((lyrics.isSynced && !forcePlainView) ? centerPadding : 32))
                    }
                    .padding(.horizontal, 20)
                }
                .simultaneousGesture(
                    DragGesture().onChanged { _ in
                        isUserScrolling = true
                        lastScrollDate = Date()
                    }
                )
                .onChange(of: activeIdx) { newIdx in
                    if newIdx >= 0 && lyrics.isSynced && !forcePlainView && !isSelectionMode && !isUserScrolling {
                        withAnimation(.spring(response: 0.38, dampingFraction: 0.85)) {
                            proxy.scrollTo(newIdx, anchor: .center)
                        }
                    }
                }
                .task(id: isUserScrolling) {
                    if isUserScrolling {
                        try? await Task.sleep(nanoseconds: 4_000_000_000)
                        if Date().timeIntervalSince(lastScrollDate) >= 3.8 {
                            withAnimation(.spring(response: 0.4, dampingFraction: 0.85)) {
                                isUserScrolling = false
                                if activeIdx >= 0 && lyrics.isSynced && !forcePlainView {
                                    proxy.scrollTo(activeIdx, anchor: .center)
                                }
                            }
                        }
                    }
                }
            }

            // Top Gradient Scrim
            VStack {
                LinearGradient(
                    colors: [Color.black.opacity(0.75), Color.clear],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: 38)
                Spacer()
            }
            .allowsHitTesting(false)

            // Bottom Gradient Scrim
            VStack {
                Spacer()
                LinearGradient(
                    colors: [Color.clear, Color.black.opacity(0.75)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: 38)
            }
            .allowsHitTesting(false)

            // Floating "Sync with Song" Snap-Back Button (Spotify-like)
            if isUserScrolling && activeIdx >= 0 && lyrics.isSynced && !forcePlainView && !isSelectionMode {
                VStack {
                    Spacer()
                    HStack {
                        Spacer()
                        Button(action: {
                            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                            isUserScrolling = false
                        }) {
                            HStack(spacing: 6) {
                                Image(systemName: "clock.arrow.circlepath")
                                    .font(.system(size: 13, weight: .bold))
                                Text("Sync")
                                    .font(.system(size: 13, weight: .bold))
                            }
                            .foregroundColor(.white)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            .background(Color(hex: 0x181A26).opacity(0.92))
                            .clipShape(Capsule())
                            .overlay(
                                Capsule().stroke(Color(hex: 0x3EA6FF).opacity(0.7), lineWidth: 1)
                            )
                            .shadow(color: Color.black.opacity(0.4), radius: 8, y: 3)
                        }
                        .padding(.trailing, 16)
                        .padding(.bottom, 16)
                        .transition(.scale.combined(with: .opacity))
                    }
                }
            }

            // Selection Mode Bottom Floating Action Bar
            if isSelectionMode {
                VStack {
                    Spacer()
                    HStack {
                        Button(action: { selectedLineIndices.removeAll() }) {
                            Image(systemName: "xmark")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.white.opacity(0.7))
                                .frame(width: 32, height: 32)
                        }

                        Text("\(selectedLineIndices.count)/3 lines")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.white)

                        Spacer()

                        Button(action: {
                            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                            let sorted = selectedLineIndices.sorted().compactMap { idx -> String? in
                                lyrics.lines[safe: idx]?.text
                            }
                            if !sorted.isEmpty {
                                UIPasteboard.general.string = sorted.joined(separator: "\n")
                                selectedLineIndices.removeAll()
                            }
                        }) {
                            HStack(spacing: 6) {
                                Image(systemName: "doc.on.doc")
                                    .font(.system(size: 12, weight: .bold))
                                Text("Copy Lines")
                                    .font(.system(size: 12, weight: .bold))
                            }
                            .foregroundColor(.white)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            .background(
                                LinearGradient(
                                    colors: [Color(hex: 0x8B5CF6), Color(hex: 0xEC4899)],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                            .clipShape(Capsule())
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(Color(hex: 0x181A26).opacity(0.95))
                    .clipShape(RoundedRectangle(cornerRadius: 20))
                    .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color(hex: 0x8B5CF6).opacity(0.5), lineWidth: 1))
                    .shadow(color: Color.black.opacity(0.5), radius: 10, y: 4)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 12)
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
    }

    // MARK: - Lyric Line Row

    @ViewBuilder
    private func lyricLineRow(
        line: LyricLine,
        index: Int,
        isActive: Bool,
        isPast: Bool,
        isUpcoming: Bool,
        isSelected: Bool,
        isSynced: Bool,
        proxy: ScrollViewProxy
    ) -> some View {
        let effectiveRoman: String = {
            if let r = line.romanizedText, !r.isEmpty { return r }
            if TransliterationEngine.shared.canRomanize(line.text) {
                return TransliterationEngine.shared.romanize(line.text)
            }
            return ""
        }()

        let primaryText: String = {
            if romanizeMode == .romanized && !effectiveRoman.isEmpty {
                return effectiveRoman
            }
            return line.text
        }()

        let showSub: Bool = romanizeMode == .both && !effectiveRoman.isEmpty && effectiveRoman != line.text

        VStack(alignment: .leading, spacing: 3) {
            Text(primaryText)
                .font(.system(
                    size: (isActive || isSelected) ? 20 : 16,
                    weight: (isActive || isSelected) ? .heavy : .medium
                ))
                .foregroundColor(
                    isSelected ? .white :
                    isActive ? .white :
                    isPast ? .white.opacity(0.35) :
                    isUpcoming ? .white.opacity(0.65) :
                    .white.opacity(0.90)
                )
                .scaleEffect((isActive || isSelected) ? 1.04 : (isPast ? 0.97 : 1.0), anchor: .leading)
                .shadow(
                    color: isActive ? Color.black.opacity(0.85) : Color.clear,
                    radius: 8,
                    x: 0,
                    y: 2
                )
                .animation(.spring(response: 0.3, dampingFraction: 0.8), value: isActive)
                .lineSpacing(4)
                .frame(maxWidth: .infinity, alignment: .leading)

            if showSub {
                Text(effectiveRoman)
                    .font(.system(size: (isActive || isSelected) ? 13 : 12, weight: .medium))
                    .foregroundColor(isActive ? Color(hex: 0x93C5FD).opacity(0.9) : .white.opacity(0.35))
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(.vertical, (isActive || isSelected) ? 6 : 3)
        .padding(.horizontal, 10)
        .background(
            isSelected ?
                LinearGradient(
                    colors: [Color(hex: 0x8B5CF6).opacity(0.35), Color(hex: 0xEC4899).opacity(0.35)],
                    startPoint: .leading,
                    endPoint: .trailing
                ) :
                LinearGradient(colors: [Color.clear, Color.clear], startPoint: .leading, endPoint: .trailing)
        )
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(isSelected ? Color(hex: 0xC084FC) : Color.clear, lineWidth: 1.5)
        )
        .contentShape(Rectangle())
        .onTapGesture {
            if isSelectionMode {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                if isSelected {
                    selectedLineIndices.remove(index)
                } else if selectedLineIndices.count < 3 {
                    selectedLineIndices.insert(index)
                }
            } else if isSynced {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                let targetSec = max(0, (line.timestampMs - lyricsOffsetMs) / 1000.0)
                onSeek(targetSec)
                isUserScrolling = false
                withAnimation(.spring(response: 0.32, dampingFraction: 0.85)) {
                    proxy.scrollTo(index, anchor: .center)
                }
            }
        }
        .onLongPressGesture {
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            if !isSelected {
                if selectedLineIndices.count >= 3 { selectedLineIndices.removeAll() }
                selectedLineIndices.insert(index)
            }
        }
    }

    // MARK: - Premium Empty State

    private var emptyStateView: some View {
        VStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(Color(hex: 0x8B5CF6).opacity(0.18))
                    .frame(width: 56, height: 56)
                    .overlay(Circle().stroke(Color(hex: 0x8B5CF6).opacity(0.35), lineWidth: 1))

                Image(systemName: "music.note")
                    .font(.system(size: 26))
                    .foregroundColor(Color(hex: 0xC084FC))
            }

            VStack(spacing: 4) {
                Text("Lyrics Not Available")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.white)

                Text("Couldn't match verified lyrics for \"\(track.title)\"")
                    .font(.system(size: 12))
                    .foregroundColor(.white.opacity(0.55))
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .padding(.horizontal, 24)
            }

            HStack(spacing: 10) {
                Button(action: onRetry) {
                    HStack(spacing: 5) {
                        Image(systemName: "arrow.clockwise")
                            .font(.system(size: 12))
                        Text("Retry")
                            .font(.system(size: 12, weight: .semibold))
                    }
                    .foregroundColor(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 7)
                    .background(Color.white.opacity(0.1))
                    .clipShape(Capsule())
                    .overlay(Capsule().stroke(Color.white.opacity(0.2), lineWidth: 1))
                }

                Button(action: onSearchManually) {
                    HStack(spacing: 5) {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 12))
                        Text("Search")
                            .font(.system(size: 12, weight: .bold))
                    }
                    .foregroundColor(Color(hex: 0x3EA6FF))
                    .padding(.horizontal, 14)
                    .padding(.vertical, 7)
                    .background(Color(hex: 0x1E3A5F))
                    .clipShape(Capsule())
                    .overlay(Capsule().stroke(Color(hex: 0x3EA6FF).opacity(0.5), lineWidth: 1))
                }

                Button(action: onAddLyrics) {
                    HStack(spacing: 5) {
                        Image(systemName: "plus")
                            .font(.system(size: 12))
                        Text("Add")
                            .font(.system(size: 12, weight: .semibold))
                    }
                    .foregroundColor(Color(hex: 0xC084FC))
                    .padding(.horizontal, 14)
                    .padding(.vertical, 7)
                    .background(Color(hex: 0x8B5CF6).opacity(0.2))
                    .clipShape(Capsule())
                    .overlay(Capsule().stroke(Color(hex: 0x8B5CF6).opacity(0.5), lineWidth: 1))
                }
            }
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .background(Color(hex: 0x131422).opacity(0.85))
        .clipShape(RoundedRectangle(cornerRadius: 22))
        .overlay(RoundedRectangle(cornerRadius: 22).stroke(Color.white.opacity(0.1), lineWidth: 1))
        .padding(.horizontal, 20)
    }
}

// MARK: - Lyrics Settings Bottom Sheet

struct LyricsSettingsSheet: View {
    let track: VideoItem
    let lyrics: LyricsResult?
    @Binding var lyricsOffsetMs: Double
    @Binding var forcePlainView: Bool
    let onDismiss: () -> Void
    let onSearchAlternative: () -> Void
    let onAddCustom: () -> Void
    let onCopyLyrics: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            // Header
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Lyrics Settings")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                    Text("\(track.title) • \(track.artist.isEmpty ? track.channelName : track.artist)")
                        .font(.system(size: 12))
                        .foregroundColor(.white.opacity(0.6))
                        .lineLimit(1)
                }
                Spacer()
                Button(action: onDismiss) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 22))
                        .foregroundColor(Color(hex: 0x777777))
                }
            }

            // Source & Status Card
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Lyrics Provider")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(.white.opacity(0.5))
                    Text(lyrics?.source ?? "LRCLIB")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.white)
                }
                Spacer()
                let isRealSynced = lyrics?.isSynced == true
                Text(isRealSynced ? "Real-time Synced" : "Estimated Pacing")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(isRealSynced ? Color(hex: 0x1DB954) : Color(hex: 0xFFFFB300))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(isRealSynced ? Color(hex: 0x1DB954).opacity(0.18) : Color(hex: 0xFFFFB300).opacity(0.18))
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(isRealSynced ? Color(hex: 0x1DB954).opacity(0.4) : Color(hex: 0xFFFFB300).opacity(0.4), lineWidth: 1))
            }
            .padding(14)
            .background(Color.white.opacity(0.05))
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.white.opacity(0.1), lineWidth: 1))

            // Sync Timing Offset Tuner
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("Sync Timing Offset")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(.white)
                    Spacer()
                    let offsetLabel = lyricsOffsetMs == 0.0 ? "0.0s (In Sync)" : String(format: "%+.1fs", lyricsOffsetMs / 1000.0)
                    Text(offsetLabel)
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(lyricsOffsetMs == 0.0 ? Color(hex: 0x1DB954) : Color(hex: 0xFFFFB300))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(lyricsOffsetMs == 0.0 ? Color(hex: 0x1DB954).opacity(0.18) : Color(hex: 0xFFFFB300).opacity(0.18))
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }

                HStack(spacing: 8) {
                    offsetButton("-0.5s") { lyricsOffsetMs = LyricsEngine.shared.adjustOffsetMs(videoId: track.id, deltaMs: -500) }
                    offsetButton("-0.1s") { lyricsOffsetMs = LyricsEngine.shared.adjustOffsetMs(videoId: track.id, deltaMs: -100) }
                    offsetButton("+0.1s") { lyricsOffsetMs = LyricsEngine.shared.adjustOffsetMs(videoId: track.id, deltaMs: 100) }
                    offsetButton("+0.5s") { lyricsOffsetMs = LyricsEngine.shared.adjustOffsetMs(videoId: track.id, deltaMs: 500) }
                }

                if lyricsOffsetMs != 0.0 {
                    Button(action: {
                        LyricsEngine.shared.resetOffset(videoId: track.id)
                        lyricsOffsetMs = 0.0
                    }) {
                        Text("Reset Timing to 0.0s")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(Color(hex: 0xFFFF8A80))
                            .frame(maxWidth: .infinity, alignment: .center)
                            .padding(.top, 4)
                    }
                }
            }
            .padding(14)
            .background(Color.white.opacity(0.05))
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.white.opacity(0.1), lineWidth: 1))

            // Actions list
            VStack(spacing: 8) {
                Toggle(isOn: $forcePlainView) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Force Plain Lyrics View")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(.white)
                        Text("Turn off auto-scrolling to read lyrics statically")
                            .font(.system(size: 11))
                            .foregroundColor(.white.opacity(0.5))
                    }
                }
                .tint(Color(hex: 0x3EA6FF))
                .padding(.vertical, 4)

                Divider().background(Color.white.opacity(0.08))

                Button(action: onSearchAlternative) {
                    HStack(spacing: 12) {
                        Image(systemName: "magnifyingglass")
                            .foregroundColor(Color(hex: 0x3EA6FF))
                            .font(.system(size: 16))
                        Text("Search / Fix Different Lyrics")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(.white)
                        Spacer()
                    }
                    .padding(.vertical, 6)
                }

                Button(action: onAddCustom) {
                    HStack(spacing: 12) {
                        Image(systemName: "plus")
                            .foregroundColor(Color(hex: 0xC084FC))
                            .font(.system(size: 16))
                        Text("Add / Paste Custom Lyrics")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(.white)
                        Spacer()
                    }
                    .padding(.vertical, 6)
                }

                Button(action: onCopyLyrics) {
                    HStack(spacing: 12) {
                        Image(systemName: "doc.on.doc")
                            .foregroundColor(Color(hex: 0xAAAAAA))
                            .font(.system(size: 16))
                        Text("Copy Lyrics to Clipboard")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(.white)
                        Spacer()
                    }
                    .padding(.vertical, 6)
                }
            }

            Spacer()
        }
        .padding(22)
        .background(Color(hex: 0x13141F).ignoresSafeArea())
    }

    private func offsetButton(_ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
                .background(Color.white.opacity(0.1))
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.white.opacity(0.15), lineWidth: 1))
        }
    }
}

// MARK: - Add Custom Lyrics Sheet

struct AddCustomLyricsSheet: View {
    let track: VideoItem
    let durationSeconds: Int
    let onSave: (LyricsResult) -> Void
    let onDismiss: () -> Void

    @State private var rawText: String = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text("Add Custom Lyrics")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
                Spacer()
                Button("Cancel", action: onDismiss)
                    .foregroundColor(.gray)
            }

            Text("Paste plain lyrics or LRC format with [mm:ss.xx] timestamps for real-time synchronization.")
                .font(.system(size: 12))
                .foregroundColor(.white.opacity(0.6))
                .lineSpacing(3)

            TextEditor(text: $rawText)
                .font(.system(size: 13, design: .monospaced))
                .foregroundColor(.white)
                .padding(8)
                .background(Color(hex: 0x1E202E))
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color(hex: 0x3EA6FF).opacity(0.4), lineWidth: 1))
                .frame(minHeight: 200)

            Button(action: {
                let trimmed = rawText.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !trimmed.isEmpty else { return }
                let result = LyricsEngine.shared.saveUserCustomLyrics(
                    videoId: track.id,
                    rawText: trimmed,
                    durationSeconds: durationSeconds
                )
                onSave(result)
            }) {
                Text("Save & Sync")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Color(hex: 0x3EA6FF))
                    .clipShape(RoundedRectangle(cornerRadius: 14))
            }
        }
        .padding(20)
        .background(Color(hex: 0x13141F).ignoresSafeArea())
    }
}

// MARK: - Search Alternative Lyrics Sheet

struct SearchLyricsSheet: View {
    let initialQuery: String
    let onSelect: (LyricsResult) -> Void
    let onDismiss: () -> Void

    @State private var query: String = ""
    @State private var results: [LyricsSearchResultItem] = []
    @State private var isSearching: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text("Search Lyrics")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
                Spacer()
                Button("Done", action: onDismiss)
                    .foregroundColor(.white)
            }

            HStack {
                TextField("Search track or artist name...", text: $query)
                    .foregroundColor(.white)
                    .font(.system(size: 14))
                    .padding(10)
                    .background(Color.white.opacity(0.08))
                    .clipShape(RoundedRectangle(cornerRadius: 10))

                Button(action: triggerSearch) {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(.white)
                        .padding(10)
                        .background(Color(hex: 0x3EA6FF))
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                }
            }

            if isSearching {
                HStack {
                    Spacer()
                    ProgressView().tint(Color(hex: 0x3EA6FF))
                    Spacer()
                }
                .padding(.vertical, 24)
            } else if !results.isEmpty {
                ScrollView {
                    LazyVStack(spacing: 8) {
                        ForEach(results) { item in
                            Button(action: {
                                let parsed = LyricsEngine.shared.parseCustomLrc(synced: item.syncedLyrics, plain: item.plainLyrics)
                                onSelect(parsed)
                            }) {
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(item.trackName)
                                            .font(.system(size: 13, weight: .semibold))
                                            .foregroundColor(.white)
                                            .lineLimit(1)
                                        Text("\(item.artistName) • \(item.durationSeconds / 60):\(String(format: "%02d", item.durationSeconds % 60))")
                                            .font(.system(size: 11))
                                            .foregroundColor(Color(hex: 0xAAAAAA))
                                    }
                                    Spacer()
                                    if item.isSynced {
                                        Text("SYNCED")
                                            .font(.system(size: 9, weight: .bold))
                                            .foregroundColor(Color(hex: 0x3EA6FF))
                                            .padding(.horizontal, 6)
                                            .padding(.vertical, 2)
                                            .background(Color(hex: 0x1E3A5F))
                                            .clipShape(RoundedRectangle(cornerRadius: 4))
                                    }
                                }
                                .padding(10)
                                .background(Color.white.opacity(0.04))
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                            }
                        }
                    }
                }
            } else {
                Text("Search LRCLIB for verified timestamped lyrics.")
                    .font(.system(size: 12))
                    .foregroundColor(.white.opacity(0.5))
                    .padding(.top, 10)
            }

            Spacer()
        }
        .padding(20)
        .background(Color(hex: 0x13141F).ignoresSafeArea())
        .onAppear {
            query = initialQuery
            triggerSearch()
        }
    }

    private func triggerSearch() {
        guard !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        isSearching = true
        Task {
            let res = await LyricsEngine.shared.searchAlternativeLyrics(query: query)
            await MainActor.run {
                results = res
                isSearching = false
            }
        }
    }
}

private extension Array {
    subscript(safe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
