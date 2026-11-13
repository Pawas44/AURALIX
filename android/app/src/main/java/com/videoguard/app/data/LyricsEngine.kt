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

        // 1. Try direct LRCLib GET endpoint
        try {
            val encodedTitle = URLEncoder.encode(cleanTitle, "UTF-8")
            val encodedArtist = URLEncoder.encode(cleanArtist, "UTF-8")
            var urlStr = "https://lrclib.net/api/get?track_name=$encodedTitle&artist_name=$encodedArtist"
            if (durationSeconds > 0) {
                urlStr += "&duration=$durationSeconds"
            }
            val jsonStr = httpGet(urlStr)
            if (jsonStr.isNotBlank()) {
                val json = JSONObject(jsonStr)
                result = parseLrcResponse(json)
            }
        } catch (e: Exception) {
            // Ignore and try search fallback
        }

        // 2. Try LRCLib search endpoint
        if (result == null || result.lines.isEmpty()) {
            try {
                val query = "$cleanTitle $cleanArtist".trim()
                val encodedQ = URLEncoder.encode(query, "UTF-8")
                val searchUrl = "https://lrclib.net/api/search?q=$encodedQ"
                val jsonStr = httpGet(searchUrl)
                if (jsonStr.isNotBlank() && jsonStr.startsWith("[")) {
                    val arr = JSONArray(jsonStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val parsed = parseLrcResponse(obj)
                        if (parsed != null && (parsed.isSynced || parsed.plainLyrics.isNotBlank())) {
                            result = parsed
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                // Search failed
            }
        }

        memoryCache[cacheKey] = result
        return@withContext result
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
        return name
            .replace(Regex("(?i)\\[.*?\\]|\\(.*?\\)"), "") // remove bracketed text like (Official Video), [Remix]
            .replace(Regex("(?i)ft\\..*|feat\\..*"), "")
            .replace(Regex("(?i)official video|music video|lyric video|audio"), "")
            .trim()
    }

    private fun cleanArtistName(name: String): String {
        return name
            .replace(Regex("(?i) - Topic|Official|VEVO"), "")
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
