package com.videoguard.app.data

import java.util.Locale

/**
 * Structured song metadata extracted intelligently from YouTube titles, descriptions,
 * audio tags, and track metadata.
 */
data class ExtractedSongMetadata(
    val songTitle: String,
    val primaryArtist: String,
    val featuredArtists: List<String> = emptyList(),
    val albumOrMovie: String = "",
    val version: String = "",
    val isArtistConfident: Boolean = false
)

object SongMetadataExtractor {

    // Comprehensive record label and promotional channel blacklist
    private val RECORD_LABELS_AND_CHANNELS = setOf(
        "t-series", "tseries", "t series", "t-series regional", "t-series bhakti",
        "zee music company", "zee music", "zee cine", "sony music india", "sony music",
        "tips official", "tips music", "tips", "speed records", "white hill music",
        "saregama", "saregama music", "yrf", "yash raj films", "eros now", "eros music",
        "desi melodies", "desi music factory", "geet mp3", "nav haryanvi", "gringo entertainments",
        "single track studios", "jass records", "saga hits", "times music", "bhojpuri wave",
        "goldmines telefilms", "goldmines", "venus", "ultra bollywood", "ultra music", "pen music",
        "dharma productions", "universal music india", "universal music", "warner music india",
        "warner music", "vevo", "topic", "official channel", "records", "music company",
        "aditya music", "lahari music", "think music india", "think music", "anand audio",
        "muzik247", "svf music", "svf", "red chillies entertainment", "shemaroo",
        "wave music", "t-series apna punjab", "speed punjabi", "dm - desi melodies",
        "lofi vibes", "chill beats", "vibe music", "romantic playlist", "best bollywood songs",
        "bollywood classics", "melody records", "audio junction", "bass boosted", "trap nation",
        "music hub", "top hits", "soundtrack records", "all bollywood", "lofi songs",
        "lofi music", "midnight vibes", "soulful melodies", "bollywood songs", "hindi songs"
    )

    // Version patterns
    private val VERSION_PATTERNS = listOf(
        Regex("(?i)\\b(slowed\\s*[+&]\\s*reverb)\\b") to "Slowed + Reverb",
        Regex("(?i)\\b(slowed\\s+down|slowed)\\b") to "Slowed",
        Regex("(?i)\\b(sped\\s*up|speed\\s*up|nightcore)\\b") to "Sped Up",
        Regex("(?i)\\b(lo-?fi\\s*(?:mix|remake|edit|version)?)\\b") to "Lofi",
        Regex("(?i)\\b(acoustic\\s*(?:version|mix)?|unplugged|stripped)\\b") to "Acoustic",
        Regex("(?i)\\b(remix|club\\s*mix|dj\\s*remix|dance\\s*mix)\\b") to "Remix",
        Regex("(?i)\\b(live\\s*(?:in\\s*concert|version|performance)?)\\b") to "Live",
        Regex("(?i)\\b(instrumental|karaoke)\\b") to "Instrumental",
        Regex("(?i)\\b(cover\\s*(?:song|version)?)\\b") to "Cover"
    )

