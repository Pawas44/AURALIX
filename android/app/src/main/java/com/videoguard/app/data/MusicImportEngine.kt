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

            if (matchedVideos.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Could not match any tracks on YouTube Music"))
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
            if (matchedVideos.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Could not match tracks on YouTube Music"))
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

    private suspend fun resolveTrackOnYouTube(meta: ImportTrackMeta): VideoItem? {
        try {
            val cleanTitle = meta.title.replace(Regex("""[([{【「『][^)\]}】」』]*[)\]}】」』]"""), " ").trim()
            val primaryArtist = meta.artist.split(Regex("[,&+/;]")).firstOrNull()?.trim() ?: meta.artist

            // 1. Primary Query: Clean title + primary artist via InnerTube API (Never blocked by YouTube web scraping!)
            val query1 = if (primaryArtist.isNotBlank() && !cleanTitle.contains(primaryArtist, ignoreCase = true)) {
                "$cleanTitle $primaryArtist"
            } else {
                cleanTitle
            }

            var results = YouTubeSyncEngine.searchVideosInnerTube(query1)
            if (results.isEmpty()) {
                results = YouTubeSearchEngine.searchVideos(query1, isMusicOnly = true)
            }

            // 2. Secondary Query: Full title + artist
            if (results.isEmpty() && meta.artist.isNotBlank()) {
                val query2 = "${meta.title} ${meta.artist}"
                results = YouTubeSyncEngine.searchVideosInnerTube(query2)
                if (results.isEmpty()) {
                    results = YouTubeSearchEngine.searchVideos(query2, isMusicOnly = true)
                }
            }

            // 3. Tertiary Query: Clean title + audio
            if (results.isEmpty()) {
                results = YouTubeSyncEngine.searchVideosInnerTube("$cleanTitle audio")
                if (results.isEmpty()) {
                    results = YouTubeSearchEngine.searchVideos(cleanTitle, isMusicOnly = true)
                }
            }

            if (results.isEmpty()) return null

            // Pick highest scoring candidate or top YouTube search result
            val best = results.maxByOrNull { scoreTrackMatch(meta, it) }
            return best ?: results.firstOrNull()
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
                    matchedVideos.add(
                        resolved.copy(
                            title = cleanTitle,
                            artist = cleanArtist,
                            album = if (resolved.album.isBlank() || resolved.album == "Single") meta.album.ifBlank { resolved.album } else resolved.album
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
                            if (title.isNotBlank()) {
                                tracks.add(ImportTrackMeta(title, subtitle))
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

    private fun scoreTrackMatch(meta: ImportTrackMeta, candidate: VideoItem): Int {
        val metaTitle = cleanTitleWords(meta.title)
        val candTitle = cleanTitleWords(candidate.title)

        // Version markers (remix, acoustic, live)
        val metaVersions = extractVersionMarkers(meta.title)
        val candVersions = extractVersionMarkers(candidate.title)
        var score = 0
        if (metaVersions == candVersions) {
            score += 20
        } else if (metaVersions.isNotEmpty() && candVersions.isEmpty()) {
            score -= 15
        }

        // Substring & Dice similarity
        if (candTitle.contains(metaTitle) || metaTitle.contains(candTitle)) {
            score += 60
        } else {
            val sim = diceSimilarity(metaTitle, candTitle)
            score += (sim * 50).toInt()
        }

        // Artist overlap
        if (meta.artist.isNotBlank() && (candidate.artist.isNotBlank() || candidate.channelName.isNotBlank())) {
            val targetArtists = meta.artist.split(Regex("[,&+/;]")).map { it.trim().lowercase().replace(" ", "") }.filter { it.isNotBlank() }
            val candArtistStr = "${candidate.artist} ${candidate.channelName} ${candidate.title}".lowercase().replace(" ", "")
            val hasOverlap = targetArtists.any { it.length > 2 && candArtistStr.contains(it) }
            if (hasOverlap) score += 30
        }

        // Duration match
        val candSec = parseDurationSec(candidate.duration)
        if (meta.durationSeconds > 0 && candSec > 0) {
            val gap = kotlin.math.abs(meta.durationSeconds - candSec)
            if (gap <= 5) score += 25
            else if (gap <= 15) score += 15
            else if (gap > 45) score -= 20
        }

        return score
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
