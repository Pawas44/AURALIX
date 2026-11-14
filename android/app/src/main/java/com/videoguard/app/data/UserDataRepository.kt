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
    val progress: Int,
    val bytesText: String,
    val isComplete: Boolean = false
)

object UserDataRepository {

    private const val PREFS_NAME = "videoguard_user_data"
    private const val KEY_HISTORY = "history_json"
    private const val KEY_PLAYLISTS = "playlists_json"
    private const val KEY_SUBSCRIPTIONS = "subscriptions_json"
    private const val KEY_SEARCH_HISTORY = "search_history_json"
    private const val KEY_OFFLINE = "offline_tracks_json"

    private var prefs: SharedPreferences? = null

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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- Offline Tracks Management ---
    fun isDownloaded(id: String): Boolean = _offlineTracks.value.any { it.id == id }

    fun startDownload(context: Context, video: VideoItem) {
        val isAlready = isDownloaded(video.id)
        if (isAlready) {
            val current = _offlineTracks.value.toMutableList()
            current.removeAll { it.id == video.id }
            _offlineTracks.value = current
            saveOfflineTracks()
            try {
                val f = File(context.filesDir, "downloads/${video.id}.m4a")
                if (f.exists()) f.delete()
            } catch (e: Exception) {}
            return
        }

        repoScope.launch {
            val dir = File(context.filesDir, "downloads")
            if (!dir.exists()) dir.mkdirs()
            val targetFile = File(dir, "${video.id}.m4a")

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
            val notifId = video.id.hashCode()

            _currentDownload.value = DownloadTask(
                videoId = video.id,
                title = video.title,
                progress = 5,
                bytesText = "Connecting...",
                isComplete = false
            )

            var isRealStreamDownloaded = false
            val audioFormat = fetchRealYouTubeAudioFormat(video.id)

            if (audioFormat != null && audioFormat.url.isNotBlank()) {
                val streamUrl = audioFormat.url
                val userAgent = "com.google.ios.youtube/21.03.3 (iPad7,6; U; CPU iPadOS 17_7_10 like Mac OS X; en-US)"
                var totalBytes = if (audioFormat.contentLength > 0) audioFormat.contentLength else 0L

                // Clean old corrupted/partial file to guarantee clean full download
                if (targetFile.exists()) {
                    targetFile.delete()
                }

                var downloaded = 0L
                var retries = 0
                val minValidBytes = 1_200_000L // Complete song must be at least 1.2 MB

                while (retries < 15) {
                    if (totalBytes > 0L && downloaded >= (totalBytes * 0.98).toLong()) {
                        break
                    }

                    try {
                        val conn = (URL(streamUrl).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 15000
                            readTimeout = 25000
                            setRequestProperty("User-Agent", userAgent)
                            if (downloaded > 0L) {
                                if (totalBytes > downloaded) {
                                    setRequestProperty("Range", "bytes=$downloaded-${totalBytes - 1}")
                                } else {
                                    setRequestProperty("Range", "bytes=$downloaded-")
                                }
                            }
                            setRequestProperty("Accept", "*/*")
                        }

                        val code = conn.responseCode
                        if (code in 200..299) {
                            if (totalBytes <= 0L) {
                                val cl = conn.contentLengthLong
                                if (cl > 0L) {
                                    totalBytes = if (downloaded > 0L) downloaded + cl else cl
                                }
                            }

                            conn.inputStream.use { input ->
                                java.io.FileOutputStream(targetFile, downloaded > 0L).use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    var bytesRead: Int
                                    var lastNotifUpdate = 0L
                                    while (input.read(buffer).also { bytesRead = it } != -1) {
                                        output.write(buffer, 0, bytesRead)
                                        downloaded += bytesRead
                                        retries = 0

                                        val currentTotal = if (totalBytes > 0L) totalBytes else 4_500_000L
                                        val percent = ((downloaded * 100) / currentTotal).toInt().coerceIn(1, 99)
                                        val mbNow = String.format(java.util.Locale.US, "%.1f", downloaded / (1024f * 1024f))
                                        val mbTotal = String.format(java.util.Locale.US, "%.1f", currentTotal / (1024f * 1024f))

                                        val now = System.currentTimeMillis()
                                        if (now - lastNotifUpdate > 500) {
                                            lastNotifUpdate = now
                                            _currentDownload.value = DownloadTask(
                                                videoId = video.id,
                                                title = video.title,
                                                progress = percent,
                                                bytesText = "$mbNow MB / $mbTotal MB",
                                                isComplete = false
                                            )

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

                            if (totalBytes > 0L && downloaded >= (totalBytes * 0.98).toLong()) {
                                break
                            }
                        } else if (code == 416) {
                            // Reached end of stream
                            break
                        } else {
                            retries++
                            kotlinx.coroutines.delay(600)
                        }
                    } catch (e: Exception) {
                        retries++
                        kotlinx.coroutines.delay(600)
                    }
                }

                val expectedThreshold = if (totalBytes > 0L) (totalBytes * 0.94).toLong() else minValidBytes
                if (targetFile.exists() && targetFile.length() >= expectedThreshold && targetFile.length() >= minValidBytes) {
                    isRealStreamDownloaded = true
                }
            }

            if (!isRealStreamDownloaded) {
                // Secondary fallback: attempt direct playback stream
                try {
                    val fallbackUrl = fetchFallbackYouTubeAudioUrl(video.id)
                    if (!fallbackUrl.isNullOrBlank()) {
                        val conn = (URL(fallbackUrl).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 15000
                            readTimeout = 25000
                            setRequestProperty("User-Agent", "com.google.ios.youtube/21.03.3 (iPad7,6; U; CPU iPadOS 17_7_10 like Mac OS X; en-US)")
                            setRequestProperty("Accept", "*/*")
                        }
                        conn.inputStream.use { input ->
                            java.io.FileOutputStream(targetFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (targetFile.exists() && targetFile.length() >= 1_200_000L) {
                            isRealStreamDownloaded = true
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (!isRealStreamDownloaded) {
                _currentDownload.value = DownloadTask(
                    videoId = video.id,
                    title = video.title,
                    progress = 0,
                    bytesText = "Download failed (network error)",
                    isComplete = true
                )
                kotlinx.coroutines.delay(2000)
                _currentDownload.value = null
                return@launch
            }

            // Mark download as 100% complete
            _currentDownload.value = DownloadTask(
                videoId = video.id,
                title = video.title,
                progress = 100,
                bytesText = "Complete",
                isComplete = true
            )

            try {
                val completeNotif = androidx.core.app.NotificationCompat.Builder(context, channelId)
                    .setContentTitle("Download Complete")
                    .setContentText("${video.title} is ready for offline playback")
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setProgress(0, 0, false)
                    .setAutoCancel(true)
                    .build()
                notifManager?.notify(notifId, completeNotif)
            } catch (e: Exception) {}

            val current = _offlineTracks.value.toMutableList()
            current.removeAll { it.id == video.id }
            current.add(0, video)
            _offlineTracks.value = current
            saveOfflineTracks()

            kotlinx.coroutines.delay(1500)
            _currentDownload.value = null
        }
    }

    data class AudioStreamFormat(
        val url: String,
        val contentLength: Long,
        val mimeType: String,
        val bitrate: Int
    )

    private suspend fun fetchRealYouTubeAudioFormat(videoId: String): AudioStreamFormat? = withContext(Dispatchers.IO) {
        // 1. Primary Client: IOS 21.03.3 (Guaranteed audio stream for all music & VEVO tracks)
        try {
            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "IOS")
                        put("clientVersion", "21.03.3")
                        put("clientId", "5")
                        put("osName", "iPadOS")
                        put("osVersion", "17.7.10.21H450")
                        put("deviceMake", "Apple")
                        put("deviceModel", "iPad7,6")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
            }
            val conn = (URL("https://www.youtube.com/youtubei/v1/player").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12000
                readTimeout = 12000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "com.google.ios.youtube/21.03.3 (iPad7,6; U; CPU iPadOS 17_7_10 like Mac OS X; en-US)")
            }
            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            if (conn.responseCode in 200..299) {
                val respText = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(respText)
                val streamingData = root.optJSONObject("streamingData")
                val adaptiveFormats = streamingData?.optJSONArray("adaptiveFormats")
                if (adaptiveFormats != null) {
                    var bestFormat: AudioStreamFormat? = null
                    for (i in 0 until adaptiveFormats.length()) {
                        val fmt = adaptiveFormats.optJSONObject(i) ?: continue
                        val mime = fmt.optString("mimeType", "")
                        val audioUrl = fmt.optString("url", "")
                        val bitrate = fmt.optInt("bitrate", 0)
                        val clen = fmt.optLong("contentLength", 0L)
                        if (mime.startsWith("audio/") && audioUrl.isNotBlank()) {
                            if (bestFormat == null || mime.contains("mp4") || bitrate > bestFormat.bitrate) {
                                bestFormat = AudioStreamFormat(audioUrl, clen, mime, bitrate)
                            }
                        }
                    }
                    if (bestFormat != null) return@withContext bestFormat
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Secondary Client: VISIONOS
        try {
            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "VISIONOS")
                        put("clientVersion", "0.1")
                        put("clientId", "101")
                        put("osName", "visionOS")
                        put("osVersion", "1.3.21O771")
                        put("deviceMake", "Apple")
                        put("deviceModel", "RealityDevice14,1")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("playbackContext", JSONObject().apply {
                    put("contentPlaybackContext", JSONObject().apply {
                        put("html5Preference", "HTML5_PREF_WANTS")
                        put("signatureTimestamp", 20000)
                    })
                })
            }
            val conn = (URL("https://www.youtube.com/youtubei/v1/player").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12000
                readTimeout = 12000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15")
            }
            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            if (conn.responseCode in 200..299) {
                val respText = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(respText)
                val streamingData = root.optJSONObject("streamingData")
                val adaptiveFormats = streamingData?.optJSONArray("adaptiveFormats")
                if (adaptiveFormats != null) {
                    var bestFormat: AudioStreamFormat? = null
                    for (i in 0 until adaptiveFormats.length()) {
                        val fmt = adaptiveFormats.optJSONObject(i) ?: continue
                        val mime = fmt.optString("mimeType", "")
                        val audioUrl = fmt.optString("url", "")
                        val bitrate = fmt.optInt("bitrate", 0)
                        val clen = fmt.optLong("contentLength", 0L)
                        if (mime.startsWith("audio/") && audioUrl.isNotBlank()) {
                            if (bestFormat == null || mime.contains("mp4") || bitrate > bestFormat.bitrate) {
                                bestFormat = AudioStreamFormat(audioUrl, clen, mime, bitrate)
                            }
                        }
                    }
                    if (bestFormat != null) return@withContext bestFormat
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Fallback Client: ANDROID_VR 1.65.10
        try {
            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID_VR")
                        put("clientVersion", "1.65.10")
                        put("clientId", "28")
                        put("osName", "Android")
                        put("osVersion", "12L")
                        put("deviceMake", "Oculus")
                        put("deviceModel", "Quest 3")
                        put("androidSdkVersion", "32")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
            }
            val conn = (URL("https://www.youtube.com/youtubei/v1/player").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12000
                readTimeout = 12000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip")
            }
            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            if (conn.responseCode in 200..299) {
                val respText = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(respText)
                val streamingData = root.optJSONObject("streamingData")
                val adaptiveFormats = streamingData?.optJSONArray("adaptiveFormats")
                if (adaptiveFormats != null) {
                    for (i in 0 until adaptiveFormats.length()) {
                        val fmt = adaptiveFormats.optJSONObject(i) ?: continue
                        val mime = fmt.optString("mimeType", "")
                        val audioUrl = fmt.optString("url", "")
                        val bitrate = fmt.optInt("bitrate", 0)
                        val clen = fmt.optLong("contentLength", 0L)
                        if (mime.startsWith("audio/") && audioUrl.isNotBlank()) {
                            return@withContext AudioStreamFormat(audioUrl, clen, mime, bitrate)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        null
    }

    private suspend fun fetchFallbackYouTubeAudioUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "IOS")
                        put("clientVersion", "21.03.3")
                        put("clientId", "5")
                        put("osName", "iPadOS")
                        put("osVersion", "17.7.10.21H450")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
            }
            val conn = (URL("https://www.youtube.com/youtubei/v1/player").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12000
                readTimeout = 12000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "com.google.ios.youtube/21.03.3 (iPad7,6; U; CPU iPadOS 17_7_10 like Mac OS X; en-US)")
            }
            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            if (conn.responseCode in 200..299) {
                val respText = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(respText)
                val streamingData = root.optJSONObject("streamingData")
                val adaptiveFormats = streamingData?.optJSONArray("adaptiveFormats")
                if (adaptiveFormats != null) {
                    for (i in 0 until adaptiveFormats.length()) {
                        val fmt = adaptiveFormats.optJSONObject(i) ?: continue
                        val mime = fmt.optString("mimeType", "")
                        val audioUrl = fmt.optString("url", "")
                        if (mime.startsWith("audio/") && audioUrl.isNotBlank()) {
                            return@withContext audioUrl
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
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
            current[index] = current[index].copy(videos = likedVideos)
        } else {
            current.add(0, UserPlaylist(
                id = "liked_videos",
                title = "Liked videos",
                description = "Videos you've liked",
                videos = likedVideos,
                isSystem = true,
                iconType = "like"
            ))
        }
        _playlists.value = current
        savePlaylists()
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
    fun isLiked(videoId: String): Boolean {
        val likedPlaylist = _playlists.value.find { it.id == "liked_videos" } ?: return false
        return likedPlaylist.videos.any { it.id == videoId }
    }

    fun toggleLiked(video: VideoItem) {
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == "liked_videos" }
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
            }
        }
    }

    fun removeVideoFromPlaylist(playlistId: String, videoId: String) {
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val playlist = current[index]
            val videos = playlist.videos.filter { it.id != videoId }
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
    private fun videoToJson(v: VideoItem): JSONObject = JSONObject().apply {
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
    }

    private fun jsonToVideo(obj: JSONObject): VideoItem = VideoItem(
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
        mood = obj.optString("mood", "Energize")
    )

    private fun playlistToJson(p: UserPlaylist): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("title", p.title)
        put("description", p.description)
        put("isSystem", p.isSystem)
        put("iconType", p.iconType)
        val arr = JSONArray()
        p.videos.forEach { arr.put(videoToJson(it)) }
        put("videos", arr)
    }

    private fun jsonToPlaylist(obj: JSONObject): UserPlaylist {
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
