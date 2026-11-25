package com.videoguard.app.data

import com.videoguard.app.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Centralized, intelligent YouTube music matching repository.
 *
 * Enforces:
 * 1. Spotify metadata as primary source of truth (Title, Artist, Album, Artwork never corrupted).
 * 2. Intelligent version classification (Official audio/video prioritized; live/covers/slowed penalized).
 * 3. Legitimate record label and artist official channel recognition.
 * 4. Duration compatibility checks against Spotify track duration.
 * 5. Local caching of resolved IDs and persistent manual user overrides.
 */
object SongMatchingRepository {

    private val resolvedCache = java.util.concurrent.ConcurrentHashMap<String, VideoItem>()

    private fun normalizeKey(title: String, artist: String): String {
        return "${title.trim().lowercase()}_${artist.trim().lowercase()}"
    }

    /**
     * Resolves a Spotify track to the best matching YouTube playback source.
     */
    suspend fun resolveTrack(target: ImportTrackMeta): VideoItem? = withContext(Dispatchers.IO) {
        val cacheKey = normalizeKey(target.title, target.artist)

        // 1. Check for User Manual Override
        val manualYtId = ManualMatchRepository.getManualMatch(target.spotifyId, target.title, target.artist)
        if (!manualYtId.isNullOrBlank() && manualYtId.length == 11) {
            val finalThumb = SpotifyArtworkResolver.resolveArtwork(
                originalArtworkUrl = target.coverUrl,
                spotifyId = target.spotifyId,
                title = target.title,
                artist = target.artist,
                youtubeVideoId = manualYtId
            )
            val manualItem = VideoItem(
                id = manualYtId,
                title = target.title,
                channelName = target.artist,
                channelAvatarUrl = finalThumb,
                thumbnailUrl = finalThumb,
                views = "Popular",
                uploadTime = "Imported",
                duration = if (target.durationSeconds > 0) "${target.durationSeconds / 60}:${String.format(java.util.Locale.US, "%02d", target.durationSeconds % 60)}" else "3:30",
                description = target.title,
                isMusicTrack = true,
                artist = target.artist,
                album = target.album.ifBlank { "Single" },
                category = "Music",
                originalTitle = target.title,
                originalArtist = target.artist,
                originalArtworkUrl = target.coverUrl,
                spotifyTrackId = target.spotifyId,
                matchedYouTubeId = manualYtId,
                matchedYouTubeTitle = target.title
            )
            resolvedCache[cacheKey] = manualItem
            return@withContext manualItem
        }

        // 2. Check In-Memory Cache
        resolvedCache[cacheKey]?.let { return@withContext it }

        // 3. Search and Rank Candidates
        val rankedEvaluations = searchAndRankCandidates(target)
        val bestEvaluation = rankedEvaluations.firstOrNull() ?: return@withContext null
        val best = bestEvaluation.candidate

        val finalThumb = SpotifyArtworkResolver.resolveArtwork(
            originalArtworkUrl = target.coverUrl,
            spotifyId = target.spotifyId,
            title = target.title,
            artist = target.artist,
            youtubeThumbnailUrl = best.thumbnailUrl,
            youtubeVideoId = best.id
        )

        val resolvedTrack = VideoItem(
            id = best.id,
            title = target.title.ifBlank { best.title },
            channelName = best.channelName,
            channelAvatarUrl = finalThumb,
            thumbnailUrl = finalThumb,
            views = best.views,
            uploadTime = best.uploadTime,
            duration = if (target.durationSeconds > 0) {
                "${target.durationSeconds / 60}:${String.format(java.util.Locale.US, "%02d", target.durationSeconds % 60)}"
            } else best.duration,
            description = target.title,
            isMusicTrack = true,
            artist = target.artist.ifBlank { best.artist },
            album = if (target.album.isNotBlank()) target.album else best.album,
            category = "Music",
            originalTitle = target.title,
            originalArtist = target.artist,
            originalArtworkUrl = target.coverUrl,
            spotifyTrackId = target.spotifyId,
            matchedYouTubeId = best.id,
            matchedYouTubeTitle = best.title
        )

        resolvedCache[cacheKey] = resolvedTrack
        return@withContext resolvedTrack
    }

