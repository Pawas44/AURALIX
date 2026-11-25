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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoguard.app.data.LyricLine
import com.videoguard.app.data.LyricsResult
import com.videoguard.app.data.LyricsRomanizeMode
import com.videoguard.app.data.TransliterationEngine
import com.videoguard.app.model.VideoItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Spotify-Inspired Synchronized Real-Time Lyrics View
 *
 * Features:
 * - Real-time audio time-synchronization following current playback.
 * - Bold, luminous active line highlighting with smooth spring animations.
 * - Past lyrics dimmed softly (~0.35f alpha) and upcoming lyrics subtly visible (~0.65f alpha).
 * - Tap any line to seek music playback with subtle haptic tap.
 * - Smart Spotify-style manual scroll detection: pauses auto-scroll while browsing so the view
 *   never wrestles with user gestures.
 * - Floating "Sync with Song" snap button when scrolled away, plus auto-resume after 4s inactivity.
 * - Top and bottom gradient scrims for seamless edge fading.
 * - Multi-line selection support for creating lyrics sharing cards (long-press).
 * - Universal Romanization pronunciation support.
 * - Premium empty state with Retry, Manual Search, and Add Custom Lyrics actions.
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
    onRetry: (() -> Unit)? = null,
    onSearchManually: (() -> Unit)? = null,
    onAddLyrics: (() -> Unit)? = null,
    onCreateLyricsCard: ((List<String>) -> Unit)? = null,
    forcePlainLyricsView: Boolean = false,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
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

    // Spotify-like manual scroll detection:
    // When the user touches / scrolls the list, pause auto-scroll.
    var isUserInteracting by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            isUserInteracting = true
            lastInteractionTime = System.currentTimeMillis()
        }
    }

    // Auto-resume tracking after 4 seconds of scroll inactivity
    LaunchedEffect(isUserInteracting, lastInteractionTime) {
        if (isUserInteracting) {
            delay(4000)
            isUserInteracting = false
        }
    }

    // Smoothly animate the active line to the vertical center only when NOT selecting and NOT user-scrolling
    LaunchedEffect(activeIndex, isUserInteracting, isSelectionMode, forcePlainLyricsView) {
        if (activeIndex >= 0 && lyrics?.isSynced == true && !forcePlainLyricsView && !isSelectionMode && !isUserInteracting) {
            listState.animateScrollToItem(activeIndex, scrollOffset = 0)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        val containerHeight = maxHeight
        val centerPadding = ((containerHeight / 2) - 28.dp).coerceAtLeast(16.dp)

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF3EA6FF),
                            modifier = Modifier.size(34.dp),
                            strokeWidth = 2.5.dp
                        )
                        Text(
                            text = "Loading lyrics...",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            lyrics != null && lyrics.lines.isNotEmpty() -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            top = if (lyrics.isSynced && !forcePlainLyricsView) centerPadding else 24.dp,
                            bottom = if (isSelectionMode) centerPadding + 70.dp else (if (lyrics.isSynced && !forcePlainLyricsView) centerPadding else 48.dp),
                            start = 16.dp,
                            end = 16.dp
                        ),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(lyrics.lines) { index, line ->
                            val isActive = index == activeIndex && lyrics.isSynced && !forcePlainLyricsView && !isSelectionMode
                            val isPast = index < activeIndex && lyrics.isSynced && !forcePlainLyricsView && !isSelectionMode
                            val isUpcoming = index > activeIndex && lyrics.isSynced && !forcePlainLyricsView && !isSelectionMode
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
                                    isActive -> Color.White
                                    isPast -> Color.White.copy(alpha = 0.35f)
                                    isUpcoming -> Color.White.copy(alpha = 0.65f)
                                    else -> Color.White.copy(alpha = 0.90f)
                                },
                                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                                label = "lyricsColor"
                            )

                            val itemScale by animateFloatAsState(
                                targetValue = when {
                                    isSelected -> 1.05f
                                    isActive -> 1.04f
                                    !lyrics.isSynced || forcePlainLyricsView -> 1.0f
                                    else -> 0.96f
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
                                    isSelectionMode -> 0.35f
                                    isActive -> 1f
                                    isPast -> 0.35f
                                    isUpcoming -> 0.65f
                                    else -> 0.90f
                                },
                                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
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
                                            } else if (lyrics.isSynced && !forcePlainLyricsView) {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                val targetSec = ((line.timestampMs - lyricsOffsetMs) / 1000f).coerceAtLeast(0f)
                                                onSeekTo(targetSec)
                                                isUserInteracting = false
                                                coroutineScope.launch {
                                                    listState.animateScrollToItem(index, 0)
                                                }
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
                                    .padding(horizontal = 14.dp, vertical = if (isSelected || isActive) 7.dp else 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = mainText,
                                        color = itemColor,
                                        fontSize = if (isActive || isSelected) 20.sp else 16.sp,
                                        lineHeight = if (isActive || isSelected) 27.sp else 22.sp,
                                        fontWeight = if (isActive || isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        letterSpacing = if (isActive || isSelected) (-0.2).sp else 0.sp,
                                        textAlign = TextAlign.Center,
                                        style = if (isActive) {
                                            TextStyle(
                                                shadow = Shadow(
                                                    color = Color.Black.copy(alpha = 0.85f),
                                                    blurRadius = 10f
                                                )
                                            )
                                        } else TextStyle.Default,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    if (showSubtitle) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = effectiveRoman,
                                            color = if (isActive || isSelected) Color(0xFF93C5FD).copy(alpha = 0.90f) else Color.White.copy(alpha = 0.35f),
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

                    // Top Gradient Scrim (Smooth edge fading)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.70f), Color.Transparent)
                                )
                            )
                    )

                    // Bottom Gradient Scrim
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.70f))
                                )
                            )
                    )

                    // Floating "Sync with Song" Snap-Back Button (Spotify-like when user scrolled away)
                    AnimatedVisibility(
                        visible = isUserInteracting && activeIndex >= 0 && lyrics.isSynced && !forcePlainLyricsView && !isSelectionMode,
                        enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 2 },
                        exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 2 },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 16.dp, end = 16.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xF0181A26),
                            border = BorderStroke(1.dp, Color(0xFF3EA6FF).copy(alpha = 0.65f)),
                            shadowElevation = 10.dp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    isUserInteracting = false
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(activeIndex, 0)
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Sync",
                                    tint = Color(0xFF3EA6FF),
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Sync",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
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
                // Premium Empty State
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xD9121320),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        shadowElevation = 14.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(22.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF8B5CF6).copy(alpha = 0.20f))
                                    .border(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.40f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = Color(0xFFC084FC),
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Lyrics Not Available",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Couldn't match verified lyrics for \"${track.title}\"",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            // Action buttons: Retry, Search, Add
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (onRetry != null) {
                                    OutlinedButton(
                                        onClick = onRetry,
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Retry", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                if (onSearchManually != null) {
                                    Button(
                                        onClick = onSearchManually,
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A5F)),
                                        border = BorderStroke(1.dp, Color(0xFF3EA6FF).copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF3EA6FF), modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Search", color = Color(0xFF3EA6FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (onAddLyrics != null) {
                                    OutlinedButton(
                                        onClick = onAddLyrics,
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC084FC)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Add", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
