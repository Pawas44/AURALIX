package com.videoguard.app.data

import android.content.Context
import android.content.SharedPreferences
import com.videoguard.app.model.VideoItem
import java.util.concurrent.ConcurrentHashMap

/**
 * Intelligent Album Artwork Resolver.
 *
 * Implements strict artwork resolution priority:
 * 1. Existing valid album artwork URL from album metadata.
 * 2. Spotify album artwork, if available (via SpotifyArtworkResolver).
 * 3. Cached album artwork (in-memory + persistent preferences).
 * 4. Artwork from another song belonging to the same album.
 * 5. Valid YouTube thumbnail as fallback.
 *
 * Guarantees that album artwork never permanently renders as a black/empty square.
 */
object AlbumArtworkResolver {

    private const val PREFS_NAME = "auralix_album_artwork_cache"
    private var prefs: SharedPreferences? = null
    private val memoryCache = ConcurrentHashMap<String, String>()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Cleans protocol-relative URLs (e.g. "//lh3.googleusercontent.com/...") into valid HTTPS URLs.
     */
    fun cleanUrl(url: String?): String {
        val raw = url?.trim().orEmpty()
        if (raw.isBlank() || raw.equals("null", ignoreCase = true)) return ""
        return if (raw.startsWith("//")) "https:$raw" else raw
    }

    /**
     * Determines whether an artwork URL is valid and non-placeholder.
     */
    fun isValidArtworkUrl(url: String?): Boolean {
        val clean = cleanUrl(url)
        if (clean.isBlank()) return false
        if (clean.contains("unsplash.com") && clean.contains("photo-1534528741775")) return false // generic dummy avatar
        return clean.startsWith("http://") || clean.startsWith("https://")
    }

    private fun generateAlbumKey(title: String, artist: String): String {
        val cleanT = SongTitleNormalizer.cleanPunctuation(SongTitleNormalizer.removeEmojis(title)).trim().lowercase()
        val cleanA = SongTitleNormalizer.cleanPunctuation(SongMetadataExtractor.cleanArtistString(artist)).trim().lowercase()
        return if (cleanT.isNotBlank()) "${cleanT}:::${cleanA}" else ""
    }

    /**
     * Caches resolved album artwork in memory and persistent storage.
     */
    fun cacheAlbumArtwork(albumId: String, title: String = "", artist: String = "", artworkUrl: String) {
        val clean = cleanUrl(artworkUrl)
        if (!isValidArtworkUrl(clean)) return

        if (albumId.isNotBlank()) {
            memoryCache[albumId] = clean
            prefs?.edit()?.putString("art_id_$albumId", clean)?.apply()
        }

        val key = generateAlbumKey(title, artist)
        if (key.isNotBlank()) {
            memoryCache[key] = clean
            prefs?.edit()?.putString("art_key_$key", clean)?.apply()
        }
    }

    /**
     * Resolves the best available artwork for an album following the 5-tier priority.
     */
    fun resolveArtwork(
        albumId: String = "",
        title: String = "",
        artist: String = "",
        primaryUrl: String? = null,
        tracks: List<VideoItem> = emptyList()
    ): String {
        val cleanPrimary = cleanUrl(primaryUrl)

        // PRIORITY 1: Existing valid album artwork URL from album metadata (e.g. Spotify CDN or Google CDN)
        if (isValidArtworkUrl(cleanPrimary)) {
            val highRes = ArtworkUrlHelper.getHighResArtworkUrl(cleanPrimary)
            cacheAlbumArtwork(albumId, title, artist, highRes)
            return highRes
        }

        // PRIORITY 2: Spotify album artwork, if available via Spotify resolver / taste cache
        if (title.isNotBlank()) {
            val spotifyArt = SpotifyArtworkResolver.resolveArtwork(
                originalArtworkUrl = null,
                title = title,
                artist = artist
            )
            if (isValidArtworkUrl(spotifyArt) && SpotifyArtworkResolver.isSpotifyArtwork(spotifyArt)) {
                cacheAlbumArtwork(albumId, title, artist, spotifyArt)
                return spotifyArt
            }
        }

        // PRIORITY 3: Cached album artwork (in-memory or persistent SharedPreferences)
        if (albumId.isNotBlank()) {
            val fromMem = memoryCache[albumId]
            if (isValidArtworkUrl(fromMem)) return fromMem!!
            val fromPrefs = prefs?.getString("art_id_$albumId", null)
            if (isValidArtworkUrl(fromPrefs)) {
                memoryCache[albumId] = fromPrefs!!
                return fromPrefs
            }
        }

        val albumKey = generateAlbumKey(title, artist)
        if (albumKey.isNotBlank()) {
            val fromMem = memoryCache[albumKey]
            if (isValidArtworkUrl(fromMem)) return fromMem!!
            val fromPrefs = prefs?.getString("art_key_$albumKey", null)
            if (isValidArtworkUrl(fromPrefs)) {
                memoryCache[albumKey] = fromPrefs!!
                return fromPrefs
            }
        }

        // PRIORITY 4: Artwork from another song belonging to the same album
        if (tracks.isNotEmpty()) {
            // 4a. Check if any song has an authentic Spotify artwork
            for (track in tracks) {
                val trackThumb = cleanUrl(track.thumbnailUrl)
                if (isValidArtworkUrl(trackThumb) && SpotifyArtworkResolver.isSpotifyArtwork(trackThumb)) {
                    val highRes = ArtworkUrlHelper.getHighResArtworkUrl(trackThumb, track.id)
                    cacheAlbumArtwork(albumId, title, artist, highRes)
                    return highRes
                }
            }

            // 4b. Check if any song has a valid high-res YouTube / Music thumbnail
            for (track in tracks) {
                val trackThumb = cleanUrl(track.thumbnailUrl)
                if (isValidArtworkUrl(trackThumb)) {
                    val highRes = ArtworkUrlHelper.getHighResArtworkUrl(trackThumb, track.id)
                    cacheAlbumArtwork(albumId, title, artist, highRes)
                    return highRes
                }
            }

            // 4c. Synthesize from first valid track ID
            val firstWithId = tracks.firstOrNull { it.id.length == 11 }
            if (firstWithId != null) {
                val fallbackUrl = "https://i.ytimg.com/vi/${firstWithId.id}/hqdefault.jpg"
                cacheAlbumArtwork(albumId, title, artist, fallbackUrl)
                return fallbackUrl
            }
        }

        // PRIORITY 5: Valid YouTube thumbnail as fallback if primary URL was present but unformatted
        if (cleanPrimary.isNotBlank()) {
            val formatted = ArtworkUrlHelper.getHighResArtworkUrl(cleanPrimary)
            if (isValidArtworkUrl(formatted)) {
                cacheAlbumArtwork(albumId, title, artist, formatted)
                return formatted
            }
        }

        return ""
    }
}
