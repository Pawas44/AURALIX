package com.videoguard.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.videoguard.app.data.UserDataRepository

/**
 * Premium, modern, animated download progress indicator.
 * Displays live progress, artwork, title, artist, live percentage, pause/resume, and cancel actions.
 * Sticks cleanly to the top directly below the status bar with purple glowing accents.
 */
@Composable
fun DynamicIslandDownloadPill(
    modifier: Modifier = Modifier,
    onOpenDownloads: () -> Unit = {}
) {
    val batchDownload by UserDataRepository.batchDownload.collectAsState()
    val currentDownload by UserDataRepository.currentDownload.collectAsState()
    val queuedCount by UserDataRepository.queuedDownloadsCount.collectAsState()

    var showCompletedSuccess by remember { mutableStateOf(false) }
    var lastCompletedTitle by remember { mutableStateOf("") }
    var lastCompletedArtist by remember { mutableStateOf("") }
    var lastCompletedThumb by remember { mutableStateOf("") }

    // Completion state handling: show success animation, then auto-hide or transition
    LaunchedEffect(currentDownload?.isComplete, batchDownload?.isComplete) {
        val singleComplete = currentDownload?.isComplete == true
        val batchComplete = batchDownload?.isComplete == true
        if (singleComplete || batchComplete) {
            showCompletedSuccess = true
            lastCompletedTitle = currentDownload?.title?.ifBlank { batchDownload?.playlistTitle } ?: ""
            lastCompletedArtist = currentDownload?.artist ?: ""
            lastCompletedThumb = currentDownload?.thumbnailUrl ?: ""

            kotlinx.coroutines.delay(1800)
            showCompletedSuccess = false
        }
    }

    val hasActiveDownload = (currentDownload != null && !currentDownload!!.isComplete) ||
            (batchDownload != null && !batchDownload!!.isComplete)
    val isVisible = hasActiveDownload || showCompletedSuccess

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeIn(animationSpec = tween(250)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        val single = currentDownload
        val batch = batchDownload
        val isDone = (single?.isComplete == true || batch?.isComplete == true || showCompletedSuccess) && !hasActiveDownload
        val isPaused = single?.isPaused == true

        // Real-time metadata
        val title = when {
            isDone -> (single?.title?.ifBlank { lastCompletedTitle }) ?: lastCompletedTitle
            single != null -> single.title
            batch != null -> batch.currentSongTitle.ifBlank { batch.playlistTitle }
            else -> lastCompletedTitle
        }

        val artist = when {
            isDone -> (single?.artist?.ifBlank { lastCompletedArtist }) ?: lastCompletedArtist
            single != null && single.artist.isNotBlank() -> single.artist
            batch != null -> batch.playlistTitle
            else -> lastCompletedArtist
        }

        val thumbUrl = when {
            single != null && single.thumbnailUrl.isNotBlank() -> single.thumbnailUrl
            lastCompletedThumb.isNotBlank() -> lastCompletedThumb
            else -> ""
        }

        // Percentage calculation
        val progressPercent = when {
            isDone -> 100
            single != null -> single.progress.coerceIn(0, 100)
            batch != null && batch.totalTracks > 0 -> {
                val donePortion = (batch.currentTrackIndex - 1).toFloat()
                val songPortion = batch.currentSongProgress / 100f
                ((donePortion + songPortion) / batch.totalTracks.toFloat() * 100f).toInt().coerceIn(1, 100)
            }
            else -> 0
        }

        val animatedProgress by animateFloatAsState(
            targetValue = (progressPercent / 100f).coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 350, easing = LinearOutSlowInEasing),
            label = "DownloadProgressAnimation"
        )

        // Pulsing animation for active downloading badge
        val infiniteTransition = rememberInfiniteTransition(label = "DownloadPulse")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.65f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(850, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "PulseAlpha"
        )

        // Download Indicator Container
        Surface(
            onClick = onOpenDownloads,
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFF101016), // Premium dark obsidian
            border = BorderStroke(
                1.2.dp,
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF8B5CF6).copy(alpha = 0.50f),
                        Color(0xFFC084FC).copy(alpha = 0.85f),
                        Color(0xFF7C3AED).copy(alpha = 0.50f)
                    )
                )
            ),
            shadowElevation = 14.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(26.dp),
                    ambientColor = Color(0xFF8B5CF6),
                    spotColor = Color(0xFFC084FC)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // LEFT: Album Artwork with rounded corners and status badge
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E1730)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (thumbUrl.isNotBlank()) {
                            AsyncImage(
                                model = thumbUrl,
                                contentDescription = title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF4C1D95), Color(0xFF7C3AED))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = Color(0xFFC084FC),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Mini badge in artwork corner
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 2.dp, y = 2.dp)
                            .size(17.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF101016))
                            .border(BorderStroke(0.8.dp, Color(0xFFC084FC)), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                isDone -> {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF4ADE80),
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                                isPaused -> {
                                    Icon(
                                        imageVector = Icons.Default.Pause,
                                        contentDescription = null,
                                        tint = Color(0xFFFBBF24),
                                        modifier = Modifier.size(9.dp)
                                    )
                                }
                                else -> {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        tint = Color(0xFFC084FC).copy(alpha = pulseAlpha),
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(11.dp))

                    // CENTER: Status label, song title, artist
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Header Label
                        val statusLabel = when {
                            isDone -> "✓ Download completed"
                            isPaused -> "Download paused"
                            single?.isFailed == true -> "Download failed"
                            batch != null -> "Downloading (${batch.currentTrackIndex} of ${batch.totalTracks})..."
                            queuedCount > 0 -> "Downloading (${queuedCount + 1} queued)..."
                            else -> "Downloading..."
                        }
                        val statusColor = when {
                            isDone -> Color(0xFF4ADE80)
                            isPaused -> Color(0xFFFBBF24)
                            single?.isFailed == true -> Color(0xFFEF4444)
                            else -> Color(0xFFC084FC)
                        }

                        Text(
                            text = statusLabel,
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(1.dp))

                        Text(
                            text = title.ifBlank { "Song" },
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = artist.ifBlank { "AURALIX Audio" },
                            color = Color(0xFFA5ABC0),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // RIGHT: Live percentage, Circular Pause/Resume button, and Cancel button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Live percentage text
                        Text(
                            text = if (isDone) "Done" else "$progressPercent%",
                            color = if (isDone) Color(0xFF4ADE80) else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )

                        // Circular Pause/Resume Button
                        if (!isDone) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF221A38))
                                    .border(BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.50f)), CircleShape)
                                    .clickable {
                                        if (isPaused) {
                                            UserDataRepository.resumeDownload()
                                        } else {
                                            UserDataRepository.pauseDownload()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                AnimatedContent(
                                    targetState = isPaused,
                                    label = "PauseResumeTransition"
                                ) { paused ->
                                    if (paused) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Resume download",
                                            tint = Color(0xFFC084FC),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Pause,
                                            contentDescription = "Pause download",
                                            tint = Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }

                            // Cancel Button
                            IconButton(
                                onClick = { UserDataRepository.cancelDownload() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel download",
                                    tint = Color(0xFF8E90A2),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(7.dp))

                // BOTTOM: Smooth animated horizontal progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF1E1730))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF7C3AED),
                                        Color(0xFFA855F7),
                                        Color(0xFFC084FC)
                                    )
                                )
                            )
                    )
                }
            }
        }
    }
}

/**
 * Convenience alias matching requirement naming.
 */
@Composable
fun DownloadProgressIndicator(
    modifier: Modifier = Modifier,
    onOpenDownloads: () -> Unit = {}
) {
    DynamicIslandDownloadPill(
        modifier = modifier,
        onOpenDownloads = onOpenDownloads
    )
}
