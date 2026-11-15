@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.videoguard.app.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.Logout
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
import androidx.compose.ui.res.painterResource
import com.videoguard.app.R
import com.videoguard.app.data.AlbumDataRepository
import com.videoguard.app.data.AlbumItem
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
import com.videoguard.app.data.RemotePlaylistSummary
import com.videoguard.app.data.SpotifyConnectEngine
import com.videoguard.app.data.UserDataRepository
import com.videoguard.app.model.UserPlaylist
import com.videoguard.app.model.VideoItem
import com.videoguard.app.ui.components.SpotifyLoginDialog
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

    // Service Import State (Spotify Link Importer)
    var selectedImportService by remember { mutableStateOf<String?>(null) }
    var importUrlInput by remember { mutableStateOf("") }
    var importProgress by remember { mutableStateOf<ImportProgress?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var importedPlaylistResult by remember { mutableStateOf<UserPlaylist?>(null) }
    val clipboardManager = LocalClipboardManager.current

    // Spotify Connect States
    val isSpotifyConnected by SpotifyConnectEngine.isConnected.collectAsState()
    val spotifyUserName by SpotifyConnectEngine.userDisplayName.collectAsState()
    val spotifyPlaylists by SpotifyConnectEngine.userPlaylists.collectAsState()
    val isSpotifySyncing by SpotifyConnectEngine.isSyncing.collectAsState()
    var showSpotifyLoginDialog by remember { mutableStateOf(false) }
    var showSpotifyLogoutDialog by remember { mutableStateOf(false) }

    val likedPlaylist = playlists.find { it.id == "liked_videos" }
    val likedCount = likedPlaylist?.videoCount ?: 0
    val downloadDurationStr = remember(offlineTracks) {
        UserDataRepository.formatCombinedDuration(offlineTracks)
    }

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
                    listOf("All", "Playlists", "Albums", "Mixtapes", "Spotify", "Liked Songs", "Recently Played", "Downloads").forEach { chip ->
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

            // 1. Spotify Connect & Import Section
            if (selectedFilter == "All" || selectedFilter == "Playlists" || selectedFilter == "Spotify") {
                item {
                    if (!isSpotifyConnected) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1610)),
                            border = BorderStroke(1.dp, Color(0xFF1DB954).copy(alpha = 0.25f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1DB954).copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_spotify),
                                            contentDescription = "Spotify",
                                            tint = Color(0xFF1DB954),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Connect Spotify",
                                            color = Color.White,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Sync your Liked Songs & Playlists to AURALIX",
                                            color = Color(0xFFAAAAAA),
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { showSpotifyLoginDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(painter = painterResource(id = R.drawable.ic_spotify), contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Connect", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            selectedImportService = "Spotify"
                                            importUrlInput = ""
                                            importProgress = null
                                            importedPlaylistResult = null
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, Color(0xFF444444)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Paste Link", color = Color.White, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        // Connected Spotify Hub
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1811)),
                            border = BorderStroke(1.dp, Color(0xFF1DB954).copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                // Header
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1DB954).copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_spotify),
                                            contentDescription = "Spotify",
                                            tint = Color(0xFF1DB954),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Connected to Spotify",
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Logged in as ${spotifyUserName ?: "Spotify Listener"}",
                                            color = Color(0xFF1DB954),
                                            fontSize = 12.sp
                                        )
                                    }

                                    // Refresh / Sync
                                    IconButton(
                                        onClick = {
                                            scope.launch { SpotifyConnectEngine.syncAll() }
                                        },
                                        enabled = !isSpotifySyncing
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Sync",
                                            tint = if (isSpotifySyncing) Color(0xFF1DB954) else Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Clear Log out button
                                    OutlinedButton(
                                        onClick = { showSpotifyLogoutDialog = true },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                                        border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(20.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Logout,
                                            contentDescription = "Log out",
                                            tint = Color(0xFFFF5252),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Log out",
                                            color = Color(0xFFFF5252),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Spotify Liked Songs Card
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val summary = RemotePlaylistSummary(
                                                id = "liked",
                                                title = "Spotify Liked Songs",
                                                description = "Imported from Spotify Liked Songs",
                                                trackCount = 0,
                                                coverUrl = null
                                            )
                                            selectedImportService = "Spotify"
                                            isImporting = true
                                            importProgress = ImportProgress("Reading Spotify Liked Songs...", 0, 0)
                                            scope.launch {
                                                val res = MusicImportEngine.importRemotePlaylist(summary) { prog ->
                                                    importProgress = prog
                                                }
                                                isImporting = false
                                                res.onSuccess { pl ->
                                                    importedPlaylistResult = pl
                                                }.onFailure { err ->
                                                    importProgress = ImportProgress("Failed", 0, 0, error = err.message ?: "Failed")
                                                }
                                            }
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF122218)),
                                    border = BorderStroke(1.dp, Color(0xFF1DB954).copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    Brush.linearGradient(
                                                        colors = listOf(Color(0xFF1DB954), Color(0xFF0F5A2C))
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Favorite,
                                                contentDescription = "Spotify Liked Songs",
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Liked Songs (Spotify)",
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Tap to import & sync to AURALIX",
                                                color = Color(0xFFAAAAAA),
                                                fontSize = 11.sp
                                            )
                                        }

                                        Icon(
                                            imageVector = Icons.Default.PlayCircle,
                                            contentDescription = "Import",
                                            tint = Color(0xFF1DB954),
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }

                                // Spotify User Playlists
                                if (spotifyPlaylists.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "Your Playlists (${spotifyPlaylists.size})",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(spotifyPlaylists) { pl ->
                                            Column(
                                                modifier = Modifier
                                                    .width(105.dp)
                                                    .clickable {
                                                        selectedImportService = "Spotify"
                                                        isImporting = true
                                                        importProgress = ImportProgress("Reading ${pl.title}...", 0, 0)
                                                        scope.launch {
                                                            val res = MusicImportEngine.importRemotePlaylist(pl) { prog ->
                                                                importProgress = prog
                                                            }
                                                            isImporting = false
                                                            res.onSuccess { imported ->
                                                                importedPlaylistResult = imported
                                                            }.onFailure { err ->
                                                                importProgress = ImportProgress("Failed", 0, 0, error = err.message ?: "Failed")
                                                            }
                                                        }
                                                    }
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(105.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(Color(0xFF1C1C1C)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (!pl.coverUrl.isNullOrBlank()) {
                                                        AsyncImage(
                                                            model = pl.coverUrl,
                                                            contentDescription = pl.title,
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    } else {
                                                        Icon(
                                                            painter = painterResource(id = R.drawable.ic_spotify),
                                                            contentDescription = null,
                                                            tint = Color(0xFF1DB954),
                                                            modifier = Modifier.size(36.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = pl.title,
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = pl.description.ifBlank { "Playlist" },
                                                    color = Color(0xFF888888),
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = {
                                            selectedImportService = "Spotify"
                                            importUrlInput = ""
                                            importProgress = null
                                            importedPlaylistResult = null
                                        },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("+ Import another link", color = Color(0xFF1DB954), fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 1b. Full Spotify Playlists & Blends List (when Spotify filter is active)
            if (selectedFilter == "Spotify" && isSpotifyConnected && spotifyPlaylists.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "All Spotify Playlists & Blends (${spotifyPlaylists.size})",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap to Import",
                            color = Color(0xFF1DB954),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                items(spotifyPlaylists, key = { "sp_${it.id}" }) { pl ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedImportService = "Spotify"
                                isImporting = true
                                importProgress = ImportProgress("Reading ${pl.title}...", 0, 0)
                                scope.launch {
                                    val res = MusicImportEngine.importRemotePlaylist(pl) { prog ->
                                        importProgress = prog
                                    }
                                    isImporting = false
                                    res.onSuccess { imported ->
                                        importedPlaylistResult = imported
                                    }.onFailure { err ->
                                        importProgress = ImportProgress("Failed", 0, 0, error = err.message ?: "Failed")
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1C1C1C)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!pl.coverUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = pl.coverUrl,
                                    contentDescription = pl.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_spotify),
                                    contentDescription = null,
                                    tint = Color(0xFF1DB954),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pl.title,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = pl.description.ifBlank { "Spotify Playlist" },
                                color = Color(0xFFAAAAAA),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = {
                                selectedImportService = "Spotify"
                                isImporting = true
                                importProgress = ImportProgress("Reading ${pl.title}...", 0, 0)
                                scope.launch {
                                    val res = MusicImportEngine.importRemotePlaylist(pl) { prog ->
                                        importProgress = prog
                                    }
                                    isImporting = false
                                    res.onSuccess { imported ->
                                        importedPlaylistResult = imported
                                    }.onFailure { err ->
                                        importProgress = ImportProgress("Failed", 0, 0, error = err.message ?: "Failed")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Import",
                                tint = Color(0xFF1DB954),
                                modifier = Modifier.size(22.dp)
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

            // 2b. Downloads Playlist Hero Card (Dedicated Music Library Playlist)
            if (selectedFilter == "All" || selectedFilter == "Playlists" || selectedFilter == "Downloads") {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable {
                                onPlaylistClick(UserDataRepository.getDownloadsPlaylist())
                            },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF101918)),
                        border = BorderStroke(1.dp, Color(0xFF40DCA5).copy(alpha = 0.35f))
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
                                            colors = listOf(Color(0xFF107050), Color(0xFF0A4030))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                val coverUrl = offlineTracks.firstOrNull()?.thumbnailUrl
                                if (!coverUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = coverUrl,
                                        contentDescription = "Downloads",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.35f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DownloadDone,
                                            contentDescription = "Downloads",
                                            tint = Color(0xFF40DCA5),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.DownloadDone,
                                        contentDescription = "Downloads",
                                        tint = Color(0xFF40DCA5),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Downloads",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${offlineTracks.size} Songs • $downloadDurationStr • 100% Offline",
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 12.sp
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (offlineTracks.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            val shuffled = offlineTracks.shuffled()
                                            onTrackSelected(shuffled.first(), shuffled)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Shuffle,
                                            contentDescription = "Shuffle Downloads",
                                            tint = Color(0xFFAAAAAA),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.PlayCircle,
                                    contentDescription = "Play All Downloads",
                                    tint = Color(0xFF40DCA5),
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clickable {
                                            if (offlineTracks.isNotEmpty()) {
                                                onTrackSelected(offlineTracks.first(), offlineTracks)
                                            } else {
                                                onPlaylistClick(UserDataRepository.getDownloadsPlaylist())
                                            }
                                        }
                                )
                            }
                        }
                    }
                }
            }

            // 2c. Albums & Mixtapes Section
            if (selectedFilter == "All" || selectedFilter == "Albums" || selectedFilter == "Mixtapes") {
                val albumsToShow = when (selectedFilter) {
                    "Albums" -> AlbumDataRepository.getPureAlbums()
                    "Mixtapes" -> AlbumDataRepository.getMixtapes()
                    else -> AlbumDataRepository.getAllAlbums()
                }

                if (albumsToShow.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (selectedFilter) {
                                        "Albums" -> "Albums"
                                        "Mixtapes" -> "Mixtapes"
                                        else -> "Albums & Mixtapes"
                                    },
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = "${albumsToShow.size} releases",
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 12.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (selectedFilter == "All") {
                                // Horizontal Carousel for "All" tab
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(albumsToShow, key = { it.id }) { album ->
                                        AlbumLibraryCard(
                                            album = album,
                                            onClick = {
                                                onPlaylistClick(AlbumDataRepository.toUserPlaylist(album))
                                            },
                                            onPlay = {
                                                scope.launch {
                                                    val tracks = AlbumDataRepository.getOrFetchAlbumTracks(album)
                                                    if (tracks.isNotEmpty()) {
                                                        onTrackSelected(tracks.first(), tracks)
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            } else {
                                // Full Grid for "Albums" or "Mixtapes" filter tab
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    albumsToShow.chunked(2).forEach { rowItems ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            rowItems.forEach { album ->
                                                AlbumLibraryCard(
                                                    album = album,
                                                    modifier = Modifier.weight(1f),
                                                    onClick = {
                                                        onPlaylistClick(AlbumDataRepository.toUserPlaylist(album))
                                                    },
                                                    onPlay = {
                                                        scope.launch {
                                                            val tracks = AlbumDataRepository.getOrFetchAlbumTracks(album)
                                                            if (tracks.isNotEmpty()) {
                                                                onTrackSelected(tracks.first(), tracks)
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                            if (rowItems.size == 1) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
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
                                text = "Create a new playlist or import from Spotify",
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

                items(
                    playlists.filter {
                        it.id != "downloads" &&
                        it.id != "liked_videos" &&
                        !it.title.equals("Downloads", ignoreCase = true) &&
                        !it.title.equals("Liked Videos", ignoreCase = true) &&
                        !it.title.equals("Liked Songs", ignoreCase = true)
                    },
                    key = { it.id }
                ) { playlist ->
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

    // Spotify Login Dialog
    if (showSpotifyLoginDialog) {
        SpotifyLoginDialog(
            onDismiss = { showSpotifyLoginDialog = false },
            onLoginSuccess = {
                showSpotifyLoginDialog = false
            }
        )
    }

    // Spotify Logout Confirmation Dialog
    if (showSpotifyLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showSpotifyLogoutDialog = false },
            title = { Text("Log out from Spotify?", color = Color.White) },
            text = {
                Text(
                    "Are you sure you want to log out of Spotify (${spotifyUserName ?: "Connected Account"})? You can log in with another account anytime.",
                    color = Color(0xFFAAAAAA),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSpotifyLogoutDialog = false
                        SpotifyConnectEngine.disconnect(clearCookies = true)
                        android.widget.Toast.makeText(context, "Logged out from Spotify", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444))
                ) {
                    Text("Log out", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSpotifyLogoutDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E1E)
        )
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
        val brandColor = Color(0xFF1DB954)

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
                            painter = painterResource(id = R.drawable.ic_spotify),
                            contentDescription = null,
                            tint = brandColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Import from Spotify",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Paste any Spotify playlist link or track names below to import all songs directly into your library:",
                        color = Color(0xFFCCCCCC),
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = importUrlInput,
                        onValueChange = { importUrlInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = "https://open.spotify.com/playlist/...",
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
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                                        selectedImportService = null
                                        onPlaylistClick(pl)
                                    }.onFailure { err ->
                                        val errText = err.message ?: "Failed to import tracks"
                                        importProgress = ImportProgress("Error", 0, 0, error = errText)
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

                // Download all songs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val pl = targetPlaylist
                            selectedPlaylistForOptions = null
                            UserDataRepository.downloadAllSongs(context, pl)
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download All Songs",
                        tint = Color(0xFF1DB954),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Download all songs",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

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

@Composable
private fun AlbumLibraryCard(
    album: AlbumItem,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(150.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF22232E))
        ) {
            AsyncImage(
                model = album.coverUrl,
                contentDescription = album.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Badge for release type
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = album.releaseType.uppercase(),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            // Quick Play Button
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.8f))
                    .clickable { onPlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = album.title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = "${album.artist} • ${album.year}",
            color = Color(0xFFAAAAAA),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
