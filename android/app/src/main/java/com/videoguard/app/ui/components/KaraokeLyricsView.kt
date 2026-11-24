@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.videoguard.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.graphicsLayer
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
 * - Centered vertical alignment: current playing line stays in the middle
 * - Smooth spring & easing animations on line changes (scale, color, alpha)
 * - 100% Transparent background floating seamlessly over the player's blurred artwork
 * - Tap any line to seek playback
 * - Universal Romanization support (Hindi, Punjabi, Korean, Japanese, Russian, etc.)
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun KaraokeLyricsView(
    lyrics: LyricsResult?,
    isLoading: Boolean,
    track: VideoItem,
    currentSeconds: Float,
    lyricsOffsetMs: Long = 0L,
    romanizeMode: LyricsRomanizeMode,
    onSeekTo: (Float) -> Unit,
    onLineLongClick: ((String) -> Unit)? = null,
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

    // Smoothly animate the active line to the exact vertical center
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && lyrics?.isSynced == true) {
            listState.animateScrollToItem(activeIndex, scrollOffset = 0)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        val containerHeight = maxHeight
        // Calculate vertical padding so active item (scrolled with offset 0) sits in vertical center
        val centerPadding = ((containerHeight / 2) - 26.dp).coerceAtLeast(16.dp)

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
                    contentPadding = PaddingValues(top = centerPadding, bottom = centerPadding, start = 14.dp, end = 14.dp),
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

                        // Smooth fluid transitions for color, scale, and opacity
                        val itemColor by animateColorAsState(
                            targetValue = when {
                                isActive -> Color(0xFF3EA6FF)
                                isPast -> Color.White.copy(alpha = 0.32f)
                                else -> Color.White.copy(alpha = 0.65f)
                            },
                            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                            label = "lyricsColor"
                        )

                        val itemScale by animateFloatAsState(
                            targetValue = if (isActive) 1.07f else 0.94f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "lyricsScale"
                        )

                        val itemAlpha by animateFloatAsState(
                            targetValue = when {
                                isActive -> 1f
                                isPast -> 0.35f
                                else -> 0.70f
                            },
                            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                            label = "lyricsAlpha"
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = itemScale
                                    scaleY = itemScale
                                    this.alpha = itemAlpha
                                }
                                .clip(RoundedCornerShape(10.dp))
                                .combinedClickable(
                                    onClick = {
                                        if (lyrics.isSynced) {
                                            val targetSec = ((line.timestampMs - lyricsOffsetMs) / 1000f).coerceAtLeast(0f)
                                            onSeekTo(targetSec)
                                        }
                                    },
                                    onLongClick = {
                                        onLineLongClick?.invoke(mainText)
                                    }
                                )
                                .padding(vertical = if (isActive) 8.dp else 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = mainText,
                                color = itemColor,
                                fontSize = if (isActive) 19.sp else 16.sp,
                                lineHeight = if (isActive) 26.sp else 22.sp,
                                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (showSubtitle) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = effectiveRoman,
                                    color = if (isActive) Color(0xFF93C5FD).copy(alpha = 0.90f) else Color.White.copy(alpha = 0.30f),
                                    fontSize = if (isActive) 13.sp else 12.sp,
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
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
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = displayPlain,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
