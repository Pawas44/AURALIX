package com.videoguard.app.recommendation

import com.videoguard.app.data.YouTubeSearchEngine
import com.videoguard.app.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MusicDiscoveryService {

    /**
     * Explains why a track is recommended to the user.
     */
    fun explainRecommendation(track: VideoItem): String {
        val profile = UserTasteProfile.get()
        val artist = profile.extractArtist(track)
        val genre = profile.extractGenre(track)

        val artistScore = profile.getArtistAffinity(artist)
        val genreScore = profile.getGenreAffinity(genre)
        val trackScore = profile.trackAffinities[track.id]

        return when {
            trackScore?.isLiked == true -> "Because you added this track to your favorites."
            artistScore > 8.0 -> "Because you listen to $artist frequently."
            artistScore > 3.0 -> "Based on your recent listening to $artist."
            genreScore > 5.0 -> "Recommended because you frequently listen to $genre."
            track.mood.isNotBlank() && (profile.moodAffinities[track.mood] ?: 0.0) > 3.0 ->
                "Matches your preferred mood: ${track.mood}."
            else -> "Recommended based on your music taste and current trending tracks."
        }
    }

    /**
     * Dynamically generates a "Show more like this" radio queue.
     */
    suspend fun getMoreLikeThis(seedTrack: VideoItem): List<VideoItem> = withContext(Dispatchers.IO) {
        val related = YouTubeSearchEngine.getRelatedVideos(seedTrack, isMusicOnly = true)
        val profile = UserTasteProfile.get()
        return@withContext related.filter {
            !profile.isTrackHidden(it.id) &&
            !profile.isArtistHidden(profile.extractArtist(it))
        }
    }

    /**
     * Handles "Not interested" action: immediately penalizes track/artist and evicts cache.
     */
    fun markNotInterested(track: VideoItem) {
        ListeningHistoryManager.onTrackNotInterested(track)
        RecommendationCache.invalidate()
    }
}
