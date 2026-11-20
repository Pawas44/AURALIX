@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.videoguard.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.videoguard.app.data.LyricLine
import com.videoguard.app.data.LyricsOffsetManager
import com.videoguard.app.data.LyricsResult
import com.videoguard.app.data.LyricsRomanizeMode
import com.videoguard.app.data.TransliterationEngine
import com.videoguard.app.model.VideoItem

/**
 * Full-Artwork Synchronized Karaoke Lyrics View
 * Features:
 * - Song poster / artwork rendered in the background with soft blur & filmic ambient gradient
 * - Synchronized lyrics cleanly floating on top with glowing active line typography
 * - Tap any line to seek playback
 * - Universal Romanization support (Devanagari, Punjabi, Korean, Japanese, Cyrillic, etc.)
 * - Sleek floating ABC toggle pill (Roman / Both / Off)
 * - Zero clutter: no intrusive buttons or extra headers
 */
@Composable
fun KaraokeLyricsView(
    lyrics: LyricsResult?,
    isLoading: Boolean,
    track: VideoItem,
    currentSeconds: Float,
    lyricsOffsetMs: Long = 0L,
    romanizeMode: LyricsRomanizeMode,
    onCycleRomanizeMode: () -> Unit,
    onSeekTo: (Float) -> Unit,
    onClose: (() -> Unit)? = null,
    showBackgroundPoster: Boolean = true,
    modifier: Modifier = Modifier
) {
    val adjustedTimeMs = (currentSeconds * 1000).toLong() + lyricsOffsetMs
    val activeIndex = remember(lyrics, adjustedTimeMs) {
        if (lyrics != null && lyrics.isSynced && lyrics.lines.isNotEmpty()) {
            val idx = lyrics.lines.indexOfLast { it.timestampMs <= adjustedTimeMs }
            if (idx < 0) -1 else idx
        } else -1
    }

    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && lyrics?.isSynced == true) {
            val target = (activeIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(target)
        }
    }

    Box(
        modifier = modifier
            .shadow(22.dp, RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.65f))
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0F1018))
    ) {
        // 1. Song Poster in Background (Atmospheric blurred artwork)
        if (showBackgroundPoster) {
            val posterUrl = track.thumbnailUrl.ifBlank { "https://img.youtube.com/vi/${track.id}/hqdefault.jpg" }
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(18.dp)
                    .graphicsLayer { alpha = 0.38f }
            )
            // Filmic dark gradient overlay so text stands out with maximum contrast
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.68f),
                                Color.Black.copy(alpha = 0.45f),
                                Color.Black.copy(alpha = 0.78f)
                            )
                        )
                    )
            )
        }

        // 2. Floating Top-Right Action Row (Minimal ABC Romanize Mode Pill + Close)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .zIndex(10f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ABC Romanization Mode Toggle Pill
            val (modeLabel, isHighlighted) = when (romanizeMode) {
                LyricsRomanizeMode.BOTH -> "ABC: Both" to true
                LyricsRomanizeMode.ROMANIZED -> "ABC: Roman" to true
                LyricsRomanizeMode.NATIVE -> "ABC: Off" to false
            }

            Box(
                modifier = Modifier
                    .shadow(4.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isHighlighted) Color(0xFF1E3A5F).copy(alpha = 0.90f) else Color.Black.copy(alpha = 0.50f))
                    .border(1.dp, if (isHighlighted) Color(0xFF3EA6FF).copy(alpha = 0.70f) else Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                    .clickable { onCycleRomanizeMode() }
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text(
                    text = modeLabel,
                    color = if (isHighlighted) Color(0xFF3EA6FF) else Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Dismiss Button (to flip back to artwork view)
            if (onClose != null) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.50f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close lyrics",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        // 3. Lyrics Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF3EA6FF),
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 2.5.dp
                        )
                    }
                }
                lyrics != null && lyrics.lines.isNotEmpty() -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(top = 48.dp, bottom = 48.dp, start = 8.dp, end = 8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(lyrics.lines) { index, line ->
                            val isActive = index == activeIndex && lyrics.isSynced
                            val isPast = index < activeIndex && lyrics.isSynced

                            // Determine main text based on Romanization mode
                            val effectiveRoman = line.romanizedText.ifBlank {
                                if (TransliterationEngine.canRomanize(line.text)) {
                                    TransliterationEngine.romanize(line.text)
                                } else ""
                            }

                            val mainText = when (romanizeMode) {
                                LyricsRomanizeMode.ROMANIZED -> {
                                    if (effectiveRoman.isNotBlank()) effectiveRoman else line.text
                                }
                                else -> line.text
                            }

                            val showSubtitle = romanizeMode == LyricsRomanizeMode.BOTH &&
                                    effectiveRoman.isNotBlank() &&
                                    effectiveRoman != line.text

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (lyrics.isSynced) {
                                            val targetSec = ((line.timestampMs - lyricsOffsetMs) / 1000f).coerceAtLeast(0f)
                                            onSeekTo(targetSec)
                                        }
                                    }
                                    .padding(vertical = if (isActive) 8.dp else 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = mainText,
                                    color = when {
                                        isActive -> Color(0xFF3EA6FF)
                                        isPast -> Color.White.copy(alpha = 0.35f)
                                        else -> Color.White.copy(alpha = 0.55f)
                                    },
                                    fontSize = if (isActive) 19.sp else 15.sp,
                                    lineHeight = if (isActive) 26.sp else 22.sp,
                                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (showSubtitle) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = effectiveRoman,
                                        color = if (isActive) Color(0xFF93C5FD).copy(alpha = 0.90f) else Color.White.copy(alpha = 0.28f),
                                        fontSize = if (isActive) 13.sp else 11.sp,
                                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    // Top Gradient Edge Fade
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xE60F1018), Color.Transparent)
                                )
                            )
                    )

                    // Bottom Gradient Edge Fade
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xE60F1018))
                                )
                            )
                    )
                }
                else -> {
                    val displayPlain = remember(track.lyrics, romanizeMode) {
                        if (track.lyrics.isBlank()) {
                            "Lyrics not available for this track"
                        } else when (romanizeMode) {
                            LyricsRomanizeMode.ROMANIZED -> {
                                TransliterationEngine.romanize(track.lyrics)
                            }
                            else -> track.lyrics
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color(0xFF555555),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = displayPlain,
                                color = Color.White.copy(alpha = 0.70f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
