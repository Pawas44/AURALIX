package com.videoguard.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.videoguard.app.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Ghost Caching Engine (Zero-Data Offline Playback).
 * 
 * Automatically and silently caches songs the user frequently listens to (2+ plays).
 * Features:
 * - Silent background caching with zero notifications or UI interruptions
 * - LRU bounded cache (max 30 songs / ~200MB) to preserve device storage
 * - Instant 0-second offline playback fallback when internet is absent or slow
 * - Seamless integration with PlaybackController
 */
object GhostCacheEngine {
    private const val TAG = "GhostCacheEngine"
    private const val PREFS_NAME = "auralix_ghost_cache_prefs"
    private const val GHOST_DIR_NAME = "ghost_cache"
    private const val MAX_CACHED_TRACKS = 30
    private const val PLAY_COUNT_THRESHOLD = 2

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefs: SharedPreferences? = null
    private val activeCachingJobs = ConcurrentHashMap<String, Boolean>()

    private val _cachedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val cachedTrackIds: StateFlow<Set<String>> = _cachedTrackIds.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        refreshCachedSet(context)
    }

    private fun getGhostDir(context: Context): File {
        val dir = File(context.cacheDir, GHOST_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getCachedFile(context: Context, trackId: String): File? {
        val dir = getGhostDir(context)
        val file = File(dir, "$trackId.m4a")
        return if (file.exists() && file.length() > 50_000L) file else null
    }

    fun isGhostCached(context: Context, trackId: String): Boolean {
        return _cachedTrackIds.value.contains(trackId) || getCachedFile(context, trackId) != null
    }

    /**
     * Called whenever a track starts playing.
     * Increments play count and triggers silent ghost caching if threshold met.
     */
    fun onTrackPlayed(context: Context, track: VideoItem) {
        val sp = prefs ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also { prefs = it }
        val id = track.id
        if (id.isBlank() || id.startsWith("search_")) return

        val now = System.currentTimeMillis()
        val currentPlays = sp.getInt("plays_$id", 0) + 1

        sp.edit()
            .putInt("plays_$id", currentPlays)
            .putLong("last_played_$id", now)
            .apply()

        // If officially downloaded, no need to ghost cache
        if (UserDataRepository.isDownloaded(id)) return

        // If already cached, just update access time
        if (getCachedFile(context, id) != null) {
            refreshCachedSet(context)
            return
        }

        // Trigger silent caching once track has been listened to at least PLAY_COUNT_THRESHOLD times
        if (currentPlays >= PLAY_COUNT_THRESHOLD) {
            silentCacheTrack(context, track)
        }
    }

    /**
     * Silently caches the audio file in the background without notifications.
     */
    fun silentCacheTrack(context: Context, track: VideoItem) {
        val id = track.id
        if (id.isBlank() || activeCachingJobs.containsKey(id)) return
        if (UserDataRepository.isDownloaded(id) || getCachedFile(context, id) != null) return

        activeCachingJobs[id] = true
        scope.launch {
            try {
                val ghostDir = getGhostDir(context)
                val targetFile = File(ghostDir, "$id.m4a")
                val tempFile = File(ghostDir, "$id.tmp")

                // Evict LRU files before writing new ones if limit reached
                evictLruIfNeeded(context)

                // 1. Resolve stream format from UserDataRepository engine
                val format = UserDataRepository.fetchMuxedFormat18(id)
                    ?: UserDataRepository.fetchRealYouTubeAudioFormat(id)

                if (format != null && format.url.isNotBlank()) {
                    val success = downloadSilentStream(format.url, format.userAgent, tempFile)
                    if (success && tempFile.exists() && tempFile.length() > 50_000L) {
                        if (targetFile.exists()) targetFile.delete()
                        tempFile.renameTo(targetFile)
                        Log.d(TAG, "Ghost cached successfully: ${track.title} ($id) [${targetFile.length()} bytes]")
                        refreshCachedSet(context)
                    } else {
                        tempFile.delete()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Silent caching failed for $id: ${e.message}")
            } finally {
                activeCachingJobs.remove(id)
            }
        }
    }

    private suspend fun downloadSilentStream(
        streamUrl: String,
        userAgent: String,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(streamUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000
                readTimeout = 25000
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Referer", "https://www.youtube.com/")
                setRequestProperty("Origin", "https://www.youtube.com")
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Connection", "Keep-Alive")
            }

            if (conn.responseCode !in 200..299) return@withContext false

            conn.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }
            true
        } catch (e: Exception) {
            outputFile.delete()
            false
        } finally {
            try { conn?.disconnect() } catch (ignored: Exception) {}
        }
    }

    /**
     * Enforces the maximum cached tracks limit by removing least-recently played items.
     */
    private fun evictLruIfNeeded(context: Context) {
        val dir = getGhostDir(context)
        val files = dir.listFiles { f -> f.extension == "m4a" } ?: return
        if (files.size < MAX_CACHED_TRACKS) return

        val sp = prefs ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Sort files by last played timestamp ascending (oldest first)
        val sorted = files.sortedBy { f ->
            val trackId = f.nameWithoutExtension
            sp.getLong("last_played_$trackId", f.lastModified())
        }

        // Delete oldest until count is under limit
        val toRemove = files.size - MAX_CACHED_TRACKS + 2
        for (i in 0 until minOf(toRemove, sorted.size)) {
            val fileToDelete = sorted[i]
            val trackId = fileToDelete.nameWithoutExtension
            fileToDelete.delete()
            sp.edit().remove("plays_$trackId").remove("last_played_$trackId").apply()
            Log.d(TAG, "LRU evicted ghost cache file: ${fileToDelete.name}")
        }
    }

    private fun refreshCachedSet(context: Context) {
        val dir = getGhostDir(context)
        val ids = dir.listFiles { f -> f.extension == "m4a" && f.length() > 50_000L }
            ?.map { it.nameWithoutExtension }
            ?.toSet() ?: emptySet()
        _cachedTrackIds.value = ids
    }
}
