package com.videoguard.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoguard.app.data.*
import com.videoguard.app.model.VideoItem
import com.videoguard.app.service.PlaybackController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchSourceBottomSheet(
    track: VideoItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val authenticTitle = track.originalTitle.ifBlank { track.title }
    val authenticArtist = track.originalArtist.ifBlank { track.artist }
    val spotifyId = track.spotifyTrackId.ifBlank { track.id.removePrefix("spotify_") }

    var searchQuery by remember { mutableStateOf("$authenticTitle $authenticArtist".trim()) }
    var isLoading by remember { mutableStateOf(true) }
    var candidates by remember { mutableStateOf<List<YouTubeMatchEvaluation>>(emptyList()) }

    fun loadMatches(queryTitle: String, queryArtist: String) {
        scope.launch {
            isLoading = true
            val durSec = YouTubeMatchScorer.parseDurationSec(track.duration)
            val meta = ImportTrackMeta(
                title = queryTitle,
                artist = queryArtist,
                album = track.album,
                durationSeconds = durSec,
                spotifyId = spotifyId,
                coverUrl = track.originalArtworkUrl.ifBlank { track.thumbnailUrl }
            )
            val results = SongMatchingRepository.searchAndRankCandidates(meta)
            candidates = results
            isLoading = false
        }
    }

    LaunchedEffect(track.id) {
        loadMatches(authenticTitle, authenticArtist)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF212131F),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.35f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Match Playback Source",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$authenticTitle • $authenticArtist",
                        color = Color(0xFF1DB954),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar for custom matching
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search YouTube title or artist...", color = Color.Gray, fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (searchQuery.isNotBlank()) {
                                loadMatches(searchQuery, "")
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

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AVAILABLE VERSIONS (${candidates.size})",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                TextButton(
                    onClick = {
                        ManualMatchRepository.clearManualMatch(spotifyId, authenticTitle, authenticArtist)
                        PlaybackController.invalidateResolvedSpotifyId(track.id, authenticTitle, authenticArtist)
                        loadMatches(authenticTitle, authenticArtist)
                        Toast.makeText(context, "Reset to automatic matching", Toast.LENGTH_SHORT).show()
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset to Auto", color = Color(0xFF8B5CF6), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF3EA6FF), modifier = Modifier.size(32.dp))
                }
            } else if (candidates.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No suitable matching videos found.\nTry a different search query above.",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(candidates, key = { it.candidate.id }) { evaluation ->
                        val item = evaluation.candidate
                        val isCurrentMatch = (item.id == track.id || item.id == track.matchedYouTubeId)

                        val badgeColor = when (evaluation.versionType) {
                            SongVersionType.OFFICIAL_AUDIO -> Color(0xFF06B6D4)
                            SongVersionType.OFFICIAL_MUSIC_VIDEO -> Color(0xFF8B5CF6)
                            SongVersionType.LABEL_UPLOAD -> Color(0xFF10B981)
                            SongVersionType.LIVE_PERFORMANCE -> Color(0xFFF59E0B)
                            SongVersionType.COVER_VERSION -> Color(0xFFEC4899)
                            SongVersionType.REMIX_MASHUP -> Color(0xFF6366F1)
                            SongVersionType.LYRICS_VIDEO -> Color(0xFF71717A)
                            else -> Color(0xFF3B82F6)
                        }

                        val badgeLabel = when (evaluation.versionType) {
                            SongVersionType.OFFICIAL_AUDIO -> "Official Audio"
                            SongVersionType.OFFICIAL_MUSIC_VIDEO -> "Music Video"
                            SongVersionType.LABEL_UPLOAD -> "Label Release"
                            SongVersionType.LIVE_PERFORMANCE -> "Live"
                            SongVersionType.COVER_VERSION -> "Cover"
                            SongVersionType.REMIX_MASHUP -> "Remix"
                            SongVersionType.LYRICS_VIDEO -> "Lyrics"
                            else -> "Standard"
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCurrentMatch) Color(0x338B5CF6) else Color.White.copy(alpha = 0.05f),
                            border = BorderStroke(
                                1.dp,
                                if (isCurrentMatch) Color(0xFF8B5CF6).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.1f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // Save manual override
                                    ManualMatchRepository.setManualMatch(spotifyId, item.id, authenticTitle, authenticArtist)
                                    PlaybackController.cacheResolvedSpotifyId(spotifyId, item.id, authenticTitle, authenticArtist)
                                    PlaybackController.cacheResolvedSpotifyId(track.id, item.id, authenticTitle, authenticArtist)

                                    val finalThumb = SpotifyArtworkResolver.resolveArtwork(
                                        originalArtworkUrl = track.originalArtworkUrl.ifBlank { track.thumbnailUrl },
                                        spotifyId = spotifyId,
                                        title = authenticTitle,
                                        artist = authenticArtist,
                                        youtubeThumbnailUrl = item.thumbnailUrl,
                                        youtubeVideoId = item.id
                                    )

                                    val updatedTrack = track.copy(
                                        id = item.id,
                                        duration = item.duration,
                                        thumbnailUrl = finalThumb,
                                        originalTitle = authenticTitle,
                                        originalArtist = authenticArtist,
                                        originalArtworkUrl = track.originalArtworkUrl.ifBlank { track.thumbnailUrl },
                                        spotifyTrackId = spotifyId,
                                        matchedYouTubeId = item.id,
                                        matchedYouTubeTitle = item.title
                                    )

                                    UserDataRepository.updateTrackInPlaylists(track.id, updatedTrack)
                                    PlaybackController.playTrack(updatedTrack)
                                    Toast.makeText(context, "Matched to: ${item.title}", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(badgeColor.copy(alpha = 0.2f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = badgeLabel,
                                                color = badgeColor,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = item.duration,
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 11.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.title,
                                        color = Color.White,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.channelName,
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (isCurrentMatch) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF8B5CF6)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Active",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
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
}
