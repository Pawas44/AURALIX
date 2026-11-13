package com.videoguard.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
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
import com.videoguard.app.data.VideoRepository
import com.videoguard.app.model.AppMode
import com.videoguard.app.model.VideoItem
import com.videoguard.app.ui.components.AddToPlaylistSheet
import com.videoguard.app.ui.theme.LiquidGlassDefaults
import com.videoguard.app.ui.theme.YouTubeDarkBg
import com.videoguard.app.ui.theme.YouTubeRed
import com.videoguard.app.ui.theme.YTMusicRedAccent
import com.videoguard.app.ui.theme.liquidGlass
import com.videoguard.app.ui.theme.liquidGlassPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeFeedScreen(
    onVideoSelected: (VideoItem, List<VideoItem>) -> Unit,
    onSwitchToMusic: () -> Unit,
    onChannelClick: (VideoItem) -> Unit = {},
    scrollToTopTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = VideoRepository.youtubeCategories
    var feedVideos by remember { mutableStateOf(VideoRepository.getVideosByCategory(selectedCategory)) }
    var isLoadingFeed by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedVideoForPlaylist by remember { mutableStateOf<VideoItem?>(null) }

    val currentAccount by com.videoguard.app.data.AccountRepository.currentAccount.collectAsState()
    val synchronizedFeed by com.videoguard.app.data.YouTubeSyncEngine.homeFeed.collectAsState()

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()

    // Smoothly drag / animate scroll back to top when Home button is clicked (no syncing)
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            if (listState.firstVisibleItemIndex > 8) {
                listState.scrollToItem(8)
            }
            listState.animateScrollToItem(0)
        }
    }

    // Initial load / category change
    LaunchedEffect(selectedCategory, currentAccount) {
        isLoadingFeed = true
        val online = com.videoguard.app.data.YouTubeSyncEngine.fetchHomeFeed(selectedCategory)
        if (online.isNotEmpty()) {
            feedVideos = online
        } else {
            feedVideos = VideoRepository.getVideosByCategory(selectedCategory)
        }
        isLoadingFeed = false
    }

    // Keep UI in sync when synchronizedFeed is updated (e.g. after fetchMoreHomeFeed appends)
    LaunchedEffect(synchronizedFeed) {
        if (synchronizedFeed.isNotEmpty() && selectedCategory == "All") {
            feedVideos = synchronizedFeed
        }
    }

    fun loadMoreFeed() {
        if (isLoadingFeed || isLoadingMore || isRefreshing) return
        isLoadingMore = true
        coroutineScope.launch {
            try {
                val loaded = com.videoguard.app.data.YouTubeSyncEngine.fetchMoreHomeFeed()
                if (loaded) {
                    val sync = com.videoguard.app.data.YouTubeSyncEngine.homeFeed.value
                    if (sync.isNotEmpty()) {
                        feedVideos = sync
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("YouTubeFeedScreen", "Error loading more feed", e)
            } finally {
                isLoadingMore = false
            }
        }
    }

    // Infinite scroll: monitor list scroll position using snapshotFlow
    LaunchedEffect(listState, selectedCategory) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            Pair(lastVisible, total)
        }.collect { (lastVisible, total) ->
            if (total > 0 && lastVisible >= total - 4) {
                loadMoreFeed()
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            coroutineScope.launch {
                isRefreshing = true
                try {
                    val fresh = com.videoguard.app.data.YouTubeSyncEngine.fetchHomeFeed(selectedCategory)
                    if (fresh.isNotEmpty()) {
                        feedVideos = fresh
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isRefreshing = false
                }
            }
        },
        state = pullToRefreshState,
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = Color(0xD0202235),
                color = Color.White
            )
        }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
        // Category Chips Row in Liquid Glass Style
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    val isSelected = category == selectedCategory
                    Box(
                        modifier = Modifier
                            .liquidGlassPill(isSelected = isSelected, activeColor = YouTubeRed)
                            .clickable { selectedCategory = category }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = category,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else Color(0xFFD4D4E2)
                        )
                    }
                }
            }
        }

        if (isLoadingFeed && feedVideos.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }

        // Videos Feed
        items(feedVideos, key = { it.id }) { video ->
            YouTubeVideoCard(
                video = video,
                onClick = { onVideoSelected(video, feedVideos) },
                onChannelClick = { onChannelClick(video) },
                onMoreClick = { selectedVideoForPlaylist = video }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Loading More spinner at bottom
        if (isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
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

        // Bottom space so mini-player won't hide last item
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    if (selectedVideoForPlaylist != null) {
        AddToPlaylistSheet(
            video = selectedVideoForPlaylist!!,
            onDismiss = { selectedVideoForPlaylist = null }
        )
    }
}
}

@Composable
fun YouTubeVideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    onChannelClick: () -> Unit = {},
    onMoreClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        // Thumbnail with Duration overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .aspectRatio(16f / 9f)
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Duration Pill - Liquid Glass Badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = video.duration,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Channel Avatar and Info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            AsyncImage(
                model = video.channelAvatarUrl,
                contentDescription = video.channelName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape)
                    .clickable(onClick = onChannelClick)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = video.title,
                    color = Color(0xFFF1F1F1),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${video.channelName} • ${video.views} • ${video.uploadTime}",
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onChannelClick)
                )
            }

            IconButton(
                onClick = { onMoreClick?.invoke() },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
