package com.videoguard.app.ui.screens

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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.videoguard.app.data.UserDataRepository
import com.videoguard.app.data.VideoRepository
import com.videoguard.app.data.YouTubeSyncEngine
import com.videoguard.app.model.VideoItem
import com.videoguard.app.ui.theme.LiquidGlassDefaults
import com.videoguard.app.ui.theme.YTMusicBlack
import com.videoguard.app.ui.theme.YTMusicRedAccent
import com.videoguard.app.ui.theme.liquidGlassPill

@Composable
fun YouTubeMusicScreen(
    onTrackSelected: (VideoItem, List<VideoItem>) -> Unit,
    scrollToTopTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    var selectedMood by remember { mutableStateOf("") }
    val moods = VideoRepository.musicCategories

    val musicFeed by YouTubeSyncEngine.musicFeed.collectAsState()
    val userHistory by UserDataRepository.history.collectAsState()
    val userPlaylists by UserDataRepository.playlists.collectAsState()

    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(selectedMood) {
        isRefreshing = true
        YouTubeSyncEngine.fetchMusicHomeFeed(selectedMood.ifBlank { null })
        isRefreshing = false
    }

    // Real user account music tracks from watch history
    val accountHistoryMusic = remember(userHistory) {
        userHistory.filter { track ->
            track.isMusicTrack ||
            track.title.contains("song", ignoreCase = true) ||
            track.title.contains("music", ignoreCase = true) ||
            track.title.contains("audio", ignoreCase = true) ||
            track.title.contains("ft.", ignoreCase = true) ||
            track.title.contains("official video", ignoreCase = true) ||
            track.title.contains("official audio", ignoreCase = true) ||
            track.title.contains("remix", ignoreCase = true) ||
            track.title.contains("soundtrack", ignoreCase = true) ||
            track.category.equals("Music", ignoreCase = true)
        }
    }

    // Real user liked music tracks
    val accountLikedMusic = remember(userPlaylists) {
        val liked = userPlaylists.find { it.id == "liked_videos" }?.videos ?: emptyList()
        liked.filter {
            it.isMusicTrack || it.category.equals("Music", ignoreCase = true) ||
            it.title.contains("song", ignoreCase = true) || it.title.contains("music", ignoreCase = true)
        }
    }

    // 1. Quick picks: Personalized YT Music recommendations or account liked/history
    val quickPicks = remember(musicFeed.quickPicks, accountLikedMusic, accountHistoryMusic) {
        if (musicFeed.quickPicks.isNotEmpty()) {
            musicFeed.quickPicks
        } else if (accountLikedMusic.isNotEmpty()) {
            accountLikedMusic.take(12)
        } else if (accountHistoryMusic.isNotEmpty()) {
            accountHistoryMusic.shuffled().take(12)
        } else {
            userHistory.take(10).ifEmpty { VideoRepository.sampleVideos }
        }
    }

    // 2. Listen again: Directly displays user's real account history songs!
    val listenAgain = remember(musicFeed.listenAgain, accountHistoryMusic, userHistory) {
        if (accountHistoryMusic.isNotEmpty()) {
            (accountHistoryMusic.take(15) + musicFeed.listenAgain).distinctBy { it.id }
        } else if (musicFeed.listenAgain.isNotEmpty()) {
            musicFeed.listenAgain
        } else if (userHistory.isNotEmpty()) {
            userHistory.take(15)
        } else {
            VideoRepository.sampleVideos
        }
    }

    // 3. Recommended videos:
    val recommendedVideos = remember(musicFeed.recommendedVideos, accountLikedMusic, quickPicks) {
        if (musicFeed.recommendedVideos.isNotEmpty()) {
            musicFeed.recommendedVideos
        } else if (accountLikedMusic.isNotEmpty()) {
            accountLikedMusic.take(10)
        } else {
            quickPicks.reversed().take(10)
        }
    }

    // 4. Trending charts:
    val trendingCharts = remember(musicFeed.trendingCharts, quickPicks) {
        if (musicFeed.trendingCharts.isNotEmpty()) {
            musicFeed.trendingCharts
        } else {
            quickPicks
        }
    }

    // 5. Similar Songs & Radio Picks (Dynamic recommendations from user history)
    var similarPicks by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var similarSeedTitle by remember { mutableStateOf("") }

    val lastPlayed = remember(accountHistoryMusic, userHistory) {
        accountHistoryMusic.firstOrNull() ?: userHistory.firstOrNull()
    }

    LaunchedEffect(lastPlayed?.id) {
        if (lastPlayed != null) {
            similarSeedTitle = (lastPlayed.artist.ifBlank { lastPlayed.title }).take(25)
            val related = com.videoguard.app.data.YouTubeSearchEngine.getRelatedVideos(lastPlayed, isMusicOnly = true)
            if (related.isNotEmpty()) {
                similarPicks = related
            }
        }
    }

    // All music tracks for sequential playback
    val allMusicTracks = remember(quickPicks, listenAgain, trendingCharts) {
        (quickPicks + listenAgain + trendingCharts).distinctBy { it.id }
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

        // Section: "Quick picks" (The signature YT Music feature)
        item {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (selectedMood.isNotBlank()) selectedMood.uppercase() else "START RADIO FROM A SONG",
                            color = Color(0xFF909090),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = if (selectedMood.isNotBlank()) "$selectedMood Picks" else "Quick picks",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.10f))
                            .border(1.dp, LiquidGlassDefaults.BorderSpecular, RoundedCornerShape(16.dp))
                            .clickable {
                                if (quickPicks.isNotEmpty()) onTrackSelected(quickPicks.first(), quickPicks)
                                else if (allMusicTracks.isNotEmpty()) onTrackSelected(allMusicTracks.first(), allMusicTracks)
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Play all", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Quick Picks Track Items
        items(quickPicks.take(8), key = { "quick_${it.id}" }) { track ->
            QuickPickTrackRow(
                track = track,
                onClick = { onTrackSelected(track, quickPicks) }
            )
        }

        // Section: "Listen again" Carousel
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                Text(
                    text = "Listen again",
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
                items(listenAgain, key = { "again_${it.id}" }) { track ->
                    AlbumCard(
                        track = track,
                        onClick = { onTrackSelected(track, listenAgain) }
                    )
                }
            }
        }

        // Section: "Similar Songs & Smart Recommendations"
        if (similarPicks.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                    Text(
                        text = "SIMILAR SONGS & RADIO",
                        color = Color(0xFF909090),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = if (similarSeedTitle.isNotBlank()) "Similar to $similarSeedTitle" else "Recommended for you",
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
                    items(similarPicks, key = { "similar_${it.id}" }) { track ->
                        AlbumCard(
                            track = track,
                            onClick = { onTrackSelected(track, similarPicks) }
                        )
                    }
                }
            }
        }

        // Section: "Recommended Music Videos" Carousel
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                Text(
                    text = "Recommended music videos",
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
                items(recommendedVideos, key = { "video_${it.id}" }) { track ->
                    VideoMusicCard(
                        track = track,
                        onClick = { onTrackSelected(track, recommendedVideos) }
                    )
                }
            }
        }

        // Section: "Trending Charts"
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                Text(
                    text = "Trending Charts",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        items(trendingCharts.take(10), key = { "chart_${it.id}" }) { track ->
            QuickPickTrackRow(
                track = track,
                onClick = { onTrackSelected(track, trendingCharts) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(90.dp))
        }
    }
}

@Composable
private fun QuickPickTrackRow(
    track: VideoItem,
    onClick: () -> Unit
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
            onClick = { /* Menu */ },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More",
                tint = Color(0xFFAAAAAA),
                modifier = Modifier.size(18.dp)
            )
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
