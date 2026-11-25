package com.videoguard.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores and retrieves manual playback source overrides selected by the user.
 * Ensures that if a user manually binds a specific YouTube video to a Spotify song,
 * automatic matching will never overwrite their choice.
 */
object ManualMatchRepository {

    private const val PREFS_NAME = "auralix_manual_matches"
    private var prefs: SharedPreferences? = null
    private val memoryOverrides = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun normalizeKey(title: String, artist: String): String {
        return "${title.trim().lowercase()}_${artist.trim().lowercase()}"
    }

    /**
     * Retrieves any saved manual match for this track ID or track name/artist.
     */
    fun getManualMatch(trackId: String, title: String = "", artist: String = ""): String? {
        val cleanId = trackId.removePrefix("spotify_")
        if (cleanId.isNotBlank()) {
            memoryOverrides[cleanId]?.let { return it }
            prefs?.getString("manual_$cleanId", null)?.let {
                memoryOverrides[cleanId] = it
                return it
            }
        }
        if (title.isNotBlank()) {
            val key = normalizeKey(title, artist)
            memoryOverrides[key]?.let { return it }
            prefs?.getString("manual_key_$key", null)?.let {
                memoryOverrides[key] = it
                return it
            }
        }
        return null
    }

    /**
     * Saves a manual match override for this track.
     */
    fun setManualMatch(trackId: String, youtubeVideoId: String, title: String = "", artist: String = "") {
        if (youtubeVideoId.length != 11) return
        val cleanId = trackId.removePrefix("spotify_")
        if (cleanId.isNotBlank()) {
            memoryOverrides[cleanId] = youtubeVideoId
            prefs?.edit()?.putString("manual_$cleanId", youtubeVideoId)?.apply()
        }
        if (title.isNotBlank()) {
            val key = normalizeKey(title, artist)
            memoryOverrides[key] = youtubeVideoId
            prefs?.edit()?.putString("manual_key_$key", youtubeVideoId)?.apply()
        }
    }

    /**
     * Resets any manual match for this track back to automatic matching.
     */
    fun clearManualMatch(trackId: String, title: String = "", artist: String = "") {
        val cleanId = trackId.removePrefix("spotify_")
        if (cleanId.isNotBlank()) {
            memoryOverrides.remove(cleanId)
            prefs?.edit()?.remove("manual_$cleanId")?.apply()
        }
        if (title.isNotBlank()) {
            val key = normalizeKey(title, artist)
            memoryOverrides.remove(key)
            prefs?.edit()?.remove("manual_key_$key")?.apply()
        }
    }
}
