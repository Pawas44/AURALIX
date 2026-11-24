@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.videoguard.app.ui.screens

import androidx.compose.foundation.BorderStroke

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collect
import kotlin.math.abs
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.absoluteValue
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
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
import com.videoguard.app.ui.components.DynamicIslandDownloadPill
import com.videoguard.app.ui.components.YouTubeVideoPlayer
import androidx.compose.ui.draw.drawBehind
import com.videoguard.app.ui.theme.AmbientPalette
import com.videoguard.app.ui.theme.ArtworkColorExtractor
import com.videoguard.app.ui.theme.LiquidGlassDefaults
import com.videoguard.app.ui.theme.YouTubeRed
import com.videoguard.app.ui.theme.YTMusicBlack
import com.videoguard.app.ui.theme.YTMusicRedAccent

fun getTrackPalette(track: VideoItem): AmbientPalette {
    return ArtworkColorExtractor.getPaletteSync(track)
}

enum class MusicSheetTab {
    UP_NEXT,
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
    onSwipeDownDelta: (Float) -> Unit = {},
    onSwipeDownRelease: (Float) -> Unit = {},
    onChannelClick: ((VideoItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(MusicSheetTab.UP_NEXT) }
    val userPlaylists by com.videoguard.app.data.UserDataRepository.playlists.collectAsState()
    val isLiked = remember(userPlaylists, track.id, track.title) {
        com.videoguard.app.data.UserDataRepository.isLiked(track.id, track.title, track.artist)
    }
    var isDisliked by remember { mutableStateOf(false) }
    val isShuffle by com.videoguard.app.service.PlaybackController.isShuffleEnabled.collectAsState()
    val isRepeat by com.videoguard.app.service.PlaybackController.isLoopEnabled.collectAsState()
    val isAutoplay by com.videoguard.app.service.PlaybackController.isAutoplayEnabled.collectAsState()
    var sliderPosition by remember { mutableFloatStateOf(0.35f) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showAddToPlaylistSheet by remember { mutableStateOf(false) }
    var showListenTogetherSheet by remember { mutableStateOf(false) }
    val isJamActive by com.videoguard.app.data.ListenTogetherEngine.isInRoom.collectAsState()
    val jamRoomCode by com.videoguard.app.data.ListenTogetherEngine.roomCode.collectAsState()
    val jamParticipants by com.videoguard.app.data.ListenTogetherEngine.participants.collectAsState()
    val offlineTracks by com.videoguard.app.data.UserDataRepository.offlineTracks.collectAsState()
    val isTrackDownloaded = offlineTracks.any { it.id == track.id }
    val currentDownload by com.videoguard.app.data.UserDataRepository.currentDownload.collectAsState()
    val sleepTimerRemaining by com.videoguard.app.service.PlaybackController.sleepTimerRemainingSeconds.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var showLyricsCardDialog by remember { mutableStateOf(false) }
    var selectedLyricsLinesForCard by remember { mutableStateOf<List<String>>(emptyList()) }

    val ghostCachedIds by com.videoguard.app.data.GhostCacheEngine.cachedTrackIds.collectAsState()
    val isTrackGhostCached = remember(ghostCachedIds, track.id) {
        ghostCachedIds.contains(track.id) || com.videoguard.app.data.GhostCacheEngine.getCachedFile(context, track.id) != null
    }

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val liveQueue by com.videoguard.app.service.PlaybackController.playbackQueue.collectAsState()
    var dynamicRelated by remember(track.id) { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isLoadingRelated by remember(track.id) { mutableStateOf(false) }
    var isSheetExpanded by remember { mutableStateOf(false) }

    var extractedPalette by remember(track.id, track.thumbnailUrl) {
        mutableStateOf(ArtworkColorExtractor.getPaletteSync(track))
    }

    LaunchedEffect(track.id, track.thumbnailUrl) {
        val p = ArtworkColorExtractor.extractPalette(context, track)
        extractedPalette = p
    }

    LaunchedEffect(track.id) {
        val isOffline = com.videoguard.app.service.PlaybackController.isOfflineModeActive
        if (isOffline) {
            val offlineList = com.videoguard.app.data.UserDataRepository.offlineTracks.value
            if (offlineList.size > 1) {
                dynamicRelated = offlineList.filter { it.id != track.id }
            }
        } else if (liveQueue.size <= 1) {
            isLoadingRelated = true
            try {
                val results = com.videoguard.app.data.YouTubeSearchEngine.getRelatedVideos(track, isMusicOnly = true)
                if (results.isNotEmpty()) {
                    dynamicRelated = results
                    com.videoguard.app.service.PlaybackController.setQueue(listOf(track) + results)
                }
            } catch (e: Exception) {}
            isLoadingRelated = false
        }
    }

    val queueTracks = remember(liveQueue, dynamicRelated, offlineTracks, track.id) {
        val isOffline = com.videoguard.app.service.PlaybackController.isOfflineModeActive
        val rawList = when {
            isOffline -> {
                if (liveQueue.isNotEmpty()) liveQueue else offlineTracks
            }
            liveQueue.isNotEmpty() -> {
                liveQueue
            }
            dynamicRelated.isNotEmpty() -> {
                listOf(track) + dynamicRelated
            }
            else -> {
                listOf(track) + VideoRepository.getMusicTracks().filter { it.id != track.id }
            }
        }
        rawList.distinctBy { it.id }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val peekSheetHeight = 58.dp + navBarBottom
    val expandedSheetHeight = (screenHeight * 0.75f).coerceAtLeast(540.dp) + navBarBottom
    val sheetHeight by animateDpAsState(
        targetValue = if (isSheetExpanded) expandedSheetHeight else peekSheetHeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
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
    var optimisticScrubTarget by remember { mutableStateOf<Float?>(null) }
    var optimisticScrubTimeMs by remember { mutableLongStateOf(0L) }

    val currentSeconds = when {
        isUserScrubbing -> scrubPosition
        optimisticScrubTarget != null -> {
            val target = optimisticScrubTarget!!
            val elapsed = System.currentTimeMillis() - optimisticScrubTimeMs
            if (elapsed < 1600L && kotlin.math.abs(currentTime - target) > 1.5f) {
                target
            } else {
                optimisticScrubTarget = null
                currentTime
            }
        }
        else -> currentTime
    }
    val maxSeconds = if (duration > 0f) duration else 100f
    val sliderProgress = (currentSeconds / maxSeconds).coerceIn(0f, 1f)

    var lyricsResult by remember(track.id) { mutableStateOf<com.videoguard.app.data.LyricsResult?>(null) }
    var isLoadingLyrics by remember(track.id) { mutableStateOf(false) }
    var showInPlayerLyrics by remember { mutableStateOf(false) }

    LaunchedEffect(context) {
        com.videoguard.app.data.LyricsOffsetManager.init(context)
    }

    var romanizeMode by remember {
        mutableStateOf(com.videoguard.app.data.LyricsOffsetManager.getRomanizeMode())
    }
    val onCycleRomanizeMode = {
        val nextMode = when (romanizeMode) {
            com.videoguard.app.data.LyricsRomanizeMode.BOTH -> com.videoguard.app.data.LyricsRomanizeMode.ROMANIZED
            com.videoguard.app.data.LyricsRomanizeMode.ROMANIZED -> com.videoguard.app.data.LyricsRomanizeMode.NATIVE
            com.videoguard.app.data.LyricsRomanizeMode.NATIVE -> com.videoguard.app.data.LyricsRomanizeMode.BOTH
        }
        romanizeMode = nextMode
        com.videoguard.app.data.LyricsOffsetManager.setRomanizeMode(nextMode)
        val toastMsg = when (nextMode) {
            com.videoguard.app.data.LyricsRomanizeMode.BOTH -> "Lyrics: Original + Romanized"
            com.videoguard.app.data.LyricsRomanizeMode.ROMANIZED -> "Lyrics: Romanized only"
            com.videoguard.app.data.LyricsRomanizeMode.NATIVE -> "Lyrics: Original script only"
        }
        android.widget.Toast.makeText(context, toastMsg, android.widget.Toast.LENGTH_SHORT).show()
    }
    var lyricsOffsetMs by remember(track.id) {
        mutableLongStateOf(com.videoguard.app.data.LyricsOffsetManager.getOffsetMs(track.id))
    }
    var showSyncTuner by remember { mutableStateOf(false) }
    var showWrongLyricsDialog by remember { mutableStateOf(false) }
    var customLyricsQuery by remember { mutableStateOf("") }
    var isSearchingCustomLyrics by remember { mutableStateOf(false) }
    var customLyricsResults by remember { mutableStateOf<List<com.videoguard.app.data.LyricsSearchResultItem>>(emptyList()) }

    LaunchedEffect(track.id) {
        lyricsOffsetMs = com.videoguard.app.data.LyricsOffsetManager.getOffsetMs(track.id)
        isLoadingLyrics = true
        try {
            lyricsResult = com.videoguard.app.data.LyricsEngine.getLyrics(
                trackTitle = track.title,
                artistName = track.artist.ifBlank { track.channelName },
                durationSeconds = duration.toInt(),
                videoId = track.id,
                context = context
            )
        } catch (e: Exception) {
            lyricsResult = null
        }
        isLoadingLyrics = false
    }

    val activeQueue = remember(liveQueue, track.id) {
        if (liveQueue.isNotEmpty() && liveQueue.any { it.id == track.id }) {
            liveQueue
        } else {
            listOf(track)
        }
    }
    val safeActiveQueue = remember(activeQueue) {
        if (activeQueue.isNotEmpty()) activeQueue else listOf(track)
    }
    val initialIndex = remember(track.id) {
        val idx = safeActiveQueue.indexOfFirst { it.id == track.id }
        if (idx >= 0) idx else 0
    }
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (safeActiveQueue.size - 1).coerceAtLeast(0))
    ) {
        maxOf(safeActiveQueue.size, 1)
    }

    // Only allow track changes from authentic, deliberate user drag gestures on the carousel
    var userSwipedByDrag by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.interactionSource) {
        pagerState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is androidx.compose.foundation.interaction.DragInteraction.Start -> {
                    userSwipedByDrag = true
                }
            }
        }
    }

