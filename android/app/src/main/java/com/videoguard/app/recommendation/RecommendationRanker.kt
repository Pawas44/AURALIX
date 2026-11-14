package com.videoguard.app.recommendation

import com.videoguard.app.model.VideoItem

data class ScoredTrack(
    val track: VideoItem,
    val score: Double,
    val primaryReason: String
)

object RecommendationRanker {

    private const val ONE_DAY_MS = 24 * 60 * 60 * 1000L

    /**
     * Ranks candidate tracks according to personalized user taste, similarity, and diversity controls.
     */
    fun rank(
        candidates: List<VideoItem>,
        recentHistory: List<VideoItem>,
        likedTracks: List<VideoItem>,
        discoveryFactor: Double = 0.25,
        maxPerArtist: Int = 2
    ): List<ScoredTrack> {
        val profile = UserTasteProfile.get()
        val now = System.currentTimeMillis()

        val recentIds = recentHistory.map { it.id }.toSet()
        val scoredList = mutableListOf<ScoredTrack>()

        for (candidate in candidates) {
            // Check if track or artist is hidden/disliked
            val artist = profile.extractArtist(candidate)
            val genre = profile.extractGenre(candidate)
            if (profile.isTrackHidden(candidate.id) || profile.isArtistHidden(artist)) {
                continue
            }

            // 1. User Preference Affinity Score (0.0 to 1.0)
            val artistScore = profile.getArtistAffinity(artist)
            val genreScore = profile.getGenreAffinity(genre)
            val moodScore = profile.moodAffinities[candidate.mood] ?: 0.0

            val normArtistScore = (artistScore / 25.0).coerceIn(0.0, 1.0)
            val normGenreScore = (genreScore / 20.0).coerceIn(0.0, 1.0)
            val normMoodScore = (moodScore / 10.0).coerceIn(0.0, 1.0)

            val tasteAffinity = (normArtistScore * 0.50) + (normGenreScore * 0.35) + (normMoodScore * 0.15)

            // 2. Similarity Against Liked Tracks and History (0.0 to 1.0)
            val similarityToLiked = SongSimilarityEngine.maxSimilarityAgainstCollection(candidate, likedTracks)
            val similarityToHistory = SongSimilarityEngine.maxSimilarityAgainstCollection(candidate, recentHistory)
            val contentSimilarity = (similarityToLiked * 0.60) + (similarityToHistory * 0.40)

            // 3. Discovery Bonus: Fresh artists that align with user's favorite genres
            val isUnfamiliarArtist = !profile.discoveredArtists.contains(artist) && artistScore < 2.0
            val discoveryBonus = if (isUnfamiliarArtist && genreScore > 1.0) {
                0.35 * discoveryFactor
            } else {
                0.0
            }

            // 4. Repetition Penalty: Penalize songs played in last 24h
            val trackAffinity = profile.trackAffinities[candidate.id]
            val lastPlayedMs = trackAffinity?.lastPlayedMs ?: 0L
            val repetitionMultiplier = if (candidate.id in recentIds || (now - lastPlayedMs < ONE_DAY_MS)) {
                0.35 // 65% cooldown penalty for recently played
            } else {
                1.0
            }

            // 5. Total Weighted Score
            val rawScore = ((tasteAffinity * 0.40) + (contentSimilarity * 0.40) + discoveryBonus) * repetitionMultiplier

            // Determine human-readable reason
            val reason = when {
                artistScore > 8.0 -> "Because you listen to $artist frequently"
                candidate.id in likedTracks.map { it.id } -> "Because you liked this track"
                similarityToLiked > 0.65 -> "Similar to your liked music"
                isUnfamiliarArtist && genreScore > 3.0 -> "New discovery in $genre"
                genreScore > 5.0 -> "Based on your love for $genre"
                else -> "Recommended for you"
            }

            scoredList.add(ScoredTrack(candidate, rawScore, reason))
        }

        // Sort descending by score
        val sorted = scoredList.sortedByDescending { it.score }

        // Apply Diversity Controls: enforce maximum tracks per artist
        val artistCounts = mutableMapOf<String, Int>()
        val diverseList = mutableListOf<ScoredTrack>()

        for (item in sorted) {
            val art = profile.extractArtist(item.track).lowercase()
            val count = artistCounts.getOrDefault(art, 0)
            if (count < maxPerArtist) {
                diverseList.add(item)
                artistCounts[art] = count + 1
            }
        }

        return diverseList
    }
}
