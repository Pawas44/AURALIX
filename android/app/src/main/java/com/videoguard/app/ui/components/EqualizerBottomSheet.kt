package com.videoguard.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoguard.app.audio.EqualizerEngine
import java.util.Locale
import kotlin.math.roundToInt

/**
 * LastWave-Native Inspired Audiophile 15-Band Equalizer Sheet.
 * Features:
 * - 15 ISO 2/3-Octave center frequencies (25 Hz to 16 kHz)
 * - Gain range: -8 dB to +8 dB (0.1 dB resolution)
 * - Interactive Dynamic Bézier Spline Curve Visualizer with dashed 0 dB baseline & ±8 dB grids
 * - Tactile Hardware Capsule Sliders with double-grip ridges & dynamic baseline fill
 * - Band categories: SUB, BASS, LOW-MID, MID, HIGH-MID, PRES, AIR
 * - LastWave Curated Audiophile Presets (Studio Master, Deep Bass Clean, Vocal Clarity, etc.)
 * - Bass Boost & 3D Spatial Virtualizer enhancers
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerBottomSheet(
    onDismiss: () -> Unit
) {
    val isEnabled by EqualizerEngine.isEnabled.collectAsState()
    val currentPreset by EqualizerEngine.currentPreset.collectAsState()
    val bandLevels by EqualizerEngine.bandLevels.collectAsState()
    val bassBoost by EqualizerEngine.bassBoostPercent.collectAsState()
    val virtualizer by EqualizerEngine.virtualizerPercent.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF20F0F16), // Dark obsidian
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.35f)) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header: Title, Reset Button, Master Switch, Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22173B))
                            .border(BorderStroke(1.dp, Color(0xFFC084FC).copy(alpha = 0.5f)), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = Color(0xFFC084FC),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "EQUALIZER",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF8B5CF6).copy(alpha = 0.2f),
                                border = BorderStroke(0.5.dp, Color(0xFFC084FC).copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "15-BAND ISO",
                                    color = Color(0xFFD8B4FE),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                )
                            }
                        }
                        Text(
                            text = "Audiophile DSP • LastWave Native Engine",
                            color = Color(0xFF9E9EAF),
                            fontSize = 11.5.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Reset to Flat button
                    IconButton(
                        onClick = { EqualizerEngine.resetToFlat() },
                        enabled = isEnabled,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset EQ",
                            tint = if (isEnabled) Color(0xFFA5ABC0) else Color(0xFF555566),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { EqualizerEngine.setEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF8B5CF6),
                            uncheckedThumbColor = Color(0xFF71717A),
                            uncheckedTrackColor = Color(0xFF27272A)
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF222230), thickness = 0.8.dp, modifier = Modifier.padding(vertical = 8.dp))

            // Interactive Dynamic Bézier Frequency Spline Curve Visualizer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .height(130.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF13131F))
                    .border(BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.35f)), RoundedCornerShape(18.dp))
            ) {
                EqualizerCurveGraph(
                    bandLevels = bandLevels,
                    isEnabled = isEnabled,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Presets Horizontal Chips Row
            Text(
                text = "AUDIOPHILE PRESETS",
                color = Color(0xFFA5ABC0),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EqualizerEngine.PRESETS.keys.forEach { presetName ->
                    val isSelected = currentPreset == presetName
                    Surface(
                        onClick = {
                            if (isEnabled) {
                                EqualizerEngine.setPreset(presetName)
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) Color(0xFF381F68) else Color(0xFF1B1B28),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFFC084FC) else Color.White.copy(alpha = 0.08f)
                        ),
                        modifier = Modifier.shadow(
                            elevation = if (isSelected) 6.dp else 0.dp,
                            shape = RoundedCornerShape(20.dp),
                            ambientColor = Color(0xFF8B5CF6)
                        )
                    ) {
                        Text(
                            text = presetName,
                            color = if (isSelected) Color(0xFFF3E8FF) else Color(0xFFC2C4D6),
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 15-Band Hardware Capsule Tactile Slider Rack
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF151522),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "15-BAND ISO CAPSULE CONSOLE",
                            color = Color(0xFFA5ABC0),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "-8 dB  to  +8 dB",
                            color = Color(0xFF717188),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Horizontal scrollable rack of 15 vertical capsule sliders
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        EqualizerEngine.EQ_BAND_FREQS_HZ.forEachIndexed { index, freqHz ->
                            val level = bandLevels.getOrElse(index) { 0.0f }
                            val category = EqualizerEngine.getCategoryForBand(freqHz)
                            val label = EqualizerEngine.FREQUENCY_LABELS[index]

                            CapsuleBandSlider(
                                index = index,
                                label = label,
                                category = category,
                                gainDb = level,
                                isEnabled = isEnabled,
                                onGainChange = { newDb ->
                                    EqualizerEngine.setBandLevel(index, newDb)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Audio FX Section: Bass Boost & 3D Spatial Virtualizer
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF151522),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ENHANCERS & SOUNDSTAGE",
                        color = Color(0xFFA5ABC0),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Bass Boost Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speaker, contentDescription = null, tint = Color(0xFFC084FC), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Deep Bass Boost", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text("$bassBoost%", color = if (isEnabled) Color(0xFFC084FC) else Color(0xFF888899), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Slider(
                        value = bassBoost.toFloat(),
                        onValueChange = {
                            if (isEnabled) EqualizerEngine.setBassBoost(it.toInt())
                        },
                        valueRange = 0f..100f,
                        enabled = isEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFC084FC),
                            activeTrackColor = Color(0xFF8B5CF6),
                            inactiveTrackColor = Color(0xFF282838)
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // 3D Virtualizer (Surround Sound) Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SurroundSound, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("3D Spatial Virtualizer", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text("$virtualizer%", color = if (isEnabled) Color(0xFF38BDF8) else Color(0xFF888899), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Slider(
                        value = virtualizer.toFloat(),
                        onValueChange = {
                            if (isEnabled) EqualizerEngine.setVirtualizer(it.toInt())
                        },
                        valueRange = 0f..100f,
                        enabled = isEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF38BDF8),
                            activeTrackColor = Color(0xFF0284C7),
                            inactiveTrackColor = Color(0xFF282838)
                        )
                    )
                }
            }
        }
    }
}

/**
 * LastWave Bézier Spline Curve Canvas Visualizer.
 * Draws:
 * - Top +8 dB dashed grid line
 * - Center 0 dB dashed baseline
 * - Bottom -8 dB dashed grid line
 * - Smooth cubic Bézier spline across the 15 band points
 * - Vertical gradient area fill from curve down to center
 * - Highlight nodes for each band
 */