    // Sync pager when track changes externally (Next/Prev buttons, playlist clicks, auto-play)
    LaunchedEffect(track.id, safeActiveQueue) {
        val targetIdx = safeActiveQueue.indexOfFirst { it.id == track.id }
        if (targetIdx >= 0 && targetIdx != pagerState.currentPage && targetIdx < safeActiveQueue.size) {
            userSwipedByDrag = false
            try {
                pagerState.scrollToPage(targetIdx)
            } catch (e: Exception) {
                // Ignore transient pager scroll race
            }
        }
    }

    // When pager settles from an authentic user swipe gesture, trigger track playback
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settledPage ->
            if (userSwipedByDrag && !pagerState.isScrollInProgress) {
                userSwipedByDrag = false
                if (settledPage in safeActiveQueue.indices) {
                    val settledTrack = safeActiveQueue[settledPage]
                    if (settledTrack.id != track.id) {
                        try {
                            com.videoguard.app.service.PlaybackController.playTrack(settledTrack, safeActiveQueue)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    // Dynamic Ambient Background Interpolation (YouTube Music — Swipe Bg Transition)
    val page = pagerState.currentPage.coerceIn(0, (safeActiveQueue.size - 1).coerceAtLeast(0))
    val offsetFraction = pagerState.currentPageOffsetFraction

    val (fromIdx, toIdx, blend) = if (offsetFraction >= 0f) {
        val next = (page + 1).coerceAtMost(safeActiveQueue.lastIndex.coerceAtLeast(0))
        Triple(page, next, offsetFraction)
    } else {
        val prev = (page - 1).coerceAtLeast(0)
        Triple(page, prev, -offsetFraction)
    }

    val fromPalette = remember(fromIdx, safeActiveQueue, extractedPalette) {
        if (fromIdx in safeActiveQueue.indices && safeActiveQueue[fromIdx].id == track.id) {
            extractedPalette
        } else if (fromIdx in safeActiveQueue.indices) {
            getTrackPalette(safeActiveQueue[fromIdx])
        } else {
            extractedPalette
        }
    }
    val toPalette = remember(toIdx, safeActiveQueue, extractedPalette) {
        if (toIdx in safeActiveQueue.indices && safeActiveQueue[toIdx].id == track.id) {
            extractedPalette
        } else if (toIdx in safeActiveQueue.indices) {
            getTrackPalette(safeActiveQueue[toIdx])
        } else {
            extractedPalette
        }
    }

    val safeBlend = if (blend.isNaN()) 0f else blend.coerceIn(0f, 1f)
    val dynamicPrimary = androidx.compose.ui.graphics.lerp(fromPalette.primary, toPalette.primary, safeBlend)
    val dynamicSecondary = androidx.compose.ui.graphics.lerp(fromPalette.secondary, toPalette.secondary, safeBlend)
    val dynamicDeep = androidx.compose.ui.graphics.lerp(fromPalette.deep, toPalette.deep, safeBlend)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {}
            .background(Color(0xFF050608))
            .drawBehind {
                // 1. Base deep ambient gradient
                drawRect(
                    brush = Brush.verticalGradient(
                        0.0f to dynamicDeep.copy(alpha = 0.85f),
                        0.55f to Color(0xFF07080B),
                        1.0f to Color(0xFF040507)
                    )
                )

                // 2. High-end radiant ambient radial aura centered behind album artwork
                val auraCenter = Offset(size.width * 0.5f, size.height * 0.35f)
                val auraRadius = size.width * 1.05f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            dynamicPrimary.copy(alpha = 0.72f),
                            dynamicSecondary.copy(alpha = 0.40f),
                            Color.Transparent
                        ),
                        center = auraCenter,
                        radius = auraRadius
                    ),
                    center = auraCenter,
                    radius = auraRadius
                )

                // 3. Secondary warm lower ambient glow behind transport controls
                val bottomCenter = Offset(size.width * 0.5f, size.height * 0.85f)
                val bottomRadius = size.width * 0.80f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            dynamicSecondary.copy(alpha = 0.30f),
                            Color.Transparent
                        ),
                        center = bottomCenter,
                        radius = bottomRadius
                    ),
                    center = bottomCenter,
                    radius = bottomRadius
                )

                // 4. Smooth filmic vignette overlay for crisp contrast and glass readability
                drawRect(
                    brush = Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.30f),
                        0.40f to Color.Transparent,
                        0.78f to Color.Black.copy(alpha = 0.55f),
                        1.0f to Color(0xFF040507).copy(alpha = 0.98f)
                    )
                )
            }
            .statusBarsPadding()
    ) {
        // Blurred Song Poster Artwork Background (Apple Music / ByTune style)
        val posterBgUrl = track.thumbnailUrl.ifBlank { "https://img.youtube.com/vi/${track.id}/hqdefault.jpg" }
        AsyncImage(
            model = posterBgUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(radius = 36.dp)
                .graphicsLayer { alpha = 0.36f }
        )
        // Deep overlay to ensure maximum readability of foreground controls and text
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.60f),
                        0.40f to Color.Black.copy(alpha = 0.35f),
                        0.75f to Color.Black.copy(alpha = 0.65f),
                        1.0f to Color(0xFF040507).copy(alpha = 0.96f)
                    )
                )
        )

        // Dynamic Island Download Progress Pill overlay
        DynamicIslandDownloadPill(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp)
                .zIndex(100f)
        )

        // Main Player UI (Fixed height, generous spacing, never squashed!)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = peekSheetHeight)
        ) {
        // Top Drag Handle & Bar Area with Vertical Drag Gesture
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            onSwipeDownDelta(dragAmount)
                        },
                        onDragEnd = { onSwipeDownRelease(0f) },
                        onDragCancel = { onSwipeDownRelease(0f) }
                    )
                }
        ) {
            // Top Drag Handle Indicator with generous touch target
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.4f))
                )
            }
        }

        // Dynamic Island Sleep Timer Pill (Live countdown pill)
        AnimatedVisibility(
            visible = sleepTimerRemaining != null,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    onClick = { showSettingsSheet = true },
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xF21C1E30),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB39DDB).copy(alpha = 0.6f)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = "Sleep Timer",
                            tint = Color(0xFFB39DDB),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Sleep Timer: ${com.videoguard.app.service.PlaybackController.formatSleepTimerRemaining(sleepTimerRemaining)}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                                .clickable { com.videoguard.app.service.PlaybackController.setSleepTimer(null) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel Timer",
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isJamActive,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    onClick = { showListenTogetherSheet = true },
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xF2161226),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.8f)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF06B6D4))
                        )
                        Text(
                            text = "Jam: ${jamRoomCode ?: ""} • ${jamParticipants.size} Friends",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.Podcasts,
                            contentDescription = "Jam",
                            tint = Color(0xFF8B5CF6),
                            modifier = Modifier.size(15.dp)
                        )
                        // Quick floating reaction buttons directly on music player
                        Spacer(modifier = Modifier.width(4.dp))
                        listOf("🔥", "❤️", "🎵", "🚀").forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .clickable {
                                        com.videoguard.app.data.ListenTogetherEngine.sendReaction(emoji)
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(text = emoji, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Top Bar: Collapse Icon | [ Song | Video ] Toggle (Top Center) | Background Play & More
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            if (dragAmount > 0f) {
                                change.consume()
                                onSwipeDownDelta(dragAmount)
                            }
                        },
                        onDragEnd = { onSwipeDownRelease(0f) },
                        onDragCancel = { onSwipeDownRelease(0f) }
                    )
                }
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

            // Dual Switcher Pill: [ Song | Video ] in Top Centre (YouTube Music style)
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .shadow(elevation = 6.dp, shape = RoundedCornerShape(20.dp), ambientColor = Color.Black.copy(alpha = 0.5f))
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xE012141F))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Song tab (active in MusicPlayerScreen)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.20f))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Song",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Song",
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Video tab (switches to video playback mode)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSwitchToVideoMode() }
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Switch to Video",
                            tint = YouTubeRed,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Video",
                            color = Color.White.copy(alpha = 0.70f),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isEqActive by com.videoguard.app.audio.EqualizerEngine.isEnabled.collectAsState()
                IconButton(
                    onClick = { showEqualizerSheet = true },
                    modifier = Modifier
                        .size(38.dp)
                        .shadow(elevation = 6.dp, shape = CircleShape)
                        .clip(CircleShape)
                        .background(
                            if (isEqActive) Color(0xFF8B5CF6).copy(alpha = 0.28f)
                            else Color.White.copy(alpha = 0.12f)
                        )
                        .border(
                            1.dp,
                            if (isEqActive) androidx.compose.ui.graphics.SolidColor(Color(0xFF8B5CF6).copy(alpha = 0.7f))
                            else LiquidGlassDefaults.BorderSpecular,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Equalizer",
                        tint = if (isEqActive) Color(0xFFC084FC) else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = { showSettingsSheet = true },
                    modifier = Modifier
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
        }

        // Center Area: YouTube Music Artwork Carousel OR Centered Synchronized Karaoke Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            if (dragAmount > 0f) {
                                change.consume()
                                onSwipeDownDelta(dragAmount)
                            }
                        },
                        onDragEnd = { onSwipeDownRelease(0f) },
                        onDragCancel = { onSwipeDownRelease(0f) }
                    )
                }
        ) {
            if (showInPlayerLyrics) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Poster Area with Floating Synchronized Lyrics (Same exact dimensions as the album artwork carousel)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 38.dp)
                                .aspectRatio(1f)
                                .shadow(
                                    elevation = 22.dp,
                                    shape = RoundedCornerShape(18.dp),
                                    spotColor = extractedPalette.primary.copy(alpha = 0.65f)
                                )
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF161616))
                                .clickable {
                                    showInPlayerLyrics = false
                                }
                        ) {
                            // 1. Song's Album Poster in background
                            AsyncImage(
                                model = track.thumbnailUrl.ifBlank { "https://img.youtube.com/vi/${track.id}/hqdefault.jpg" },
                                contentDescription = track.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // 2. Gentle dark scrim so lyrics are sharp, high-contrast and crystal-clear
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.52f))
                            )

                            // 3. 100% Transparent Synchronized Karaoke Lyrics (No inner square card, edge-to-edge over poster)
                            com.videoguard.app.ui.components.KaraokeLyricsView(
                                lyrics = lyricsResult,
                                isLoading = isLoadingLyrics,
                                track = track,
                                currentSeconds = currentSeconds,
                                lyricsOffsetMs = lyricsOffsetMs,
                                romanizeMode = romanizeMode,
                                onSeekTo = { posSec ->
                                    com.videoguard.app.service.PlaybackController.seekTo(posSec)
                                },
                                onCreateLyricsCard = { lines ->
                                    selectedLyricsLinesForCard = lines
                                    showLyricsCardDialog = true
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            // 4. Top Floating Controls Row: ABC Romanize, ⏱️ Sync Tuner, 🔍 Search/Fix
                            val (modeLabel, isHighlighted) = when (romanizeMode) {
                                com.videoguard.app.data.LyricsRomanizeMode.BOTH -> "ABC: Both" to true
                                com.videoguard.app.data.LyricsRomanizeMode.ROMANIZED -> "ABC: Roman" to true
                                com.videoguard.app.data.LyricsRomanizeMode.NATIVE -> "ABC: Off" to false
                            }
                            val hasOffset = lyricsOffsetMs != 0L
                            val offsetFormatted = if (lyricsOffsetMs != 0L) {
                                String.format(java.util.Locale.US, "%+.1fs", lyricsOffsetMs / 1000.0)
                            } else {
                                "Sync"
                            }

                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(end = 12.dp, top = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // ABC Romanize Pill
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isHighlighted) Color(0xFF1E3A5F).copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.55f))
                                        .border(1.dp, if (isHighlighted) Color(0xFF3EA6FF).copy(alpha = 0.70f) else Color.White.copy(alpha = 0.20f), RoundedCornerShape(12.dp))
                                        .clickable { onCycleRomanizeMode() }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = modeLabel,
                                        color = if (isHighlighted) Color(0xFF3EA6FF) else Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // ⏱️ Sync Timing Tuner Toggle
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (showSyncTuner || hasOffset) Color(0xFF2C2416).copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.55f))
                                        .border(1.dp, if (showSyncTuner || hasOffset) Color(0xFFFFB300).copy(alpha = 0.75f) else Color.White.copy(alpha = 0.20f), RoundedCornerShape(12.dp))
                                        .clickable { showSyncTuner = !showSyncTuner }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "⏱️ $offsetFormatted",
                                        color = if (showSyncTuner || hasOffset) Color(0xFFFFC107) else Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // 🔍 Search / Fix Lyrics Button
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black.copy(alpha = 0.55f))
                                        .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(12.dp))
                                        .clickable {
                                            customLyricsQuery = com.videoguard.app.data.LyricsEngine.cleanTrackNameRaw(track.title).ifBlank { track.title }
                                            showWrongLyricsDialog = true
                                            scope.launch {
                                                isSearchingCustomLyrics = true
                                                customLyricsResults = com.videoguard.app.data.LyricsOffsetManager.searchAlternativeLyrics(customLyricsQuery)
                                                isSearchingCustomLyrics = false
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "🔍 Fix",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 5. Floating Sync Timing Tuner Bar (When opened)
                            if (showSyncTuner) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 48.dp, start = 12.dp, end = 12.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFF13141F).copy(alpha = 0.94f))
                                        .border(1.dp, Color(0xFFFFB300).copy(alpha = 0.40f), RoundedCornerShape(16.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // -0.5s
                                    TunerButton("-0.5s") {
                                        lyricsOffsetMs = com.videoguard.app.data.LyricsOffsetManager.adjustOffsetMs(track.id, -500L)
                                    }
                                    // -0.1s
                                    TunerButton("-0.1s") {
                                        lyricsOffsetMs = com.videoguard.app.data.LyricsOffsetManager.adjustOffsetMs(track.id, -100L)
                                    }

                                    // Offset Display Badge
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (lyricsOffsetMs == 0L) "0.0s (Sync)" else String.format(java.util.Locale.US, "%+.1fs", lyricsOffsetMs / 1000.0),
                                            color = if (lyricsOffsetMs == 0L) Color(0xFF66BB6A) else Color(0xFFFFC107),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // +0.1s
                                    TunerButton("+0.1s") {
                                        lyricsOffsetMs = com.videoguard.app.data.LyricsOffsetManager.adjustOffsetMs(track.id, 100L)
                                    }
                                    // +0.5s
                                    TunerButton("+0.5s") {
                                        lyricsOffsetMs = com.videoguard.app.data.LyricsOffsetManager.adjustOffsetMs(track.id, 500L)
                                    }

                                    if (lyricsOffsetMs != 0L) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.Red.copy(alpha = 0.20f))
                                                .clickable {
                                                    com.videoguard.app.data.LyricsOffsetManager.resetOffset(track.id)
                                                    lyricsOffsetMs = 0L
                                                }
                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "Reset",
                                                color = Color(0xFFFF8A80),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    // Close button
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Tuner",
                                        tint = Color.Gray,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { showSyncTuner = false }
                                    )
                                }
                            }
                        }

                        // Maintain vertical alignment balance with the artwork carousel's bottom spacing
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = 38.dp),
                    pageSpacing = 16.dp,
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    val itemTrack = safeActiveQueue.getOrNull(pageIndex) ?: track
                    val rawOffset = abs((pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction)
                    val pageOffset = if (rawOffset.isNaN()) 0f else rawOffset.coerceIn(0f, 1f)
                    val scale = (0.88f + (1f - 0.88f) * (1f - pageOffset)).coerceIn(0.5f, 1.5f)
                    val alpha = (0.45f + (1f - 0.45f) * (1f - pageOffset)).coerceIn(0f, 1f)
                    val itemPalette = remember(itemTrack.id) { getTrackPalette(itemTrack) }
                    LaunchedEffect(itemTrack.id, itemTrack.thumbnailUrl) {
                        ArtworkColorExtractor.extractPalette(context, itemTrack)
                    }

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        this.alpha = alpha
                                    }
                                    .shadow(
                                        elevation = ((22 * (1f - pageOffset)).coerceAtLeast(0f)).dp,
                                        shape = RoundedCornerShape(18.dp),
                                        spotColor = itemPalette.primary.copy(alpha = 0.65f)
                                    )
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color(0xFF161616))
                                    .clickable {
                                        showInPlayerLyrics = true
                                        isSheetExpanded = false
                                    }
                            ) {
                                AsyncImage(
                                    model = itemTrack.thumbnailUrl,
                                    contentDescription = itemTrack.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Audio visualizer bar when playing on the active page
                            if (pageIndex == pagerState.currentPage && isPlaying) {
                                Spacer(modifier = Modifier.height(14.dp))
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
                            } else {
                                Spacer(modifier = Modifier.height(32.dp))
                            }
                        }
                    }
                }
            }
        }

        // Action Icons Row (Placed ABOVE Song Title, between poster and song name)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {}
                .padding(horizontal = 28.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = { showAddToPlaylistSheet = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlaylistAdd,
                    contentDescription = "Add to playlist",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = {
                    com.videoguard.app.data.UserDataRepository.startDownload(context, track)
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (isTrackDownloaded) Icons.Filled.CheckCircle else Icons.Default.Download,
                    contentDescription = "Download Song",
                    tint = if (isTrackDownloaded) YTMusicRedAccent else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Music / Lyrics Toggle Button ("wo jo music wala button nhi tha wo lga de uskle press krke lyrics aaengi")
            IconButton(
                onClick = {
                    showInPlayerLyrics = !showInPlayerLyrics
                    if (showInPlayerLyrics) {
                        isSheetExpanded = false
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (showInPlayerLyrics) Color(0xFF1E3A5F) else Color.Transparent)
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "Lyrics view",
                    tint = if (showInPlayerLyrics) Color(0xFF3EA6FF) else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Like Button (Dislike / ThumbDown removed as requested!)
            IconButton(
                onClick = {
                    com.videoguard.app.data.UserDataRepository.toggleLiked(track)
                    if (isDisliked) isDisliked = false
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                    contentDescription = "Like",
                    tint = if (isLiked) YTMusicRedAccent else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Track Info Row (Song Title & Artist below the action icons)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {}
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            if (dragAmount > 0f) {
                                change.consume()
                                onSwipeDownDelta(dragAmount)
                            }
                        },
                        onDragEnd = { onSwipeDownRelease(0f) },
                        onDragCancel = { onSwipeDownRelease(0f) }
                    )
                }
                .padding(horizontal = 24.dp, vertical = 2.dp),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.artist.ifEmpty { track.channelName },
                        color = Color(0xFFAAAAAA),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clickable {
                                val chVideo = if (track.channelName.isNotBlank()) track else track.copy(channelName = track.artist)
                                onChannelClick?.invoke(chVideo)
                            }
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = {
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_SUBJECT, track.title)
                            putExtra(android.content.Intent.EXTRA_TEXT, "Listen to ${track.title} by ${track.artist} on AURALIX: https://youtu.be/${track.id}")
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Track"))
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.10f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "AAC",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
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
                value = (if (isUserScrubbing) scrubPosition else currentSeconds).coerceIn(0f, maxSeconds),
                onValueChange = { newVal ->
                    isUserScrubbing = true
                    scrubPosition = newVal
                },
                onValueChangeFinished = {
                    isUserScrubbing = false
                    optimisticScrubTarget = scrubPosition
                    optimisticScrubTimeMs = System.currentTimeMillis()
                    com.videoguard.app.service.PlaybackController.seekTo(scrubPosition)
                },
                valueRange = 0f..maxSeconds,
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        modifier = Modifier.height(2.5.dp),
                        thumbTrackGapSize = 0.dp,
                        drawStopIndicator = null,
                        colors = SliderDefaults.colors(
                            activeTrackColor = YTMusicRedAccent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                        )
                    )
                },
                thumb = {
                    SliderDefaults.Thumb(
                        interactionSource = remember { MutableInteractionSource() },
                        thumbSize = DpSize(10.dp, 10.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = YTMusicRedAccent
                        )
                    )
                },
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
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {}
                .padding(horizontal = 22.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Shuffle Button with Label (Single click target)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { com.videoguard.app.service.PlaybackController.toggleShuffle() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (isShuffle) YTMusicRedAccent else Color(0xFFAAAAAA),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Shuffle",
                    color = if (isShuffle) YTMusicRedAccent else Color(0xFFAAAAAA),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
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

            // Loop Button with Label (Single click target)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { com.videoguard.app.service.PlaybackController.toggleLoop() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Repeat,
                    contentDescription = "Loop",
                    tint = if (isRepeat) YTMusicRedAccent else Color(0xFFAAAAAA),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Loop",
                    color = if (isRepeat) YTMusicRedAccent else Color(0xFFAAAAAA),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } // End of Playback Controls Row
        } // End of Main Player Column

        // Darkened Scrim behind bottom sheet when expanded (tap to collapse)
        AnimatedVisibility(
            visible = isSheetExpanded,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { isSheetExpanded = false }
            )
        }

        // Bottom Sheet Tabs: [ UP NEXT | LYRICS ] - Liquid Glass Dock Overlay (Slides UP over controls!)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeight)
                .align(Alignment.BottomCenter)
                .shadow(elevation = 20.dp, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            border = BorderStroke(1.dp, LiquidGlassDefaults.BorderSpecular),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xF8141524)
            )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Sleek handle bar
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.40f))
                        .align(Alignment.CenterHorizontally)
                        .clickable { isSheetExpanded = !isSheetExpanded }
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Tab Buttons + Expand Toggle + ABC Romanize pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TabPill(
                            title = "UP NEXT",
                            isActive = activeTab == MusicSheetTab.UP_NEXT && isSheetExpanded,
                            onClick = {
                                activeTab = MusicSheetTab.UP_NEXT
                                isSheetExpanded = true
                            }
                        )
                        TabPill(
                            title = "RELATED",
                            isActive = activeTab == MusicSheetTab.RELATED && isSheetExpanded,
                            onClick = {
                                activeTab = MusicSheetTab.RELATED
                                isSheetExpanded = true
                            }
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

                if (isSheetExpanded && sheetHeight > 90.dp) {
                    HorizontalDivider(color = Color(0xFF333333), thickness = 1.dp)

                    // Tab Content
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
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
                                } else if (queueTracks.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(vertical = 36.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = Color(0xFF666666),
                                                modifier = Modifier.size(36.dp)
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                text = if (com.videoguard.app.service.PlaybackController.isOfflineModeActive)
                                                    "No more downloaded songs in queue"
                                                else
                                                    "No upcoming tracks",
                                                color = Color(0xFFAAAAAA),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                } else {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        // AutoPlay Header Bar (ByTune style)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Autoplay",
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Continuous music that never runs dry",
                                                    color = Color(0xFFAAAAAA),
                                                    fontSize = 11.sp
                                                )
                                            }
                                            Switch(
                                                checked = isAutoplay,
                                                onCheckedChange = { com.videoguard.app.service.PlaybackController.toggleAutoplay() },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.White,
                                                    checkedTrackColor = YTMusicRedAccent,
                                                    uncheckedThumbColor = Color(0xFFAAAAAA),
                                                    uncheckedTrackColor = Color(0xFF333333)
                                                )
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))

                                        LazyColumn(
                                            contentPadding = PaddingValues(bottom = navBarBottom + 8.dp)
                                        ) {
                                            itemsIndexed(queueTracks, key = { index, item -> "${item.id}_$index" }) { _, qTrack ->
                                                val isCurrentlyPlaying = qTrack.id == track.id
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(
                                                            if (isCurrentlyPlaying) Color(0xFF1DB954).copy(alpha = 0.16f)
                                                            else Color.Transparent
                                                        )
                                                        .border(
                                                            width = if (isCurrentlyPlaying) 1.dp else 0.dp,
                                                            color = if (isCurrentlyPlaying) Color(0xFF1DB954).copy(alpha = 0.55f) else Color.Transparent,
                                                            shape = RoundedCornerShape(10.dp)
                                                        )
                                                        .clickable {
                                                            val activeQueue = if (liveQueue.size > 1) liveQueue else listOf(track) + queueTracks
                                                            com.videoguard.app.service.PlaybackController.playTrack(qTrack, activeQueue)
                                                        }
                                                        .padding(horizontal = if (isCurrentlyPlaying) 8.dp else 4.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        AsyncImage(
                                                            model = qTrack.thumbnailUrl,
                                                            contentDescription = qTrack.title,
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier
                                                                .size(42.dp)
                                                                .clip(RoundedCornerShape(6.dp))
                                                        )
                                                        if (isCurrentlyPlaying) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(42.dp)
                                                                    .clip(RoundedCornerShape(6.dp))
                                                                    .background(Color.Black.copy(alpha = 0.45f)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.GraphicEq,
                                                                    contentDescription = "Playing",
                                                                    tint = Color(0xFF1DB954),
                                                                    modifier = Modifier.size(20.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        if (isCurrentlyPlaying) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier.padding(bottom = 2.dp)
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .clip(RoundedCornerShape(4.dp))
                                                                        .background(Color(0xFF1DB954))
                                                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                                                ) {
                                                                    Text(
                                                                        text = "NOW PLAYING",
                                                                        color = Color.Black,
                                                                        fontSize = 9.sp,
                                                                        fontWeight = FontWeight.ExtraBold,
                                                                        letterSpacing = 0.5.sp
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        Text(
                                                            text = qTrack.title,
                                                            color = if (isCurrentlyPlaying) Color.White else Color(0xFFEEEEEE),
                                                            fontSize = 13.sp,
                                                            fontWeight = if (isCurrentlyPlaying) FontWeight.Bold else FontWeight.Medium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = qTrack.artist.ifEmpty { qTrack.channelName },
                                                            color = if (isCurrentlyPlaying) Color(0xFF1DB954) else Color(0xFFAAAAAA),
                                                            fontSize = 11.sp,
                                                            fontWeight = if (isCurrentlyPlaying) FontWeight.SemiBold else FontWeight.Normal,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    Text(
                                                        text = qTrack.duration,
                                                        color = if (isCurrentlyPlaying) Color(0xFF1DB954) else Color(0xFF888888),
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isCurrentlyPlaying) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    // "Don't play this" button (ByTune feature)
                                                    IconButton(
                                                        onClick = {
                                                            com.videoguard.app.service.PlaybackController.dontPlayTrack(qTrack)
                                                            android.widget.Toast.makeText(context, "Won't suggest this song again", android.widget.Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.size(34.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Block,
                                                            contentDescription = "Don't play this",
                                                            tint = Color(0xFF777777),
                                                            modifier = Modifier.size(17.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
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
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .clickable {
                                            val chVideo = if (track.channelName.isNotBlank()) track else track.copy(channelName = track.artist)
                                            onChannelClick?.invoke(chVideo)
                                        }
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "About the Artist",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "View Channel ›",
                                            color = Color(0xFFC084FC),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
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
    }
    }

    if (showSettingsSheet) {
        com.videoguard.app.ui.components.PlayerSettingsBottomSheet(
            onDismiss = { showSettingsSheet = false },
            onOpenJam = {
                showSettingsSheet = false
                showListenTogetherSheet = true
            }
        )
    }

    if (showAddToPlaylistSheet) {
        AddToPlaylistSheet(
            video = track,
            onDismiss = { showAddToPlaylistSheet = false }
        )
    }

    if (showListenTogetherSheet) {
        com.videoguard.app.ui.components.ListenTogetherSheet(onDismiss = { showListenTogetherSheet = false })
    }

    if (showEqualizerSheet) {
        com.videoguard.app.ui.components.EqualizerBottomSheet(onDismiss = { showEqualizerSheet = false })
    }

    if (showWrongLyricsDialog) {
        ModalBottomSheet(
            onDismissRequest = {
                showWrongLyricsDialog = false
                customLyricsResults = emptyList()
            },
            containerColor = Color(0xFF161722)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Search Lyrics (Fix Sync)",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = customLyricsQuery,
                    onValueChange = { customLyricsQuery = it },
                    placeholder = { Text("Search song or artist name...", color = Color.Gray, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (customLyricsQuery.isNotBlank()) {
                                    scope.launch {
                                        isSearchingCustomLyrics = true
                                        customLyricsResults = com.videoguard.app.data.LyricsOffsetManager.searchAlternativeLyrics(customLyricsQuery)
                                        isSearchingCustomLyrics = false
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF3EA6FF))
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF3EA6FF),
                        unfocusedBorderColor = Color(0xFF444444)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isSearchingCustomLyrics) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF3EA6FF), modifier = Modifier.size(28.dp))
                    }
                } else if (customLyricsResults.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)) {
                        items(customLyricsResults) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val parsed = com.videoguard.app.data.LyricsEngine.parseCustomLrc(item.syncedLyrics, item.plainLyrics)
                                        lyricsResult = parsed
                                        com.videoguard.app.data.LyricsEngine.saveLyricsToDisk(context, track.id, parsed)
                                        showWrongLyricsDialog = false
                                        android.widget.Toast.makeText(context, "Lyrics updated!", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.trackName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text("${item.artistName} • ${item.durationSeconds / 60}:${String.format(java.util.Locale.US, "%02d", item.durationSeconds % 60)}", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                                }
                                if (item.isSynced) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF1E3A5F))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("SYNCED", color = Color(0xFF3EA6FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            HorizontalDivider(color = Color(0xFF222222), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }

    // Floating Reactions Overlay: flies up over the album art and full music player!
    com.videoguard.app.ui.components.FloatingReactionsOverlay(modifier = Modifier.fillMaxSize())

    // Editorial Lyrics Sharing Card Dialog
    if (showLyricsCardDialog) {
        com.videoguard.app.ui.components.lyrics.LyricsCardEditorDialog(
            track = track,
            selectedLines = selectedLyricsLinesForCard,
            onDismiss = { showLyricsCardDialog = false }
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

@Composable
private fun TunerButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
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
