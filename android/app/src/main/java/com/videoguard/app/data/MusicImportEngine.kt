package com.videoguard.app.data

import com.videoguard.app.model.UserPlaylist
import com.videoguard.app.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
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
                trimmed.contains("music.amazon.com") -> parseAmazonMusic(trimmed, onProgress)
                else -> parsePlainTextList(trimmed)
            }

            if (trackMetas.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No tracks found to import. Please check the link or song list."))
            }

            val total = trackMetas.size
            onProgress(ImportProgress("Matching tracks with YouTube Music...", 0, total))

            val matchedVideos = mutableListOf<VideoItem>()
            val batchSize = 3 // process in controlled parallel batches to prevent rate limits

            for (i in trackMetas.indices step batchSize) {
                val batch = trackMetas.subList(i, minOf(i + batchSize, total))
                val resolvedBatch = coroutineScope {
                    batch.map { meta ->
                        async {
                            try {
                                val query = if (meta.artist.isNotBlank()) "${meta.title} ${meta.artist}" else meta.title
                                val results = YouTubeSearchEngine.searchVideos(query, isMusicOnly = true)
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
            title = titleMatcher.group(1)?.replace("on Amazon Music", "")?.replace("Amazon.com: ", "")?.trim() ?: title
        }

        val tracks = mutableListOf<ImportTrackMeta>()
        val rowRegex = Pattern.compile("<meta property=\"music:song\" content=\"([^\"]+)\"")
        val rMatcher = rowRegex.matcher(html)
        while (rMatcher.find()) {
            val trackUrl = rMatcher.group(1) ?: continue
            val songName = trackUrl.substringAfterLast("/").replace("-", " ")
            if (songName.isNotBlank()) {
                tracks.add(ImportTrackMeta(songName, ""))
            }
        }

        if (tracks.isEmpty()) {
            tracks.add(ImportTrackMeta(title, ""))
        }

        return Pair(title, tracks)
    }

    private fun parsePlainTextList(text: String): Pair<String, List<ImportTrackMeta>> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val tracks = mutableListOf<ImportTrackMeta>()

        for (line in lines) {
            val cleaned = line.replace(Regex("^\\d+[\\.\\)\\-]\\s*"), "")
            val parts = if (cleaned.contains(" - ")) {
                cleaned.split(" - ")
            } else if (cleaned.contains(" by ")) {
                cleaned.split(" by ")
            } else {
                listOf(cleaned)
            }

            if (parts.size >= 2) {
                tracks.add(ImportTrackMeta(parts[0].trim(), parts[1].trim()))
            } else if (cleaned.isNotBlank()) {
                tracks.add(ImportTrackMeta(cleaned, ""))
            }
        }

        return Pair("My Custom Playlist", tracks)
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
