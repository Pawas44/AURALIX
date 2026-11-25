package com.videoguard.app.data

import android.content.Context
import android.content.SharedPreferences
import com.videoguard.app.model.UserPlaylist
import com.videoguard.app.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.net.HttpURLConnection
import java.util.UUID

data class DownloadTask(
    val videoId: String,
    val title: String,
    val artist: String = "",
    val thumbnailUrl: String = "",
    val progress: Int,
    val bytesText: String,
    val isComplete: Boolean = false,
    val isPaused: Boolean = false,
    val isFailed: Boolean = false,
    val totalTracksInBatch: Int = 0,
    val currentBatchIndex: Int = 0
)

data class BatchDownloadState(
    val playlistTitle: String,
    val currentTrackIndex: Int,
    val totalTracks: Int,
    val currentSongTitle: String,
    val currentSongProgress: Int,
    val isComplete: Boolean = false
)

object UserDataRepository {

    private const val PREFS_NAME = "videoguard_user_data"
    private const val KEY_HISTORY = "history_json"
    private const val KEY_PLAYLISTS = "playlists_json"
    private const val KEY_SUBSCRIPTIONS = "subscriptions_json"
    private const val KEY_SEARCH_HISTORY = "search_history_json"
    private const val KEY_OFFLINE = "offline_tracks_json"
    private const val KEY_UNLIKED_IDS = "unliked_video_ids"

    private var prefs: SharedPreferences? = null
    private val unlikedVideoIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private val _history = MutableStateFlow<List<VideoItem>>(emptyList())
    val history: StateFlow<List<VideoItem>> = _history.asStateFlow()

    private val _playlists = MutableStateFlow<List<UserPlaylist>>(emptyList())
    val playlists: StateFlow<List<UserPlaylist>> = _playlists.asStateFlow()

    private val _subscribedChannels = MutableStateFlow<Set<String>>(emptySet())
    val subscribedChannels: StateFlow<Set<String>> = _subscribedChannels.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private val _offlineTracks = MutableStateFlow<List<VideoItem>>(emptyList())
    val offlineTracks: StateFlow<List<VideoItem>> = _offlineTracks.asStateFlow()

    private val _currentDownload = MutableStateFlow<DownloadTask?>(null)
    val currentDownload: StateFlow<DownloadTask?> = _currentDownload.asStateFlow()

    private val _batchDownload = MutableStateFlow<BatchDownloadState?>(null)
    val batchDownload: StateFlow<BatchDownloadState?> = _batchDownload.asStateFlow()

    private val isDownloadPaused = java.util.concurrent.atomic.AtomicBoolean(false)
    private val isDownloadCancelled = java.util.concurrent.atomic.AtomicBoolean(false)
    private val downloadQueue = java.util.Collections.synchronizedList(mutableListOf<Pair<Context, VideoItem>>())
    private val _queuedDownloadsCount = MutableStateFlow(0)
    val queuedDownloadsCount: StateFlow<Int> = _queuedDownloadsCount.asStateFlow()

    fun pauseDownload() {
        isDownloadPaused.set(true)
        _currentDownload.value = _currentDownload.value?.copy(isPaused = true)
    }

    fun resumeDownload() {
        isDownloadPaused.set(false)
        _currentDownload.value = _currentDownload.value?.copy(isPaused = false)
    }

    fun cancelDownload() {
        isDownloadCancelled.set(true)
        isDownloadPaused.set(false)
        downloadQueue.clear()
        _queuedDownloadsCount.value = 0
        _currentDownload.value = null
        _batchDownload.value = null
    }

    fun playDownloadSuccessSound(context: Context) {
        try {
            val mp = android.media.MediaPlayer.create(context.applicationContext, com.videoguard.app.R.raw.download_success)
            mp?.setOnCompletionListener { player ->
                try {
                    player.stop()
                    player.release()
                } catch (e: Exception) {}
            }
            mp?.start()
        } catch (e: Exception) {
            android.util.Log.e("UserDataRepository", "Failed to play download sound: ${e.message}")
        }
    }

