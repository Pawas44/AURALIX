package com.videoguard.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

data class LyricsResult(
    val lines: List<LyricLine>,
    val plainLyrics: String,
    val isSynced: Boolean
)

object LyricsEngine {
    private val memoryCache = mutableMapOf<String, LyricsResult?>()

    suspend fun getLyrics(
        trackTitle: String,
        artistName: String,
        durationSeconds: Int = 0
    ): LyricsResult? = withContext(Dispatchers.IO) {
        val cleanTitle = cleanTrackName(trackTitle)
        val cleanArtist = cleanArtistName(artistName)
        val cacheKey = "${cleanTitle.lowercase()}_${cleanArtist.lowercase()}"

        if (memoryCache.containsKey(cacheKey)) {
            return@withContext memoryCache[cacheKey]
        }

        var result: LyricsResult? = null

        // 1. Direct LRCLib GET endpoint
        result = tryGetLrc(cleanTitle, cleanArtist, durationSeconds)

        // 1b. If YouTube title has "Artist - Track" format
        if ((result == null || result.lines.isEmpty()) && trackTitle.contains(" - ")) {
            val parts = trackTitle.split(" - ")
            if (parts.size >= 2) {
                val guessedArtist = cleanArtistName(parts[0])
                val guessedTitle = cleanTrackName(parts[1])
                result = tryGetLrc(guessedTitle, guessedArtist, durationSeconds)
            }
        }

        // 2. LRCLib Search: query = title + artist
        if (result == null || result.lines.isEmpty()) {
            val q = "$cleanTitle $cleanArtist".trim()
            if (q.isNotBlank()) {
                result = trySearchLrc(q, cleanTitle, cleanArtist)
            }
        }

        // 3. LRCLib Search: query = title only (Strictly verified against expected artist/title)
        if (result == null || result.lines.isEmpty()) {
            if (cleanTitle.isNotBlank()) {
                result = trySearchLrc(cleanTitle, cleanTitle, cleanArtist)
            }
        }

        memoryCache[cacheKey] = result
        return@withContext result
    }

