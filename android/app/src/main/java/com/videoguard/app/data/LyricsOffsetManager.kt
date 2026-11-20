package com.videoguard.app.data

import android.content.Context
import android.content.SharedPreferences

data class LyricsSearchResultItem(
    val id: Long,
    val trackName: String,
    val artistName: String,
    val durationSeconds: Int,
    val isSynced: Boolean,
    val syncedLyrics: String,
    val plainLyrics: String
)

object LyricsOffsetManager {
    private const val PREFS_NAME = "auralix_lyrics_offsets"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun getOffsetMs(videoId: String): Long {
        return prefs?.getLong("offset_$videoId", 0L) ?: 0L
    }

    fun setOffsetMs(videoId: String, offsetMs: Long) {
        prefs?.edit()?.putLong("offset_$videoId", offsetMs)?.apply()
    }

    fun adjustOffsetMs(videoId: String, deltaMs: Long): Long {
        val current = getOffsetMs(videoId)
        val updated = (current + deltaMs).coerceIn(-10000L, 10000L)
        setOffsetMs(videoId, updated)
        return updated
    }

    fun resetOffset(videoId: String) {
        prefs?.edit()?.remove("offset_$videoId")?.apply()
    }

    /**
     * Search LRCLib for custom alternatives when the user clicks "Wrong lyrics?"
     */
    suspend fun searchAlternativeLyrics(query: String): List<LyricsSearchResultItem> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val list = mutableListOf<LyricsSearchResultItem>()
        if (query.isBlank()) return@withContext list
        try {
            val encodedQ = java.net.URLEncoder.encode(query.trim(), "UTF-8")
            val urlStr = "https://lrclib.net/api/search?q=$encodedQ"
            val jsonStr = httpGet(urlStr)
            if (jsonStr.isNotBlank() && jsonStr.startsWith("[")) {
                val arr = org.json.JSONArray(jsonStr)
                for (i in 0 until arr.length().coerceAtMost(10)) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optLong("id", 0L)
                    val tName = obj.optString("trackName", "")
                    val aName = obj.optString("artistName", "")
                    val dur = obj.optDouble("duration", 0.0).toInt()
                    val syn = obj.optString("syncedLyrics", "")
                    val pl = obj.optString("plainLyrics", "")
                    if (syn.isNotBlank() || pl.isNotBlank()) {
                        list.add(
                            LyricsSearchResultItem(
                                id = id,
                                trackName = tName,
                                artistName = aName,
                                durationSeconds = dur,
                                isSynced = syn.isNotBlank(),
                                syncedLyrics = syn,
                                plainLyrics = pl
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext list
    }

    private fun httpGet(urlStr: String): String {
        var conn: java.net.HttpURLConnection? = null
        return try {
            val url = java.net.URL(urlStr)
            conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "AURALIX/1.0 (Android)")
            }
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else ""
        } catch (e: Exception) {
            ""
        } finally {
            conn?.disconnect()
        }
    }
}
