package com.videoguard.app.ui.theme

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.videoguard.app.model.VideoItem
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

data class AmbientPalette(
    val primary: Color,
    val secondary: Color,
    val deep: Color
)

object ArtworkColorExtractor {
    private val cache = ConcurrentHashMap<String, AmbientPalette>()

    fun getPaletteSync(track: VideoItem): AmbientPalette {
        return cache[track.id] ?: getFallbackPalette(track.id)
    }

    fun getFallbackPalette(id: String): AmbientPalette {
        val hash = id.hashCode()
        val hue = ((hash and 0x7FFFFFFF) % 360).toFloat()
        // Vibrant, luxury jewel tones
        val primary = Color.hsl(hue = hue, saturation = 0.72f, lightness = 0.32f)
        val secondary = Color.hsl(hue = (hue + 28f) % 360f, saturation = 0.58f, lightness = 0.16f)
        val deep = Color(0xFF060709)
        return AmbientPalette(primary, secondary, deep)
    }

    suspend fun extractPalette(context: Context, track: VideoItem): AmbientPalette {
        cache[track.id]?.let { return it }
        val fallback = getFallbackPalette(track.id)
        if (track.thumbnailUrl.isBlank()) return fallback

        return try {
            val imageLoader = Coil.imageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(track.thumbnailUrl)
                .allowHardware(false)
                .size(48, 48)
                .build()
            val drawable = (imageLoader.execute(request) as? SuccessResult)?.drawable
            val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: return fallback

            val width = bitmap.width
            val height = bitmap.height
            if (width <= 0 || height <= 0) return fallback

            var bestScore = -1f
            var bestColor = android.graphics.Color.DKGRAY
            var avgR = 0L
            var avgG = 0L
            var avgB = 0L
            var count = 0

            val stepX = (width / 16).coerceAtLeast(1)
            val stepY = (height / 16).coerceAtLeast(1)

            val hsv = FloatArray(3)
            for (x in 0 until width step stepX) {
                for (y in 0 until height step stepY) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = android.graphics.Color.red(pixel)
                    val g = android.graphics.Color.green(pixel)
                    val b = android.graphics.Color.blue(pixel)

                    android.graphics.Color.colorToHSV(pixel, hsv)
                    val sat = hsv[1]
                    val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f

                    // High saturation and medium luminance = vibrant artwork colors
                    if (lum in 0.18f..0.85f && sat > 0.20f) {
                        val score = sat * 1.6f + (1f - abs(lum - 0.5f))
                        if (score > bestScore) {
                            bestScore = score
                            bestColor = pixel
                        }
                    }
                    avgR += r
                    avgG += g
                    avgB += b
                    count++
                }
            }

            val finalColor = if (bestScore > 0f) {
                bestColor
            } else if (count > 0) {
                android.graphics.Color.rgb((avgR / count).toInt(), (avgG / count).toInt(), (avgB / count).toInt())
            } else {
                return fallback
            }

            android.graphics.Color.colorToHSV(finalColor, hsv)
            val primary = Color.hsl(
                hue = hsv[0],
                saturation = hsv[1].coerceIn(0.60f, 0.90f),
                lightness = 0.32f
            )
            val secondary = Color.hsl(
                hue = (hsv[0] + 24f) % 360f,
                saturation = (hsv[1] * 0.80f).coerceIn(0.40f, 0.75f),
                lightness = 0.15f
            )
            val deep = Color(0xFF060709)

            val palette = AmbientPalette(primary, secondary, deep)
            cache[track.id] = palette
            palette
        } catch (e: Exception) {
            fallback
        }
    }
}
