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
    val isAlbumPlaylist = playlistId.startsWith("album_") || playlistId.startsWith("user_album_") || playlistId.startsWith("OLAK5uy_") || playlistId.startsWith("MPREb_")
    val isRemoteYouTubePlaylist = playlistId.startsWith("PL") || playlistId.startsWith("RD") || playlistId.startsWith("VL") || playlistId.startsWith("OLAK5uy_") || playlistId.startsWith("MPREb_")
    val offlineTracks by UserDataRepository.offlineTracks.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val playlists by UserDataRepository.playlists.collectAsState()
    val isSaved = playlists.any { it.id == playlistId }
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

    var dynamicVideos by remember(playlistId) {
        mutableStateOf(if (isDownloads) offlineTracks else (repoPlaylist?.videos?.takeIf { it.isNotEmpty() } ?: initialPlaylist?.videos ?: emptyList()))
    }

    LaunchedEffect(repoPlaylist?.videos, offlineTracks) {
        if (isDownloads) {
            dynamicVideos = offlineTracks
        } else if (repoPlaylist?.videos != null && repoPlaylist.videos.isNotEmpty()) {
            dynamicVideos = repoPlaylist.videos
        }
    }

    val currentVideos = dynamicVideos
    val totalDurationStr = remember(currentVideos) {
        UserDataRepository.formatCombinedDuration(currentVideos)
    }

    var isLoading by remember(playlistId) { mutableStateOf(!isDownloads && currentVideos.isEmpty()) }
    var isRefreshing by remember(playlistId) { mutableStateOf(false) }
    var showAddSongsSheet by remember { mutableStateOf(false) }
    var addSongSearchQuery by remember { mutableStateOf("") }
    var addSongResults by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isSearchingAddSong by remember { mutableStateOf(false) }
    var songToDelete by remember { mutableStateOf<VideoItem?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun refreshVideos() {
        if (isDownloads) {
            isRefreshing = false
            isLoading = false
            return
        }
        isRefreshing = true
        val online = withContext(Dispatchers.IO) {
            if (isAlbumPlaylist) {
                val album = com.videoguard.app.data.AlbumDataRepository.getAllAlbums().find { it.id == playlistId }
                    ?: com.videoguard.app.data.AlbumItem(
                        id = playlistId,
                        title = playlist.title,
                        artist = playlist.description.substringAfter("• ").substringBefore(" •").trim().ifBlank { playlist.title },
                        coverUrl = playlist.customCoverUrl ?: "",
                        releaseType = "Album"
                    )
                com.videoguard.app.data.AlbumDataRepository.getOrFetchAlbumTracks(album)
            } else {
                YouTubeSyncEngine.fetchPlaylistVideos(playlistId)
            }
        }
        if (online.isNotEmpty()) {
            dynamicVideos = online
            val updatedPl = playlist.copy(
                videos = online,
                customCoverUrl = playlist.customCoverUrl ?: online.firstOrNull()?.thumbnailUrl
            )
            // Only update in repository if this playlist has already been explicitly saved in the user's library
            if (UserDataRepository.playlists.value.any { it.id == playlistId }) {
                UserDataRepository.savePlaylist(updatedPl)
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
                IconButton(onClick = {
                    val safeList = currentVideos.filter { it.id.isNotBlank() }
                    val plToShare = playlist.copy(videos = safeList)
                    com.videoguard.app.data.PlaylistShareEngine.sharePlaylist(context, plToShare)
                }) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share Playlist",
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
                                try {
                                    val safeList = currentVideos.filter { it.id.isNotBlank() }
                                    if (safeList.isNotEmpty()) {
                                        onVideoSelected(safeList.first(), safeList)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
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
                                try {
                                    val safeList = currentVideos.filter { it.id.isNotBlank() }
                                    if (safeList.isNotEmpty()) {
                                        val shuffled = safeList.shuffled()
                                        onVideoSelected(shuffled.first(), shuffled)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
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

                        if (!isDownloads) {
                            IconButton(
                                onClick = {
                                    if (isSaved) {
                                        UserDataRepository.deletePlaylist(playlistId)
                                        android.widget.Toast.makeText(context, "Removed from library", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        val toSave = playlist.copy(
                                            videos = currentVideos,
                                            customCoverUrl = playlist.customCoverUrl ?: currentVideos.firstOrNull()?.thumbnailUrl
                                        )
                                        UserDataRepository.savePlaylist(toSave)
                                        android.widget.Toast.makeText(context, "Saved to library", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isSaved) Color(0xFF331E54) else Color(0xFF272727))
                            ) {
                                Icon(
                                    imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = if (isSaved) "Remove from library" else "Save to library",
                                    tint = if (isSaved) Color(0xFFC084FC) else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    val safeList = currentVideos.filter { it.id.isNotBlank() }
                                    if (safeList.isNotEmpty()) {
                                        UserDataRepository.downloadAllSongs(context, safeList, playlist.title)
                                    }
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFF272727)),
                                enabled = currentVideos.isNotEmpty()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download all songs",
                                    tint = Color(0xFF1DB954),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    val safeList = currentVideos.filter { it.id.isNotBlank() }
                                    val plToShare = playlist.copy(videos = safeList)
                                    com.videoguard.app.data.PlaylistShareEngine.sharePlaylist(context, plToShare)
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFF272727))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share Playlist",
                                    tint = Color(0xFF3EA6FF),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else if (currentVideos.isNotEmpty()) {
                            IconButton(
                                onClick = { showClearAllDialog = true },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFF3B1E1E))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Delete All Downloads",
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    if (!isSaved && !isDownloads) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                val toSave = playlist.copy(
                                    videos = currentVideos,
                                    customCoverUrl = playlist.customCoverUrl ?: currentVideos.firstOrNull()?.thumbnailUrl
                                )
                                UserDataRepository.savePlaylist(toSave)
                                android.widget.Toast.makeText(context, "Saved \"${playlist.title}\" to your library!", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3EA6FF).copy(alpha = 0.20f),
                                contentColor = Color(0xFF3EA6FF)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3EA6FF).copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) {
                            Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save to My Library", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (!isDownloads && !isAlbumPlaylist && isSaved) {
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
                        isDownloads = isDownloads,
                        onClick = { onVideoSelected(video, currentVideos) },
                        onRemove = {
                            if (isDownloads) {
                                songToDelete = video
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
                                        } else {
                                            UserDataRepository.addVideoToPlaylist(playlist.id, item)
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

    if (songToDelete != null) {
        val target = songToDelete!!
        AlertDialog(
            onDismissRequest = { songToDelete = null },
            title = {
                Text(
                    text = "Delete Download",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove \"${target.title}\" from your offline storage?",
                    color = Color(0xFFCCCCCC),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        UserDataRepository.deleteDownload(context, target.id)
                        songToDelete = null
                        android.widget.Toast.makeText(context, "Removed from offline downloads", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { songToDelete = null }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E212B),
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = {
                Text(
                    text = "Delete All Downloads",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete all ${currentVideos.size} downloaded songs? This will free up storage space on your device.",
                    color = Color(0xFFCCCCCC),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        UserDataRepository.clearAllDownloads(context)
                        showClearAllDialog = false
                        android.widget.Toast.makeText(context, "All downloaded songs cleared", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("Delete All", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E212B),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun PlaylistItemRow(
    index: Int,
    video: VideoItem,
    isDownloads: Boolean = false,
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
                imageVector = if (isDownloads) Icons.Default.DeleteOutline else Icons.Default.Close,
                contentDescription = if (isDownloads) "Delete Download" else "Remove",
                tint = if (isDownloads) Color(0xFFFF5252) else Color(0xFFAAAAAA),
                modifier = Modifier.size(if (isDownloads) 18.dp else 16.dp)
            )
        }
    }
}