@Composable
fun EqualizerCurveGraph(
    bandLevels: List<Float>,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f
        val maxGain = EqualizerEngine.EQ_MAX_GAIN_DB
        val stepX = w / (bandLevels.size - 1).coerceAtLeast(1)

        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

        // +8 dB Top Grid line
        drawLine(
            color = Color.White.copy(alpha = 0.08f),
            start = Offset(0f, 6f),
            end = Offset(w, 6f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = dashEffect
        )

        // Center 0 dB Baseline
        drawLine(
            color = Color.White.copy(alpha = 0.20f),
            start = Offset(0f, centerY),
            end = Offset(w, centerY),
            strokeWidth = 1.2.dp.toPx(),
            pathEffect = dashEffect
        )

        // -8 dB Bottom Grid line
        drawLine(
            color = Color.White.copy(alpha = 0.08f),
            start = Offset(0f, h - 6f),
            end = Offset(w, h - 6f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = dashEffect
        )

        if (!isEnabled || bandLevels.isEmpty()) return@Canvas

        val points = bandLevels.mapIndexed { i, db ->
            val x = i * stepX
            val normalized = (db / maxGain).coerceIn(-1f, 1f)
            val y = centerY - (normalized * (centerY - 10f))
            Offset(x, y)
        }

        val splinePath = Path()
        splinePath.moveTo(points[0].x, points[0].y)
        for (i in 0 until points.size - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]
            val cpX = (p0.x + p1.x) / 2f
            splinePath.cubicTo(cpX, p0.y, cpX, p1.y, p1.x, p1.y)
        }

        // Closed path for subtle gradient fill
        val fillPath = Path().apply {
            addPath(splinePath)
            lineTo(points.last().x, h)
            lineTo(points.first().x, h)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF8B5CF6).copy(alpha = 0.35f),
                    Color(0xFF38BDF8).copy(alpha = 0.12f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = h
            )
        )

        // Spline Curve Stroke
        drawPath(
            path = splinePath,
            brush = Brush.horizontalGradient(
                listOf(
                    Color(0xFF8B5CF6),
                    Color(0xFFC084FC),
                    Color(0xFF38BDF8),
                    Color(0xFF818CF8)
                )
            ),
            style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
        )

        // Highlight Glowing Node Points
        points.forEach { pt ->
            drawCircle(
                color = Color(0xFFC084FC),
                radius = 3.5.dp.toPx(),
                center = pt
            )
            drawCircle(
                color = Color.White,
                radius = 1.8.dp.toPx(),
                center = pt
            )
        }
    }
}

/**
 * Tactile Hardware Capsule Band Slider.
 * Emulates the tactile LastWave-Native mixer capsule thumb with double-grip ridges
 * and dynamic baseline fill.
 */
