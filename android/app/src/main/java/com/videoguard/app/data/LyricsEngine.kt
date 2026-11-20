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
    val text: String,
    val romanizedText: String = ""
)

data class LyricsResult(
    val lines: List<LyricLine>,
    val plainLyrics: String,
    val isSynced: Boolean
)

object LyricsEngine {
    private val memoryCache = mutableMapOf<String, LyricsResult?>()

    private val RECORD_LABELS = setOf(
        "t-series", "tseries", "t series", "zee music", "sony music", "tips", "speed records",
        "white hill", "saregama", "yrf", "yash raj", "eros", "desi melodies", "desi music factory",
        "geet mp3", "nav haryanvi", "gringo", "single track", "jass records", "saga hits",
        "times music", "bhojpuri", "goldmines", "venus", "ultra", "pen music", "dharma",
        "universal music", "warner music", "vevo", "topic", "official", "records", "music",
        "channel", "production", "films"
    )

    private fun isGenericOrLabel(name: String): Boolean {
        val lower = name.lowercase().trim()
        if (lower.isBlank()) return true
        return RECORD_LABELS.any { lower.contains(it) }
    }

    suspend fun getLyrics(
        trackTitle: String,
        artistName: String,
        durationSeconds: Int = 0,
        videoId: String = ""
    ): LyricsResult? = withContext(Dispatchers.IO) {
        val cleanTitle = cleanTrackNameRaw(trackTitle)
        val cleanArtist = cleanArtistRaw(artistName)
        val cacheKey = "${cleanTitle.lowercase()}_${cleanArtist.lowercase()}_$videoId"

        if (memoryCache.containsKey(cacheKey)) {
            return@withContext memoryCache[cacheKey]
        }

        // Build list of smart search pairs (Title to Artist)
        val variants = mutableListOf<Pair<String, String>>()
        if (cleanTitle.isNotBlank()) {
            variants.add(cleanTitle to cleanArtist)
        }

        // Split by " - " to test both Track/Artist and Artist/Track combinations
        if (trackTitle.contains(" - ")) {
            val parts = trackTitle.split(" - ")
            if (parts.size >= 2) {
                val p0 = cleanTrackNameRaw(parts[0])
                val p1 = cleanTrackNameRaw(parts[1])
                if (p0.isNotBlank() && p1.isNotBlank()) {
                    variants.add(p0 to p1)
                    variants.add(p1 to p0)
                }
            }
        }

        // Split by "|" e.g. "Barsaat | Banjaare, Roni"
        if (trackTitle.contains("|")) {
            val parts = trackTitle.split("|")
            if (parts.size >= 2) {
                val p0 = cleanTrackNameRaw(parts[0])
                val p1 = cleanArtistRaw(parts[1])
                if (p0.isNotBlank()) {
                    variants.add(p0 to p1)
                }
            }
        }

        // Title alone (essential when artist is a record label like T-Series or Zee Music)
        if (cleanTitle.isNotBlank()) {
            variants.add(cleanTitle to "")
        }

        val distinctVariants = variants.distinct()
        var bestCandidate: LyricsResult? = null

        // Pass 1: Seek true Synced (.lrc) lyrics across all variants
        for ((title, artist) in distinctVariants) {
            val effectiveArtist = if (isGenericOrLabel(artist)) "" else artist

            // 1a. Direct LRCLib GET (exact track & artist)
            if (effectiveArtist.isNotBlank()) {
                val direct = tryGetLrc(title, effectiveArtist, durationSeconds)
                if (direct != null && direct.isSynced) {
                    memoryCache[cacheKey] = direct
                    return@withContext direct
                } else if (direct != null && bestCandidate == null) {
                    bestCandidate = direct
                }
            }

            // 1b. LRCLib Search: query = title + artist
            val qWithArtist = if (effectiveArtist.isNotBlank()) "$title $effectiveArtist".trim() else title
            val searched = trySearchLrc(qWithArtist, title, effectiveArtist, durationSeconds)
            if (searched != null && searched.isSynced) {
                memoryCache[cacheKey] = searched
                return@withContext searched
            } else if (searched != null && bestCandidate == null) {
                bestCandidate = searched
            }

            // 1c. LRCLib Search: query = title alone (high hit rate for songs with record labels or feature artists)
            if (effectiveArtist.isNotBlank()) {
                val searchTitleOnly = trySearchLrc(title, title, "", durationSeconds)
                if (searchTitleOnly != null && searchTitleOnly.isSynced) {
                    memoryCache[cacheKey] = searchTitleOnly
                    return@withContext searchTitleOnly
                } else if (searchTitleOnly != null && bestCandidate == null) {
                    bestCandidate = searchTitleOnly
                }
            }
        }

        // Pass 2: Fallback to Official YouTube Music Native Lyrics
        if (videoId.isNotBlank()) {
            val ytmLyrics = fetchYouTubeMusicOfficialLyrics(videoId, durationSeconds)
            if (ytmLyrics != null) {
                memoryCache[cacheKey] = ytmLyrics
                return@withContext ytmLyrics
            }
        }

        // Pass 3: If only plain lyrics were found, auto-sync them against the song duration
        if (bestCandidate != null) {
            val autoSynced = autoSyncPlainLyrics(bestCandidate.plainLyrics, durationSeconds)
            memoryCache[cacheKey] = autoSynced
            return@withContext autoSynced
        }

        memoryCache[cacheKey] = null
        return@withContext null
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
                parseLrcResponse(json, duration)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun trySearchLrc(query: String, expectedTitle: String, expectedArtist: String, durationSeconds: Int = 0): LyricsResult? {
        return try {
            val encodedQ = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://lrclib.net/api/search?q=$encodedQ"
            val jsonStr = httpGet(searchUrl)
            if (jsonStr.isNotBlank() && jsonStr.startsWith("[")) {
                val arr = JSONArray(jsonStr)
                var bestCandidateJson: JSONObject? = null
                var bestScore = -1

                val expTitleClean = expectedTitle.lowercase().trim()
                val expArtistClean = expectedArtist.lowercase().trim()
                val isGeneric = isGenericOrLabel(expArtistClean)

                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val resTrack = obj.optString("trackName", "").lowercase().trim()
                    val resArtist = obj.optString("artistName", "").lowercase().trim()

                    // Title must have significant overlap
                    val titleMatches = resTrack.contains(expTitleClean) || expTitleClean.contains(resTrack) ||
                            shareSignificantWord(resTrack, expTitleClean)

                    if (!titleMatches) continue

                    // Artist validation: if artist is not generic, ensure it doesn't match a completely different artist
                    var artistMatches = true
                    if (!isGeneric && expArtistClean.isNotBlank()) {
                        artistMatches = resArtist.contains(expArtistClean) || expArtistClean.contains(resArtist) ||
                                shareSignificantWord(resArtist, expArtistClean)
                    }

                    if (!artistMatches) {
                        continue
                    }

                    val hasSynced = obj.optString("syncedLyrics", "").isNotBlank()
                    val hasPlain = obj.optString("plainLyrics", "").isNotBlank()
                    if (!hasSynced && !hasPlain) continue

                    var score = 0
                    if (hasSynced) score += 60 // Heavily favor synced lyrics
                    if (hasPlain) score += 5
                    if (resArtist == expArtistClean && !isGeneric) score += 30
                    if (resTrack == expTitleClean) score += 30

                    // DURATION MATCHING: Ensures lyrics match the exact cut/mix of the song
                    val candidateDur = obj.optDouble("duration", 0.0).toInt()
                    if (durationSeconds > 0 && candidateDur > 0) {
                        val diff = kotlin.math.abs(candidateDur - durationSeconds)
                        if (diff <= 3) {
                            score += 50 // Perfect duration match
                        } else if (diff <= 8) {
                            score += 25
                        } else if (diff > 30) {
                            score -= 35
                        }
                    }

                    if (score > bestScore) {
                        bestScore = score
                        bestCandidateJson = obj
                    }
                }

                if (bestCandidateJson != null) {
                    return parseLrcResponse(bestCandidateJson, durationSeconds)
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

    /**
     * Converts plain lyrics text into accurately-spaced auto-synchronized karaoke lyrics
     * based on natural vocal phrasing, syllable weight, and total track duration.
     */
    fun autoSyncPlainLyrics(plain: String, durationSeconds: Int): LyricsResult {
        val rawLines = plain.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("[") && !it.startsWith("Source:") && !it.contains("Lyrics") }

        if (rawLines.isEmpty()) {
            return LyricsResult(lines = emptyList(), plainLyrics = plain, isSynced = false)
        }

        val totalMs = (if (durationSeconds > 0) durationSeconds else 210) * 1000L
        val introMs = (totalMs * 0.08f).toLong().coerceIn(4000L, 16000L)
        val outroMs = (totalMs * 0.08f).toLong().coerceIn(4000L, 16000L)
        val singingSpanMs = (totalMs - introMs - outroMs).coerceAtLeast(10000L)

        val weights = rawLines.map { line ->
            val charWeight = line.length.coerceIn(8, 60).toFloat()
            val wordWeight = line.split(Regex("\\s+")).size.toFloat() * 2.5f
            charWeight + wordWeight
        }
        val totalWeight = weights.sum().coerceAtLeast(1f)

        var currentMs = introMs
        val resultLines = mutableListOf<LyricLine>()

        for (i in rawLines.indices) {
            val lineText = rawLines[i]
            val lineDurationMs = ((weights[i] / totalWeight) * singingSpanMs).toLong().coerceIn(2000L, 8500L)
            val roman = if (TransliterationEngine.canRomanize(lineText)) TransliterationEngine.romanize(lineText) else ""

            resultLines.add(
                LyricLine(
                    timestampMs = currentMs,
                    text = lineText,
                    romanizedText = roman
                )
            )
            currentMs += lineDurationMs
        }

        return LyricsResult(lines = resultLines, plainLyrics = plain, isSynced = true)
    }

    fun parseCustomLrc(synced: String, plain: String): LyricsResult {
        if (synced.isNotBlank()) {
            val lines = parseLrcText(synced)
            if (lines.isNotEmpty()) {
                return LyricsResult(lines = lines, plainLyrics = plain, isSynced = true)
            }
        }
        return autoSyncPlainLyrics(plain, 0)
    }

    private fun parseLrcResponse(json: JSONObject, durationSeconds: Int = 0): LyricsResult? {
        val synced = json.optString("syncedLyrics", "")
        val plain = json.optString("plainLyrics", "")

        if (synced.isNotBlank()) {
            val lines = parseLrcText(synced)
            if (lines.isNotEmpty()) {
                return LyricsResult(lines = lines, plainLyrics = plain, isSynced = true)
            }
        }

        if (plain.isNotBlank()) {
            return autoSyncPlainLyrics(plain, durationSeconds)
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
                val roman = if (TransliterationEngine.canRomanize(text)) TransliterationEngine.romanize(text) else ""
                list.add(LyricLine(timestampMs = ms, text = text, romanizedText = roman))
            }
        }
        return list.sortedBy { it.timestampMs }
    }

    fun cleanTrackNameRaw(name: String): String {
        return name
            .replace(Regex("(?i)\\[.*?\\]|\\(.*?\\)"), "") // remove bracketed text
            .replace(Regex("(?i)\\b(official music video|official video|music video|lyric video|lyrical video|visualizer|official audio|audio|video|4k|hd|1080p|remastered|lyrics|prod\\. by.*|full song|full video)\\b"), "")
            .replace(Regex("(?i)\\b(ft\\.?|feat\\.?|featuring)\\s+[^|\\-]+"), "")
            .replace(Regex("\\|.*$"), "")
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
    }

    fun cleanArtistRaw(name: String): String {
        return name
            .replace(Regex("(?i) - Topic|Official|VEVO"), "")
            .replace(Regex("(?i)\\[.*?\\]|\\(.*?\\)"), "")
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
    }

    private fun fetchYouTubeMusicOfficialLyrics(videoId: String, durationSeconds: Int = 0): LyricsResult? {
        if (videoId.isBlank()) return null
        return try {
            val nextPayload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20231214.01.00")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("videoId", videoId)
            }
            val nextResStr = httpPostJson("https://music.youtube.com/youtubei/v1/next", nextPayload.toString())
            if (nextResStr.isBlank()) return null
            val nextJson = JSONObject(nextResStr)
            val tabs = nextJson.optJSONObject("contents")
                ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
                ?.optJSONObject("tabbedRenderer")
                ?.optJSONObject("watchNextTabbedResultsRenderer")
                ?.optJSONArray("tabs") ?: return null

            var browseId: String? = null
            var params: String? = null

            for (i in 0 until tabs.length()) {
                val tab = tabs.optJSONObject(i)?.optJSONObject("tabRenderer") ?: continue
                val ep = tab.optJSONObject("endpoint")?.optJSONObject("browseEndpoint")
                val bId = ep?.optString("browseId") ?: ""
                val title = tab.optString("title", "").lowercase()
                if (bId.startsWith("MPLYt") || title.contains("lyrics")) {
                    browseId = bId
                    params = ep?.optString("params")
                    break
                }
            }

            if (browseId.isNullOrBlank() && tabs.length() > 1) {
                val tab = tabs.optJSONObject(1)?.optJSONObject("tabRenderer")
                val ep = tab?.optJSONObject("endpoint")?.optJSONObject("browseEndpoint")
                browseId = ep?.optString("browseId")
                params = ep?.optString("params")
            }

            if (browseId.isNullOrBlank()) return null

            val browsePayload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20231214.01.00")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("browseId", browseId)
                if (!params.isNullOrBlank()) {
                    put("params", params)
                }
            }

            val browseResStr = httpPostJson("https://music.youtube.com/youtubei/v1/browse", browsePayload.toString())
            if (browseResStr.isBlank()) return null
            val browseJson = JSONObject(browseResStr)
            val contents = browseJson.optJSONObject("contents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return null

            for (i in 0 until contents.length()) {
                val item = contents.optJSONObject(i) ?: continue
                val shelf = item.optJSONObject("musicDescriptionShelfRenderer") ?: continue
                val runs = shelf.optJSONObject("description")?.optJSONArray("runs") ?: continue
                val sb = StringBuilder()
                for (r in 0 until runs.length()) {
                    sb.append(runs.optJSONObject(r)?.optString("text") ?: "")
                }
                val lyricsText = sb.toString().trim()
                if (lyricsText.isNotBlank()) {
                    return autoSyncPlainLyrics(lyricsText, durationSeconds)
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun httpPostJson(urlStr: String, jsonBody: String): String {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            conn.setRequestProperty("Referer", "https://music.youtube.com/")
            conn.outputStream.use { os ->
                os.write(jsonBody.toByteArray(Charsets.UTF_8))
            }
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
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
