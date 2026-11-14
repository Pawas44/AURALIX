package com.videoguard.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoguard.app.ui.theme.LiquidGlassDefaults
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerControlsOverlay(
    isPlaying: Boolean,
    currentTime: Float,
    duration: Float,
    onPlayPauseToggle: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeekBy: (Int) -> Unit,
    onSeekTo: (Float) -> Unit,
    onCollapse: () -> Unit,
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    onSwipeDownDelta: ((Float) -> Unit)? = null,
    onSwipeDownRelease: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var isUserScrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableFloatStateOf(0f) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    var doubleTapSide by remember { mutableStateOf<String?>(null) }
    var doubleTapTimestamp by remember { mutableLongStateOf(0L) }

    // Clear double-tap indicator after 650ms
    LaunchedEffect(doubleTapTimestamp) {
        if (doubleTapTimestamp > 0L) {
            delay(650)
            doubleTapSide = null
        }
    }

    // Auto-hide controls after 3.5 seconds when playing
    LaunchedEffect(controlsVisible, isPlaying, isUserScrubbing, showSettingsSheet) {
        if (controlsVisible && isPlaying && !isUserScrubbing && !showSettingsSheet) {
            delay(3500)
            controlsVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        controlsVisible = !controlsVisible
                    },
                    onDoubleTap = { offset ->
                        val isLeft = offset.x < size.width / 2
                        if (isLeft) {
                            onSeekBy(-10)
                            doubleTapSide = "left"
                            doubleTapTimestamp = System.currentTimeMillis()
                        } else {
                            onSeekBy(10)
                            doubleTapSide = "right"
                            doubleTapTimestamp = System.currentTimeMillis()
                        }
                    }
                )
            }
    ) {
        // --- DOUBLE TAP TRANSIENT OVERLAYS ---
        AnimatedVisibility(
            visible = doubleTapSide == "left",
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .align(Alignment.CenterStart)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "-10s",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "10 seconds",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = doubleTapSide == "right",
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .align(Alignment.CenterEnd)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "+10s",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "10 seconds",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // --- CONTROLS OVERLAY ---
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top scrim vignette
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                            )
                        )
                )

                // Bottom scrim vignette
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.90f))
                            )
                        )
                )

                // Center subtle dimming
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f))
                )

                // --- TOP BAR ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .pointerInput(isFullscreen) {
                            if (!isFullscreen) {
                                detectVerticalDragGestures(
                                    onVerticalDrag = { change, dragAmount ->
                                        if (dragAmount > 0f) {
                                            change.consume()
                                            onSwipeDownDelta?.invoke(dragAmount)
                                        }
                                    },
                                    onDragEnd = { onSwipeDownRelease?.invoke(0f) }
                                )
                            }
                        }
                        .padding(
                            horizontal = if (isFullscreen) 24.dp else 12.dp,
                            vertical = if (isFullscreen) 14.dp else 10.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .shadow(elevation = 6.dp, shape = CircleShape)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .border(1.dp, LiquidGlassDefaults.BorderSpecular, CircleShape)
                            .clickable(onClick = onCollapse),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isFullscreen) "Exit Fullscreen" else "Collapse",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .shadow(elevation = 6.dp, shape = CircleShape)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .border(1.dp, LiquidGlassDefaults.BorderSpecular, CircleShape)
                            .clickable { showSettingsSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // --- CENTER CONTROLS (Liquid Glass Action Orbs) ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .padding(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Track Liquid Glass Orb
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .shadow(elevation = 8.dp, shape = CircleShape)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .border(1.dp, LiquidGlassDefaults.BorderSpecular, CircleShape)
                            .clickable(onClick = onPrev),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    // Play / Pause Liquid Glass Centerpiece
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .shadow(elevation = 16.dp, shape = CircleShape, ambientColor = Color.White.copy(alpha = 0.15f))
                            .clip(CircleShape)
                            .background(
                                androidx.compose.ui.graphics.Brush.radialGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.28f),
                                        Color.White.copy(alpha = 0.10f)
                                    )
                                )
                            )
                            .border(1.2.dp, LiquidGlassDefaults.BorderLuminous, CircleShape)
                            .clickable(onClick = onPlayPauseToggle),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    // Next Track Liquid Glass Orb
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .shadow(elevation = 8.dp, shape = CircleShape)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .border(1.dp, LiquidGlassDefaults.BorderSpecular, CircleShape)
                            .clickable(onClick = onNext),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // --- BOTTOM TIME & SCRUBBER ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(
                            horizontal = if (isFullscreen) 28.dp else 14.dp,
                            vertical = if (isFullscreen) 12.dp else 6.dp
                        )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val displayCurrent = if (isUserScrubbing) scrubPosition else currentTime
                        val currentStr = formatTime(displayCurrent)
                        val totalStr = formatTime(duration)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentStr,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = " / $totalStr",
                                color = Color(0xFFAAAAAA),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }

                        IconButton(
                            onClick = onToggleFullscreen,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = if (isFullscreen) "Exit Fullscreen" else "Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Scrubber Bar - sleek thin slider
                    val effectiveMax = if (duration > 0f) duration else 100f
                    val effectiveVal = (if (isUserScrubbing) scrubPosition else currentTime).coerceIn(0f, effectiveMax)

                    Slider(
                        value = effectiveVal,
                        onValueChange = { newVal ->
                            isUserScrubbing = true
                            scrubPosition = newVal
                        },
                        onValueChangeFinished = {
                            isUserScrubbing = false
                            onSeekTo(scrubPosition)
                        },
                        valueRange = 0f..effectiveMax,
                        track = { sliderState ->
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                modifier = Modifier.height(2.5.dp),
                                thumbTrackGapSize = 0.dp,
                                colors = SliderDefaults.colors(
                                    activeTrackColor = Color(0xFFFF0000),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                                )
                            )
                        },
                        thumb = {
                            SliderDefaults.Thumb(
                                interactionSource = remember { MutableInteractionSource() },
                                thumbSize = DpSize(10.dp, 10.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFFF0000)
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                    )
                }
            }
        }
    }

    if (showSettingsSheet) {
        PlayerSettingsBottomSheet(onDismiss = { showSettingsSheet = false })
    }
}

private fun formatTime(seconds: Float): String {
    val totalSec = seconds.toInt().coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    val h = m / 60
    val remM = m % 60
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, remM, s)
    } else {
        String.format("%d:%02d", remM, s)
    }
}
