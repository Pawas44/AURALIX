package com.videoguard.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import coil.compose.AsyncImage
import com.videoguard.app.model.VideoItem
import kotlin.math.roundToInt

enum class PlayerSkin(val label: String, val icon: String) {
    MODERN("Modern", "🎨"),
    VINYL("Vinyl 33 RPM", "💿"),
    CASSETTE("90s Cassette", "📼"),
    CAR_DASHBOARD("Car Mode", "🚗")
}

object PlayerSkinManager {
    private const val PREFS_NAME = "auralix_player_skin_prefs"
    private const val KEY_SKIN = "active_player_skin"

    fun getSavedSkin(context: android.content.Context): PlayerSkin {
        val sp = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val name = sp.getString(KEY_SKIN, PlayerSkin.MODERN.name) ?: PlayerSkin.MODERN.name
        return try {
            PlayerSkin.valueOf(name)
        } catch (e: Exception) {
            PlayerSkin.MODERN
        }
    }

    fun saveSkin(context: android.content.Context, skin: PlayerSkin) {
        val sp = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        sp.edit().putString(KEY_SKIN, skin.name).apply()
    }
}

/**
 * Turntable Vinyl Record Skin.
 * Features:
 * - 33 RPM Rotating grooved vinyl disc with album art center label
 * - Realistic animated Tonearm needle that rests on record while playing and lifts when paused
 * - Interactive Scratch / DJ needle-drop seeking gesture
 */
@Composable
fun VinylRecordSkin(
    track: VideoItem,
    isPlaying: Boolean,
    currentSeconds: Float,
    durationSeconds: Float,
    onSeekTo: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val infiniteTransition = rememberInfiniteTransition(label = "vinylSpin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinylSpinAngle"
    )

    // Needle arm animation: rests over record when playing (28 deg), lifts off when paused (0 deg)
    val needleAngle by animateFloatAsState(
        targetValue = if (isPlaying) 26f else -6f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "needleAngle"
    )

    var scratchOffset by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        // Turntable Platter Deck Base
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF222433), Color(0xFF11121A))
                    )
                )
                .border(BorderStroke(1.5.dp, Color(0xFF3B3E54)), RoundedCornerShape(24.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            // RPM Badge & Speed indicator
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.5f),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = "33⅓ RPM • HI-FI",
                        color = Color(0xFFD4AF37),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Rotating Vinyl Disc (with scratch detection)
            Box(
                modifier = Modifier
                    .fillMaxSize(0.92f)
                    .pointerInput(isPlaying) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                scratchOffset += dragAmount.x
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                val seekDelta = (dragAmount.x / 40f) * 2f
                                val newPos = (currentSeconds + seekDelta).coerceIn(0f, durationSeconds.coerceAtLeast(1f))
                                onSeekTo(newPos)
                            }
                        )
                    }
                    .graphicsLayer {
                        rotationZ = (if (isPlaying) spinAngle else 0f) + scratchOffset
                    },
                contentAlignment = Alignment.Center
            ) {
                // Vinyl Grooves Canvas
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)

                    // Outer Vinyl Body
                    drawCircle(
                        color = Color(0xFF101014),
                        radius = radius,
                        center = center
                    )

                    // Concentric Grooves
                    val grooveColor = Color(0xFF1F2026)
                    val specularColor = Color(0xFF333540).copy(alpha = 0.35f)

                    for (i in 1..9) {
                        val r = radius * (0.42f + (i * 0.06f))
                        drawCircle(
                            color = if (i % 2 == 0) specularColor else grooveColor,
                            radius = r,
                            center = center,
                            style = Stroke(width = 1.2.dp.toPx())
                        )
                    }

                    // Outer Rim Highlight
                    drawCircle(
                        color = Color(0xFF4A4D5E),
                        radius = radius - 1.5.dp.toPx(),
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // Center Album Art Sticker Label
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.38f)
                        .clip(CircleShape)
                        .border(BorderStroke(2.dp, Color(0xFFD4AF37)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Center Spindle Hole
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0B0B0E))
                            .border(BorderStroke(1.5.dp, Color(0xFFB0B3C6)), CircleShape)
                    )
                }
            }

            // Stylus / Tonearm Needle
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 10.dp)
                    .graphicsLayer {
                        rotationZ = needleAngle
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)
                    }
            ) {
                Canvas(modifier = Modifier.size(width = 90.dp, height = 120.dp)) {
                    // Pivot base
                    drawCircle(
                        color = Color(0xFF7A7D8F),
                        radius = 12.dp.toPx(),
                        center = Offset(size.width - 12.dp.toPx(), 12.dp.toPx())
                    )
                    drawCircle(
                        color = Color(0xFFD4AF37),
                        radius = 6.dp.toPx(),
                        center = Offset(size.width - 12.dp.toPx(), 12.dp.toPx())
                    )

                    // Metallic arm rod
                    drawLine(
                        color = Color(0xFFCBD5E1),
                        start = Offset(size.width - 12.dp.toPx(), 12.dp.toPx()),
                        end = Offset(18.dp.toPx(), size.height - 20.dp.toPx()),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Needle cartridge head
                    drawRoundRect(
                        color = Color(0xFFF59E0B),
                        topLeft = Offset(8.dp.toPx(), size.height - 26.dp.toPx()),
                        size = Size(20.dp.toPx(), 14.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                }
            }
        }
    }
}

