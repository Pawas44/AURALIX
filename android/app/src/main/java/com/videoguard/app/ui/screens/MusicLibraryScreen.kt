@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.videoguard.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.videoguard.app.data.AccountRepository
import com.videoguard.app.data.ImportProgress
import com.videoguard.app.data.MusicImportEngine
import com.videoguard.app.data.UserDataRepository
import com.videoguard.app.model.UserPlaylist
import com.videoguard.app.model.VideoItem
import com.videoguard.app.ui.theme.YTMusicRedAccent
import kotlinx.coroutines.launch

@Composable
fun MusicLibraryScreen(
    onTrackSelected: (VideoItem, List<VideoItem>) -> Unit,
    onPlaylistClick: (UserPlaylist) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAccountSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentAccount by AccountRepository.currentAccount.collectAsState()
    val playlists by UserDataRepository.playlists.collectAsState()
    val history by UserDataRepository.history.collectAsState()
    val offlineTracks by UserDataRepository.offlineTracks.collectAsState()

    var selectedFilter by remember { mutableStateOf("All") }
    val scope = rememberCoroutineScope()

    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistTitle by remember { mutableStateOf("") }
    var selectedPlaylistForOptions by remember { mutableStateOf<UserPlaylist?>(null) }

    // Service Import State
    var selectedImportService by remember { mutableStateOf<String?>(null) }
    var showInAppWebSheet by remember { mutableStateOf(false) }
    var webSheetCurrentUrl by remember { mutableStateOf("") }
    var importUrlInput by remember { mutableStateOf("") }
    var importProgress by remember { mutableStateOf<ImportProgress?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var importedPlaylistResult by remember { mutableStateOf<UserPlaylist?>(null) }
    val isSpotifyConnected by com.videoguard.app.data.SpotifyOAuthEngine.isConnected.collectAsState()
    val spotifyUserName by com.videoguard.app.data.SpotifyOAuthEngine.userDisplayName.collectAsState()
    val spotifyPlaylists by com.videoguard.app.data.SpotifyOAuthEngine.userPlaylists.collectAsState()
    var selectedPlaylistIds by remember { mutableStateOf(setOf<String>()) }
    val clipboardManager = LocalClipboardManager.current

    val likedPlaylist = playlists.find { it.id == "liked_videos" }
    val likedCount = likedPlaylist?.videoCount ?: 0

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(54.dp)
                    .background(Color(0xFF030303))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // User Avatar / Initial
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(YTMusicRedAccent)
                        .clickable(onClick = onOpenAccountSheet),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentAccount?.avatarUrl != null) {
                        AsyncImage(
                            model = currentAccount!!.avatarUrl,
                            contentDescription = currentAccount!!.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = currentAccount?.initial ?: "U",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Text(
                    text = "Library",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = onOpenSearch) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.White
                    )
                }
            }
        },
        containerColor = Color(0xFF030303),
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Category Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Playlists", "Liked Songs", "Recently Played", "Downloads").forEach { chip ->
                        val isSelected = selectedFilter == chip
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedFilter = chip },
                            label = { Text(chip, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color(0xFF1E1E24),
                                labelColor = Color.White,
                                selectedContainerColor = Color.White,
                                selectedLabelColor = Color.Black
                            ),
                            border = null,
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            }

            // 1. "Import your music" Section (Matching user reference image)
            if (selectedFilter == "All" || selectedFilter == "Playlists") {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1019)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Import your music",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Select a service below to move your library and playlists to AURALIX",
                                color = Color(0xFFAAAAAA),
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // 1. Spotify
                            ServiceRow(
                                title = "Spotify",
                                brandColor = Color(0xFF1DB954),
                                icon = Icons.Default.MusicNote,
                                onClick = {
                                    selectedImportService = "Spotify"
                                    importUrlInput = ""
                                    importProgress = null
                                    importedPlaylistResult = null
                                }
                            )

                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.07f),
                                modifier = Modifier.padding(vertical = 2.dp)
                            )

                            // 2. Amazon Music
                            ServiceRow(
                                title = "Amazon Music",
                                brandColor = Color(0xFF00A8E1),
                                icon = Icons.Default.MusicNote,
                                onClick = {
                                    selectedImportService = "Amazon Music"
                                    importUrlInput = ""
                                    importProgress = null
                                    importedPlaylistResult = null
                                }
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Select Spotify or Amazon Music to import your playlists and albums directly via link.",
                                color = Color(0xFF888B9E),
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // 2. Liked Songs Hero Card
            if (selectedFilter == "All" || selectedFilter == "Liked Songs") {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable {
                                likedPlaylist?.let { onPlaylistClick(it) }
                            },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF191014)),
                        border = BorderStroke(1.dp, YTMusicRedAccent.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(YTMusicRedAccent, Color(0xFF990022))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Liked Music",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Liked Songs",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Auto playlist • $likedCount tracks",
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 12.sp
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = "Play",
                                tint = YTMusicRedAccent,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }
            }

            // 3. Recently Played Carousel
            if ((selectedFilter == "All" || selectedFilter == "Recently Played") && history.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "Recently played",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(history.take(12)) { track ->
                                Column(
                                    modifier = Modifier
                                        .width(110.dp)
                                        .clickable { onTrackSelected(track, history) }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(110.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF222222))
                                    ) {
                                        AsyncImage(
                                            model = track.thumbnailUrl,
                                            contentDescription = track.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = track.title,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = track.channelName,
                                        color = Color(0xFFAAAAAA),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Made For You / Smart Recommendations
            if ((selectedFilter == "All" || selectedFilter == "Made For You") && history.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "Made for you",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(history.shuffled().take(8)) { track ->
                                Column(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .clickable { onTrackSelected(track, history) }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(120.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF222222))
                                    ) {
                                        AsyncImage(
                                            model = track.thumbnailUrl,
                                            contentDescription = track.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(6.dp)
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.7f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = track.title,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Mix • ${track.channelName}",
                                        color = Color(0xFFAAAAAA),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Downloads / Offline Songs Section
            if (selectedFilter == "All" || selectedFilter == "Downloads") {
                if (offlineTracks.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Downloads (${offlineTracks.size})",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Offline Available",
                                    color = YTMusicRedAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                offlineTracks.forEach { track ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF141620))
                                            .clickable { onTrackSelected(track, offlineTracks) }
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = track.thumbnailUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = track.title,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${track.artist.ifBlank { track.channelName }} • Offline",
                                                color = Color.Gray,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = "Downloaded",
                                            tint = YTMusicRedAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (selectedFilter == "Downloads") {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No downloaded tracks",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap the download icon on any song to listen offline anytime.",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // 5. Playlists Header with '+' New Playlist Button
            if (selectedFilter == "All" || selectedFilter == "Playlists") {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Playlists",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Surface(
                            onClick = { showNewPlaylistDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "New Playlist",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "New Playlist",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                if (playlists.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No playlists yet",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Create a new playlist or import from Spotify / Amazon Music",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { showNewPlaylistDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = YTMusicRedAccent),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create Playlist", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                items(playlists, key = { it.id }) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlaylistClick(playlist) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF252525)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!playlist.coverThumbnailUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = playlist.coverThumbnailUrl,
                                    contentDescription = playlist.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.VideoLibrary,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist.title,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Playlist • ${playlist.videoCount} songs",
                                color = Color(0xFFAAAAAA),
                                fontSize = 12.sp
                            )
                        }

                        IconButton(onClick = { selectedPlaylistForOptions = playlist }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = Color(0xFFAAAAAA),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(90.dp))
            }
        }
    }

    // Modal: New Playlist Dialog
    if (showNewPlaylistDialog) {
        AlertDialog(
            onDismissRequest = {
                showNewPlaylistDialog = false
                newPlaylistTitle = ""
            },
            title = {
                Text(
                    text = "New Playlist",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                OutlinedTextField(
                    value = newPlaylistTitle,
                    onValueChange = { newPlaylistTitle = it },
                    placeholder = { Text("Playlist Title", color = Color.Gray, fontSize = 14.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = YTMusicRedAccent,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        cursorColor = YTMusicRedAccent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = newPlaylistTitle.trim()
                        if (trimmed.isNotEmpty()) {
                            val created = UserDataRepository.createPlaylist(trimmed)
                            newPlaylistTitle = ""
                            showNewPlaylistDialog = false
                            onPlaylistClick(created)
                        }
                    }
                ) {
                    Text("Create", color = YTMusicRedAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        newPlaylistTitle = ""
                        showNewPlaylistDialog = false
                    }
                ) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Modal: Link-based Service Import Dialog
    val currentService = selectedImportService
    if (currentService != null) {
        val brandColor = if (currentService == "Spotify") Color(0xFF1DB954) else Color(0xFF00A8E1)

        AlertDialog(
            onDismissRequest = {
                if (!isImporting) {
                    selectedImportService = null
                    importProgress = null
                    importedPlaylistResult = null
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(brandColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = brandColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Import from $currentService",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (currentService == "Spotify") {
                            "Paste any Spotify playlist or album link to import all tracks directly into AURALIX:"
                        } else {
                            "Paste Amazon Music share link, or paste song titles (e.g. Title - Artist) directly:"
                        },
                        color = Color(0xFFCCCCCC),
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = importUrlInput,
                        onValueChange = { importUrlInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = if (currentService == "Spotify") {
                                    "https://open.spotify.com/playlist/..."
                                } else {
                                    "https://music.amazon.com/playlists/... or song names"
                                },
                                fontSize = 12.sp,
                                color = Color(0xFF666666)
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = brandColor,
                            unfocusedBorderColor = Color(0xFF444444)
                        ),
                        singleLine = false,
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                webSheetCurrentUrl = if (currentService == "Spotify") "https://open.spotify.com" else "https://music.amazon.com"
                                showInAppWebSheet = true
                            }
                        ) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = brandColor, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Web Importer", color = brandColor, fontSize = 12.sp)
                        }

                        TextButton(
                            onClick = {
                                clipboardManager.getText()?.text?.let { importUrlInput = it }
                            }
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, tint = brandColor, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paste Clipboard", color = brandColor, fontSize = 12.sp)
                        }
                    }

                    val progress = importProgress
                    if (progress != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        if (isImporting) {
                            LinearProgressIndicator(
                                progress = {
                                    if (progress.total > 0) progress.current.toFloat() / progress.total else 0f
                                },
                                modifier = Modifier.fillMaxWidth(),
                                color = brandColor
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (progress.isComplete) {
                                "✓ Done! Matched ${progress.total} tracks"
                            } else if (progress.error != null) {
                                "Error: ${progress.error}"
                            } else if (progress.total > 0) {
                                "${progress.stage} (${progress.current}/${progress.total})"
                            } else {
                                progress.stage
                            },
                            color = if (progress.error != null) Color(0xFFFF5252) else Color(0xFFAAAAAA),
                            fontSize = 11.sp
                        )
                    }

                    val result = importedPlaylistResult
                    if (result != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = brandColor.copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "✓ Successfully Imported!",
                                    color = brandColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${result.title} • ${result.videoCount} tracks",
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                val result = importedPlaylistResult
                if (result != null) {
                    Button(
                        onClick = {
                            selectedImportService = null
                            onPlaylistClick(result)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = brandColor)
                    ) {
                        Text("Open Playlist", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            if (importUrlInput.isNotBlank() && !isImporting) {
                                isImporting = true
                                importProgress = null
                                importedPlaylistResult = null
                                scope.launch {
                                    val res = MusicImportEngine.importPlaylistFromUrl(importUrlInput) { prog ->
                                        importProgress = prog
                                    }
                                    isImporting = false
                                    res.onSuccess { pl ->
                                        importedPlaylistResult = pl
                                    }.onFailure { err ->
                                        val errText = err.message ?: "Failed to import tracks"
                                        importProgress = ImportProgress("Error", 0, 0, error = errText)
                                        if (importUrlInput.contains("amazon", ignoreCase = true) && importUrlInput.startsWith("http")) {
                                            webSheetCurrentUrl = importUrlInput
                                            showInAppWebSheet = true
                                        }
                                    }
                                }
                            }
                        },
                        enabled = importUrlInput.isNotBlank() && !isImporting,
                        colors = ButtonDefaults.buttonColors(containerColor = brandColor)
                    ) {
                        Text(if (isImporting) "Importing..." else "Start Import", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        selectedImportService = null
                        importProgress = null
                        importedPlaylistResult = null
                    },
                    enabled = !isImporting
                ) {
                    Text("Close", color = Color.White)
                }
            },
            containerColor = Color(0xFF1B1D28)
        )
    }

    // In-App Web Importer BottomSheet (Full WebView with '⚡ Import This Playlist' button)
    if (showInAppWebSheet) {
        var webViewInstance by remember { mutableStateOf<WebView?>(null) }
        var isExtracting by remember { mutableStateOf(false) }

        ModalBottomSheet(
            onDismissRequest = { showInAppWebSheet = false },
            containerColor = Color(0xFF14151E),
            modifier = Modifier.fillMaxHeight(0.92f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sign In & Open Playlist",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = {
                            val wv = webViewInstance ?: return@Button
                            isExtracting = true
                            val js = """
                                (function() {
                                    var tracks = [];
                                    var seen = {};

                                    function add(title, artist) {
                                        title = (title || '').trim().replace(/^[\d\.\s\-]+/, '');
                                        artist = (artist || '').trim().replace(/Explicit Content/gi, '').trim();
                                        if (title.length > 1 && !title.includes('Amazon') && !title.includes('Playlist') && !title.includes('Library') && !title.includes('Add to')) {
                                            var key = (title + '|' + artist).toLowerCase();
                                            if (!seen[key]) {
                                                seen[key] = true;
                                                tracks.push(artist ? (title + ' - ' + artist) : title);
                                            }
                                        }
                                    }

                                    // 1. Amazon Music button items (Title, Artist, Album)
                                    document.querySelectorAll('button[aria-label]').forEach(function(b) {
                                        var label = b.getAttribute('aria-label') || '';
                                        if (label.includes(',') && !label.includes('Play') && !label.includes('Add to') && !label.includes('Menu') && !label.includes('Share')) {
                                            var parts = label.split(',');
                                            if (parts.length >= 2) add(parts[0], parts[1]);
                                        }
                                    });

                                    // 2. Amazon Music track links
                                    document.querySelectorAll('a[href*="trackAsin"]').forEach(function(a) {
                                        var t = a.innerText.trim();
                                        var row = a.closest('[role="row"], div, li');
                                        var artA = row ? row.querySelector('a[href*="/artists/"]') : null;
                                        add(t, artA ? artA.innerText : '');
                                    });

                                    // 3. Spotify tracklist rows
                                    document.querySelectorAll('[data-testid="tracklist-row"]').forEach(function(row) {
                                        var tEl = row.querySelector('div[dir="auto"], [data-encore-id="text"]');
                                        var aEl = row.querySelector('a[href*="/artist/"]');
                                        if (tEl) add(tEl.innerText, aEl ? aEl.innerText : '');
                                    });

                                    // 4. Generic table/list items
                                    if (tracks.length === 0) {
                                        document.querySelectorAll('li, tr, [role="row"]').forEach(function(el) {
                                            var txt = el.innerText || '';
                                            var lines = txt.split('\n').map(function(s) { return s.trim(); }).filter(Boolean);
                                            if (lines.length >= 2 && lines[0].length < 80 && lines[1].length < 60) {
                                                add(lines[0], lines[1]);
                                            }
                                        });
                                    }

                                    var title = (document.querySelector('h1') ? document.querySelector('h1').innerText.trim() : document.title)
                                        .replace(/Playlist on Amazon Music.*/i, '')
                                        .replace(/\| Spotify/i, '')
                                        .trim();

                                    return JSON.stringify({ title: title, tracks: tracks });
                                })()
                            """.trimIndent()

                            wv.evaluateJavascript(js) { resultJson ->
                                isExtracting = false
                                if (!resultJson.isNullOrBlank() && resultJson != "null") {
                                    val unquoted = if (resultJson.startsWith("\"") && resultJson.endsWith("\"")) {
                                        try {
                                            org.json.JSONTokener(resultJson).nextValue().toString()
                                        } catch (e: Exception) {
                                            resultJson.removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")
                                        }
                                    } else resultJson

                                    showInAppWebSheet = false
                                    isImporting = true
                                    importProgress = ImportProgress("Importing from web session...", 0, 0)
                                    scope.launch {
                                        val res = MusicImportEngine.importPlaylistFromUrl(unquoted) { p ->
                                            importProgress = p
                                        }
                                        isImporting = false
                                        res.onSuccess { pl ->
                                            importedPlaylistResult = pl
                                        }.onFailure { err ->
                                            importProgress = ImportProgress("Error", 0, 0, error = err.message ?: "Failed to import tracks")
                                        }
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                        enabled = !isExtracting
                    ) {
                        Text(if (isExtracting) "Extracting..." else "⚡ Import This Playlist", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                            }
                            webViewClient = WebViewClient()
                            loadUrl(webSheetCurrentUrl)
                            webViewInstance = this
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }

    // Playlist 3-Dots Options BottomSheet (Delete & Clear All Songs)
    if (selectedPlaylistForOptions != null) {
        val targetPlaylist = selectedPlaylistForOptions!!
        ModalBottomSheet(
            onDismissRequest = { selectedPlaylistForOptions = null },
            containerColor = Color(0xFF1B1D28)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = targetPlaylist.title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${targetPlaylist.videoCount} songs",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Clear all songs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            UserDataRepository.clearPlaylist(targetPlaylist.id)
                            selectedPlaylistForOptions = null
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = "Clear All Songs",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Clear all songs",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Delete Playlist
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            UserDataRepository.deletePlaylist(targetPlaylist.id)
                            selectedPlaylistForOptions = null
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Playlist",
                        tint = Color(0xFFFF4D4D),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Delete playlist",
                        color = Color(0xFFFF4D4D),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceRow(
    title: String,
    brandColor: Color,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(brandColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color(0xFF888888),
            modifier = Modifier.size(20.dp)
        )
    }
}