    private fun tryGetLrc(title: String, artist: String, duration: Int): LyricsResult? {
        if (title.isBlank()) return null
        return try {
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            val encodedArtist = URLEncoder.encode(artist, "UTF-8")
            var urlStr = "https://lrclib.net/api/get?track_name=$encodedTitle&artist_name=$encodedArtist"
            if (duration > 0) {
                urlStr += "&duration=$duration"
            }
            val jsonStr = httpGet(urlStr)
            if (jsonStr.isNotBlank() && jsonStr.startsWith("{")) {
                val json = JSONObject(jsonStr)
                parseLrcResponse(json)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun trySearchLrc(query: String, expectedTitle: String, expectedArtist: String): LyricsResult? {
        return try {
            val encodedQ = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://lrclib.net/api/search?q=$encodedQ"
            val jsonStr = httpGet(searchUrl)
            if (jsonStr.isNotBlank() && jsonStr.startsWith("[")) {
                val arr = JSONArray(jsonStr)
                var bestCandidate: JSONObject? = null
                var bestScore = -1

                val expTitleClean = expectedTitle.lowercase().trim()
                val expArtistClean = expectedArtist.lowercase().trim()

                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val resTrack = obj.optString("trackName", "").lowercase().trim()
                    val resArtist = obj.optString("artistName", "").lowercase().trim()

                    // Title must have significant overlap
                    val titleMatches = resTrack.contains(expTitleClean) || expTitleClean.contains(resTrack) ||
                            shareSignificantWord(resTrack, expTitleClean)

                    if (!titleMatches) continue

                    // Artist validation: if we have an expected artist, ensure results do NOT belong to an unrelated artist
                    val isGenericArtist = expArtistClean.isBlank() || expArtistClean.contains("topic") || expArtistClean.contains("vevo")
                    var artistMatches = true
                    if (!isGenericArtist) {
                        artistMatches = resArtist.contains(expArtistClean) || expArtistClean.contains(resArtist) ||
                                shareSignificantWord(resArtist, expArtistClean)
                    }

                    if (!artistMatches) {
                        // Do NOT use lyrics from a completely different artist!
                        continue
                    }

                    val hasSynced = obj.optString("syncedLyrics", "").isNotBlank()
                    val hasPlain = obj.optString("plainLyrics", "").isNotBlank()
                    if (!hasSynced && !hasPlain) continue

                    var score = 0
                    if (hasSynced) score += 20
                    if (hasPlain) score += 5
                    if (resArtist == expArtistClean) score += 30
                    if (resTrack == expTitleClean) score += 30

                    if (score > bestScore) {
                        bestScore = score
                        bestCandidate = obj
                    }
                }

                if (bestCandidate != null) {
                    return parseLrcResponse(bestCandidate)
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun shareSignificantWord(s1: String, s2: String): Boolean {
        val stopwords = setOf("the", "and", "official", "audio", "video", "feat", "with", "prod")
        val w1 = s1.split(Regex("[\\s\\-_,./&|()]+")).map { it.trim().lowercase() }.filter { it.length > 2 && it !in stopwords }
        val w2 = s2.split(Regex("[\\s\\-_,./&|()]+")).map { it.trim().lowercase() }.filter { it.length > 2 && it !in stopwords }
        return w1.any { it in w2 }
    }

    private fun parseLrcResponse(json: JSONObject): LyricsResult? {
        val synced = json.optString("syncedLyrics", "")
        val plain = json.optString("plainLyrics", "")

        if (synced.isNotBlank()) {
            val lines = parseLrcText(synced)
            if (lines.isNotEmpty()) {
                return LyricsResult(lines = lines, plainLyrics = plain, isSynced = true)
            }
        }

        if (plain.isNotBlank()) {
            val plainLines = plain.lines().mapIndexed { idx, line ->
                LyricLine(timestampMs = idx * 3000L, text = line.trim())
            }.filter { it.text.isNotBlank() }
            return LyricsResult(lines = plainLines, plainLyrics = plain, isSynced = false)
        }

        return null
    }

    private fun parseLrcText(lrc: String): List<LyricLine> {
        val list = mutableListOf<LyricLine>()
        val pattern = Pattern.compile("\\[(\\d{2}):(\\d{2}(?:\\.\\d+)?)\\](.*)")
        for (rawLine in lrc.lines()) {
            val matcher = pattern.matcher(rawLine.trim())
            if (matcher.matches()) {
                val min = matcher.group(1)?.toLongOrNull() ?: 0L
                val secStr = matcher.group(2) ?: "0"
                val secFloat = secStr.toFloatOrNull() ?: 0f
                val ms = (min * 60 * 1000L) + (secFloat * 1000).toLong()
                val text = matcher.group(3)?.trim() ?: ""
                list.add(LyricLine(timestampMs = ms, text = text))
            }
        }
        return list.sortedBy { it.timestampMs }
    }

    private fun cleanTrackName(name: String): String {
        var res = name
            .replace(Regex("(?i)\\[.*?\\]|\\(.*?\\)"), "") // remove bracketed text
            .replace(Regex("(?i)\\b(official music video|official video|music video|lyric video|visualizer|official audio|audio|video|4k|hd|remastered|lyrics|prod\\. by.*)\\b"), "")
            .replace(Regex("(?i)\\b(ft\\.?|feat\\.?|featuring)\\s+[^|\\-]+"), "")
            .replace(Regex("\\|.*$"), "")
            .trim()
        if (res.contains(" - ")) {
            val parts = res.split(" - ")
            if (parts.size >= 2) {
                res = parts[1].trim()
            }
        }
        return res.trim().removeSurrounding("\"").removeSurrounding("'")
    }

    private fun cleanArtistName(name: String): String {
        return name
            .replace(Regex("(?i) - Topic|Official|VEVO"), "")
            .replace(Regex("(?i)\\[.*?\\]|\\(.*?\\)"), "")
            .trim()
    }

    private fun httpGet(urlStr: String): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 4000
        conn.readTimeout = 4000
        conn.setRequestProperty("User-Agent", "AURALIX-Music/1.0 (https://github.com/Pawas44/AURALIX)")
        return if (conn.responseCode in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            ""
        }
    }
}
