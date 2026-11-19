package com.videoguard.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.videoguard.app.model.VideoItem
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.regex.Pattern
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class RemotePlaylistSummary(
    val id: String,
    val title: String,
    val description: String,
    val trackCount: Int,
    val coverUrl: String?,
    val sourceProvider: String = "Spotify"
)

object SpotifyConnectEngine {

    const val DEFAULT_CLIENT_ID = "271077752670460c918ef93949f506e7"
    const val REDIRECT_URI = "https://open.spotify.com/"

    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private const val WEB = "https://open.spotify.com"
    private const val PATHFINDER = "https://api-partner.spotify.com/pathfinder/v2/query"

    private const val PREFS_NAME = "spotify_connect_prefs"
    private const val KEY_SP_DC = "sp_dc_cookie"
    private const val KEY_DISPLAY_NAME = "user_display_name"
    private const val KEY_CACHED_PLAYLISTS = "cached_playlists_json"
    private const val KEY_CACHED_TASTE = "cached_spotify_taste_tracks"
    private const val KEY_CACHED_SECRET = "cached_player_secret"
    private const val KEY_CACHED_SECRET_VER = "cached_player_secret_ver"

    private var prefs: SharedPreferences? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _userDisplayName = MutableStateFlow<String?>(null)
    val userDisplayName: StateFlow<String?> = _userDisplayName.asStateFlow()

    private val _userPlaylists = MutableStateFlow<List<RemotePlaylistSummary>>(emptyList())
    val userPlaylists: StateFlow<List<RemotePlaylistSummary>> = _userPlaylists.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _spotifyTasteTracks = MutableStateFlow<List<VideoItem>>(emptyList())
    val spotifyTasteTracks: StateFlow<List<VideoItem>> = _spotifyTasteTracks.asStateFlow()

    // Query hashes dynamically extracted or verified fallback
    private val KNOWN_HASHES = mutableMapOf(
        "fetchPlaylist" to "21ea11a6ac75a5f3e01fd365d29c18e0a4a8b7dc09f89bbb5d73910190c6acaf",
        "libraryV3" to "390c78e5b951029bad359785e69b07b536a509c581cbcd0aded5e5067f187455",
        "fetchLibraryTracks" to "087278b20b743578a6262c2b0b4bcd20d879c503cc359a2285baf083ef944240",
        "profileAttributes" to "08ffb4730af3746e04a8301396f20875dbbce10c75243803091a9274eacc8ac0",
        "searchTracks" to "bc1ca2fcd0ba1013a0fc88e6cc4f190af501851e3dafd3e1ef85840297694428",
        "canvas" to "575138ab27cd5c1b3e54da54d0a7cc8d85485402de26340c2145f0f6bb5e7a9f"
    )

    private var cachedSecret: String? = null
    private var cachedSecretVersion: Int = 61

    private var cachedAccessToken: String? = null
    private var tokenExpiresAtMs: Long = 0L

    fun init(context: Context) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp
        val cookie = sp.getString(KEY_SP_DC, null)
        val name = sp.getString(KEY_DISPLAY_NAME, null)
        cachedSecret = sp.getString(KEY_CACHED_SECRET, null)
        cachedSecretVersion = sp.getInt(KEY_CACHED_SECRET_VER, 61)

