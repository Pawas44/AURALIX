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

