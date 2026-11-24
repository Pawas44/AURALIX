package com.videoguard.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.videoguard.app.model.UserPlaylist
import com.videoguard.app.model.VideoItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object PlaylistShareEngine {

    private const val BASE_WEB_URL = "https://auralix.app/playlist"
    private const val BASE_SCHEME_URL = "auralix://playlist"

    /**
     * Generates a 1-click share link and launches the native Android sharesheet.
     */
    fun sharePlaylist(context: Context, playlist: UserPlaylist) {
        val shareLink = generateShareLink(playlist)

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, playlist.title)
            putExtra(Intent.EXTRA_TEXT, shareLink)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(sendIntent, "Share \"${playlist.title}\"")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /**
     * Direct custom-scheme deep link (auralix://playlist?p=...) that launches the app directly
     * without routing through a web browser.
     */
    fun generateAppSchemeLink(playlist: UserPlaylist): String {
        return try {
            val isRemoteYt = playlist.id.startsWith("PL") || playlist.id.startsWith("OLAK5uy_") || playlist.id.startsWith("RD")
            if (isRemoteYt && playlist.videos.isEmpty()) {
                val encTitle = URLEncoder.encode(playlist.title, "UTF-8")
                return "$BASE_SCHEME_URL?id=${playlist.id}&title=$encTitle"
            }
            val payload = buildCompressedPayload(playlist)
            val encTitle = URLEncoder.encode(playlist.title, "UTF-8")
            "$BASE_SCHEME_URL?p=$payload&title=$encTitle"
        } catch (e: Exception) {
            val vids = playlist.videos.map { it.id }.joinToString(",")
            val encTitle = URLEncoder.encode(playlist.title, "UTF-8")
            "$BASE_SCHEME_URL?title=$encTitle&v=$vids"
        }
    }

    /**
     * Creates an ultra-compact 1-click share URL.
     * Uses GZIP + URL-Safe Base64 so full song lists (even 50+ songs) pack into a tiny URL
     * that opens instantly on any phone without requiring a server database.
     */
    fun generateShareLink(playlist: UserPlaylist): String {
        return try {
            val isRemoteYt = playlist.id.startsWith("PL") || playlist.id.startsWith("OLAK5uy_") || playlist.id.startsWith("RD")
            if (isRemoteYt && playlist.videos.isEmpty()) {
                val encTitle = URLEncoder.encode(playlist.title, "UTF-8")
                return "$BASE_WEB_URL/?id=${playlist.id}&title=$encTitle"
            }

            val compressedBase64 = buildCompressedPayload(playlist)
            val encTitle = URLEncoder.encode(playlist.title, "UTF-8")
            "$BASE_WEB_URL/?p=$compressedBase64&title=$encTitle"
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to simple comma-separated IDs
            val vids = playlist.videos.map { it.id }.joinToString(",")
            val encTitle = URLEncoder.encode(playlist.title, "UTF-8")
            "$BASE_WEB_URL/?title=$encTitle&v=$vids"
        }
    }

    private fun buildCompressedPayload(playlist: UserPlaylist): String {
        val json = JSONObject().apply {
            put("i", playlist.id)
            put("t", playlist.title)
            if (playlist.description.isNotBlank()) put("d", playlist.description)
            if (!playlist.customCoverUrl.isNullOrBlank()) put("c", playlist.customCoverUrl)
            val arr = JSONArray()
            for (v in playlist.videos) {
                val vo = JSONObject().apply {
                    put("i", v.id)
                    put("t", v.title)
                    put("a", v.artist.ifBlank { v.channelName })
                    put("d", v.duration)
                    if (v.thumbnailUrl.isNotBlank()) put("u", v.thumbnailUrl)
                }
                arr.put(vo)
            }
            put("v", arr)
        }
        return compressAndEncode(json.toString())
    }

    /**
     * Parses an incoming deep link URI (from WhatsApp, Chrome, Telegram, SMS, etc.)
     * Returns a fully reconstructed UserPlaylist ready to view and play.
     */
    fun parseIncomingDeepLink(uri: Uri): UserPlaylist? {
        try {
            val scheme = uri.scheme?.lowercase() ?: ""
            val host = uri.host?.lowercase() ?: ""

            // 1. Auralix Web or Scheme Link (auralix://, GitHub Pages, auralix.app, or any URI with ?p=)
            val isAuralixLink = scheme == "auralix" ||
                    host.contains("pawas44.github.io") ||
                    host.contains("auralix.app") ||
                    uri.getQueryParameter("p") != null

            if (isAuralixLink) {
                // A. Check compressed payload `p`
                val p = uri.getQueryParameter("p")
                if (!p.isNullOrBlank()) {
                    val jsonStr = decompressAndDecode(p)
                    if (jsonStr.isNotBlank() && jsonStr.startsWith("{")) {
                        val json = JSONObject(jsonStr)
                        val id = json.optString("i", "shared_${System.currentTimeMillis()}")
                        val title = json.optString("t", "Shared Playlist")
                        val desc = json.optString("d", "Shared via AURALIX")
                        val cover = json.optString("c", "")
                        val arr = json.optJSONArray("v") ?: JSONArray()
                        val list = mutableListOf<VideoItem>()
                        for (idx in 0 until arr.length()) {
                            val obj = arr.optJSONObject(idx) ?: continue
                            val vid = obj.optString("i", "")
                            if (vid.isBlank()) continue
                            val vTitle = obj.optString("t", "Song $idx")
                            val vArtist = obj.optString("a", "Unknown Artist")
                            val vDur = obj.optString("d", "3:30")
                            val vThumb = obj.optString("u", ArtworkUrlHelper.getHighResArtworkUrl("", vid))
                            list.add(
                                VideoItem(
                                    id = vid,
                                    title = vTitle,
                                    channelName = vArtist,
                                    channelAvatarUrl = vThumb,
                                    thumbnailUrl = vThumb,
                                    views = "",
                                    uploadTime = "",
                                    duration = vDur,
                                    isMusicTrack = true,
                                    artist = vArtist
                                )
                            )
                        }
                        return UserPlaylist(
                            id = if (id.startsWith("shared_")) id else "shared_$id",
                            title = title,
                            description = desc,
                            videos = list,
                            isSystem = false,
                            iconType = "playlist",
                            customCoverUrl = cover.ifBlank { list.firstOrNull()?.thumbnailUrl }
                        )
                    }
                }

                // B. Check simple video list `v` and `title`
                val vidsParam = uri.getQueryParameter("v")
                val titleParam = uri.getQueryParameter("title") ?: uri.getQueryParameter("t") ?: "Shared Playlist"
                if (!vidsParam.isNullOrBlank()) {
                    val ids = vidsParam.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    val list = ids.mapIndexed { idx, vid ->
                        val thumb = ArtworkUrlHelper.getHighResArtworkUrl("", vid)
                        VideoItem(
                            id = vid,
                            title = "Song ${idx + 1}",
                            channelName = "AURALIX Track",
                            channelAvatarUrl = thumb,
                            thumbnailUrl = thumb,
                            views = "",
                            uploadTime = "",
                            duration = "3:30",
                            isMusicTrack = true
                        )
                    }
                    return UserPlaylist(
                        id = "shared_${System.currentTimeMillis()}",
                        title = titleParam,
                        description = "Shared via AURALIX",
                        videos = list,
                        isSystem = false,
                        iconType = "playlist",
                        customCoverUrl = list.firstOrNull()?.thumbnailUrl
                    )
                }

                // C. Check remote YouTube playlist ID `id` (e.g. PL...)
                val idParam = uri.getQueryParameter("id")
                if (!idParam.isNullOrBlank()) {
                    return UserPlaylist(
                        id = idParam,
                        title = titleParam.ifBlank { "YouTube Playlist" },
                        description = "Synced via YouTube",
                        isSystem = false,
                        iconType = "playlist"
                    )
                }
            }

            // 2. Direct YouTube Playlist link (e.g. youtube.com/playlist?list=PL...)
            if (host.contains("youtube.com") || host.contains("youtu.be")) {
                val listId = uri.getQueryParameter("list")
                if (!listId.isNullOrBlank()) {
                    return UserPlaylist(
                        id = listId,
                        title = "YouTube Playlist",
                        description = "Imported from YouTube",
                        isSystem = false,
                        iconType = "playlist"
                    )
                }
            }

            // 3. Direct Spotify Playlist link (e.g. open.spotify.com/playlist/...)
            if (host.contains("spotify.com") && uri.path?.contains("/playlist/") == true) {
                val sId = uri.pathSegments.lastOrNull().orEmpty().substringBefore("?")
                if (sId.isNotBlank()) {
                    return UserPlaylist(
                        id = "spotify_$sId",
                        title = "Spotify Playlist",
                        description = "Imported from Spotify",
                        isSystem = false,
                        iconType = "playlist"
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Robust parser that extracts a playlist from ANY text, whether it is a URL,
     * an entire copied WhatsApp/Telegram message, or raw parameters.
     */
    fun parseTextForPlaylist(rawInput: String): UserPlaylist? {
        val text = rawInput.trim()
        if (text.isBlank()) return null

        // 1. Direct regex match on compressed `p` parameter anywhere in the text
        val pMatch = Regex("""[?&]p=([A-Za-z0-9_-]+)""").find(text)
        if (pMatch != null) {
            val pVal = pMatch.groupValues[1]
            try {
                val jsonStr = decompressAndDecode(pVal)
                if (jsonStr.isNotBlank() && jsonStr.startsWith("{")) {
                    val json = JSONObject(jsonStr)
                    val id = json.optString("i", "shared_${System.currentTimeMillis()}")
                    val title = json.optString("t", "Shared Playlist")
                    val desc = json.optString("d", "Shared via AURALIX")
                    val cover = json.optString("c", "")
                    val arr = json.optJSONArray("v") ?: JSONArray()
                    val list = mutableListOf<VideoItem>()
                    for (idx in 0 until arr.length()) {
                        val obj = arr.optJSONObject(idx) ?: continue
                        val vid = obj.optString("i", "")
                        if (vid.isBlank()) continue
                        val vTitle = obj.optString("t", "Song $idx")
                        val vArtist = obj.optString("a", "Unknown Artist")
                        val vDur = obj.optString("d", "3:30")
                        val vThumb = obj.optString("u", ArtworkUrlHelper.getHighResArtworkUrl("", vid))
                        list.add(
                            VideoItem(
                                id = vid,
                                title = vTitle,
                                channelName = vArtist,
                                channelAvatarUrl = vThumb,
                                thumbnailUrl = vThumb,
                                views = "",
                                uploadTime = "",
                                duration = vDur,
                                isMusicTrack = true,
                                artist = vArtist
                            )
                        )
                    }
                    return UserPlaylist(
                        id = if (id.startsWith("shared_")) id else "shared_$id",
                        title = title,
                        description = desc,
                        videos = list,
                        isSystem = false,
                        iconType = "playlist",
                        customCoverUrl = cover.ifBlank { list.firstOrNull()?.thumbnailUrl }
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Fallback to URI parsing
        val urlMatch = Regex("""(https?://\S+|auralix://\S+)""").find(text)?.value ?: text
        return try {
            val uri = Uri.parse(urlMatch)
            parseIncomingDeepLink(uri)
        } catch (e: Exception) {
            null
        }
    }

    private fun compressAndEncode(input: String): String {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(input.toByteArray(Charsets.UTF_8)) }
        return Base64.encodeToString(bos.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun decompressAndDecode(base64Str: String): String {
        val bytes = Base64.decode(base64Str, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val bis = ByteArrayInputStream(bytes)
        return GZIPInputStream(bis).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