@Composable
fun CapsuleBandSlider(
    index: Int,
    label: String,
    category: String,
    gainDb: Float,
    isEnabled: Boolean,
    onGainChange: (Float) -> Unit
) {
    val view = LocalView.current
    var wasZero by remember { mutableStateOf(gainDb == 0f) }

    // Category badge color
    val catColor = when (category) {
        "SUB" -> Color(0xFFF43F5E)
        "BASS" -> Color(0xFFFB923C)
        "LOW-MID" -> Color(0xFFFBBF24)
        "MID" -> Color(0xFF34D399)
        "HIGH-MID" -> Color(0xFF38BDF8)
        "PRES" -> Color(0xFF818CF8)
        else -> Color(0xFFA855F7) // AIR
    }

    val formattedDb = if (gainDb > 0) "+%.1f".format(Locale.US, gainDb) else "%.1f".format(Locale.US, gainDb)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1B1B29))
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        // Category Tag Badge
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = catColor.copy(alpha = 0.2f),
            border = BorderStroke(0.5.dp, catColor.copy(alpha = 0.5f))
        ) {
            Text(
                text = category,
                color = catColor,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // dB Readout
        Text(
            text = formattedDb,
            color = if (gainDb != 0f && isEnabled) Color(0xFFE2E8F0) else Color(0xFF717188),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Vertical Tactile Capsule Track & Thumb
        val sliderHeight = 140.dp
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(sliderHeight)
                .pointerInput(isEnabled) {
                    if (!isEnabled) return@pointerInput
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        // 140dp total, -8 to +8 dB (16 dB span)
                        val deltaDb = -(dragAmount / 140f.coerceAtLeast(1f)) * 16f
                        val newDb = ((gainDb + deltaDb) * 10f).roundToInt() / 10f
                        val clamped = newDb.coerceIn(-EqualizerEngine.EQ_MAX_GAIN_DB, EqualizerEngine.EQ_MAX_GAIN_DB)

                        // Haptic feedback when crossing 0 dB baseline
                        if ((gainDb > 0f && clamped <= 0f) || (gainDb < 0f && clamped >= 0f)) {
                            if (!wasZero) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                wasZero = true
                            }
                        } else {
                            wasZero = false
                        }

                        onGainChange(clamped)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trackWidth = 8.dp.toPx()
                val trackHeight = size.height
                val trackX = (size.width - trackWidth) / 2f
                val centerY = trackHeight / 2f
                val maxGain = EqualizerEngine.EQ_MAX_GAIN_DB

                // Background capsule track
                drawRoundRect(
                    color = Color(0xFF26263A),
                    topLeft = Offset(trackX, 0f),
                    size = Size(trackWidth, trackHeight),
                    cornerRadius = CornerRadius(trackWidth / 2f, trackWidth / 2f)
                )

                // 0 dB center tick mark
                drawLine(
                    color = Color.White.copy(alpha = 0.4f),
                    start = Offset(trackX - 3.dp.toPx(), centerY),
                    end = Offset(trackX + trackWidth + 3.dp.toPx(), centerY),
                    strokeWidth = 1.5.dp.toPx()
                )

                // Dynamic baseline fill from 0 dB to thumb
                val thumbY = centerY - (gainDb / maxGain) * (centerY - 12.dp.toPx())
                if (gainDb != 0f && isEnabled) {
                    val fillTop = minOf(centerY, thumbY)
                    val fillHeight = kotlin.math.abs(centerY - thumbY)
                    val fillColor = if (gainDb > 0) Color(0xFFC084FC) else Color(0xFF38BDF8)

                    drawRoundRect(
                        color = fillColor.copy(alpha = 0.85f),
                        topLeft = Offset(trackX, fillTop),
                        size = Size(trackWidth, fillHeight),
                        cornerRadius = CornerRadius(trackWidth / 2f, trackWidth / 2f)
                    )
                }

                // Hardware capsule thumb with double-grip ridges
                val thumbWidth = 26.dp.toPx()
                val thumbHeight = 36.dp.toPx()
                val thumbLeft = (size.width - thumbWidth) / 2f
                val thumbTop = thumbY - (thumbHeight / 2f)

                // Thumb outer capsule
                drawRoundRect(
                    color = if (isEnabled) Color(0xFFE0E7FF) else Color(0xFF4B5563),
                    topLeft = Offset(thumbLeft, thumbTop),
                    size = Size(thumbWidth, thumbHeight),
                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                )

                // Thumb border
                drawRoundRect(
                    color = Color(0xFF1E1B4B),
                    topLeft = Offset(thumbLeft, thumbTop),
                    size = Size(thumbWidth, thumbHeight),
                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                    style = Stroke(width = 1.2.dp.toPx())
                )

                // Tactile hardware double horizontal grip ridges
                val gripColor = Color(0xFF475569)
                val gripWidth = 12.dp.toPx()
                val gripLeft = (size.width - gripWidth) / 2f
                val ridge1Y = thumbY - 3.5.dp.toPx()
                val ridge2Y = thumbY + 3.5.dp.toPx()

                drawLine(
                    color = gripColor,
                    start = Offset(gripLeft, ridge1Y),
                    end = Offset(gripLeft + gripWidth, ridge1Y),
                    strokeWidth = 1.6.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = gripColor,
                    start = Offset(gripLeft, ridge2Y),
                    end = Offset(gripLeft + gripWidth, ridge2Y),
                    strokeWidth = 1.6.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Band Frequency Label (e.g. 25 Hz, 1 kHz, 16 kHz)
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
