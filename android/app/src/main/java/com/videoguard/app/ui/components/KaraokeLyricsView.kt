@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.videoguard.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoguard.app.data.LyricLine
import com.videoguard.app.data.LyricsResult
import com.videoguard.app.data.LyricsRomanizeMode
import com.videoguard.app.data.TransliterationEngine
import com.videoguard.app.model.VideoItem

/**
 * Transparent Synchronized Karaoke Lyrics View
 * Features:
 * - 100% Transparent background floating seamlessly over the player's blurred artwork
 * - Smooth frame-by-frame synchronized scrolling
 * - Large, glowing typography for active lyrics
 * - Tap any line to seek playback
 * - Universal Romanization support (Hindi, Punjabi, Korean, Japanese, Russian, etc.)
 */
@Composable
fun KaraokeLyricsView(
    lyrics: LyricsResult?,
    isLoading: Boolean,
    track: VideoItem,
    currentSeconds: Float,
    lyricsOffsetMs: Long = 0L,
    romanizeMode: LyricsRomanizeMode,
    onSeekTo: (Float) -> Unit,
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
            val target = (activeIndex - 1).coerceAtLeast(0)
            listState.animateScrollToItem(target)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
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
                    contentPadding = PaddingValues(top = 28.dp, bottom = 48.dp, start = 12.dp, end = 12.dp),
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
                                .padding(vertical = if (isActive) 10.dp else 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = mainText,
                                color = when {
                                    isActive -> Color(0xFF3EA6FF)
                                    isPast -> Color.White.copy(alpha = 0.35f)
                                    else -> Color.White.copy(alpha = 0.60f)
                                },
                                fontSize = if (isActive) 21.sp else 16.sp,
                                lineHeight = if (isActive) 28.sp else 23.sp,
                                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (showSubtitle) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = effectiveRoman,
                                    color = if (isActive) Color(0xFF93C5FD).copy(alpha = 0.92f) else Color.White.copy(alpha = 0.30f),
                                    fontSize = if (isActive) 14.sp else 12.sp,
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // Top Gradient Edge Fade (Transparent fade)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.50f), Color.Transparent)
                            )
                        )
                )

                // Bottom Gradient Edge Fade
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.50f))
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
                            tint = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = displayPlain,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
