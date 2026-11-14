package com.videoguard.app.recommendation

import com.videoguard.app.model.VideoItem
import java.util.concurrent.ConcurrentHashMap

data class CachedSection(
    val tracks: List<VideoItem>,
    val timestampMs: Long
)

object RecommendationCache {

    private const val DEFAULT_TTL_MS = 30 * 60 * 1000L // 30 minutes cache TTL
    private val memoryCache = ConcurrentHashMap<String, CachedSection>()

    fun get(sectionKey: String, ttlMs: Long = DEFAULT_TTL_MS): List<VideoItem>? {
        val entry = memoryCache[sectionKey] ?: return null
        if (System.currentTimeMillis() - entry.timestampMs > ttlMs) {
            memoryCache.remove(sectionKey)
            return null
        }
        return entry.tracks
    }

    fun put(sectionKey: String, tracks: List<VideoItem>) {
        if (tracks.isNotEmpty()) {
            memoryCache[sectionKey] = CachedSection(tracks, System.currentTimeMillis())
        }
    }

    fun invalidate(sectionKey: String? = null) {
        if (sectionKey != null) {
            memoryCache.remove(sectionKey)
        } else {
            memoryCache.clear()
        }
    }
}
