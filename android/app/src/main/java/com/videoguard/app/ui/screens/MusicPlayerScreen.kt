@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.videoguard.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.videoguard.app.data.VideoRepository
import com.videoguard.app.model.VideoItem
import com.videoguard.app.ui.components.AddToPlaylistSheet
import com.videoguard.app.ui.components.YouTubeVideoPlayer
import com.videoguard.app.ui.theme.LiquidGlassDefaults
import com.videoguard.app.ui.theme.YouTubeRed
import com.videoguard.app.ui.theme.YTMusicBlack
import com.videoguard.app.ui.theme.YTMusicRedAccent

enum class MusicSheetTab {
    UP_NEXT,
    LYRICS,
    RELATED
}

@Composable
fun MusicPlayerScreen(
    track: VideoItem,
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    onCollapse: () -> Unit,
    onNextTrack: () -> Unit,
    onPrevTrack: () -> Unit,
    onTrackSelected: (VideoItem) -> Unit,
    isBackgroundPlay: Boolean = true,
    onToggleBackgroundPlay: () -> Unit = {},
    onSwitchToVideoMode: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(MusicSheetTab.UP_NEXT) }
    var isLiked by remember { mutableStateOf(false) }
    var isDisliked by remember { mutableStateOf(false) }
    var isShuffle by remember { mutableStateOf(false) }
    val isRepeat by com.videoguard.app.service.PlaybackController.isLoopEnabled.collectAsState()
    var sliderPosition by remember { mutableFloatStateOf(0.35f) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showAddToPlaylistSheet by remember { mutableStateOf(false) }
    val offlineTracks by com.videoguard.app.data.UserDataRepository.offlineTracks.collectAsState()
    val isTrackDownloaded = offlineTracks.any { it.id == track.id }
    val currentDownload by com.videoguard.app.data.UserDataRepository.currentDownload.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val liveQueue by com.videoguard.app.service.PlaybackController.playbackQueue.collectAsState()
    var dynamicRelated by remember(track.id) { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isLoadingRelated by remember(track.id) { mutableStateOf(false) }
    var isSheetExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(track.id) {
        if (liveQueue.size <= 1) {
            isLoadingRelated = true
            val results = com.videoguard.app.data.YouTubeSearchEngine.getRelatedVideos(track, isMusicOnly = true)
            if (results.isNotEmpty()) {
                dynamicRelated = results
                com.videoguard.app.service.PlaybackController.setQueue(listOf(track) + results)
            }
            isLoadingRelated = false
        }
    }

    val queueTracks = remember(liveQueue, dynamicRelated, track.id) {
        if (liveQueue.size > 1) {
            val currentIdx = liveQueue.indexOfFirst { it.id == track.id }
            if (currentIdx != -1) {
                val upcoming = liveQueue.drop(currentIdx + 1)
                val previous = liveQueue.take(currentIdx)
                upcoming + previous
            } else {
                liveQueue.filter { it.id != track.id }
            }
        } else if (dynamicRelated.isNotEmpty()) {
            dynamicRelated
        } else {
            VideoRepository.getMusicTracks().filter { it.id != track.id }
        }
    }

    val sheetHeight by animateDpAsState(
        targetValue = (if (isSheetExpanded) 360.dp else 180.dp) + navBarBottom,
        label = "sheetHeight"
    )

    // Audio Visualizer waveform animation
    val infiniteTransition = rememberInfiniteTransition(label = "audioWave")
    val wave1 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse),
        label = "w1"
    )
    val wave2 by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(550, easing = LinearEasing), RepeatMode.Reverse),
        label = "w2"
    )
    val wave3 by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Reverse),
        label = "w3"
    )

    val currentTime by com.videoguard.app.service.PlaybackController.currentTimeSeconds.collectAsState()
    val duration by com.videoguard.app.service.PlaybackController.durationSeconds.collectAsState()
    var isUserScrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableFloatStateOf(0f) }

    val currentSeconds = if (isUserScrubbing) scrubPosition else currentTime
    val maxSeconds = if (duration > 0f) duration else 100f
    val sliderProgress = (currentSeconds / maxSeconds).coerceIn(0f, 1f)

    var lyricsResult by remember(track.id) { mutableStateOf<com.videoguard.app.data.LyricsResult?>(null) }
    var isLoadingLyrics by remember(track.id) { mutableStateOf(false) }

    LaunchedEffect(track.id) {
        isLoadingLyrics = true
        lyricsResult = com.videoguard.app.data.LyricsEngine.getLyrics(
            trackTitle = track.title,
            artistName = track.artist.ifBlank { track.channelName },
            durationSeconds = duration.toInt(),
            videoId = track.id
        )
        isLoadingLyrics = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { /* Absorb background taps */ }
            }
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2B0C16),
                        Color(0xFF14060B),
                        Color(0xFF070707)
                    )
                )
            )
            .statusBarsPadding()
    ) {
        // Floating Real-Time Download Progress Banner
        AnimatedVisibility(
            visible = currentDownload != null,
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
                    border = androidx.compose.foundation.BorderStroke(1.dp, YTMusicRedAccent.copy(alpha = 0.5f))
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
                                color = YTMusicRedAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { dl.progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = YTMusicRedAccent,
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

        // Top Bar: Collapse Icon | [ Song | Video ] Toggle (Top Center) | Background Play & More
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            IconButton(
                onClick = onCollapse,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(38.dp)
                    .shadow(elevation = 6.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(1.dp, LiquidGlassDefaults.BorderSpecular, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Collapse",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Video mode switcher pill in Top Centre (matching homescreen switcher style)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
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
                    .clickable { onSwitchToVideoMode() }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Switch to Video",
                        tint = YouTubeRed,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Video",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            IconButton(
                onClick = { showSettingsSheet = true },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(38.dp)
                    .shadow(elevation = 6.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(1.dp, LiquidGlassDefaults.BorderSpecular, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Center Area: Square Album Art with Audio Visualizer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp)
                .pointerInput(Unit) {
                    detectTapGestures { /* Absorb center taps */ }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .aspectRatio(1f)
                        .shadow(24.dp, RoundedCornerShape(16.dp), spotColor = Color(0x66FF1744))
                        .clip(RoundedCornerShape(16.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { /* Do not toggle play/pause on album tap */ }
                        }
                ) {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Audio visualizer bar when playing
                if (isPlaying) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(18.dp)
                    ) {
                        listOf(wave1, wave2, wave3, wave1 * 0.7f, wave2 * 0.9f).forEach { h ->
                            Box(
                                modifier = Modifier
                                    .width(3.5.dp)
                                    .fillMaxHeight(h)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(YTMusicRedAccent)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "High Quality Audio",
                            color = Color(0xFFAAAAAA),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Track Info & Like / Dislike
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = track.artist.ifEmpty { track.channelName },
                    color = Color(0xFFAAAAAA),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showAddToPlaylistSheet = true }) {
                    Icon(
                        imageVector = Icons.Default.PlaylistAdd,
                        contentDescription = "Add to playlist",
                        tint = Color.White
                    )
                }
                IconButton(
                    onClick = {
                        com.videoguard.app.data.UserDataRepository.startDownload(context, track)
                    }
                ) {
                    Icon(
                        imageVector = if (isTrackDownloaded) Icons.Filled.CheckCircle else Icons.Default.Download,
                        contentDescription = "Download Song",
                        tint = if (isTrackDownloaded) YTMusicRedAccent else Color.White
                    )
                }
                IconButton(
                    onClick = {
                        isDisliked = !isDisliked
                        if (isDisliked) isLiked = false
                    }
                ) {
                    Icon(
                        imageVector = if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                        contentDescription = "Dislike",
                        tint = if (isDisliked) YTMusicRedAccent else Color.White
                    )
                }
                IconButton(
                    onClick = {
                        isLiked = !isLiked
                        if (isLiked) isDisliked = false
                    }
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = "Like",
                        tint = if (isLiked) YTMusicRedAccent else Color.White
                    )
                }
            }
        }

        // Seekbar / Slider (Live synchronized)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 2.dp)
        ) {
            Slider(
                value = currentSeconds.coerceIn(0f, maxSeconds),
                onValueChange = { newVal ->
                    isUserScrubbing = true
                    scrubPosition = newVal
                },
                onValueChangeFinished = {
                    isUserScrubbing = false
                    com.videoguard.app.service.PlaybackController.seekTo(scrubPosition)
                },
                valueRange = 0f..maxSeconds,
                colors = SliderDefaults.colors(
                    thumbColor = YTMusicRedAccent,
                    activeTrackColor = YTMusicRedAccent,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatMusicTime(currentSeconds), color = Color(0xFFAAAAAA), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(if (duration > 0f) formatMusicTime(duration) else track.duration, color = Color(0xFFAAAAAA), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }

        // Playback Controls: Shuffle | Prev | Play/Pause | Next | Repeat
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = { isShuffle = !isShuffle },
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (isShuffle) YTMusicRedAccent else Color(0xFFAAAAAA),
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(onClick = onPrevTrack, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Big Modern Play/Pause Button with subtle ring
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onPlayPauseToggle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(38.dp)
                )
            }

            IconButton(onClick = onNextTrack, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            IconButton(
                onClick = { com.videoguard.app.service.PlaybackController.toggleLoop() },
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Repeat,
                    contentDescription = "Repeat",
                    tint = if (isRepeat) YTMusicRedAccent else Color(0xFFAAAAAA),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Bottom Sheet Tabs: [ UP NEXT | LYRICS | RELATED ] - Liquid Glass Dock
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeight)
                .border(1.dp, LiquidGlassDefaults.BorderSpecular, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xF2161726))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Sleek handle bar
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.35f))
                        .align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Tab Buttons + Expand Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TabPill(
                            title = "UP NEXT",
                            isActive = activeTab == MusicSheetTab.UP_NEXT,
                            onClick = { activeTab = MusicSheetTab.UP_NEXT }
                        )
                        TabPill(
                            title = "LYRICS",
                            isActive = activeTab == MusicSheetTab.LYRICS,
                            onClick = { activeTab = MusicSheetTab.LYRICS }
                        )
                        TabPill(
                            title = "RELATED",
                            isActive = activeTab == MusicSheetTab.RELATED,
                            onClick = { activeTab = MusicSheetTab.RELATED }
                        )
                    }

                    IconButton(
                        onClick = { isSheetExpanded = !isSheetExpanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isSheetExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = if (isSheetExpanded) "Collapse Queue" else "Expand Queue",
                            tint = Color(0xFFAAAAAA),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF333333), thickness = 1.dp)

                // Tab Content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    when (activeTab) {
                        MusicSheetTab.UP_NEXT -> {
                            if (isLoadingRelated && queueTracks.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(bottom = navBarBottom + 8.dp)
                                ) {
                                    items(queueTracks, key = { it.id }) { qTrack ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val activeQueue = if (liveQueue.size > 1) liveQueue else listOf(track) + queueTracks
                                                    com.videoguard.app.service.PlaybackController.playTrack(qTrack, activeQueue)
                                                }
                                                .padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AsyncImage(
                                                model = qTrack.thumbnailUrl,
                                                contentDescription = qTrack.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = qTrack.title,
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = qTrack.artist.ifEmpty { qTrack.channelName },
                                                    color = Color(0xFFAAAAAA),
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = qTrack.duration,
                                                color = Color(0xFF888888),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        MusicSheetTab.LYRICS -> {
                            val lyrics = lyricsResult
                            val currentMs = (currentSeconds * 1000).toLong()
                            val activeIndex = remember(lyrics, currentMs) {
                                if (lyrics != null && lyrics.isSynced && lyrics.lines.isNotEmpty()) {
                                    lyrics.lines.indexOfLast { it.timestampMs <= currentMs }.coerceAtLeast(0)
                                } else -1
                            }
                            val lyricListState = rememberLazyListState()

                            LaunchedEffect(activeIndex) {
                                if (activeIndex >= 0 && lyrics?.isSynced == true) {
                                    val target = (activeIndex - 1).coerceAtLeast(0)
                                    lyricListState.animateScrollToItem(target)
                                }
                            }

                            if (isLoadingLyrics) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = YTMusicRedAccent,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            } else if (lyrics != null && lyrics.lines.isNotEmpty()) {
                                LazyColumn(
                                    state = lyricListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(top = 6.dp, bottom = navBarBottom + 8.dp)
                                ) {
                                    itemsIndexed(lyrics.lines) { index, line ->
                                        val isActive = index == activeIndex && lyrics.isSynced
                                        Text(
                                            text = line.text,
                                            color = if (isActive) Color.White else Color(0xFF777777),
                                            fontSize = if (isActive) 18.sp else 15.sp,
                                            lineHeight = if (isActive) 26.sp else 22.sp,
                                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Normal,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    if (lyrics.isSynced) {
                                                        com.videoguard.app.service.PlaybackController.seekTo(line.timestampMs / 1000f)
                                                    }
                                                }
                                                .padding(vertical = 4.dp)
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(top = 6.dp, bottom = navBarBottom + 8.dp)
                                ) {
                                    Text(
                                        text = track.lyrics.ifEmpty {
                                            "Lyrics are not currently available for this track."
                                        },
                                        color = Color(0xFFEEEEEE),
                                        fontSize = 15.sp,
                                        lineHeight = 24.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                }
                            }
                        }
                        MusicSheetTab.RELATED -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(top = 6.dp, bottom = navBarBottom + 8.dp)
                            ) {
                                Text(
                                    text = "About the Artist",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${track.artist.ifEmpty { track.channelName }} has over ${track.views} across YouTube and YouTube Music. Album: ${track.album.ifEmpty { "Single" }}.",
                                    color = Color(0xFFCCCCCC),
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        com.videoguard.app.ui.components.PlayerSettingsBottomSheet(onDismiss = { showSettingsSheet = false })
    }

    if (showAddToPlaylistSheet) {
        AddToPlaylistSheet(
            video = track,
            onDismiss = { showAddToPlaylistSheet = false }
        )
    }
}


@Composable
private fun TabPill(
    title: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isActive) Color.White.copy(alpha = 0.16f)
                else Color.White.copy(alpha = 0.04f)
            )
            .border(
                width = 1.dp,
                brush = if (isActive) LiquidGlassDefaults.BorderSpecular else LiquidGlassDefaults.BorderSubtle,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            color = if (isActive) Color.White else Color(0xFF9E9EAF),
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            fontSize = 12.sp,
            letterSpacing = 0.5.sp
        )
    }
}

private fun formatMusicTime(seconds: Float): String {
    val totalSec = seconds.toInt().coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, m, s)
    } else {
        String.format("%d:%02d", m, s)
    }
}
