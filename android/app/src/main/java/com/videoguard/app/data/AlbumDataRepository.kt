package com.videoguard.app.data

import com.videoguard.app.model.UserPlaylist
import com.videoguard.app.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.net.URL
import java.net.HttpURLConnection

data class AlbumItem(
    val id: String,
    val title: String,
    val artist: String,
    val coverUrl: String,
    val year: String = "2024",
    val trackCount: Int = 0,
    val releaseType: String = "Album", // "Album" or "Mixtape" or "EP"
    val tracks: List<VideoItem> = emptyList(),
    val isCurated: Boolean = false
)

object AlbumDataRepository {

    val curatedReleases: List<AlbumItem> = listOf(
        AlbumItem(
            id = "album_glory",
            title = "GLORY",
            artist = "Yo Yo Honey Singh",
            coverUrl = "https://i.scdn.co/image/ab67616d0000b273760bc21008f1b621e25e4f45",
            year = "2024",
            trackCount = 18,
            releaseType = "Album",
            isCurated = true
        ),
        AlbumItem(
            id = "album_moosetape",
            title = "Moosetape",
            artist = "Sidhu Moose Wala",
            coverUrl = "https://i.scdn.co/image/ab67616d0000b273e34b9d0738e44fa789b7eb50",
            year = "2021",
            trackCount = 32,
            releaseType = "Mixtape",
            isCurated = true
        ),
        AlbumItem(
            id = "album_making_memories",
            title = "Making Memories",
            artist = "Karan Aujla, Ikky",
            coverUrl = "https://i.scdn.co/image/ab67616d0000b273bc3674681329b3f9ff43e2f9",
            year = "2023",
            trackCount = 9,
            releaseType = "Album",
            isCurated = true
        ),
        AlbumItem(
            id = "album_four_you",
            title = "Four You",
            artist = "Karan Aujla",
            coverUrl = "https://i.scdn.co/image/ab67616d0000b273752e5057b2cb7980ffca3260",
            year = "2023",
            trackCount = 4,
            releaseType = "EP",
            isCurated = true
        ),
        AlbumItem(
            id = "album_still_here",
            title = "Still Here",
            artist = "KR\$NA",
            coverUrl = "https://i.scdn.co/image/ab67616d0000b27393d6d539828d54157e841261",
            year = "2021",
            trackCount = 9,
            releaseType = "Album",
            isCurated = true
        ),
        AlbumItem(
            id = "album_hard_drive",
            title = "Hard Drive Vol. 1",
            artist = "Raftaar",
            coverUrl = "https://i.scdn.co/image/ab67616d0000b2733cba73be6be9bfec6f642646",
            year = "2022",
            trackCount = 7,
            releaseType = "Mixtape",
            isCurated = true
        ),
        AlbumItem(
            id = "album_rockstar",
            title = "Rockstar",
            artist = "A. R. Rahman, Mohit Chauhan",
            coverUrl = "https://i.scdn.co/image/ab67616d0000b273a7f80db27d5320c0f86eeff0",
            year = "2011",
            trackCount = 14,
            releaseType = "Album",
            isCurated = true
        ),
        AlbumItem(
            id = "album_animal",
            title = "Animal",
            artist = "Pritam, Vishal Mishra, Manan",
            coverUrl = "https://i.scdn.co/image/ab67616d0000b27318ff24f5a34e8f85eeecb510",
            year = "2023",
            trackCount = 14,
            releaseType = "Album",
            isCurated = true
        ),
        AlbumItem(
            id = "album_ghost",
            title = "Ghost",
            artist = "Diljit Dosanjh",
            coverUrl = "https://i.scdn.co/image/ab67616d0000b27301daec6faae61ba7f3295c2f",
            year = "2023",
            trackCount = 21,
            releaseType = "Album",
            isCurated = true
        ),
        AlbumItem(
            id = "album_open_letter",
            title = "Open Letter",
            artist = "Talha Anjum, Umair",
            coverUrl = "https://i.scdn.co/image/ab67616d0000b273618146aaecf3e09886fdf06a",
            year = "2023",
            trackCount = 15,
            releaseType = "Album",
            isCurated = true
        ),
        AlbumItem(
            id = "album_aashiqui2",
            title = "Aashiqui 2",
            artist = "Mithoon, Ankit Tiwari, Arijit Singh",
            coverUrl = "https://i.scdn.co/image/ab67616d0000b273cb181fa39fb39366e6eb293e",
            year = "2013",
            trackCount = 11,
            releaseType = "Album",
            isCurated = true
        ),
        AlbumItem(
            id = "album_karam",
            title = "Karam",
            artist = "KSHMR",
            coverUrl = "https://i.scdn.co/image/ab67616d0000b273860bb4f7960fa0a37e170da5",
            year = "2023",
            trackCount = 14,
            releaseType = "Album",
            isCurated = true
        ),
        AlbumItem(
            id = "album_starboy",
            title = "Starboy",
            artist = "The Weeknd",
            coverUrl = "https://i.scdn.co/image/ab67616d0000b2734718e2b124f79258be7bc452",
            year = "2016",
            trackCount = 18,
            releaseType = "Album",
            isCurated = true
        ),
        AlbumItem(
            id = "album_after_hours",
            title = "After Hours",
            artist = "The Weeknd",
            coverUrl = "https://i.scdn.co/image/ab67616d0000b2738863bc11d2aa12b54f5aeb36",
            year = "2020",
            trackCount = 14,
            releaseType = "Album",
            isCurated = true
        )
    )

