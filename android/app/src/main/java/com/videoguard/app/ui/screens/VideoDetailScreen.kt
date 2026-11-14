@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.videoguard.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.videoguard.app.data.VideoRepository
import com.videoguard.app.model.VideoItem
import com.videoguard.app.ui.components.AddToPlaylistSheet
import com.videoguard.app.ui.components.YouTubeVideoPlayer
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import com.videoguard.app.ui.theme.LiquidGlassDefaults
import com.videoguard.app.ui.theme.YouTubeDarkBg
import com.videoguard.app.ui.theme.YTMusicRedAccent

@Composable
fun VideoDetailScreen(
    video: VideoItem,
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    onCollapse: () -> Unit,
    onSwitchToMusicMode: (VideoItem) -> Unit,
    onVideoSelected: (VideoItem) -> Unit,
    onChannelClick: (VideoItem) -> Unit = {},
    onNextTrack: () -> Unit = { com.videoguard.app.service.PlaybackController.nextTrack() },
    onPrevTrack: () -> Unit = { com.videoguard.app.service.PlaybackController.prevTrack() },
    isBackgroundPlay: Boolean = true,
    onToggleBackgroundPlay: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val userPlaylists by com.videoguard.app.data.UserDataRepository.playlists.collectAsState()
    val isLiked = remember(userPlaylists, video.id) {
        com.videoguard.app.data.UserDataRepository.isLiked(video.id)
    }
    val isSaved = remember(userPlaylists, video.id) {
        com.videoguard.app.data.UserDataRepository.isWatchLater(video.id)
    }

    var isDisliked by remember { mutableStateOf(false) }
    var showFullDescription by remember { mutableStateOf(false) }

    val liveQueue by com.videoguard.app.service.PlaybackController.playbackQueue.collectAsState()
    var dynamicRelated by remember(video.id) { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isLoadingRelated by remember(video.id) { mutableStateOf(false) }

    LaunchedEffect(video.id) {
        if (liveQueue.size <= 1) {
            isLoadingRelated = true
            val results = com.videoguard.app.data.YouTubeSearchEngine.getRelatedVideos(video, isMusicOnly = false)
            if (results.isNotEmpty()) {
                dynamicRelated = results
                com.videoguard.app.service.PlaybackController.setQueue(listOf(video) + results)
            }
            isLoadingRelated = false
        }
    }

    val displayVideos = remember(liveQueue, dynamicRelated, video.id) {
        if (liveQueue.size > 1) {
            val currentIdx = liveQueue.indexOfFirst { it.id == video.id }
            if (currentIdx != -1) {
                val upcoming = liveQueue.drop(currentIdx + 1)
                val previous = liveQueue.take(currentIdx)
                upcoming + previous
            } else {
                liveQueue.filter { it.id != video.id }
            }
        } else if (dynamicRelated.isNotEmpty()) {
            dynamicRelated
        } else {
            VideoRepository.sampleVideos.filter { it.id != video.id }
        }
    }

    var showAddToPlaylistSheet by remember { mutableStateOf(false) }
    val offlineTracks by com.videoguard.app.data.UserDataRepository.offlineTracks.collectAsState()
    val isTrackDownloaded = offlineTracks.any { it.id == video.id }
    val currentDownload by com.videoguard.app.data.UserDataRepository.currentDownload.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {}
            .background(LiquidGlassDefaults.LiquidCanvas)
    ) {
        // Top Player Section Spacer (Pinned player sits above this in MainScreen)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .aspectRatio(16f / 9f)
        )

        // Real-Time Download Banner for Active Download
        AnimatedVisibility(
            visible = currentDownload != null && currentDownload?.videoId == video.id,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            currentDownload?.let { dl ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1B1D2C),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF1744).copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (dl.isComplete) "✓ Download Complete" else "Downloading: ${dl.title}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (dl.isComplete) "Ready" else "${dl.progress}%",
                                color = Color(0xFFFF1744),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { dl.progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFFFF1744),
                            trackColor = Color.White.copy(alpha = 0.15f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = dl.bytesText,
                            color = Color(0xFFAAAAAA),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // Scrollable Video Details & Related Videos
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
        ) {
            // Video Title
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = video.title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                // View count & date
                Text(
                    text = "${video.views} • ${video.uploadTime}",
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Channel Bar
            item {
                val resolvedChannelName = remember(video) {
                    if (com.videoguard.app.data.YouTubeSyncEngine.isViewOrDateString(video.channelName)) {
                        if (!com.videoguard.app.data.YouTubeSyncEngine.isViewOrDateString(video.artist) && video.artist.isNotBlank() && video.artist != "YouTube Creator") {
                            video.artist
                        } else {
                            "YouTube Creator"
                        }
                    } else {
                        video.channelName
                    }
                }
                val safeVideo = remember(video, resolvedChannelName) {
                    video.copy(channelName = resolvedChannelName)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = video.channelAvatarUrl,
                        contentDescription = resolvedChannelName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF333333))
                            .clickable { onChannelClick(safeVideo) }
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onChannelClick(safeVideo) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = resolvedChannelName,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Verified",
                            tint = Color(0xFFAAAAAA),
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    // YT Music Switcher on right end of channel name line (Liquid Glass Pill)
                    Box(
                        modifier = Modifier
                            .shadow(elevation = 6.dp, shape = RoundedCornerShape(20.dp), ambientColor = Color.Black.copy(alpha = 0.5f))
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.16f),
                                        Color.White.copy(alpha = 0.05f)
                                    )
                                )
                            )
                            .border(1.dp, LiquidGlassDefaults.BorderSpecular, RoundedCornerShape(20.dp))
                            .clickable { onSwitchToMusicMode(video) }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = "Switch to Music",
                                tint = YTMusicRedAccent,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Music",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Quick Actions: Like | Download | Save to Playlist
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        onClick = {
                            com.videoguard.app.data.UserDataRepository.toggleLiked(video)
                        },
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White.copy(alpha = 0.08f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                contentDescription = "Like",
                                tint = if (isLiked) Color(0xFFFF1744) else Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Like", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Surface(
                        onClick = {
                            com.videoguard.app.data.UserDataRepository.startDownload(context, video)
                            android.widget.Toast.makeText(
                                context,
                                if (isTrackDownloaded) "Removed from downloads" else "Downloading \"${video.title}\" for offline playback...",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        },
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White.copy(alpha = 0.08f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isTrackDownloaded) Icons.Filled.CheckCircle else Icons.Default.Download,
                                contentDescription = "Download",
                                tint = if (isTrackDownloaded) Color(0xFFFF1744) else Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isTrackDownloaded) "Downloaded" else "Download", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Surface(
                        onClick = { showAddToPlaylistSheet = true },
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White.copy(alpha = 0.08f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlaylistAdd,
                                contentDescription = "Save to playlist",
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Up Next / Queue Header
            item {
                val currentIdx = liveQueue.indexOfFirst { it.id == video.id }
                val queueText = if (liveQueue.size > 1 && currentIdx != -1) {
                    "Queue (${currentIdx + 1}/${liveQueue.size})"
                } else {
                    "Up next"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = queueText,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    if (liveQueue.size > 1) {
                        Text(
                            text = "Auto-play queue",
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (isLoadingRelated && displayVideos.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            // Up Next Videos / Queue items
            items(displayVideos, key = { it.id }) { relatedVideo ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val activeQueue = if (liveQueue.size > 1) liveQueue else listOf(video) + displayVideos
                            com.videoguard.app.service.PlaybackController.playTrack(relatedVideo, activeQueue)
                        }
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 120.dp, height = 68.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF222222))
                    ) {
                        AsyncImage(
                            model = relatedVideo.thumbnailUrl,
                            contentDescription = relatedVideo.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.85f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = relatedVideo.duration,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = relatedVideo.title,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${relatedVideo.channelName} • ${relatedVideo.views}",
                            color = Color(0xFFAAAAAA),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { /* Item options */ },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = Color(0xFFAAAAAA),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    if (showAddToPlaylistSheet) {
        AddToPlaylistSheet(
            video = video,
            onDismiss = { showAddToPlaylistSheet = false }
        )
    }
}

@Composable
private fun ActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF242424))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color(0xFFF1F1F1),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = Color(0xFFF1F1F1),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