/**
 * 90s Vintage Cassette Tape Deck Skin.
 * Features:
 * - Dual spinning spools that rotate in sync with playback
 * - Physical magnetic ribbon that moves from left to right as song plays
 * - Handwritten typewriter label with track title & artist
 * - Authentic cassette screws, ridges, and stereo badges
 */
@Composable
fun CassetteTapeSkin(
    track: VideoItem,
    isPlaying: Boolean,
    currentSeconds: Float,
    durationSeconds: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cassetteReels")
    val spoolAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spoolAngle"
    )

    val progress = (currentSeconds / durationSeconds.coerceAtLeast(1f)).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .aspectRatio(1.55f)
            .shadow(16.dp, RoundedCornerShape(16.dp), spotColor = Color(0xFF000000))
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E1E24))
            .border(BorderStroke(2.dp, Color(0xFF3F3F4E)), RoundedCornerShape(16.dp))
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Cassette Header: "AURALIX AUDIO • SIDE A"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AURALIX • HIGH OUTPUT",
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "TYPE I [NORMAL] • 90 MIN",
                    color = Color(0xFFE2E8F0),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Cassette Sticker Label (Cream paper with marker handwriting)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF1EFE7),
                border = BorderStroke(1.dp, Color(0xFFD6D3C7)),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Handwritten song title
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = Color(0xFF1E293B),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Black,
                                fontStyle = FontStyle.Italic,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                color = Color(0xFF475569),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Side A Red Badge
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFDC2626),
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(
                                text = "SIDE A",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Cassette Window: Dual Spinning Spools + Magnetic Tape Ribbon
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF141419))
                            .border(BorderStroke(1.dp, Color(0xFF2A2A38)), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        // Magnetic brown tape ribbon filling
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val centerY = size.height / 2f
                            val leftSpoolX = size.width * 0.28f
                            val rightSpoolX = size.width * 0.72f

                            // Left spool tape pack (shrinks with progress)
                            val leftRadius = (20.dp.toPx() * (1f - progress * 0.65f)).coerceAtLeast(10.dp.toPx())
                            drawCircle(
                                color = Color(0xFF5A3825),
                                radius = leftRadius,
                                center = Offset(leftSpoolX, centerY)
                            )

                            // Right spool tape pack (grows with progress)
                            val rightRadius = (10.dp.toPx() + (20.dp.toPx() * progress * 0.65f)).coerceAtLeast(10.dp.toPx())
                            drawCircle(
                                color = Color(0xFF5A3825),
                                radius = rightRadius,
                                center = Offset(rightSpoolX, centerY)
                            )

                            // Tape ribbon bridging the two spools
                            drawLine(
                                color = Color(0xFF43281C),
                                start = Offset(leftSpoolX, centerY + 14.dp.toPx()),
                                end = Offset(rightSpoolX, centerY + 14.dp.toPx()),
                                strokeWidth = 3.dp.toPx()
                            )
                        }

                        // Spool Gears Row
                        Row(
                            modifier = Modifier.fillMaxWidth(0.68f),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left Spool Gear
                            CassetteGear(rotation = if (isPlaying) spoolAngle else 0f)

                            // Center Tape Ruler Marks
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                repeat(5) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(if (it == 2) 14.dp else 8.dp)
                                            .background(Color(0xFF64748B))
                                    )
                                }
                            }

                            // Right Spool Gear
                            CassetteGear(rotation = if (isPlaying) spoolAngle else 0f)
                        }
                    }
                }
            }

            // Bottom Cassette Tape Head Trapezoid Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF475569))
                    )
                }
            }
        }
    }
}

