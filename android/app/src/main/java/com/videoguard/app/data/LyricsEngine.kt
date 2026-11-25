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

data class LyricLine(
    val timestampMs: Long,
    val text: String,
    val romanizedText: String = ""
)

data class LyricsResult(
    val lines: List<LyricLine>,
    val plainLyrics: String,
    val isSynced: Boolean,
    val isEstimated: Boolean = false,
    val confidenceScore: Int = 100
)

object LyricsEngine {
    private val memoryCache = mutableMapOf<String, LyricsResult?>()

    fun isGenericOrLabel(name: String): Boolean {
        return SongMetadataExtractor.isLabelOrChannel(name)
    }

    fun extractCandidateArtists(title: String, artist: String, description: String = ""): List<String> {
        val metadata = SongMetadataExtractor.extract(title = title, rawArtist = artist, description = description)
        val list = mutableListOf<String>()
        if (metadata.primaryArtist.isNotBlank()) list.add(metadata.primaryArtist)
        list.addAll(metadata.featuredArtists)
        return list.distinct()
    }

    fun extractBestArtist(title: String, artist: String, description: String = ""): String {
        return SongMetadataExtractor.extract(title = title, rawArtist = artist, description = description).primaryArtist
    }

    fun splitArtists(raw: String): List<String> {
        return SongMetadataExtractor.splitArtists(raw)
    }

    fun cleanTrackNameRaw(name: String): String {
        return SongMetadataExtractor.extract(title = name).songTitle
    }