    private val repoScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    fun init(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp

        // Load History
        val historyStr = sp.getString(KEY_HISTORY, null)
        if (!historyStr.isNullOrBlank()) {
            try {
                val arr = JSONArray(historyStr)
                val list = mutableListOf<VideoItem>()
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { list.add(jsonToVideo(it)) }
                }
                _history.value = list.filter {
                    it.id != "jfKfPfyJRdk" && it.id != "5qap5aO4i9A" && it.id != "dtp6bOj69QD" &&
                            it.id != "dvgZkm1xWPE" && it.id != "OPf0YbXqDm0"
                }
                saveHistory()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Load Playlists
        val playlistsStr = sp.getString(KEY_PLAYLISTS, null)
        if (!playlistsStr.isNullOrBlank()) {
            try {
                val arr = JSONArray(playlistsStr)
                val list = mutableListOf<UserPlaylist>()
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { list.add(jsonToPlaylist(it)) }
                }
                _playlists.value = list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (_playlists.value.isEmpty()) {
            // Seed default playlists
            _playlists.value = listOf(
                UserPlaylist(
                    id = "liked_videos",
                    title = "Liked videos",
                    description = "Videos you've liked",
                    videos = emptyList(),
                    isSystem = true,
                    iconType = "like"
                ),
                UserPlaylist(
                    id = "watch_later",
                    title = "Watch later",
                    description = "Saved for later viewing",
                    videos = emptyList(),
                    isSystem = true,
                    iconType = "watch_later"
                ),
                UserPlaylist(
                    id = "favorites",
                    title = "Favorites",
                    description = "Personal favorite tracks & clips",
                    videos = emptyList(),
                    isSystem = false,
                    iconType = "playlist"
                )
            )
            savePlaylists()
        }

        // Load Subscriptions
        val subsStr = sp.getString(KEY_SUBSCRIPTIONS, null)
        if (!subsStr.isNullOrBlank()) {
            try {
                val arr = JSONArray(subsStr)
                val set = mutableSetOf<String>()
                for (i in 0 until arr.length()) {
                    set.add(arr.getString(i))
                }
                _subscribedChannels.value = set
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            _subscribedChannels.value = setOf("The Weeknd", "Dua Lipa")
            saveSubscriptions()
        }
        // Load Search History
        val searchHistoryStr = sp.getString(KEY_SEARCH_HISTORY, null)
        if (!searchHistoryStr.isNullOrBlank()) {
            try {
                val arr = JSONArray(searchHistoryStr)
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val item = arr.optString(i)
                    if (item.isNotBlank()) list.add(item)
                }
                _searchHistory.value = list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Load Unliked/Removed Liked Video IDs
        val unlikedSet = sp.getStringSet(KEY_UNLIKED_IDS, null)
        if (!unlikedSet.isNullOrEmpty()) {
            unlikedVideoIds.addAll(unlikedSet)
        }

        // Load Offline Tracks
        val offlineStr = sp.getString(KEY_OFFLINE, null)
        if (!offlineStr.isNullOrBlank()) {
            try {
                val arr = JSONArray(offlineStr)
                val list = mutableListOf<VideoItem>()
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { list.add(jsonToVideo(it)) }
                }
                _offlineTracks.value = list

                // Auto-healing: If any track has placeholder title like "YouTube Video (id)", fetch real song title asynchronously
                repoScope.launch {
                    var modified = false
                    val healed = _offlineTracks.value.map { trk ->
                        if (trk.title.contains("YouTube Video", ignoreCase = true) || trk.title.contains(trk.id)) {
                            val real = fetchRealVideoMeta(trk.id)
                            if (real != null && real.first.isNotBlank()) {
                                modified = true
                                trk.copy(
                                    title = real.first,
                                    artist = if (trk.artist.isBlank() || trk.artist == "Artist" || trk.artist == "YouTube Channel") real.second else trk.artist,
                                    channelName = if (trk.channelName == "YouTube Channel" || trk.channelName.isBlank()) real.second else trk.channelName,
                                    thumbnailUrl = if (trk.thumbnailUrl.isBlank()) real.third else trk.thumbnailUrl
                                )
                            } else trk
                        } else trk
                    }
                    if (modified) {
                        _offlineTracks.value = healed
                        saveOfflineTracks()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- Offline Tracks Management ---
    fun isDownloaded(id: String): Boolean {
        val resolvedId = com.videoguard.app.service.PlaybackController.getResolvedSpotifyId(id)
        return _offlineTracks.value.any { it.id == id || (resolvedId != null && it.id == resolvedId) }
    }

    fun formatCombinedDuration(tracks: List<VideoItem>): String {
        var totalSeconds = 0L
        for (t in tracks) {
            val parts = t.duration.trim().split(":").mapNotNull { it.toIntOrNull() }
            val sec = when (parts.size) {
                2 -> parts[0] * 60L + parts[1]
                3 -> parts[0] * 3600L + parts[1] * 60L + parts[2]
                else -> 0L
            }
            totalSeconds += sec
        }
        if (totalSeconds <= 0L) return "0 min"
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return when {
            hours > 0 && minutes > 0 -> "$hours hr $minutes min"
            hours > 0 -> "$hours hr"
            else -> "$minutes min"
        }
    }

    fun getDownloadsPlaylist(): UserPlaylist {
        val tracks = _offlineTracks.value
        return UserPlaylist(
            id = "downloads",
            title = "Downloads",
            description = "Offline downloaded songs",
            videos = tracks,
            isSystem = true,
            iconType = "download",
            customCoverUrl = tracks.firstOrNull()?.thumbnailUrl
        )
    }

    fun deleteDownload(context: Context, videoId: String) {
        val current = _offlineTracks.value.toMutableList()
        val removed = current.removeAll { it.id == videoId }
        if (removed) {
            _offlineTracks.value = current
            saveOfflineTracks()
            try {
                val f = File(context.filesDir, "downloads/$videoId.m4a")
                if (f.exists()) f.delete()
                val lrcFile = File(context.filesDir, "lyrics_cache/$videoId.json")
                if (lrcFile.exists()) lrcFile.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearAllDownloads(context: Context) {
        _offlineTracks.value = emptyList()
        saveOfflineTracks()
        try {
            val dlDir = File(context.filesDir, "downloads")
            if (dlDir.exists()) dlDir.deleteRecursively()
            val lrcDir = File(context.filesDir, "lyrics_cache")
            if (lrcDir.exists()) lrcDir.deleteRecursively()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun fetchRealVideoMeta(videoId: String): Triple<String, String, String>? {
        if (videoId.isBlank()) return null
        return try {
            val url = URL("https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode == 200) {
                val raw = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(raw)
                val rawTitle = json.optString("title", "").trim()
                val author = json.optString("author_name", "").trim()
                val thumb = json.optString("thumbnail_url", "").trim().ifBlank {
                    "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                }
                if (rawTitle.isNotBlank()) {
                    Triple(rawTitle, author, thumb)
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun downloadSong(context: Context, video: VideoItem) = startDownload(context, video)

    fun startDownload(context: Context, video: VideoItem) {
        val resolvedId = com.videoguard.app.service.PlaybackController.getResolvedSpotifyId(video.id)
        val isAlready = isDownloaded(video.id) || (resolvedId != null && isDownloaded(resolvedId))
        if (isAlready) {
            val current = _offlineTracks.value.toMutableList()
            current.removeAll { it.id == video.id || (resolvedId != null && it.id == resolvedId) }
            _offlineTracks.value = current
            saveOfflineTracks()
            try {
                val dir = File(context.filesDir, "downloads")
                val idsToDelete = listOfNotNull(video.id, resolvedId).distinct()
                idsToDelete.forEach { cid ->
                    listOf(
                        File(dir, "$cid.m4a"),
                        File(dir, "$cid.mp4"),
                        File(dir, "$cid.webm"),
                        File(dir, "$cid.tmp"),
                        File(context.filesDir, "lyrics_cache/$cid.json")
                    ).forEach { if (it.exists()) it.delete() }
                }
            } catch (e: Exception) {}
            repoScope.launch(Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Removed from downloads", android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }

        // If a download is already actively running, enqueue it
        val active = _currentDownload.value
        if (active != null && !active.isComplete) {
            if (downloadQueue.none { it.second.id == video.id }) {
                downloadQueue.add(Pair(context, video))
                _queuedDownloadsCount.value = downloadQueue.size
                android.widget.Toast.makeText(
                    context,
                    "Added to download queue (${downloadQueue.size} waiting)",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        startDownloadExecution(context, video)
    }

    private fun startDownloadExecution(context: Context, video: VideoItem) {
        isDownloadPaused.set(false)
        isDownloadCancelled.set(false)

        repoScope.launch {
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(
                    context,
                    "Downloading \"${video.title}\" for offline playback...",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            val ok = downloadSingleTrackDirect(context, video)
            if (ok) {
                playDownloadSuccessSound(context)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "✓ \"${video.title}\" is ready for offline playback",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } else if (!isDownloadCancelled.get()) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "Download failed. Please check internet connection.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }

            if (downloadQueue.isNotEmpty()) {
                kotlinx.coroutines.delay(400)
                val next = downloadQueue.removeAt(0)
                _queuedDownloadsCount.value = downloadQueue.size
                startDownloadExecution(next.first, next.second)
            } else {
                kotlinx.coroutines.delay(700)
                _currentDownload.value = null
            }
        }
    }

    fun downloadAllSongs(context: Context, playlist: UserPlaylist) {
        downloadAllSongs(context, playlist.videos, playlist.title)
    }

    fun downloadAllSongs(context: Context, videos: List<VideoItem>, playlistTitle: String) {
        val toDownload = videos.filter { !isDownloaded(it.id) }
        if (toDownload.isEmpty()) {
            android.widget.Toast.makeText(context, "All songs in \"$playlistTitle\" are already downloaded!", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        android.widget.Toast.makeText(
            context,
            "Downloading ${toDownload.size} songs from \"$playlistTitle\" in background...",
            android.widget.Toast.LENGTH_LONG
        ).show()

        repoScope.launch {
            var completedCount = 0
            _batchDownload.value = BatchDownloadState(
                playlistTitle = playlistTitle,
                currentTrackIndex = 1,
                totalTracks = toDownload.size,
                currentSongTitle = toDownload.first().title,
                currentSongProgress = 5,
                isComplete = false
            )

            for ((index, video) in toDownload.withIndex()) {
                val currentIdx = index + 1
                _batchDownload.value = BatchDownloadState(
                    playlistTitle = playlistTitle,
                    currentTrackIndex = currentIdx,
                    totalTracks = toDownload.size,
                    currentSongTitle = video.title,
                    currentSongProgress = 5,
                    isComplete = false
                )

                val ok = downloadSingleTrackDirect(
                    context = context,
                    video = video,
                    batchIndex = currentIdx,
                    batchTotal = toDownload.size
                )
                if (ok) completedCount++

                _batchDownload.value = BatchDownloadState(
                    playlistTitle = playlistTitle,
                    currentTrackIndex = currentIdx,
                    totalTracks = toDownload.size,
                    currentSongTitle = video.title,
                    currentSongProgress = 100,
                    isComplete = false
                )
            }

            _batchDownload.value = BatchDownloadState(
                playlistTitle = playlistTitle,
                currentTrackIndex = toDownload.size,
                totalTracks = toDownload.size,
                currentSongTitle = "All $completedCount/${toDownload.size} songs downloaded",
                currentSongProgress = 100,
                isComplete = true
            )

            if (completedCount > 0) {
                playDownloadSuccessSound(context)
            }

            _currentDownload.value = null
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(
                    context,
                    "✓ All songs downloaded ($completedCount/${toDownload.size}) from \"$playlistTitle\"",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }

            kotlinx.coroutines.delay(1000)
            _batchDownload.value = null
        }
    }

    suspend fun downloadSingleTrackDirect(
        context: Context,
        video: VideoItem,
        batchIndex: Int = 0,
        batchTotal: Int = 0
    ): Boolean = withContext(Dispatchers.IO) {
        var targetVideo = video

        // 1. Resolve placeholder IDs (e.g. spotify_...)
        if (targetVideo.id.startsWith("spotify_") || targetVideo.id.length != 11) {
            val cachedResolved = com.videoguard.app.service.PlaybackController.getResolvedSpotifyId(targetVideo.id)
            if (cachedResolved != null && cachedResolved.length == 11) {
                targetVideo = targetVideo.copy(id = cachedResolved)
            } else {
                val durSec = YouTubeMatchScorer.parseDurationSec(targetVideo.duration)
                val sId = targetVideo.spotifyTrackId.ifBlank { targetVideo.id.removePrefix("spotify_") }
                val meta = ImportTrackMeta(
                    title = targetVideo.originalTitle.ifBlank { targetVideo.title },
                    artist = targetVideo.originalArtist.ifBlank { targetVideo.artist },
                    album = targetVideo.album,
                    durationSeconds = durSec,
                    spotifyId = sId,
                    coverUrl = targetVideo.originalArtworkUrl.ifBlank { targetVideo.thumbnailUrl }
                )
                val resolved = SongMatchingRepository.resolveTrack(meta)
                if (resolved != null && resolved.id.length == 11) {
                    com.videoguard.app.service.PlaybackController.cacheResolvedSpotifyId(video.id, resolved.id, targetVideo.title, targetVideo.artist)
                    targetVideo = targetVideo.copy(
                        id = resolved.id,
                        duration = if (targetVideo.duration != "0:00" && targetVideo.duration.isNotBlank()) targetVideo.duration else resolved.duration,
                        thumbnailUrl = if (targetVideo.thumbnailUrl.isNotBlank() && !targetVideo.thumbnailUrl.contains("default")) targetVideo.thumbnailUrl else resolved.thumbnailUrl,
                        originalArtworkUrl = targetVideo.originalArtworkUrl.ifBlank { resolved.originalArtworkUrl },
                        matchedYouTubeId = resolved.id,
                        matchedYouTubeTitle = resolved.matchedYouTubeTitle
                    )
                }
            }
        }

        if (targetVideo.id.length != 11) return@withContext false
        if (isDownloaded(targetVideo.id) && isDownloaded(video.id)) return@withContext true

        // 2. Guarantee real metadata before initiating download
        if (targetVideo.title.contains("YouTube Video", ignoreCase = true) ||
            targetVideo.title.contains(targetVideo.id) ||
            targetVideo.title.isBlank() ||
            targetVideo.artist == "Artist" ||
            targetVideo.artist == "YouTube Channel"
        ) {
            val realMeta = fetchRealVideoMeta(targetVideo.id)
            if (realMeta != null && realMeta.first.isNotBlank()) {
                targetVideo = targetVideo.copy(
                    title = realMeta.first,
                    artist = if (targetVideo.artist.isBlank() || targetVideo.artist == "Artist" || targetVideo.artist == "YouTube Channel") realMeta.second else targetVideo.artist,
                    channelName = if (targetVideo.channelName == "YouTube Channel" || targetVideo.channelName.isBlank()) realMeta.second else targetVideo.channelName,
                    thumbnailUrl = if (targetVideo.thumbnailUrl.isBlank()) realMeta.third else targetVideo.thumbnailUrl
                )
            }
        }

        val dir = File(context.filesDir, "downloads")
        if (!dir.exists()) dir.mkdirs()
        val targetFile = File(dir, "${targetVideo.id}.m4a")

        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        val channelId = "downloads_channel"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Downloads",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            notifManager?.createNotificationChannel(channel)
        }
        val notifId = targetVideo.id.hashCode()

        val batchPrefix = if (batchTotal > 0) "($batchIndex/$batchTotal) " else ""
        _currentDownload.value = DownloadTask(
            videoId = targetVideo.id,
            title = targetVideo.title,
            artist = targetVideo.artist.ifBlank { targetVideo.channelName },
            thumbnailUrl = targetVideo.thumbnailUrl,
            progress = 5,
            bytesText = if (batchTotal > 0) "Track $batchIndex of $batchTotal" else "Connecting...",
            isComplete = false,
            isPaused = isDownloadPaused.get(),
            totalTracksInBatch = batchTotal,
            currentBatchIndex = batchIndex
        )

        var isRealStreamDownloaded = false
        val dlQuality = AppSettingsRepository.downloadQuality.value.lowercase()
        val candidateFormats = mutableListOf<AudioStreamFormat>()

        if (dlQuality == "low") {
            // Low quality: check pure adaptive low audio stream first, then fallback to muxed 18
            val primary = fetchRealYouTubeAudioFormat(targetVideo.id)
            if (primary != null) candidateFormats.add(primary)
            fetchMuxedFormat18(targetVideo.id)?.let { if (candidateFormats.none { f -> f.url == it.url }) candidateFormats.add(it) }
        } else {
            // High / Medium: Format 18 AAC unthrottled or high-bitrate adaptive
            fetchMuxedFormat18(targetVideo.id)?.let { candidateFormats.add(it) }
            val primary = fetchRealYouTubeAudioFormat(targetVideo.id)
            if (primary != null && candidateFormats.none { it.url == primary.url }) {
                candidateFormats.add(primary)
            }
        }

        for (format in candidateFormats) {
            if (targetFile.exists()) targetFile.delete()
            val ok = downloadStreamToFile(context, targetVideo, format, targetFile, notifManager, channelId, notifId, batchIndex, batchTotal)
            if (ok && targetFile.exists() && targetFile.length() > 100_000L) {
                isRealStreamDownloaded = true
                break
            } else {
                if (targetFile.exists()) targetFile.delete()
            }
        }

        if (!isRealStreamDownloaded) {
            _currentDownload.value = DownloadTask(
                videoId = targetVideo.id,
                title = targetVideo.title,
                artist = targetVideo.artist.ifBlank { targetVideo.channelName },
                thumbnailUrl = targetVideo.thumbnailUrl,
                progress = 0,
                bytesText = "Download failed",
                isComplete = true,
                isFailed = true,
                isPaused = false,
                totalTracksInBatch = batchTotal,
                currentBatchIndex = batchIndex
            )
            return@withContext false
        }

        // Mark download as 100% complete
        _currentDownload.value = DownloadTask(
            videoId = targetVideo.id,
            title = targetVideo.title,
            artist = targetVideo.artist.ifBlank { targetVideo.channelName },
            thumbnailUrl = targetVideo.thumbnailUrl,
            progress = 100,
            bytesText = "Complete",
            isComplete = true,
            isFailed = false,
            isPaused = false,
            totalTracksInBatch = batchTotal,
            currentBatchIndex = batchIndex
        )

        // Also create file alias for original ID if different (e.g., spotify_...)
        if (video.id != targetVideo.id) {
            try {
                val aliasFile = File(dir, "${video.id}.m4a")
                targetFile.copyTo(aliasFile, overwrite = true)
            } catch (e: Exception) {}
            try {
                updateTrackInPlaylists(video.id, targetVideo)
            } catch (e: Exception) {}
            com.videoguard.app.service.PlaybackController.cacheResolvedSpotifyId(video.id, targetVideo.id, targetVideo.title, targetVideo.artist)
        }

        val current = _offlineTracks.value.toMutableList()
        current.removeAll { it.id == targetVideo.id || it.id == video.id }
        current.add(0, targetVideo)
        if (video.id != targetVideo.id) {
            current.add(0, video)
        }
        _offlineTracks.value = current
        saveOfflineTracks()

        // Proactively fetch and cache lyrics locally for offline playback
        try {
            val parts = targetVideo.duration.trim().split(":").mapNotNull { it.toIntOrNull() }
            val durSec = when (parts.size) {
                3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                2 -> parts[0] * 60 + parts[1]
                1 -> parts[0]
                else -> 0
            }
            val fetchedLyrics = LyricsEngine.getLyrics(
                trackTitle = targetVideo.title,
                artistName = targetVideo.artist,
                durationSeconds = durSec,
                videoId = targetVideo.id,
                context = context,
                description = targetVideo.description,
                channelName = targetVideo.channelName
            )
            if (fetchedLyrics != null) {
                LyricsEngine.saveLyricsToDisk(context, targetVideo.id, fetchedLyrics)
                if (video.id != targetVideo.id) {
                    LyricsEngine.saveLyricsToDisk(context, video.id, fetchedLyrics)
                }
            }
        } catch (e: Exception) {}

        try {
            val completeNotif = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setContentTitle("Download Complete")
                .setContentText("${targetVideo.title} is ready for offline playback")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setProgress(0, 0, false)
                .setAutoCancel(true)
                .build()
            notifManager?.notify(notifId, completeNotif)
        } catch (e: Exception) {}

        true
    }

    private suspend fun downloadStreamToFile(
        context: Context,
        video: VideoItem,
        format: AudioStreamFormat,
        targetFile: File,
        notifManager: android.app.NotificationManager?,
        channelId: String,
        notifId: Int,
        batchIndex: Int = 0,
        batchTotal: Int = 0
    ): Boolean = withContext(Dispatchers.IO) {
        val tempFile = File(targetFile.parentFile, "${video.id}.tmp")
        try {
            if (tempFile.exists()) tempFile.delete()
            val url = format.url
            val userAgent = format.userAgent
            val totalBytes = if (format.contentLength > 0L) format.contentLength else 0L

            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Referer", "https://www.youtube.com/")
                setRequestProperty("Origin", "https://www.youtube.com")
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                setRequestProperty("Connection", "Keep-Alive")
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                android.util.Log.w("UserDataRepository", "downloadStreamToFile HTTP code $code for ${video.id}")
                return@withContext false
            }

            val expectedTotal = if (totalBytes > 0L) totalBytes else {
                val cl = conn.contentLengthLong
                if (cl > 0L) cl else 10_000_000L
            }

            var downloaded = 0L
            var lastNotifUpdate = 0L

            conn.inputStream.use { input ->
                java.io.FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isDownloadCancelled.get()) {
                            try { if (tempFile.exists()) tempFile.delete() } catch (ignored: Exception) {}
                            return@withContext false
                        }

                        while (isDownloadPaused.get()) {
                            kotlinx.coroutines.delay(200)
                            if (isDownloadCancelled.get()) {
                                try { if (tempFile.exists()) tempFile.delete() } catch (ignored: Exception) {}
                                return@withContext false
                            }
                        }

                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastNotifUpdate > 300) {
                            lastNotifUpdate = now
                            val percent = ((downloaded * 100) / expectedTotal).toInt().coerceIn(1, 99)
                            val mbNow = String.format(java.util.Locale.US, "%.1f", downloaded / (1024f * 1024f))
                            val mbTotal = String.format(java.util.Locale.US, "%.1f", expectedTotal / (1024f * 1024f))

                            _currentDownload.value = DownloadTask(
                                videoId = video.id,
                                title = video.title,
                                artist = video.artist.ifBlank { video.channelName },
                                thumbnailUrl = video.thumbnailUrl,
                                progress = percent,
                                bytesText = "$mbNow MB / $mbTotal MB",
                                isComplete = false,
                                isPaused = isDownloadPaused.get(),
                                totalTracksInBatch = batchTotal,
                                currentBatchIndex = batchIndex
                            )
                            if (_batchDownload.value != null) {
                                _batchDownload.value = _batchDownload.value?.copy(
                                    currentSongProgress = percent
                                )
                            }

                            try {
                                val notif = androidx.core.app.NotificationCompat.Builder(context, channelId)
                                    .setContentTitle("Downloading: ${video.title}")
                                    .setContentText("$percent% ($mbNow MB / $mbTotal MB)")
                                    .setSmallIcon(android.R.drawable.stat_sys_download)
                                    .setProgress(100, percent, false)
                                    .setOngoing(true)
                                    .setOnlyAlertOnce(true)
                                    .build()
                                notifManager?.notify(notifId, notif)
                            } catch (e: Exception) {}
                        }
                    }
                }
            }

            if (tempFile.exists() && tempFile.length() > 100_000L) {
                if (targetFile.exists()) targetFile.delete()
                val renamed = tempFile.renameTo(targetFile)
                renamed || (targetFile.exists() && targetFile.length() > 100_000L)
            } else {
                if (tempFile.exists()) tempFile.delete()
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("UserDataRepository", "downloadStreamToFile error for ${video.id}: ${e.message}")
            try { if (tempFile.exists()) tempFile.delete() } catch (ignored: Exception) {}
            false
        }
    }

    data class AudioStreamFormat(
        val url: String,
        val contentLength: Long,
        val mimeType: String,
        val bitrate: Int,
        val userAgent: String = "com.google.android.youtube/21.03.38 (Linux; U; Android 14) gzip",
        val clientName: String = "",
        val isMuxed: Boolean = false
    )

    /**
     * Tries to extract a usable stream URL from a signatureCipher field.
     * Format: s=...&sp=...&url=... (URL-encoded params)
     */
    private fun tryParseSignatureCipher(cipherStr: String): String? {
        try {
            val params = cipherStr.split("&").associate { part ->
                val kv = part.split("=", limit = 2)
                if (kv.size == 2) java.net.URLDecoder.decode(kv[0], "UTF-8") to java.net.URLDecoder.decode(kv[1], "UTF-8")
                else kv[0] to ""
            }
            val url = params["url"]
            if (!url.isNullOrBlank() && url.startsWith("http")) {
                return url
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Generic InnerTube player request — tries a single client config and returns the best audio/stream format.
     */
    private fun tryPlayerClient(
        videoId: String,
        clientName: String,
        clientVersion: String,
        clientId: String,
        osName: String,
        osVersion: String,
        deviceMake: String,
        deviceModel: String,
        userAgent: String,
        androidSdkVersion: String? = null,
        connectTimeoutMs: Int = 10000,
        readTimeoutMs: Int = 15000
    ): AudioStreamFormat? {
        return try {
            val clientObj = JSONObject().apply {
                put("clientName", clientName)
                put("clientVersion", clientVersion)
                put("clientId", clientId)
                put("osName", osName)
                put("osVersion", osVersion)
                put("deviceMake", deviceMake)
                put("deviceModel", deviceModel)
                put("hl", "en")
                put("gl", "US")
                if (androidSdkVersion != null) put("androidSdkVersion", androidSdkVersion)
            }
            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply { put("client", clientObj) })
            }
            val conn = (URL("https://www.youtube.com/youtubei/v1/player").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                this.connectTimeout = connectTimeoutMs
                this.readTimeout = readTimeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", userAgent)
            }
            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            if (conn.responseCode in 200..299) {
                val respText = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(respText)
                val streamingData = root.optJSONObject("streamingData")

                var bestFormat: AudioStreamFormat? = null

                // 1. Check adaptive audio formats (m4a, webm)
                val adaptiveFormats = streamingData?.optJSONArray("adaptiveFormats")
                if (adaptiveFormats != null) {
                    for (i in 0 until adaptiveFormats.length()) {
                        val fmt = adaptiveFormats.optJSONObject(i) ?: continue
                        val mime = fmt.optString("mimeType", "")
                        if (!mime.startsWith("audio/")) continue

                        var audioUrl = fmt.optString("url", "")
                        val bitrate = fmt.optInt("bitrate", 0)
                        val clen = fmt.optLong("contentLength", 0L)

                        if (audioUrl.isBlank()) {
                            val cipher = fmt.optString("signatureCipher", "")
                            if (cipher.isNotBlank()) {
                                audioUrl = tryParseSignatureCipher(cipher) ?: ""
                            }
                        }

                        if (audioUrl.isNotBlank()) {
                            val isMp4 = mime.contains("mp4") || mime.contains("m4a") || mime.contains("aac")
                            val currentIsMp4 = bestFormat?.mimeType?.let { it.contains("mp4") || it.contains("m4a") || it.contains("aac") } ?: false
                            val dlQuality = AppSettingsRepository.downloadQuality.value.lowercase()
                            val shouldPick = when (dlQuality) {
                                "low" -> {
                                    // Low: prefer lower bitrate (~40k-128k) to save space & data
                                    bestFormat == null || (bitrate in 40_000..128_000 && (bestFormat?.bitrate ?: 999_999) > bitrate)
                                }
                                "medium" -> {
                                    // Medium: prefer balanced ~128k-192k
                                    bestFormat == null || (bitrate in 110_000..192_000)
                                }
                                else -> {
                                    // High: prefer highest bitrate available
                                    when {
                                        bestFormat == null -> true
                                        isMp4 && !currentIsMp4 -> true
                                        isMp4 && currentIsMp4 && bitrate > (bestFormat?.bitrate ?: 0) -> true
                                        !isMp4 && !currentIsMp4 && bitrate > (bestFormat?.bitrate ?: 0) -> true
                                        else -> false
                                    }
                                }
                            }
                            if (shouldPick) {
                                bestFormat = AudioStreamFormat(
                                    url = audioUrl,
                                    contentLength = clen,
                                    mimeType = mime,
                                    bitrate = bitrate,
                                    userAgent = userAgent,
                                    clientName = clientName,
                                    isMuxed = false
                                )
                            }
                        }
                    }
                }

                // 2. Check formats for muxed format 18 (mp4 video + AAC audio)
                if (bestFormat == null) {
                    val formats = streamingData?.optJSONArray("formats")
                    if (formats != null) {
                        for (i in 0 until formats.length()) {
                            val fmt = formats.optJSONObject(i) ?: continue
                            val itag = fmt.optInt("itag", 0)
                            var streamUrl = fmt.optString("url", "")
                            if (streamUrl.isBlank()) {
                                val cipher = fmt.optString("signatureCipher", "")
                                if (cipher.isNotBlank()) {
                                    streamUrl = tryParseSignatureCipher(cipher) ?: ""
                                }
                            }
                            if (streamUrl.isNotBlank() && (itag == 18 || itag == 22)) {
                                bestFormat = AudioStreamFormat(
                                    url = streamUrl,
                                    contentLength = fmt.optLong("contentLength", 0L),
                                    mimeType = fmt.optString("mimeType", "video/mp4"),
                                    bitrate = fmt.optInt("bitrate", 0),
                                    userAgent = userAgent,
                                    clientName = clientName,
                                    isMuxed = true
                                )
                                break
                            }
                        }
                    }
                }

                if (bestFormat != null) {
                    android.util.Log.d("UserDataRepository", "Audio stream found via $clientName for $videoId, isMuxed=${bestFormat.isMuxed}, bitrate=${bestFormat.bitrate}")
                    return bestFormat
                }
            }
            null
        } catch (e: Exception) {
            android.util.Log.w("UserDataRepository", "tryPlayerClient $clientName failed for $videoId: ${e.message}")
            null
        }
    }

    suspend fun fetchMuxedFormat18(videoId: String): AudioStreamFormat? = withContext(Dispatchers.IO) {
        try {
            val clientObj = JSONObject().apply {
                put("clientName", "ANDROID")
                put("clientVersion", "21.03.38")
                put("clientId", "3")
                put("osName", "Android")
                put("osVersion", "14")
                put("hl", "en")
                put("gl", "US")
            }
            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply { put("client", clientObj) })
            }
            val conn = (URL("https://www.youtube.com/youtubei/v1/player").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "com.google.android.youtube/21.03.38 (Linux; U; Android 14) gzip")
            }
            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            if (conn.responseCode in 200..299) {
                val respText = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(respText)
                val streamingData = root.optJSONObject("streamingData")
                val formats = streamingData?.optJSONArray("formats")
                if (formats != null) {
                    for (i in 0 until formats.length()) {
                        val fmt = formats.optJSONObject(i) ?: continue
                        val itag = fmt.optInt("itag", 0)
                        val url = fmt.optString("url", "")
                        if (url.isNotBlank() && (itag == 18 || itag == 22)) {
                            return@withContext AudioStreamFormat(
                                url = url,
                                contentLength = fmt.optLong("contentLength", 0L),
                                mimeType = fmt.optString("mimeType", "video/mp4"),
                                bitrate = fmt.optInt("bitrate", 0),
                                userAgent = "com.google.android.youtube/21.03.38 (Linux; U; Android 14) gzip",
                                clientName = "ANDROID",
                                isMuxed = true
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("UserDataRepository", "fetchMuxedFormat18 error: ${e.message}")
        }
        null
    }

    suspend fun fetchRealYouTubeAudioFormat(videoId: String): AudioStreamFormat? = withContext(Dispatchers.IO) {
        // 1. ANDROID client (21.03.38) — provides reliable format 18 and audio streams
        tryPlayerClient(
            videoId = videoId,
            clientName = "ANDROID",
            clientVersion = "21.03.38",
            clientId = "3",
            osName = "Android",
            osVersion = "14",
            deviceMake = "Google",
            deviceModel = "Pixel 8",
            androidSdkVersion = "34",
            userAgent = "com.google.android.youtube/21.03.38 (Linux; U; Android 14) gzip"
        )?.let { return@withContext it }

        // 2. IOS client (iPad 21.03.3) — provides direct itag 140 m4a audio stream
        tryPlayerClient(
            videoId = videoId,
            clientName = "IOS",
            clientVersion = "21.03.3",
            clientId = "5",
            osName = "iPadOS",
            osVersion = "17.7.10.21H450",
            deviceMake = "Apple",
            deviceModel = "iPad7,6",
            userAgent = "com.google.ios.youtube/21.03.3 (iPad7,6; U; CPU iPadOS 17_7_10 like Mac OS X; en-US)"
        )?.let { return@withContext it }

        // 3. ANDROID_VR client (1.65.10) — guest client fallback
        tryPlayerClient(
            videoId = videoId,
            clientName = "ANDROID_VR",
            clientVersion = "1.65.10",
            clientId = "28",
            osName = "Android",
            osVersion = "12L",
            deviceMake = "Oculus",
            deviceModel = "Quest 3",
            androidSdkVersion = "32",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
        )?.let { return@withContext it }

        // 4. ANDROID_MUSIC (7.27.52)
        tryPlayerClient(
            videoId = videoId,
            clientName = "ANDROID_MUSIC",
            clientVersion = "7.27.52",
            clientId = "21",
            osName = "Android",
            osVersion = "14",
            deviceMake = "Google",
            deviceModel = "Pixel 8",
            androidSdkVersion = "34",
            userAgent = "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 14; Pixel 8 Build/UQ1A.240605.009) gzip"
        )?.let { return@withContext it }

        android.util.Log.e("UserDataRepository", "All audio format clients failed for $videoId")
        null
    }

    private suspend fun fetchFallbackYouTubeAudioUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        val fmt = fetchMuxedFormat18(videoId) ?: fetchRealYouTubeAudioFormat(videoId)
        fmt?.url
    }

    fun toggleDownload(video: VideoItem): Boolean {
        val current = _offlineTracks.value.toMutableList()
        val exists = current.any { it.id == video.id }
        return if (exists) {
            current.removeAll { it.id == video.id }
            _offlineTracks.value = current
            saveOfflineTracks()
            false
        } else {
            current.add(0, video)
            _offlineTracks.value = current
            saveOfflineTracks()

            if (video.title.contains("YouTube Video", ignoreCase = true) ||
                video.title.contains(video.id) ||
                video.title.isBlank() ||
                video.artist == "Artist" ||
                video.artist == "YouTube Channel"
            ) {
                repoScope.launch {
                    val realMeta = fetchRealVideoMeta(video.id)
                    if (realMeta != null && realMeta.first.isNotBlank()) {
                        val updated = _offlineTracks.value.map { trk ->
                            if (trk.id == video.id) {
                                trk.copy(
                                    title = realMeta.first,
                                    artist = if (trk.artist.isBlank() || trk.artist == "Artist" || trk.artist == "YouTube Channel") realMeta.second else trk.artist,
                                    channelName = if (trk.channelName == "YouTube Channel" || trk.channelName.isBlank()) realMeta.second else trk.channelName,
                                    thumbnailUrl = if (trk.thumbnailUrl.isBlank()) realMeta.third else trk.thumbnailUrl
                                )
                            } else trk
                        }
                        _offlineTracks.value = updated
                        saveOfflineTracks()
                    }
                }
            }
            true
        }
    }

    private fun saveOfflineTracks() {
        val arr = JSONArray()
        for (v in _offlineTracks.value) {
            arr.put(videoToJson(v))
        }
        prefs?.edit()?.putString(KEY_OFFLINE, arr.toString())?.apply()
    }

    // --- Search History Management ---
    fun addSearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val current = _searchHistory.value.toMutableList()
        current.removeAll { it.equals(trimmed, ignoreCase = true) }
        current.add(0, trimmed)
        val trimmedList = current.take(50)
        _searchHistory.value = trimmedList
        saveSearchHistory(trimmedList)
    }

    fun removeSearchQuery(query: String) {
        val current = _searchHistory.value.toMutableList()
        current.removeAll { it.equals(query, ignoreCase = true) }
        _searchHistory.value = current
        saveSearchHistory(current)
    }

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        saveSearchHistory(emptyList())
    }

    private fun saveSearchHistory(list: List<String>) {
        try {
            val arr = JSONArray()
            list.forEach { arr.put(it) }
            prefs?.edit()?.putString(KEY_SEARCH_HISTORY, arr.toString())?.apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- History Management ---
    fun addToHistory(video: VideoItem) {
        val current = _history.value.toMutableList()
        current.removeAll { it.id == video.id }
        current.add(0, video)
        if (current.size > 100) {
            _history.value = current.take(100)
        } else {
            _history.value = current
        }
        saveHistory()
    }

    fun removeFromHistory(videoId: String) {
        _history.value = _history.value.filter { it.id != videoId }
        saveHistory()
    }

    fun clearHistory() {
        _history.value = emptyList()
        saveHistory()
    }

    fun setOnlineHistory(onlineVideos: List<VideoItem>) {
        if (onlineVideos.isNotEmpty()) {
            _history.value = onlineVideos.take(100)
            saveHistory()
        }
    }

    fun setOnlinePlaylists(onlinePlaylists: List<UserPlaylist>) {
        if (onlinePlaylists.isNotEmpty()) {
            val systemPlaylists = _playlists.value.filter { it.isSystem }
            val localCustom = _playlists.value.filter { it.id.startsWith("playlist_") && !it.isSystem }
            val existingVideosMap = _playlists.value.associate { it.id to it.videos }

            val mergedOnline = onlinePlaylists.map { pl ->
                val cached = existingVideosMap[pl.id]
                if (pl.videos.isNotEmpty()) {
                    pl
                } else if (!cached.isNullOrEmpty()) {
                    pl.copy(videos = cached, customCoverUrl = pl.customCoverUrl ?: cached.firstOrNull()?.thumbnailUrl)
                } else {
                    pl
                }
            }

            _playlists.value = systemPlaylists + mergedOnline + localCustom
            savePlaylists()
        }
    }

    fun updatePlaylistVideos(playlistId: String, videos: List<VideoItem>) {
        if (videos.isEmpty()) return
        if (playlistId == "liked_videos") {
            updateLikedVideos(videos)
            return
        }
        if (playlistId == "watch_later") {
            updateWatchLaterVideos(videos)
            return
        }
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val pl = current[index]
            val cover = pl.customCoverUrl ?: videos.firstOrNull()?.thumbnailUrl
            current[index] = pl.copy(videos = videos, customCoverUrl = cover)
            _playlists.value = current
            savePlaylists()
        } else {
            val newPl = UserPlaylist(
                id = playlistId,
                title = "Album",
                videos = videos,
                customCoverUrl = videos.firstOrNull()?.thumbnailUrl
            )
            current.add(newPl)
            _playlists.value = current
            savePlaylists()
        }
    }

    fun updateWatchLaterVideos(watchLaterVideos: List<VideoItem>) {
        if (watchLaterVideos.isEmpty()) return
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == "watch_later" }
        if (index != -1) {
            current[index] = current[index].copy(videos = watchLaterVideos)
        } else {
            current.add(UserPlaylist(
                id = "watch_later",
                title = "Watch later",
                description = "Saved for later viewing",
                videos = watchLaterVideos,
                isSystem = true,
                iconType = "watch_later"
            ))
        }
        _playlists.value = current
        savePlaylists()
    }

    fun updateLikedVideos(likedVideos: List<VideoItem>) {
        if (likedVideos.isEmpty()) return
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == "liked_videos" }
        if (index != -1) {
            val existing = current[index].videos
            // Filter out remote videos that the user explicitly removed/unliked locally
            val cleanRemote = likedVideos.filterNot { unlikedVideoIds.contains(it.id) }
            val existingIds = existing.map { it.id }.toSet()
            val newFromRemote = cleanRemote.filter { it.id !in existingIds }
            val merged = (existing + newFromRemote).filterNot { unlikedVideoIds.contains(it.id) }.distinctBy { it.id }
            current[index] = current[index].copy(videos = merged)
        } else {
            val clean = likedVideos.filterNot { unlikedVideoIds.contains(it.id) }
            current.add(0, UserPlaylist(
                id = "liked_videos",
                title = "Liked videos",
                description = "Videos you've liked",
                videos = clean,
                isSystem = true,
                iconType = "like"
            ))
        }
        _playlists.value = current
        savePlaylists()
    }

    private fun saveUnlikedIds() {
        val sp = prefs ?: return
        try {
            sp.edit().putStringSet(KEY_UNLIKED_IDS, unlikedVideoIds.toSet()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveHistory() {
        val sp = prefs ?: return
        try {
            val arr = JSONArray()
            _history.value.forEach { arr.put(videoToJson(it)) }
            sp.edit().putString(KEY_HISTORY, arr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Playlist Management ---
    fun isLiked(videoId: String, title: String = "", artist: String = ""): Boolean {
        val likedPlaylist = _playlists.value.find { it.id == "liked_videos" } ?: return false
        return likedPlaylist.videos.any {
            it.id == videoId || (title.isNotBlank() && it.title.equals(title, ignoreCase = true) && (artist.isBlank() || it.artist.isBlank() || it.artist.equals(artist, ignoreCase = true)))
        }
    }

    fun toggleLiked(video: VideoItem) {
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == "liked_videos" }
        if (index != -1) {
            val playlist = current[index]
            val videos = playlist.videos.toMutableList()
            val alreadyLiked = videos.any {
                it.id == video.id || (it.title.isNotBlank() && it.title.equals(video.title, ignoreCase = true))
            }
            if (alreadyLiked) {
                videos.removeAll {
                    it.id == video.id || (it.title.isNotBlank() && it.title.equals(video.title, ignoreCase = true))
                }
                unlikedVideoIds.add(video.id)
                saveUnlikedIds()
            } else {
                unlikedVideoIds.remove(video.id)
                saveUnlikedIds()
                videos.add(0, video)
                com.videoguard.app.recommendation.ListeningHistoryManager.onTrackLiked(video)
            }
            current[index] = playlist.copy(videos = videos)
            _playlists.value = current
            savePlaylists()
        }
    }

    fun isWatchLater(videoId: String): Boolean {
        val watchLater = _playlists.value.find { it.id == "watch_later" } ?: return false
        return watchLater.videos.any { it.id == videoId }
    }

    fun toggleWatchLater(video: VideoItem) {
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == "watch_later" }
        if (index != -1) {
            val playlist = current[index]
            val videos = playlist.videos.toMutableList()
            if (videos.any { it.id == video.id }) {
                videos.removeAll { it.id == video.id }
            } else {
                videos.add(0, video)
            }
            current[index] = playlist.copy(videos = videos)
            _playlists.value = current
            savePlaylists()
        }
    }

    fun createPlaylist(title: String): UserPlaylist {
        val newPlaylist = UserPlaylist(
            id = "playlist_" + UUID.randomUUID().toString().take(8),
            title = title.ifBlank { "New Playlist" },
            videos = emptyList(),
            isSystem = false,
            iconType = "playlist"
        )
        val current = _playlists.value.toMutableList()
        current.add(newPlaylist)
        _playlists.value = current
        savePlaylists()
        return newPlaylist
    }

    fun addVideoToPlaylist(playlistId: String, video: VideoItem) {
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val playlist = current[index]
            val videos = playlist.videos.toMutableList()
            if (videos.none { it.id == video.id }) {
                videos.add(video)
                current[index] = playlist.copy(videos = videos)
                _playlists.value = current
                savePlaylists()
                com.videoguard.app.recommendation.ListeningHistoryManager.onTrackAddedToPlaylist(video)
            }
        }
    }

    fun removeVideoFromPlaylist(playlistId: String, videoId: String) {
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val playlist = current[index]
            val videos = playlist.videos.filter { it.id != videoId }
            if (playlistId == "liked_videos") {
                unlikedVideoIds.add(videoId)
                saveUnlikedIds()
            }
            current[index] = playlist.copy(videos = videos)
            _playlists.value = current
            savePlaylists()
        }
    }

    fun deletePlaylist(playlistId: String) {
        _playlists.value = _playlists.value.filter { it.id != playlistId || it.isSystem }
        savePlaylists()
    }

    fun clearPlaylist(playlistId: String) {
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            current[index] = current[index].copy(videos = emptyList())
            _playlists.value = current
            savePlaylists()
        }
    }

    fun savePlaylist(playlist: UserPlaylist) {
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlist.id }
        if (index != -1) {
            current[index] = playlist
        } else {
            current.add(playlist)
        }
        _playlists.value = current
        savePlaylists()
    }

    fun updateTrackInPlaylists(oldId: String, newTrack: VideoItem) {
        val current = _playlists.value.toMutableList()
        var changed = false
        for (i in current.indices) {
            val pl = current[i]
            val vIdx = pl.videos.indexOfFirst { it.id == oldId }
            if (vIdx != -1) {
                val updatedVideos = pl.videos.toMutableList()
                updatedVideos[vIdx] = newTrack
                current[i] = pl.copy(videos = updatedVideos)
                changed = true
            }
        }
        if (changed) {
            _playlists.value = current
            savePlaylists()
        }
    }

    private fun savePlaylists() {
        val sp = prefs ?: return
        try {
            val arr = JSONArray()
            _playlists.value.forEach { arr.put(playlistToJson(it)) }
            sp.edit().putString(KEY_PLAYLISTS, arr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Subscriptions Management ---
    fun isSubscribed(channelName: String): Boolean {
        return _subscribedChannels.value.contains(channelName)
    }

    fun toggleSubscribe(channelName: String) {
        val current = _subscribedChannels.value.toMutableSet()
        if (current.contains(channelName)) {
            current.remove(channelName)
        } else {
            current.add(channelName)
        }
        _subscribedChannels.value = current
        saveSubscriptions()
    }

    fun setSubscribedChannels(channels: Set<String>) {
        if (channels.isNotEmpty()) {
            _subscribedChannels.value = channels
            saveSubscriptions()
        }
    }

    private fun saveSubscriptions() {
        val sp = prefs ?: return
        try {
            val arr = JSONArray()
            _subscribedChannels.value.forEach { arr.put(it) }
            sp.edit().putString(KEY_SUBSCRIPTIONS, arr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- JSON Helpers ---
    fun videoToJson(v: VideoItem): JSONObject = JSONObject().apply {
        put("id", v.id)
        put("title", v.title)
        put("channelName", v.channelName)
        put("channelAvatarUrl", v.channelAvatarUrl)
        put("subscriberCount", v.subscriberCount)
        put("thumbnailUrl", v.thumbnailUrl)
        put("views", v.views)
        put("uploadTime", v.uploadTime)
        put("duration", v.duration)
        put("description", v.description)
        put("likes", v.likes)
        put("isMusicTrack", v.isMusicTrack)
        put("artist", v.artist)
        put("album", v.album)
        put("lyrics", v.lyrics)
        put("category", v.category)
        put("mood", v.mood)
        put("channelId", v.channelId)
        put("originalTitle", v.originalTitle)
        put("originalArtist", v.originalArtist)
        put("originalArtworkUrl", v.originalArtworkUrl)
        put("spotifyTrackId", v.spotifyTrackId)
        put("matchedYouTubeId", v.matchedYouTubeId)
        put("matchedYouTubeTitle", v.matchedYouTubeTitle)
    }

    fun jsonToVideo(obj: JSONObject): VideoItem = VideoItem(
        id = obj.optString("id", ""),
        title = obj.optString("title", ""),
        channelName = obj.optString("channelName", ""),
        channelAvatarUrl = obj.optString("channelAvatarUrl", ""),
        subscriberCount = obj.optString("subscriberCount", "5.2M subscribers"),
        thumbnailUrl = obj.optString("thumbnailUrl", ""),
        views = obj.optString("views", ""),
        uploadTime = obj.optString("uploadTime", ""),
        duration = obj.optString("duration", ""),
        description = obj.optString("description", ""),
        likes = obj.optString("likes", "45K"),
        isMusicTrack = obj.optBoolean("isMusicTrack", false),
        artist = obj.optString("artist", ""),
        album = obj.optString("album", ""),
        lyrics = obj.optString("lyrics", ""),
        category = obj.optString("category", "All"),
        mood = obj.optString("mood", "Energize"),
        channelId = obj.optString("channelId", ""),
        originalTitle = obj.optString("originalTitle", ""),
        originalArtist = obj.optString("originalArtist", ""),
        originalArtworkUrl = obj.optString("originalArtworkUrl", ""),
        spotifyTrackId = obj.optString("spotifyTrackId", ""),
        matchedYouTubeId = obj.optString("matchedYouTubeId", ""),
        matchedYouTubeTitle = obj.optString("matchedYouTubeTitle", "")
    )

    fun restoreFullData(restoredHistory: List<VideoItem>, restoredPlaylists: List<UserPlaylist>) {
        if (restoredHistory.isNotEmpty()) {
            _history.value = restoredHistory
            saveHistory()
        }
        if (restoredPlaylists.isNotEmpty()) {
            _playlists.value = restoredPlaylists
            savePlaylists()
        }
    }

    fun playlistToJson(p: UserPlaylist): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("title", p.title)
        put("description", p.description)
        put("isSystem", p.isSystem)
        put("iconType", p.iconType)
        val arr = JSONArray()
        p.videos.forEach { arr.put(videoToJson(it)) }
        put("videos", arr)
    }

    fun jsonToPlaylist(obj: JSONObject): UserPlaylist {
        val videosList = mutableListOf<VideoItem>()
        val arr = obj.optJSONArray("videos")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val vObj = arr.optJSONObject(i)
                if (vObj != null) {
                    videosList.add(jsonToVideo(vObj))
                }
            }
        }
        return UserPlaylist(
            id = obj.optString("id", UUID.randomUUID().toString()),
            title = obj.optString("title", "Untitled"),
            description = obj.optString("description", ""),
            videos = videosList,
            isSystem = obj.optBoolean("isSystem", false),
            iconType = obj.optString("iconType", "playlist")
        )
    }
}
