package com.videoguard.app.data

import com.videoguard.app.model.VideoItem
import kotlin.math.abs

data class YouTubeMatchEvaluation(
    val candidate: VideoItem,
    val totalScore: Int,
    val isAcceptable: Boolean,
    val versionType: SongVersionType,
    val channelSourceType: ChannelSourceType,
    val matchReason: String = ""
)

object YouTubeMatchScorer {

    const val MINIMUM_ACCEPTABLE_SCORE = 55
    const val MINIMUM_TITLE_SIMILARITY = 45

    /**
     * Converts a duration string like "3:45", "03:45", or "1:02:15" into total seconds.
     */
    fun parseDurationSec(durationStr: String): Int {
        val parts = durationStr.trim().split(":").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            1 -> parts[0]
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0
        }
    }

    /**
     * Evaluates a candidate YouTube video against the authentic Spotify song metadata.
     */
    fun scoreCandidate(
        candidate: VideoItem,
        targetTitle: String,
        targetArtist: String,
        targetFeaturedArtists: List<String> = emptyList(),
        targetAlbum: String = "",
        targetDurationSeconds: Int = 0
    ): YouTubeMatchEvaluation {
        // Disqualify empty or invalid IDs
        if (candidate.id.length != 11 || candidate.id.startsWith("search_")) {
            return YouTubeMatchEvaluation(
                candidate = candidate,
                totalScore = -999,
                isAcceptable = false,
                versionType = SongVersionType.DISQUALIFIED,
                channelSourceType = ChannelSourceType.GENERAL_UPLOADER,
                matchReason = "Invalid video ID"
            )
        }

        // 1. Version Classification & Compatibility
        val targetVersion = SongVersionClassifier.classify(targetTitle)
        val candidateVersion = SongVersionClassifier.classify(candidate.title, candidate.channelName)

        if (candidateVersion == SongVersionType.DISQUALIFIED) {
            return YouTubeMatchEvaluation(
                candidate = candidate,
                totalScore = -999,
                isAcceptable = false,
                versionType = candidateVersion,
                channelSourceType = ChannelSourceType.GENERAL_UPLOADER,
                matchReason = "Disqualified content (reaction/interview/podcast/trailer)"
            )
        }

        val versionScore = SongVersionClassifier.evaluateVersionCompatibility(targetVersion, candidateVersion)

        // 2. Channel Source Classification
        val channelType = MusicSourceClassifier.classifySource(candidate.channelName, targetArtist)
        val sourceScore = MusicSourceClassifier.getSourceScoreBonus(candidate.channelName, targetArtist)

        // 3. Title Matching
        val cleanCandidateTitle = SongTitleNormalizer.removeVideoFluff(
            SongTitleNormalizer.removeEmojis(candidate.title)
        )
            .replace(Regex("(?i)\\[.*?\\]|\\(.*?\\)"), " ")
            .replace(Regex("""\s[-–—|•]\s.*$"""), " ")
            .trim()

        val cleanTargetTitle = SongTitleNormalizer.removeVideoFluff(
            SongTitleNormalizer.removeEmojis(targetTitle)
        )
            .replace(Regex("(?i)\\[.*?\\]|\\(.*?\\)"), " ")
            .replace(Regex("""\s[-–—|•]\s.*$"""), " ")
            .trim()

        val titleSim = SongTitleNormalizer.calculateSimilarity(cleanCandidateTitle, cleanTargetTitle)
        val titleScore = (titleSim * 100).toInt().coerceIn(0, 100)

        // Title containment check
        val nCand = SongTitleNormalizer.normalizeForMatching(cleanCandidateTitle)
        val nTarg = SongTitleNormalizer.normalizeForMatching(cleanTargetTitle)
        val exactOrContains = nCand == nTarg ||
                (nTarg.length >= 3 && nCand.contains(nTarg)) ||
                (nCand.length >= 3 && nTarg.contains(nCand))

        if (titleScore < MINIMUM_TITLE_SIMILARITY && !exactOrContains) {
            return YouTubeMatchEvaluation(
                candidate = candidate,
                totalScore = titleScore - 100,
                isAcceptable = false,
                versionType = candidateVersion,
                channelSourceType = channelType,
                matchReason = "Title mismatch (score: $titleScore)"
            )
        }

        // 4. Artist Matching
        var artistScore = 0
        val targetArtistList = (listOf(targetArtist) + targetFeaturedArtists)
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }

        val candidateText = "${candidate.title} ${candidate.channelName} ${candidate.artist}".lowercase()
        val artistMatches = targetArtistList.any { art ->
            art.length >= 3 && candidateText.contains(art)
        }

        if (artistMatches) {
            artistScore += 35
        } else if (channelType == ChannelSourceType.OFFICIAL_RECORD_LABEL || channelType == ChannelSourceType.TOPIC_CHANNEL) {
            // Legitimate label or topic channel may only mention artist in description or title
            artistScore += 15
        } else if (targetArtistList.isNotEmpty() && !exactOrContains) {
            // Third-party upload that doesn't mention the artist gets penalized
            artistScore -= 30
        }

        // 5. Duration Compatibility
        var durationScore = 0
        val candSec = parseDurationSec(candidate.duration)

        if (targetDurationSeconds > 0 && candSec > 0) {
            val diff = abs(targetDurationSeconds - candSec)
            when {
                diff <= 4 -> durationScore += 30 // Near-identical studio length
                diff <= 15 -> durationScore += 22 // Standard music video intro/outro
                diff <= 30 -> durationScore += 12 // Slightly longer music video sequence
                diff <= 45 -> durationScore += 0
                diff <= 80 -> durationScore -= 45 // Likely a live stage performance or acoustic session
                else -> durationScore -= 85 // Compilation, full concert, or wrong cut
            }
        }

        // 6. Album Match Bonus
        var albumScore = 0
        if (targetAlbum.isNotBlank() && targetAlbum != "Single" && targetAlbum != "Spotify") {
            val cleanAlbum = targetAlbum.lowercase().trim()
            if (candidateText.contains(cleanAlbum)) {
                albumScore += 15
            }
        }

        // 7. Calculate Total Score
        val totalScore = titleScore + artistScore + durationScore + versionScore + sourceScore + albumScore
        val isAcceptable = totalScore >= MINIMUM_ACCEPTABLE_SCORE &&
                candidateVersion != SongVersionType.DISQUALIFIED &&
                (titleScore >= MINIMUM_TITLE_SIMILARITY || exactOrContains)

        val reason = "Title: $titleScore, Art: $artistScore, Dur: $durationScore, Ver: $versionScore (${candidateVersion.name}), Src: $sourceScore (${channelType.name})"

        return YouTubeMatchEvaluation(
            candidate = candidate,
            totalScore = totalScore,
            isAcceptable = isAcceptable,
            versionType = candidateVersion,
            channelSourceType = channelType,
            matchReason = reason
        )
    }

    /**
     * Filters and ranks candidate YouTube videos, returning them sorted from best to worst.
     */
    fun rankCandidates(
        candidates: List<VideoItem>,
        targetTitle: String,
        targetArtist: String,
        targetFeaturedArtists: List<String> = emptyList(),
        targetAlbum: String = "",
        targetDurationSeconds: Int = 0
    ): List<YouTubeMatchEvaluation> {
        return candidates
            .filter { it.id.length == 11 && !it.id.startsWith("search_") }
            .distinctBy { it.id }
            .map { cand ->
                scoreCandidate(
                    candidate = cand,
                    targetTitle = targetTitle,
                    targetArtist = targetArtist,
                    targetFeaturedArtists = targetFeaturedArtists,
                    targetAlbum = targetAlbum,
                    targetDurationSeconds = targetDurationSeconds
                )
            }
            .filter { it.isAcceptable }
            .sortedByDescending { it.totalScore }
    }
}
