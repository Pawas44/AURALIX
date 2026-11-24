package com.videoguard.app.ui.components.lyrics

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.videoguard.app.model.VideoItem
import com.videoguard.app.ui.theme.AmbientPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

object LyricsCardRenderer {

    const val CARD_WIDTH = 1080
    const val CARD_HEIGHT = 1920

    /**
     * Renders a static, production-grade 1080x1920 bitmap for the selected lyrics and configuration.
     */
    suspend fun renderToBitmap(
        context: Context,
        track: VideoItem,
        lyricsLines: List<String>,
        config: LyricsCardConfig,
        palette: AmbientPalette
    ): Bitmap = withContext(Dispatchers.IO) {
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Fetch album artwork bitmap via Coil
        val artBitmap = loadArtworkBitmap(context, track)

        // 2. Render background based on selected aesthetic template
        renderBackground(canvas, artBitmap, config, palette)

        // 3. Render content elements (Quote, Lyrics Text, Metadata, Branding)
        renderForegroundContent(canvas, track, artBitmap, lyricsLines, config, palette)

        bitmap
    }

    private suspend fun loadArtworkBitmap(context: Context, track: VideoItem): Bitmap? {
        val artUrl = com.videoguard.app.data.ArtworkUrlHelper.getHighResArtworkUrl(track.thumbnailUrl, track.id)
        return try {
            val loader = Coil.imageLoader(context)
            val req = ImageRequest.Builder(context)
                .data(artUrl)
                .allowHardware(false)
                .size(1080, 1080)
                .build()
            val res = loader.execute(req)
            if (res is SuccessResult) {
                (res.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun renderBackground(
        canvas: Canvas,
        artBitmap: Bitmap?,
        config: LyricsCardConfig,
        palette: AmbientPalette
    ) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        when (config.template) {
            LyricsCardTemplate.MINIMAL_DARK -> {
                // Pure Deep OLED Black with gentle ambient aura
                bgPaint.color = android.graphics.Color.parseColor("#090A0E")
                canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), bgPaint)

                // Very subtle radial ambient glow from palette at center
                val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    val pColor = toAndroidColor(palette.primary, 40)
                    shader = RadialGradient(
                        CARD_WIDTH * 0.5f, CARD_HEIGHT * 0.45f,
                        CARD_WIDTH * 0.85f,
                        intArrayOf(pColor, android.graphics.Color.TRANSPARENT),
                        floatArrayOf(0f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), auraPaint)
            }

            LyricsCardTemplate.CINEMATIC_BLUR -> {
                if (artBitmap != null) {
                    drawScaledBlurredBitmap(canvas, artBitmap, blurRadius = 25f)
                } else {
                    bgPaint.color = android.graphics.Color.parseColor("#11131A")
                    canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), bgPaint)
                }

                // Atmospheric cinematic vignette overlay
                val dimAlpha = if (config.deepContrast) 235 else 205
                val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, CARD_HEIGHT.toFloat(),
                        intArrayOf(
                            android.graphics.Color.argb(160, 5, 6, 9),
                            android.graphics.Color.argb(dimAlpha, 5, 6, 9),
                            android.graphics.Color.argb(250, 4, 4, 6)
                        ),
                        floatArrayOf(0f, 0.45f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), vignettePaint)
            }

