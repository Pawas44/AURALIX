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
import androidx.compose.material.icons.filled.DownloadDone
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

    val isVisible = batchDownload != null || (currentDownload != null && !currentDownload!!.isComplete)

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        val batch = batchDownload
        val single = currentDownload

        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(22.dp),
            color = Color(0xF212141D),
            border = androidx.compose.foundation.BorderStroke(
                1.2.dp,
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF1DB954).copy(alpha = 0.85f),
                        Color(0xFF10B981).copy(alpha = 0.60f)
                    )
                )
            ),
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
        ) {
            if (batch != null) {
                // Batch Playlist Download Pill (Live: e.g. "3 of 15 songs downloaded • FINDEX")
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Icon indicator
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1DB954).copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (batch.isComplete) Icons.Default.Check else Icons.Default.Download,
                                contentDescription = null,
                                tint = Color(0xFF1DB954),
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // Headline
                        if (batch.isComplete) {
                            Text(
                                text = "✓ All ${batch.totalTracks} songs downloaded!",
                                color = Color(0xFF1DB954),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Downloading (${batch.currentTrackIndex} of ${batch.totalTracks})",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(text = "•", color = Color.Gray, fontSize = 11.sp)
                                Text(
                                    text = batch.playlistTitle,
                                    color = Color(0xFF1DB954),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Batch percentage or status
                        val overallPercent = if (batch.totalTracks > 0) {
                            val donePortion = (batch.currentTrackIndex - 1).toFloat()
                            val songPortion = batch.currentSongProgress / 100f
                            ((donePortion + songPortion) / batch.totalTracks.toFloat() * 100f).toInt().coerceIn(1, 100)
                        } else 100

                        Text(
                            text = if (batch.isComplete) "Ready" else "$overallPercent%",
                            color = Color(0xFF1DB954),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Current track name
                    if (!batch.isComplete) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = batch.currentSongTitle,
                                color = Color(0xFFA5ABC0),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${batch.currentSongProgress}%",
                                color = Color(0xFFA5ABC0),
                                fontSize = 11.sp
                            )
                        }

                        // Overall Progress Bar
                        val overallFraction = if (batch.totalTracks > 0) {
                            val donePortion = (batch.currentTrackIndex - 1).toFloat()
                            val songPortion = batch.currentSongProgress / 100f
                            ((donePortion + songPortion) / batch.totalTracks.toFloat()).coerceIn(0f, 1f)
                        } else 1f

                        LinearProgressIndicator(
                            progress = { overallFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.5.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color(0xFF1DB954),
                            trackColor = Color.White.copy(alpha = 0.12f)
                        )
                    }
                }
            } else if (single != null) {
                // Single Track Download Pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1DB954).copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (single.isComplete) Icons.Default.Check else Icons.Default.Download,
                            contentDescription = null,
                            tint = Color(0xFF1DB954),
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (single.isComplete) "✓ Download Complete" else "Downloading: ${single.title}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!single.isComplete && single.bytesText.isNotBlank()) {
                            Text(
                                text = single.bytesText,
                                color = Color.Gray,
                                fontSize = 10.5.sp
                            )
                        }
                    }

                    Text(
                        text = if (single.isComplete) "Ready" else "${single.progress}%",
                        color = Color(0xFF1DB954),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
