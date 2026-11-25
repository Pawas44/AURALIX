package com.videoguard.app.recommendation

import android.content.Context
import com.videoguard.app.data.UserDataRepository
import com.videoguard.app.data.VideoRepository
import com.videoguard.app.data.YouTubeSearchEngine
import com.videoguard.app.data.YouTubeSyncEngine
import com.videoguard.app.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class SmartHomeFeed(
    val madeForYou: List<VideoItem> = emptyList(),
    val recommendedForYou: List<VideoItem> = emptyList(),
    val discoverWeekly: List<VideoItem> = emptyList(),
    val dailyMixes: List<DailyMix> = emptyList(),
    val becauseYouListenedTo: Pair<String, List<VideoItem>>? = null,
    val yourFavouriteGenres: List<Pair<String, List<VideoItem>>> = emptyList(),
    val continueListening: List<VideoItem> = emptyList(),
    val yourMostPlayed: List<VideoItem> = emptyList(),
    val recentlyDiscovered: List<VideoItem> = emptyList(),
    val hiddenGems: List<VideoItem> = emptyList(),
    val trendingInFavouriteGenres: List<VideoItem> = emptyList(),
    val similarToPlaylist: List<VideoItem> = emptyList(),
    val moodRecommendations: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false
)

object RecommendationEngine {

