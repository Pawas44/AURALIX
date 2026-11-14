package com.videoguard.app.recommendation

import com.videoguard.app.model.VideoItem

object SongSimilarityEngine {

    private val STOP_WORDS = setOf(
        "official", "video", "audio", "music", "lyric", "lyrics", "hd", "4k",
        "remix", "version", "full", "song", "records", "topic", "vevo", "feat",
        "ft", "prod", "with", "and", "the", "a", "an", "of", "in", "to"
    )

    /**
     * Computes a normalized similarity score (0.0 to 1.0) between two tracks.
     */
    fun computeSimilarity(trackA: VideoItem, trackB: VideoItem): Double {
        if (trackA.id == trackB.id) return 1.0

        val artistA = UserTasteProfile.get().extractArtist(trackA).lowercase()
        val artistB = UserTasteProfile.get().extractArtist(trackB).lowercase()

        // 1. Artist Similarity (Weight: 0.35)
        val artistScore = when {
            artistA.isNotBlank() && artistA == artistB -> 1.0
            artistA.isNotBlank() && artistB.isNotBlank() && (artistA.contains(artistB) || artistB.contains(artistA)) -> 0.70
            tokensOverlap(artistA, artistB) > 0.3 -> 0.50
            else -> 0.0
        }

        // 2. Title & Keyword Similarity (Weight: 0.25)
        val titleTokensA = cleanTokens(trackA.title)
        val titleTokensB = cleanTokens(trackB.title)
        val titleScore = jaccardSimilarity(titleTokensA, titleTokensB)

        // 3. Genre & Category Similarity (Weight: 0.20)
        val genreA = UserTasteProfile.get().extractGenre(trackA)
        val genreB = UserTasteProfile.get().extractGenre(trackB)
        val genreScore = when {
            genreA == genreB -> 1.0
            genreA.contains(genreB) || genreB.contains(genreA) -> 0.75
            else -> 0.0
        }

        // 4. Mood Similarity (Weight: 0.10)
        val moodScore = if (trackA.mood.isNotBlank() && trackA.mood.equals(trackB.mood, ignoreCase = true)) {
            1.0
        } else {
            0.20
        }

        // 5. Channel / Producer Overlap (Weight: 0.10)
        val channelScore = if (trackA.channelName.isNotBlank() && trackA.channelName.equals(trackB.channelName, ignoreCase = true)) {
            1.0
        } else {
            0.0
        }

        val finalScore = (artistScore * 0.35) +
                (titleScore * 0.25) +
                (genreScore * 0.20) +
                (moodScore * 0.10) +
                (channelScore * 0.10)

        return finalScore.coerceIn(0.0, 1.0)
    }

    /**
     * Finds the closest match score of a candidate track against a collection of seed tracks.
     */
    fun maxSimilarityAgainstCollection(candidate: VideoItem, seeds: List<VideoItem>): Double {
        if (seeds.isEmpty()) return 0.5
        var maxScore = 0.0
        for (seed in seeds.take(15)) {
            val score = computeSimilarity(candidate, seed)
            if (score > maxScore) {
                maxScore = score
            }
        }
        return maxScore
    }

    private fun cleanTokens(text: String): Set<String> {
        val clean = text.lowercase()
            .replace(Regex("\\[.*?\\]"), " ")
            .replace(Regex("\\(.*?\\)"), " ")
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
        return clean.split(Regex("\\s+"))
            .filter { it.length > 2 && it !in STOP_WORDS }
            .toSet()
    }

    private fun jaccardSimilarity(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersection = a.intersect(b).size
        val union = a.union(b).size
        return if (union > 0) intersection.toDouble() / union.toDouble() else 0.0
    }

    private fun tokensOverlap(textA: String, textB: String): Double {
        val setA = textA.split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()
        val setB = textB.split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()
        return jaccardSimilarity(setA, setB)
    }
}