            LyricsCardTemplate.GLASSMORPHISM -> {
                if (artBitmap != null) {
                    drawScaledBlurredBitmap(canvas, artBitmap, blurRadius = 22f)
                } else {
                    bgPaint.color = android.graphics.Color.parseColor("#0D0E17")
                    canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), bgPaint)
                }

                // Dark base gradient
                val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, CARD_HEIGHT.toFloat(),
                        intArrayOf(
                            android.graphics.Color.argb(140, 10, 12, 20),
                            android.graphics.Color.argb(210, 8, 9, 15),
                            android.graphics.Color.argb(245, 4, 5, 8)
                        ),
                        null,
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), scrimPaint)

                // Frosted Glass Center Card Floating Container
                val cardMarginH = 64f
                val cardMarginTop = 220f
                val cardMarginBottom = 260f
                val glassRect = RectF(cardMarginH, cardMarginTop, CARD_WIDTH - cardMarginH, CARD_HEIGHT - cardMarginBottom)

                val glassFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb(32, 255, 255, 255)
                }
                canvas.drawRoundRect(glassRect, 48f, 48f, glassFillPaint)

                // Glass Specular Border Highlight
                val glassStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 3.5f
                    shader = LinearGradient(
                        glassRect.left, glassRect.top,
                        glassRect.right, glassRect.bottom,
                        intArrayOf(
                            android.graphics.Color.argb(120, 255, 255, 255),
                            android.graphics.Color.argb(25, 255, 255, 255),
                            android.graphics.Color.argb(60, 255, 255, 255)
                        ),
                        floatArrayOf(0f, 0.6f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRoundRect(glassRect, 48f, 48f, glassStrokePaint)
            }

            LyricsCardTemplate.EDITORIAL -> {
                // Luxury Off-Black Editorial Studio Background with warm tint
                bgPaint.color = android.graphics.Color.parseColor("#0C0B0E")
                canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), bgPaint)

                // Subtle warm gradient accent at bottom
                val editorialGradient = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    val warmAccent = toAndroidColor(palette.primary, 55)
                    shader = LinearGradient(
                        0f, CARD_HEIGHT * 0.4f, 0f, CARD_HEIGHT.toFloat(),
                        intArrayOf(android.graphics.Color.TRANSPARENT, warmAccent, android.graphics.Color.parseColor("#09080B")),
                        floatArrayOf(0f, 0.7f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), editorialGradient)
            }

            LyricsCardTemplate.AMBIENT_GRADIENT -> {
                // Vibrant, high-end multi-stop ambient aura mesh
                val primaryC = toAndroidColor(palette.primary, 230)
                val secondaryC = toAndroidColor(palette.secondary, 200)
                val deepC = toAndroidColor(palette.deep, 255)

                // 1. Base linear fill
                val baseLinear = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(),
                        intArrayOf(secondaryC, deepC, android.graphics.Color.parseColor("#06070A")),
                        floatArrayOf(0f, 0.55f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), baseLinear)

                // 2. High-glow radial aura behind text
                val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        CARD_WIDTH * 0.5f, CARD_HEIGHT * 0.40f,
                        CARD_WIDTH * 0.95f,
                        intArrayOf(primaryC, android.graphics.Color.TRANSPARENT),
                        floatArrayOf(0f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), auraPaint)

                // 3. Contrast dark scrim for readability
                val dimAlpha = if (config.deepContrast) 180 else 130
                val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb(dimAlpha, 5, 6, 9)
                }
                canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), scrimPaint)
            }
        }
    }

    private fun renderForegroundContent(
        canvas: Canvas,
        track: VideoItem,
        artBitmap: Bitmap?,
        lyricsLines: List<String>,
        config: LyricsCardConfig,
        palette: AmbientPalette
    ) {
        val isGlass = config.template == LyricsCardTemplate.GLASSMORPHISM
        val isEditorial = config.template == LyricsCardTemplate.EDITORIAL

        val contentLeft = if (isGlass) 120f else 96f
        val contentRight = CARD_WIDTH - contentLeft
        val contentWidth = (contentRight - contentLeft).toInt()

        // 1. Editorial Quotation Mark / Stylized Header
        val quotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = if (isEditorial) 160f else 110f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            color = when (config.template) {
                LyricsCardTemplate.EDITORIAL -> android.graphics.Color.parseColor("#FBBF24")
                LyricsCardTemplate.GLASSMORPHISM -> android.graphics.Color.parseColor("#C084FC")
                LyricsCardTemplate.AMBIENT_GRADIENT -> toAndroidColor(palette.primary, 240)
                else -> android.graphics.Color.argb(160, 255, 255, 255)
            }
        }
        val quoteY = if (isGlass) 380f else 320f
        val quoteX = if (config.alignment == LyricsAlignment.CENTER) {
            (CARD_WIDTH / 2f) - (quotePaint.measureText("“") / 2f)
        } else {
            contentLeft
        }
        canvas.drawText("“", quoteX, quoteY, quotePaint)

        // 2. Lyrics Text Layout
        val formattedLines = if (config.font.isAllCaps) lyricsLines.map { it.uppercase() } else lyricsLines
        val lyricsText = formattedLines.joinToString("\n\n")
        val baseSp = config.fontSize.spSize
        val pxSize = baseSp * 2.8f // Convert SP to 1080p scale

        val lyricsPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = pxSize
            typeface = resolveNativeTypeface(config.font)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && config.font.letterSpacingSp != 0f) {
                letterSpacing = config.font.letterSpacingSp / 14f
            }
            color = android.graphics.Color.WHITE
            setShadowLayer(8f, 0f, 4f, android.graphics.Color.argb(160, 0, 0, 0))
        }

        val textAlignment = when (config.alignment) {
            LyricsAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
            LyricsAlignment.LEFT -> Layout.Alignment.ALIGN_NORMAL
        }

        val staticLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(lyricsText, 0, lyricsText.length, lyricsPaint, contentWidth)
                .setAlignment(textAlignment)
                .setLineSpacing(16f, 1.25f)
                .setIncludePad(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(lyricsText, lyricsPaint, contentWidth, textAlignment, 1.25f, 16f, true)
        }

        canvas.save()
        val textY = quoteY + 60f
        canvas.translate(contentLeft, textY)
        staticLayout.draw(canvas)
        canvas.restore()

        // 3. Song Metadata (Thumbnail + Title + Artist)
        val metaY = CARD_HEIGHT - 360f

        if (config.showArtistInfo) {
            var textStartX = contentLeft

            // Optional Thumbnail
            if (config.showThumbnail && artBitmap != null) {
                val thumbSize = 130f
                val thumbRect = RectF(contentLeft, metaY, contentLeft + thumbSize, metaY + thumbSize)
                drawRoundedThumbnail(canvas, artBitmap, thumbRect, 24f)
                textStartX += thumbSize + 32f
            }

            // Title
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 42f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                color = android.graphics.Color.WHITE
                setShadowLayer(4f, 0f, 2f, android.graphics.Color.argb(180, 0, 0, 0))
            }
            val titleTruncated = truncateString(track.title, titlePaint, CARD_WIDTH - textStartX - contentLeft)
            canvas.drawText(titleTruncated, textStartX, metaY + 54f, titlePaint)

            // Artist Name
            val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 34f
                typeface = if (isEditorial) {
                    Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                } else {
                    Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                }
                color = android.graphics.Color.argb(205, 255, 255, 255)
            }
            val artistText = track.artist.ifBlank { track.channelName }.ifBlank { "Auralix Music" }
            val artistTruncated = truncateString(artistText, artistPaint, CARD_WIDTH - textStartX - contentLeft)
            canvas.drawText(artistTruncated, textStartX, metaY + 104f, artistPaint)
        }

        // 4. Subtle Minimalist Auralix Branding
        if (config.showBranding) {
            val brandY = CARD_HEIGHT - 120f
            val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 30f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                color = android.graphics.Color.argb(140, 255, 255, 255)
                letterSpacing = 0.25f
            }
            val brandText = "♫  A U R A L I X"
            val brandX = (CARD_WIDTH - brandPaint.measureText(brandText)) / 2f
            canvas.drawText(brandText, brandX, brandY, brandPaint)
        }
    }

    private fun drawScaledBlurredBitmap(canvas: Canvas, src: Bitmap, blurRadius: Float) {
        // High-quality crop & scale to fill 1080x1920
        val srcW = src.width.toFloat()
        val srcH = src.height.toFloat()
        val scale = max(CARD_WIDTH / srcW, CARD_HEIGHT / srcH)
        val scaledW = srcW * scale
        val scaledH = srcH * scale
        val left = (CARD_WIDTH - scaledW) / 2f
        val top = (CARD_HEIGHT - scaledH) / 2f

        val destRect = RectF(left, top, left + scaledW, top + scaledH)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        // Downscale before drawing to create smooth hardware-free bokeh blur
        val sampleSize = 8
        val small = Bitmap.createScaledBitmap(src, max(1, src.width / sampleSize), max(1, src.height / sampleSize), true)
        canvas.drawBitmap(small, null, destRect, paint)
    }

    private fun drawRoundedThumbnail(canvas: Canvas, src: Bitmap, rect: RectF, cornerRadius: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val path = Path().apply {
            addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(src, null, rect, paint)
        canvas.restore()

        // Border around thumbnail
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = android.graphics.Color.argb(80, 255, 255, 255)
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)
    }

    private fun truncateString(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var truncated = text
        while (truncated.isNotEmpty() && paint.measureText("$truncated…") > maxWidth) {
            truncated = truncated.dropLast(1)
        }
        return "$truncated…"
    }

    private fun toAndroidColor(composeColor: androidx.compose.ui.graphics.Color, alpha: Int = 255): Int {
        val r = (composeColor.red * 255).toInt().coerceIn(0, 255)
        val g = (composeColor.green * 255).toInt().coerceIn(0, 255)
        val b = (composeColor.blue * 255).toInt().coerceIn(0, 255)
        return android.graphics.Color.argb(alpha, r, g, b)
    }

    /**
     * Saves the 1080x1920 card directly into device gallery MediaStore.
     */
    suspend fun saveToGallery(context: Context, bitmap: Bitmap, trackTitle: String): Uri? = withContext(Dispatchers.IO) {
        val filename = "Auralix_${trackTitle.replace(Regex("[^a-zA-Z0-9]"), "_").take(24)}_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Auralix")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        if (uri != null) {
            try {
                resolver.openOutputStream(uri)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved to Gallery (Pictures/Auralix) ✨", Toast.LENGTH_SHORT).show()
                }
                return@withContext uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to save card: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        null
    }

    /**
     * Writes image to cache and launches native Android Sharesheet with Instagram / WhatsApp support.
     */
    suspend fun shareLyricsCard(context: Context, bitmap: Bitmap, track: VideoItem) = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "lyrics_cards").apply { mkdirs() }
        val shareFile = File(cacheDir, "lyrics_card_${System.currentTimeMillis()}.png")

        try {
            FileOutputStream(shareFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                shareFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "“${track.title}” by ${track.artist}\nListen on Auralix 🎵 https://youtu.be/${track.id}"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            withContext(Dispatchers.Main) {
                val chooser = Intent.createChooser(shareIntent, "Share Lyrics Card").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Sharing failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resolveNativeTypeface(font: LyricsFont): Typeface {
        return when (font) {
            LyricsFont.MODERN -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            LyricsFont.EDITORIAL -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
            LyricsFont.SIGNATURE -> Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
            LyricsFont.HEADLINE -> Typeface.create("sans-serif-condensed", Typeface.BOLD)
            LyricsFont.TYPEWRITER -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
    }
}
