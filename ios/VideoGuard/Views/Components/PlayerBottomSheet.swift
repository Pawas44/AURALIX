import SwiftUI

struct PlayerBottomSheet: View {
    let appMode: AppMode
    let onDismiss: () -> Void
    var onOpenJam: (() -> Void)? = nil

    @ObservedObject var playbackService = MediaPlaybackService.shared
    @ObservedObject var jamEngine = ListenTogetherEngine.shared

    @State private var showQualityPicker = false
    @State private var showSpeedPicker = false
    @State private var showSleepTimerPicker = false
    @State private var showStatsAlert = false

    private let qualities = ["Auto (1080p)", "1080p HD", "720p HD", "480p", "360p"]
    private let speeds: [Float] = [0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0]
    private let timerOptions: [(String, Int?)] = [
        ("Off", nil), ("15 minutes", 15), ("30 minutes", 30), ("45 minutes", 45), ("60 minutes", 60)
    ]

    var body: some View {
        ZStack {
            Color(hex: 0x18192A).ignoresSafeArea()

            VStack(spacing: 0) {
                HStack {
                    Text("Playback settings")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                    Spacer()
                    Button(action: onDismiss) {
                        Image(systemName: "xmark")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(.white)
                            .padding(6)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 14)

                Divider().background(Color(hex: 0x333333))

                ScrollView {
                    VStack(spacing: 4) {
                        SettingsActionRow(
                            icon: "person.2.fill",
                            title: "Listen Together (Live Jam)",
                            subtitle: jamEngine.isInRoom ? "Active Jam: \(jamEngine.roomCode ?? "") • \(jamEngine.participants.count) Friends" : "Sync music with friends in real-time"
                        ) {
                            onDismiss()
                            onOpenJam?()
                        }

                        SettingsActionRow(
                            icon: "sparkles.tv",
                            title: "Quality",
                            subtitle: playbackService.playbackQuality
                        ) { showQualityPicker = true }

                        let speedLabel = playbackService.playbackSpeed == 1.0
                            ? "Normal (1.0x)"
                            : String(format: "%.2gx", playbackService.playbackSpeed)
                        SettingsActionRow(
                            icon: "speedometer",
                            title: "Playback speed",
                            subtitle: speedLabel
                        ) { showSpeedPicker = true }

                        SettingsToggleRow(
                            icon: "headphones",
                            title: "Background playback",
                            subtitle: "Keep audio playing when screen is off",
                            isOn: Binding(
                                get: { playbackService.isBackgroundPlayEnabled },
                                set: { _ in playbackService.toggleBackgroundPlay() }
                            )
                        )

                        SettingsToggleRow(
                            icon: "repeat",
                            title: "Loop video",
                            subtitle: "Automatically replay when finished",
                            isOn: Binding(
                                get: { playbackService.isLoopEnabled },
                                set: { _ in playbackService.toggleLoop() }
                            )
                        )

                        SettingsToggleRow(
                            icon: "play.circle",
                            title: "Autoplay",
                            subtitle: "Automatically play next video when finished",
                            isOn: Binding(
                                get: { playbackService.isAutoplayEnabled },
                                set: { _ in playbackService.toggleAutoplay() }
                            )
                        )

                        SettingsToggleRow(
                            icon: "captions.bubble",
                            title: "Captions (Subtitles)",
                            subtitle: "Display closed captions on video",
                            isOn: Binding(
                                get: { playbackService.isCaptionsEnabled },
                                set: { _ in playbackService.toggleCaptions() }
                            )
                        )

                        let timerText = playbackService.sleepTimerMinutes.map { "\($0) minutes remaining" } ?? "Off"
                        SettingsActionRow(
                            icon: "timer",
                            title: "Sleep timer",
                            subtitle: timerText
                        ) { showSleepTimerPicker = true }

                        SettingsActionRow(
                            icon: "info.circle",
                            title: "Stats & video info",
                            subtitle: "Resolution, ID, codec & channel"
                        ) { showStatsAlert = true }
                    }
                    .padding(.vertical, 8)
                }
            }
        }
        .confirmationDialog("Select Video Quality", isPresented: $showQualityPicker, titleVisibility: .visible) {
            ForEach(qualities, id: \.self) { q in
                Button(q) { playbackService.setPlaybackQuality(q) }
            }
            Button("Cancel", role: .cancel) {}
        }
        .confirmationDialog("Select Playback Speed", isPresented: $showSpeedPicker, titleVisibility: .visible) {
            ForEach(speeds, id: \.self) { spd in
                let label = spd == 1.0 ? "Normal (1.0x)" : String(format: "%.2gx", spd)
                Button(label) { playbackService.setPlaybackSpeed(spd) }
            }
            Button("Cancel", role: .cancel) {}
        }
        .confirmationDialog("Sleep Timer", isPresented: $showSleepTimerPicker, titleVisibility: .visible) {
            ForEach(timerOptions, id: \.0) { opt in
                Button(opt.0) { playbackService.setSleepTimer(minutes: opt.1) }
            }
            Button("Cancel", role: .cancel) {}
        }
        .alert("Stats & Video Info", isPresented: $showStatsAlert) {
            Button("OK", role: .cancel) {}
        } message: {
            if let track = playbackService.currentTrack {
                Text("Video ID: \(track.id)\nChannel: \(track.channelName)\nQuality: \(playbackService.playbackQuality)\nSpeed: \(playbackService.playbackSpeed)x\nViews: \(track.views)")
            } else {
                Text("No active media loaded.")
            }
        }
    }
}

private struct SettingsActionRow: View {
    let icon: String
    let title: String
    let subtitle: String
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 14) {
                Image(systemName: icon)
                    .font(.system(size: 19))
                    .foregroundColor(.white)
                    .frame(width: 26)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 15, weight: .medium))
                        .foregroundColor(.white)
                    Text(subtitle)
                        .font(.system(size: 12))
                        .foregroundColor(Color(hex: 0xAAAAAA))
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 13))
                    .foregroundColor(Color(hex: 0x777777))
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
        }
    }
}

private struct SettingsToggleRow: View {
    let icon: String
    let title: String
    let subtitle: String
    @Binding var isOn: Bool

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .font(.system(size: 19))
                .foregroundColor(.white)
                .frame(width: 26)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundColor(.white)
                Text(subtitle)
                    .font(.system(size: 12))
                    .foregroundColor(Color(hex: 0xAAAAAA))
            }
            Spacer()
            Toggle("", isOn: $isOn)
                .labelsHidden()
                .tint(AppTheme.youTubeRed)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 10)
    }
}
