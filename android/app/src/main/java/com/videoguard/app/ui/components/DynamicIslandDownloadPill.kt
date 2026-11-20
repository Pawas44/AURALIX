package com.videoguard.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoguard.app.data.UserDataRepository

@Composable
fun DynamicIslandDownloadPill(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val batchDownload by UserDataRepository.batchDownload.collectAsState()
    val currentDownload by UserDataRepository.currentDownload.collectAsState()

    var showCompletedSingle by remember { mutableStateOf(false) }
    var lastCompletedTitle by remember { mutableStateOf("") }

    LaunchedEffect(currentDownload?.isComplete) {
        if (currentDownload?.isComplete == true) {
            showCompletedSingle = true
            lastCompletedTitle = currentDownload?.title ?: ""
            // Disappear quickly after completion as requested ("download hone ke baad ht jaana chahia")
            kotlinx.coroutines.delay(800)
            showCompletedSingle = false
        }
    }

    val isVisible = batchDownload != null || (currentDownload != null && !currentDownload!!.isComplete) || showCompletedSingle

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        val batch = batchDownload
        val single = currentDownload

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                onClick = {
                    showCompletedSingle = false
                    onClick()
                },
                shape = RoundedCornerShape(26.dp),
                color = Color(0xF80E1018), // Deep obsidian glass
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color(0xFF00E676).copy(alpha = 0.50f),
                            Color.White.copy(alpha = 0.15f)
                        )
                    )
                ),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .wrapContentWidth()
                    .widthIn(min = 220.dp, max = 340.dp)
            ) {
                if (batch != null) {
                    // Batch Playlist Download Capsule
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E676).copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (batch.isComplete) Icons.Default.Check else Icons.Default.Download,
                                    contentDescription = null,
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.size(13.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = if (batch.isComplete) "✓ All ${batch.totalTracks} downloaded" else "Downloading (${batch.currentTrackIndex}/${batch.totalTracks})",
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = batch.currentSongTitle.ifBlank { batch.playlistTitle },
                                    color = Color(0xFFA5ABC0),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            val overallPercent = if (batch.totalTracks > 0) {
                                val donePortion = (batch.currentTrackIndex - 1).toFloat()
                                val songPortion = batch.currentSongProgress / 100f
                                ((donePortion + songPortion) / batch.totalTracks.toFloat() * 100f).toInt().coerceIn(1, 100)
                            } else 100

                            Text(
                                text = if (batch.isComplete) "Done" else "$overallPercent%",
                                color = Color(0xFF00E676),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (!batch.isComplete) {
                            val overallFraction = if (batch.totalTracks > 0) {
                                val donePortion = (batch.currentTrackIndex - 1).toFloat()
                                val songPortion = batch.currentSongProgress / 100f
                                ((donePortion + songPortion) / batch.totalTracks.toFloat()).coerceIn(0f, 1f)
                            } else 1f

                            LinearProgressIndicator(
                                progress = { overallFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.5.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = Color(0xFF00E676),
                                trackColor = Color.White.copy(alpha = 0.10f)
                            )
                        }
                    }
                } else if (single != null || showCompletedSingle) {
                    // Single Track Download Capsule (Apple Dynamic Island floating glass capsule)
                    val isDone = single?.isComplete == true || showCompletedSingle
                    val title = if (isDone) (single?.title ?: lastCompletedTitle) else (single?.title ?: "")
                    val progress = single?.progress ?: 100

                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            // Circular indicator with progress ring or green checkmark
                            Box(
                                modifier = Modifier.size(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isDone) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF00E676)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else {
                                    CircularProgressIndicator(
                                        progress = { (progress / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF00E676),
                                        trackColor = Color.White.copy(alpha = 0.12f)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        tint = Color(0xFF00E676),
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = if (isDone) "✓ Downloaded" else title,
                                    color = if (isDone) Color(0xFF00E676) else Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isDone) title else "${single?.bytesText ?: "Downloading"} • $progress%",
                                    color = Color(0xFFA5ABC0),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                text = if (isDone) "Ready" else "$progress%",
                                color = Color(0xFF00E676),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (!isDone) {
                            LinearProgressIndicator(
                                progress = { (progress / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(1.dp)),
                                color = Color(0xFF00E676),
                                trackColor = Color.White.copy(alpha = 0.08f)
                            )
                        }
                    }
                }
            }
        }
    }
}
