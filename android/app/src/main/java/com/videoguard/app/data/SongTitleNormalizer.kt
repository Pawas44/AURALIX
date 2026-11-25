package com.videoguard.app.data

import java.util.Locale

/**
 * Normalizes song titles, artist names, and search queries.
 * Handles emoji removal, promotional fluff stripping, Hindi/Urdu Romanization phonetic variants,
 * and text similarity calculation.
 */
object SongTitleNormalizer {

    private val EMOJI_REGEX = Regex("[\\p{So}\\p{Cn}\\p{Cs}\\p{Sc}\\p{Sk}\\p{Sm}]|[\uD83C-\uDBFF\uDC00-\uDFFF]+")
    private val MULTI_SPACE_REGEX = Regex("\\s+")
    private val PUNCTUATION_REGEX = Regex("[^a-zA-Z0-9\\s\u0900-\u097F\u0A00-\u0A7F\u0600-\u06FF]") // Preserves Devanagari, Gurmukhi, Arabic/Urdu

    // Common promotional and video fluff tokens
    private val VIDEO_FLUFF_PATTERNS = listOf(
        Regex("(?i)\\b(official music video|official video|music video|lyric video|lyrical video|visualizer|audio video)\\b"),
        Regex("(?i)\\b(official audio|original audio|full audio|studio audio)\\b"),
        Regex("(?i)\\b(full song|full video|complete song|title track|full track)\\b"),
        Regex("(?i)\\b(4k ultra hd|4k uhd|4k video|4k|1080p|720p|hd video|hd|uhd)\\b"),
        Regex("(?i)\\b(remastered|remaster|digitally remastered|dolby atmos|spatial audio)\\b"),
        Regex("(?i)\\b(lyrics|with lyrics|lyric|lyrics video|song lyrics)\\b"),
        Regex("(?i)\\b(best bollywood songs?|romantic songs?|love songs?|trending songs?|hit songs?|sad songs?)\\b"),
        Regex("(?i)\\b(best songs?|latest songs?|new songs?|top hits?|playlist|jukebox)\\b"),
        Regex("(?i)\\b(prod(?:uced)?\\.?\\s+by\\s+[^|\\-]+)"),
        Regex("(?i)\\b(directed by\\s+[^|\\-]+)"),
        Regex("(?i)\\b(choreography by\\s+[^|\\-]+)"),
        Regex("(?i)\\b(teaser|trailer|promo|glimpse|sneak peek)\\b"),
        Regex("(?i)\\b(status|whatsapp status|fullscreen status|shorts|reels)\\b"),
        Regex("(?i)#[a-zA-Z0-9_]+"), // Hashtags
        Regex("(?i)\\b(20[12][0-9]|19[89][0-9])\\b") // Years like 2024, 2023, 2022, 1999
    )

    /**
     * Removes all emojis from the given text.
     */
    fun removeEmojis(text: String): String {
        return EMOJI_REGEX.replace(text, " ").trim()
    }

    /**
     * Strips promotional and video clutter from titles.
     */
    fun removeVideoFluff(text: String): String {
        var clean = text
        for (pattern in VIDEO_FLUFF_PATTERNS) {
            clean = pattern.replace(clean, " ")
        }
        return clean
    }

