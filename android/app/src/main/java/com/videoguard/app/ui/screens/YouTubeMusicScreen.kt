package com.videoguard.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.videoguard.app.data.AlbumDataRepository
import com.videoguard.app.data.AlbumItem
import com.videoguard.app.data.UserDataRepository
import com.videoguard.app.data.VideoRepository
import com.videoguard.app.data.YouTubeSyncEngine
import com.videoguard.app.model.UserPlaylist
import com.videoguard.app.model.VideoItem
import com.videoguard.app.recommendation.DailyMix
import com.videoguard.app.recommendation.MusicDiscoveryService
import com.videoguard.app.recommendation.RecommendationEngine
import com.videoguard.app.ui.components.AddToPlaylistSheet
import com.videoguard.app.ui.theme.LiquidGlassDefaults
import com.videoguard.app.ui.theme.YTMusicRedAccent
import com.videoguard.app.ui.theme.liquidGlassPill
import kotlinx.coroutines.launch

@Composable
fun YouTubeMusicScreen(
    onTrackSelected: (VideoItem, List<VideoItem>) -> Unit,
    onPlaylistClick: (UserPlaylist) -> Unit = {},
    scrollToTopTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedMood by remember { mutableStateOf("") }
    val moods = VideoRepository.musicCategories

    val smartFeed by RecommendationEngine.feedState.collectAsState()
    val userHistory by UserDataRepository.history.collectAsState()
    val userPlaylists by UserDataRepository.playlists.collectAsState()

    var selectedTrackForOptions by remember { mutableStateOf<VideoItem?>(null) }
    var selectedVideoForPlaylist by remember { mutableStateOf<VideoItem?>(null) }
    var explanationTrack by remember { mutableStateOf<VideoItem?>(null) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    val albumsList = remember(userPlaylists.size, userHistory.size) { AlbumDataRepository.getAllAlbums() }

    // Initial load and whenever mood changes or user history updates
    LaunchedEffect(selectedMood, userHistory.size, userPlaylists.size) {
        YouTubeSyncEngine.fetchMusicHomeFeed(selectedMood.ifBlank { null })
        RecommendationEngine.refreshHomeFeed(selectedMood.ifBlank { null })
    }

    val listState = rememberLazyListState()

    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            if (listState.firstVisibleItemIndex > 8) {
                listState.scrollToItem(8)
            }
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // Mood / Activity Filter Chips in Liquid Glass Style
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                moods.forEach { mood ->
                    val isSelected = mood == selectedMood
                    Box(
                        modifier = Modifier
                            .liquidGlassPill(isSelected = isSelected, activeColor = YTMusicRedAccent)
                            .clickable {
                                selectedMood = if (isSelected) "" else mood
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mood,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else Color(0xFFD4D4E2)
                        )
                    }
                }
            }
        }

        // Section 1: Made For You (Personalized Quick Picks)
        val madeForYouList = smartFeed.madeForYou
        item {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (selectedMood.isNotBlank()) selectedMood.uppercase() else "MADE FOR YOU",
                            color = Color(0xFF909090),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = if (selectedMood.isNotBlank()) "$selectedMood Mix" else "Made For You",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.10f))
                                .border(1.dp, LiquidGlassDefaults.BorderSpecular, RoundedCornerShape(16.dp))
                                .clickable {
                                    coroutineScope.launch {
                                        RecommendationEngine.refreshHomeFeed(selectedMood.ifBlank { null }, forceRefresh = true)
                                        Toast.makeText(context, "Recommendations refreshed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.10f))
                                .border(1.dp, LiquidGlassDefaults.BorderSpecular, RoundedCornerShape(16.dp))
                                .clickable { showSettingsSheet = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Sound & Suggestions",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.10f))
                                .border(1.dp, LiquidGlassDefaults.BorderSpecular, RoundedCornerShape(16.dp))
                                .clickable {
                                    if (madeForYouList.isNotEmpty()) {
                                        onTrackSelected(madeForYouList.first(), madeForYouList)
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Play all", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Made For You Track Items
        items(madeForYouList.take(6), key = { "mfy_${it.id}" }) { track ->
            SmartTrackRow(
                track = track,
                onClick = { onTrackSelected(track, madeForYouList) },
                onMoreClick = { selectedTrackForOptions = track }
            )
        }

        // Section: Featured Albums & Mixtapes
        if (albumsList.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "FEATURED DISCOGRAPHY",
                                color = Color(0xFF909090),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "Albums & Mixtapes",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(albumsList, key = { it.id }) { album ->
                            HomeAlbumCard(
                                album = album,
                                onClick = {
                                    val pl = AlbumDataRepository.toUserPlaylist(album)
                                    onPlaylistClick(pl)
                                },
                                onPlayClick = {
                                    coroutineScope.launch {
                                        val tracks = AlbumDataRepository.getOrFetchAlbumTracks(album)
                                        if (tracks.isNotEmpty()) {
                                            onTrackSelected(tracks.first(), tracks)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Section 2: Discover Weekly (Spotify-Style Banner)
        if (smartFeed.discoverWeekly.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(18.dp))
                DiscoverWeeklyBanner(
                    tracks = smartFeed.discoverWeekly,
                    onPlayClick = {
                        onTrackSelected(smartFeed.discoverWeekly.first(), smartFeed.discoverWeekly)
                    }
                )
            }
        }

        // Section 3: Daily Mixes (Spotify-Style Mix 1 to Mix 5)
        if (smartFeed.dailyMixes.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(22.dp))
                Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                    Text(
                        text = "YOUR DAILY MIXES",
                        color = Color(0xFF909090),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "Daily Mixes",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(smartFeed.dailyMixes, key = { it.id }) { mix ->
                        DailyMixCard(
                            mix = mix,
                            onClick = {
                                val pl = UserPlaylist(
                                    id = mix.id,
                                    title = mix.title,
                                    description = "${mix.subtitle} • ${mix.tracks.size} tracks",
                                    videos = mix.tracks,
                                    isSystem = false,
                                    iconType = "playlist",
                                    customCoverUrl = mix.coverUrl
                                )
                                onPlaylistClick(pl)
                            },
                            onPlayClick = {
                                if (mix.tracks.isNotEmpty()) {
                                    onTrackSelected(mix.tracks.first(), mix.tracks)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Section 4: Because You Listened To [Artist / Track]
        smartFeed.becauseYouListenedTo?.let { (seedName, tracks) ->
            if (tracks.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                        Text(
                            text = "SIMILAR SONGS & ARTISTS",
                            color = Color(0xFF909090),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "Because you listened to $seedName",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(tracks, key = { "because_${it.id}" }) { track ->
                            AlbumCard(
                                track = track,
                                onClick = { onTrackSelected(track, tracks) }
                            )
                        }
                    }
                }
            }
        }

        // Section 5: Continue Listening (User Watch / Playback History)
        if (smartFeed.continueListening.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                    Text(
                        text = "Continue listening",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(smartFeed.continueListening, key = { "cont_${it.id}" }) { track ->
                        AlbumCard(
                            track = track,
                            onClick = { onTrackSelected(track, smartFeed.continueListening) }
                        )
                    }
                }
            }
        }

        // Section 6: Your Most Played
        if (smartFeed.yourMostPlayed.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                    Text(
                        text = "ON REPEAT",
                        color = Color(0xFF909090),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "Your Most Played",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(smartFeed.yourMostPlayed, key = { "repeat_${it.id}" }) { track ->
                        AlbumCard(
                            track = track,
                            onClick = { onTrackSelected(track, smartFeed.yourMostPlayed) }
                        )
                    }
                }
            }
        }

        // Section 7: Your Favourite Genres
        smartFeed.yourFavouriteGenres.forEach { (genre, genreTracks) ->
            if (genreTracks.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                        Text(
                            text = "EXPLORE GENRE",
                            color = Color(0xFF909090),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "Best of $genre",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(genreTracks, key = { "genre_${genre}_${it.id}" }) { track ->
                            AlbumCard(
                                track = track,
                                onClick = { onTrackSelected(track, genreTracks) }
                            )
                        }
                    }
                }
            }
        }

        // Section 8: Hidden Gems & New Discoveries
        if (smartFeed.hiddenGems.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                    Text(
                        text = "FRESH FINDS",
                        color = Color(0xFF909090),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "Hidden Gems",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(smartFeed.hiddenGems, key = { "gem_${it.id}" }) { track ->
                        AlbumCard(
                            track = track,
                            onClick = { onTrackSelected(track, smartFeed.hiddenGems) }
                        )
                    }
                }
            }
        }

        // Section 9: Recommended Music Videos
        val recommendedList = smartFeed.recommendedForYou
        if (recommendedList.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                    Text(
                        text = "Recommended for you",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(recommendedList, key = { "video_${it.id}" }) { track ->
                        VideoMusicCard(
                            track = track,
                            onClick = { onTrackSelected(track, recommendedList) }
                        )
                    }
                }
            }
        }

        // Section 10: Trending in Your Favourite Genres
        if (smartFeed.trendingInFavouriteGenres.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                    Text(
                        text = "TRENDING",
                        color = Color(0xFF909090),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "Trending In Your Mix",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            items(smartFeed.trendingInFavouriteGenres.take(6), key = { "trending_${it.id}" }) { track ->
                SmartTrackRow(
                    track = track,
                    onClick = { onTrackSelected(track, smartFeed.trendingInFavouriteGenres) },
                    onMoreClick = { selectedTrackForOptions = track }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(90.dp))
        }
    }

    // Modal Sheet 1: Track Options & Music Discovery Interactions
    selectedTrackForOptions?.let { track ->
        TrackOptionsBottomSheet(
            track = track,
            onDismiss = { selectedTrackForOptions = null },
            onWhySeeing = {
                selectedTrackForOptions = null
                explanationTrack = track
            },
            onShowMoreLikeThis = {
                selectedTrackForOptions = null
                coroutineScope.launch {
                    val similar = MusicDiscoveryService.getMoreLikeThis(track)
                    if (similar.isNotEmpty()) {
                        onTrackSelected(similar.first(), similar)
                    } else {
                        Toast.makeText(context, "No similar tracks found", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onAddToPlaylist = {
                selectedVideoForPlaylist = track
                selectedTrackForOptions = null
            },
            onNotInterested = {
                MusicDiscoveryService.markNotInterested(track)
                selectedTrackForOptions = null
                coroutineScope.launch {
                    RecommendationEngine.refreshHomeFeed(selectedMood.ifBlank { null }, forceRefresh = true)
                    Toast.makeText(context, "Got it, we will recommend less like this", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Modal Sheet 2: "Why am I seeing this song?" Explanation Dialog
    explanationTrack?.let { track ->
        AlertDialog(
            onDismissRequest = { explanationTrack = null },
            title = {
                Text(
                    text = "Why this recommendation?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = track.title,
                        color = Color(0xFFE2E8F0),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = MusicDiscoveryService.explainRecommendation(track),
                        color = Color(0xFFA0AEC0),
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { explanationTrack = null }) {
                    Text("OK", color = YTMusicRedAccent, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1E202E),
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Modal Sheet 3: Add to Playlist Sheet
    selectedVideoForPlaylist?.let { video ->
        AddToPlaylistSheet(
            video = video,
            onDismiss = { selectedVideoForPlaylist = null }
        )
    }

    if (showSettingsSheet) {
        com.videoguard.app.ui.components.PlayerSettingsBottomSheet(
            onDismiss = { showSettingsSheet = false }
        )
    }
}

@Composable
private fun DiscoverWeeklyBanner(
    tracks: List<VideoItem>,
    onPlayClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF1E3A8A), Color(0xFF0F172A))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
            .clickable(onClick = onPlayClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SPOTIFY-INSPIRED WEEKLY MIX",
                    color = Color(0xFF93C5FD),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Discover Weekly",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Your weekly mixtape of fresh music. 30 tracks updated every Monday.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp,
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF38BDF8))
                    .clickable(onClick = onPlayClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color(0xFF0F172A),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun DailyMixCard(
    mix: DailyMix,
    onClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(148.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(148.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF2E1065), Color(0xFF1E1B4B))
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
        ) {
            if (!mix.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = mix.coverUrl,
                    contentDescription = mix.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                        )
                    )
            )
            // Play Button
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(34.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color(0xFF1DB954))
                    .clickable(onClick = onPlayClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play Mix",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .padding(end = 36.dp)
            ) {
                Text(
                    text = mix.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = mix.subtitle,
            color = Color(0xFF9E9EAF),
            fontSize = 11.5.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SmartTrackRow(
    track: VideoItem,
    onClick: () -> Unit,
    onMoreClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(6.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${track.artist.ifEmpty { track.channelName }} • ${track.duration}",
                color = Color(0xFF909090),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = Color(0xFFAAAAAA),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackOptionsBottomSheet(
    track: VideoItem,
    onDismiss: () -> Unit,
    onWhySeeing: () -> Unit,
    onShowMoreLikeThis: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onNotInterested: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141520),
        tonalElevation = 16.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Track Preview Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = Color.White,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist.ifEmpty { track.channelName },
                        color = Color(0xFF9E9EAF),
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            // Action 1: Why am I seeing this song?
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onWhySeeing)
                    .padding(vertical = 10.dp, horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF60A5FA))
                Text("Why am I seeing this song?", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }

            // Action 2: Show more like this (Start Radio)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onShowMoreLikeThis)
                    .padding(vertical = 10.dp, horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Default.Radio, contentDescription = null, tint = Color(0xFF34D399))
                Text("Start radio (More like this)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }

            // Action 3: Add to playlist
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onAddToPlaylist)
                    .padding(vertical = 10.dp, horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = Color.White)
                Text("Add to playlist", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }

            // Action 4: Not interested / Hide this song
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onNotInterested)
                    .padding(vertical = 10.dp, horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Default.NotInterested, contentDescription = null, tint = Color(0xFFF87171))
                Text("Not interested / Hide this song", color = Color(0xFFF87171), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun AlbumCard(
    track: VideoItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = track.album,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = track.title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artist.ifEmpty { track.channelName },
            color = Color(0xFF909090),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun VideoMusicCard(
    track: VideoItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(width = 220.dp, height = 124.dp)
                .clip(RoundedCornerShape(8.dp))
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
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(track.duration, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = track.title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${track.artist.ifEmpty { track.channelName }} • ${track.views}",
            color = Color(0xFF909090),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HomeAlbumCard(
    album: AlbumItem,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF22232E))
        ) {
            AsyncImage(
                model = album.coverUrl,
                contentDescription = album.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Release Type Pill (e.g. ALBUM, MIXTAPE, EP)
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
                    .clickable { onPlayClick() },
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

        Spacer(modifier = Modifier.height(8.dp))

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
