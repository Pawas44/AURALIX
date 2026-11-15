package com.videoguard.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
                colors = CardDefaults.cardColors(containerColor = Color(0xFF16181F)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2B2E38))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Hero Art: Cosmic Rocket Launch Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(185.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF1E212A))
                    ) {
                        RocketLaunchBanner()

                        // Frosted Pill Badge (Version Tag)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.50f))
                                .border(0.8.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "✨ v${info.tagName.removePrefix("v")}",
                                color = Color(0xFFFFD166),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Clean, Elegant Headline matching reference design
                    Text(
                        text = "Upgrade to the new version of our app",
                        color = Color.White,
                        fontSize = 18.5.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Friendly, Natural Microcopy
                    Text(
                        text = "We've made some major improvements to give you a faster experience and new features you'll love. Download the latest version now to keep things rolling smoothly!",
                        color = Color(0xFF9EABB8),
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Checklist matching reference design with stylish check boxes
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        UpdateFeatureCheckItem("Improved user interface")
                        UpdateFeatureCheckItem("More reliable streaming")
                        UpdateFeatureCheckItem("Easier to navigate")
                    }

                    // Download Progress Section (when downloading)
                    AnimatedVisibility(visible = isDownloading) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF20232D))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Downloading update ($progress%)...",
                                    color = Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "$progress%",
                                    color = Color(0xFFFF5436),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFFFF5436),
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

                    Spacer(modifier = Modifier.height(20.dp))

                    // Solid Coral Pill Action Button ("Update Now")
                    Button(
                        onClick = {
                            AppUpdateManager.downloadAndInstallUpdate(context)
                        },
                        enabled = !isDownloading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF5436),
                            disabledContainerColor = Color(0xFFFF5436).copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(26.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text(
                            text = if (isDownloading) "Downloading update ($progress%)..." else "Update Now",
                            color = Color.White,
                            fontSize = 15.5.sp,
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
                                text = "Maybe later",
                                color = Color(0xFF71788A),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateFeatureCheckItem(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF282B35))
                .border(0.8.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(11.dp)) {
                val strokeWidth = 1.8.dp.toPx()
                val path = Path().apply {
                    moveTo(size.width * 0.15f, size.height * 0.50f)
                    lineTo(size.width * 0.42f, size.height * 0.80f)
                    lineTo(size.width * 0.88f, size.height * 0.20f)
                }
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = 0.95f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = strokeWidth,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = text,
            color = Color(0xFFD6DBE5),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun RocketLaunchBanner() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1. Deep Celestial Space Gradient
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF141724), Color(0xFF222634))
            )
        )

        // 2. Stars & Musical Cosmic Dust
        val starCoords = listOf(
            Offset(w * 0.15f, h * 0.22f),
            Offset(w * 0.28f, h * 0.15f),
            Offset(w * 0.42f, h * 0.28f),
            Offset(w * 0.78f, h * 0.16f),
            Offset(w * 0.88f, h * 0.32f),
            Offset(w * 0.18f, h * 0.45f),
            Offset(w * 0.84f, h * 0.55f),
            Offset(w * 0.35f, h * 0.10f),
            Offset(w * 0.62f, h * 0.12f)
        )
        for ((index, pos) in starCoords.withIndex()) {
            val r = if (index % 2 == 0) 2.2f else 1.5f
            val alpha = if (index % 3 == 0) 0.95f else 0.60f
            drawCircle(Color.White.copy(alpha = alpha), radius = r.dp.toPx(), center = pos)
        }

        // Planetary accent dot
        drawCircle(Color(0xFF93C5FD), radius = 2.dp.toPx(), center = Offset(w * 0.25f, h * 0.26f))

        // 3. Crescent Moon (Upper right)
        val moonCenter = Offset(w * 0.75f, h * 0.32f)
        val moonRadius = 26.dp.toPx()
        drawCircle(Color(0xFFF3E8FF), radius = moonRadius, center = moonCenter)
        // Cutout circle for soft realistic crescent
        drawCircle(Color(0xFF222533), radius = moonRadius * 0.92f, center = Offset(moonCenter.x - 10.dp.toPx(), moonCenter.y - 6.dp.toPx()))

        // 4. Soft Billowing Pastel Clouds at launch base
        val smokeColor = Color(0xFFFCE6E2)
        val smokeShadow = Color(0xFFE8C7C1)

        // Puffy cloud cluster
        drawCircle(smokeShadow, radius = 42.dp.toPx(), center = Offset(w * 0.50f, h * 0.94f))
        drawCircle(smokeColor, radius = 38.dp.toPx(), center = Offset(w * 0.44f, h * 0.90f))
        drawCircle(smokeColor, radius = 35.dp.toPx(), center = Offset(w * 0.57f, h * 0.88f))
        drawCircle(smokeColor, radius = 26.dp.toPx(), center = Offset(w * 0.37f, h * 0.82f))
        drawCircle(smokeColor, radius = 23.dp.toPx(), center = Offset(w * 0.61f, h * 0.84f))

        // Rising vapor trail to rocket engine
        drawCircle(smokeShadow, radius = 18.dp.toPx(), center = Offset(w * 0.47f, h * 0.74f))
        drawCircle(smokeColor, radius = 15.dp.toPx(), center = Offset(w * 0.48f, h * 0.68f))
        drawCircle(smokeColor, radius = 11.dp.toPx(), center = Offset(w * 0.46f, h * 0.60f))

        // 5. Rocket Launching Diagonally (Angle ~32°)
        val rocketCenterX = w * 0.52f
        val rocketCenterY = h * 0.42f

        withTransform({
            rotate(degrees = 32f, pivot = Offset(rocketCenterX, rocketCenterY))
        }) {
            // Radiant Flame
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

            // Engine Thruster Nozzle
            drawRoundRect(
                color = Color(0xFF262933),
                topLeft = Offset(rocketCenterX - 11.dp.toPx(), rocketCenterY + 30.dp.toPx()),
                size = Size(22.dp.toPx(), 8.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )

            // Stabilizer Wings (Left & Right)
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

            // Main Fuselage
            val rocketBodyPath = Path().apply {
                moveTo(rocketCenterX - 17.dp.toPx(), rocketCenterY + 30.dp.toPx())
                lineTo(rocketCenterX - 17.dp.toPx(), rocketCenterY - 14.dp.toPx())
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

            // Aerodynamic Body Shadowing
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

            // Round Porthole Cockpit Window
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