    fun getUserAlbums(): List<AlbumItem> {
        val all = mutableListOf<VideoItem>()
        UserDataRepository.playlists.value.forEach { all.addAll(it.videos) }
        UserDataRepository.history.value.forEach { all.add(it) }

        val grouped = all
            .filter { it.album.isNotBlank() && !it.album.equals("Single", ignoreCase = true) && !it.album.equals("Unknown", ignoreCase = true) }
            .groupBy { "${it.album.trim().lowercase()}:::${it.artist.trim().lowercase()}" }

        return grouped.map { (_, group) ->
            val first = group.first()
            val albumName = first.album.trim()
            val isMixtape = albumName.contains("tape", ignoreCase = true) ||
                            albumName.contains("mix", ignoreCase = true) ||
                            albumName.contains("vol", ignoreCase = true)
            AlbumItem(
                id = "user_album_${albumName.hashCode()}",
                title = albumName,
                artist = first.artist.ifBlank { first.channelName },
                coverUrl = first.thumbnailUrl,
                year = "2024",
                trackCount = group.distinctBy { it.id }.size,
                releaseType = if (isMixtape) "Mixtape" else "Album",
                tracks = group.distinctBy { it.id },
                isCurated = false
            )
        }
    }

    fun getAllAlbums(): List<AlbumItem> {
        val userAlbums = getUserAlbums()
        val userAlbumTitles = userAlbums.map { it.title.lowercase() }.toSet()
        val remainingCurated = curatedReleases.filter { it.title.lowercase() !in userAlbumTitles }
        return (userAlbums + remainingCurated).distinctBy { it.id }
    }

    fun getMixtapes(): List<AlbumItem> {
        return getAllAlbums().filter {
            it.releaseType.equals("Mixtape", ignoreCase = true) ||
            it.title.contains("tape", ignoreCase = true) ||
            it.title.contains("mix", ignoreCase = true)
        }
    }

    fun getPureAlbums(): List<AlbumItem> {
        return getAllAlbums().filter {
            !it.releaseType.equals("Mixtape", ignoreCase = true) &&
            !it.title.contains("tape", ignoreCase = true)
        }
    }

    private const val INNERTUBE_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"

