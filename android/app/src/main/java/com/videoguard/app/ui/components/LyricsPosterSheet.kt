package com.videoguard.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.videoguard.app.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class PosterTheme(val label: String, val bgColors: List<Color>, val textColor: Color, val accentColor: Color) {
    AURA_VIOLET(
        "Aura Violet",
        listOf(Color(0xFF2E0854), Color(0xFF140529), Color(0xFF090214)),
        Color.White,
        Color(0xFFC084FC)
    ),
    MIDNIGHT_OLED(
        "Midnight OLED",
        listOf(Color(0xFF13141F), Color(0xFF0A0B12), Color(0xFF050508)),
        Color.White,
        Color(0xFF38BDF8)
    ),
    SUNSET_NEON(
        "Sunset Neon",
        listOf(Color(0xFF5B1028), Color(0xFF2E0927), Color(0xFF100318)),
        Color.White,
        Color(0xFFFB7185)
    ),
    VINTAGE_PAPER(
        "Vintage Sepia",
        listOf(Color(0xFF2A231C), Color(0xFF1C1712), Color(0xFF0F0C0A)),
        Color(0xFFFDE68A),
        Color(0xFFF59E0B)
    )
}

enum class PosterAspectRatio(val label: String, val ratioText: String, val isStory: Boolean) {
    STORY_9_16("Story (9:16)", "Instagram / WhatsApp", true),
    SQUARE_1_1("Square (1:1)", "Feed Post", false)
}

/**
 * Dynamic Lyrics Poster Maker & Story Share Sheet.
 * Allows users to select memorable lyrics lines and render an aesthetic, high-res
 * vertical poster ready to share to Instagram Stories, WhatsApp Status, or save to gallery.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsPosterSheet(
    track: VideoItem,
    initialLine: String,
    availableLines: List<String> = emptyList(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var selectedLines by remember {
        mutableStateOf(
            if (initialLine.isNotBlank()) listOf(initialLine)
            else availableLines.take(2).ifEmpty { listOf("Listening to ${track.title}") }
        )
    }

    var activeTheme by remember { mutableStateOf(PosterTheme.AURA_VIOLET) }
    var activeRatio by remember { mutableStateOf(PosterAspectRatio.STORY_9_16) }
    var isGeneratingImage by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF20F101A),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.35f)) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF281845))
                            .border(BorderStroke(1.dp, Color(0xFFC084FC).copy(alpha = 0.5f)), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFFC084FC),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "LYRICS STORY MAKER",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Share to Instagram & WhatsApp Status",
                            color = Color(0xFF9E9EAF),
                            fontSize = 11.5.sp
                        )
                    }
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            HorizontalDivider(color = Color(0xFF222234), thickness = 0.8.dp, modifier = Modifier.padding(vertical = 10.dp))

            // Live Interactive Poster Card Preview
            Box(
                modifier = Modifier
                    .width(if (activeRatio.isStory) 240.dp else 260.dp)
                    .aspectRatio(if (activeRatio.isStory) 9f / 16f else 1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.verticalGradient(activeTheme.bgColors))
                    .border(BorderStroke(1.5.dp, activeTheme.accentColor.copy(alpha = 0.5f)), RoundedCornerShape(20.dp))
                    .shadow(24.dp, RoundedCornerShape(20.dp), spotColor = activeTheme.accentColor)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Poster Header: Track Info & Thumbnail
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)), RoundedCornerShape(10.dp))
                        ) {
                            AsyncImage(
                                model = track.thumbnailUrl,
                                contentDescription = track.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = activeTheme.textColor,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                color = activeTheme.textColor.copy(alpha = 0.70f),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = activeTheme.accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Center Selected Lyrics Quote Box
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "“",
                            color = activeTheme.accentColor.copy(alpha = 0.65f),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Serif,
                            lineHeight = 24.sp
                        )

                        selectedLines.forEach { line ->
                            Text(
                                text = line,
                                color = activeTheme.textColor,
                                fontSize = if (activeRatio.isStory) 14.5.sp else 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                                lineHeight = 21.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }

                        Text(
                            text = "”",
                            color = activeTheme.accentColor.copy(alpha = 0.65f),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Serif,
                            lineHeight = 24.sp
                        )
                    }

                    // Poster Footer Branding
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(activeTheme.accentColor)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Auralix Music",
                                color = activeTheme.textColor.copy(alpha = 0.75f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }

                        Text(
                            text = "auralix.app",
                            color = activeTheme.textColor.copy(alpha = 0.45f),
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Theme Options Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "THEME",
                    color = Color(0xFFA5ABC0),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PosterTheme.values().forEach { theme ->
                    val isSelected = activeTheme == theme
                    Surface(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            activeTheme = theme
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) Color(0xFF281E45) else Color(0xFF171724),
                        border = BorderStroke(1.dp, if (isSelected) theme.accentColor else Color.White.copy(alpha = 0.08f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(theme.accentColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = theme.label,
                                color = if (isSelected) Color.White else Color(0xFFB0B3C6),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Aspect Ratio Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PosterAspectRatio.values().forEach { ratio ->
                    val isSelected = activeRatio == ratio
                    Surface(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            activeRatio = ratio
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFF8B5CF6).copy(alpha = 0.22f) else Color(0xFF171724),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFFC084FC) else Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = ratio.label,
                                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = ratio.ratioText,
                                color = if (isSelected) Color(0xFFD8B4FE) else Color(0xFF64748B),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons: Share Story, Copy Quote, Close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Copy Lyrics Quote
                FilledTonalButton(
                    onClick = {
                        val text = "“${selectedLines.joinToString("\n")}”\n— ${track.title} by ${track.artist} on Auralix"
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Lyrics Quote", text))
                        Toast.makeText(context, "Quote copied to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF1E202E),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                // Share to Instagram / WhatsApp / System Sheet
                Button(
                    onClick = {
                        scope.launch {
                            isGeneratingImage = true
                            try {
                                val bitmap = renderPosterBitmap(
                                    context = context,
                                    track = track,
                                    lines = selectedLines,
                                    theme = activeTheme,
                                    isStory = activeRatio.isStory
                                )

                                val cacheDir = File(context.cacheDir, "lyrics_stories").apply { if (!exists()) mkdirs() }
                                val imageFile = File(cacheDir, "lyrics_story_${System.currentTimeMillis()}.png")
                                FileOutputStream(imageFile).use { out ->
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                    out.flush()
                                }

                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    imageFile
                                )

                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "“${selectedLines.joinToString("\n")}”\n🎵 ${track.title} - ${track.artist}\nListening on Auralix"
                                    )
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }

                                context.startActivity(Intent.createChooser(shareIntent, "Share to Story / Status"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error sharing story: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isGeneratingImage = false
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF8B5CF6),
                        contentColor = Color.White
                    ),
                    enabled = !isGeneratingImage,
                    modifier = Modifier.weight(2f).height(50.dp)
                ) {
                    if (isGeneratingImage) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Rendering...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Story / Status", fontSize = 13.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

/**
 * High-definition Canvas Bitmap Renderer (1080x1920 for Stories, 1080x1080 for Square).
 * Renders album art, stylish serif quote typography, and branding ready for Instagram & WhatsApp.
 */
