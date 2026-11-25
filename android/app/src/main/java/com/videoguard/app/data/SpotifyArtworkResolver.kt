package com.videoguard.app.data

import android.content.Context
import android.content.SharedPreferences
import java.io.File

/**
 * Resolves and preserves authentic Spotify album artwork.
 * Guarantees that YouTube thumbnails or lyrics video screenshots
 * never overwrite Spotify album covers.
 */
object SpotifyArtworkResolver {

    private const val PREFS_NAME = "auralix_spotify_artwork_cache"
    private var prefs: SharedPreferences? = null
    private val memoryCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Determines whether a URL is an authentic Spotify image or external album art.
     */
    fun isSpotifyArtwork(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()
        return lower.contains("scdn.co") ||
                lower.contains("spotify.com") ||
                lower.contains("spotifycdn.com") ||
                lower.contains("spotifycdn.net") ||
                (!lower.contains("ytimg.com") && !lower.contains("youtube.com") && !lower.contains("default.jpg") && !lower.contains("unsplash"))
    }

    /**
     * Caches Spotify artwork URL for a track.
     */
    fun cacheArtwork(spotifyId: String, artworkUrl: String, title: String = "", artist: String = "") {
        if (artworkUrl.isBlank()) return
        if (spotifyId.isNotBlank()) {
            memoryCache[spotifyId] = artworkUrl
            prefs?.edit()?.putString("art_$spotifyId", artworkUrl)?.apply()
        }
        if (title.isNotBlank()) {
            val key = "${title.trim().lowercase()}_${artist.trim().lowercase()}"
            memoryCache[key] = artworkUrl
            prefs?.edit()?.putString("art_key_$key", artworkUrl)?.apply()
        }
    }

    /**
     * Resolves the highest-priority album artwork.
     * Priority:
     * 1. Direct Spotify artwork URL
     * 2. In-memory / persistent cache for this Spotify ID or Track
     * 3. Non-YouTube existing cover URL
     * 4. YouTube thumbnail fallback
     */
    fun resolveArtwork(
        originalArtworkUrl: String?,
        spotifyId: String = "",
        title: String = "",
        artist: String = "",
        youtubeThumbnailUrl: String = "",
        youtubeVideoId: String = ""
    ): String {
        // 1. Direct Spotify artwork
        if (isSpotifyArtwork(originalArtworkUrl)) {
            val url = originalArtworkUrl!!.trim()
            cacheArtwork(spotifyId, url, title, artist)
            return url
        }

        // 2. Cached artwork by Spotify ID
        if (spotifyId.isNotBlank()) {
            val fromMem = memoryCache[spotifyId]
            if (!fromMem.isNullOrBlank()) return fromMem
            val fromPrefs = prefs?.getString("art_$spotifyId", null)
            if (!fromPrefs.isNullOrBlank()) {
                memoryCache[spotifyId] = fromPrefs
                return fromPrefs
            }
        }

        // 3. Cached artwork by Title + Artist key
        if (title.isNotBlank()) {
            val key = "${title.trim().lowercase()}_${artist.trim().lowercase()}"
            val fromMem = memoryCache[key]
            if (!fromMem.isNullOrBlank()) return fromMem
            val fromPrefs = prefs?.getString("art_key_$key", null)
            if (!fromPrefs.isNullOrBlank()) {
                memoryCache[key] = fromPrefs
                return fromPrefs
            }
        }

        // 4. Fallback to existing YouTube thumbnail
        if (youtubeThumbnailUrl.isNotBlank() && !youtubeThumbnailUrl.contains("unsplash")) {
            return youtubeThumbnailUrl
        }

        // 5. Fallback to YouTube maxres default
        if (youtubeVideoId.isNotBlank() && youtubeVideoId.length == 11) {
            return "https://img.youtube.com/vi/$youtubeVideoId/hqdefault.jpg"
        }

        return ""
    }
}
