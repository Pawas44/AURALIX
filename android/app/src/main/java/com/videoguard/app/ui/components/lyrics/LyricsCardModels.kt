package com.videoguard.app.ui.components.lyrics

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

/**
 * 5 distinct aesthetic editorial templates for Lyrics Sharing Cards.
 */
enum class LyricsCardTemplate(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconEmoji: String,
    val accentColor: Color
) {
    MINIMAL_DARK(
        id = "minimal_dark",
        title = "Minimal Dark",
        subtitle = "Clean Swiss typography on deep pitch black",
        iconEmoji = "🖤",
        accentColor = Color(0xFFE2E8F0)
    ),
    CINEMATIC_BLUR(
        id = "cinematic_blur",
        title = "Cinematic Blur",
        subtitle = "Atmospheric vignetted blur of album art",
        iconEmoji = "🎬",
        accentColor = Color(0xFF38BDF8)
    ),
    GLASSMORPHISM(
        id = "glassmorphism",
        title = "Glassmorphism",
        subtitle = "Frosted translucent luxury glass card",
        iconEmoji = "💎",
        accentColor = Color(0xFFC084FC)
    ),
    EDITORIAL(
        id = "editorial",
        title = "Editorial Magazine",
        subtitle = "High-fashion music magazine with serif quotes",
        iconEmoji = "📰",
        accentColor = Color(0xFFFBBF24)
    ),
    AMBIENT_GRADIENT(
        id = "ambient_gradient",
        title = "Ambient Gradient",
        subtitle = "Dynamic glowing aura extracted from artwork",
        iconEmoji = "🌈",
        accentColor = Color(0xFFF43F5E)
    )
}

enum class LyricsFontSize(val label: String, val spSize: Float) {
    SMALL("Small", 22f),
    MEDIUM("Medium", 28f),
    LARGE("Large", 34f)
}

enum class LyricsAlignment(val label: String, val composeAlign: TextAlign) {
    LEFT("Left", TextAlign.Start),
    CENTER("Center", TextAlign.Center)
}

/**
 * Apple & Instagram Story-inspired aesthetic typography fonts.
 */
enum class LyricsFont(
    val id: String,
    val displayName: String,
    val previewSample: String,
    val composeFontFamily: FontFamily,
    val composeFontWeight: FontWeight,
    val composeFontStyle: FontStyle = FontStyle.Normal,
    val letterSpacingSp: Float = 0f,
    val isAllCaps: Boolean = false
) {
    SF_PRO(
        id = "sf_pro",
        displayName = "SF Pro",
        previewSample = "Aa",
        composeFontFamily = FontFamily.SansSerif,
        composeFontWeight = FontWeight.Bold,
        letterSpacingSp = -0.5f
    ),
    NEW_YORK(
        id = "new_york",
        displayName = "New York",
        previewSample = "Aa",
        composeFontFamily = FontFamily.Serif,
        composeFontWeight = FontWeight.SemiBold,
        composeFontStyle = FontStyle.Italic,
        letterSpacingSp = -0.2f
    ),
    SF_ROUNDED(
        id = "sf_rounded",
        displayName = "SF Rounded",
        previewSample = "Aa",
        composeFontFamily = FontFamily.SansSerif,
        composeFontWeight = FontWeight.SemiBold,
        letterSpacingSp = 0.2f
    ),
    SIGNATURE(
        id = "signature",
        displayName = "Signature",
        previewSample = "Aa",
        composeFontFamily = FontFamily.Cursive,
        composeFontWeight = FontWeight.Medium,
        composeFontStyle = FontStyle.Italic
    ),
    HEADLINE(
        id = "headline",
        displayName = "Headline",
        previewSample = "AA",
        composeFontFamily = FontFamily.SansSerif,
        composeFontWeight = FontWeight.ExtraBold,
        letterSpacingSp = 1.2f,
        isAllCaps = true
    ),
    SF_MONO(
        id = "sf_mono",
        displayName = "SF Mono",
        previewSample = "Aa",
        composeFontFamily = FontFamily.Monospace,
        composeFontWeight = FontWeight.Medium,
        letterSpacingSp = 0.5f
    )
}

data class LyricsCardConfig(
    val template: LyricsCardTemplate = LyricsCardTemplate.CINEMATIC_BLUR,
    val font: LyricsFont = LyricsFont.SF_PRO,
    val alignment: LyricsAlignment = LyricsAlignment.LEFT,
    val fontSize: LyricsFontSize = LyricsFontSize.MEDIUM,
    val showThumbnail: Boolean = true,
    val showArtistInfo: Boolean = true,
    val showBranding: Boolean = true,
    val deepContrast: Boolean = false
)
