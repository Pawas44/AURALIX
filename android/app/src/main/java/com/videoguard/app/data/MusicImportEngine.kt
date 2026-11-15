package com.videoguard.app.data

import com.videoguard.app.model.UserPlaylist
import com.videoguard.app.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.regex.Pattern

data class ImportTrackMeta(
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSeconds: Int = 0,
    val spotifyId: String = ""
)

data class ImportProgress(
    val stage: String,
    val current: Int,
    val total: Int,
    val currentTrackName: String = "",
    val isComplete: Boolean = false,
    val error: String? = null
)

object MusicImportEngine {

    suspend fun importPlaylistFromUrl(
        inputUrlOrText: String,
        onProgress: (ImportProgress) -> Unit = {}
    ): Result<UserPlaylist> = withContext(Dispatchers.IO) {
        val trimmed = inputUrlOrText.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a valid Spotify link or song titles"))
        }

        try {
            onProgress(ImportProgress("Analyzing link...", 0, 0))

            var (playlistName, trackMetas) = when {
                trimmed.startsWith("{") && trimmed.contains("\"tracks\"") -> parseDomTracksJson(trimmed)
                trimmed.contains("spotify.com") -> parseSpotify(trimmed, onProgress)
                trimmed.contains("music.amazon.") || (trimmed.contains("amazon.") && trimmed.contains("music")) || trimmed.contains("amzn.to") -> parseAmazonMusic(trimmed, onProgress)
                trimmed.contains("music.apple.com") || trimmed.contains("apple.com") -> parseAppleMusic(trimmed, onProgress)
                else -> parsePlainTextList(trimmed)
            }

            if (trackMetas.isEmpty() && (trimmed.contains("\n") || trimmed.contains(" - ") || trimmed.contains("\t"))) {
                val plainFallback = parsePlainTextList(trimmed)
                if (plainFallback.second.isNotEmpty()) {
                    playlistName = plainFallback.first
                    trackMetas = plainFallback.second
                }
            }

            if (trackMetas.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No tracks found to import. Please check the link or song list."))
            }

            val matchedVideos = matchTracksWithYouTube(trackMetas, onProgress)
                .filter { it.id.length == 11 && !it.id.startsWith("search_") }
                .toMutableList()

            if (matchedVideos.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Could not match tracks on YouTube. Please check your internet connection."))
            }

            val finalPlaylist = UserPlaylist(
                id = UUID.randomUUID().toString(),
                title = playlistName.ifBlank { "Imported Playlist" },
                description = "Imported into AURALIX (${matchedVideos.size} tracks)",
                videos = matchedVideos,
                isSystem = false,
                iconType = "playlist",
                customCoverUrl = matchedVideos.firstOrNull()?.thumbnailUrl
            )

            // Auto-save into UserDataRepository & Enrich User Taste Profile
            UserDataRepository.savePlaylist(finalPlaylist)
            com.videoguard.app.recommendation.UserTasteProfile.get().enrichFromImportedTracks(matchedVideos)
            onProgress(ImportProgress("Complete!", trackMetas.size, trackMetas.size, isComplete = true))

            Result.success(finalPlaylist)
        } catch (e: Exception) {
            e.printStackTrace()
            onProgress(ImportProgress("Failed", 0, 0, error = e.localizedMessage))
            Result.failure(e)
        }
    }

    suspend fun importRemotePlaylist(
        summary: RemotePlaylistSummary,
        onProgress: (ImportProgress) -> Unit = {}
    ): Result<UserPlaylist> = withContext(Dispatchers.IO) {
        try {
            onProgress(ImportProgress("Fetching tracks from ${summary.sourceProvider}...", 0, summary.trackCount))
            val tracks = if (summary.sourceProvider == "Spotify") {
                SpotifyConnectEngine.fetchPlaylistTracks(summary.id) { prog ->
                    onProgress(prog)
                }
            } else {
                emptyList()
            }
            if (tracks.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No tracks found in playlist"))
            }
            val matchedVideos = matchTracksWithYouTube(tracks, onProgress)
                .filter { it.id.length == 11 && !it.id.startsWith("search_") }
                .toMutableList()

            if (matchedVideos.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Could not match tracks on YouTube. Please check your internet connection."))
            }
            val finalPlaylist = UserPlaylist(
                id = UUID.randomUUID().toString(),
                title = summary.title,
                description = "Imported from ${summary.sourceProvider} (${matchedVideos.size} tracks)",
                videos = matchedVideos,
                isSystem = false,
                iconType = "playlist",
                customCoverUrl = summary.coverUrl ?: matchedVideos.firstOrNull()?.thumbnailUrl
            )
            UserDataRepository.savePlaylist(finalPlaylist)
            com.videoguard.app.recommendation.UserTasteProfile.get().enrichFromImportedTracks(matchedVideos)
            onProgress(ImportProgress("Complete!", tracks.size, tracks.size, isComplete = true))
            Result.success(finalPlaylist)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private const val INNERTUBE_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    // Official YouTube Music Song Filter from ByTune (guarantees studio audio tracks, eliminates jukeboxes and fan recordings)
    private const val YTM_SONGS_FILTER = "EgWKAQIIAWoKEAkQChAFEAMQBA=="
    private const val YT_VIDEO_FILTER = "EgIQAQ=="

    private suspend fun searchInnerTubeDirect(
        query: String,
        useMusicApi: Boolean,
        params: String? = null
    ): List<VideoItem> = withContext(Dispatchers.IO) {
        val outList = mutableListOf<VideoItem>()
        if (query.isBlank()) return@withContext outList

        try {
            val endpoint = if (useMusicApi) "https://music.youtube.com/youtubei/v1/search?key=$INNERTUBE_KEY"
                           else "https://www.youtube.com/youtubei/v1/search?key=$INNERTUBE_KEY"
            val clientName = if (useMusicApi) "WEB_REMIX" else "WEB"
            val clientVer = if (useMusicApi) "1.20240101.01.00" else "2.20240101.01.00"
            val origin = if (useMusicApi) "https://music.youtube.com" else "https://www.youtube.com"

            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", clientName)
                        put("clientVersion", clientVer)
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("query", query)
                val filterParam = params ?: if (useMusicApi) YTM_SONGS_FILTER else YT_VIDEO_FILTER
                if (!filterParam.isNullOrBlank()) {
                    put("params", filterParam)
                }
            }

            val payloadBytes = payload.toString().toByteArray(Charsets.UTF_8)
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setFixedLengthStreamingMode(payloadBytes.size)
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Content-Length", payloadBytes.size.toString())
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                setRequestProperty("Origin", origin)
                setRequestProperty("Referer", "$origin/")
                setRequestProperty("X-YouTube-Client-Name", if (useMusicApi) "67" else "1")
                setRequestProperty("X-YouTube-Client-Version", clientVer)
            }

            conn.outputStream.use { os ->
                os.write(payloadBytes)
                os.flush()
            }

            if (conn.responseCode in 200..299) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                if (text.isNotBlank()) {
                    val root = JSONObject(text)
                    parseYouTubeSearchJson(root, outList)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        outList.distinctBy { it.id }
    }

    private suspend fun searchYouTubeFreeWeb(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val outList = mutableListOf<VideoItem>()
        if (query.isBlank()) return@withContext outList

        try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = URL("https://www.youtube.com/results?search_query=$encoded&sp=EgIQAQ%253D%253D")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                setRequestProperty("Cookie", "SOCS=CAESEwgDEgk0ODE3Nzk3MjQaAmVuIAEaBgiA_LyaBg; CONSENT=YES+")
            }

            if (conn.responseCode in 200..299) {
                val html = conn.inputStream.bufferedReader().use { it.readText() }
                parseYouTubeSearchHtml(html, outList, query)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Secondary fallback to free YouTubeSearchEngine if needed
        if (outList.isEmpty()) {
            try {
                val searchResults = YouTubeSearchEngine.searchVideos(query, isMusicOnly = true)
                outList.addAll(searchResults)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        outList.distinctBy { it.id }
    }

    private fun parseYouTubeSearchHtml(html: String, outList: MutableList<VideoItem>, query: String) {
        try {
            val markers = listOf("var ytInitialData = ", "ytInitialData = ", "window[\"ytInitialData\"] = ")
            var jsonStr: String? = null
            for (marker in markers) {
                val startIdx = html.indexOf(marker)
                if (startIdx != -1) {
                    val jsonStart = startIdx + marker.length
                    var endIdx = html.indexOf(";</script>", jsonStart)
                    if (endIdx == -1) {
                        endIdx = html.indexOf("</script>", jsonStart)
                    }
                    if (endIdx != -1) {
                        jsonStr = html.substring(jsonStart, endIdx).trim().trimEnd(';')
                        break
                    }
                }
            }

            if (!jsonStr.isNullOrBlank()) {
                val root = JSONObject(jsonStr)
                parseYouTubeSearchJson(root, outList)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fast regex fallback if JSON structure changed
        if (outList.isEmpty()) {
            val vidRegex = Regex("\"videoId\":\"([a-zA-Z0-9_-]{11})\"")
            val matches = vidRegex.findAll(html).map { it.groupValues[1] }.distinct().take(10).toList()
            for (vid in matches) {
                outList.add(
                    VideoItem(
                        id = vid,
                        title = query,
                        channelName = "YouTube Music",
                        channelAvatarUrl = "https://img.youtube.com/vi/$vid/hqdefault.jpg",
                        thumbnailUrl = "https://img.youtube.com/vi/$vid/hqdefault.jpg",
                        views = "Popular",
                        uploadTime = "Recently",
                        duration = "3:30",
                        description = query,
                        isMusicTrack = true,
                        artist = "YouTube Music",
                        album = "Single",
                        category = "Music"
                    )
                )
            }
        }
    }

    private fun parseYouTubeSearchJson(obj: Any, outList: MutableList<VideoItem>) {
        when (obj) {
            is JSONObject -> {
                if (obj.has("musicCardShelfRenderer")) {
                    parseMusicCardShelf(obj.optJSONObject("musicCardShelfRenderer"))?.let { outList.add(it) }
                }
                if (obj.has("musicResponsiveListItemRenderer")) {
                    parseMusicResponsiveItem(obj.optJSONObject("musicResponsiveListItemRenderer"))?.let { outList.add(it) }
                }
                if (obj.has("videoRenderer")) {
                    parseVideoRendererItem(obj.optJSONObject("videoRenderer"))?.let { outList.add(it) }
                }
                if (obj.has("compactVideoRenderer")) {
                    parseVideoRendererItem(obj.optJSONObject("compactVideoRenderer"))?.let { outList.add(it) }
                }
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    parseYouTubeSearchJson(obj.get(k), outList)
                }
            }
            is JSONArray -> {
                for (i in 0 until obj.length()) {
                    val elem = obj.opt(i)
                    if (elem != null) {
                        parseYouTubeSearchJson(elem, outList)
                    }
                }
            }
        }
    }

    private fun parseMusicCardShelf(shelf: JSONObject?): VideoItem? {
        if (shelf == null) return null
        val titleObj = shelf.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)
        val title = titleObj?.optString("text") ?: ""
        var vid = titleObj?.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId") ?: ""
        if (vid.isBlank()) {
            vid = shelf.optJSONObject("onTap")?.optJSONObject("watchEndpoint")?.optString("videoId") ?: ""
        }
        if (vid.isBlank() || title.isBlank()) return null

        val subRuns = shelf.optJSONObject("subtitle")?.optJSONArray("runs")
        var artist = "YouTube Music"
        var duration = "3:30"
        if (subRuns != null) {
            for (i in 0 until subRuns.length()) {
                val r = subRuns.optJSONObject(i)?.optString("text") ?: ""
                if (r != "Video" && r != " • " && r != "Song" && !r.contains("views") && !r.contains(":")) {
                    if (artist == "YouTube Music" && r.isNotBlank()) artist = r
                }
                if (r.contains(":")) {
                    duration = r
                }
            }
        }

        return VideoItem(
            id = vid,
            title = title,
            channelName = artist,
            channelAvatarUrl = "https://img.youtube.com/vi/$vid/hqdefault.jpg",
            thumbnailUrl = "https://img.youtube.com/vi/$vid/hqdefault.jpg",
            views = "Popular",
            uploadTime = "Recently",
            duration = duration,
            description = title,
            isMusicTrack = true,
            artist = artist,
            album = "Single",
            category = "Music"
        )
    }

    private fun parseMusicResponsiveItem(m: JSONObject?): VideoItem? {
        if (m == null) return null
        var vid = m.optJSONObject("playlistItemData")?.optString("videoId") ?: ""
        if (vid.isBlank()) {
            val overlay = m.optJSONObject("overlay")?.optJSONObject("musicItemThumbnailOverlayRenderer")
            vid = overlay?.optJSONObject("content")?.optJSONObject("musicPlayButtonRenderer")
                ?.optJSONObject("playNavigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId") ?: ""
        }
        val cols = m.optJSONArray("flexColumns")
        var title = ""
        var artist = "Artist"
        var album = "Single"
        var duration = "3:30"

        if (cols != null && cols.length() > 0) {
            val runs0 = cols.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")?.optJSONArray("runs")
            if (runs0 != null && runs0.length() > 0) {
                title = runs0.optJSONObject(0)?.optString("text") ?: ""
                if (vid.isBlank()) {
                    vid = runs0.optJSONObject(0)?.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("watchEndpoint")?.optString("videoId") ?: ""
                }
            }

            // Extract all text runs across remaining columns (artists, album, duration, etc.)
            val allRuns = mutableListOf<String>()
            for (colIdx in 1 until cols.length()) {
                val rArr = cols.optJSONObject(colIdx)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")?.optJSONArray("runs") ?: continue
                for (rIdx in 0 until rArr.length()) {
                    val txt = rArr.optJSONObject(rIdx)?.optString("text")?.trim() ?: ""
                    if (txt.isNotBlank()) {
                        allRuns.add(txt)
                    }
                }
            }

            // Split runs into bullet-separated (' • ') sections
            val sections = mutableListOf<MutableList<String>>()
            var curSection = mutableListOf<String>()
            for (r in allRuns) {
                if (r == "•" || r.contains("•")) {
                    if (curSection.isNotEmpty()) {
                        sections.add(curSection)
                        curSection = mutableListOf()
                    }
                } else {
                    curSection.add(r)
                }
            }
            if (curSection.isNotEmpty()) {
                sections.add(curSection)
            }

            // Look for duration across all tokens (format mm:ss or hh:mm:ss)
            val durationRegex = Regex("""^\d{1,2}:\d{2}(?::\d{2})?$""")
            for (sec in sections) {
                for (tok in sec) {
                    if (durationRegex.matches(tok.trim())) {
                        duration = tok.trim()
                        break
                    }
                }
            }

            // Identify artist and album sections
            val categoryTokens = setOf("song", "single", "video", "album", "playlist", "episode", "artist", "track", "audio")
            for (sec in sections) {
                val firstTok = sec.firstOrNull()?.lowercase()?.trim() ?: ""
                // Skip standalone category headers like ["Song"] or ["Single"]
                if (sec.size == 1 && categoryTokens.contains(firstTok)) continue
                val joined = sec.filter { it != "&" && it != "," }.joinToString(" ").trim()
                // Skip sections containing views / plays / duration
                if (durationRegex.matches(joined) || joined.contains("views", ignoreCase = true) || joined.contains("plays", ignoreCase = true)) {
                    continue
                }
                if (artist == "Artist") {
                    artist = sec.joinToString("").trim()
                } else if (album == "Single") {
                    album = sec.joinToString("").trim()
                }
            }
        }

        if (vid.isBlank() || title.isBlank() || vid.length != 11) return null

        var thumb = ""
        val thumbs = m.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        if (thumbs != null && thumbs.length() > 0) {
            val lastUrl = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") ?: ""
            if (lastUrl.isNotBlank()) {
                thumb = lastUrl.replace(Regex("""=w\d+-h\d+"""), "=w544-h544")
            }
        }
        if (thumb.isBlank()) {
            thumb = "https://img.youtube.com/vi/$vid/hqdefault.jpg"
        }

        return VideoItem(
            id = vid,
            title = title,
            channelName = artist,
            channelAvatarUrl = thumb,
            thumbnailUrl = thumb,
            views = "Popular",
            uploadTime = "Recently",
            duration = duration,
            description = title,
            isMusicTrack = true,
            artist = artist,
            album = album,
            category = "Music"
        )
    }

    private fun parseVideoRendererItem(vr: JSONObject?): VideoItem? {
        if (vr == null) return null
        val vid = vr.optString("videoId").takeIf { it.isNotBlank() }
            ?: vr.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")
            ?: return null

        val title = vr.optJSONObject("title")?.let { t ->
            val runs = t.optJSONArray("runs")
            if (runs != null && runs.length() > 0) {
                val sb = StringBuilder()
                for (i in 0 until runs.length()) {
                    sb.append(runs.optJSONObject(i)?.optString("text") ?: "")
                }
                sb.toString()
            } else {
                t.optString("simpleText")
            }
        }?.takeIf { it.isNotBlank() } ?: return null

        val channel = vr.optJSONObject("ownerText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            ?: vr.optJSONObject("shortBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            ?: "YouTube Creator"

        val duration = vr.optJSONObject("lengthText")?.optString("simpleText") ?: "3:45"
        val thumb = "https://img.youtube.com/vi/$vid/hqdefault.jpg"

        return VideoItem(
            id = vid,
            title = title,
            channelName = channel,
            channelAvatarUrl = thumb,
            thumbnailUrl = thumb,
            views = "Popular",
            uploadTime = "Recently",
            duration = duration,
            description = title,
            isMusicTrack = true,
            artist = channel,
            album = "Single",
            category = "Music"
        )
    }

    private suspend fun resolveTrackOnYouTube(meta: ImportTrackMeta): VideoItem? {
        try {
            val queries = searchQueries(meta)
            var matched: VideoItem? = null

            // 1. YouTube Music Official Songs Catalog (ByTune method - exact studio audio tracks)
            for (q in queries) {
                val results = searchInnerTubeDirect(q, useMusicApi = true, params = YTM_SONGS_FILTER)
                matched = bestMatch(meta, results)
                if (matched != null) break
            }

            // 2. Regular YouTube Video Search with official audio filter
            if (matched == null) {
                for (q in queries) {
                    val ytResults = searchInnerTubeDirect("$q official audio", useMusicApi = false, params = YT_VIDEO_FILTER)
                    matched = bestMatch(meta, ytResults)
                    if (matched != null) break
                }
            }

            // 3. YouTube Music Catalog without filter (in case songs filter hid a rare regional track)
            if (matched == null) {
                for (q in queries) {
                    val fallbackYtm = searchInnerTubeDirect(q, useMusicApi = true, params = null)
                    matched = bestMatch(meta, fallbackYtm)
                    if (matched != null) break
                }
            }

            // 4. Free YouTube web scraping fallback (gets real YouTube video for the song)
            if (matched == null) {
                val primaryQ = queries.firstOrNull() ?: "${meta.title} ${meta.artist}"
                val webResults = searchYouTubeFreeWeb(primaryQ)
                matched = bestMatch(meta, webResults) ?: webResults.firstOrNull()
            }

            // 5. YouTubeSearchEngine fallback
            if (matched == null) {
                val primaryQ = queries.firstOrNull() ?: "${meta.title} ${meta.artist}"
                val engineResults = YouTubeSearchEngine.searchVideos(primaryQ, isMusicOnly = true)
                matched = bestMatch(meta, engineResults) ?: engineResults.firstOrNull()
            }

            if (matched != null && matched.id.length == 11) {
                val realThumb = if (matched.thumbnailUrl.isNotBlank() && !matched.thumbnailUrl.contains("unsplash")) {
                    matched.thumbnailUrl
                } else {
                    "https://img.youtube.com/vi/${matched.id}/hqdefault.jpg"
                }
                return matched.copy(
                    title = meta.title.ifBlank { matched.title },
                    artist = meta.artist.ifBlank { matched.artist },
                    thumbnailUrl = realThumb,
                    channelAvatarUrl = realThumb
                )
            }

            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private suspend fun matchTracksWithYouTube(
        trackMetas: List<ImportTrackMeta>,
        onProgress: (ImportProgress) -> Unit
    ): List<VideoItem> = withContext(Dispatchers.IO) {
        val total = trackMetas.size
        onProgress(ImportProgress("Matching tracks with YouTube Music...", 0, total))

        val matchedVideos = mutableListOf<VideoItem>()
        val batchSize = 3 // controlled parallel batch

        for (i in trackMetas.indices step batchSize) {
            val batch = trackMetas.subList(i, minOf(i + batchSize, total))
            val resolvedBatch = coroutineScope {
                batch.map { meta ->
                    async {
                        resolveTrackOnYouTube(meta)
                    }
                }.awaitAll()
            }

            for (idx in resolvedBatch.indices) {
                val resolved = resolvedBatch[idx]
                val meta = batch[idx]
                if (resolved != null) {
                    val cleanTitle = if (resolved.title.contains("YouTube Video", ignoreCase = true) || resolved.title.contains(resolved.id) || resolved.title.isBlank()) {
                        meta.title
                    } else {
                        resolved.title
                    }
                    val cleanArtist = if (resolved.artist.isBlank() || resolved.artist == "Artist" || resolved.artist == "YouTube Channel") {
                        meta.artist.ifBlank { resolved.channelName }
                    } else {
                        resolved.artist
                    }
                    val finalThumb = if (resolved.thumbnailUrl.isNotBlank() && !resolved.thumbnailUrl.contains("unsplash")) {
                        resolved.thumbnailUrl
                    } else {
                        "https://img.youtube.com/vi/${resolved.id}/hqdefault.jpg"
                    }
                    matchedVideos.add(
                        resolved.copy(
                            title = cleanTitle,
                            artist = cleanArtist,
                            album = if (resolved.album.isBlank() || resolved.album == "Single") meta.album.ifBlank { resolved.album } else resolved.album,
                            thumbnailUrl = finalThumb,
                            channelAvatarUrl = finalThumb
                        )
                    )
                }
                val currentDone = minOf(i + idx + 1, total)
                onProgress(
                    ImportProgress(
                        stage = "Matching tracks...",
                        current = currentDone,
                        total = total,
                        currentTrackName = "${meta.title} • ${meta.artist}"
                    )
                )
            }
        }
        matchedVideos
    }

    private fun parseSpotify(url: String, onProgress: (ImportProgress) -> Unit): Pair<String, List<ImportTrackMeta>> {
        val cleanUrl = url.substringBefore("?").trimEnd('/')
        val isTrack = url.contains("/track/")
        val isAlbum = url.contains("/album/")
        val isPlaylist = url.contains("/playlist/") || (!isTrack && !isAlbum)

        val id = Regex("(?:playlist|album|track)/([a-zA-Z0-9]+)").find(url)?.groupValues?.get(1)
            ?: cleanUrl.substringAfterLast("/")

        if (isTrack) {
            val oembedUrl = "https://open.spotify.com/oembed?url=" + java.net.URLEncoder.encode("https://open.spotify.com/track/$id", "UTF-8")
            val jsonStr = httpGet(oembedUrl)
            if (jsonStr.isNotBlank() && jsonStr.startsWith("{")) {
                val json = JSONObject(jsonStr)
                val title = json.optString("title", "Spotify Track")
                val author = json.optString("author_name", "")
                return Pair(title, listOf(ImportTrackMeta(title, author)))
            }
        }

        if (isPlaylist) {
            if (SpotifyConnectEngine.isConnected.value || !SpotifyConnectEngine.getSpDcCookie().isNullOrBlank()) {
                try {
                    val (pTitle, connectedTracks) = kotlinx.coroutines.runBlocking {
                        val title = SpotifyConnectEngine.fetchPlaylistTitle(id)
                        val tracks = SpotifyConnectEngine.fetchPlaylistTracks(id) { prog ->
                            onProgress(prog)
                        }
                        Pair(title, tracks)
                    }
                    if (connectedTracks.isNotEmpty()) {
                        return Pair(pTitle, connectedTracks)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        val embedUrl = if (isAlbum) {
            "https://open.spotify.com/embed/album/$id"
        } else {
            "https://open.spotify.com/embed/playlist/$id"
        }

        val html = httpGet(embedUrl)

        var playlistTitle = "Spotify Playlist"
        val tracks = mutableListOf<ImportTrackMeta>()

        // 1. __NEXT_DATA__ JSON blob
        val nextDataPattern = Pattern.compile("<script id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>", Pattern.DOTALL)
        val matcher = nextDataPattern.matcher(html)
        if (matcher.find()) {
            val jsonStr = matcher.group(1) ?: ""
            try {
                val data = JSONObject(jsonStr)
                val entity = data.optJSONObject("props")
                    ?.optJSONObject("pageProps")
                    ?.optJSONObject("state")
                    ?.optJSONObject("data")
                    ?.optJSONObject("entity")

                if (entity != null) {
                    playlistTitle = entity.optString("name", "Spotify Playlist")
                    val trackList = entity.optJSONArray("trackList")
                    if (trackList != null) {
                        for (i in 0 until trackList.length()) {
                            val t = trackList.optJSONObject(i) ?: continue
                            val title = t.optString("title", "")
                            val subtitle = t.optString("subtitle", "")
                            val durMs = t.optLong("duration", 0L)
                            val durSec = if (durMs > 0) (durMs / 1000).toInt() else 0
                            if (title.isNotBlank()) {
                                tracks.add(ImportTrackMeta(title = title, artist = subtitle, durationSeconds = durSec))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Fallback to oEmbed for playlist title
        if (tracks.isEmpty()) {
            try {
                val oembedUrl = "https://open.spotify.com/oembed?url=" + java.net.URLEncoder.encode("https://open.spotify.com/playlist/$id", "UTF-8")
                val oembedJson = httpGet(oembedUrl)
                if (oembedJson.isNotBlank() && oembedJson.startsWith("{")) {
                    val obj = JSONObject(oembedJson)
                    val t = obj.optString("title")
                    if (t.isNotBlank()) playlistTitle = t
                }
            } catch (e: Exception) {}
        }

        // 3. Fallback regex match for title/subtitle in HTML
        if (tracks.isEmpty()) {
            val trackRegex = Pattern.compile("\"title\":\"([^\"]+)\"[^}]*?\"subtitle\":\"([^\"]+)\"")
            val trMatcher = trackRegex.matcher(html)
            while (trMatcher.find()) {
                val t = trMatcher.group(1) ?: ""
                val s = trMatcher.group(2) ?: ""
                if (t.isNotBlank() && !t.contains("Spotify")) {
                    tracks.add(ImportTrackMeta(t, s))
                }
            }
        }

        return Pair(playlistTitle, tracks)
    }

    private suspend fun parseAmazonMusic(rawUrl: String, onProgress: (ImportProgress) -> Unit): Pair<String, List<ImportTrackMeta>> {
        val url = resolveRedirectUrl(rawUrl)
        val html = httpGet(url)
        var title = "Amazon Music Playlist"

        val titleMatcher = Pattern.compile("<title>([^<]+)</title>", Pattern.CASE_INSENSITIVE).matcher(html)
        if (titleMatcher.find()) {
            title = titleMatcher.group(1)
                ?.replace("on Amazon Music", "", ignoreCase = true)
                ?.replace("Amazon.com: ", "", ignoreCase = true)
                ?.replace("Amazon Music: ", "", ignoreCase = true)
                ?.replace("Amazon Music", "", ignoreCase = true)
                ?.trim() ?: title
        }

        val tracks = mutableListOf<ImportTrackMeta>()

        // 1. Check for JSON-LD schema scripts (<script type="application/ld+json">)
        val jsonLdRegex = Pattern.compile("<script[^>]*type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
        val jsonLdMatcher = jsonLdRegex.matcher(html)
        while (jsonLdMatcher.find()) {
            val jsonContent = jsonLdMatcher.group(1)?.trim() ?: continue
            try {
                if (jsonContent.startsWith("{")) {
                    val root = JSONObject(jsonContent)
                    val playlistName = root.optString("name", "")
                    if (playlistName.isNotBlank() && (title == "Amazon Music Playlist" || title.isBlank())) {
                        title = playlistName
                    }
                    val trackArr = root.optJSONArray("track")
                    if (trackArr != null) {
                        for (i in 0 until trackArr.length()) {
                            val item = trackArr.optJSONObject(i) ?: continue
                            val tName = item.optString("name", "")
                            val artistObj = item.optJSONObject("byArtist")
                            val aName = artistObj?.optString("name", "") ?: item.optString("artist", "")
                            if (tName.isNotBlank()) {
                                tracks.add(ImportTrackMeta(tName, aName))
                            }
                        }
                    }
                    val itemArr = root.optJSONArray("itemListElement")
                    if (itemArr != null) {
                        for (i in 0 until itemArr.length()) {
                            val elem = itemArr.optJSONObject(i) ?: continue
                            val item = elem.optJSONObject("item") ?: elem
                            val tName = item.optString("name", "")
                            val artistObj = item.optJSONObject("byArtist")
                            val aName = artistObj?.optString("name", "") ?: item.optString("artist", "")
                            if (tName.isNotBlank()) {
                                tracks.add(ImportTrackMeta(tName, aName))
                            }
                        }
                    }
                }
            } catch (e: Exception) {}
        }

        // 2. Parse Amazon Web Client embedded track JSON blobs:
        if (tracks.isEmpty()) {
            val ptRegex = Pattern.compile("\"primaryText\"\\s*:\\s*\"([^\"]+)\"[^}]*?\"secondaryText(?:1)?\"\\s*:\\s*\"([^\"]+)\"")
            val ptMatcher = ptRegex.matcher(html)
            while (ptMatcher.find()) {
                val t = ptMatcher.group(1)?.trim() ?: ""
                val a = ptMatcher.group(2)?.trim() ?: ""
                if (t.isNotBlank() && !t.equals("Amazon Music", ignoreCase = true) && !t.equals("Library", ignoreCase = true)) {
                    tracks.add(ImportTrackMeta(t, a))
                }
            }
        }

        // 3. Alternative Amazon Music track/artist JSON fields ("title" & "artistName")
        if (tracks.isEmpty()) {
            val titleArtistRegex = Pattern.compile("\"title\"\\s*:\\s*\"([^\"]+)\"[^}]*?\"artistName\"\\s*:\\s*\"([^\"]+)\"")
            val taMatcher = titleArtistRegex.matcher(html)
            while (taMatcher.find()) {
                val t = taMatcher.group(1)?.trim() ?: ""
                val a = taMatcher.group(2)?.trim() ?: ""
                if (t.isNotBlank() && !t.contains("Amazon")) {
                    tracks.add(ImportTrackMeta(t, a))
                }
            }
        }

        // 4. OpenGraph music:song tags fallback
        if (tracks.isEmpty()) {
            val rowRegex = Pattern.compile("<meta property=\"music:song\" content=\"([^\"]+)\"")
            val rMatcher = rowRegex.matcher(html)
            while (rMatcher.find()) {
                val trackUrl = rMatcher.group(1) ?: continue
                val songName = trackUrl.substringAfterLast("/").replace("-", " ")
                if (songName.isNotBlank()) {
                    tracks.add(ImportTrackMeta(songName, ""))
                }
            }
        }

        // Deduplicate tracks while preserving order
        val uniqueTracks = mutableListOf<ImportTrackMeta>()
        val seen = mutableSetOf<String>()
        for (tr in tracks) {
            val key = "${tr.title.lowercase().trim()}_${tr.artist.lowercase().trim()}"
            if (!seen.contains(key)) {
                seen.add(key)
                uniqueTracks.add(tr)
            }
        }

        val filteredTracks = uniqueTracks.filter { tr ->
            val lower = tr.title.lowercase()
            !lower.contains("how to make") &&
            !lower.contains("amazon music help") &&
            !lower.contains("customer service") &&
            !lower.contains("sign in") &&
            !lower.contains("log in") &&
            lower != "library" &&
            lower != "browse" &&
            lower != "home"
        }

        var resultTracks = filteredTracks
        if (resultTracks.isEmpty()) {
            val cleanTitle = title
                .replace(Regex("(?i) on Amazon Music.*"), "")
                .replace(Regex("(?i) - music\\.amazon\\..*"), "")
                .replace(Regex("(?i) \\| Amazon\\..*"), "")
                .replace(Regex("(?i)\\bPlaylist\\b"), "")
                .trim()
            if (cleanTitle.isNotBlank() && cleanTitle.length > 2 &&
                !cleanTitle.equals("Amazon Music", ignoreCase = true) &&
                !cleanTitle.equals("Amazon Music Playlist", ignoreCase = true)) {
                onProgress(ImportProgress("Searching tracks for $cleanTitle...", 0, 0))
                try {
                    val searchItems = YouTubeSearchEngine.searchVideos("$cleanTitle songs", isMusicOnly = true)
                    val candidateList = mutableListOf<ImportTrackMeta>()
                    for (item in searchItems.take(30)) {
                        candidateList.add(ImportTrackMeta(item.title, item.artist.ifBlank { item.channelName }))
                    }
                    if (candidateList.isNotEmpty()) {
                        resultTracks = candidateList
                    }
                } catch (e: Exception) {}
            }
        }

        if (resultTracks.isEmpty()) {
            throw IllegalStateException("Could not read songs from this Amazon link. Please copy & paste your playlist song titles directly into the box, or use the In-App Web Importer.")
        }

        return Pair(title, resultTracks)
    }

    private fun resolveRedirectUrl(urlStr: String): String {
        return try {
            var curUrl = urlStr
            var hops = 0
            while (hops < 5) {
                val url = URL(curUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "HEAD"
                    connectTimeout = 6000
                    readTimeout = 6000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location")
                    if (!loc.isNullOrBlank()) {
                        curUrl = if (loc.startsWith("http")) loc else URL(url, loc).toString()
                        hops++
                        continue
                    }
                }
                break
            }
            curUrl
        } catch (e: Exception) {
            urlStr
        }
    }

    private fun parseDomTracksJson(jsonStr: String): Pair<String, List<ImportTrackMeta>> {
        val obj = JSONObject(jsonStr)
        val title = obj.optString("title").ifBlank { "Imported Playlist" }
        val tracksArr = obj.optJSONArray("tracks") ?: JSONArray()
        val list = mutableListOf<ImportTrackMeta>()
        for (i in 0 until tracksArr.length()) {
            val line = tracksArr.optString(i, "").trim()
            if (line.isNotBlank()) {
                val parts = line.split(" - ", " – ", " by ", limit = 2)
                val t = parts[0].trim()
                val a = if (parts.size > 1) parts[1].trim() else ""
                val lower = t.lowercase()
                if (!lower.contains("how to make") && !lower.contains("amazon music help") && lower != "library" && lower != "browse") {
                    list.add(ImportTrackMeta(t, a))
                }
            }
        }
        if (list.isEmpty()) {
            throw IllegalStateException("No tracks detected on this page. Please open a playlist and tap Sync.")
        }
        return Pair(title, list)
    }

    private fun parseAppleMusic(url: String, onProgress: (ImportProgress) -> Unit): Pair<String, List<ImportTrackMeta>> {
        val html = try { httpGet(url) } catch (e: Exception) { "" }
        var title = "Apple Music Playlist"

        val titleMatcher = Pattern.compile("<title>([^<]+)</title>", Pattern.CASE_INSENSITIVE).matcher(html)
        if (titleMatcher.find()) {
            title = titleMatcher.group(1)
                ?.replace("on Apple Music", "", ignoreCase = true)
                ?.replace("Apple Music", "", ignoreCase = true)
                ?.replace(" - Playlist - ", " - ")
                ?.trim() ?: title
        }

        val tracks = mutableListOf<ImportTrackMeta>()

        // 1. JSON-LD schema
        val jsonLdRegex = Pattern.compile("<script[^>]*type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
        val jsonLdMatcher = jsonLdRegex.matcher(html)
        while (jsonLdMatcher.find()) {
            val jsonContent = jsonLdMatcher.group(1)?.trim() ?: continue
            try {
                if (jsonContent.startsWith("{")) {
                    val root = JSONObject(jsonContent)
                    val pName = root.optString("name", "")
                    if (pName.isNotBlank() && (title == "Apple Music Playlist" || title.isBlank())) {
                        title = pName
                    }
                    val trackArr = root.optJSONArray("track") ?: root.optJSONArray("tracks")
                    if (trackArr != null) {
                        for (i in 0 until trackArr.length()) {
                            val item = trackArr.optJSONObject(i) ?: continue
                            val tName = item.optString("name", "")
                            val artistObj = item.optJSONObject("byArtist")
                            val aName = artistObj?.optString("name", "") ?: item.optString("artist", "")
                            if (tName.isNotBlank()) {
                                tracks.add(ImportTrackMeta(tName, aName))
                            }
                        }
                    }
                    val itemArr = root.optJSONArray("itemListElement")
                    if (itemArr != null) {
                        for (i in 0 until itemArr.length()) {
                            val item = itemArr.optJSONObject(i) ?: continue
                            val itemObj = item.optJSONObject("item") ?: item
                            val tName = itemObj.optString("name", "")
                            val artistObj = itemObj.optJSONObject("byArtist")
                            val aName = artistObj?.optString("name", "") ?: itemObj.optString("artist", "")
                            if (tName.isNotBlank()) {
                                tracks.add(ImportTrackMeta(tName, aName))
                            }
                        }
                    }
                }
            } catch (e: Exception) {}
        }

        // 2. Fallback regex for meta / JSON tags
        if (tracks.isEmpty()) {
            val trackRegex = Pattern.compile("\"name\":\"([^\"]+)\"[^}]*?\"byArtist\":\\{\"@type\":\"MusicGroup\",\"name\":\"([^\"]+)\"\\}")
            val trMatcher = trackRegex.matcher(html)
            while (trMatcher.find()) {
                val t = trMatcher.group(1)?.trim() ?: ""
                val a = trMatcher.group(2)?.trim() ?: ""
                if (t.isNotBlank() && !t.contains("Apple Music")) {
                    tracks.add(ImportTrackMeta(t, a))
                }
            }
        }

        // Deduplicate tracks
        val uniqueTracks = mutableListOf<ImportTrackMeta>()
        val seen = mutableSetOf<String>()
        for (tr in tracks) {
            val key = "${tr.title.lowercase().trim()}_${tr.artist.lowercase().trim()}"
            if (!seen.contains(key)) {
                seen.add(key)
                uniqueTracks.add(tr)
            }
        }

        return Pair(title, uniqueTracks)
    }

    private fun parsePlainTextList(text: String): Pair<String, List<ImportTrackMeta>> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val tracks = mutableListOf<ImportTrackMeta>()

        for (line in lines) {
            // Remove leading track numbers e.g. "1.", "01 -", "(1)"
            var cleaned = line.replace(Regex("^\\s*\\(?\\d+[\\.\\)\\-]\\s*"), "").trim()
            // Remove trailing timestamp/duration like "(03:45)" or "3:45"
            cleaned = cleaned.replace(Regex("\\s*\\(?\\d{1,2}:\\d{2}\\)?$"), "").trim()

            // Skip table header lines
            if (cleaned.equals("Title", ignoreCase = true) ||
                cleaned.equals("Track", ignoreCase = true) ||
                cleaned.equals("Song", ignoreCase = true) ||
                cleaned.startsWith("Track Title", ignoreCase = true)) {
                continue
            }

            val parts = when {
                cleaned.contains("\t") -> cleaned.split("\t")
                cleaned.contains(" - ") -> cleaned.split(" - ")
                cleaned.contains(" – ") -> cleaned.split(" – ")
                cleaned.contains(" — ") -> cleaned.split(" — ")
                cleaned.contains(" by ") -> cleaned.split(" by ")
                cleaned.contains(" | ") -> cleaned.split(" | ")
                else -> listOf(cleaned)
            }

            if (parts.size >= 2) {
                val t = parts[0].trim().removeSurrounding("\"")
                val a = parts[1].trim().removeSurrounding("\"")
                if (t.isNotBlank()) {
                    tracks.add(ImportTrackMeta(t, a))
                }
            } else if (cleaned.isNotBlank()) {
                tracks.add(ImportTrackMeta(cleaned.removeSurrounding("\""), ""))
            }
        }

        return Pair("Imported Custom Playlist", tracks)
    }

    private fun httpGet(urlStr: String): String {
        return try {
            var curUrl = urlStr
            var hops = 0
            while (hops < 5) {
                val url = URL(curUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                    setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location")
                    if (!loc.isNullOrBlank()) {
                        curUrl = if (loc.startsWith("http")) loc else URL(url, loc).toString()
                        hops++
                        continue
                    }
                }
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream ?: return ""
                return stream.bufferedReader().use { it.readText() }
            }
            ""
        } catch (e: Exception) {
            ""
        }
    }

    private val VERSION_REGEX = Regex("""(?i)\b(remix|live|acoustic|instrumental|unplugged|karaoke|slowed|reverb|sped|nightcore|lo-?fi|cover|reprise|mashup)\b""")

    private fun extractVersionMarkers(title: String): Set<String> {
        return VERSION_REGEX.findAll(title.lowercase()).map { it.value.replace("-", "") }.toSet()
    }

    private fun cleanTitleWords(title: String): String {
        return title.replace(Regex("""[([{【「『][^)\]}】」』]*[)\]}】」』]"""), " ")
            .replace(Regex("""(?i)\b(official\s+video|official\s+audio|music\s+video|lyric\s+video|lyrics|audio|video|hd|4k|ft\.?|feat\.?)\b"""), " ")
            .replace(Regex("""[^a-zA-Z0-9\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .lowercase()
    }

    private fun diceSimilarity(a: String, b: String): Float {
        val x = a.replace(" ", "")
        val y = b.replace(" ", "")
        if (x.isEmpty() || y.isEmpty()) return 0f
        if (x == y) return 1f
        if (x.contains(y) || y.contains(x)) return 0.85f
        if (x.length < 2 || y.length < 2) return 0f
        val pairs = mutableMapOf<String, Int>()
        for (i in 0 until x.length - 1) {
            val pair = x.substring(i, i + 2)
            pairs[pair] = (pairs[pair] ?: 0) + 1
        }
        var shared = 0
        for (i in 0 until y.length - 1) {
            val pair = y.substring(i, i + 2)
            val count = pairs[pair] ?: 0
            if (count > 0) {
                shared++
                pairs[pair] = count - 1
            }
        }
        return (2f * shared) / (x.length - 1 + y.length - 1)
    }

    private fun searchQueries(meta: ImportTrackMeta): List<String> {
        val cleanTitle = meta.title
            .replace(Regex("""[([{【「『][^)\]}】」』]*[)\]}】」』]"""), " ")
            .replace(Regex("""\s[-–—]\s.*$"""), " ")
            .trim()
        val baseTitle = if (cleanTitle.isNotBlank()) cleanTitle else meta.title.trim()
        val version = extractVersionMarkers(meta.title).sorted().joinToString(" ")
        val name = if (version.isNotBlank()) "$baseTitle $version" else baseTitle

        val firstArtist = meta.artist
            .split(Regex("""\s*(?:,|&|\+|\/|;|、|\sx\s|\sand\s|\s(?:feat|ft)\.?\s|\sfeaturing\s|\swith\s|\svs\.?\s)\s*""", RegexOption.IGNORE_CASE))
            .firstOrNull()?.trim() ?: ""

        val queries = mutableListOf<String>()
        if (meta.artist.isNotBlank() && !name.contains(meta.artist, ignoreCase = true)) {
            queries.add("$name ${meta.artist}")
        }
        if (firstArtist.isNotBlank() && firstArtist != meta.artist && !name.contains(firstArtist, ignoreCase = true)) {
            queries.add("$name $firstArtist")
        }
        queries.add(name)
        return queries.distinct()
    }

    private fun artistNames(artist: String): List<String> {
        return artist
            .split(Regex("""\s*(?:,|&|\+|\/|;|、|\sx\s|\sand\s|\s(?:feat|ft)\.?\s|\sfeaturing\s|\swith\s|\svs\.?\s)\s*""", RegexOption.IGNORE_CASE))
            .map { cleanTitleWords(it).replace(" ", "") }
            .filter { it.length >= 2 }
    }

    /**
     * ByTune's exact match scoring logic (ported from electron/track-match.ts)
     * Returns a float score (higher is surer) or null if candidate is rejected as a different recording.
     */
    private fun matchScore(target: ImportTrackMeta, candidate: VideoItem): Float? {
        // 1. Version markers must agree (remix with remix, live with live)
        if (extractVersionMarkers(target.title) != extractVersionMarkers(candidate.title)) return null

        // 2. Title similarity check
        val targetWords = cleanTitleWords(target.title)
        val candWords = cleanTitleWords(candidate.title)
        val sim = diceSimilarity(targetWords, candWords)
        val titleMatches = sim >= 0.70f || targetWords == candWords ||
            (targetWords.length >= 4 && candWords.contains(targetWords)) ||
            (candWords.length >= 4 && targetWords.contains(candWords))
        if (!titleMatches) return null

        // 3. Duration check if duration is known (gap <= 25 sec)
        val candSec = parseDurationSec(candidate.duration)
        val gap = if (target.durationSeconds > 0 && candSec > 0) kotlin.math.abs(target.durationSeconds - candSec) else null
        if (gap != null && gap > 25) return null

        // 4. Reject long mix/jukebox
        if (candSec > 600 && target.durationSeconds < 600) return null

        // 5. Reject candidate with explicit junk keywords if target does not have them
        val candLow = candidate.title.lowercase()
        val targetLow = target.title.lowercase()
        val junkKeywords = listOf("jukebox", "nonstop", "non stop", "full album", "all songs", "mashup", "compilation", "1 hour", "10 hour")
        if (junkKeywords.any { candLow.contains(it) && !targetLow.contains(it) }) return null

        // 6. Artist check
        val wanted = artistNames(target.artist)
        val candArtistStr = "${candidate.artist} ${candidate.channelName} ${candidate.title}".lowercase().replace(" ", "")
        val shared = wanted.any { it.length > 2 && candArtistStr.contains(it) }

        var score = sim * 100f + (if (shared) 40f else 0f)
        if (gap != null) {
            score += if (gap <= 3) 25f else if (gap <= 8) 15f else 5f
        }
        return score
    }

    private fun bestMatch(target: ImportTrackMeta, candidates: List<VideoItem>): VideoItem? {
        var best: VideoItem? = null
        var bestScore = -1f
        for (cand in candidates) {
            if (cand.id.length != 11) continue
            val sc = matchScore(target, cand) ?: continue
            if (sc > bestScore) {
                best = cand
                bestScore = sc
            }
        }
        return best
    }

    private fun parseDurationSec(str: String): Int {
        val parts = str.trim().split(":").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            1 -> parts[0]
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0
        }
    }
}
