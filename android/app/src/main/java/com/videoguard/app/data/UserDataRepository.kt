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
            val candidateFormats = mutableListOf<AudioStreamFormat>()

            // 1. Fetch primary format (checks ANDROID muxed format 18, IPADOS itag 140/251, ANDROID_VR)
            val primary = fetchRealYouTubeAudioFormat(video.id)
            if (primary != null) {
                candidateFormats.add(primary)
            }

            // 2. Always guarantee ANDROID Format 18 (mp4 with AAC audio) is in candidate list as unthrottled fallback
            if (candidateFormats.none { it.isMuxed }) {
                fetchMuxedFormat18(video.id)?.let { candidateFormats.add(it) }
            }

            for (format in candidateFormats) {
                if (targetFile.exists()) targetFile.delete()
                val ok = downloadStreamToFile(context, video, format, targetFile, notifManager, channelId, notifId)
                if (ok && targetFile.exists() && targetFile.length() > 200_000L) {
                    isRealStreamDownloaded = true
                    break
                } else {
                    if (targetFile.exists()) targetFile.delete()
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

            kotlinx.coroutines.delay(1200)
            _currentDownload.value = null
        }
    }

    private suspend fun downloadStreamToFile(
        context: Context,
        video: VideoItem,
        format: AudioStreamFormat,
        targetFile: File,
        notifManager: android.app.NotificationManager?,
        channelId: String,
        notifId: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
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
                java.io.FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
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

            targetFile.exists() && targetFile.length() > 200_000L
        } catch (e: Exception) {
            android.util.Log.e("UserDataRepository", "downloadStreamToFile error for ${video.id}: ${e.message}")
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
                            if (bestFormat == null || mime.contains("mp4") || bitrate > bestFormat.bitrate) {
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

    private suspend fun fetchMuxedFormat18(videoId: String): AudioStreamFormat? = withContext(Dispatchers.IO) {
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

    private suspend fun fetchRealYouTubeAudioFormat(videoId: String): AudioStreamFormat? = withContext(Dispatchers.IO) {
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