        val cachedListsJson = sp.getString(KEY_CACHED_PLAYLISTS, null)
        if (!cachedListsJson.isNullOrBlank()) {
            try {
                val arr = JSONArray(cachedListsJson)
                val list = mutableListOf<RemotePlaylistSummary>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    list.add(
                        RemotePlaylistSummary(
                            id = obj.optString("id"),
                            title = obj.optString("title"),
                            description = obj.optString("description"),
                            trackCount = obj.optInt("trackCount"),
                            coverUrl = obj.optString("coverUrl").takeIf { it.isNotBlank() },
                            sourceProvider = "Spotify"
                        )
                    )
                }
                _userPlaylists.value = list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val cachedTasteJson = sp.getString(KEY_CACHED_TASTE, null)
        if (!cachedTasteJson.isNullOrBlank()) {
            try {
                val arr = JSONArray(cachedTasteJson)
                val list = mutableListOf<VideoItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    list.add(
                        VideoItem(
                            id = obj.optString("id"),
                            title = obj.optString("title"),
                            channelName = obj.optString("channelName"),
                            channelAvatarUrl = obj.optString("channelAvatarUrl"),
                            thumbnailUrl = obj.optString("thumbnailUrl"),
                            views = obj.optString("views", "Spotify"),
                            uploadTime = obj.optString("uploadTime", "Favorite"),
                            duration = obj.optString("duration", "3:30"),
                            description = obj.optString("description"),
                            isMusicTrack = true,
                            artist = obj.optString("artist"),
                            album = obj.optString("album", "Spotify"),
                            category = "Music"
                        )
                    )
                }
                _spotifyTasteTracks.value = list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (!cookie.isNullOrBlank()) {
            _isConnected.value = true
            _userDisplayName.value = name
        }
    }

    fun getSpDcCookie(): String? = prefs?.getString(KEY_SP_DC, null)

    fun getAccessToken(): String? = cachedAccessToken

    fun saveSession(spDcCookie: String) {
        val cleanCookie = spDcCookie.trim().removePrefix("sp_dc=").trim()
        if (cleanCookie.isBlank()) return
        prefs?.edit()?.putString(KEY_SP_DC, cleanCookie)?.apply()
        cachedAccessToken = null
        tokenExpiresAtMs = 0L
        _isConnected.value = true
    }

    fun saveAccessToken(token: String, displayName: String = "Spotify User") {
        saveSession(token)
        _userDisplayName.value = displayName
    }

    fun restoreSession(spDcCookie: String, displayName: String?) {
        saveSession(spDcCookie)
        if (!displayName.isNullOrBlank()) {
            _userDisplayName.value = displayName
            prefs?.edit()?.putString(KEY_DISPLAY_NAME, displayName)?.apply()
        }
    }

    fun createAuthUrl(): String = "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"

    fun handleAuthCallback(redirectedUrl: String): Boolean = false

    fun disconnect(clearCookies: Boolean = true) {
        prefs?.edit()
            ?.remove(KEY_SP_DC)
            ?.remove(KEY_DISPLAY_NAME)
            ?.remove(KEY_CACHED_PLAYLISTS)
            ?.remove(KEY_CACHED_TASTE)
            ?.apply()
        cachedAccessToken = null
        tokenExpiresAtMs = 0L
        _isConnected.value = false
        _userDisplayName.value = null
        _userPlaylists.value = emptyList()
        _spotifyTasteTracks.value = emptyList()

        if (clearCookies) {
            try {
                android.webkit.CookieManager.getInstance().removeAllCookies(null)
                android.webkit.CookieManager.getInstance().flush()
                android.webkit.WebStorage.getInstance().deleteAllData()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- RFC 4226 / 6238 TOTP computation matching Spotify Web Player ---
    private fun playerTotp(secret: String, epochMs: Long): String {
        val sb = StringBuilder()
        secret.forEachIndexed { i, c ->
            val xorVal = c.code xor ((i % 33) + 9)
            sb.append(xorVal)
        }
        val key = sb.toString().toByteArray(Charsets.UTF_8)
        val counter = epochMs / 30000L
        return hotp(key, counter)
    }

    private fun hotp(key: ByteArray, counter: Long): String {
        val block = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val h = mac.doFinal(block)
        val o = h[h.size - 1].toInt() and 0x0f
        val code = ((h[o].toInt() and 0x7f) shl 24) or
                ((h[o + 1].toInt() and 0xff) shl 16) or
                ((h[o + 2].toInt() and 0xff) shl 8) or
                (h[o + 3].toInt() and 0xff)
        return String.format("%06d", code % 1_000_000)
    }

    private fun unescapeJs(str: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < str.length) {
            val c = str[i]
            if (c == '\\' && i + 1 < str.length) {
                val next = str[i + 1]
                when (next) {
                    'x' -> {
                        if (i + 3 < str.length) {
                            val hex = str.substring(i + 2, i + 4)
                            hex.toIntOrNull(16)?.let { sb.append(it.toChar()) } ?: sb.append("\\x").append(hex)
                            i += 4
                            continue
                        }
                    }
                    'u' -> {
                        if (i + 5 < str.length) {
                            val hex = str.substring(i + 2, i + 6)
                            hex.toIntOrNull(16)?.let { sb.append(it.toChar()) } ?: sb.append("\\u").append(hex)
                            i += 6
                            continue
                        }
                    }
                    'n' -> { sb.append('\n'); i += 2; continue }
                    'r' -> { sb.append('\r'); i += 2; continue }
                    't' -> { sb.append('\t'); i += 2; continue }
                    'b' -> { sb.append('\b'); i += 2; continue }
                    'f' -> { sb.append('\u000C'); i += 2; continue }
                    '\'' -> { sb.append('\''); i += 2; continue }
                    '"' -> { sb.append('"'); i += 2; continue }
                    '\\' -> { sb.append('\\'); i += 2; continue }
                    else -> { sb.append(next); i += 2; continue }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private suspend fun readPlayerScript(): Boolean = withContext(Dispatchers.IO) {
        try {
            val pageHtml = httpGet("$WEB/")
            val scriptUrlMatch = Pattern.compile("""https://open\.spotifycdn\.com/cdn/build/web-player/web-player\.[0-9a-f]+\.js""").matcher(pageHtml)
            val scriptUrl = if (scriptUrlMatch.find()) scriptUrlMatch.group(0) else null ?: return@withContext false

            val scriptText = httpGet(scriptUrl)
            if (scriptText.isBlank()) return@withContext false

            // Extract secret & highest version
            val secretPattern = Pattern.compile("""\{secret:(['"])((?:\\.|(?!\1).)*)\1,version:(\d+)\}""")
            val secretMatcher = secretPattern.matcher(scriptText)
            var bestVer = -1
            var bestSec: String? = null
            while (secretMatcher.find()) {
                val ver = secretMatcher.group(3)?.toIntOrNull() ?: continue
                val rawSec = secretMatcher.group(2) ?: continue
                if (ver > bestVer) {
                    bestVer = ver
                    bestSec = unescapeJs(rawSec)
                }
            }

            if (bestSec != null && bestVer > 0) {
                cachedSecret = bestSec
                cachedSecretVersion = bestVer
                prefs?.edit()
                    ?.putString(KEY_CACHED_SECRET, bestSec)
                    ?.putInt(KEY_CACHED_SECRET_VER, bestVer)
                    ?.apply()
            }

            // Extract query hashes
            for (op in KNOWN_HASHES.keys.toList()) {
                val hashPattern = Pattern.compile("""["']$op["']\s*,\s*["']query["']\s*,\s*["']([0-9a-f]{64})["']""")
                val hashMatcher = hashPattern.matcher(scriptText)
                if (hashMatcher.find()) {
                    val hash = hashMatcher.group(1)
                    if (!hash.isNullOrBlank()) {
                        KNOWN_HASHES[op] = hash
                    }
                }
            }
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun mintAccessToken(forceFresh: Boolean = false): String? = withContext(Dispatchers.IO) {
        val cookie = getSpDcCookie() ?: return@withContext null

        if (forceFresh || cachedSecret.isNullOrBlank()) {
            readPlayerScript()
        }

        val secret = cachedSecret ?: "secret"
        val ver = cachedSecretVersion
        val totp = playerTotp(secret, System.currentTimeMillis())

        val tokenUrl = "$WEB/api/token?reason=init&productType=web-player&totp=$totp&totpServer=$totp&totpVer=$ver"
        try {
            val conn = (URL(tokenUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 12000
                setRequestProperty("Cookie", "sp_dc=$cookie")
                setRequestProperty("User-Agent", UA)
            }

            val code = conn.responseCode
            val responseText = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            if (code == 401) {
                // Session expired
                disconnect()
                return@withContext null
            }

            val json = if (responseText.startsWith("{")) JSONObject(responseText) else JSONObject()
            if (json.optBoolean("isAnonymous", false)) {
                disconnect()
                return@withContext null
            }

            if (!forceFresh && (code == 400 || json.optBoolean("totpVerExpired", false))) {
                // Retry once with fresh script
                return@withContext mintAccessToken(forceFresh = true)
            }

            val token = json.optString("accessToken")
            if (token.isNotBlank()) {
                val expiresAt = json.optLong("accessTokenExpirationTimestampMs", System.currentTimeMillis() + 3600_000L)
                cachedAccessToken = token
                tokenExpiresAtMs = expiresAt
                return@withContext token
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    suspend fun getValidAccessToken(): String? = withContext(Dispatchers.IO) {
        if (!cachedAccessToken.isNullOrBlank() && System.currentTimeMillis() < tokenExpiresAtMs - 60_000L) {
            return@withContext cachedAccessToken
        }
        mintAccessToken()
    }

    suspend fun queryPathfinder(
        operationName: String,
        variables: JSONObject,
        retried: Boolean = false
    ): JSONObject? = withContext(Dispatchers.IO) {
        val token = getValidAccessToken() ?: return@withContext null
        val hash = KNOWN_HASHES[operationName] ?: return@withContext null

        try {
            val conn = (URL(PATHFINDER).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json;charset=UTF-8")
                setRequestProperty("App-Platform", "WebPlayer")
                setRequestProperty("User-Agent", UA)
            }

            val payload = JSONObject().apply {
                put("variables", variables)
                put("operationName", operationName)
                put("extensions", JSONObject().apply {
                    put("persistedQuery", JSONObject().apply {
                        put("version", 1)
                        put("sha256Hash", hash)
                    })
                })
            }

            conn.outputStream.bufferedWriter().use { it.write(payload.toString()) }

            val code = conn.responseCode
            val responseText = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            if (code == 401 && !retried) {
                cachedAccessToken = null
                return@withContext queryPathfinder(operationName, variables, retried = true)
            }

            val json = if (responseText.startsWith("{")) JSONObject(responseText) else JSONObject()
            val errors = json.optJSONArray("errors")
            if (errors != null && !retried) {
                for (i in 0 until errors.length()) {
                    val msg = errors.optJSONObject(i)?.optString("message") ?: ""
                    if (msg.contains("PersistedQueryNotFound", ignoreCase = true)) {
                        readPlayerScript()
                        return@withContext queryPathfinder(operationName, variables, retried = true)
                    }
                }
            }

            return@withContext json.optJSONObject("data")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun syncAll(): Boolean = withContext(Dispatchers.IO) {
        if (getSpDcCookie().isNullOrBlank()) return@withContext false
        _isSyncing.value = true
        try {
            fetchUserProfile()
            fetchUserPlaylists()
            fetchAndSyncSpotifyTaste()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            _isSyncing.value = false
        }
    }

        suspend fun fetchAndSyncSpotifyTaste(): List<VideoItem> = withContext(Dispatchers.IO) {
        val tasteMetas = mutableListOf<ImportTrackMeta>()

        // 1. Fetch user's Liked Songs (highest indicator of user taste!)
        try {
            val liked = fetchLikedSongs()
            if (liked.isNotEmpty()) {
                tasteMetas.addAll(liked.take(50))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Fetch tracks from user's first 3 playlists
        val playlists = _userPlaylists.value
        for (pl in playlists.take(3)) {
            try {
                val plTracks = fetchPlaylistTracks(pl.id)
                tasteMetas.addAll(plTracks.take(20))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val distinctMetas = tasteMetas.distinctBy { "${it.title.lowercase()}_${it.artist.lowercase()}" }
        val videos = distinctMetas.map { meta ->
            val sId = if (meta.spotifyId.isNotBlank()) "spotify_${meta.spotifyId}" else "spotify_${java.util.UUID.randomUUID().toString().take(11)}"
            val durStr = if (meta.durationSeconds > 0) {
                "${meta.durationSeconds / 60}:${String.format(java.util.Locale.US, "%02d", meta.durationSeconds % 60)}"
            } else "3:30"
            VideoItem(
                id = sId,
                title = meta.title,
                channelName = meta.artist,
                channelAvatarUrl = meta.coverUrl,
                thumbnailUrl = meta.coverUrl,
                views = "Spotify",
                uploadTime = "Favorite",
                duration = durStr,
                description = meta.title,
                isMusicTrack = true,
                artist = meta.artist,
                album = meta.album.ifBlank { "Spotify" },
                category = "Music"
            )
        }

        _spotifyTasteTracks.value = videos

        // Cache in SharedPreferences
        try {
            val arr = JSONArray()
            for (v in videos.take(100)) {
                val obj = JSONObject().apply {
                    put("id", v.id)
                    put("title", v.title)
                    put("artist", v.artist)
                    put("album", v.album)
                    put("channelName", v.channelName)
                    put("channelAvatarUrl", v.channelAvatarUrl)
                    put("thumbnailUrl", v.thumbnailUrl)
                    put("duration", v.duration)
                    put("views", v.views)
                    put("uploadTime", v.uploadTime)
                    put("description", v.description)
                }
                arr.put(obj)
            }
            prefs?.edit()?.putString(KEY_CACHED_TASTE, arr.toString())?.apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Enrich UserTasteProfile with Spotify taste & refresh home feed!
        if (videos.isNotEmpty()) {
            com.videoguard.app.recommendation.UserTasteProfile.get().enrichFromImportedTracks(videos)
            com.videoguard.app.recommendation.RecommendationEngine.refreshHomeFeed(forceRefresh = true)
        }

        videos
    }

    suspend fun fetchUserProfile(): String? = withContext(Dispatchers.IO) {
        val token = getValidAccessToken()
        var name: String? = null

        // 1. Primary: Spotify Web API /v1/me
        if (!token.isNullOrBlank()) {
            try {
                val conn = (URL("https://api.spotify.com/v1/me").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("User-Agent", UA)
                }
                if (conn.responseCode in 200..299) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(text)
                    name = json.optString("display_name").takeIf { it.isNotBlank() }
                        ?: json.optString("id").takeIf { it.isNotBlank() }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Secondary: Pathfinder profileAttributes
        if (name.isNullOrBlank()) {
            val data = queryPathfinder("profileAttributes", JSONObject())
            val profile = data?.optJSONObject("me")?.optJSONObject("profile")
            name = profile?.optString("name")?.takeIf { it.isNotBlank() }
                ?: profile?.optString("username")?.takeIf { it.isNotBlank() }
        }

        val finalName = name ?: "Spotify User"
        _userDisplayName.value = finalName
        prefs?.edit()?.putString(KEY_DISPLAY_NAME, finalName)?.apply()
        finalName
    }

    suspend fun fetchUserPlaylists(): List<RemotePlaylistSummary> = withContext(Dispatchers.IO) {
        val results = mutableListOf<RemotePlaylistSummary>()
        val token = getValidAccessToken()

        // 1. Primary: Spotify Web API /v1/me/playlists (Returns all user, followed, and blend playlists)
        if (!token.isNullOrBlank()) {
            try {
                var nextUrl: String? = "https://api.spotify.com/v1/me/playlists?limit=50"
                while (!nextUrl.isNullOrBlank() && results.size < 500) {
                    val conn = (URL(nextUrl).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 12000
                        readTimeout = 12000
                        setRequestProperty("Authorization", "Bearer $token")
                        setRequestProperty("User-Agent", UA)
                    }

                    if (conn.responseCode in 200..299) {
                        val text = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(text)
                        val items = json.optJSONArray("items")
                        if (items != null) {
                            for (i in 0 until items.length()) {
                                val item = items.optJSONObject(i) ?: continue
                                val id = item.optString("id")
                                val name = item.optString("name")
                                if (id.isBlank() || name.isBlank()) continue

                                var coverUrl: String? = null
                                val images = item.optJSONArray("images")
                                if (images != null && images.length() > 0) {
                                    coverUrl = images.optJSONObject(0)?.optString("url")
                                }

                                val ownerName = item.optJSONObject("owner")?.optString("display_name") ?: ""
                                val trackCount = item.optJSONObject("tracks")?.optInt("total", 0) ?: 0
                                val desc = item.optString("description").ifBlank {
                                    if (ownerName.isNotBlank()) "By $ownerName" else "Spotify Playlist"
                                }

                                results.add(
                                    RemotePlaylistSummary(
                                        id = id,
                                        title = name,
                                        description = desc,
                                        trackCount = trackCount,
                                        coverUrl = coverUrl,
                                        sourceProvider = "Spotify"
                                    )
                                )
                            }
                        }
                        nextUrl = json.optString("next").takeIf { it.isNotBlank() }
                    } else {
                        break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Secondary fallback: Pathfinder libraryV3
        if (results.isEmpty()) {
            results.addAll(fetchPlaylistsPathfinder())
        }

        _userPlaylists.value = results

        // Cache locally for offline/fast restart
        try {
            val jsonArr = JSONArray()
            for (p in results) {
                jsonArr.put(JSONObject().apply {
                    put("id", p.id)
                    put("title", p.title)
                    put("description", p.description)
                    put("trackCount", p.trackCount)
                    put("coverUrl", p.coverUrl ?: "")
                })
            }
            prefs?.edit()?.putString(KEY_CACHED_PLAYLISTS, jsonArr.toString())?.apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        results
    }

    private suspend fun fetchPlaylistsPathfinder(): List<RemotePlaylistSummary> = withContext(Dispatchers.IO) {
        val results = mutableListOf<RemotePlaylistSummary>()
        var offset = 0
        val limit = 50

        while (offset < 1000) {
            val vars = JSONObject().apply {
                put("filters", JSONArray().put("Playlists"))
                put("order", JSONObject.NULL)
                put("textFilter", "")
                put("features", JSONArray().apply {
                    put("LIKED_SONGS")
                    put("YOUR_EPISODES_V2")
                    put("PRERELEASES")
                    put("EVENTS")
                })
                put("limit", limit)
                put("offset", offset)
                put("flatten", true)
                put("expandedFolders", JSONArray())
                put("folderUri", JSONObject.NULL)
                put("includeFoldersWhenFlattening", false)
            }

            val data = queryPathfinder("libraryV3", vars) ?: break
            val libraryV3 = data.optJSONObject("me")?.optJSONObject("libraryV3") ?: break
            val items = libraryV3.optJSONArray("items") ?: break
            val totalCount = libraryV3.optInt("totalCount", items.length())

            for (i in 0 until items.length()) {
                val entry = items.optJSONObject(i) ?: continue
                val wrapper = entry.optJSONObject("item") ?: continue
                val itemData = wrapper.optJSONObject("data") ?: continue
                val uri = wrapper.optString("_uri", "")
                val id = if (uri.startsWith("spotify:playlist:")) uri.removePrefix("spotify:playlist:") else ""
                val name = itemData.optString("name", "")

                if (id.isNotBlank() && name.isNotBlank()) {
                    var cover: String? = null
                    val images = itemData.optJSONObject("images")?.optJSONArray("items")
                    if (images != null && images.length() > 0) {
                        val sources = images.optJSONObject(0)?.optJSONArray("sources")
                        if (sources != null && sources.length() > 0) {
                            cover = sources.optJSONObject(0)?.optString("url")
                        }
                    }
                    val owner = itemData.optJSONObject("ownerV2")?.optJSONObject("data")?.optString("name", "") ?: ""
                    results.add(
                        RemotePlaylistSummary(
                            id = id,
                            title = name,
                            description = if (owner.isNotBlank()) "By $owner" else "Spotify Playlist",
                            trackCount = 0,
                            coverUrl = cover,
                            sourceProvider = "Spotify"
                        )
                    )
                }
            }

            offset += items.length()
            if (items.length() == 0 || offset >= totalCount) break
        }
        results
    }

    suspend fun fetchLikedSongs(onProgress: (ImportProgress) -> Unit = {}): List<ImportTrackMeta> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<ImportTrackMeta>()
        val token = getValidAccessToken()

        onProgress(ImportProgress("Reading Spotify Liked Songs...", 0, 0))

        // 1. Primary: Spotify Web API /v1/me/tracks
        if (!token.isNullOrBlank()) {
            try {
                var nextUrl: String? = "https://api.spotify.com/v1/me/tracks?limit=50"
                var total = 0

                while (!nextUrl.isNullOrBlank() && tracks.size < 5000) {
                    val conn = (URL(nextUrl).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 12000
                        readTimeout = 12000
                        setRequestProperty("Authorization", "Bearer $token")
                        setRequestProperty("User-Agent", UA)
                    }

                    if (conn.responseCode in 200..299) {
                        val text = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(text)
                        total = json.optInt("total", total)
                        val items = json.optJSONArray("items")
                        if (items != null) {
                            for (i in 0 until items.length()) {
                                val item = items.optJSONObject(i) ?: continue
                                val track = item.optJSONObject("track") ?: continue
                                val name = track.optString("name", "")
                                val id = track.optString("id", "")
                                val durMs = track.optLong("duration_ms", 0L)
                                val albumObj = track.optJSONObject("album")
                                val albumName = albumObj?.optString("name", "") ?: ""
                                var coverUrl = ""
                                val images = albumObj?.optJSONArray("images")
                                if (images != null && images.length() > 0) {
                                    coverUrl = images.optJSONObject(0)?.optString("url", "") ?: ""
                                }

                                val artistsArr = track.optJSONArray("artists")
                                val artistNames = mutableListOf<String>()
                                if (artistsArr != null) {
                                    for (a in 0 until artistsArr.length()) {
                                        val aObj = artistsArr.optJSONObject(a)
                                        val aName = aObj?.optString("name", "") ?: ""
                                        if (aName.isNotBlank()) artistNames.add(aName)
                                    }
                                }
                                val artist = artistNames.joinToString(", ")

                                if (name.isNotBlank()) {
                                    tracks.add(
                                        ImportTrackMeta(
                                            title = name,
                                            artist = artist,
                                            album = albumName,
                                            durationSeconds = (durMs / 1000).toInt(),
                                            spotifyId = id,
                                            coverUrl = coverUrl
                                        )
                                    )
                                }
                            }
                        }
                        onProgress(ImportProgress("Reading Spotify Liked Songs...", tracks.size, total))
                        nextUrl = json.optString("next").takeIf { it.isNotBlank() }
                    } else {
                        break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Secondary fallback: Pathfinder fetchLibraryTracks
        if (tracks.isEmpty()) {
            tracks.addAll(fetchLikedSongsPathfinder(onProgress))
        }

        tracks
    }

    private suspend fun fetchLikedSongsPathfinder(onProgress: (ImportProgress) -> Unit): List<ImportTrackMeta> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<ImportTrackMeta>()
        var offset = 0
        val limit = 100

        while (offset < 5000) {
            val vars = JSONObject().apply {
                put("offset", offset)
                put("limit", limit)
            }
            val data = queryPathfinder("fetchLibraryTracks", vars) ?: break
            val tracksObj = data.optJSONObject("me")?.optJSONObject("library")?.optJSONObject("tracks") ?: break
            val items = tracksObj.optJSONArray("items") ?: break
            val total = tracksObj.optInt("totalCount", items.length())

            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val trackWrapper = item.optJSONObject("track") ?: continue
                val tData = trackWrapper.optJSONObject("data") ?: continue
                val name = tData.optString("name", "")
                val uri = tData.optString("uri", trackWrapper.optString("_uri", ""))
                val spotifyId = if (uri.startsWith("spotify:track:")) uri.removePrefix("spotify:track:") else ""
                val durMs = tData.optJSONObject("trackDuration")?.optLong("totalMilliseconds", 0L)
                    ?: tData.optJSONObject("duration")?.optLong("totalMilliseconds", 0L) ?: 0L

                val artistsArr = tData.optJSONObject("artists")?.optJSONArray("items")
                val artistList = mutableListOf<String>()
                if (artistsArr != null) {
                    for (a in 0 until artistsArr.length()) {
                        artistsArr.optJSONObject(a)?.optJSONObject("profile")?.optString("name")?.let {
                            if (it.isNotBlank()) artistList.add(it)
                        }
                    }
                }
                val artist = artistList.joinToString(", ")
                val albumObj = tData.optJSONObject("albumOfTrack")
                val album = albumObj?.optString("name", "") ?: ""
                var coverUrl = ""
                val sources = albumObj?.optJSONObject("coverArt")?.optJSONArray("sources")
                if (sources != null && sources.length() > 0) {
                    coverUrl = sources.optJSONObject(0)?.optString("url", "") ?: ""
                }

                if (name.isNotBlank()) {
                    tracks.add(
                        ImportTrackMeta(
                            title = name,
                            artist = artist,
                            album = album,
                            durationSeconds = (durMs / 1000).toInt(),
                            spotifyId = spotifyId,
                            coverUrl = coverUrl
                        )
                    )
                }
            }

            offset += items.length()
            onProgress(ImportProgress("Reading Spotify Liked Songs...", tracks.size, total))
            if (items.length() == 0 || offset >= total) break
        }
        tracks
    }

    suspend fun fetchPlaylistTracks(
        playlistId: String,
        onProgress: (ImportProgress) -> Unit = {}
    ): List<ImportTrackMeta> = withContext(Dispatchers.IO) {
        if (playlistId == "liked") {
            return@withContext fetchLikedSongs(onProgress)
        }

        val tracks = mutableListOf<ImportTrackMeta>()
        val token = getValidAccessToken()

        onProgress(ImportProgress("Reading Spotify Playlist...", 0, 0))

        // 1. Primary: Spotify Web API /v1/playlists/{id}/tracks
        if (!token.isNullOrBlank()) {
            try {
                var nextUrl: String? = "https://api.spotify.com/v1/playlists/$playlistId/tracks?limit=100"
                var total = 0

                while (!nextUrl.isNullOrBlank() && tracks.size < 5000) {
                    val conn = (URL(nextUrl).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 12000
                        readTimeout = 12000
                        setRequestProperty("Authorization", "Bearer $token")
                        setRequestProperty("User-Agent", UA)
                    }

                    if (conn.responseCode in 200..299) {
                        val text = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(text)
                        total = json.optInt("total", total)
                        val items = json.optJSONArray("items")
                        if (items != null) {
                            for (i in 0 until items.length()) {
                                val item = items.optJSONObject(i) ?: continue
                                val track = item.optJSONObject("track") ?: continue
                                val name = track.optString("name", "")
                                val id = track.optString("id", "")
                                val durMs = track.optLong("duration_ms", 0L)
                                val albumObj = track.optJSONObject("album")
                                val albumName = albumObj?.optString("name", "") ?: ""
                                var coverUrl = ""
                                val images = albumObj?.optJSONArray("images")
                                if (images != null && images.length() > 0) {
                                    coverUrl = images.optJSONObject(0)?.optString("url", "") ?: ""
                                }

                                val artistsArr = track.optJSONArray("artists")
                                val artistNames = mutableListOf<String>()
                                if (artistsArr != null) {
                                    for (a in 0 until artistsArr.length()) {
                                        val aObj = artistsArr.optJSONObject(a)
                                        val aName = aObj?.optString("name", "") ?: ""
                                        if (aName.isNotBlank()) artistNames.add(aName)
                                    }
                                }
                                val artist = artistNames.joinToString(", ")

                                if (name.isNotBlank()) {
                                    tracks.add(
                                        ImportTrackMeta(
                                            title = name,
                                            artist = artist,
                                            album = albumName,
                                            durationSeconds = (durMs / 1000).toInt(),
                                            spotifyId = id,
                                            coverUrl = coverUrl
                                        )
                                    )
                                }
                            }
                        }
                        onProgress(ImportProgress("Reading Spotify Playlist...", tracks.size, total))
                        nextUrl = json.optString("next").takeIf { it.isNotBlank() }
                    } else {
                        break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Secondary: Pathfinder fallback
        if (tracks.isEmpty()) {
            tracks.addAll(fetchPlaylistTracksPathfinder(playlistId, onProgress))
        }

        tracks
    }

    private suspend fun fetchPlaylistTracksPathfinder(
        playlistId: String,
        onProgress: (ImportProgress) -> Unit
    ): List<ImportTrackMeta> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<ImportTrackMeta>()
        var offset = 0
        val limit = 100

        while (offset < 5000) {
            val vars = JSONObject().apply {
                put("uri", "spotify:playlist:$playlistId")
                put("offset", offset)
                put("limit", limit)
                put("enableWatchFeedEntrypoint", false)
            }

            val data = queryPathfinder("fetchPlaylist", vars) ?: break
            val playlistV2 = data.optJSONObject("playlistV2") ?: break
            val content = playlistV2.optJSONObject("content") ?: break
            val items = content.optJSONArray("items") ?: break
            val total = content.optInt("totalCount", items.length())

            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val tData = item.optJSONObject("itemV2")?.optJSONObject("data") ?: continue
                val typename = tData.optString("__typename")
                if (typename != "Track") continue

                val name = tData.optString("name", "")
                val uri = tData.optString("uri", "")
                val spotifyId = if (uri.startsWith("spotify:track:")) uri.removePrefix("spotify:track:") else ""
                val durMs = tData.optJSONObject("trackDuration")?.optLong("totalMilliseconds", 0L)
                    ?: tData.optJSONObject("duration")?.optLong("totalMilliseconds", 0L) ?: 0L

                val artistsArr = tData.optJSONObject("artists")?.optJSONArray("items")
                val artistList = mutableListOf<String>()
                if (artistsArr != null) {
                    for (a in 0 until artistsArr.length()) {
                        artistsArr.optJSONObject(a)?.optJSONObject("profile")?.optString("name")?.let {
                            if (it.isNotBlank()) artistList.add(it)
                        }
                    }
                }
                val artist = artistList.joinToString(", ")
                val albumObj = tData.optJSONObject("albumOfTrack")
                val album = albumObj?.optString("name", "") ?: ""
                var coverUrl = ""
                val sources = albumObj?.optJSONObject("coverArt")?.optJSONArray("sources")
                if (sources != null && sources.length() > 0) {
                    coverUrl = sources.optJSONObject(0)?.optString("url", "") ?: ""
                }

                if (name.isNotBlank()) {
                    tracks.add(
                        ImportTrackMeta(
                            title = name,
                            artist = artist,
                            album = album,
                            durationSeconds = (durMs / 1000).toInt(),
                            spotifyId = spotifyId,
                            coverUrl = coverUrl
                        )
                    )
                }
            }

            offset += items.length()
            onProgress(ImportProgress("Reading Spotify Playlist...", tracks.size, total))
            if (items.length() == 0 || offset >= total) break
        }
        tracks
    }

    suspend fun fetchPlaylistTitle(playlistId: String): String = withContext(Dispatchers.IO) {
        if (playlistId == "liked") return@withContext "Liked Songs (Spotify)"
        val token = getValidAccessToken()
        if (!token.isNullOrBlank()) {
            try {
                val conn = (URL("https://api.spotify.com/v1/playlists/$playlistId").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("User-Agent", UA)
                }
                if (conn.responseCode in 200..299) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val name = JSONObject(text).optString("name")
                    if (name.isNotBlank()) return@withContext name
                }
            } catch (e: Exception) {}
        }
        val vars = JSONObject().apply {
            put("uri", "spotify:playlist:$playlistId")
            put("offset", 0)
            put("limit", 1)
            put("enableWatchFeedEntrypoint", false)
        }
        val data = queryPathfinder("fetchPlaylist", vars)
        val name = data?.optJSONObject("playlistV2")?.optString("name", "")
        if (!name.isNullOrBlank()) name else "Spotify Playlist"
    }

    private fun httpGet(urlStr: String, headers: Map<String, String> = emptyMap()): String {
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 12000
                setRequestProperty("User-Agent", UA)
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
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
}

// Backward compatibility alias so existing code keeps compiling cleanly
typealias SpotifyOAuthEngine = SpotifyConnectEngine
