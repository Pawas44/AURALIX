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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import coil.compose.AsyncImage
import com.videoguard.app.data.LyricLine
import com.videoguard.app.data.LyricsOffsetManager
import com.videoguard.app.data.LyricsResult
import com.videoguard.app.model.VideoItem
import java.util.Locale

/**
 * RiMusic / ViMusic style Centered Synchronized Karaoke Lyrics View
 * Features:
 * - Centered glowing karaoke typography
 * - Dual-line Romanized transliteration underneath native Indic lines
 * - Live sync offset adjuster (- 0.0s +)
 * - "Wrong lyrics?" custom search dialog
 * - ABC Romanization toggle button
 * - Smooth edge gradient fade
 */
@Composable
fun KaraokeLyricsView(
    lyrics: LyricsResult?,
    isLoading: Boolean,
    track: VideoItem,
    currentSeconds: Float,
    lyricsOffsetMs: Long,
    romanizeMode: com.videoguard.app.data.LyricsRomanizeMode,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onCycleRomanizeMode: () -> Unit,
    onWrongLyricsClicked: () -> Unit,
    onSeekTo: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isHeroCard: Boolean = false,
    onCloseHeroCard: (() -> Unit)? = null
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
            .then(
                if (isHeroCard) {
                    Modifier
                        .shadow(16.dp, RoundedCornerShape(22.dp), spotColor = Color(0x66000000))
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF101118))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
                } else {
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF101118))
                }
            )
    ) {
        // Background Artwork Blur in Hero Card Mode
        if (isHeroCard) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.18f }
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xF2101118),
                                Color(0xFA0B0C12)
                            )
                        )
                    )
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (lyrics?.isSynced == true) Color(0xFF3EA6FF) else Color(0xFF777777))
                    )
                    Text(
                        text = if (lyrics?.isSynced == true) "SYNCHRONIZED LYRICS" else "LYRICS",
                        color = if (lyrics?.isSynced == true) Color(0xFF3EA6FF) else Color(0xFFAAAAAA),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }

                if (isHeroCard && onCloseHeroCard != null) {
                    IconButton(
                        onClick = onCloseHeroCard,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close lyrics view",
                            tint = Color(0xFFAAAAAA),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Lyrics Scroll Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF3EA6FF),
                                modifier = Modifier.size(30.dp),
                                strokeWidth = 2.5.dp
                            )
                        }
                    }
                    lyrics != null && lyrics.lines.isNotEmpty() -> {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(vertical = 36.dp, horizontal = 16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(lyrics.lines) { index, line ->
                                val isActive = index == activeIndex && lyrics.isSynced
                                val isPast = index < activeIndex && lyrics.isSynced

                                val mainText = when (romanizeMode) {
                                    com.videoguard.app.data.LyricsRomanizeMode.ROMANIZED -> {
                                        if (line.romanizedText.isNotBlank()) line.romanizedText else line.text
                                    }
                                    else -> line.text
                                }
                                val showSubtitle = romanizeMode == com.videoguard.app.data.LyricsRomanizeMode.BOTH &&
                                        line.romanizedText.isNotBlank() &&
                                        line.romanizedText != line.text

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
                                        .padding(vertical = if (isActive) 6.dp else 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = mainText,
                                        color = when {
                                            isActive -> Color(0xFF3EA6FF)
                                            isPast -> Color.White.copy(alpha = 0.35f)
                                            else -> Color.White.copy(alpha = 0.48f)
                                        },
                                        fontSize = if (isActive) 18.sp else 15.sp,
                                        lineHeight = if (isActive) 25.sp else 22.sp,
                                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    if (showSubtitle) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = line.romanizedText,
                                            color = if (isActive) Color(0xFF93C5FD).copy(alpha = 0.90f) else Color.White.copy(alpha = 0.25f),
                                            fontSize = if (isActive) 13.sp else 11.sp,
                                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }

                        // Top Gradient Fade
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .align(Alignment.TopCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color(0xF0101118), Color.Transparent)
                                    )
                                )
                        )

                        // Bottom Gradient Fade
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color(0xF0101118))
                                    )
                                )
                        )
                    }
                    else -> {
                        val displayPlain = remember(track.lyrics, romanizeMode) {
                            if (track.lyrics.isBlank()) {
                                "Lyrics not available for this track"
                            } else when (romanizeMode) {
                                com.videoguard.app.data.LyricsRomanizeMode.ROMANIZED -> {
                                    com.videoguard.app.data.TransliterationEngine.romanize(track.lyrics)
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
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = displayPlain,
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Controller Bar (Offset Adjuster, Wrong Lyrics, ABC Transliteration)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C0D13))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Timing Offset Controller
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 3.dp, vertical = 2.dp)
                ) {
                    IconButton(
                        onClick = { onAdjustOffset(-500L) },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Text("-", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "${if (lyricsOffsetMs > 0) "+" else ""}${String.format(Locale.US, "%.1fs", lyricsOffsetMs / 1000f)}",
                        color = if (lyricsOffsetMs != 0L) Color(0xFF3EA6FF) else Color(0xFFAAAAAA),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clickable { onResetOffset() }
                    )
                    IconButton(
                        onClick = { onAdjustOffset(500L) },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Text("+", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Wrong lyrics?
                Text(
                    text = "Wrong lyrics?",
                    color = Color(0xFFAAAAAA),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onWrongLyricsClicked() }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )

                // ABC Transliteration 3-Way Mode Toggle
                val (modeLabel, isHighlighted) = when (romanizeMode) {
                    com.videoguard.app.data.LyricsRomanizeMode.BOTH -> "ABC: Both" to true
                    com.videoguard.app.data.LyricsRomanizeMode.ROMANIZED -> "ABC: Roman" to true
                    com.videoguard.app.data.LyricsRomanizeMode.NATIVE -> "ABC: Native" to false
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isHighlighted) Color(0xFF1E3A5F) else Color.White.copy(alpha = 0.08f))
                        .clickable { onCycleRomanizeMode() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = modeLabel,
                        color = if (isHighlighted) Color(0xFF3EA6FF) else Color(0xFFAAAAAA),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
