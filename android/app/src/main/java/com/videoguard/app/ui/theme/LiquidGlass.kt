package com.videoguard.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object LiquidGlassDefaults {
    // Glass surface colors
    val GlassBaseDark = Color(0xCC0E0F18)
    val GlassCardBg = Color(0x99171825)
    val GlassPillBg = Color(0x22FFFFFF)
    val GlassPillSelected = Color(0x40FFFFFF)

    // Specular highlight borders
    val BorderSpecular = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.42f),
            Color.White.copy(alpha = 0.06f),
            Color.White.copy(alpha = 0.22f)
        )
    )

    val BorderSubtle = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.22f),
            Color.White.copy(alpha = 0.04f),
            Color.White.copy(alpha = 0.14f)
        )
    )

    val BorderLuminous = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.75f),
            Color.White.copy(alpha = 0.20f),
            Color.White.copy(alpha = 0.50f)
        )
    )

    // Ambient liquid canvas gradients
    val LiquidCanvas = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0E0F18),
            Color(0xFF090A11),
            Color(0xFF06060B)
        )
    )

    val LiquidMusicCanvas = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF160B1A),
            Color(0xFF0B0711),
            Color(0xFF050308)
        )
    )
}

fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(18.dp),
    backgroundColor: Color = LiquidGlassDefaults.GlassBaseDark,
    borderBrush: Brush = LiquidGlassDefaults.BorderSpecular,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 6.dp
): Modifier = this
    .shadow(
        elevation = elevation,
        shape = shape,
        ambientColor = Color.Black.copy(alpha = 0.45f),
        spotColor = Color.Black.copy(alpha = 0.55f)
    )
    .clip(shape)
    .background(
        brush = Brush.verticalGradient(
            colors = listOf(
                backgroundColor.copy(alpha = 0.80f),
                backgroundColor.copy(alpha = 0.94f)
            )
        )
    )
    .border(
        width = borderWidth,
        brush = borderBrush,
        shape = shape
    )

fun Modifier.liquidGlassPill(
    isSelected: Boolean = false,
    activeColor: Color = Color(0xFFFF0033)
): Modifier {
    val shape = RoundedCornerShape(20.dp)
    val bg = if (isSelected) {
        Brush.linearGradient(
            listOf(
                activeColor.copy(alpha = 0.35f),
                activeColor.copy(alpha = 0.15f)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.10f),
                Color.White.copy(alpha = 0.03f)
            )
        )
    }
    val border = if (isSelected) {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.70f),
                activeColor.copy(alpha = 0.85f),
                Color.White.copy(alpha = 0.45f)
            )
        )
    } else {
        LiquidGlassDefaults.BorderSubtle
    }
    return this
        .clip(shape)
        .background(bg)
        .border(1.dp, border, shape)
}
