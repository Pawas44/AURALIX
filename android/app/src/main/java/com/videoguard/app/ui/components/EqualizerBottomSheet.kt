package com.videoguard.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoguard.app.audio.EqualizerEngine

/**
 * Tunex-inspired Premium Equalizer & Audio FX BottomSheet.
 * Provides:
 * - 5-Band Dynamic Frequency Sliders (-12 dB to +12 dB)
 * - Live Graphic Frequency Curve Visualizer
 * - 10 Audio Presets (Flat, Bass Boost, Rock, Pop, Hip Hop, Electronic, Jazz, Classical, Vocal Boost, Custom)
 * - Bass Boost dial/slider (0% - 100%)
 * - 3D Surround Virtualizer dial/slider (0% - 100%)
 * - Modifies ONLY this app's audio output (isolated audioSessionId).
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
        containerColor = Color(0xF2101016), // Dark obsidian
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.35f)) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header: Title, Master Switch, Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 6.dp),
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
                        Text(
                            text = "Equalizer",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "App audio only • Tunex Engine",
                            color = Color(0xFF9E9EAF),
                            fontSize = 11.5.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF222230), thickness = 0.8.dp, modifier = Modifier.padding(vertical = 8.dp))

            // Animated Graphical Frequency Curve Visualizer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .height(95.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF161622))
                    .border(BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.35f)), RoundedCornerShape(16.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 14.dp)) {
                    val w = size.width
                    val h = size.height
                    val centerY = h / 2f
                    val stepX = w / 4f

                    // Zero-line guide
                    drawLine(
                        color = Color.White.copy(alpha = 0.12f),
                        start = Offset(0f, centerY),
                        end = Offset(w, centerY),
                        strokeWidth = 1.dp.toPx()
                    )

                    if (isEnabled) {
                        val path = Path()
                        val points = bandLevels.mapIndexed { i, db ->
                            val x = i * stepX
                            // Map -12 dB .. +12 dB to height (inverted)
                            val normalized = (db / 12f).coerceIn(-1f, 1f)
                            val y = centerY - (normalized * (h * 0.40f))
                            Offset(x, y)
                        }

                        path.moveTo(points[0].x, points[0].y)
                        for (i in 0 until points.size - 1) {
                            val p0 = points[i]
                            val p1 = points[i + 1]
                            val controlX = (p0.x + p1.x) / 2f
                            path.cubicTo(controlX, p0.y, controlX, p1.y, p1.x, p1.y)
                        }

                        // Gradient stroke line
                        drawPath(
                            path = path,
                            brush = Brush.horizontalGradient(
                                listOf(Color(0xFF8B5CF6), Color(0xFFC084FC), Color(0xFFA855F7))
                            ),
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Data dots
                        points.forEach { pt ->
                            drawCircle(
                                color = Color(0xFFC084FC),
                                radius = 4.dp.toPx(),
                                center = pt
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 2.dp.toPx(),
                                center = pt
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Presets Horizontal Chips Row
            Text(
                text = "PRESETS",
                color = Color(0xFFA5ABC0),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 4.dp),
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

            // 5 Band Sliders
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF171724),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "FREQUENCY BANDS",
                        color = Color(0xFFA5ABC0),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    EqualizerEngine.FREQUENCY_LABELS.forEachIndexed { index, label ->
                        val level = bandLevels.getOrElse(index) { 0 }
                        val levelText = if (level > 0) "+$level dB" else "$level dB"

                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = levelText,
                                    color = if (level != 0 && isEnabled) Color(0xFFC084FC) else Color(0xFF888899),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Slider(
                                value = level.toFloat(),
                                onValueChange = {
                                    if (isEnabled) {
                                        EqualizerEngine.setBandLevel(index, it.toInt())
                                    }
                                },
                                valueRange = -12f..12f,
                                steps = 23, // 1 dB steps
                                enabled = isEnabled,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFC084FC),
                                    activeTrackColor = Color(0xFF8B5CF6),
                                    inactiveTrackColor = Color(0xFF282838),
                                    disabledThumbColor = Color(0xFF555566),
                                    disabledActiveTrackColor = Color(0xFF333344)
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Audio FX Section: Bass Boost & 3D Virtualizer
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF171724),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ENHANCERS",
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
                            Text("Bass Boost", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
                            Text("3D Virtualizer (Surround)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