@Composable
private fun CassetteGear(rotation: Float) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .graphicsLayer { rotationZ = rotation },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f

            // White plastic gear ring
            drawCircle(color = Color(0xFFEDEDF2), radius = radius, center = center)
            drawCircle(color = Color(0xFF0F0F14), radius = radius * 0.45f, center = center)

            // 6 Gear teeth
            for (i in 0 until 6) {
                val angleRad = Math.toRadians((i * 60).toDouble())
                val toothX = center.x + (radius * 0.65f * kotlin.math.cos(angleRad)).toFloat()
                val toothY = center.y + (radius * 0.65f * kotlin.math.sin(angleRad)).toFloat()
                drawCircle(color = Color(0xFF4A4A5A), radius = 2.dp.toPx(), center = Offset(toothX, toothY))
            }
        }
    }
}

/**
 * Car Dashboard Night Drive Mode Skin.
 * Features:
 * - High-contrast, minimal distraction-free night mode
 * - Giant 80dp touch targets for Play/Pause and Skip
 * - Large glanceable track metadata and progress display
 * - Quick one-tap exit button
 */
@Composable
fun CarDashboardSkin(
    track: VideoItem,
    isPlaying: Boolean,
    currentSeconds: Float,
    durationSeconds: Float,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onExitCarMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val progress = (currentSeconds / durationSeconds.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val curMins = (currentSeconds / 60).toInt()
    val curSecs = (currentSeconds % 60).toInt()
    val durMins = (durationSeconds / 60).toInt()
    val durSecs = (durationSeconds % 60).toInt()
    val timeFormatted = "%d:%02d / %d:%02d".format(curMins, curSecs, durMins, durSecs)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0E))
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Car Mode Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DirectionsCar,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CAR MODE",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }

            FilledTonalButton(
                onClick = onExitCarMode,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0xFF222433),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Exit", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Exit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Glanceable Album Art & Track Info
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(BorderStroke(2.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)), RoundedCornerShape(24.dp))
                    .shadow(24.dp, RoundedCornerShape(24.dp), ambientColor = Color(0xFF38BDF8))
            ) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = track.title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = track.artist,
                color = Color(0xFF94A3B8),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Time & Progress Bar
            Text(
                text = timeFormatted,
                color = Color(0xFF38BDF8),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Color(0xFF38BDF8),
                trackColor = Color(0xFF26263A)
            )
        }

        // Giant Driving Controls (Safe, fast, big hit targets)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Big Prev
            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onPrev()
                },
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E202E))
                    .border(BorderStroke(1.5.dp, Color(0xFF3B3E54)), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Massive Play/Pause (84dp)
            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onPlayPause()
                },
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF38BDF8), Color(0xFF0284C7))
                        )
                    )
                    .shadow(16.dp, CircleShape, spotColor = Color(0xFF38BDF8))
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.Black,
                    modifier = Modifier.size(48.dp)
                )
            }

            // Big Next
            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onNext()
                },
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E202E))
                    .border(BorderStroke(1.5.dp, Color(0xFF3B3E54)), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

/**
 * Vintage Player Skin Selector Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkinSelectorSheet(
    currentSkin: PlayerSkin,
    onSelectSkin: (PlayerSkin) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF20F101A),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.35f)) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .padding(bottom = 36.dp)
        ) {
            Text(
                text = "Player Vintage Skins",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Choose your favorite full-screen playback aesthetic",
                color = Color(0xFF9E9EAF),
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(20.dp))

            PlayerSkin.values().forEach { skin ->
                val isSelected = skin == currentSkin
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isSelected) Color(0xFF8B5CF6).copy(alpha = 0.22f)
                            else Color.White.copy(alpha = 0.05f)
                        )
                        .border(
                            1.dp,
                            if (isSelected) Color(0xFFC084FC).copy(alpha = 0.8f)
                            else Color.White.copy(alpha = 0.12f),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { onSelectSkin(skin) }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = skin.icon, fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = skin.label,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        val desc = when (skin) {
                            PlayerSkin.MODERN -> "Clean album artwork & animated lyrics"
                            PlayerSkin.VINYL -> "33 RPM rotating vinyl disc & scratch seeking"
                            PlayerSkin.CASSETTE -> "90s dual spinning reels with handwritten label"
                            PlayerSkin.CAR_DASHBOARD -> "High-contrast night drive mode with giant buttons"
                        }
                        Text(
                            text = desc,
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp
                        )
                    }
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = Color(0xFFC084FC),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
