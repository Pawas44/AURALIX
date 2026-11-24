@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.videoguard.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
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
 * - Tap any line to seek playback (normal mode)
 * - Long-Press any line to enter Lyrics Selection mode
 * - Select 2-3 lines with visual highlight and contextual "Create Card" action bar
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
    onCreateLyricsCard: ((List<String>) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val adjustedTimeMs = (currentSeconds * 1000).toLong() + lyricsOffsetMs
    val activeIndex = remember(lyrics, adjustedTimeMs) {
        if (lyrics != null && lyrics.isSynced && lyrics.lines.isNotEmpty()) {
            val idx = lyrics.lines.indexOfLast { it.timestampMs <= adjustedTimeMs }
            if (idx < 0) -1 else idx
        } else -1
    }

    val listState = rememberLazyListState()

    // Multi-line selection state (Max 3 consecutive lines)
    val selectedIndices = remember { mutableStateListOf<Int>() }
    val isSelectionMode = selectedIndices.isNotEmpty()

    // Smoothly animate the active line to the exact vertical center only when NOT selecting
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && lyrics?.isSynced == true && !isSelectionMode) {
            listState.animateScrollToItem(activeIndex, scrollOffset = 0)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        val containerHeight = maxHeight
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
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            top = if (lyrics.isSynced) centerPadding else 24.dp,
                            bottom = if (isSelectionMode) centerPadding + 70.dp else (if (lyrics.isSynced) centerPadding else 48.dp),
                            start = 14.dp,
                            end = 14.dp
                        ),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(lyrics.lines) { index, line ->
                            val isActive = index == activeIndex && lyrics.isSynced && !isSelectionMode
                            val isPast = index < activeIndex && lyrics.isSynced && !isSelectionMode
                            val isSelected = selectedIndices.contains(index)

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

                            // Fluid transitions for color, scale, and opacity
                            val itemColor by animateColorAsState(
                                targetValue = when {
                                    isSelected -> Color.White
                                    isActive -> Color(0xFF3EA6FF)
                                    isPast -> Color.White.copy(alpha = 0.32f)
                                    !lyrics.isSynced -> Color.White.copy(alpha = 0.92f)
                                    else -> Color.White.copy(alpha = 0.65f)
                                },
                                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                                label = "lyricsColor"
                            )

                            val itemScale by animateFloatAsState(
                                targetValue = when {
                                    isSelected -> 1.05f
                                    isActive -> 1.07f
                                    !lyrics.isSynced -> 1.0f
                                    else -> 0.94f
                                },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                label = "lyricsScale"
                            )

                            val itemAlpha by animateFloatAsState(
                                targetValue = when {
                                    isSelected -> 1f
                                    isSelectionMode -> 0.40f
                                    isActive -> 1f
                                    isPast -> 0.35f
                                    !lyrics.isSynced -> 0.95f
                                    else -> 0.70f
                                },
                                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                                label = "lyricsAlpha"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = if (isSelected || isActive) 6.dp else 3.dp)
                                    .graphicsLayer {
                                        scaleX = itemScale
                                        scaleY = itemScale
                                        this.alpha = itemAlpha
                                    }
                                    .clip(RoundedCornerShape(14.dp))
                                    .then(
                                        if (isSelected) {
                                            Modifier.background(
                                                Brush.horizontalGradient(
                                                    listOf(
                                                        Color(0xFF8B5CF6).copy(alpha = 0.38f),
                                                        Color(0xFFEC4899).copy(alpha = 0.38f)
                                                    )
                                                )
                                            )
                                        } else Modifier
                                    )
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.dp,
                                        brush = if (isSelected) {
                                            Brush.horizontalGradient(
                                                listOf(Color(0xFFC084FC), Color(0xFFF472B6))
                                            )
                                        } else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            if (isSelectionMode) {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                if (isSelected) {
                                                    selectedIndices.remove(index)
                                                } else {
                                                    if (selectedIndices.size < 3) {
                                                        selectedIndices.add(index)
                                                    }
                                                }
                                            } else if (lyrics.isSynced) {
                                                val targetSec = ((line.timestampMs - lyricsOffsetMs) / 1000f).coerceAtLeast(0f)
                                                onSeekTo(targetSec)
                                            }
                                        },
                                        onLongClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            if (!isSelected) {
                                                if (selectedIndices.size >= 3) selectedIndices.clear()
                                                selectedIndices.add(index)
                                            }
                                        }
                                    )
                                    .padding(horizontal = 14.dp, vertical = if (isSelected || isActive) 8.dp else 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = mainText,
                                        color = itemColor,
                                        fontSize = if (isActive || isSelected) 19.sp else 16.sp,
                                        lineHeight = if (isActive || isSelected) 26.sp else 22.sp,
                                        fontWeight = if (isActive || isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        letterSpacing = if (isActive || isSelected) (-0.3).sp else 0.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    if (showSubtitle) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = effectiveRoman,
                                            color = if (isActive || isSelected) Color(0xFF93C5FD).copy(alpha = 0.90f) else Color.White.copy(alpha = 0.30f),
                                            fontSize = if (isActive || isSelected) 13.sp else 12.sp,
                                            fontWeight = if (isActive || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Floating Contextual Action Bar when in Selection Mode
                    AnimatedVisibility(
                        visible = isSelectionMode,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp, start = 16.dp, end = 16.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = Color(0xF0181A26),
                            shadowElevation = 14.dp,
                            border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { selectedIndices.clear() },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Cancel",
                                            tint = Color(0xFFA1A1AA),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${selectedIndices.size}/3 lines",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Button(
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        val lines = selectedIndices.sorted().mapNotNull { idx ->
                                            lyrics.lines.getOrNull(idx)?.text
                                        }
                                        if (lines.isNotEmpty()) {
                                            onCreateLyricsCard?.invoke(lines)
                                            selectedIndices.clear()
                                        }
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                    contentPadding = PaddingValues(),
                                    modifier = Modifier
                                        .height(38.dp)
                                        .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = Color(0xFF8B5CF6))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))
                                                )
                                            )
                                            .padding(horizontal = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Create Card",
                                                color = Color.White,
                                                fontSize = 13.sp,
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
                        if (track.lyrics.isNotBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    val lines = track.lyrics.lines().filter { it.isNotBlank() }.take(3)
                                    onCreateLyricsCard?.invoke(lines.ifEmpty { listOf(track.title) })
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6).copy(alpha = 0.3f)),
                                border = BorderStroke(1.dp, Color(0xFFC084FC).copy(alpha = 0.6f))
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFC084FC), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create Card", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
