package com.videoguard.app.ui.components.lyrics

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.videoguard.app.model.VideoItem
import com.videoguard.app.ui.theme.AmbientPalette
import com.videoguard.app.ui.theme.ArtworkColorExtractor
import kotlinx.coroutines.launch

/**
 * Premium Full-Screen Editorial Lyrics Card Editor.
 * Features:
 * - Large 9:16 live rendered card preview matching exact 1080x1920 export
 * - 5 luxury templates with instant preview update
 * - Customization toolbar (Alignment, Font Size, Contrast, Info Toggles)
 * - 1-Tap Share to Instagram Stories / WhatsApp Status
 * - 1-Tap Save to Device Gallery
 */
@Composable
fun LyricsCardEditorDialog(
    track: VideoItem,
    selectedLines: List<String>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var config by remember { mutableStateOf(LyricsCardConfig()) }
    var isExporting by remember { mutableStateOf(false) }

    val palette = remember(track.id) {
        ArtworkColorExtractor.getPaletteSync(track)
    }

    LaunchedEffect(track.id) {
        ArtworkColorExtractor.extractPalette(context, track)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF506070B))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Lyrics Story Card",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${selectedLines.size} lines selected",
                            color = Color(0xFF9E9EAF),
                            fontSize = 12.sp
                        )
                    }

                    Box(modifier = Modifier.size(38.dp)) // Alignment spacer
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Centered 9:16 Live Card Preview Container - Responsive & Screen-fitted
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 56.dp)
                        .heightIn(max = 350.dp)
                        .aspectRatio(9f / 16f)
                        .shadow(elevation = 20.dp, shape = RoundedCornerShape(22.dp), spotColor = config.template.accentColor.copy(alpha = 0.40f))
                        .clip(RoundedCornerShape(22.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(22.dp))
                ) {
                    LiveLyricsCardPreview(
                        track = track,
                        lines = selectedLines,
                        config = config,
                        palette = palette,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Exporting Loading Scrim
                    if (isExporting) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.75f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(0xFFC084FC), strokeWidth = 3.dp)
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Rendering 1080p Card…",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 1. Instagram-Style Typography Selector
                Text(
                    text = "FONT STYLE",
                    color = Color(0xFFA1A1AA),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LyricsFont.values().forEach { fontOption ->
                        val isSel = config.font == fontOption
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSel) Color.White.copy(alpha = 0.22f)
                                    else Color.White.copy(alpha = 0.05f)
                                )
                                .border(
                                    width = if (isSel) 1.5.dp else 0.8.dp,
                                    color = if (isSel) Color.White else Color.White.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    config = config.copy(font = fontOption)
                                }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = fontOption.previewSample,
                                    fontFamily = fontOption.composeFontFamily,
                                    fontWeight = fontOption.composeFontWeight,
                                    fontStyle = fontOption.composeFontStyle,
                                    fontSize = 15.sp,
                                    color = if (isSel) Color.White else Color(0xFFD4D4D8)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = fontOption.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) Color.White else Color(0xFFA1A1AA)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Template Selector Bar (Horizontal Scroll)
                Text(
                    text = "AESTHETIC TEMPLATE",
                    color = Color(0xFFA1A1AA),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LyricsCardTemplate.values().forEach { tmpl ->
                        val isSelected = config.template == tmpl
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSelected) tmpl.accentColor.copy(alpha = 0.24f)
                                    else Color.White.copy(alpha = 0.05f)
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) tmpl.accentColor else Color.White.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    config = config.copy(template = tmpl)
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = tmpl.iconEmoji, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = tmpl.title,
                                    color = if (isSelected) Color.White else Color(0xFFD4D4D8),
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Customization Controls Row (Alignment, Font Size, Contrast)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Alignment Toggle
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(0.8.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            .padding(2.dp)
                    ) {
                        IconButton(
                            onClick = { config = config.copy(alignment = LyricsAlignment.LEFT) },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (config.alignment == LyricsAlignment.LEFT) Color.White.copy(alpha = 0.20f) else Color.Transparent)
                        ) {
                            Icon(Icons.Default.FormatAlignLeft, contentDescription = "Left", tint = Color.White, modifier = Modifier.size(17.dp))
                        }
                        IconButton(
                            onClick = { config = config.copy(alignment = LyricsAlignment.CENTER) },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (config.alignment == LyricsAlignment.CENTER) Color.White.copy(alpha = 0.20f) else Color.Transparent)
                        ) {
                            Icon(Icons.Default.FormatAlignCenter, contentDescription = "Center", tint = Color.White, modifier = Modifier.size(17.dp))
                        }
                    }

                    // Font Size Selector (S, M, L)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(0.8.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        LyricsFontSize.values().forEach { sizeOption ->
                            val isSel = config.fontSize == sizeOption
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) Color.White.copy(alpha = 0.22f) else Color.Transparent)
                                    .clickable { config = config.copy(fontSize = sizeOption) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = sizeOption.label.first().toString(),
                                    color = if (isSel) Color.White else Color(0xFFA1A1AA),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Info Toggle: Thumbnail & Branding
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(0.8.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            .padding(2.dp)
                    ) {
                        IconButton(
                            onClick = { config = config.copy(showThumbnail = !config.showThumbnail) },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (config.showThumbnail) Color.White.copy(alpha = 0.20f) else Color.Transparent)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = "Art", tint = if (config.showThumbnail) Color(0xFF38BDF8) else Color(0xFF71717A), modifier = Modifier.size(17.dp))
                        }
                        IconButton(
                            onClick = { config = config.copy(deepContrast = !config.deepContrast) },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (config.deepContrast) Color.White.copy(alpha = 0.20f) else Color.Transparent)
                        ) {
                            Icon(Icons.Default.Contrast, contentDescription = "Contrast", tint = if (config.deepContrast) Color(0xFFFBBF24) else Color(0xFF71717A), modifier = Modifier.size(17.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Bottom Action Buttons (Save Image & Share Story)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Save to Gallery Button
                    OutlinedButton(
                        onClick = {
                            if (isExporting) return@OutlinedButton
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            scope.launch {
                                isExporting = true
                                val bmp = LyricsCardRenderer.renderToBitmap(context, track, selectedLines, config, palette)
                                LyricsCardRenderer.saveToGallery(context, bmp, track.title)
                                isExporting = false
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Save", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // 2. Share Story Button (Instagram / WhatsApp)
                    Button(
                        onClick = {
                            if (isExporting) return@Button
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            scope.launch {
                                isExporting = true
                                val bmp = LyricsCardRenderer.renderToBitmap(context, track, selectedLines, config, palette)
                                LyricsCardRenderer.shareLyricsCard(context, bmp, track)
                                isExporting = false
                            }
                        },
                        modifier = Modifier
                            .weight(1.5f)
                            .height(52.dp)
                            .shadow(12.dp, RoundedCornerShape(16.dp), spotColor = Color(0xFFEC4899)),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent
                        ),
                        contentPadding = PaddingValues()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFFF43F5E))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Share to Story",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Live, high-fidelity Compose card preview matching the 1080x1920 export.
 */
@Composable
private fun LiveLyricsCardPreview(
    track: VideoItem,
    lines: List<String>,
    config: LyricsCardConfig,
    palette: AmbientPalette,
    modifier: Modifier = Modifier
) {
    val artUrl = com.videoguard.app.data.ArtworkUrlHelper.getHighResArtworkUrl(track.thumbnailUrl, track.id)
    val isGlass = config.template == LyricsCardTemplate.GLASSMORPHISM
    val isEditorial = config.template == LyricsCardTemplate.EDITORIAL

    Box(modifier = modifier) {
        // 1. Dynamic Background based on selected template
        when (config.template) {
            LyricsCardTemplate.MINIMAL_DARK -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF090A0E))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(palette.primary.copy(alpha = 0.25f), Color.Transparent)
                                )
                            )
                    )
                }
            }

            LyricsCardTemplate.CINEMATIC_BLUR -> {
                AsyncImage(
                    model = artUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(32.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.55f),
                                0.45f to Color.Black.copy(alpha = if (config.deepContrast) 0.88f else 0.78f),
                                1f to Color.Black.copy(alpha = 0.96f)
                            )
                        )
                )
            }

            LyricsCardTemplate.GLASSMORPHISM -> {
                AsyncImage(
                    model = artUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(26.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xD00A0C14), Color(0xF2040508))
                            )
                        )
                )
                // Frosted Glass Center Panel
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 20.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(
                            1.dp,
                            Brush.linearGradient(
                                listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.08f))
                            ),
                            RoundedCornerShape(20.dp)
                        )
                )
            }

            LyricsCardTemplate.EDITORIAL -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0C0B0E))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.4f to Color.Transparent,
                                    0.8f to palette.primary.copy(alpha = 0.22f),
                                    1f to Color(0xFF09080B)
                                )
                            )
                    )
                }
            }

            LyricsCardTemplate.AMBIENT_GRADIENT -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(palette.secondary.copy(alpha = 0.90f), palette.deep, Color(0xFF06070A))
                            )
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(palette.primary.copy(alpha = 0.85f), Color.Transparent)
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = if (config.deepContrast) 0.70f else 0.50f))
                    )
                }
            }
        }

        // 2. Foreground Card Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isGlass) 22.dp else 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = if (config.alignment == LyricsAlignment.CENTER) Alignment.CenterHorizontally else Alignment.Start
        ) {
            // Quotation Mark
            Text(
                text = "“",
                color = when (config.template) {
                    LyricsCardTemplate.EDITORIAL -> Color(0xFFFBBF24)
                    LyricsCardTemplate.GLASSMORPHISM -> Color(0xFFC084FC)
                    LyricsCardTemplate.AMBIENT_GRADIENT -> palette.primary
                    else -> Color.White.copy(alpha = 0.65f)
                },
                fontSize = if (isEditorial) 36.sp else 28.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                lineHeight = 28.sp,
                textAlign = config.alignment.composeAlign,
                modifier = Modifier.fillMaxWidth()
            )

            // Selected Lyrics Text with Instagram-Style Typography
            Column(
                modifier = Modifier.weight(1f, fill = false),
                horizontalAlignment = if (config.alignment == LyricsAlignment.CENTER) Alignment.CenterHorizontally else Alignment.Start
            ) {
                lines.forEachIndexed { idx, line ->
                    val displayLine = if (config.font.isAllCaps) line.uppercase() else line
                    Text(
                        text = displayLine,
                        color = Color.White,
                        fontSize = (config.fontSize.spSize * 0.62f).sp, // Scaled for fitted preview viewport
                        fontFamily = config.font.composeFontFamily,
                        fontWeight = config.font.composeFontWeight,
                        fontStyle = config.font.composeFontStyle,
                        letterSpacing = config.font.letterSpacingSp.sp,
                        lineHeight = (config.fontSize.spSize * 0.85f).sp,
                        textAlign = config.alignment.composeAlign,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (idx < lines.lastIndex) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // Bottom Section: Song Meta + Branding
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (config.showArtistInfo) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (config.showThumbnail) {
                            AsyncImage(
                                model = artUrl,
                                contentDescription = track.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist.ifBlank { track.channelName }.ifBlank { "Auralix Music" },
                                color = Color(0xFFD4D4D8),
                                fontSize = 11.sp,
                                fontFamily = if (isEditorial) FontFamily.Serif else FontFamily.SansSerif,
                                fontStyle = if (isEditorial) FontStyle.Italic else FontStyle.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (config.showBranding) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "♫  A U R A L I X",
                        color = Color.White.copy(alpha = 0.40f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
