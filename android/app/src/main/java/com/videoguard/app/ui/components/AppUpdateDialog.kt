package com.videoguard.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141624)),
                border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF3395FF).copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Glowing Update Icon
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF3395FF), Color(0xFF40DCA5))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SystemUpdate,
                            contentDescription = "Update Available",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Title & Version
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "New Update Available! 🚀",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF3395FF).copy(alpha = 0.18f))
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "AURALIX ${info.tagName}",
                                color = Color(0xFF3395FF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "A new version of AURALIX is available with the latest features, performance improvements, and bug fixes.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    // Download Progress (when active)
                    AnimatedVisibility(visible = isDownloading) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.05f))
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
                                    color = Color(0xFF40DCA5),
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
                                color = Color(0xFF3395FF),
                                trackColor = Color.White.copy(alpha = 0.15f)
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

                    // Action Buttons
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                AppUpdateManager.downloadAndInstallUpdate(context)
                            },
                            enabled = !isDownloading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3395FF),
                                disabledContainerColor = Color(0xFF3395FF).copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text(
                                text = if (isDownloading) "Downloading ($progress%)..." else "Click Here to Update Now ⚡",
                                color = Color.White,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (!isDownloading) {
                            TextButton(
                                onClick = { AppUpdateManager.dismissDialog() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Later",
                                    color = Color(0xFFA0AEC0),
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
}
