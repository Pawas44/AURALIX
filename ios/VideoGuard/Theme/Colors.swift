import SwiftUI

extension Color {
    init(hex: UInt, alpha: Double = 1.0) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255.0,
            green: Double((hex >> 8) & 0xFF) / 255.0,
            blue: Double(hex & 0xFF) / 255.0,
            opacity: alpha
        )
    }

    init(argbHex: UInt) {
        let a = Double((argbHex >> 24) & 0xFF) / 255.0
        let r = Double((argbHex >> 16) & 0xFF) / 255.0
        let g = Double((argbHex >> 8) & 0xFF) / 255.0
        let b = Double(argbHex & 0xFF) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: a > 0 ? a : 1.0)
    }
}

struct AppTheme {
    // YouTube Mode Colors
    static let youTubeRed = Color(hex: 0xFF0000)
    static let youTubeDarkBg = Color(hex: 0x0F0F0F)
    static let youTubeCardBg = Color(hex: 0x1E1E1E)
    static let youTubeChipBg = Color(hex: 0x272727)
    static let youTubeChipSelectedBg = Color(hex: 0xF1F1F1)
    static let youTubeChipSelectedText = Color(hex: 0x0F0F0F)
    static let youTubeTextPrimary = Color(hex: 0xF1F1F1)
    static let youTubeTextSecondary = Color(hex: 0xAAAAAA)
    static let youTubeBorder = Color(hex: 0x303030)

    // YouTube Music Mode Colors
    static let ytMusicBlack = Color(hex: 0x030303)
    static let ytMusicSurface = Color(hex: 0x121212)
    static let ytMusicCardBg = Color(hex: 0x1D1D1D)
    static let ytMusicRedAccent = Color(hex: 0xFF0033)
    static let ytMusicChipBg = Color(hex: 0x212121)
    static let ytMusicChipActiveBg = Color(hex: 0xFFFFFF)
    static let ytMusicChipActiveText = Color(hex: 0x030303)
    static let ytMusicTextPrimary = Color(hex: 0xFFFFFF)
    static let ytMusicTextSecondary = Color(hex: 0x909090)
    static let ytMusicProgressBar = Color(hex: 0xFF0033)
    static let ytMusicGlassOverlay = Color(hex: 0x101010, alpha: 0.6)
}

struct LiquidGlassDefaults {
    static let glassBaseDark = Color(hex: 0x0E0F18, alpha: 0.80)
    static let glassCardBg = Color(hex: 0x171825, alpha: 0.60)
    static let glassPillBg = Color.white.opacity(0.13)
    static let glassPillSelected = Color.white.opacity(0.25)

    static let borderSpecular = LinearGradient(
        colors: [
            Color.white.opacity(0.42),
            Color.white.opacity(0.06),
            Color.white.opacity(0.22)
        ],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )

    static let borderSubtle = LinearGradient(
        colors: [
            Color.white.opacity(0.22),
            Color.white.opacity(0.04),
            Color.white.opacity(0.14)
        ],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )

    static let borderLuminous = LinearGradient(
        colors: [
            Color.white.opacity(0.75),
            Color.white.opacity(0.20),
            Color.white.opacity(0.50)
        ],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )

    static let liquidCanvas = LinearGradient(
        colors: [
            Color(hex: 0x0E0F18),
            Color(hex: 0x090A11),
            Color(hex: 0x06060B)
        ],
        startPoint: .top,
        endPoint: .bottom
    )

    static let liquidMusicCanvas = LinearGradient(
        colors: [
            Color(hex: 0x160B1A),
            Color(hex: 0x0B0711),
            Color(hex: 0x050308)
        ],
        startPoint: .top,
        endPoint: .bottom
    )
}

extension View {
    func liquidGlass(
        cornerRadius: CGFloat = 18,
        backgroundColor: Color = LiquidGlassDefaults.glassBaseDark,
        borderWidth: CGFloat = 1
    ) -> some View {
        self
            .background(
                LinearGradient(
                    colors: [
                        backgroundColor.opacity(0.82),
                        backgroundColor.opacity(0.94)
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
            )
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius)
                    .strokeBorder(LiquidGlassDefaults.borderSpecular, lineWidth: borderWidth)
            )
            .shadow(color: Color.black.opacity(0.45), radius: 8, x: 0, y: 4)
    }

    func liquidGlassPill(
        isSelected: Bool = false,
        activeColor: Color = Color(hex: 0xFF0033)
    ) -> some View {
        let bg = isSelected
            ? LinearGradient(
                colors: [activeColor.opacity(0.35), activeColor.opacity(0.15)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            : LinearGradient(
                colors: [Color.white.opacity(0.10), Color.white.opacity(0.03)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )

        let border = isSelected
            ? LinearGradient(
                colors: [Color.white.opacity(0.70), activeColor.opacity(0.85), Color.white.opacity(0.45)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            : LiquidGlassDefaults.borderSubtle

        return self
            .background(bg)
            .clipShape(RoundedRectangle(cornerRadius: 20))
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .strokeBorder(border, lineWidth: 1)
            )
    }
}
