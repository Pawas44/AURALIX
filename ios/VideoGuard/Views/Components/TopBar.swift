import SwiftUI

struct YouTubeTopBar: View {
    let appMode: AppMode
    let onModeChanged: (AppMode) -> Void
    let onTitleClick: () -> Void
    let onSearchClick: () -> Void
    let isBackgroundPlay: Bool
    let onToggleBackgroundPlay: () -> Void
    let onProfileClick: () -> Void

    var body: some View {
        let isMusic = appMode == .youtubeMusic

        VStack(spacing: 0) {
            HStack {
                Button(action: onTitleClick) {
                    Text("AURALIX")
                        .font(.system(size: 21, weight: .heavy))
                        .tracking(-0.5)
                        .foregroundColor(.white)
                }

                Spacer()

                // Liquid Glass Mode Switcher Pill (Matches Android TopBar)
                Button(action: {
                    onModeChanged(isMusic ? .youtube : .youtubeMusic)
                }) {
                    HStack(spacing: 6) {
                        Image(systemName: isMusic ? "play.fill" : "music.note")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(isMusic ? AppTheme.youTubeRed : AppTheme.ytMusicRedAccent)
                        Text(isMusic ? "Video" : "Music")
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
                    .overlay(
                        RoundedRectangle(cornerRadius: 20)
                            .strokeBorder(LiquidGlassDefaults.borderSpecular, lineWidth: 1)
                    )
                    .shadow(color: Color.black.opacity(0.45), radius: 6, x: 0, y: 3)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(
                LinearGradient(
                    colors: [Color(hex: 0x121320, alpha: 0.85), Color(hex: 0x0C0D16, alpha: 0.65)],
                    startPoint: .top,
                    endPoint: .bottom
                )
            )

            // Specular highlight hairline
            LinearGradient(
                colors: [
                    Color.clear,
                    Color.white.opacity(0.18),
                    Color.white.opacity(0.35),
                    Color.white.opacity(0.18),
                    Color.clear
                ],
                startPoint: .leading,
                endPoint: .trailing
            )
            .frame(height: 0.8)
        }
    }
}
