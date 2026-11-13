package com.videoguard.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    var selectedFilter by remember { mutableStateOf("All") }
    val scope = rememberCoroutineScope()

    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistTitle by remember { mutableStateOf("") }

    // Service Import State
    var selectedImportService by remember { mutableStateOf<String?>(null) }
    var importUrlInput by remember { mutableStateOf("") }
    var importProgress by remember { mutableStateOf<ImportProgress?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var importedPlaylistResult by remember { mutableStateOf<UserPlaylist?>(null) }
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
                    listOf("All", "Playlists", "Liked Songs", "Recently Played", "Made For You").forEach { chip ->
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
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            // 2. Apple Music
                            ServiceRow(
                                title = "Apple Music",
                                brandColor = Color(0xFFFA243C),
                                icon = Icons.Default.QueueMusic,
                                onClick = {
                                    selectedImportService = "Apple Music"
                                    importUrlInput = ""
                                    importProgress = null
                                    importedPlaylistResult = null
                                }
                            )

                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.07f),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            // 3. Amazon Music
                            ServiceRow(
                                title = "Amazon Music",
                                brandColor = Color(0xFF00A8E1),
                                icon = Icons.Default.Headphones,
                                onClick = {
                                    selectedImportService = "Amazon Music"
                                    importUrlInput = ""
                                    importProgress = null
                                    importedPlaylistResult = null
                                }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "By selecting a service above, you are choosing to transfer your followed artists, liked tracks, albums and playlists to AURALIX via TuneMyMusic or Direct Sync.",
                                color = Color(0xFF6E7180),
                                fontSize = 10.sp,
                                lineHeight = 14.sp
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
                                imageVector = Icons.Default.LibraryMusic,
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
                                text = "Create a new playlist or import from Spotify / Apple Music / Amazon Music",
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
                                    imageVector = Icons.Default.LibraryMusic,
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

                        IconButton(onClick = { /* Options */ }) {
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

    // Modal: Service Import & Sign-in Sheet
    val currentService = selectedImportService
    if (currentService != null) {
        val brandColor = when (currentService) {
            "Spotify" -> Color(0xFF1DB954)
            "Apple Music" -> Color(0xFFFA243C)
            "Amazon Music" -> Color(0xFF00A8E1)
            else -> YTMusicRedAccent
        }

        val authUrl = when (currentService) {
            "Spotify" -> "https://accounts.spotify.com/login"
            "Apple Music" -> "https://music.apple.com/login"
            "Amazon Music" -> "https://music.amazon.com"
            else -> "https://www.tunemymusic.com"
        }

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
                            imageVector = when (currentService) {
                                "Spotify" -> Icons.Default.MusicNote
                                "Apple Music" -> Icons.Default.QueueMusic
                                else -> Icons.Default.Headphones
                            },
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
                        text = "Sign in to transfer your playlists and liked library automatically, or paste a direct playlist link below:",
                        color = Color(0xFFCCCCCC),
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Sign In Button
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = brandColor),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Login, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign In with $currentService", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.15f))
                        Text(
                            text = " OR PASTE LINK ",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.15f))
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = importUrlInput,
                        onValueChange = { importUrlInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = when (currentService) {
                                    "Spotify" -> "https://open.spotify.com/playlist/..."
                                    "Apple Music" -> "https://music.apple.com/.../playlist/..."
                                    else -> "https://music.amazon.com/playlists/..."
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

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = {
                                clipboardManager.getText()?.text?.let { importUrlInput = it }
                            }
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, tint = brandColor, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paste from Clipboard", color = brandColor, fontSize = 12.sp)
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
                            text = if (progress.total > 0) "${progress.stage} (${progress.current}/${progress.total})" else progress.stage,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (progress.currentTrackName.isNotBlank()) {
                            Text(
                                text = progress.currentTrackName,
                                color = Color(0xFFAAAAAA),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (progress.error != null) {
                            Text(
                                text = "Error: ${progress.error}",
                                color = Color(0xFFFF5252),
                                fontSize = 11.sp
                            )
                        }
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
                        Text("Play Playlist", color = Color.Black, fontWeight = FontWeight.Bold)
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
                                    }
                                }
                            }
                        },
                        enabled = importUrlInput.isNotBlank() && !isImporting,
                        colors = ButtonDefaults.buttonColors(containerColor = brandColor)
                    ) {
                        Text(if (isImporting) "Syncing..." else "Start Sync", color = Color.Black, fontWeight = FontWeight.Bold)
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
                .clip(CircleShape)
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