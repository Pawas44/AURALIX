package com.videoguard.app.recommendation

import android.content.Context
import android.content.SharedPreferences
import com.videoguard.app.data.UserDataRepository
import com.videoguard.app.data.YouTubeSearchEngine
import com.videoguard.app.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object DiscoverWeeklyGenerator {

    private const val PREFS_NAME = "auralix_discover_weekly"
    private const val KEY_WEEKLY_TRACKS = "weekly_tracks_json"
    private const val KEY_LAST_REFRESH_MS = "last_refresh_ms"
    private const val KEY_PREVIOUS_WEEK_TRACKS = "previous_week_tracks_json"

    private const val SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000L
    private const val TARGET_DISCOVER_SIZE = 30

    private var prefs: SharedPreferences? = null
    private var cachedWeekly: List<VideoItem> = emptyList()
    private var previousWeekly: List<VideoItem> = emptyList()
    private var lastRefreshTimestamp: Long = 0L

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadFromPrefs()
    }

    /**
     * Retrieves the current Discover Weekly playlist (approx 30 tracks).
     * Refreshes automatically if older than 7 days.
     */
    suspend fun getOrGenerateDiscoverWeekly(
        candidatePool: List<VideoItem>,
        forceRefresh: Boolean = false
    ): List<VideoItem> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedWeekly.size >= 15 && (now - lastRefreshTimestamp) < SEVEN_DAYS_MS) {
            return@withContext cachedWeekly
        }

        val generated = generateDiscoverWeekly(candidatePool)
        if (generated.isNotEmpty()) {
            if (cachedWeekly.isNotEmpty()) {
                previousWeekly = cachedWeekly
            }
            cachedWeekly = generated
            lastRefreshTimestamp = now
            saveToPrefs()
        }
        return@withContext if (cachedWeekly.isNotEmpty()) cachedWeekly else candidatePool.take(TARGET_DISCOVER_SIZE)
    }

    private suspend fun generateDiscoverWeekly(candidatePool: List<VideoItem>): List<VideoItem> {
        val profile = UserTasteProfile.get()
        val history = UserDataRepository.history.value
        val topArtists = profile.getTopArtists(limit = 6).map { it.artistName }
        val topGenres = profile.getTopGenres(limit = 4).map { it.genre }

        val familiarTracks = mutableListOf<VideoItem>()
        val discoveryTracks = mutableListOf<VideoItem>()

        // 1. Separate candidates into familiar and discovery
        for (track in candidatePool) {
            val artist = profile.extractArtist(track)
            if (profile.isTrackHidden(track.id) || profile.isArtistHidden(artist)) continue

            if (topArtists.any { it.equals(artist, ignoreCase = true) }) {
                familiarTracks.add(track)
            } else {
                discoveryTracks.add(track)
            }
        }

        // 2. Target ratio: ~40% familiar (~12 tracks), ~60% discovery (~18 tracks)
        val targetFamiliarCount = 12
        val targetDiscoveryCount = 18

        val selectedFamiliar = familiarTracks.shuffled().take(targetFamiliarCount)
        val selectedDiscovery = discoveryTracks.shuffled().take(targetDiscoveryCount)

        val blended = (selectedFamiliar + selectedDiscovery).distinctBy { it.id }.toMutableList()

        // 3. Fallback: if not enough tracks, pull from candidatePool
        if (blended.size < TARGET_DISCOVER_SIZE) {
            val remainingNeeded = TARGET_DISCOVER_SIZE - blended.size
            val fill = candidatePool.filter { c ->
                blended.none { it.id == c.id } &&
                !profile.isTrackHidden(c.id) &&
                !profile.isArtistHidden(profile.extractArtist(c))
            }.shuffled().take(remainingNeeded)
            blended.addAll(fill)
        }

        return blended.shuffled()
    }

    fun getPreviousWeek(): List<VideoItem> = previousWeekly

    @Synchronized
    private fun saveToPrefs() {
        val sp = prefs ?: return
        try {
            val curArr = JSONArray()
            for (track in cachedWeekly) {
                curArr.put(UserDataRepository.videoToJson(track))
            }
            val prevArr = JSONArray()
            for (track in previousWeekly) {
                prevArr.put(UserDataRepository.videoToJson(track))
            }
            sp.edit()
                .putString(KEY_WEEKLY_TRACKS, curArr.toString())
                .putString(KEY_PREVIOUS_WEEK_TRACKS, prevArr.toString())
                .putLong(KEY_LAST_REFRESH_MS, lastRefreshTimestamp)
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    private fun loadFromPrefs() {
        val sp = prefs ?: return
        try {
            lastRefreshTimestamp = sp.getLong(KEY_LAST_REFRESH_MS, 0L)
            val curStr = sp.getString(KEY_WEEKLY_TRACKS, null)
            if (!curStr.isNullOrBlank()) {
                val arr = JSONArray(curStr)
                val list = mutableListOf<VideoItem>()
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { list.add(UserDataRepository.jsonToVideo(it)) }
                }
                cachedWeekly = list
            }
            val prevStr = sp.getString(KEY_PREVIOUS_WEEK_TRACKS, null)
            if (!prevStr.isNullOrBlank()) {
                val arr = JSONArray(prevStr)
                val list = mutableListOf<VideoItem>()
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { list.add(UserDataRepository.jsonToVideo(it)) }
                }
                previousWeekly = list
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
