package com.videoguard.app.recommendation

import com.videoguard.app.model.VideoItem
import kotlin.math.sqrt

/**
 * Spotify-Style Content-Based Recommendation Engine
 * Inspired by unkletam/Spotify-Recommendation-System
 *
 * Evaluates high-dimensional acoustic, lyrical, tempo, genre and semantic vector profiles
 * and computes Cosine Vector Similarity between candidate songs and the user's taste centroid.
 */
object SongSimilarityEngine {

    private val STOP_WORDS = setOf(
        "official", "video", "audio", "music", "lyric", "lyrics", "hd", "4k",
        "remix", "version", "full", "song", "records", "topic", "vevo", "feat",
        "ft", "prod", "with", "and", "the", "a", "an", "of", "in", "to"
    )

    private val GENRE_CLUSTERS = listOf(
        "pop", "hip hop", "rock", "desi", "lofi", "edm", "r&b", "punjabi", "classical", "indie", "electronic", "bollywood"
    )

    private const val VECTOR_DIM = 24

    /**
     * Extracts an N-dimensional feature vector for a song:
     * [0]: Energy (0.0 to 1.0)
     * [1]: Valence (0.0 to 1.0)
     * [2]: Danceability (0.0 to 1.0)
     * [3]: Acousticness (0.0 to 1.0)
     * [4]: Duration profile (0.0 to 1.0)
     * [5..16]: 12 Genre cluster projections
     * [17..23]: 7 Semantic Token Hash projections
     */
    fun extractFeatureVector(track: VideoItem): DoubleArray {
        val vec = DoubleArray(VECTOR_DIM)
        val text = "${track.title} ${track.artist} ${track.channelName} ${track.category} ${track.mood}".lowercase()

        // 1. Energy
        var energy = 0.50
        if (text.contains("remix") || text.contains("bass") || text.contains("party") || text.contains("club") || text.contains("edm") || text.contains("trap") || text.contains("rock")) energy += 0.35
        if (text.contains("acoustic") || text.contains("unplugged") || text.contains("piano") || text.contains("slowed") || text.contains("lofi") || text.contains("sleep")) energy -= 0.35
        vec[0] = energy.coerceIn(0.05, 0.95)

        // 2. Valence (musical positiveness)
        var valence = 0.50
        if (text.contains("happy") || text.contains("love") || text.contains("dance") || text.contains("summer") || text.contains("vibe") || text.contains("celebrate")) valence += 0.30
        if (text.contains("sad") || text.contains("alone") || text.contains("heartbreak") || text.contains("dark") || text.contains("cry") || text.contains("breakup")) valence -= 0.30
        vec[1] = valence.coerceIn(0.05, 0.95)

        // 3. Danceability
        var dance = 0.50
        if (text.contains("beat") || text.contains("dance") || text.contains("groove") || text.contains("drop") || text.contains("banger")) dance += 0.35
        if (text.contains("speech") || text.contains("podcast") || text.contains("meditation") || text.contains("classical")) dance -= 0.35
        vec[2] = dance.coerceIn(0.05, 0.95)

        // 4. Acousticness
        var acoustic = 0.30
        if (text.contains("acoustic") || text.contains("unplugged") || text.contains("guitar") || text.contains("piano") || text.contains("orchestra") || text.contains("folk")) acoustic += 0.50
        if (text.contains("synth") || text.contains("electronic") || text.contains("techno") || text.contains("autotune") || text.contains("dubstep")) acoustic -= 0.25
        vec[3] = acoustic.coerceIn(0.05, 0.95)

        // 5. Duration Profile
        val parts = track.duration.trim().split(":").mapNotNull { it.toIntOrNull() }
        val durSec = when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 210
        }
        vec[4] = (durSec.toDouble() / 420.0).coerceIn(0.1, 1.0)

        // 6..16: Genre Cluster One-Hot Projections
        GENRE_CLUSTERS.forEachIndexed { i, genre ->
            vec[5 + i] = if (text.contains(genre)) 1.0 else 0.0
        }

        // 17..23: Semantic Token Projection via consistent Murmur/Hash
        val tokens = cleanTokens("${track.title} ${track.artist}")
        for (token in tokens) {
            val h = kotlin.math.abs(token.hashCode()) % 7
            vec[17 + h] = (vec[17 + h] + 0.30).coerceAtMost(1.0)
        }

        return vec
    }

    /**
     * Cosine Vector Similarity:
     * CosineSim(u, v) = (u . v) / (||u|| * ||v||)
     */
    fun cosineSimilarity(v1: DoubleArray, v2: DoubleArray): Double {
        if (v1.isEmpty() || v2.isEmpty()) return 0.0
        var dot = 0.0
        var norm1 = 0.0
        var norm2 = 0.0
        val len = minOf(v1.size, v2.size)
        for (i in 0 until len) {
            dot += v1[i] * v2[i]
            norm1 += v1[i] * v1[i]
            norm2 += v2[i] * v2[i]
        }
        val denom = sqrt(norm1) * sqrt(norm2)
        return if (denom > 1e-6) (dot / denom).coerceIn(0.0, 1.0) else 0.0
    }

    /**
     * Computes the mean Centroid vector of user preferences:
     * P_user = sum(w_i * V(track_i)) / sum(w_i)
     */
    fun computeProfileCentroid(weightedTracks: List<Pair<VideoItem, Double>>): DoubleArray {
        val centroid = DoubleArray(VECTOR_DIM)
        var totalWeight = 0.0
        for ((track, weight) in weightedTracks) {
            if (weight <= 0.0) continue
            val vec = extractFeatureVector(track)
            for (i in 0 until VECTOR_DIM) {
                centroid[i] += vec[i] * weight
            }
            totalWeight += weight
        }
        if (totalWeight > 0.0) {
            for (i in 0 until VECTOR_DIM) {
                centroid[i] /= totalWeight
            }
        }
        return centroid
    }

    /**
     * Normalized overall similarity (0.0 to 1.0) between two tracks,
     * combining Cosine Feature Vector Similarity (65%) with lexical and artist matching (35%).
     */
    fun computeSimilarity(trackA: VideoItem, trackB: VideoItem): Double {
        if (trackA.id == trackB.id) return 1.0

        val v1 = extractFeatureVector(trackA)
        val v2 = extractFeatureVector(trackB)
        val vectorSim = cosineSimilarity(v1, v2)

        val artistA = UserTasteProfile.get().extractArtist(trackA).lowercase()
        val artistB = UserTasteProfile.get().extractArtist(trackB).lowercase()

        val artistScore = when {
            artistA.isNotBlank() && artistA == artistB -> 1.0
            artistA.isNotBlank() && artistB.isNotBlank() && (artistA.contains(artistB) || artistB.contains(artistA)) -> 0.70
            tokensOverlap(artistA, artistB) > 0.3 -> 0.50
            else -> 0.0
        }

        return (vectorSim * 0.65) + (artistScore * 0.35)
    }

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
