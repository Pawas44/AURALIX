package com.videoguard.app.data

import kotlin.math.abs

/**
 * Evaluates candidate lyrics against expected song metadata to ensure
 * high-accuracy matching and prevent displaying lyrics of wrong songs.
 */
data class ScoredLyricsCandidate(
    val titleScore: Int,
    val artistScore: Int,
    val durationScore: Int,
    val syncedBonus: Int,
    val totalConfidenceScore: Int,
    val isAcceptable: Boolean
)

object LyricsMatchScorer {

    const val MINIMUM_CONFIDENCE_THRESHOLD = 60
    const val MINIMUM_TITLE_SIMILARITY = 50

    /**
     * Scores a candidate lyrics item against expected metadata.
     *
     * @param candidateTitle Title returned from lyrics API
     * @param candidateArtist Artist returned from lyrics API
     * @param candidateDurationSeconds Duration returned from lyrics API (0 if unknown)
     * @param hasSynced Whether this result has real timestamped synced lyrics
     * @param expectedTitle Clean song title
     * @param expectedArtist Expected artist (may be blank if unknown)
     * @param actualDurationSeconds Actual playing track duration (0 if unknown)
     */
    fun scoreCandidate(
        candidateTitle: String,
        candidateArtist: String,
        candidateDurationSeconds: Int,
        hasSynced: Boolean,
        expectedTitle: String,
        expectedArtist: String,
        actualDurationSeconds: Int
    ): ScoredLyricsCandidate {
        // 1. Song Title Similarity (0..100)
        val titleSim = SongTitleNormalizer.calculateSimilarity(candidateTitle, expectedTitle)
        val titleScore = (titleSim * 100).toInt().coerceIn(0, 100)

        // Hard reject if title similarity is too low (< 50%)
        if (titleScore < MINIMUM_TITLE_SIMILARITY) {
            return ScoredLyricsCandidate(
                titleScore = titleScore,
                artistScore = 0,
                durationScore = 0,
                syncedBonus = 0,
                totalConfidenceScore = titleScore / 2,
                isAcceptable = false
            )
        }

        // 2. Artist Similarity (0..100)
        val artistScore: Int
        val isExpectedArtistKnown = expectedArtist.isNotBlank() && !SongMetadataExtractor.isLabelOrChannel(expectedArtist)

        if (isExpectedArtistKnown) {
            val artSim = SongTitleNormalizer.calculateSimilarity(candidateArtist, expectedArtist)
            val baseArtistScore = (artSim * 100).toInt().coerceIn(0, 100)

            // Token overlap bonus for artists (e.g. "Arijit Singh" vs "Arijit Singh, Pritam")
            val cTokens = SongTitleNormalizer.normalizeForMatching(candidateArtist).split(" ").filter { it.length > 2 }
            val eTokens = SongTitleNormalizer.normalizeForMatching(expectedArtist).split(" ").filter { it.length > 2 }
            val hasCommonToken = cTokens.any { it in eTokens }

            artistScore = if (hasCommonToken && baseArtistScore < 75) {
                maxOf(baseArtistScore, 80)
            } else {
                baseArtistScore
            }
        } else {
            // Artist is unknown: neutral artist score so title and duration drive matching
            artistScore = 70
        }

        // 3. Duration Compatibility (0..100)
        var durationScore = 75 // Neutral default when duration is unknown
        var hardRejectDuration = false

        if (actualDurationSeconds > 0 && candidateDurationSeconds > 0) {
            val diff = abs(candidateDurationSeconds - actualDurationSeconds)
            when {
                diff <= 3 -> durationScore = 100 // Near perfect match
                diff <= 8 -> durationScore = 90
                diff <= 15 -> durationScore = 75
                diff <= 25 -> durationScore = 50
                diff <= 35 -> durationScore = 25
                else -> {
                    durationScore = 0
                    hardRejectDuration = true // Exceeded 35s difference: likely a different cut, extended mix, or wrong track
                }
            }
        }

        if (hardRejectDuration) {
            return ScoredLyricsCandidate(
                titleScore = titleScore,
                artistScore = artistScore,
                durationScore = 0,
                syncedBonus = 0,
                totalConfidenceScore = 30,
                isAcceptable = false
            )
        }

        // 4. Synced Lyrics Bonus (+10)
        val syncedBonus = if (hasSynced) 10 else 0

        // 5. Weighted Aggregate Score: Title (45%) + Artist (35%) + Duration (20%) + Synced Bonus
        val weightedScore = (titleScore * 0.45f) + (artistScore * 0.35f) + (durationScore * 0.20f)
        val totalScore = (weightedScore + syncedBonus).toInt().coerceIn(0, 100)

        val isAcceptable = totalScore >= MINIMUM_CONFIDENCE_THRESHOLD && titleScore >= MINIMUM_TITLE_SIMILARITY

        return ScoredLyricsCandidate(
            titleScore = titleScore,
            artistScore = artistScore,
            durationScore = durationScore,
            syncedBonus = syncedBonus,
            totalConfidenceScore = totalScore,
            isAcceptable = isAcceptable
        )
    }
}
