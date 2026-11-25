package com.videoguard.app.data

import java.util.regex.Pattern

/**
 * Handles duration-aware synchronization of lyrics:
 * 1. Parses standard timestamped LRC lyrics.
 * 2. Implements intelligent fallback synchronization for plain unsynced lyrics
 *    based on musical pacing, syllable/line length, intro/outro allowances,
 *    and stanza boundaries.
 */
object LyricsSyncEngine {

    private val LRC_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2}(?:\\.\\d+)?)\\](.*)")

    /**
     * Parses standard LRC text into a list of [LyricLine]s.
     */
    fun parseLrcText(lrc: String): List<LyricLine> {
        val list = mutableListOf<LyricLine>()
        for (rawLine in lrc.lines()) {
            val trimmed = rawLine.trim()
            val matcher = LRC_PATTERN.matcher(trimmed)
            if (matcher.matches()) {
                val min = matcher.group(1)?.toLongOrNull() ?: 0L
                val secStr = matcher.group(2) ?: "0"
                val secFloat = secStr.toFloatOrNull() ?: 0f
                val ms = (min * 60 * 1000L) + (secFloat * 1000).toLong()
                val text = matcher.group(3)?.trim() ?: ""

                // Filter out LRC header tags like [ar:Singer], [ti:Title], [al:Album]
                if (text.startsWith("[") && text.endsWith("]")) continue

                val roman = if (TransliterationEngine.canRomanize(text)) {
                    TransliterationEngine.romanize(text)
                } else ""

                list.add(LyricLine(timestampMs = ms, text = text, romanizedText = roman))
            }
        }
        return list.sortedBy { it.timestampMs }
    }

    /**
     * Duration-Aware Fallback Synchronization for Plain Lyrics.
     *
     * Calculates musical pacing using:
     * - Song total duration
     * - Intro delay allowance (singing starts after instrumental intro)
     * - Outro padding (song fades out after last line)
     * - Line length and word count weights
     * - Stanza break pauses (extra rest time between verses/choruses)
     */
    fun calculateEstimatedSync(plainLyrics: String, durationSeconds: Int): LyricsResult {
        val rawParagraphs = plainLyrics.split(Regex("(?:\r?\n){2,}"))
        data class LineWithPause(val text: String, val isParagraphEnd: Boolean)

        val processedLines = mutableListOf<LineWithPause>()
        for (para in rawParagraphs) {
            val linesInPara = para.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("[") && !it.startsWith("Source:") && !it.contains("Lyrics") }

            for (i in linesInPara.indices) {
                val isLastInPara = (i == linesInPara.lastIndex)
                processedLines.add(LineWithPause(linesInPara[i], isLastInPara))
            }
        }

        if (processedLines.isEmpty()) {
            return LyricsResult(
                lines = emptyList(),
                plainLyrics = plainLyrics,
                isSynced = false,
                isEstimated = false
            )
        }

        // If duration is unknown or too short, return unsynced lines at timestamp 0
        if (durationSeconds <= 30) {
            val unsyncedLines = processedLines.map {
                val roman = if (TransliterationEngine.canRomanize(it.text)) TransliterationEngine.romanize(it.text) else ""
                LyricLine(timestampMs = 0L, text = it.text, romanizedText = roman)
            }
            return LyricsResult(
                lines = unsyncedLines,
                plainLyrics = plainLyrics,
                isSynced = false,
                isEstimated = false
            )
        }

        val totalMs = durationSeconds * 1000L

        // Musical Timing Heuristics:
        // 1. Intro delay: 6% to 9% of total song time (clamped between 6,000ms and 18,000ms)
        val introMs = (totalMs * 0.07f).toLong().coerceIn(6000L, 18000L)

        // 2. Outro padding: 5% to 8% of total song time (clamped between 5,000ms and 15,000ms)
        val outroMs = (totalMs * 0.06f).toLong().coerceIn(5000L, 15000L)

        // 3. Singing Window
        val singingMs = (totalMs - introMs - outroMs).coerceAtLeast(10000L)

        // 4. Calculate relative weights for each line
        // Weight = word count * 1.5 + char count * 0.2 + (if paragraph end, + 2.5 for musical interlude)
        val weights = processedLines.map { lineItem ->
            val words = lineItem.text.split(Regex("\\s+")).filter { it.isNotBlank() }.size.coerceAtLeast(1)
            val chars = lineItem.text.length
            var w = (words * 1.6f) + (chars * 0.15f)
            if (lineItem.isParagraphEnd) {
                w += 2.8f // Musical pause between stanzas
            }
            w.coerceAtLeast(1.0f)
        }

        val totalWeight = weights.sum()

        // 5. Distribute singingMs across lines proportionally
        val resultLines = mutableListOf<LyricLine>()
        var currentOffsetMs = introMs

        for (i in processedLines.indices) {
            val item = processedLines[i]
            val lineDurationMs = ((weights[i] / totalWeight) * singingMs).toLong()

            val roman = if (TransliterationEngine.canRomanize(item.text)) {
                TransliterationEngine.romanize(item.text)
            } else ""

            resultLines.add(
                LyricLine(
                    timestampMs = currentOffsetMs,
                    text = item.text,
                    romanizedText = roman
                )
            )

            currentOffsetMs += lineDurationMs
        }

        return LyricsResult(
            lines = resultLines,
            plainLyrics = plainLyrics,
            isSynced = true,
            isEstimated = true,
            confidenceScore = 75
        )
    }
}
