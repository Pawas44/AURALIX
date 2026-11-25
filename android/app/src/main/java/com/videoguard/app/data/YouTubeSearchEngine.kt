package com.videoguard.app.data

import com.videoguard.app.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object YouTubeSearchEngine {

    suspend fun getSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = URL("https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=$encoded")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            }
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(response)
                val suggestionsArray = jsonArray.optJSONArray(1) ?: return@withContext emptyList()
                val list = mutableListOf<String>()
                for (i in 0 until suggestionsArray.length()) {
                    list.add(suggestionsArray.getString(i))
                }
                return@withContext list
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        emptyList()
    }

    suspend fun searchVideos(query: String, isMusicOnly: Boolean = false, page: Int = 0): List<VideoItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            return@withContext if (isMusicOnly) VideoRepository.getMusicTracks() else VideoRepository.sampleVideos
        }

        // When in Music Mode, prioritize genuine Songs (Audio tracks) via YouTube Music Songs API
        // This avoids Music Videos, Live stage performances, skits, dialogues and cover versions.
        if (isMusicOnly) {
            val musicSongs = searchYouTubeMusicSongs(query.trim())
            if (musicSongs.isNotEmpty()) {
                return@withContext musicSongs
            }
        }

        val searchQuery = if (isMusicOnly && !query.lowercase().contains("song") && !query.lowercase().contains("music")) {
            "$query song"
        } else {
            query
        }

        try {
            val encoded = URLEncoder.encode(searchQuery.trim(), "UTF-8")
            // YouTube paginates via "sp" continuation tokens; we rotate query modifiers
            // to get fresh result sets when paging beyond what one HTML request gives.
            val pageParam = when (page) {
                0 -> ""
                1 -> "&sp=EgIQAQ%3D%3D"   // videos filter — slightly different ordering
                2 -> "&sp=EgIQAw%3D%3D"   // live filter excluded — fresh set
                else -> "&sp=EgIQAQ%3D%3D&page=$page"
            }
            val url = URL("https://www.youtube.com/results?search_query=$encoded$pageParam")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            }

            if (conn.responseCode == 200) {
                val html = conn.inputStream.bufferedReader().use { it.readText() }
                val parsed = parseSearchResults(html, isMusicOnly, query)
                if (parsed.isNotEmpty()) {
                    return@withContext parsed
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback to local filtering if online fails or returns empty
        if (isMusicOnly) VideoRepository.searchMusic(query) else VideoRepository.searchVideos(query)
    }

    suspend fun searchYouTubeMusicSongs(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val outList = mutableListOf<VideoItem>()
        try {
            val url = URL("https://music.youtube.com/youtubei/v1/search?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                setRequestProperty("Origin", "https://music.youtube.com")
                setRequestProperty("Referer", "https://music.youtube.com/")
                setRequestProperty("X-YouTube-Client-Name", "67")
                setRequestProperty("X-YouTube-Client-Version", "1.20240101.01.00")
                doOutput = true
            }

            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240101.01.00")
                        put("hl", "en")
                        put("gl", "IN")
                    })
                })
                put("query", query)
                put("params", "EgWKAQIIAWoSEAQQAxAFEAoQCRAVEA4QEBAR")
            }

            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonStr)
                parseMusicSearchResponse(root, outList)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        outList
    }

    private fun parseMusicSearchResponse(root: JSONObject, outList: MutableList<VideoItem>) {
        val tabs = root.optJSONObject("contents")
            ?.optJSONObject("tabbedSearchResultsRenderer")
            ?.optJSONArray("tabs")
        val tab0 = tabs?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents") ?: return

        for (i in 0 until tab0.length()) {
            val shelfObj = tab0.optJSONObject(i) ?: continue
            val shelfRenderer = shelfObj.optJSONObject("musicShelfRenderer")
            val itemSectionRenderer = shelfObj.optJSONObject("itemSectionRenderer")
            val contents = shelfRenderer?.optJSONArray("contents")
                ?: itemSectionRenderer?.optJSONArray("contents")
                ?: continue

            for (j in 0 until contents.length()) {
                val itemObj = contents.optJSONObject(j) ?: continue
                val resp = itemObj.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                var vid = resp.optJSONObject("playlistItemData")?.optString("videoId") ?: ""
                val flexCols = resp.optJSONArray("flexColumns") ?: continue

                if (vid.isBlank() && flexCols.length() > 0) {
                    val runs0 = flexCols.optJSONObject(0)
                        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.optJSONObject("text")?.optJSONArray("runs")
                    vid = runs0?.optJSONObject(0)?.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("watchEndpoint")?.optString("videoId") ?: ""
                }
                if (vid.isBlank() || vid.length != 11) continue

                var title = ""
                if (flexCols.length() > 0) {
                    val runs0 = flexCols.optJSONObject(0)
                        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.optJSONObject("text")?.optJSONArray("runs")
                    title = runs0?.optJSONObject(0)?.optString("text")?.trim() ?: ""
                }
                if (title.isBlank()) continue

                var artist = "YouTube Music"
                var album = "Single"
                var duration = "3:30"

                if (flexCols.length() > 1) {
                    val runs1 = flexCols.optJSONObject(1)
                        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.optJSONObject("text")?.optJSONArray("runs")
                    if (runs1 != null) {
                        val parts = mutableListOf<String>()
                        var curPart = StringBuilder()
                        for (r in 0 until runs1.length()) {
                            val txt = runs1.optJSONObject(r)?.optString("text") ?: ""
                            if (txt == " • " || txt == "•") {
                                if (curPart.isNotBlank()) {
                                    parts.add(curPart.toString().trim())
                                    curPart = StringBuilder()
                                }
                            } else {
                                curPart.append(txt)
                            }
                        }
                        if (curPart.isNotBlank()) {
                            parts.add(curPart.toString().trim())
                        }

                        if (parts.size >= 3) {
                            artist = parts[0]
                            album = parts[1]
                            duration = parts[2]
                        } else if (parts.size == 2) {
                            artist = parts[0]
                            duration = parts[1]
                        } else if (parts.size == 1) {
                            artist = parts[0]
                        }
                    }
                }

                var thumb = "https://img.youtube.com/vi/$vid/hqdefault.jpg"
                val thumbs = resp.optJSONObject("thumbnail")
                    ?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")
                    ?.optJSONArray("thumbnails")
                if (thumbs != null && thumbs.length() > 0) {
                    val lastUrl = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") ?: ""
                    if (lastUrl.isNotBlank()) {
                        thumb = if (lastUrl.startsWith("//")) "https:$lastUrl" else lastUrl
                    }
                }
                thumb = ArtworkUrlHelper.getHighResArtworkUrl(thumb, vid)

                outList.add(
                    VideoItem(
                        id = vid,
                        title = title,
                        channelName = artist,
                        channelAvatarUrl = thumb,
                        subscriberCount = "Song Track",
                        thumbnailUrl = thumb,
                        views = "Official Song",
                        uploadTime = "YouTube Music",
                        duration = duration,
                        description = "$title - $artist",
                        likes = "100K",
                        isMusicTrack = true,
                        artist = artist,
                        album = album,
                        lyrics = "",
                        category = "Music"
                    )
                )
            }
        }
    }

    private fun parseSearchResults(html: String, isMusic: Boolean, query: String = ""): List<VideoItem> {
        val results = mutableListOf<VideoItem>()
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
                val contents = root.optJSONObject("contents")
                    ?.optJSONObject("twoColumnSearchResultsRenderer")
                    ?.optJSONObject("primaryContents")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")

                if (contents != null) {
                    for (i in 0 until contents.length()) {
                        val section = contents.optJSONObject(i)
                        val itemSection = section?.optJSONObject("itemSectionRenderer")?.optJSONArray("contents")
                        if (itemSection != null) {
                            for (j in 0 until itemSection.length()) {
                                val item = itemSection.optJSONObject(j) ?: continue

                                // 1. Channel result card (channelRenderer)
                                val cr = item.optJSONObject("channelRenderer")
                                if (cr != null && !isMusic) {
                                    parseChannelRenderer(cr)?.let { results.add(it) }
                                }

                                // 2. Video item (videoRenderer)
                                val vr = item.optJSONObject("videoRenderer")
                                if (vr != null) {
                                    parseVideoRenderer(vr, isMusic)?.let { results.add(it) }
                                }

                                // 3. Compact video item (compactVideoRenderer)
                                val cvr = item.optJSONObject("compactVideoRenderer")
                                if (cvr != null) {
                                    parseVideoRenderer(cvr, isMusic)?.let { results.add(it) }
                                }

                                // 4. Lockup View Model (lockupViewModel)
                                val lvm = item.optJSONObject("lockupViewModel")
                                if (lvm != null) {
                                    parseLockupViewModel(lvm, isMusic)?.let { results.add(it) }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Secondary regex fallback if JSON format differed
        if (results.isEmpty()) {
            val vidRegex = Regex("\"videoId\":\"([a-zA-Z0-9_-]{11})\"")
            val matches = vidRegex.findAll(html).map { it.groupValues[1] }.distinct().take(15).toList()
            for (vid in matches) {
                var cleanTitle = query.replace(" song", "", ignoreCase = true).trim()
                if (cleanTitle.isBlank()) cleanTitle = "Song Track"
                val cleanArtist = "YouTube Music"

                results.add(
                    VideoItem(
                        id = vid,
                        title = cleanTitle,
                        channelName = cleanArtist,
                        channelAvatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150",
                        subscriberCount = "1M subscribers",
                        thumbnailUrl = "https://img.youtube.com/vi/$vid/hqdefault.jpg",
                        views = "500K views",
                        uploadTime = "Recently",
                        duration = "3:45",
                        description = cleanTitle,
                        likes = "50K",
                        isMusicTrack = isMusic,
                        artist = cleanArtist,
                        album = "Single",
                        lyrics = "",
                        category = if (isMusic) "Music" else "YouTube"
                    )
                )
            }
        }

        if (isMusic && results.isNotEmpty()) {
            val avoidableRegex = Regex(
                "(?i)\\b(official\\s+video|official\\s+music\\s+video|music\\s+video|video\\s+song|full\\s+video|live\\s+performance|live\\s+concert|stage\\s+performance|live\\s+at)\\b|\\[(official\\s+video|music\\s+video|video|live)\\]|\\((official\\s+video|music\\s+video|video|live)\\)"
            )
            val pureSongs = results.filter { !avoidableRegex.containsMatchIn(it.title) }
            val videoItems = results.filter { avoidableRegex.containsMatchIn(it.title) }
            return if (pureSongs.isNotEmpty()) (pureSongs + videoItems) else results
        }

        return results
    }

    private fun parseChannelRenderer(cr: JSONObject): VideoItem? {
        val cid = cr.optString("channelId")
        val title = cr.optJSONObject("title")?.optString("simpleText")
            ?: cr.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            ?: ""
        if (cid.isBlank() || title.isBlank()) return null

        var avatar = ""
        val thumbs = cr.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        if (thumbs != null && thumbs.length() > 0) {
            avatar = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") ?: ""
        }
        if (avatar.startsWith("//")) avatar = "https:$avatar"

        val subscriberText = cr.optJSONObject("videoCountText")?.optString("simpleText")
            ?: cr.optJSONObject("subscriberCountText")?.optString("simpleText")
            ?: "Channel"
        val handle = cr.optJSONObject("subscriberCountText")?.optString("simpleText") ?: ""

        val displaySubCount = if (handle.startsWith("@") && !subscriberText.startsWith("@")) {
            "$handle • $subscriberText"
        } else {
            subscriberText
        }

        return VideoItem(
            id = "channel_$cid",
            title = title,
            channelName = title,
            channelAvatarUrl = avatar.ifBlank { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150" },
            subscriberCount = displaySubCount,
            thumbnailUrl = avatar.ifBlank { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150" },
            views = "",
            uploadTime = "",
            duration = "",
            description = cr.optJSONObject("descriptionSnippet")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "",
            likes = "",
            isMusicTrack = false,
            artist = title,
            album = "",
            lyrics = "",
            category = "Channel",
            channelId = cid
        )
    }

    private fun parseVideoRenderer(vr: JSONObject, isMusic: Boolean): VideoItem? {
        val vid = vr.optString("videoId")
        val title = vr.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            ?: vr.optJSONObject("title")?.optString("simpleText") ?: ""
        if (vid.isBlank() || title.isBlank()) return null

        val channel = vr.optJSONObject("ownerText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            ?: vr.optJSONObject("shortBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            ?: "YouTube Creator"
        val views = vr.optJSONObject("viewCountText")?.optString("simpleText")
            ?: vr.optJSONObject("shortViewCountText")?.optString("simpleText")
            ?: "1M views"
        val duration = vr.optJSONObject("lengthText")?.optString("simpleText") ?: "4:00"
        val uploadTime = vr.optJSONObject("publishedTimeText")?.optString("simpleText") ?: "Recently"

        // Extract REAL channel avatar thumbnail
        var avatar = ""
        val ctThumbnails = vr.optJSONObject("channelThumbnailSupportedRenderers")
            ?.optJSONObject("channelThumbnailWithLinkRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
        if (ctThumbnails != null && ctThumbnails.length() > 0) {
            avatar = ctThumbnails.optJSONObject(ctThumbnails.length() - 1)?.optString("url") ?: ""
        }
        if (avatar.isBlank()) {
            val rawThumb = vr.optJSONObject("channelThumbnail")?.optJSONArray("thumbnails")
            if (rawThumb != null && rawThumb.length() > 0) {
                avatar = rawThumb.optJSONObject(rawThumb.length() - 1)?.optString("url") ?: ""
            }
        }
        if (avatar.startsWith("//")) {
            avatar = "https:$avatar"
        }

        // Extract channel browseId
        var cid = vr.optJSONObject("channelThumbnailSupportedRenderers")
            ?.optJSONObject("channelThumbnailWithLinkRenderer")
            ?.optJSONObject("navigationEndpoint")
            ?.optJSONObject("browseEndpoint")
            ?.optString("browseId") ?: ""
        if (cid.isBlank()) {
            cid = vr.optJSONObject("ownerText")
                ?.optJSONArray("runs")
                ?.optJSONObject(0)
                ?.optJSONObject("navigationEndpoint")
                ?.optJSONObject("browseEndpoint")
                ?.optString("browseId") ?: ""
        }

        // Extract video thumbnail
        var thumb = "https://img.youtube.com/vi/$vid/hqdefault.jpg"
        val vidThumbs = vr.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        if (vidThumbs != null && vidThumbs.length() > 0) {
            val t = vidThumbs.optJSONObject(vidThumbs.length() - 1)?.optString("url") ?: ""
            if (t.isNotBlank()) {
                thumb = if (t.startsWith("//")) "https:$t" else t
            }
        }
        thumb = ArtworkUrlHelper.getHighResArtworkUrl(thumb, vid)

        return VideoItem(
            id = vid,
            title = title,
            channelName = channel.ifEmpty { "YouTube" },
            channelAvatarUrl = avatar.ifBlank { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150" },
            subscriberCount = "Subscribers",
            thumbnailUrl = thumb,
            views = views.ifEmpty { "1M views" },
            uploadTime = uploadTime.ifEmpty { "Recently" },
            duration = duration.ifEmpty { "4:00" },
            description = title,
            likes = "100K",
            isMusicTrack = isMusic || title.contains("Music", ignoreCase = true) || title.contains("Song", ignoreCase = true),
            artist = channel,
            album = "YouTube Single",
            lyrics = "",
            category = if (isMusic) "Music" else "YouTube",
            channelId = cid
        )
    }

    private fun parseLockupViewModel(lvm: JSONObject, isMusic: Boolean): VideoItem? {
        val vid = lvm.optString("contentId")
        if (vid.isBlank() || vid.length != 11) return null

        val meta = lvm.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
        val title = meta?.optJSONObject("title")?.optString("content") ?: ""
        if (title.isBlank()) return null

        var channel = "YouTube Creator"
        var views = "1M views"
        var uploadTime = "Recently"

        val rows = meta?.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")
        if (rows != null) {
            for (i in 0 until rows.length()) {
                val parts = rows.optJSONObject(i)?.optJSONArray("metadataParts")
                if (parts != null) {
                    for (j in 0 until parts.length()) {
                        val txt = parts.optJSONObject(j)?.optJSONObject("text")?.optString("content")?.trim() ?: ""
                        if (txt.isNotBlank()) {
                            val lower = txt.lowercase()
                            if (lower.contains("view") || lower.contains("watching")) {
                                views = txt
                            } else if (lower.contains("ago") || lower.contains("streamed")) {
                                uploadTime = txt
                            } else if (channel == "YouTube Creator") {
                                channel = txt
                            }
                        }
                    }
                }
            }
        }

        var avatar = ""
        val avatarSources = meta?.optJSONObject("image")?.optJSONObject("decoratedAvatarViewModel")
            ?.optJSONObject("avatar")?.optJSONObject("avatarViewModel")?.optJSONObject("image")?.optJSONArray("sources")
        if (avatarSources != null && avatarSources.length() > 0) {
            avatar = avatarSources.optJSONObject(avatarSources.length() - 1)?.optString("url") ?: ""
        }
        if (avatar.startsWith("//")) avatar = "https:$avatar"

        var thumb = "https://img.youtube.com/vi/$vid/hqdefault.jpg"
        val thumbSources = lvm.optJSONObject("contentImage")?.optJSONObject("thumbnailViewModel")
            ?.optJSONObject("image")?.optJSONArray("sources")
        if (thumbSources != null && thumbSources.length() > 0) {
            val t = thumbSources.optJSONObject(thumbSources.length() - 1)?.optString("url") ?: ""
            if (t.isNotBlank()) thumb = if (t.startsWith("//")) "https:$t" else t
        }
        thumb = ArtworkUrlHelper.getHighResArtworkUrl(thumb, vid)

        var duration = "3:45"
        val overlays = lvm.optJSONObject("contentImage")?.optJSONObject("thumbnailViewModel")?.optJSONArray("overlays")
        if (overlays != null) {
            for (i in 0 until overlays.length()) {
                val overlay = overlays.optJSONObject(i)?.optJSONObject("thumbnailBottomOverlayViewModel")
                val badges = overlay?.optJSONArray("badges")
                if (badges != null && badges.length() > 0) {
                    val badgeText = badges.optJSONObject(0)?.optJSONObject("thumbnailBadgeViewModel")?.optString("text")
                    if (!badgeText.isNullOrBlank()) {
                        duration = badgeText
                        break
                    }
                }
            }
        }

        return VideoItem(
            id = vid,
            title = title,
            channelName = channel,
            channelAvatarUrl = avatar.ifBlank { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150" },
            subscriberCount = "Subscribers",
            thumbnailUrl = thumb,
            views = views,
            uploadTime = uploadTime,
            duration = duration,
            description = title,
            likes = "100K",
            isMusicTrack = isMusic,
            artist = channel,
            album = "YouTube Single",
            lyrics = "",
            category = if (isMusic) "Music" else "YouTube"
        )
    }

    suspend fun getRelatedVideos(video: VideoItem, isMusicOnly: Boolean = false): List<VideoItem> = withContext(Dispatchers.IO) {
        if (isMusicOnly) {
            try {
                // Tier 1 (ByTune Native): Official YouTube Music RDAMVM automix song radio
                val songRadio = getSongRadio(video.id)
                if (songRadio.isNotEmpty()) {
                    return@withContext songRadio
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val candidates = mutableListOf<VideoItem>()
        try {
            val cleanTitle = video.title.replace(Regex("[\\[\\(].*?[\\]\\)]"), "").trim()
            val artist = video.artist.ifBlank { video.channelName }.trim()

            // 1. Search clean title + artist
            val query = if (artist.isNotBlank() && !cleanTitle.contains(artist, ignoreCase = true)) {
                "$artist $cleanTitle"
            } else {
                cleanTitle
            }
            candidates.addAll(searchVideos(query, isMusicOnly).filter { it.id != video.id })

            // 2. Discover related tracks by same artist / artist radio
            if (artist.isNotBlank() && artist != "YouTube Channel" && candidates.size < 12) {
                candidates.addAll(searchVideos("$artist songs", isMusicOnly).filter { it.id != video.id })
            }

            // 3. Search genre / style matching tracks
            if (candidates.size < 8 && artist.isNotBlank()) {
                candidates.addAll(searchVideos("$artist radio", isMusicOnly).filter { it.id != video.id })
            }

            // Deduplicate
            val distinct = candidates.distinctBy { it.id }.filter { it.id != video.id }

            if (distinct.isNotEmpty()) {
                // Rank candidates based on high-dimensional acoustic, lyrical and genre vector similarity (unkletam/Spotify-Recommendation-System)
                val ranked = distinct.sortedByDescending { candidate ->
                    com.videoguard.app.recommendation.SongSimilarityEngine.computeSimilarity(video, candidate)
                }
                return@withContext ranked
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Final fallback: sample videos ranked by vector similarity to seed
        val fallback = if (isMusicOnly) VideoRepository.getMusicTracks().filter { it.id != video.id }
        else VideoRepository.sampleVideos.filter { it.id != video.id }
        fallback.sortedByDescending { com.videoguard.app.recommendation.SongSimilarityEngine.computeSimilarity(video, it) }
    }

    /**
     * Official YouTube Music Radio (RDAMVM<id>) — returns continuous songs specifically
     * curated for this song using YouTube Music's native automix radio algorithm.
     */
    suspend fun getSongRadio(videoId: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<VideoItem>()
        try {
            val url = URL("https://music.youtube.com/youtubei/v1/next")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                setRequestProperty("X-YouTube-Client-Name", "67")
                setRequestProperty("X-YouTube-Client-Version", "1.20240101.01.00")
            }

            val body = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240101.01.00")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("videoId", videoId)
                put("playlistId", "RDAMVM$videoId")
                put("enablePersistentPlaylistPanel", true)
                put("tunerSettingValue", "AUTOMIX_SETTING_NORMAL")
                put("watchEndpointMusicSupportedConfigs", JSONObject().apply {
                    put("watchEndpointMusicConfig", JSONObject().apply {
                        put("hasPersistentPlaylistPanel", true)
                        put("musicVideoType", "MUSIC_VIDEO_TYPE_ATV")
                    })
                })
            }

            conn.outputStream.bufferedWriter().use { it.write(body.toString()) }

            if (conn.responseCode in 200..299) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                if (jsonStr.startsWith("{")) {
                    val root = JSONObject(jsonStr)
                    extractRadioTracks(root, tracks)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        tracks.filter { it.id != videoId }.distinctBy { it.id }
    }

    private fun extractRadioTracks(node: Any?, out: MutableList<VideoItem>) {
        when (node) {
            is JSONObject -> {
                val ppvr = node.optJSONObject("playlistPanelVideoRenderer")
                if (ppvr != null) {
                    val id = ppvr.optString("videoId")
                    val title = ppvr.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                        ?: ppvr.optJSONObject("title")?.optString("simpleText", "") ?: ""
                    val artist = ppvr.optJSONObject("longBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                        ?: ppvr.optJSONObject("shortBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Artist"
                    val thumb = ppvr.optJSONObject("thumbnail")?.optJSONArray("thumbnails")?.let { arr ->
                        arr.optJSONObject(arr.length() - 1)?.optString("url")
                    } ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"
                    val dur = ppvr.optJSONObject("lengthText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                        ?: ppvr.optJSONObject("lengthText")?.optString("simpleText", "3:30") ?: "3:30"

                    if (id.isNotBlank() && title.isNotBlank() && out.none { it.id == id }) {
                        out.add(
                            VideoItem(
                                id = id,
                                title = title,
                                channelName = artist,
                                channelAvatarUrl = "",
                                thumbnailUrl = thumb,
                                views = "",
                                uploadTime = "",
                                duration = dur,
                                isMusicTrack = true,
                                artist = artist,
                                album = ""
                            )
                        )
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    extractRadioTracks(node.opt(key), out)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    extractRadioTracks(node.opt(i), out)
                }
            }
        }
    }
}