    /**
     * Generates structured queries to find candidate YouTube videos in priority order.
     */
    fun buildSearchQueries(meta: ImportTrackMeta): List<String> {
        val cleanTitle = SongTitleNormalizer.removeVideoFluff(
            SongTitleNormalizer.removeEmojis(meta.title)
        )
            .replace(Regex("(?i)\\[.*?\\]|\\(.*?\\)"), " ")
            .replace(Regex("""\s[-–—|•]\s.*$"""), " ")
            .trim()

        val bareTitle = cleanTitle.ifBlank { meta.title.trim() }
        val cleanArtist = meta.artist.replace('\u00A0', ' ').trim()
        val firstArtist = cleanArtist.split(Regex("""\s*(?:,|&|\sx\s|\s(?:feat|ft)\.?\s)\s*""", RegexOption.IGNORE_CASE))
            .firstOrNull()?.trim() ?: ""

        val queries = mutableListOf<String>()

        // Priority 1: Song Name + Primary Artist + Featured Artists
        if (cleanArtist.isNotBlank()) {
            queries.add("$bareTitle $cleanArtist")
        }

        // Priority 2: Song Name + Primary Artist + Official Audio
        if (firstArtist.isNotBlank()) {
            queries.add("$bareTitle $firstArtist Official Audio")
        }

        // Priority 3: Song Name + Primary Artist + Official Video
        if (firstArtist.isNotBlank()) {
            queries.add("$bareTitle $firstArtist Official Video")
        }

        // Priority 4: Song Name + Album Name + Primary Artist
        if (meta.album.isNotBlank() && meta.album != "Single" && meta.album != "Spotify" && firstArtist.isNotBlank()) {
            queries.add("$bareTitle ${meta.album} $firstArtist")
        }

        // Fallback: Title + Primary Artist alone
        if (firstArtist.isNotBlank() && firstArtist != cleanArtist) {
            queries.add("$bareTitle $firstArtist")
        }

        queries.add(bareTitle)
        return queries.distinct()
    }

    /**
     * Searches YouTube and evaluates all candidates using YouTubeMatchScorer.
     */
    suspend fun searchAndRankCandidates(target: ImportTrackMeta): List<YouTubeMatchEvaluation> = withContext(Dispatchers.IO) {
        val allCandidates = mutableListOf<VideoItem>()
        val queries = buildSearchQueries(target)
        val targetFeatured = if (target.featuredArtists.isNotEmpty()) {
            target.featuredArtists
        } else {
            SongMetadataExtractor.extract(target.title, target.artist).featuredArtists
        }

        for (q in queries) {
            try {
                // 1. YouTube Music Official Songs API
                val ytmSongs = MusicImportEngine.searchInnerTubeDirect(q, useMusicApi = true, params = "EgWKAQIIAWoKEAkQChAFEAMQBA==")
                allCandidates.addAll(ytmSongs)

                // If high-quality candidates found on YouTube Music, we can score early
                val earlyRanked = YouTubeMatchScorer.rankCandidates(
                    candidates = allCandidates,
                    targetTitle = target.title,
                    targetArtist = target.artist,
                    targetFeaturedArtists = targetFeatured,
                    targetAlbum = target.album,
                    targetDurationSeconds = target.durationSeconds
                )
                if (earlyRanked.isNotEmpty() && earlyRanked.first().totalScore >= 120) {
                    return@withContext earlyRanked
                }
            } catch (e: Exception) {}

            try {
                // 2. Regular YouTube Video search
                val ytVideos = MusicImportEngine.searchInnerTubeDirect(q, useMusicApi = false, params = "EgIQAQ==")
                allCandidates.addAll(ytVideos)
            } catch (e: Exception) {}

            try {
                // 3. YouTubeSyncEngine fallback
                val syncVideos = YouTubeSyncEngine.searchVideosInnerTube(q, filterToVideos = true)
                allCandidates.addAll(syncVideos)
            } catch (e: Exception) {}
        }

        YouTubeMatchScorer.rankCandidates(
            candidates = allCandidates,
            targetTitle = target.title,
            targetArtist = target.artist,
            targetFeaturedArtists = targetFeatured,
            targetAlbum = target.album,
            targetDurationSeconds = target.durationSeconds
        )
    }
}
