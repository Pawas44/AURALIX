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
                val parsed = parseSearchResults(html, isMusicOnly)
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

    private fun parseSearchResults(html: String, isMusic: Boolean): List<VideoItem> {
        val results = mutableListOf<VideoItem>()
        try {
            val marker = "var ytInitialData = "
            val startIdx = html.indexOf(marker)
            if (startIdx != -1) {
                val jsonStart = startIdx + marker.length
                val endIdx = html.indexOf(";</script>", jsonStart)
                if (endIdx != -1) {
                    val jsonStr = html.substring(jsonStart, endIdx)
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
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Secondary regex fallback if JSON format differed
        if (results.isEmpty()) {
            val vidRegex = Regex("\"videoId\":\"([a-zA-Z0-9_-]{11})\"")
            val matches = vidRegex.findAll(html).map { it.groupValues[1] }.distinct().take(15).toList()
            for (vid in matches) {
                results.add(
                    VideoItem(
                        id = vid,
                        title = "YouTube Video ($vid)",
                        channelName = "YouTube Channel",
                        channelAvatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150",
                        subscriberCount = "1M subscribers",
                        thumbnailUrl = "https://img.youtube.com/vi/$vid/hqdefault.jpg",
                        views = "500K views",
                        uploadTime = "Recently",
                        duration = "3:45",
                        description = "YouTube stream playback",
                        likes = "50K",
                        isMusicTrack = isMusic,
                        artist = "Artist",
                        album = "Single",
                        lyrics = "",
                        category = if (isMusic) "Music" else "YouTube"
                    )
                )
            }
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
        try {
            // First search with clean title or artist + title
            val cleanTitle = video.title.replace(Regex("[\\[\\(].*?[\\]\\)]"), "").trim()
            val query = if (video.artist.isNotBlank() && !video.title.contains(video.artist, ignoreCase = true)) {
                "${video.artist} $cleanTitle"
            } else if (video.channelName.isNotBlank() && !video.channelName.startsWith("@") && !video.title.contains(video.channelName, ignoreCase = true)) {
                "${video.channelName} $cleanTitle"
            } else {
                cleanTitle
            }

            val searchRes = searchVideos(query, isMusicOnly).filter { it.id != video.id }
            if (searchRes.isNotEmpty()) {
                return@withContext searchRes
            }

            // Fallback: search by channel or artist alone
            val secondaryQuery = video.artist.ifBlank { video.channelName }
            if (secondaryQuery.isNotBlank()) {
                val secRes = searchVideos(secondaryQuery, isMusicOnly).filter { it.id != video.id }
                if (secRes.isNotEmpty()) {
                    return@withContext secRes
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Final fallback: sample videos
        if (isMusicOnly) VideoRepository.getMusicTracks().filter { it.id != video.id }
        else VideoRepository.sampleVideos.filter { it.id != video.id }
    }
}
