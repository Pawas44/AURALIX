package com.videoguard.app.recommendation

import android.content.Context
import android.content.SharedPreferences
import com.videoguard.app.model.VideoItem
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class ArtistAffinity(
    val artistName: String,
    var score: Double = 0.0,
    var playCount: Int = 0,
    var completeCount: Int = 0,
    var skipCount: Int = 0,
    var likeCount: Int = 0,
    var dislikeCount: Int = 0,
    var lastInteractedMs: Long = System.currentTimeMillis()
)

data class TrackAffinity(
    val trackId: String,
    val title: String,
    val artist: String,
    var playCount: Int = 0,
    var completeCount: Int = 0,
    var skipCount: Int = 0,
    var isLiked: Boolean = false,
    var isDisliked: Boolean = false,
    var isHidden: Boolean = false,
    var totalDurationSec: Float = 0f,
    var listenedDurationSec: Float = 0f,
    var lastPlayedMs: Long = System.currentTimeMillis()
)

data class GenreAffinity(
    val genre: String,
    var score: Double = 0.0,
    var interactionCount: Int = 0
)

class UserTasteProfile private constructor() {

    companion object {
        private const val PREFS_NAME = "auralix_user_taste_profile"
        private const val KEY_PROFILE = "profile_json"

        // Configurable Scoring Weights
        const val WEIGHT_SONG_START = 1.0
        const val WEIGHT_SONG_COMPLETED = 3.5
        const val WEIGHT_SONG_REPLAYED = 4.5
        const val WEIGHT_SONG_LIKED = 5.0
        const val WEIGHT_SONG_ADDED_TO_PLAYLIST = 4.0
        const val WEIGHT_SONG_SKIPPED_EARLY = -2.5
        const val WEIGHT_SONG_SKIPPED_LATE = -0.5
        const val WEIGHT_SONG_DISLIKED = -8.0
        const val WEIGHT_NOT_INTERESTED = -10.0

        @Volatile
        private var instance: UserTasteProfile? = null

        fun get(): UserTasteProfile {
            return instance ?: synchronized(this) {
                instance ?: UserTasteProfile().also { instance = it }
            }
        }
    }

    private var prefs: SharedPreferences? = null