    /**
     * Normalizes punctuation and excessive whitespace.
     */
    fun cleanPunctuation(text: String): String {
        return PUNCTUATION_REGEX.replace(text, " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
    }

    /**
     * Normalizes Hindi/Punjabi/Urdu Romanized spelling variants so that
     * queries like "Tumhi Ho" / "Tum Hi Ho" or "Kahani Suno" / "Kahani Sunoo"
     * match seamlessly.
     */
    fun normalizeRomanizedPhonetics(text: String): String {
        var s = text.lowercase(Locale.ROOT)
        // Common double vowel collapses: aa -> a, ee -> i, oo -> u
        s = s.replace(Regex("aa+"), "a")
        s = s.replace(Regex("ee+"), "i")
        s = s.replace(Regex("oo+"), "u")
        // Common consonant digraph variants: kh -> k, gh -> g, ph -> f, th -> t, bh -> b, dh -> d
        s = s.replace("kh", "k")
        s = s.replace("gh", "g")
        s = s.replace("ph", "f")
        s = s.replace("th", "t")
        s = s.replace("bh", "b")
        s = s.replace("dh", "d")
        s = s.replace("sh", "s")
        s = s.replace("z", "j") // z/j interchangeability in Hindi-Urdu transliterations (e.g. Zara / Jara)
        s = s.replace("v", "w") // v/w interchangeability
        return s.trim()
    }

    /**
     * Prepares a string for searching lyrics APIs.
     * Keeps letters, numbers, spaces, and removes extraneous noise.
     */
    fun normalizeForSearch(text: String): String {
        val withoutEmoji = removeEmojis(text)
        val withoutFluff = removeVideoFluff(withoutEmoji)
        val clean = withoutFluff
            .replace(Regex("(?i)\\[.*?\\]|\\(.*?\\)"), " ") // remove bracketed metadata
            .replace(Regex("(?i)\\b(ft\\.?|feat\\.?|featuring)\\s+[^|\\-]+"), " ")
            .replace(Regex("[|/•&\\-_]+"), " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
        return clean
    }

    /**
     * Prepares a string for exact or fuzzy comparison.
     */
    fun normalizeForMatching(text: String): String {
        val withoutEmoji = removeEmojis(text)
        val withoutFluff = removeVideoFluff(withoutEmoji)
        return cleanPunctuation(withoutFluff).lowercase(Locale.ROOT)
    }

    /**
     * Calculates Levenshtein distance between two strings.
     */
    fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,       // deletion
                    dp[i][j - 1] + 1,       // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        return dp[len1][len2]
    }

    /**
     * Returns a similarity ratio from 0.0 to 1.0 using a combination of
     * Levenshtein ratio, Token Set Ratio, and Phonetic normalization.
     */
    fun calculateSimilarity(s1: String, s2: String): Float {
        val n1 = normalizeForMatching(s1)
        val n2 = normalizeForMatching(s2)

        if (n1 == n2) return 1.0f
        if (n1.isBlank() || n2.isBlank()) return 0.0f

        // Exact containment check
        if (n1.contains(n2) || n2.contains(n1)) {
            val minLen = minOf(n1.length, n2.length).toFloat()
            val maxLen = maxOf(n1.length, n2.length).toFloat()
            val containmentScore = minLen / maxLen
            if (containmentScore >= 0.7f) return containmentScore
        }

        // Token Set Ratio
        val tokens1 = n1.split(" ").filter { it.length > 1 }.toSet()
        val tokens2 = n2.split(" ").filter { it.length > 1 }.toSet()

        val intersection = tokens1.intersect(tokens2).size
        val union = tokens1.union(tokens2).size

        val jaccard = if (union > 0) intersection.toFloat() / union.toFloat() else 0.0f

        // Levenshtein Ratio
        val maxLen = maxOf(n1.length, n2.length)
        val levDist = levenshteinDistance(n1, n2)
        val levRatio = 1.0f - (levDist.toFloat() / maxLen.toFloat())

        // Phonetic comparison for Hindi/Urdu/Punjabi
        val p1 = normalizeRomanizedPhonetics(n1)
        val p2 = normalizeRomanizedPhonetics(n2)
        val phoneticRatio = if (p1 == p2) 1.0f else {
            val pLev = levenshteinDistance(p1, p2)
            val pMax = maxOf(p1.length, p2.length)
            if (pMax > 0) 1.0f - (pLev.toFloat() / pMax.toFloat()) else 0.0f
        }

        return maxOf(jaccard, levRatio, phoneticRatio)
    }
}
