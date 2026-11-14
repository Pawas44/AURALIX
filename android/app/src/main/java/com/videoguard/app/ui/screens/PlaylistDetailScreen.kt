@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.videoguard.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.videoguard.app.data.UserDataRepository
import com.videoguard.app.data.YouTubeSyncEngine
import com.videoguard.app.model.UserPlaylist
import com.videoguard.app.model.VideoItem
import com.videoguard.app.ui.theme.YTMusicRedAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    onBack: () -> Unit,
    onVideoSelected: (VideoItem, List<VideoItem>) -> Unit,
    initialPlaylist: UserPlaylist? = null,
    modifier: Modifier = Modifier
) {
    val isDownloads = playlistId == "downloads"
    val offlineTracks by UserDataRepository.offlineTracks.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val playlists by UserDataRepository.playlists.collectAsState()
    val repoPlaylist = if (isDownloads) {
        UserDataRepository.getDownloadsPlaylist()
    } else {
        playlists.find { it.id == playlistId }
    }
    val playlist = repoPlaylist ?: initialPlaylist ?: UserPlaylist(
        id = playlistId,
        title = if (isDownloads) "Downloads" else "Playlist",
        videos = if (isDownloads) offlineTracks else emptyList(),
        iconType = if (isDownloads) "download" else "playlist"
    )

    val currentVideos = if (isDownloads) offlineTracks else {
        var videos by remember(playlistId) { mutableStateOf(playlist.videos) }
        LaunchedEffect(repoPlaylist?.videos) {
            val updated = repoPlaylist?.videos
            if (!updated.isNullOrEmpty()) {
                videos = updated
            }
        }
        videos
    }

    var isLoading by remember(playlistId) { mutableStateOf(!isDownloads && playlist.videos.isEmpty()) }
    var isRefreshing by remember(playlistId) { mutableStateOf(false) }
    var showAddSongsSheet by remember { mutableStateOf(false) }
    var addSongSearchQuery by remember { mutableStateOf("") }
    var addSongResults by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isSearchingAddSong by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun refreshVideos() {
        if (isDownloads || (!playlistId.startsWith("PL") && playlistId != "liked_videos" && playlistId != "watch_later")) {
            isRefreshing = false
            isLoading = false
            return
        }
        isRefreshing = true
        val online = withContext(Dispatchers.IO) {
            YouTubeSyncEngine.fetchPlaylistVideos(playlistId)
        }
        if (online.isNotEmpty()) {
            when (playlistId) {
                "liked_videos" -> UserDataRepository.updateLikedVideos(online)
                "watch_later" -> UserDataRepository.updateWatchLaterVideos(online)
                else -> UserDataRepository.updatePlaylistVideos(playlistId, online)
            }
        }
        isRefreshing = false
        isLoading = false
    }

    // Always fetch fresh real-time videos on open (unless offline downloads)
    LaunchedEffect(playlistId) {
        if (!isDownloads) {
            if (currentVideos.isEmpty()) {
                isLoading = true
            }
            refreshVideos()
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(52.dp)
                    .background(Color(0xFF0F0F0F))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = playlist.title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { /* Search */ }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.White
                    )
                }
            }
        },
        containerColor = Color(0xFF0F0F0F),
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Playlist Header Hero Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF2C3440),
                                    Color(0xFF0F0F0F)
                                )
                            )
                        )
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Playlist Cover Artwork
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF222222)),
                            contentAlignment = Alignment.Center
                        ) {
                            val coverUrl = playlist.customCoverUrl ?: currentVideos.firstOrNull()?.thumbnailUrl
                            if (!coverUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = coverUrl,
                                    contentDescription = playlist.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = when (playlist.iconType) {
                                        "like" -> Icons.Default.ThumbUp
                                        "watch_later" -> Icons.Default.WatchLater
                                        else -> Icons.Default.PlaylistPlay
                                    },
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist.title,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val totalDurationStr = remember(currentVideos) {
                                UserDataRepository.formatCombinedDuration(currentVideos)
                            }
                            Text(
                                text = if (isDownloads) {
                                    "${currentVideos.size} Songs • $totalDurationStr • Available Offline"
                                } else if (isRefreshing) {
                                    "Syncing latest changes..."
                                } else {
                                    "${currentVideos.size} videos • $totalDurationStr"
                                },
                                color = if (isDownloads) Color(0xFF40DCA5) else if (isRefreshing) Color(0xFF3EA6FF) else Color(0xFFAAAAAA),
                                fontSize = 12.sp,
                                fontWeight = if (isDownloads) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (playlist.description.isNotBlank()) playlist.description else if (isDownloads) "All offline downloaded songs" else "Auto playlist",
                                color = Color(0xFF888888),
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Buttons: Play All & Shuffle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (currentVideos.isNotEmpty()) {
                                    onVideoSelected(currentVideos.first(), currentVideos)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            enabled = currentVideos.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play all",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play all", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Button(
                            onClick = {
                                if (currentVideos.isNotEmpty()) {
                                    val shuffled = currentVideos.shuffled()
                                    onVideoSelected(shuffled.first(), shuffled)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF272727),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            enabled = currentVideos.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Shuffle", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    if (!isDownloads) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { showAddSongsSheet = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.08f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add songs to this playlist", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Empty or loading state
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
                    }
                }
            } else if (currentVideos.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isDownloads) "No downloaded songs yet.\nTap the Download icon on any track to listen offline anytime." else "No videos in this playlist yet.",
                            color = Color(0xFFAAAAAA),
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                itemsIndexed(currentVideos, key = { idx, item -> "${item.id}_$idx" }) { index, video ->
                    PlaylistItemRow(
                        index = index + 1,
                        video = video,
                        onClick = { onVideoSelected(video, currentVideos) },
                        onRemove = {
                            if (isDownloads) {
                                UserDataRepository.deleteDownload(context, video.id)
                            } else {
                                UserDataRepository.removeVideoFromPlaylist(playlist.id, video.id)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // Bottom space for mini player
            item {
                Spacer(modifier = Modifier.height(90.dp))
            }
        }
    }

    if (showAddSongsSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showAddSongsSheet = false
                addSongSearchQuery = ""
                addSongResults = emptyList()
            },
            containerColor = Color(0xFF1E1E28)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Add to ${playlist.title}",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = addSongSearchQuery,
                    onValueChange = { q ->
                        addSongSearchQuery = q
                        if (q.trim().length >= 2) {
                            scope.launch {
                                isSearchingAddSong = true
                                val res = withContext(Dispatchers.IO) {
                                    com.videoguard.app.data.YouTubeSearchEngine.searchVideos(q.trim(), isMusicOnly = true)
                                }
                                addSongResults = res
                                isSearchingAddSong = false
                            }
                        }
                    },
                    placeholder = { Text("Search song or artist name...", color = Color.Gray, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = YTMusicRedAccent,
                        unfocusedBorderColor = Color(0xFF444444)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isSearchingAddSong) {
                    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = YTMusicRedAccent, modifier = Modifier.size(24.dp))
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)) {
                        items(addSongResults, key = { it.id }) { item ->
                            val alreadyIn = currentVideos.any { it.id == item.id }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = item.thumbnailUrl,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(item.channelName, color = Color.Gray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(
                                    onClick = {
                                        if (alreadyIn) {
                                            UserDataRepository.removeVideoFromPlaylist(playlist.id, item.id)
                                            currentVideos = currentVideos.filter { it.id != item.id }
                                        } else {
                                            UserDataRepository.addVideoToPlaylist(playlist.id, item)
                                            currentVideos = currentVideos + item
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (alreadyIn) Icons.Default.Check else Icons.Default.Add,
                                        contentDescription = null,
                                        tint = if (alreadyIn) YTMusicRedAccent else Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistItemRow(
    index: Int,
    video: VideoItem,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$index",
            color = Color(0xFFAAAAAA),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(24.dp)
        )

        // Thumbnail
        Box(
            modifier = Modifier
                .width(110.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF222222))
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = video.duration,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = video.channelName,
                color = Color(0xFFAAAAAA),
                fontSize = 11.sp
            )
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                tint = Color(0xFFAAAAAA),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