    fun cleanArtistRaw(name: String): String {
        return SongMetadataExtractor.cleanArtistString(name)
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
            val isEstimated = json.optBoolean("isEstimated", false)
            val confidenceScore = json.optInt("confidenceScore", 100)
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
                LyricsResult(
                    lines = lines,
                    plainLyrics = plainLyrics,
                    isSynced = isSynced,
                    isEstimated = isEstimated,
                    confidenceScore = confidenceScore
                )
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
                put("isEstimated", result.isEstimated)
                put("confidenceScore", result.confidenceScore)
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
        context: Context? = null,
        description: String = "",
        channelName: String = "",
        album: String = ""
    ): LyricsResult? = withContext(Dispatchers.IO) {
        // 1. Intelligent Song Metadata Extraction
        val meta = SongMetadataExtractor.extract(
            title = trackTitle,
            rawArtist = artistName,
            description = description,
            album = album,
            channelName = channelName
        )

        val cleanTitle = meta.songTitle
        val primaryArtist = meta.primaryArtist
        val cacheKey = "${cleanTitle.lowercase()}_${primaryArtist.lowercase()}_$videoId"

        // 2. In-memory Cache check
        if (memoryCache.containsKey(cacheKey) && memoryCache[cacheKey] != null) {
            return@withContext memoryCache[cacheKey]
        }

        // 3. Persistent Disk Cache check (offline songs)
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

        var bestCandidate: LyricsResult? = null
        var bestCandidateScore = -1

        // -------------------------------------------------------------
        // Priority 1: Exact Song Name + Actual Artist (GET & Structured)
        // -------------------------------------------------------------
        if (primaryArtist.isNotBlank() && meta.isArtistConfident) {
            // Direct GET from LRCLib
            val direct = tryGetLrc(cleanTitle, primaryArtist, durationSeconds)
            if (direct != null) {
                if (direct.isSynced && direct.confidenceScore >= LyricsMatchScorer.MINIMUM_CONFIDENCE_THRESHOLD) {
                    return@withContext cacheAndReturn(direct)
                }
                if (direct.confidenceScore > bestCandidateScore) {
                    bestCandidate = direct
                    bestCandidateScore = direct.confidenceScore
                }
            }

            // Structured Search on LRCLib (track_name + artist_name)
            val structuredSearch = trySearchLrcStructured(
                cleanTitle = cleanTitle,
                artist = primaryArtist,
                actualDuration = durationSeconds
            )
            if (structuredSearch != null) {
                if (structuredSearch.isSynced && structuredSearch.confidenceScore >= LyricsMatchScorer.MINIMUM_CONFIDENCE_THRESHOLD) {
                    return@withContext cacheAndReturn(structuredSearch)
                }
                if (structuredSearch.confidenceScore > bestCandidateScore) {
                    bestCandidate = structuredSearch
                    bestCandidateScore = structuredSearch.confidenceScore
                }
            }
        }

        // -------------------------------------------------------------
        // Priority 2: Exact Song Name + Artist + Lyrics Query
        // -------------------------------------------------------------
        val allArtistsToTry = mutableListOf<String>()
        if (primaryArtist.isNotBlank()) allArtistsToTry.add(primaryArtist)
        allArtistsToTry.addAll(meta.featuredArtists)

        for (art in allArtistsToTry.distinct()) {
            val q = "$cleanTitle $art".trim()
            val searched = trySearchLrcQuery(
                query = q,
                expectedTitle = cleanTitle,
                expectedArtist = art,
                actualDuration = durationSeconds
            )
            if (searched != null) {
                if (searched.isSynced && searched.confidenceScore >= LyricsMatchScorer.MINIMUM_CONFIDENCE_THRESHOLD) {
                    return@withContext cacheAndReturn(searched)
                }
                if (searched.confidenceScore > bestCandidateScore) {
                    bestCandidate = searched
                    bestCandidateScore = searched.confidenceScore
                }
            }

            // Also try with "Lyrics" appended
            val qLyrics = "$cleanTitle $art Lyrics".trim()
            val searchedLyrics = trySearchLrcQuery(
                query = qLyrics,
                expectedTitle = cleanTitle,
                expectedArtist = art,
                actualDuration = durationSeconds
            )
            if (searchedLyrics != null) {
                if (searchedLyrics.isSynced && searchedLyrics.confidenceScore >= LyricsMatchScorer.MINIMUM_CONFIDENCE_THRESHOLD) {
                    return@withContext cacheAndReturn(searchedLyrics)
                }
                if (searchedLyrics.confidenceScore > bestCandidateScore) {
                    bestCandidate = searchedLyrics
                    bestCandidateScore = searchedLyrics.confidenceScore
                }
            }
        }

        // -------------------------------------------------------------
        // Priority 3: Search using normalized Song Title only
        // (Crucial if artist is unknown or label was stripped)
        // -------------------------------------------------------------
        val searchTitleOnly = trySearchLrcQuery(
            query = cleanTitle,
            expectedTitle = cleanTitle,
            expectedArtist = primaryArtist,
            actualDuration = durationSeconds
        )
        if (searchTitleOnly != null) {
            if (searchTitleOnly.isSynced && searchTitleOnly.confidenceScore >= LyricsMatchScorer.MINIMUM_CONFIDENCE_THRESHOLD) {
                return@withContext cacheAndReturn(searchTitleOnly)
            }
            if (searchTitleOnly.confidenceScore > bestCandidateScore) {
                bestCandidate = searchTitleOnly
                bestCandidateScore = searchTitleOnly.confidenceScore
            }
        }

        // -------------------------------------------------------------
        // Priority 4: YouTube Music Official Native Lyrics
        // -------------------------------------------------------------
        if (videoId.isNotBlank()) {
            val ytmLyrics = fetchYouTubeMusicOfficialLyrics(videoId, durationSeconds)
            if (ytmLyrics != null) {
                return@withContext cacheAndReturn(ytmLyrics)
            }
        }

        // If we found an acceptable candidate (with plain lyrics or fallback sync)
        if (bestCandidate != null && bestCandidateScore >= LyricsMatchScorer.MINIMUM_CONFIDENCE_THRESHOLD) {
            // If candidate is not already timestamped, apply Duration-Aware Sync
            return@withContext if (!bestCandidate.isSynced && bestCandidate.plainLyrics.isNotBlank()) {
                val durationSynced = LyricsSyncEngine.calculateEstimatedSync(bestCandidate.plainLyrics, durationSeconds)
                cacheAndReturn(durationSynced)
            } else {
                cacheAndReturn(bestCandidate)
            }
        }

        // No acceptable match found with adequate confidence
        memoryCache[cacheKey] = null
        return@withContext null
    }