    private val _feedState = MutableStateFlow(SmartHomeFeed())
    val feedState: StateFlow<SmartHomeFeed> = _feedState.asStateFlow()

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        UserTasteProfile.get().init(context)
        DiscoverWeeklyGenerator.init(context)
    }

    /**
     * Re-generates all personalized smart home sections asynchronously.
     */
    suspend fun refreshHomeFeed(selectedMood: String? = null, forceRefresh: Boolean = false): SmartHomeFeed =
        withContext(Dispatchers.IO) {
            _feedState.value = _feedState.value.copy(isLoading = true)

            if (forceRefresh) {
                RecommendationCache.invalidate()
            }

            val profile = UserTasteProfile.get()
            val userHistory = UserDataRepository.history.value
            val userPlaylists = UserDataRepository.playlists.value
            val spotifyTaste = com.videoguard.app.data.SpotifyConnectEngine.spotifyTasteTracks.value
            val likedPlaylist = userPlaylists.find { it.id == "liked_videos" }?.videos ?: emptyList()
            val importedPlaylistTracks = userPlaylists.flatMap { it.videos }

            val filterRecentHistory = com.videoguard.app.data.AppSettingsRepository.dontRepeatSuggestions.value
            val recentPlayedIds = if (filterRecentHistory) userHistory.take(40).map { it.id }.toSet() else emptySet()

            // 1. Dynamically explore and fetch NEW songs matching user taste from YouTube Music
            val dynamicDiscoveryTracks = mutableListOf<VideoItem>()
            val topArtists: List<String> = profile.getTopArtists(limit = 4).map { it.artistName }.filter { it.isNotBlank() }
            val seedTracks = (likedPlaylist + userHistory).filter { it.id.length == 11 }.take(3)

            // A. Algorithmic Song Radio for top tracks (brings 20+ fresh songs with similar acoustic vibe)
            for (st in seedTracks) {
                try {
                    val radioTracks = YouTubeSearchEngine.getSongRadio(st.id)
                    if (radioTracks.isNotEmpty()) {
                        dynamicDiscoveryTracks.addAll(radioTracks)
                    }
                } catch (e: Exception) {}
            }

            // B. Fetch fresh songs for user's favorite artists & styles
            for (artist in topArtists) {
                try {
                    val freshArtistTracks = YouTubeSyncEngine.searchMusicVideosInnerTube("$artist songs")
                    if (freshArtistTracks.isNotEmpty()) {
                        dynamicDiscoveryTracks.addAll(freshArtistTracks.take(8))
                    }
                } catch (e: Exception) {}
            }

            // C. If selectedMood is present, fetch fresh songs for that mood!
            if (!selectedMood.isNullOrBlank()) {
                try {
                    val moodTracks = YouTubeSyncEngine.searchMusicVideosInnerTube("$selectedMood music")
                    if (moodTracks.isNotEmpty()) {
                        dynamicDiscoveryTracks.addAll(moodTracks.take(12))
                    }
                } catch (e: Exception) {}
            }

            // Collect candidate pool with dynamic discovery songs included, deduplicated by canonical identity
            val syncFeed = YouTubeSyncEngine.musicFeed.value
            val rawCandidatePool = (
                dynamicDiscoveryTracks +
                spotifyTaste +
                syncFeed.quickPicks +
                syncFeed.listenAgain +
                syncFeed.recommendedVideos +
                syncFeed.trendingCharts +
                importedPlaylistTracks +
                userHistory +
                likedPlaylist +
                VideoRepository.sampleVideos
            ).filter { it.id !in recentPlayedIds }
            val candidatePool = SongDeduplicationEngine.deduplicate(rawCandidatePool)

            // Deduplication tracker across all smart sections (both Video ID & Canonical Song Identity)
            val placedTrackIds = mutableSetOf<String>()
            val placedCanonicalKeys = mutableSetOf<String>()

            // 1. Made For You / Selected Mood Mix (Blend familiar favorites + new exploration gems)
            val scoredMadeForYou = if (!selectedMood.isNullOrBlank()) {
                val moodCandidates = candidatePool.filter {
                    it.mood.equals(selectedMood, ignoreCase = true) ||
                    it.category.equals(selectedMood, ignoreCase = true)
                }
                val candidatesToUse = if (moodCandidates.isNotEmpty()) moodCandidates else candidatePool
                val pool = if (forceRefresh) candidatesToUse.shuffled() else candidatesToUse
                SongDeduplicationEngine.deduplicateAndFill(
                    primaryTracks = pool.filter { it.id !in placedTrackIds },
                    candidatePool = candidatePool,
                    targetCount = 16,
                    excludedKeys = placedCanonicalKeys
                )
            } else {
                val pool = if (forceRefresh) candidatePool.shuffled() else candidatePool
                val rankedTracks = RecommendationRanker.rank(
                    candidates = pool,
                    recentHistory = userHistory.take(15),
                    likedTracks = likedPlaylist,
                    discoveryFactor = if (forceRefresh) 0.55 else 0.40,
                    maxPerArtist = 2
                ).map { it.track }.filter { it.id !in placedTrackIds }
                SongDeduplicationEngine.deduplicateAndFill(
                    primaryTracks = rankedTracks,
                    candidatePool = candidatePool,
                    targetCount = 16,
                    excludedKeys = placedCanonicalKeys
                )
            }
            placedTrackIds.addAll(scoredMadeForYou.map { it.id })
            placedCanonicalKeys.addAll(scoredMadeForYou.map { SongDeduplicationEngine.getCanonicalIdentity(it).canonicalKey })

            // 2. Discover Weekly (~30 tracks, ~40% familiar / ~60% discovery, 7-day cache)
            val discoverWeeklyTracks = DiscoverWeeklyGenerator.getOrGenerateDiscoverWeekly(
                candidatePool = candidatePool,
                forceRefresh = forceRefresh
            )

            // 3. Daily Mixes (Mix 1 to Mix 5)
            val dailyMixes = DailyMixGenerator.generateDailyMixes(candidatePool)

            // 4. Because You Listened To... (Contextual seed from user history)
            val seedTrack = userHistory.firstOrNull { it.isMusicTrack } ?: userHistory.firstOrNull()
            var becauseYouListenedPair: Pair<String, List<VideoItem>>? = null
            if (seedTrack != null) {
                val seedArtist = profile.extractArtist(seedTrack).ifBlank { seedTrack.title }
                val similar = candidatePool.filter { track ->
                    track.id != seedTrack.id &&
                    track.id !in placedTrackIds &&
                    SongSimilarityEngine.computeSimilarity(seedTrack, track) > 0.40
                }.take(10)
                if (similar.isNotEmpty()) {
                    becauseYouListenedPair = Pair(seedArtist, similar)
                    placedTrackIds.addAll(similar.map { it.id })
                }
            }

            // 5. Your Favourite Genres
            val topGenres = profile.getTopGenres(limit = 3)
            val genreSections = mutableListOf<Pair<String, List<VideoItem>>>()
            for (ga in topGenres) {
                val genreTracks = candidatePool.filter { track ->
                    track.id !in placedTrackIds &&
                    profile.extractGenre(track).contains(ga.genre, ignoreCase = true)
                }.take(8)
                if (genreTracks.isNotEmpty()) {
                    genreSections.add(Pair(ga.genre, genreTracks))
                    placedTrackIds.addAll(genreTracks.map { it.id })
                }
            }

            // 6. Continue Listening (Recently played tracks)
            val continueListening = userHistory.filter { it.id !in placedTrackIds }.take(10)
            placedTrackIds.addAll(continueListening.map { it.id })

            // 7. Your Most Played
            val mostPlayed = userHistory.groupBy { it.id }
                .mapValues { it.value.size }
                .toList()
                .sortedByDescending { it.second }
                .mapNotNull { entry -> userHistory.find { it.id == entry.first } }
                .filter { it.id !in placedTrackIds }
                .take(10)
            placedTrackIds.addAll(mostPlayed.map { it.id })

            // 8. Recently Discovered (Tracks by fresh/new artists)
            val familiarArtists = profile.getTopArtists(limit = 5).map { it.artistName.lowercase() }.toSet()
            val recentlyDiscovered = candidatePool.filter { track ->
                val art = profile.extractArtist(track).lowercase()
                art.isNotBlank() && art !in familiarArtists && track.id !in placedTrackIds
            }.take(10)
            placedTrackIds.addAll(recentlyDiscovered.map { it.id })

            // 9. Hidden Gems (High discovery potential)
            val hiddenGems = candidatePool.filter { track ->
                track.id !in placedTrackIds &&
                !profile.isTrackHidden(track.id) &&
                (track.views.contains("K", ignoreCase = true) || !track.views.contains("M", ignoreCase = true))
            }.shuffled().take(10)
            placedTrackIds.addAll(hiddenGems.map { it.id })

            // 10. Trending In Your Favourite Genres
            val primaryGenre = topGenres.firstOrNull()?.genre ?: "Pop"
            val trendingInGenre = candidatePool.filter { track ->
                track.id !in placedTrackIds &&
                profile.extractGenre(track).contains(primaryGenre, ignoreCase = true)
            }.take(10)
            placedTrackIds.addAll(trendingInGenre.map { it.id })

            // 11. Similar To Your Playlist
            val playlistSeed = likedPlaylist.firstOrNull()
            val similarToPlaylist = if (playlistSeed != null) {
                candidatePool.filter { track ->
                    track.id !in placedTrackIds &&
                    track.id != playlistSeed.id &&
                    SongSimilarityEngine.computeSimilarity(playlistSeed, track) > 0.35
                }.take(10)
            } else {
                emptyList()
            }
            placedTrackIds.addAll(similarToPlaylist.map { it.id })

            // 12. Mood-based recommendations
            val targetMood = selectedMood?.ifBlank { null } ?: "Energize"
            val rawMoodTracks = candidatePool.filter { track ->
                track.id !in placedTrackIds &&
                (track.mood.equals(targetMood, ignoreCase = true) ||
                 track.category.equals(targetMood, ignoreCase = true))
            }
            val moodTracks = if (forceRefresh) rawMoodTracks.shuffled().take(10) else rawMoodTracks.take(10)

            // Recommended for you (Fall-through / high ranking general)
            val remainingCandidates = candidatePool.filter { it.id !in placedTrackIds }
            val poolToRank = if (forceRefresh) remainingCandidates.shuffled() else remainingCandidates
            val recommendedForYou = RecommendationRanker.rank(
                candidates = poolToRank,
                recentHistory = userHistory,
                likedTracks = likedPlaylist,
                discoveryFactor = if (forceRefresh) 0.45 else 0.30,
                maxPerArtist = 2
            ).map { it.track }.take(12)

            val cleanMadeForYou = SongDeduplicationEngine.ensureNoConsecutiveDuplicates(
                if (scoredMadeForYou.isNotEmpty()) scoredMadeForYou
                else SongDeduplicationEngine.deduplicateAndFill(candidatePool, candidatePool, 10, placedCanonicalKeys)
            )

            val cleanRecommended = SongDeduplicationEngine.ensureNoConsecutiveDuplicates(
                if (recommendedForYou.isNotEmpty()) recommendedForYou
                else SongDeduplicationEngine.deduplicateAndFill(candidatePool.shuffled(), candidatePool, 10, placedCanonicalKeys)
            )

            val finalFeed = SmartHomeFeed(
                madeForYou = cleanMadeForYou,
                recommendedForYou = cleanRecommended,
                discoverWeekly = discoverWeeklyTracks,
                dailyMixes = dailyMixes,
                becauseYouListenedTo = becauseYouListenedPair,
                yourFavouriteGenres = genreSections,
                continueListening = continueListening,
                yourMostPlayed = mostPlayed,
                recentlyDiscovered = recentlyDiscovered,
                hiddenGems = hiddenGems,
                trendingInFavouriteGenres = trendingInGenre,
                similarToPlaylist = similarToPlaylist,
                moodRecommendations = moodTracks,
                isLoading = false
            )

            _feedState.value = finalFeed
            finalFeed
        }
}