suspend fun renderPosterBitmap(
    context: Context,
    track: VideoItem,
    lines: List<String>,
    theme: PosterTheme,
    isStory: Boolean
): Bitmap = withContext(Dispatchers.IO) {
    val width = 1080
    val height = if (isStory) 1920 else 1080

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // 1. Background Gradient
    val bgPaint = Paint().apply {
        isDither = true
        shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            theme.bgColors.map { it.value.toInt() }.toIntArray(),
            null,
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

    // 2. Fetch Album Artwork Bitmap via Coil
    val imageLoader = ImageLoader(context)
    val request = ImageRequest.Builder(context)
        .data(track.thumbnailUrl)
        .allowHardware(false)
        .build()

    val artBitmap = try {
        val result = (imageLoader.execute(request) as? SuccessResult)?.drawable
        result?.toBitmap(180, 180, Bitmap.Config.ARGB_8888)
    } catch (e: Exception) { null }

    val startY = if (isStory) 240f else 120f

    // 3. Draw Track Artwork & Info
    if (artBitmap != null) {
        val artRect = RectF(90f, startY, 90f + 160f, startY + 160f)
        val artPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawRoundRect(artRect, 28f, 28f, artPaint)
        canvas.drawBitmap(artBitmap, null, artRect, artPaint)
    }

    val textX = if (artBitmap != null) 280f else 90f
    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 46f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText(
        if (track.title.length > 28) track.title.substring(0, 26) + "..." else track.title,
        textX,
        startY + 75f,
        titlePaint
    )

    val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#94A3B8")
        textSize = 34f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    canvas.drawText(
        if (track.artist.length > 34) track.artist.substring(0, 32) + "..." else track.artist,
        textX,
        startY + 130f,
        artistPaint
    )

    // 4. Center Quotation Marks & Lyrics Lines
    val quoteMarkPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#C084FC")
        textSize = 120f
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val centerY = height / 2f - 60f
    canvas.drawText("“", width / 2f, centerY - 80f, quoteMarkPaint)

    val lyricsPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = if (isStory) 58f else 50f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    val joinedText = lines.joinToString("\n")
    val textLayout = StaticLayout.Builder.obtain(
        joinedText, 0, joinedText.length, lyricsPaint, width - 200
    )
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(16f, 1.15f)
        .build()

    canvas.save()
    canvas.translate(100f, centerY - (textLayout.height / 2f))
    textLayout.draw(canvas)
    canvas.restore()

    canvas.drawText("”", width / 2f, centerY + (textLayout.height / 2f) + 100f, quoteMarkPaint)

    // 5. Footer Branding
    val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#CBD5E1")
        textSize = 32f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val footerY = height - 120f
    canvas.drawCircle(100f, footerY - 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#A855F7")
    })
    canvas.drawText("Auralix Music • Played with high-fidelity", 130f, footerY, footerPaint)

    bitmap
}
