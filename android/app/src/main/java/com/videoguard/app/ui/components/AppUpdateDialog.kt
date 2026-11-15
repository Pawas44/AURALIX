package com.videoguard.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.videoguard.app.data.AppUpdateManager

@Composable
fun AppUpdateDialog() {
    val showDialog by AppUpdateManager.showUpdateDialog.collectAsState()
    val updateInfo by AppUpdateManager.updateInfo.collectAsState()
    val isDownloading by AppUpdateManager.isDownloading.collectAsState()
    val progress by AppUpdateManager.downloadProgress.collectAsState()
    val bytesText by AppUpdateManager.downloadBytesText.collectAsState()
    val context = LocalContext.current

    if (showDialog && updateInfo != null) {
        val info = updateInfo!!
        Dialog(onDismissRequest = {
            if (!isDownloading) {
                AppUpdateManager.dismissDialog()
            }
        }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF191B1F)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C2F36))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Hero Illustration: Retro-Modern Rocket Launching into Night Sky
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF23262D))
                    ) {
                        RocketLaunchBanner()
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Headline
                    Text(
                        text = "Upgrade to the new version of our app",
                        color = Color.White,
                        fontSize = 18.5.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Subtitle / Description
                    Text(
                        text = "We've made some major improvements to give you a faster experience and new features you'll love. Download ${info.tagName} now to keep things rolling smoothly!",
                        color = Color(0xFFA0A5B2),
                        fontSize = 12.5.sp,
                        lineHeight = 17.5.sp,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Feature Checklist Items (Matching reference image)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        UpdateChecklistItem(text = "Improved user interface & player backdrop")
                        UpdateChecklistItem(text = "Download all songs in playlist for offline")
                        UpdateChecklistItem(text = "Personalized Spotify-driven recommendations")
                    }

                    // Download Progress (when active)
                    AnimatedVisibility(visible = isDownloading) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF23262D))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Downloading Update...",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "$progress%",
                                    color = Color(0xFFFF5733),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFFFF5733),
                                trackColor = Color.White.copy(alpha = 0.12f)
                            )

                            if (bytesText.isNotBlank()) {
                                Text(
                                    text = bytesText,
                                    color = Color(0xFFA0AEC0),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Main Action CTA: Pill-shaped Coral Button ("Update Now")
                    Button(
                        onClick = {
                            AppUpdateManager.downloadAndInstallUpdate(context)
                        },
                        enabled = !isDownloading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF5733),
                            disabledContainerColor = Color(0xFFFF5733).copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = if (isDownloading) "Downloading ($progress%)..." else "Update Now",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!isDownloading) {
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = { AppUpdateManager.dismissDialog() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Later",
                                color = Color(0xFF7E8492),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateChecklistItem(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF282B33)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(13.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            color = Color(0xFFDCDFE6),
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
private fun RocketLaunchBanner() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1. Dark Space Gradient
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF1E212A), Color(0xFF252934))
            )
        )

        // 2. Stars
        val starCoords = listOf(
            Offset(w * 0.15f, h * 0.22f),
            Offset(w * 0.28f, h * 0.15f),
            Offset(w * 0.42f, h * 0.28f),
            Offset(w * 0.78f, h * 0.18f),
            Offset(w * 0.88f, h * 0.32f),
            Offset(w * 0.18f, h * 0.45f),
            Offset(w * 0.82f, h * 0.55f),
            Offset(w * 0.35f, h * 0.10f)
        )
        for ((index, pos) in starCoords.withIndex()) {
            val r = if (index % 2 == 0) 2.2f else 1.5f
            val alpha = if (index % 3 == 0) 0.95f else 0.65f
            drawCircle(Color.White.copy(alpha = alpha), radius = r.dp.toPx(), center = pos)
        }

        // Small planetary ring / satellite dot
        drawCircle(Color(0xFFB0B8C4), radius = 2.5f.dp.toPx(), center = Offset(w * 0.25f, h * 0.25f))

        // 3. Crescent Moon (Upper right)
        val moonCenter = Offset(w * 0.74f, h * 0.32f)
        val moonRadius = 26.dp.toPx()
        drawCircle(Color(0xFFE2E7ED), radius = moonRadius, center = moonCenter)
        // Cutout circle to create realistic crescent
        drawCircle(Color(0xFF242732), radius = moonRadius * 0.92f, center = Offset(moonCenter.x - 10.dp.toPx(), moonCenter.y - 6.dp.toPx()))

        // 4. Billowing Smoke Clouds at bottom center
        val smokeColor = Color(0xFFFBE5E1)
        val smokeShadow = Color(0xFFE8C6C0)

        // Lower base smoke cluster
        drawCircle(smokeShadow, radius = 42.dp.toPx(), center = Offset(w * 0.50f, h * 0.94f))
        drawCircle(smokeColor, radius = 38.dp.toPx(), center = Offset(w * 0.45f, h * 0.90f))
        drawCircle(smokeColor, radius = 34.dp.toPx(), center = Offset(w * 0.56f, h * 0.88f))
        drawCircle(smokeColor, radius = 26.dp.toPx(), center = Offset(w * 0.38f, h * 0.82f))
        drawCircle(smokeColor, radius = 22.dp.toPx(), center = Offset(w * 0.60f, h * 0.84f))

        // Rising swirl smoke leading up to rocket tail
        drawCircle(smokeShadow, radius = 18.dp.toPx(), center = Offset(w * 0.47f, h * 0.74f))
        drawCircle(smokeColor, radius = 15.dp.toPx(), center = Offset(w * 0.48f, h * 0.68f))
        drawCircle(smokeColor, radius = 11.dp.toPx(), center = Offset(w * 0.46f, h * 0.60f))

        // 5. Rocket Launch (Pointing diagonally towards upper-right ~35 deg)
        val rocketCenterX = w * 0.52f
        val rocketCenterY = h * 0.42f

        withTransform({
            rotate(degrees = 32f, pivot = Offset(rocketCenterX, rocketCenterY))
        }) {
            // Rocket Exhaust Flame
            val flamePath = Path().apply {
                moveTo(rocketCenterX - 10.dp.toPx(), rocketCenterY + 36.dp.toPx())
                quadraticTo(
                    rocketCenterX, rocketCenterY + 62.dp.toPx(),
                    rocketCenterX + 10.dp.toPx(), rocketCenterY + 36.dp.toPx()
                )
                close()
            }
            drawPath(
                flamePath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFF9800), Color(0xFFFFEB3B)),
                    startY = rocketCenterY + 36.dp.toPx(),
                    endY = rocketCenterY + 62.dp.toPx()
                )
            )

            // Rocket Engine Base
            drawRoundRect(
                color = Color(0xFF2A2D35),
                topLeft = Offset(rocketCenterX - 11.dp.toPx(), rocketCenterY + 30.dp.toPx()),
                size = Size(22.dp.toPx(), 8.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )

            // Left & Right Fins
            val leftFin = Path().apply {
                moveTo(rocketCenterX - 16.dp.toPx(), rocketCenterY + 12.dp.toPx())
                lineTo(rocketCenterX - 30.dp.toPx(), rocketCenterY + 34.dp.toPx())
                lineTo(rocketCenterX - 16.dp.toPx(), rocketCenterY + 32.dp.toPx())
                close()
            }
            drawPath(leftFin, color = Color(0xFFD6361D))

            val rightFin = Path().apply {
                moveTo(rocketCenterX + 16.dp.toPx(), rocketCenterY + 12.dp.toPx())
                lineTo(rocketCenterX + 30.dp.toPx(), rocketCenterY + 34.dp.toPx())
                lineTo(rocketCenterX + 16.dp.toPx(), rocketCenterY + 32.dp.toPx())
                close()
            }
            drawPath(rightFin, color = Color(0xFFD6361D))

            // Main Rocket Fuselage
            val rocketBodyPath = Path().apply {
                moveTo(rocketCenterX - 17.dp.toPx(), rocketCenterY + 30.dp.toPx())
                lineTo(rocketCenterX - 17.dp.toPx(), rocketCenterY - 14.dp.toPx())
                // Aerodynamic nose cone tip
                cubicTo(
                    rocketCenterX - 17.dp.toPx(), rocketCenterY - 36.dp.toPx(),
                    rocketCenterX - 6.dp.toPx(), rocketCenterY - 50.dp.toPx(),
                    rocketCenterX, rocketCenterY - 56.dp.toPx()
                )
                cubicTo(
                    rocketCenterX + 6.dp.toPx(), rocketCenterY - 50.dp.toPx(),
                    rocketCenterX + 17.dp.toPx(), rocketCenterY - 36.dp.toPx(),
                    rocketCenterX + 17.dp.toPx(), rocketCenterY - 14.dp.toPx()
                )
                lineTo(rocketCenterX + 17.dp.toPx(), rocketCenterY + 30.dp.toPx())
                close()
            }
            drawPath(rocketBodyPath, color = Color(0xFFFF5238))

            // Shading highlight on rocket body
            val rocketHighlight = Path().apply {
                moveTo(rocketCenterX + 4.dp.toPx(), rocketCenterY + 28.dp.toPx())
                lineTo(rocketCenterX + 13.dp.toPx(), rocketCenterY + 28.dp.toPx())
                lineTo(rocketCenterX + 13.dp.toPx(), rocketCenterY - 14.dp.toPx())
                cubicTo(
                    rocketCenterX + 13.dp.toPx(), rocketCenterY - 32.dp.toPx(),
                    rocketCenterX + 5.dp.toPx(), rocketCenterY - 44.dp.toPx(),
                    rocketCenterX, rocketCenterY - 52.dp.toPx()
                )
                lineTo(rocketCenterX + 2.dp.toPx(), rocketCenterY - 50.dp.toPx())
                close()
            }
            drawPath(rocketHighlight, color = Color(0xFFE83E24))

            // Cockpit Window (Circular porthole)
            drawCircle(
                color = Color.White,
                radius = 11.dp.toPx(),
                center = Offset(rocketCenterX, rocketCenterY - 10.dp.toPx())
            )
            drawCircle(
                color = Color(0xFF38BDF8),
                radius = 8.dp.toPx(),
                center = Offset(rocketCenterX, rocketCenterY - 10.dp.toPx())
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.8f),
                radius = 2.5f.dp.toPx(),
                center = Offset(rocketCenterX + 3.dp.toPx(), rocketCenterY - 13.dp.toPx())
            )
        }
    }
}