    private fun tryGetLrc(title: String, artist: String, duration: Int): LyricsResult? {
        if (title.isBlank()) return null
        return try {
            val encT = URLEncoder.encode(title, "UTF-8")
            val encA = URLEncoder.encode(artist, "UTF-8")
            var urlStr = "https://lrclib.net/api/get?track_name=$encT&artist_name=$encA"
            if (duration > 0) {
                urlStr += "&duration=$duration"
            }
            val jsonStr = httpGet(urlStr)
            if (jsonStr.isNotBlank() && jsonStr.startsWith("{")) {
                val json = JSONObject(jsonStr)
                val candTitle = json.optString("trackName", "")
                val candArtist = json.optString("artistName", "")
                val candDur = json.optDouble("duration", 0.0).toInt()
                val synced = json.optString("syncedLyrics", "")
                val plain = json.optString("plainLyrics", "")

                val scored = LyricsMatchScorer.scoreCandidate(
                    candidateTitle = candTitle,
                    candidateArtist = candArtist,
                    candidateDurationSeconds = candDur,
                    hasSynced = synced.isNotBlank(),
                    expectedTitle = title,
                    expectedArtist = artist,
                    actualDurationSeconds = duration
                )

                if (scored.isAcceptable) {
                    parseLrcResponse(json, duration, scored.totalConfidenceScore)
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun trySearchLrcStructured(cleanTitle: String, artist: String, actualDuration: Int): LyricsResult? {
        return try {
            val encT = URLEncoder.encode(cleanTitle, "UTF-8")
            val encA = URLEncoder.encode(artist, "UTF-8")
            val urlStr = "https://lrclib.net/api/search?track_name=$encT&artist_name=$encA"
            evaluateSearchJson(urlStr, cleanTitle, artist, actualDuration)
        } catch (e: Exception) {
            null
        }
    }

    private fun trySearchLrcQuery(query: String, expectedTitle: String, expectedArtist: String, actualDuration: Int): LyricsResult? {
        return try {
            val encQ = URLEncoder.encode(query, "UTF-8")
            val urlStr = "https://lrclib.net/api/search?q=$encQ"
            evaluateSearchJson(urlStr, expectedTitle, expectedArtist, actualDuration)
        } catch (e: Exception) {
            null
        }
    }

    private fun evaluateSearchJson(
        urlStr: String,
        expectedTitle: String,
        expectedArtist: String,
        actualDuration: Int
    ): LyricsResult? {
        val jsonStr = httpGet(urlStr)
        if (jsonStr.isBlank() || !jsonStr.startsWith("[")) return null
        val arr = JSONArray(jsonStr)

        var bestObj: JSONObject? = null
        var highestScore = -1

        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val candTitle = obj.optString("trackName", "")
            val candArtist = obj.optString("artistName", "")
            val candDur = obj.optDouble("duration", 0.0).toInt()
            val synced = obj.optString("syncedLyrics", "")
            val plain = obj.optString("plainLyrics", "")

            if (synced.isBlank() && plain.isBlank()) continue

            val scored = LyricsMatchScorer.scoreCandidate(
                candidateTitle = candTitle,
                candidateArtist = candArtist,
                candidateDurationSeconds = candDur,
                hasSynced = synced.isNotBlank(),
                expectedTitle = expectedTitle,
                expectedArtist = expectedArtist,
                actualDurationSeconds = actualDuration
            )

            if (scored.isAcceptable && scored.totalConfidenceScore > highestScore) {
                highestScore = scored.totalConfidenceScore
                bestObj = obj
            }
        }

        return if (bestObj != null && highestScore >= LyricsMatchScorer.MINIMUM_CONFIDENCE_THRESHOLD) {
            parseLrcResponse(bestObj, actualDuration, highestScore)
        } else null
    }

    fun parseCustomLrc(synced: String, plain: String): LyricsResult {
        if (synced.isNotBlank()) {
            val lines = LyricsSyncEngine.parseLrcText(synced)
            if (lines.isNotEmpty()) {
                return LyricsResult(
                    lines = lines,
                    plainLyrics = plain,
                    isSynced = true,
                    isEstimated = false,
                    confidenceScore = 100
                )
            }
        }
        return autoSyncPlainLyrics(plain, 0)
    }

    private fun parseLrcResponse(json: JSONObject, durationSeconds: Int = 0, confidenceScore: Int = 100): LyricsResult? {
        val synced = json.optString("syncedLyrics", "")
        val plain = json.optString("plainLyrics", "")

        if (synced.isNotBlank()) {
            val lines = LyricsSyncEngine.parseLrcText(synced)
            if (lines.isNotEmpty()) {
                return LyricsResult(
                    lines = lines,
                    plainLyrics = plain,
                    isSynced = true,
                    isEstimated = false,
                    confidenceScore = confidenceScore
                )
            }
        }

        if (plain.isNotBlank()) {
            val durationResult = LyricsSyncEngine.calculateEstimatedSync(plain, durationSeconds)
            return durationResult.copy(confidenceScore = confidenceScore)
        }

        return null
    }

    fun autoSyncPlainLyrics(plain: String, durationSeconds: Int): LyricsResult {
        return LyricsSyncEngine.calculateEstimatedSync(plain, durationSeconds)
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
                    val synced = LyricsSyncEngine.calculateEstimatedSync(lyricsText, durationSeconds)
                    return synced.copy(confidenceScore = 80)
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
