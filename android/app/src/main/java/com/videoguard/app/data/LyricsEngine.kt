package com.videoguard.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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

    fun getLyricsFromDisk(context: Context, videoId: String): LyricsResult? {
        if (videoId.isBlank()) return null
        return try {
            val dir = File(context.filesDir, "lyrics_cache")
            val file = File(dir, "$videoId.json")
            if (!file.exists()) return null
            val raw = file.readText()
            val json = JSONObject(raw)
            val isSynced = json.optBoolean("isSynced", false)
            val plainLyrics = json.optString("plainLyrics", "")
            val linesArray = json.optJSONArray("lines") ?: JSONArray()
            val lines = mutableListOf<LyricLine>()
            for (i in 0 until linesArray.length()) {
                val lineObj = linesArray.getJSONObject(i)
                lines.add(
                    LyricLine(
                        timestampMs = lineObj.optLong("timestampMs", 0L),
                        text = lineObj.optString("text", ""),
                        romanizedText = lineObj.optString("romanizedText", "")
                    )
                )
            }
            if (lines.isNotEmpty() || plainLyrics.isNotBlank()) {
                LyricsResult(lines = lines, plainLyrics = plainLyrics, isSynced = isSynced)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun saveLyricsToDisk(context: Context, videoId: String, result: LyricsResult) {
        if (videoId.isBlank()) return
        try {
            val dir = File(context.filesDir, "lyrics_cache")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "$videoId.json")
            val json = JSONObject().apply {
                put("isSynced", result.isSynced)
                put("plainLyrics", result.plainLyrics)
                val linesArray = JSONArray()
                for (line in result.lines) {
                    val lineObj = JSONObject().apply {
                        put("timestampMs", line.timestampMs)
                        put("text", line.text)
                        put("romanizedText", line.romanizedText)
                    }
                    linesArray.put(lineObj)
                }
                put("lines", linesArray)
            }
            file.writeText(json.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getLyrics(
        trackTitle: String,
        artistName: String,
        durationSeconds: Int = 0,
        videoId: String = "",
        context: Context? = null
    ): LyricsResult? = withContext(Dispatchers.IO) {
        val cleanTitle = cleanTrackNameRaw(trackTitle)
        val cleanArtist = cleanArtistRaw(artistName)
        val cacheKey = "${cleanTitle.lowercase()}_${cleanArtist.lowercase()}_$videoId"

        // 1. In-memory Cache check
        if (memoryCache.containsKey(cacheKey) && memoryCache[cacheKey] != null) {
            return@withContext memoryCache[cacheKey]
        }

        // 2. Persistent Disk Cache check (Crucial for offline downloaded songs!)
        if (context != null && videoId.isNotBlank()) {
            val fromDisk = getLyricsFromDisk(context, videoId)
            if (fromDisk != null) {
                memoryCache[cacheKey] = fromDisk
                return@withContext fromDisk
            }
        }

        val cacheAndReturn: (LyricsResult) -> LyricsResult = { res ->
            memoryCache[cacheKey] = res
            if (context != null && videoId.isNotBlank()) {
                saveLyricsToDisk(context, videoId, res)
            }
            res
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

        // Split by "|" e.g. "Barsaat | Banjaare, Roni" or "Song | Movie | Actor | Singer"
        if (trackTitle.contains("|")) {
            val parts = trackTitle.split("|").map { it.trim() }
            val p0 = cleanTrackNameRaw(parts[0])
            for (i in 1 until parts.size) {
                val segArtist = cleanArtistRaw(parts[i])
                if (segArtist.isNotBlank() && !isGenericOrLabel(segArtist)) {
                    variants.add(p0 to segArtist)
                }
            }
        }

        // Split by "by" or "feat" e.g. "Song by Singer" or "Song feat. Singer"
        if (trackTitle.contains(" by ", ignoreCase = true)) {
            val parts = trackTitle.split(Regex("(?i)\\s+by\\s+"))
            if (parts.size >= 2) {
                val p0 = cleanTrackNameRaw(parts[0])
                val p1 = cleanArtistRaw(parts[1])
                if (p0.isNotBlank() && p1.isNotBlank()) {
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
                    return@withContext cacheAndReturn(direct)
                } else if (direct != null && bestCandidate == null) {
                    bestCandidate = direct
                }
            }

            // 1b. LRCLib Search: query = title + artist
            val qWithArtist = if (effectiveArtist.isNotBlank()) "$title $effectiveArtist".trim() else title
            val searched = trySearchLrc(qWithArtist, title, effectiveArtist, durationSeconds)
            if (searched != null && searched.isSynced) {
                return@withContext cacheAndReturn(searched)
            } else if (searched != null && bestCandidate == null) {
                bestCandidate = searched
            }

            // 1c. LRCLib Search: query = title alone (high hit rate for songs with record labels or feature artists)
            if (effectiveArtist.isNotBlank()) {
                val searchTitleOnly = trySearchLrc(title, title, "", durationSeconds)
                if (searchTitleOnly != null && searchTitleOnly.isSynced) {
                    return@withContext cacheAndReturn(searchTitleOnly)
                } else if (searchTitleOnly != null && bestCandidate == null) {
                    bestCandidate = searchTitleOnly
                }
            }
        }

        // Pass 2: Fallback to Official YouTube Music Native Lyrics
        if (videoId.isNotBlank()) {
            val ytmLyrics = fetchYouTubeMusicOfficialLyrics(videoId, durationSeconds)
            if (ytmLyrics != null) {
                return@withContext cacheAndReturn(ytmLyrics)
            }
        }

        // Pass 3: If only plain lyrics were found, auto-sync them against the song duration
        if (bestCandidate != null) {
            val autoSynced = autoSyncPlainLyrics(bestCandidate.plainLyrics, durationSeconds)
            return@withContext cacheAndReturn(autoSynced)
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

                    // Title must have strong match (exact, containment, or high significant word overlap)
                    val titleMatches = titleMatchesWell(resTrack, expTitleClean)
                    if (!titleMatches) continue

                    // Artist validation: if artist is specified, ensure it doesn't match an entirely different artist
                    var artistMatches = true
                    if (!isGeneric && expArtistClean.isNotBlank()) {
                        artistMatches = resArtist.contains(expArtistClean) || expArtistClean.contains(resArtist) ||
                                titleMatchesWell(resArtist, expArtistClean)
                    }

                    if (!artistMatches) {
                        continue
                    }

                    val hasSynced = obj.optString("syncedLyrics", "").isNotBlank()
                    val hasPlain = obj.optString("plainLyrics", "").isNotBlank()
                    if (!hasSynced && !hasPlain) continue

                    // DURATION MATCHING: Reject songs with large duration discrepancy (>20s) to avoid wrong cuts/mixes
                    val candidateDur = obj.optDouble("duration", 0.0).toInt()
                    if (durationSeconds > 0 && candidateDur > 0) {
                        val diff = kotlin.math.abs(candidateDur - durationSeconds)
                        if (diff > 20) {
                            continue
                        }
                    }

                    var score = 0
                    if (hasSynced) score += 70 // Strongly prefer real synced lyrics
                    if (hasPlain) score += 5
                    if (resArtist == expArtistClean && !isGeneric) score += 40
                    if (resTrack == expTitleClean) score += 40

                    if (durationSeconds > 0 && candidateDur > 0) {
                        val diff = kotlin.math.abs(candidateDur - durationSeconds)
                        if (diff <= 3) {
                            score += 60 // Exact duration match
                        } else if (diff <= 8) {
                            score += 30
                        } else {
                            score -= (diff * 2)
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

    private fun titleMatchesWell(s1: String, s2: String): Boolean {
        val clean1 = s1.lowercase().trim()
        val clean2 = s2.lowercase().trim()
        if (clean1 == clean2) return true
        if (clean1.contains(clean2) || clean2.contains(clean1)) return true

        val stopwords = setOf("the", "and", "official", "audio", "video", "feat", "with", "prod", "song", "lyrics")
        val w1 = clean1.split(Regex("[\\s\\-_,./&|()]+")).map { it.trim() }.filter { it.length > 2 && it !in stopwords }
        val w2 = clean2.split(Regex("[\\s\\-_,./&|()]+")).map { it.trim() }.filter { it.length > 2 && it !in stopwords }

        if (w1.isEmpty() || w2.isEmpty()) return false
        val common = w1.count { it in w2 }
        val minLen = minOf(w1.size, w2.size)
        return common >= 2 || (minLen == 1 && common == 1) || (common.toFloat() / minLen >= 0.6f)
    }

    /**
     * Converts plain lyrics text into readable, scrollable lyric lines.
     * Note: Marked as isSynced = false so the UI presents them as clean readable text
     * rather than jumping ahead/behind on artificial mathematical guesses!
     */
    fun autoSyncPlainLyrics(plain: String, durationSeconds: Int): LyricsResult {
        val rawLines = plain.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("[") && !it.startsWith("Source:") && !it.contains("Lyrics") }

        if (rawLines.isEmpty()) {
            return LyricsResult(lines = emptyList(), plainLyrics = plain, isSynced = false)
        }

        val resultLines = rawLines.map { lineText ->
            val roman = if (TransliterationEngine.canRomanize(lineText)) TransliterationEngine.romanize(lineText) else ""
            LyricLine(timestampMs = 0L, text = lineText, romanizedText = roman)
        }
        return LyricsResult(lines = resultLines, plainLyrics = plain, isSynced = false)
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