    /**
     * Determines whether a given string is a known record label, channel, or generic descriptor.
     */
    fun isLabelOrChannel(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT).trim()
        if (lower.isBlank()) return true
        if (lower.length <= 1) return true
        return RECORD_LABELS_AND_CHANNELS.any { lower.contains(it) || it.contains(lower) }
    }

    /**
     * Cleans an artist name of bracketed fluff, quotes, and channel suffixes.
     */
    fun cleanArtistString(raw: String): String {
        return raw
            .replace(Regex("(?i)\\s*-\\s*topic$"), "")
            .replace(Regex("(?i)\\s*vevo$"), "")
            .replace(Regex("(?i)\\s*official$"), "")
            .replace(Regex("(?i)\\[.*?\\]|\\(.*?\\)"), "")
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
    }

    /**
     * Splits compound artist strings into individual artists.
     * Examples: "Pritam, Arijit Singh & Tulsi Kumar", "Badshah feat. Aastha Gill"
     */
    fun splitArtists(raw: String): List<String> {
        val cleaned = cleanArtistString(raw)
        return cleaned.split(Regex("(?i)[,&/|•]|\\band\\b|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bwith\\b|\\bx\\b"))
            .map { cleanArtistString(it) }
            .filter { it.isNotBlank() && !isLabelOrChannel(it) && it.length in 2..50 }
            .distinct()
    }

    /**
     * Intelligently extracts song metadata from title, provided artist, description, and channel.
     */
    fun extract(
        title: String,
        rawArtist: String = "",
        description: String = "",
        album: String = "",
        channelName: String = ""
    ): ExtractedSongMetadata {
        val emojiCleanTitle = SongTitleNormalizer.removeEmojis(title)

        // 1. Detect Version (e.g. Slowed + Reverb, Lofi, Acoustic, Remix)
        var detectedVersion = ""
        for ((pattern, label) in VERSION_PATTERNS) {
            if (pattern.containsMatchIn(emojiCleanTitle)) {
                detectedVersion = label
                break
            }
        }

        // 2. Extract Album / Movie (e.g. From "Brahmastra", Kesariya - Brahmastra | ...)
        var detectedAlbum = album.trim()
        if (detectedAlbum.isBlank()) {
            val fromMovieMatch = Regex("(?i)(?:from|film|movie)\\s+[\"']([^\"']+)[\"']").find(emojiCleanTitle)
            if (fromMovieMatch != null) {
                detectedAlbum = fromMovieMatch.groupValues[1].trim()
            }
        }

        // 3. Extract Artists from Description (YouTube description provides official YouTube Music credits)
        val descriptionArtists = mutableListOf<String>()
        var descMovie = ""
        if (description.isNotBlank()) {
            // "Provided to YouTube by ... \n\n Track · Artist"
            val providedMatcher = Regex("(?i)Provided to YouTube by [^\n\r]+\n\n([^\n\r·]+)·\\s*([^\n\r·\n]+)").find(description)
            if (providedMatcher != null) {
                val art = cleanArtistString(providedMatcher.groupValues[2])
                if (art.isNotBlank() && !isLabelOrChannel(art)) {
                    descriptionArtists.addAll(splitArtists(art))
                }
            }

            // Singer: / Artist: / Vocals:
            val singerPatterns = listOf(
                Regex("(?i)(?:Singer|Singers|Vocals|Artist|Artists|Vocalist)\\s*[:\\-–|]\\s*([^\n\r;|\"]+)"),
                Regex("(?i)(?:Music|Music Director|Composer|Music by)\\s*[:\\-–|]\\s*([^\n\r;|\"]+)")
            )
            for (p in singerPatterns) {
                p.findAll(description).take(3).forEach { m ->
                    val raw = m.groupValues[1]
                    descriptionArtists.addAll(splitArtists(raw))
                }
            }

            // Movie: / Film: / Album:
            if (detectedAlbum.isBlank()) {
                val moviePattern = Regex("(?i)(?:Movie|Film|Album)\\s*[:\\-–|]\\s*([^\n\r;|\"]+)")
                val movieMatch = moviePattern.find(description)
                if (movieMatch != null) {
                    descMovie = movieMatch.groupValues[1].trim()
                }
            }
        }
        if (detectedAlbum.isBlank() && descMovie.isNotBlank()) {
            detectedAlbum = descMovie
        }

        // 4. Candidate Artists from Provided Artist (only if not generic or channel name)
        val validProvidedArtists = mutableListOf<String>()
        if (rawArtist.isNotBlank() && !isLabelOrChannel(rawArtist)) {
            val cleanProvided = cleanArtistString(rawArtist)
            if (cleanProvided.isNotBlank() && !cleanProvided.equals(channelName, ignoreCase = true)) {
                validProvidedArtists.addAll(splitArtists(cleanProvided))
            }
        }

        // 5. Deconstruct Title Segments
        // Split on major separators: "|" or " - "
        val titleSegments = emojiCleanTitle.split(Regex("[|]"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        var candidateTitle = ""
        val titleArtists = mutableListOf<String>()

        if (titleSegments.size > 1) {
            // Check first segment
            val firstSeg = titleSegments[0]
            val restSegments = titleSegments.subList(1, titleSegments.size)

            // See if first segment contains " - " (e.g. "Arijit Singh - Tum Hi Ho")
            if (firstSeg.contains(" - ")) {
                val subParts = firstSeg.split(" - ")
                val p0 = subParts[0].trim()
                val p1 = subParts[1].trim()
                if (isLikelyArtist(p0) && !isLabelOrChannel(p0)) {
                    titleArtists.addAll(splitArtists(p0))
                    candidateTitle = p1
                } else if (isLikelyArtist(p1) && !isLabelOrChannel(p1)) {
                    titleArtists.addAll(splitArtists(p1))
                    candidateTitle = p0
                } else {
                    candidateTitle = p0
                }
            } else {
                candidateTitle = firstSeg
            }

            // Inspect remaining segments for artists, album, or version
            for (seg in restSegments) {
                if (isLabelOrChannel(seg)) continue
                // Check if segment is a movie / album tag
                if (seg.startsWith("From \"", ignoreCase = true) || seg.endsWith("Movie", ignoreCase = true)) {
                    if (detectedAlbum.isBlank()) {
                        detectedAlbum = seg.replace(Regex("(?i)From |Movie|\""), "").trim()
                    }
                    continue
                }
                // Check if segment is version
                if (detectedVersion.isBlank()) {
                    for ((pattern, label) in VERSION_PATTERNS) {
                        if (pattern.containsMatchIn(seg)) {
                            detectedVersion = label
                            break
                        }
                    }
                }
                // Try splitting into artists
                val artistsInSeg = splitArtists(seg)
                if (artistsInSeg.isNotEmpty()) {
                    titleArtists.addAll(artistsInSeg)
                }
            }
        } else {
            // Single segment: Check if it has " - "
            val singleTitle = titleSegments.firstOrNull() ?: emojiCleanTitle
            if (singleTitle.contains(" - ")) {
                val subParts = singleTitle.split(" - ")
                val p0 = subParts[0].trim()
                val p1 = subParts[1].trim()
                if (isLikelyArtist(p0) && !isLabelOrChannel(p0)) {
                    titleArtists.addAll(splitArtists(p0))
                    candidateTitle = p1
                } else if (isLikelyArtist(p1) && !isLabelOrChannel(p1)) {
                    titleArtists.addAll(splitArtists(p1))
                    candidateTitle = p0
                } else {
                    candidateTitle = p0
                }
            } else {
                candidateTitle = singleTitle
            }
        }

        // Also check "feat." in candidate title
        val featMatches = Regex("(?i)[(\\[](?:feat\\.?|ft\\.?|with)\\s+([^)\\]]+)[)\\]]").findAll(candidateTitle)
        for (m in featMatches) {
            titleArtists.addAll(splitArtists(m.groupValues[1]))
        }

        // Clean candidate title of bracketed fluff and promotional words
        var cleanTitle = SongTitleNormalizer.removeVideoFluff(candidateTitle)
            .replace(Regex("(?i)\\[.*?\\]|\\(.*?\\)"), " ")
            .replace(Regex("(?i)\\b(ft\\.?|feat\\.?|featuring)\\s+[^|\\-]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")

        if (cleanTitle.isBlank()) {
            cleanTitle = SongTitleNormalizer.cleanPunctuation(title)
        }

        // Consolidate candidate artists in order of confidence:
        // Description artists > Metadata artists > Title artists
        val allArtists = (descriptionArtists + validProvidedArtists + titleArtists)
            .map { cleanArtistString(it) }
            .filter { it.isNotBlank() && !isLabelOrChannel(it) && it.length in 2..50 }
            .distinct()

        val primaryArtist = allArtists.firstOrNull().orEmpty()
        val featuredArtists = if (allArtists.size > 1) allArtists.subList(1, allArtists.size) else emptyList()
        val isArtistConfident = primaryArtist.isNotBlank() && !isLabelOrChannel(primaryArtist)

        return ExtractedSongMetadata(
            songTitle = cleanTitle,
            primaryArtist = primaryArtist,
            featuredArtists = featuredArtists,
            albumOrMovie = detectedAlbum,
            version = detectedVersion,
            isArtistConfident = isArtistConfident
        )
    }

    /**
     * Heuristic to determine if a string segment is likely an artist name.
     */
    private fun isLikelyArtist(part: String): Boolean {
        val p = part.trim()
        if (p.length !in 3..45) return false
        if (isLabelOrChannel(p)) return false
        val words = p.split(" ")
        // Usually artists have 1 to 4 words and contain typical artist name casing or tokens
        return words.size in 1..4 && !p.contains(Regex("(?i)\\b(full|official|video|audio|movie|lyrics)\\b"))
    }
}
