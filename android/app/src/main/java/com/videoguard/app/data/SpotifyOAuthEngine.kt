package com.videoguard.app.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom

data class RemotePlaylistSummary(
    val id: String,
    val title: String,
    val description: String,
    val trackCount: Int,
    val coverUrl: String?,
    val sourceProvider: String = "Spotify"
)

object SpotifyOAuthEngine {

    // Standard Spotify Client ID configured for PKCE
    const val DEFAULT_CLIENT_ID = "271077752670460c918ef93949f506e7"
    const val REDIRECT_URI = "https://open.spotify.com/"
    private const val PREFS_NAME = "spotify_oauth_prefs"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_USER_DISPLAY_NAME = "display_name"

    private var prefs: SharedPreferences? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _userDisplayName = MutableStateFlow<String?>(null)
    val userDisplayName: StateFlow<String?> = _userDisplayName.asStateFlow()

    private val _userPlaylists = MutableStateFlow<List<RemotePlaylistSummary>>(emptyList())
    val userPlaylists: StateFlow<List<RemotePlaylistSummary>> = _userPlaylists.asStateFlow()

    private var currentCodeVerifier: String = ""

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs?.getString(KEY_ACCESS_TOKEN, null)
        val name = prefs?.getString(KEY_USER_DISPLAY_NAME, null)
        if (!token.isNullOrBlank()) {
            _isConnected.value = true
            _userDisplayName.value = name
        }
    }

    fun getAccessToken(): String? = prefs?.getString(KEY_ACCESS_TOKEN, null)

    fun createAuthUrl(): String {
        currentCodeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(currentCodeVerifier)
        val scopes = "playlist-read-private playlist-read-collaborative user-library-read"

        return Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", DEFAULT_CLIENT_ID)
            .appendQueryParameter("response_type", "token") // Token response mode for in-app mobile OAuth
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", scopes)
            .appendQueryParameter("show_dialog", "true")
            .build()
            .toString()
    }

    fun handleAuthCallback(redirectedUrl: String): Boolean {
        try {
            // Check implicit token in hash: #access_token=...&expires_in=...
            if (redirectedUrl.contains("#access_token=") || redirectedUrl.contains("&access_token=")) {
                val tokenPart = if (redirectedUrl.contains("#access_token=")) {
                    redirectedUrl.substringAfter("#access_token=").substringBefore("&")
                } else {
                    redirectedUrl.substringAfter("&access_token=").substringBefore("&")
                }
                if (tokenPart.isNotBlank()) {
                    saveAccessToken(tokenPart)
                    return true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    fun saveAccessToken(token: String, displayName: String = "Spotify User") {
        prefs?.edit()
            ?.putString(KEY_ACCESS_TOKEN, token)
            ?.putString(KEY_USER_DISPLAY_NAME, displayName)
            ?.apply()
        _isConnected.value = true
        _userDisplayName.value = displayName
    }

    fun disconnect() {
        prefs?.edit()
            ?.remove(KEY_ACCESS_TOKEN)
            ?.remove(KEY_REFRESH_TOKEN)
            ?.remove(KEY_USER_DISPLAY_NAME)
            ?.apply()
        _isConnected.value = false
        _userDisplayName.value = null
        _userPlaylists.value = emptyList()
    }

    suspend fun fetchCurrentUserProfile(): String? = withContext(Dispatchers.IO) {
        val token = getAccessToken() ?: return@withContext null
        try {
            val jsonStr = httpGetWithAuth("https://api.spotify.com/v1/me", token)
            if (jsonStr.isNotBlank()) {
                val json = JSONObject(jsonStr)
                val name = json.optString("display_name", "Spotify User")
                _userDisplayName.value = name
                prefs?.edit()?.putString(KEY_USER_DISPLAY_NAME, name)?.apply()
                return@withContext name
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    suspend fun fetchUserPlaylists(): List<RemotePlaylistSummary> = withContext(Dispatchers.IO) {
        val token = getAccessToken()
        val list = mutableListOf<RemotePlaylistSummary>()

        if (!token.isNullOrBlank()) {
            try {
                var nextUrl: String? = "https://api.spotify.com/v1/me/playlists?limit=50"
                while (!nextUrl.isNullOrBlank()) {
                    val jsonStr = httpGetWithAuth(nextUrl, token)
                    if (jsonStr.isBlank() || !jsonStr.startsWith("{")) break
                    val root = JSONObject(jsonStr)
                    val items = root.optJSONArray("items") ?: break
                    for (i in 0 until items.length()) {
                        val pl = items.optJSONObject(i) ?: continue
                        val id = pl.optString("id", "")
                        val title = pl.optString("name", "Untitled")
                        val desc = pl.optString("description", "")
                        val tracksObj = pl.optJSONObject("tracks")
                        val count = tracksObj?.optInt("total", 0) ?: 0
                        var coverUrl: String? = null
                        val images = pl.optJSONArray("images")
                        if (images != null && images.length() > 0) {
                            coverUrl = images.optJSONObject(0)?.optString("url")
                        }
                        if (id.isNotBlank()) {
                            list.add(RemotePlaylistSummary(id, title, desc, count, coverUrl, "Spotify"))
                        }
                    }
                    nextUrl = if (root.has("next") && !root.isNull("next")) root.optString("next") else null
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        _userPlaylists.value = list
        list
    }

    suspend fun fetchPlaylistTracks(playlistId: String): List<ImportTrackMeta> = withContext(Dispatchers.IO) {
        val token = getAccessToken()
        val tracks = mutableListOf<ImportTrackMeta>()

        if (!token.isNullOrBlank()) {
            try {
                var nextUrl: String? = "https://api.spotify.com/v1/playlists/$playlistId/tracks?limit=50"
                while (!nextUrl.isNullOrBlank()) {
                    val jsonStr = httpGetWithAuth(nextUrl, token)
                    if (jsonStr.isBlank() || !jsonStr.startsWith("{")) break
                    val root = JSONObject(jsonStr)
                    val items = root.optJSONArray("items") ?: break
                    for (i in 0 until items.length()) {
                        val itemObj = items.optJSONObject(i) ?: continue
                        val trackObj = itemObj.optJSONObject("track") ?: continue
                        val name = trackObj.optString("name", "")
                        val artistsArr = trackObj.optJSONArray("artists")
                        val artistList = mutableListOf<String>()
                        if (artistsArr != null) {
                            for (a in 0 until artistsArr.length()) {
                                artistsArr.optJSONObject(a)?.optString("name")?.let { artistList.add(it) }
                            }
                        }
                        val artistStr = artistList.joinToString(", ")
                        val albumObj = trackObj.optJSONObject("album")
                        val albumName = albumObj?.optString("name", "") ?: ""
                        if (name.isNotBlank()) {
                            tracks.add(ImportTrackMeta(name, artistStr, albumName))
                        }
                    }
                    nextUrl = if (root.has("next") && !root.isNull("next")) root.optString("next") else null
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        tracks
    }

    private fun httpGetWithAuth(urlStr: String, token: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
        }
        return if (conn.responseCode in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            ""
        }
    }

    private fun generateCodeVerifier(): String {
        val sr = SecureRandom()
        val bytes = ByteArray(32)
        sr.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val md = MessageDigest.getInstance("SHA-256")
        md.update(bytes, 0, bytes.size)
        val digest = md.digest()
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
