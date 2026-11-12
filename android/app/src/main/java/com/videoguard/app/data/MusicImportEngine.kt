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
import java.util.UUID
import java.util.regex.Pattern

data class ImportTrackMeta(
    val title: String,
    val artist: String
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
            return@withContext Result.failure(IllegalArgumentException("Please enter a valid Spotify/Amazon Music link or song titles"))
        }

        try {
            onProgress(ImportProgress("Analyzing link...", 0, 0))

            val (playlistName, trackMetas) = when {
                trimmed.contains("spotify.com") -> parseSpotify(trimmed, onProgress)
                trimmed.contains("music.amazon.") || (trimmed.contains("amazon.") && trimmed.contains("music")) || trimmed.contains("amzn.to") -> parseAmazonMusic(trimmed, onProgress)
                else -> parsePlainTextList(trimmed)
            }

            if (trackMetas.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No tracks found to import. Please check the link or song list."))
            }

            val total = trackMetas.size
            onProgress(ImportProgress("Matching tracks with YouTube Music...", 0, total))

            val matchedVideos = mutableListOf<VideoItem>()
            val batchSize = 4 // process in controlled parallel batches for faster 100+ tracks import

            for (i in trackMetas.indices step batchSize) {
                val batch = trackMetas.subList(i, minOf(i + batchSize, total))
                val resolvedBatch = coroutineScope {
                    batch.map { meta ->
                        async {
                            try {
                                val query = if (meta.artist.isNotBlank()) "${meta.title} ${meta.artist}" else meta.title
                                var results = YouTubeSearchEngine.searchVideos(query, isMusicOnly = true)
                                if (results.isEmpty() && meta.artist.isNotBlank()) {
                                    results = YouTubeSearchEngine.searchVideos(meta.title, isMusicOnly = true)
                                }
                                results.firstOrNull()
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }.awaitAll()
                }

                for (idx in resolvedBatch.indices) {
                    val resolved = resolvedBatch[idx]
                    val meta = batch[idx]
                    if (resolved != null) {
                        matchedVideos.add(resolved)
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

            // Auto-save into UserDataRepository
            UserDataRepository.savePlaylist(finalPlaylist)
            onProgress(ImportProgress("Complete!", total, total, isComplete = true))

            Result.success(finalPlaylist)
        } catch (e: Exception) {
            e.printStackTrace()
            onProgress(ImportProgress("Failed", 0, 0, error = e.localizedMessage))
            Result.failure(e)
        }
    }

    private fun parseSpotify(url: String, onProgress: (ImportProgress) -> Unit): Pair<String, List<ImportTrackMeta>> {
        val cleanUrl = url.substringBefore("?")
        val isTrack = cleanUrl.contains("/track/")
        val isAlbum = cleanUrl.contains("/album/")
        val isPlaylist = cleanUrl.contains("/playlist/")

        if (isTrack) {
            // Single Track via oEmbed
            val oembedUrl = "https://open.spotify.com/oembed?url=" + java.net.URLEncoder.encode(cleanUrl, "UTF-8")
            val jsonStr = httpGet(oembedUrl)
            val json = JSONObject(jsonStr)
            val title = json.optString("title", "Spotify Track")
            val author = json.optString("author_name", "")
            return Pair(title, listOf(ImportTrackMeta(title, author)))
        }

        // Playlist or Album via Embed
        val embedUrl = if (isPlaylist) {
            val id = cleanUrl.substringAfterLast("/")
            "https://open.spotify.com/embed/playlist/$id"
        } else if (isAlbum) {
            val id = cleanUrl.substringAfterLast("/")
            "https://open.spotify.com/embed/album/$id"
        } else {
            cleanUrl
        }

        val html = httpGet(embedUrl)

        // Extract Title from HTML or NEXT_DATA
        var playlistTitle = "Spotify Playlist"
        val tracks = mutableListOf<ImportTrackMeta>()

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

        // Fallback title / track extraction if __NEXT_DATA__ is absent
        if (tracks.isEmpty()) {
            val titleMatcher = Pattern.compile("<title>([^<]+)</title>", Pattern.CASE_INSENSITIVE).matcher(html)
            if (titleMatcher.find()) {
                playlistTitle = titleMatcher.group(1)?.replace(" | Spotify", "")?.replace("Spotify – ", "")?.trim() ?: "Spotify Playlist"
            }
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

    private fun parseAmazonMusic(url: String, onProgress: (ImportProgress) -> Unit): Pair<String, List<ImportTrackMeta>> {
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
        // Amazon Music embeds tracks as primaryText (title) & secondaryText / secondaryText1 (artist)
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

        if (uniqueTracks.isEmpty()) {
            uniqueTracks.add(ImportTrackMeta(title, ""))
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
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            setRequestProperty("Accept", "text/html,application/json,*/*")
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }
}
