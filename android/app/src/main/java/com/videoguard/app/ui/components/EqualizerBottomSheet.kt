package com.videoguard.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoguard.app.audio.EqualizerEngine
import kotlin.math.roundToInt

/**
 * Butter-Smooth Pro Studio Graphic Equalizer.
 * Designed with native Material 3 Slider components for instant, zero-lag touch response.
 * Features:
 * - 10 ISO Octave Bands (31 Hz to 16 kHz) with sound description tags
 * - 12 Curated Presets (Bass Boost, Vocal Clarity, Rock, EDM, etc.)
 * - Live Bézier Spline Graphic Visualizer
 * - Deep Bass Boost & 3D Spatial Virtualizer enhancers
 * - 1-Tap Reset button
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
    val view = LocalView.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF20F1018), // Obsidian glass
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.35f)) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2E1955))
                            .border(BorderStroke(1.dp, Color(0xFFC084FC).copy(alpha = 0.6f)), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = Color(0xFFC084FC),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "STUDIO EQUALIZER",
                                color = Color.White,
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF8B5CF6).copy(alpha = 0.25f),
                                border = BorderStroke(0.5.dp, Color(0xFFC084FC).copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "10-BAND ISO",
                                    color = Color(0xFFD8B4FE),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Real-time DSP • App Audio Only",
                            color = Color(0xFF9E9EAF),
                            fontSize = 11.5.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Reset to Flat button
                    FilledTonalIconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            EqualizerEngine.resetToFlat()
                        },
                        enabled = isEnabled,
                        modifier = Modifier.size(34.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color(0xFF1F202E),
                            contentColor = Color(0xFFD1D5DB)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset EQ",
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Switch(
                        checked = isEnabled,
                        onCheckedChange = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            EqualizerEngine.setEnabled(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF8B5CF6),
                            uncheckedThumbColor = Color(0xFF71717A),
                            uncheckedTrackColor = Color(0xFF27272A)
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }

            HorizontalDivider(
                color = Color(0xFF222230),
                thickness = 0.8.dp,
                modifier = Modifier.padding(vertical = 10.dp)
            )

            // Live Graphic Bézier Spline Curve Visualizer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp)
                    .height(110.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF13141F))
                    .border(BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.35f)), RoundedCornerShape(18.dp))
            ) {
                LiveSplineCurveGraph(
                    bandLevels = bandLevels,
                    isEnabled = isEnabled,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Presets Horizontal Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PRESETS",
                    color = Color(0xFFA5ABC0),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Active: $currentPreset",
                    color = Color(0xFFC084FC),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EqualizerEngine.PRESETS.keys.forEach { presetName ->
                    val isSelected = currentPreset == presetName
                    Surface(
                        onClick = {
                            if (isEnabled) {
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                EqualizerEngine.setPreset(presetName)
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) Color(0xFF451A7A) else Color(0xFF191A28),
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
                            color = if (isSelected) Color(0xFFF5F3FF) else Color(0xFFC0C2D4),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 10-Band Sliders with Butter-Smooth Material 3 Sliders
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF141522),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FREQUENCY BANDS",
                            color = Color(0xFFA5ABC0),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "-10 dB  to  +10 dB",
                            color = Color(0xFF717188),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    EqualizerEngine.FREQUENCIES.forEachIndexed { index, _ ->
                        val label = EqualizerEngine.FREQUENCY_LABELS[index]
                        val desc = EqualizerEngine.FREQUENCY_DESCRIPTIONS[index]
                        val level = bandLevels.getOrElse(index) { 0f }
                        val formattedDb = if (level > 0) "+${level.roundToInt()} dB" else "${level.roundToInt()} dB"

                        Column(modifier = Modifier.padding(vertical = 5.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = label,
                                        color = Color.White,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "• $desc",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (level != 0f && isEnabled) Color(0xFF8B5CF6).copy(alpha = 0.25f) else Color(0xFF222230),
                                    border = BorderStroke(0.5.dp, if (level != 0f && isEnabled) Color(0xFFC084FC).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f))
                                ) {
                                    Text(
                                        text = formattedDb,
                                        color = if (level != 0f && isEnabled) Color(0xFFD8B4FE) else Color(0xFF78788C),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Native Material 3 Slider (Butter smooth, zero touch conflict)
                            Slider(
                                value = level,
                                onValueChange = { newGain ->
                                    if (isEnabled) {
                                        val rounded = newGain.roundToInt().toFloat()
                                        if (rounded != level) {
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        }
                                        EqualizerEngine.setBandLevel(index, rounded)
                                    }
                                },
                                valueRange = -10f..10f,
                                steps = 19, // 1 dB per step
                                enabled = isEnabled,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFC084FC),
                                    activeTrackColor = Color(0xFF8B5CF6),
                                    inactiveTrackColor = Color(0xFF272738),
                                    disabledThumbColor = Color(0xFF4B4B58),
                                    disabledActiveTrackColor = Color(0xFF323242)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Enhancers: Deep Bass Boost & 3D Spatial Virtualizer
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF141522),
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
                            if (isEnabled) EqualizerEngine.setBassBoost(it.roundToInt())
                        },
                        valueRange = 0f..100f,
                        enabled = isEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFC084FC),
                            activeTrackColor = Color(0xFF8B5CF6),
                            inactiveTrackColor = Color(0xFF272738)
                        ),
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 3D Spatial Virtualizer Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SurroundSound, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("3D Spatial Soundstage", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text("$virtualizer%", color = if (isEnabled) Color(0xFF38BDF8) else Color(0xFF888899), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Slider(
                        value = virtualizer.toFloat(),
                        onValueChange = {
                            if (isEnabled) EqualizerEngine.setVirtualizer(it.roundToInt())
                        },
                        valueRange = 0f..100f,
                        enabled = isEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF38BDF8),
                            activeTrackColor = Color(0xFF0284C7),
                            inactiveTrackColor = Color(0xFF272738)
                        ),
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    )
                }
            }
        }
    }
}

/**
 * Live Continuous Spline Visualizer Canvas.
 */
@Composable
fun LiveSplineCurveGraph(
    bandLevels: List<Float>,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f
        val maxGain = EqualizerEngine.MAX_GAIN_DB
        val stepX = w / (bandLevels.size - 1).coerceAtLeast(1)

        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

        // Center 0 dB Baseline
        drawLine(
            color = Color.White.copy(alpha = 0.15f),
            start = Offset(0f, centerY),
            end = Offset(w, centerY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = dashEffect
        )

        if (!isEnabled || bandLevels.isEmpty()) return@Canvas

        val points = bandLevels.mapIndexed { i, db ->
            val x = i * stepX
            val normalized = (db / maxGain).coerceIn(-1f, 1f)
            val y = centerY - (normalized * (centerY - 8f))
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

        // Fill below spline
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
                    Color(0xFF38BDF8).copy(alpha = 0.10f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = h
            )
        )

        // Stroke line
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
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )

        // Node dots
        points.forEach { pt ->
            drawCircle(
                color = Color(0xFFC084FC),
                radius = 3.dp.toPx(),
                center = pt
            )
            drawCircle(
                color = Color.White,
                radius = 1.5.dp.toPx(),
                center = pt
            )
        }
    }
}
