package com.videoguard.app.recommendation

import com.videoguard.app.data.SongMetadataExtractor
import com.videoguard.app.data.SongTitleNormalizer
import com.videoguard.app.data.SongVersionClassifier
import com.videoguard.app.data.SongVersionType
import com.videoguard.app.model.VideoItem
import java.util.Locale

data class SongIdentity(
    val normalizedTitle: String,
    val normalizedArtist: String,
    val versionType: SongVersionType,
    val canonicalKey: String
)

/**
 * Intelligent Song Deduplication Engine.
 *
 * Guarantees that duplicate tracks (same song with different video IDs, official video vs audio,
 * topic uploads, user re-uploads, lyrics videos, etc.) are strictly deduplicated, while genuinely
 * distinct artistic versions (Remix, Live, Acoustic, Slowed + Reverb, Instrumental, Cover)
 * are intelligently preserved.
 */
object SongDeduplicationEngine {

    /**
     * Extracts canonical song identity using normalized metadata and version classification.
     */
    fun getCanonicalIdentity(track: VideoItem): SongIdentity {
        // 1. If explicit Spotify ID exists, leverage it for canonical comparison
        val spotifyId = when {
            track.id.startsWith("spotify_") -> track.id.removePrefix("spotify_")
            track.id.startsWith("spotify:") -> track.id.removePrefix("spotify:")
            else -> ""
        }

        // 2. Classify song version
        val rawTitle = track.title.trim()
        val version = SongVersionClassifier.classify(rawTitle, track.channelName)
        val versionKey = when (version) {
            SongVersionType.REMIX_MASHUP -> "remix"
            SongVersionType.LIVE_PERFORMANCE -> "live"
            SongVersionType.ACOUSTIC_VERSION -> "acoustic"
            SongVersionType.SLOWED_REVERB -> "slowed"
            SongVersionType.INSTRUMENTAL_KARAOKE -> "instrumental"
            SongVersionType.COVER_VERSION -> "cover"
            else -> "original" // OFFICIAL_AUDIO, OFFICIAL_MUSIC_VIDEO, LABEL_UPLOAD, STANDARD_UPLOAD, LYRICS_VIDEO
        }

        // 3. Normalize Title: strip emojis, video fluff (Official Audio, 4K, Lyrics, etc.), brackets, punctuation
        val emojiCleaned = SongTitleNormalizer.removeEmojis(rawTitle)
        val fluffRemoved = SongTitleNormalizer.removeVideoFluff(emojiCleaned)
        val unbracketed = fluffRemoved.replace(Regex("(?i)\\[.*?\\]|\\(.*?\\)"), " ")
        val cleanPunct = SongTitleNormalizer.cleanPunctuation(unbracketed)
        val normTitle = SongTitleNormalizer.normalizeRomanizedPhonetics(cleanPunct)
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .trim()

        // 4. Normalize Artist: resolve channel vs actual artist
        val rawArtist = track.artist.ifBlank { track.channelName }.trim()
        val extractedMeta = SongMetadataExtractor.extract(
            title = rawTitle,
            rawArtist = rawArtist,
            channelName = track.channelName
        )
        val effectiveArtist = extractedMeta.primaryArtist.ifBlank { rawArtist }
        val cleanArtist = SongMetadataExtractor.cleanArtistString(effectiveArtist)
        val normArtist = SongTitleNormalizer.normalizeRomanizedPhonetics(
            SongTitleNormalizer.cleanPunctuation(cleanArtist)
        ).lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()

        // 5. Construct canonical key
        val canonicalKey = if (spotifyId.isNotBlank()) {
            "spotify:$spotifyId::$versionKey"
        } else {
            "${normTitle}:::${normArtist}::$versionKey"
        }

        return SongIdentity(
            normalizedTitle = normTitle,
            normalizedArtist = normArtist,
            versionType = version,
            canonicalKey = canonicalKey
        )
    }

    /**
     * Checks if two tracks are representations of the same song and version.
     */
    fun areDuplicateSongs(a: VideoItem, b: VideoItem): Boolean {
        if (a.id == b.id && a.id.isNotBlank()) return true
        val idA = getCanonicalIdentity(a)
        val idB = getCanonicalIdentity(b)
        return idA.canonicalKey == idB.canonicalKey
    }

    /**
     * Deduplicates a list of tracks preserving order.
     */
    fun deduplicate(tracks: List<VideoItem>): List<VideoItem> {
        val seenKeys = mutableSetOf<String>()
        val seenVideoIds = mutableSetOf<String>()
        val result = mutableListOf<VideoItem>()

        for (track in tracks) {
            if (track.id.isBlank()) continue
            if (track.id in seenVideoIds) continue
            val identity = getCanonicalIdentity(track)
            if (identity.canonicalKey in seenKeys) continue

            seenVideoIds.add(track.id)
            seenKeys.add(identity.canonicalKey)
            result.add(track)
        }
        return result
    }

    /**
     * Deduplicates and fills a recommendation list up to [targetCount] by pulling
     * unique replacement tracks from [candidatePool].
     * Never returns empty slots or broken layouts.
     */
    fun deduplicateAndFill(
        primaryTracks: List<VideoItem>,
        candidatePool: List<VideoItem>,
        targetCount: Int,
        excludedKeys: Set<String> = emptySet()
    ): List<VideoItem> {
        val seenKeys = excludedKeys.toMutableSet()
        val seenVideoIds = mutableSetOf<String>()
        val result = mutableListOf<VideoItem>()

        // 1. Add unique items from primary tracks
        for (track in primaryTracks) {
            if (result.size >= targetCount) break
            if (track.id.isBlank() || track.id in seenVideoIds) continue
            val identity = getCanonicalIdentity(track)
            if (identity.canonicalKey in seenKeys) continue

            seenVideoIds.add(track.id)
            seenKeys.add(identity.canonicalKey)
            result.add(track)
        }

        // 2. If below targetCount, fill with unique candidates from candidatePool
        if (result.size < targetCount) {
            for (candidate in candidatePool) {
                if (result.size >= targetCount) break
                if (candidate.id.isBlank() || candidate.id in seenVideoIds) continue
                val identity = getCanonicalIdentity(candidate)
                if (identity.canonicalKey in seenKeys) continue

                seenVideoIds.add(candidate.id)
                seenKeys.add(identity.canonicalKey)
                result.add(candidate)
            }
        }

        return ensureNoConsecutiveDuplicates(result)
    }

    /**
     * Ensures no consecutive songs have the same song title or artist.
     * Swaps with a subsequent distinct track if needed.
     */
    fun ensureNoConsecutiveDuplicates(tracks: List<VideoItem>): List<VideoItem> {
        if (tracks.size <= 2) return tracks
        val list = tracks.toMutableList()

        for (i in 0 until list.size - 1) {
            val curr = getCanonicalIdentity(list[i])
            val next = getCanonicalIdentity(list[i + 1])

            // If consecutive items share title or artist
            if (curr.normalizedTitle == next.normalizedTitle ||
                (curr.normalizedArtist.isNotBlank() && curr.normalizedArtist == next.normalizedArtist)) {
                // Find next item that does not clash
                var swapIdx = -1
                for (j in i + 2 until list.size) {
                    val candidate = getCanonicalIdentity(list[j])
                    if (candidate.normalizedTitle != curr.normalizedTitle &&
                        candidate.normalizedArtist != curr.normalizedArtist) {
                        swapIdx = j
                        break
                    }
                }
                if (swapIdx != -1) {
                    val temp = list[i + 1]
                    list[i + 1] = list[swapIdx]
                    list[swapIdx] = temp
                }
            }
        }

        return list
    }
}