    suspend fun searchAlbums(query: String): List<AlbumItem> = withContext(Dispatchers.IO) {
        val outList = mutableListOf<AlbumItem>()
        if (query.isBlank()) return@withContext getAllAlbums()

        try {
            val endpoint = "https://music.youtube.com/youtubei/v1/search?key=$INNERTUBE_KEY"
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240101.01.00")
                        put("hl", "en")
                        put("gl", "IN")
                    })
                })
                put("query", query)
                put("params", "EgWKAQIYAWoKEAkQChAFEAMQBA==")
            }

            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                setRequestProperty("Origin", "https://music.youtube.com")
                setRequestProperty("Referer", "https://music.youtube.com/")
                setRequestProperty("X-YouTube-Client-Name", "67")
                setRequestProperty("X-YouTube-Client-Version", "1.20240101.01.00")
            }

            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode in 200..299) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                if (text.isNotBlank()) {
                    val root = JSONObject(text)
                    val tabs = root.optJSONObject("contents")
                        ?.optJSONObject("tabbedSearchResultsRenderer")
                        ?.optJSONArray("tabs")
                    val tab0 = tabs?.optJSONObject(0)?.optJSONObject("tabRenderer")?.optJSONObject("content")
                    val shelves = tab0?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
                    if (shelves != null) {
                        for (sIdx in 0 until shelves.length()) {
                            val items = shelves.optJSONObject(sIdx)?.optJSONObject("musicShelfRenderer")?.optJSONArray("contents") ?: continue
                            for (i in 0 until items.length()) {
                                val itObj = items.optJSONObject(i)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                                val cols = itObj.optJSONArray("flexColumns") ?: continue
                                if (cols.length() == 0) continue

                                val runs0 = cols.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                    ?.optJSONObject("text")?.optJSONArray("runs")
                                val title = runs0?.optJSONObject(0)?.optString("text", "")?.trim() ?: ""
                                if (title.isBlank()) continue

                                val itStr = itObj.toString()
                                val pidRegex = Regex(""""playlistId":\s*"([^"]+)"""")
                                val pidMatch = pidRegex.find(itStr)
                                val pid = pidMatch?.groupValues?.get(1) ?: ""

                                val thumbs = itObj.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
                                    ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                                val thumb = if (thumbs != null && thumbs.length() > 0) {
                                    thumbs.optJSONObject(thumbs.length() - 1)?.optString("url", "") ?: ""
                                } else ""

                                var relType = "Album"
                                var year = "2024"
                                val artists = mutableListOf<String>()

                                if (cols.length() > 1) {
                                    val runs1 = cols.optJSONObject(1)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                        ?.optJSONObject("text")?.optJSONArray("runs")
                                    if (runs1 != null) {
                                        for (r in 0 until runs1.length()) {
                                            val t = runs1.optJSONObject(r)?.optString("text", "")?.trim() ?: ""
                                            if (t == "•" || t == "," || t == "&" || t.isBlank()) continue
                                            when {
                                                t.equals("Album", ignoreCase = true) ||
                                                t.equals("EP", ignoreCase = true) ||
                                                t.equals("Single", ignoreCase = true) ||
                                                t.equals("Mixtape", ignoreCase = true) -> relType = t
                                                t.matches(Regex("^\\d{4}$")) -> year = t
                                                t.length > 1 -> artists.add(t)
                                            }
                                        }
                                    }
                                }

                                val artistName = if (artists.isNotEmpty()) artists.joinToString(", ") else query
                                outList.add(
                                    AlbumItem(
                                        id = if (pid.isNotBlank()) pid else "album_${kotlin.math.abs(title.hashCode())}",
                                        title = title,
                                        artist = artistName,
                                        coverUrl = thumb,
                                        year = year,
                                        releaseType = relType
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (outList.isNotEmpty()) {
            outList.distinctBy { it.id }
        } else {
            getAllAlbums().filter {
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true)
            }
        }
    }

    suspend fun getOrFetchAlbumTracks(album: AlbumItem): List<VideoItem> = withContext(Dispatchers.IO) {
        if (album.tracks.isNotEmpty()) return@withContext album.tracks

        // If it's a real YouTube Music album playlist (OLAK5uy_...)
        if (album.id.startsWith("OLAK5uy_")) {
            val plTracks = YouTubeSyncEngine.fetchPlaylistVideos(album.id)
            if (plTracks.isNotEmpty()) {
                return@withContext plTracks.map {
                    it.copy(
                        album = album.title,
                        artist = if (it.artist.isNotBlank() && it.artist != "YouTube Music") it.artist else album.artist
                    )
                }
            }
        }

        val query = "${album.title} ${album.artist} full album songs"
        val results = YouTubeSearchEngine.searchVideos(query, isMusicOnly = true)
        results.map {
            it.copy(
                album = album.title,
                artist = album.artist
            )
        }
    }

    fun toUserPlaylist(album: AlbumItem): UserPlaylist {
        return UserPlaylist(
            id = album.id,
            title = album.title,
            description = "${album.releaseType.uppercase()} • ${album.artist} • ${album.year} • ${if (album.trackCount > 0) "${album.trackCount} tracks" else "Full Album"}",
            videos = album.tracks,
            isSystem = false,
            iconType = "album",
            customCoverUrl = album.coverUrl
        )
    }
}
