package com.videoguard.app.data

import java.util.Locale

/**
 * Classifies song titles and YouTube candidates into version categories
 * to enforce strict version matching (e.g. preventing live concert or cover
 * versions from replacing original studio recordings).
 */
enum class SongVersionType {
    OFFICIAL_AUDIO,        // Original studio recording, official audio, topic release
    OFFICIAL_MUSIC_VIDEO,  // Official music video containing studio audio
    LABEL_UPLOAD,          // Legitimate label audio release
    STANDARD_UPLOAD,       // Regular clean song upload
    LYRICS_VIDEO,          // Video containing song lyrics text
    LIVE_PERFORMANCE,      // Concert, live show, stage performance
    ACOUSTIC_VERSION,      // Acoustic, stripped down
    COVER_VERSION,         // Fan cover, tribute, alternate rendition
    REMIX_MASHUP,          // Remix, club mix, mashup, DJ version
    SLOWED_REVERB,         // Slowed, reverb, lofi, sped up, nightcore
    INSTRUMENTAL_KARAOKE,  // Instrumental, karaoke, backing track
    DISQUALIFIED           // Podcast, reaction, review, trailer, teaser
}

object SongVersionClassifier {

    private val DISQUALIFIED_REGEX = Regex(
        "(?i)\\b(reaction|reacting|interview|podcast|review|vlog|behind the scenes|making of|teaser|trailer|promo|glimpse)\\b"
    )

    private val LIVE_REGEX = Regex(
        "(?i)\\b(live|concert|tour|live performance|live from|unplugged|stage show|performing live|on stage|in concert)\\b"
    )

    private val COVER_REGEX = Regex(
        "(?i)\\b(cover|tribute|rendition|covered by|cover song)\\b"
    )

    private val REMIX_REGEX = Regex(
        "(?i)\\b(remix|club mix|mashup|dj remix|dance mix|extended mix|reprise|electronic mix)\\b"
    )

    private val SLOWED_REVERB_REGEX = Regex(
        "(?i)\\b(slowed|reverb|slowed\\s*[+&]\\s*reverb|lo-?fi|sped up|speed up|nightcore)\\b"
    )

    private val INSTRUMENTAL_REGEX = Regex(
        "(?i)\\b(instrumental|karaoke|backing track|minus one)\\b"
    )

    private val OFFICIAL_AUDIO_REGEX = Regex(
        "(?i)\\b(official audio|original audio|full audio|studio audio|studio version|title track)\\b"
    )

    private val OFFICIAL_VIDEO_REGEX = Regex(
        "(?i)\\b(official video|official music video|music video)\\b"
    )

    private val LYRICS_REGEX = Regex(
        "(?i)\\b(lyrics|lyric video|lyrical video|with lyrics|song lyrics)\\b"
    )

    private val ACOUSTIC_REGEX = Regex(
        "(?i)\\b(acoustic|stripped|acoustic version)\\b"
    )

    /**
     * Classifies a song title and channel into a [SongVersionType].
     */
    fun classify(title: String, channelName: String = ""): SongVersionType {
        val combined = "$title $channelName".lowercase(Locale.ROOT)

        if (DISQUALIFIED_REGEX.containsMatchIn(combined)) {
            return SongVersionType.DISQUALIFIED
        }

        if (LIVE_REGEX.containsMatchIn(combined)) {
            return SongVersionType.LIVE_PERFORMANCE
        }

        if (COVER_REGEX.containsMatchIn(combined)) {
            return SongVersionType.COVER_VERSION
        }

        if (REMIX_REGEX.containsMatchIn(combined)) {
            return SongVersionType.REMIX_MASHUP
        }

        if (SLOWED_REVERB_REGEX.containsMatchIn(combined)) {
            return SongVersionType.SLOWED_REVERB
        }

        if (INSTRUMENTAL_REGEX.containsMatchIn(combined)) {
            return SongVersionType.INSTRUMENTAL_KARAOKE
        }

        if (OFFICIAL_AUDIO_REGEX.containsMatchIn(combined)) {
            return SongVersionType.OFFICIAL_AUDIO
        }

        if (OFFICIAL_VIDEO_REGEX.containsMatchIn(combined)) {
            return SongVersionType.OFFICIAL_MUSIC_VIDEO
        }

        if (ACOUSTIC_REGEX.containsMatchIn(combined)) {
            return SongVersionType.ACOUSTIC_VERSION
        }

        if (LYRICS_REGEX.containsMatchIn(combined)) {
            return SongVersionType.LYRICS_VIDEO
        }

        return SongVersionType.STANDARD_UPLOAD
    }

    /**
     * Determines whether the candidate version matches the expected target version.
     * E.g. If the target song is NOT a live concert, live candidates are penalized.
     * If the target song IS a live concert, live candidates are rewarded!
     */
    fun evaluateVersionCompatibility(
        targetVersion: SongVersionType,
        candidateVersion: SongVersionType
    ): Int {
        // Disqualified candidates get fatal penalty
        if (candidateVersion == SongVersionType.DISQUALIFIED) {
            return -200
        }

        // Exact version match
        if (targetVersion == candidateVersion) {
            return when (targetVersion) {
                SongVersionType.OFFICIAL_AUDIO -> 40
                SongVersionType.OFFICIAL_MUSIC_VIDEO -> 32
                SongVersionType.LIVE_PERFORMANCE -> 35
                SongVersionType.REMIX_MASHUP -> 35
                SongVersionType.ACOUSTIC_VERSION -> 35
                else -> 20
            }
        }

        // When target is standard or official audio:
        val isTargetStandardStudio = targetVersion == SongVersionType.STANDARD_UPLOAD ||
                targetVersion == SongVersionType.OFFICIAL_AUDIO ||
                targetVersion == SongVersionType.OFFICIAL_MUSIC_VIDEO

        if (isTargetStandardStudio) {
            return when (candidateVersion) {
                SongVersionType.OFFICIAL_AUDIO -> 38
                SongVersionType.OFFICIAL_MUSIC_VIDEO -> 28
                SongVersionType.STANDARD_UPLOAD -> 15
                SongVersionType.LYRICS_VIDEO -> -25 // Acceptable only as last-resort fallback
                SongVersionType.LIVE_PERFORMANCE -> -75 // Severe penalty for live concert uploads!
                SongVersionType.COVER_VERSION -> -75 // Severe penalty for cover uploads!
                SongVersionType.REMIX_MASHUP -> -60 // Penalty for remix uploads!
                SongVersionType.SLOWED_REVERB -> -60
                SongVersionType.INSTRUMENTAL_KARAOKE -> -65
                SongVersionType.ACOUSTIC_VERSION -> -40
                SongVersionType.DISQUALIFIED -> -200
                SongVersionType.LABEL_UPLOAD -> 25
            }
        }

        // Target specifies a special version (e.g. target is Live):
        if (targetVersion == SongVersionType.LIVE_PERFORMANCE) {
            return if (candidateVersion == SongVersionType.LIVE_PERFORMANCE) 40 else -30
        }

        if (targetVersion == SongVersionType.REMIX_MASHUP) {
            return if (candidateVersion == SongVersionType.REMIX_MASHUP) 40 else -30
        }

        if (targetVersion == SongVersionType.ACOUSTIC_VERSION) {
            return if (candidateVersion == SongVersionType.ACOUSTIC_VERSION) 40 else -30
        }

        return 0
    }
}