    // Thread-safe profile data maps
    val artistAffinities = ConcurrentHashMap<String, ArtistAffinity>()
    val trackAffinities = ConcurrentHashMap<String, TrackAffinity>()
    val genreAffinities = ConcurrentHashMap<String, GenreAffinity>()
    val moodAffinities = ConcurrentHashMap<String, Double>()
    val hiddenTrackIds = ConcurrentHashMap.newKeySet<String>()
    val hiddenArtistNames = ConcurrentHashMap.newKeySet<String>()
    val discoveredArtists = ConcurrentHashMap.newKeySet<String>()

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadFromPrefs()
    }

    /**
     * Records a track start event.
     */
    fun recordTrackStarted(track: VideoItem) {
        val artist = extractArtist(track)
        val genre = extractGenre(track)
        val now = System.currentTimeMillis()

        // Track affinity
        val ta = trackAffinities.getOrPut(track.id) {
            TrackAffinity(track.id, track.title, artist, lastPlayedMs = now)
        }
        ta.playCount++
        ta.lastPlayedMs = now

        // Artist affinity
        if (artist.isNotBlank()) {
            val aa = artistAffinities.getOrPut(artist) {
                ArtistAffinity(artist, lastInteractedMs = now)
            }
            aa.playCount++
            aa.score += WEIGHT_SONG_START
            aa.lastInteractedMs = now
            discoveredArtists.add(artist)
        }

        // Genre affinity
        if (genre.isNotBlank()) {
            val ga = genreAffinities.getOrPut(genre) { GenreAffinity(genre) }
            ga.score += WEIGHT_SONG_START
            ga.interactionCount++
        }

        // Mood affinity
        if (track.mood.isNotBlank()) {
            val cur = moodAffinities.getOrDefault(track.mood, 0.0)
            moodAffinities[track.mood] = cur + 0.5
        }

        persistAsync()
    }

    /**
     * Records a completed track (user listened to >= 85% or natural completion).
     */
    fun recordTrackCompleted(track: VideoItem, isReplay: Boolean = false) {
        val artist = extractArtist(track)
        val genre = extractGenre(track)
        val now = System.currentTimeMillis()

        val weight = if (isReplay) WEIGHT_SONG_REPLAYED else WEIGHT_SONG_COMPLETED

        val ta = trackAffinities.getOrPut(track.id) {
            TrackAffinity(track.id, track.title, artist, lastPlayedMs = now)
        }
        ta.completeCount++

        if (artist.isNotBlank()) {
            val aa = artistAffinities.getOrPut(artist) { ArtistAffinity(artist, lastInteractedMs = now) }
            aa.completeCount++
            aa.score += weight
            aa.lastInteractedMs = now
        }

        if (genre.isNotBlank()) {
            val ga = genreAffinities.getOrPut(genre) { GenreAffinity(genre) }
            ga.score += weight
            ga.interactionCount++
        }

        persistAsync()
    }

    /**
     * Records a skip. Early skip (< 30s or < 30%) carries heavier penalty.
     */
    fun recordTrackSkipped(track: VideoItem, listenedSec: Float, totalSec: Float) {
        val artist = extractArtist(track)
        val genre = extractGenre(track)
        val now = System.currentTimeMillis()

        val ratio = if (totalSec > 0f) (listenedSec / totalSec) else 0f
        val isEarly = listenedSec < 30f || ratio < 0.30f
        val weight = if (isEarly) WEIGHT_SONG_SKIPPED_EARLY else WEIGHT_SONG_SKIPPED_LATE

        val ta = trackAffinities.getOrPut(track.id) {
            TrackAffinity(track.id, track.title, artist, lastPlayedMs = now)
        }
        ta.skipCount++

        if (artist.isNotBlank()) {
            val aa = artistAffinities.getOrPut(artist) { ArtistAffinity(artist, lastInteractedMs = now) }
            aa.skipCount++
            aa.score = (aa.score + weight).coerceAtLeast(0.0)
            aa.lastInteractedMs = now
        }

        if (genre.isNotBlank() && isEarly) {
            val ga = genreAffinities.getOrPut(genre) { GenreAffinity(genre) }
            ga.score = (ga.score + (weight * 0.5)).coerceAtLeast(0.0)
        }

        persistAsync()
    }

    /**
     * Records explicit Like.
     */
    fun recordTrackLiked(track: VideoItem) {
        val artist = extractArtist(track)
        val genre = extractGenre(track)
        val now = System.currentTimeMillis()

        val ta = trackAffinities.getOrPut(track.id) {
            TrackAffinity(track.id, track.title, artist, lastPlayedMs = now)
        }
        ta.isLiked = true
        ta.isDisliked = false

        if (artist.isNotBlank()) {
            val aa = artistAffinities.getOrPut(artist) { ArtistAffinity(artist, lastInteractedMs = now) }
            aa.likeCount++
            aa.score += WEIGHT_SONG_LIKED
            aa.lastInteractedMs = now
        }

        if (genre.isNotBlank()) {
            val ga = genreAffinities.getOrPut(genre) { GenreAffinity(genre) }
            ga.score += WEIGHT_SONG_LIKED
            ga.interactionCount++
        }

        persistAsync()
    }

    /**
     * Records explicit Dislike or Not Interested.
     */
    fun recordNotInterested(track: VideoItem) {
        val artist = extractArtist(track)
        val now = System.currentTimeMillis()

        hiddenTrackIds.add(track.id)

        val ta = trackAffinities.getOrPut(track.id) {
            TrackAffinity(track.id, track.title, artist, lastPlayedMs = now)
        }
        ta.isDisliked = true
        ta.isHidden = true

        if (artist.isNotBlank()) {
            val aa = artistAffinities.getOrPut(artist) { ArtistAffinity(artist, lastInteractedMs = now) }
            aa.dislikeCount++
            aa.score = (aa.score + WEIGHT_NOT_INTERESTED).coerceAtLeast(0.0)
            if (aa.dislikeCount >= 3) {
                hiddenArtistNames.add(artist.lowercase())
            }
        }

        persistAsync()
    }

    /**
     * Records track added to playlist.
     */
    fun recordTrackAddedToPlaylist(track: VideoItem) {
        val artist = extractArtist(track)
        val genre = extractGenre(track)
        val now = System.currentTimeMillis()

        if (artist.isNotBlank()) {
            val aa = artistAffinities.getOrPut(artist) { ArtistAffinity(artist, lastInteractedMs = now) }
            aa.score += WEIGHT_SONG_ADDED_TO_PLAYLIST
        }
        if (genre.isNotBlank()) {
            val ga = genreAffinities.getOrPut(genre) { GenreAffinity(genre) }
            ga.score += WEIGHT_SONG_ADDED_TO_PLAYLIST
        }
        persistAsync()
    }

    /**
     * Enriches profile from imported Spotify playlists.
     */
    fun enrichFromImportedTracks(tracks: List<VideoItem>) {
        val now = System.currentTimeMillis()
        for (track in tracks) {
            val artist = extractArtist(track)
            val genre = extractGenre(track)
            val ta = trackAffinities.getOrPut(track.id) {
                TrackAffinity(track.id, track.title, artist, lastPlayedMs = now)
            }
            ta.playCount += 1

            if (artist.isNotBlank()) {
                val aa = artistAffinities.getOrPut(artist) { ArtistAffinity(artist, lastInteractedMs = now) }
                aa.score += 2.0 // Boost affinity from imported library
                discoveredArtists.add(artist)
            }
            if (genre.isNotBlank()) {
                val ga = genreAffinities.getOrPut(genre) { GenreAffinity(genre) }
                ga.score += 1.5
            }
        }
        persistAsync()
    }

    fun isTrackHidden(trackId: String): Boolean {
        return hiddenTrackIds.contains(trackId)
    }

    fun isArtistHidden(artistName: String): Boolean {
        return hiddenArtistNames.contains(artistName.lowercase().trim())
    }

    fun getTopArtists(limit: Int = 10): List<ArtistAffinity> {
        return artistAffinities.values
            .filter { !isArtistHidden(it.artistName) }
            .sortedByDescending { it.score }
            .take(limit)
    }

    fun getTopGenres(limit: Int = 5): List<GenreAffinity> {
        return genreAffinities.values
            .sortedByDescending { it.score }
            .take(limit)
    }

    fun getArtistAffinity(artist: String): Double {
        val clean = artist.trim()
        if (clean.isBlank() || isArtistHidden(clean)) return 0.0
        return artistAffinities[clean]?.score ?: 0.0
    }

    fun getGenreAffinity(genre: String): Double {
        val clean = genre.trim()
        if (clean.isBlank()) return 0.0
        return genreAffinities[clean]?.score ?: 0.0
    }

    fun extractArtist(track: VideoItem): String {
        if (track.artist.isNotBlank()) return track.artist.trim()
        val title = track.title
        if (title.contains(" - ")) {
            return title.substringBefore(" - ").trim()
        }
        if (track.channelName.isNotBlank() && !track.channelName.contains("VEVO", ignoreCase = true)) {
            return track.channelName.replace(" - Topic", "").trim()
        }
        return track.channelName.trim()
    }

    fun extractGenre(track: VideoItem): String {
        val text = "${track.title} ${track.description} ${track.category}".lowercase()
        return when {
            text.contains("bollywood") || text.contains("hindi") || text.contains("punjabi") -> "Bollywood / Indian"
            text.contains("pop") -> "Pop"
            text.contains("hip hop") || text.contains("rap") || text.contains("trap") -> "Hip-Hop / Rap"
            text.contains("lo-fi") || text.contains("lofi") || text.contains("chill") -> "Lo-Fi / Chill"
            text.contains("electronic") || text.contains("edm") || text.contains("house") || text.contains("dance") -> "Electronic / Dance"
            text.contains("acoustic") || text.contains("unplugged") || text.contains("piano") || text.contains("guitar") -> "Acoustic / Indie"
            text.contains("rock") || text.contains("metal") -> "Rock"
            text.contains("classical") || text.contains("instrumental") -> "Classical / Instrumental"
            text.contains("r&b") || text.contains("soul") -> "R&B / Soul"
            track.category.isNotBlank() && track.category != "All" -> track.category
            else -> "General Music"
        }
    }

    private fun persistAsync() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            saveToPrefs()
        }
    }

    @Synchronized
    private fun saveToPrefs() {
        val sp = prefs ?: return
        try {
            val root = JSONObject()

            // Artists
            val artistsArr = JSONArray()
            for (aa in artistAffinities.values) {
                val obj = JSONObject().apply {
                    put("name", aa.artistName)
                    put("score", aa.score)
                    put("playCount", aa.playCount)
                    put("completeCount", aa.completeCount)
                    put("skipCount", aa.skipCount)
                    put("likeCount", aa.likeCount)
                    put("dislikeCount", aa.dislikeCount)
                    put("lastMs", aa.lastInteractedMs)
                }
                artistsArr.put(obj)
            }
            root.put("artists", artistsArr)

            // Genres
            val genresArr = JSONArray()
            for (ga in genreAffinities.values) {
                val obj = JSONObject().apply {
                    put("genre", ga.genre)
                    put("score", ga.score)
                    put("count", ga.interactionCount)
                }
                genresArr.put(obj)
            }
            root.put("genres", genresArr)

            // Hidden items
            root.put("hiddenTracks", JSONArray(hiddenTrackIds.toList()))
            root.put("hiddenArtists", JSONArray(hiddenArtistNames.toList()))
            root.put("discovered", JSONArray(discoveredArtists.toList()))

            sp.edit().putString(KEY_PROFILE, root.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    private fun loadFromPrefs() {
        val sp = prefs ?: return
        val raw = sp.getString(KEY_PROFILE, null) ?: return
        try {
            val root = JSONObject(raw)

            // Artists
            val artistsArr = root.optJSONArray("artists")
            if (artistsArr != null) {
                for (i in 0 until artistsArr.length()) {
                    val obj = artistsArr.getJSONObject(i)
                    val name = obj.getString("name")
                    artistAffinities[name] = ArtistAffinity(
                        artistName = name,
                        score = obj.optDouble("score", 0.0),
                        playCount = obj.optInt("playCount", 0),
                        completeCount = obj.optInt("completeCount", 0),
                        skipCount = obj.optInt("skipCount", 0),
                        likeCount = obj.optInt("likeCount", 0),
                        dislikeCount = obj.optInt("dislikeCount", 0),
                        lastInteractedMs = obj.optLong("lastMs", System.currentTimeMillis())
                    )
                }
            }

            // Genres
            val genresArr = root.optJSONArray("genres")
            if (genresArr != null) {
                for (i in 0 until genresArr.length()) {
                    val obj = genresArr.getJSONObject(i)
                    val g = obj.getString("genre")
                    genreAffinities[g] = GenreAffinity(
                        genre = g,
                        score = obj.optDouble("score", 0.0),
                        interactionCount = obj.optInt("count", 0)
                    )
                }
            }

            // Hidden
            val ht = root.optJSONArray("hiddenTracks")
            if (ht != null) {
                for (i in 0 until ht.length()) {
                    hiddenTrackIds.add(ht.getString(i))
                }
            }

            val ha = root.optJSONArray("hiddenArtists")
            if (ha != null) {
                for (i in 0 until ha.length()) {
                    hiddenArtistNames.add(ha.getString(i))
                }
            }

            val da = root.optJSONArray("discovered")
            if (da != null) {
                for (i in 0 until da.length()) {
                    discoveredArtists.add(da.getString(i))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
