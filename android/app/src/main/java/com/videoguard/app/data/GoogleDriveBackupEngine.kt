package com.videoguard.app.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import com.videoguard.app.model.UserAccount
import com.videoguard.app.model.UserPlaylist
import com.videoguard.app.model.VideoItem
import com.videoguard.app.recommendation.RecommendationEngine
import com.videoguard.app.recommendation.UserTasteProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupState(
    val hasLocalBackup: Boolean = false,
    val isDriveConnected: Boolean = false,
    val lastBackupFormatted: String = "Never",
    val backupSizeBytes: Long = 0L,
    val isBackingUp: Boolean = false,
    val isRestoring: Boolean = false,
    val isSpotifySaved: Boolean = false,
    val isYouTubeSaved: Boolean = false,
    val playlistsCount: Int = 0
) {
    val formattedSize: String
        get() = when {
            backupSizeBytes <= 0 -> "0 KB"
            backupSizeBytes < 1024 -> "$backupSizeBytes B"
            backupSizeBytes < 1024 * 1024 -> "${backupSizeBytes / 1024} KB"
            else -> String.format(Locale.US, "%.1f MB", backupSizeBytes / (1024f * 1024f))
        }
}

object GoogleDriveBackupEngine {

    private const val PREFS_NAME = "auralix_full_backup_prefs"
    private const val KEY_LAST_BACKUP_TIME = "last_backup_time"
    private const val KEY_LAST_BACKUP_SIZE = "last_backup_size"
    private const val KEY_AUTO_RESTORED = "auto_restored_on_install"

    private const val BACKUP_FILENAME = "auralix_backup.json"

    private var prefs: SharedPreferences? = null

    private val _backupState = MutableStateFlow(BackupState())
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp

        val lastTime = sp.getLong(KEY_LAST_BACKUP_TIME, 0L)
        val lastSize = sp.getLong(KEY_LAST_BACKUP_SIZE, 0L)
        val formattedDate = if (lastTime > 0) formatTimestamp(lastTime) else "Never"

        val backupFile = findAnyExistingBackup(context)
        val hasBackup = backupFile != null && backupFile.exists() && backupFile.length() > 0

        _backupState.value = BackupState(
            hasLocalBackup = hasBackup,
            isDriveConnected = hasBackup,
            lastBackupFormatted = formattedDate,
            backupSizeBytes = if (lastSize > 0) lastSize else (backupFile?.length() ?: 0L),
            isSpotifySaved = SpotifyConnectEngine.isConnected.value,
            isYouTubeSaved = YouTubeSyncEngine.hasAuthCookies(),
            playlistsCount = UserDataRepository.playlists.value.size
        )
    }

    /**
     * Checks if a backup file exists from a previous install or transfer,
     * and automatically restores Spotify, YouTube, Playlists, and Recommendations!
     */
    suspend fun checkAndAutoRestoreOnInstall(context: Context): Boolean = withContext(Dispatchers.IO) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val alreadyChecked = sp.getBoolean(KEY_AUTO_RESTORED, false)
        if (alreadyChecked) return@withContext false

        // Only auto-restore on a fresh launch where user data is not yet populated
        val hasExistingUserData = UserDataRepository.history.value.isNotEmpty() ||
                                  UserDataRepository.playlists.value.any { it.id != "liked_videos" } ||
                                  SpotifyConnectEngine.isConnected.value

        if (hasExistingUserData) {
            sp.edit().putBoolean(KEY_AUTO_RESTORED, true).apply()
            return@withContext false
        }

        val backupFile = findAnyExistingBackup(context) ?: return@withContext false
        try {
            val success = restoreFromStream(backupFile.inputStream())
            if (success) {
                sp.edit().putBoolean(KEY_AUTO_RESTORED, true).apply()
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "Restored your Spotify, playlists & recommendations!",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
            success
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Serializes COMPLETE application state:
     * - Taste Profile & Affinities
     * - User Playlists & Listening History
     * - Spotify Session (sp_dc cookie + display name)
     * - YouTube Session (authenticated cookies)
     * - Connected Google Account info
     */
    fun createFullBackupJson(): JSONObject {
        val root = JSONObject()
        root.put("app", "AURALIX")
        root.put("version", 2)
        root.put("timestamp", System.currentTimeMillis())
        root.put("formattedDate", SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault()).format(Date()))

        // 1. Personalized Taste Profile
        try {
            root.put("tasteProfile", UserTasteProfile.get().exportTasteProfileJson())
        } catch (e: Exception) {
            e.printStackTrace()
            root.put("tasteProfile", JSONObject())
        }

        // 2. Playlists (defensive copy to prevent ConcurrentModificationException)
        val playlistsArr = JSONArray()
        try {
            val list = UserDataRepository.playlists.value.toList()
            list.forEach {
                playlistsArr.put(UserDataRepository.playlistToJson(it))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        root.put("playlists", playlistsArr)

        // 3. Listening / Watch History (defensive copy)
        val historyArr = JSONArray()
        try {
            val list = UserDataRepository.history.value.toList()
            list.forEach {
                historyArr.put(UserDataRepository.videoToJson(it))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        root.put("history", historyArr)

        // 4. Spotify Session & Auth
        try {
            val spotifyObj = JSONObject()
            val spDc = SpotifyConnectEngine.getSpDcCookie()
            val spName = SpotifyConnectEngine.userDisplayName.value
            val isSpConnected = SpotifyConnectEngine.isConnected.value
            spotifyObj.put("connected", isSpConnected)
            if (!spDc.isNullOrBlank()) {
                spotifyObj.put("sp_dc", spDc)
            }
            if (!spName.isNullOrBlank()) {
                spotifyObj.put("displayName", spName)
            }
            root.put("spotify", spotifyObj)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 5. YouTube Auth & Session Cookies
        try {
            val ytObj = JSONObject()
            val ytCookies = YouTubeSyncEngine.getAuthCookies()
            if (ytCookies.isNotBlank()) {
                ytObj.put("cookies", ytCookies)
            }
            root.put("youtube", ytObj)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 6. Connected Google Account
        try {
            val account = AccountRepository.currentAccount.value
            if (account != null) {
                val accObj = JSONObject().apply {
                    put("name", account.name)
                    put("email", account.email)
                    put("handle", account.handle)
                    put("avatarUrl", account.avatarUrl ?: "")
                }
                root.put("account", accObj)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return root
    }

    /**
     * Saves complete backup locally across app-internal and external scoped storage.
     * Persists across app uninstalls!
     */
    suspend fun backupLocally(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            if (prefs == null) init(context)
            _backupState.value = _backupState.value.copy(isBackingUp = true)

            val json = createFullBackupJson()
            val bytes = json.toString(2).toByteArray(Charsets.UTF_8)

            saveBackupToAllLocations(context, bytes)

            val now = System.currentTimeMillis()
            val formatted = formatTimestamp(now)

            prefs?.edit()
                ?.putLong(KEY_LAST_BACKUP_TIME, now)
                ?.putLong(KEY_LAST_BACKUP_SIZE, bytes.size.toLong())
                ?.apply()

            _backupState.value = _backupState.value.copy(
                hasLocalBackup = true,
                isDriveConnected = true,
                lastBackupFormatted = formatted,
                backupSizeBytes = bytes.size.toLong(),
                isBackingUp = false,
                isSpotifySaved = SpotifyConnectEngine.isConnected.value,
                isYouTubeSaved = YouTubeSyncEngine.hasAuthCookies(),
                playlistsCount = UserDataRepository.playlists.value.size
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            _backupState.value = _backupState.value.copy(isBackingUp = false)
            false
        }
    }

    /**
     * Writes backup JSON to given OutputStream (e.g. Google Drive SAF URI).
     */
    suspend fun writeBackupToStream(context: Context, outputStream: OutputStream?): Boolean = withContext(Dispatchers.IO) {
        if (outputStream == null) return@withContext false
        try {
            if (prefs == null) init(context)
            _backupState.value = _backupState.value.copy(isBackingUp = true)

            val json = createFullBackupJson()
            val bytes = json.toString(2).toByteArray(Charsets.UTF_8)

            outputStream.use { out ->
                out.write(bytes)
                out.flush()
            }

            // Also keep local and documents snapshots safely
            saveBackupToAllLocations(context, bytes)

            val now = System.currentTimeMillis()
            val formatted = formatTimestamp(now)
            prefs?.edit()
                ?.putLong(KEY_LAST_BACKUP_TIME, now)
                ?.putLong(KEY_LAST_BACKUP_SIZE, bytes.size.toLong())
                ?.apply()

            _backupState.value = _backupState.value.copy(
                hasLocalBackup = true,
                isDriveConnected = true,
                lastBackupFormatted = formatted,
                backupSizeBytes = bytes.size.toLong(),
                isBackingUp = false,
                isSpotifySaved = SpotifyConnectEngine.isConnected.value,
                isYouTubeSaved = YouTubeSyncEngine.hasAuthCookies(),
                playlistsCount = UserDataRepository.playlists.value.size
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            _backupState.value = _backupState.value.copy(isBackingUp = false)
            false
        }
    }

    /**
     * Restores COMPLETE application state from given InputStream (Google Drive or local file):
     * - Taste Profile
     * - Playlists & History
     * - Spotify Session
     * - YouTube Session
     * - Google Account
     */
    suspend fun restoreFromStream(inputStream: InputStream?): Boolean = withContext(Dispatchers.IO) {
        if (inputStream == null) return@withContext false
        try {
            _backupState.value = _backupState.value.copy(isRestoring = true)

            val content = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (content.isBlank()) return@withContext false
            val root = JSONObject(content)

            // 1. Restore Taste Profile
            try {
                root.optJSONObject("tasteProfile")?.let {
                    UserTasteProfile.get().importTasteProfileJson(it)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Restore Playlists
            val restoredPlaylists = mutableListOf<UserPlaylist>()
            try {
                val playlistsArr = root.optJSONArray("playlists")
                if (playlistsArr != null) {
                    for (i in 0 until playlistsArr.length()) {
                        playlistsArr.optJSONObject(i)?.let {
                            restoredPlaylists.add(UserDataRepository.jsonToPlaylist(it))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 3. Restore History
            val restoredHistory = mutableListOf<VideoItem>()
            try {
                val historyArr = root.optJSONArray("history")
                if (historyArr != null) {
                    for (i in 0 until historyArr.length()) {
                        historyArr.optJSONObject(i)?.let {
                            restoredHistory.add(UserDataRepository.jsonToVideo(it))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Apply data safely
            try {
                UserDataRepository.restoreFullData(restoredHistory, restoredPlaylists)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 4. Restore Spotify Session
            try {
                root.optJSONObject("spotify")?.let { spObj ->
                    val spDc = spObj.optString("sp_dc", "")
                    val displayName = spObj.optString("displayName", "Spotify User")
                    if (spDc.isNotBlank()) {
                        SpotifyConnectEngine.restoreSession(spDc, displayName)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 5. Restore YouTube Cookies (Safely on Main thread to prevent Chromium UI deadlocks)
            try {
                root.optJSONObject("youtube")?.let { ytObj ->
                    val cookies = ytObj.optString("cookies", "")
                    if (cookies.isNotBlank()) {
                        withContext(Dispatchers.Main) {
                            try {
                                val cm = CookieManager.getInstance()
                                cm.setAcceptCookie(true)
                                cm.setCookie("https://www.youtube.com", cookies)
                                cm.setCookie("https://m.youtube.com", cookies)
                                cm.flush()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 6. Restore Google Account
            try {
                root.optJSONObject("account")?.let { accObj ->
                    val name = accObj.optString("name", "")
                    val email = accObj.optString("email", "")
                    val handle = accObj.optString("handle", "@user")
                    val avatarUrl = accObj.optString("avatarUrl", null).takeIf { it?.isNotBlank() == true }
                    if (name.isNotBlank()) {
                        AccountRepository.setGoogleAccount(name, email, avatarUrl, handle)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 7. Non-blocking Async Refresh for Recommendations (Does NOT block the UI)
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                try {
                    RecommendationEngine.refreshHomeFeed(forceRefresh = true)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            _backupState.value = _backupState.value.copy(
                hasLocalBackup = true,
                isDriveConnected = true,
                isRestoring = false,
                isSpotifySaved = SpotifyConnectEngine.isConnected.value,
                isYouTubeSaved = YouTubeSyncEngine.hasAuthCookies(),
                playlistsCount = UserDataRepository.playlists.value.size
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            _backupState.value = _backupState.value.copy(isRestoring = false)
            false
        } finally {
            _backupState.value = _backupState.value.copy(isRestoring = false)
        }
    }

    private fun saveBackupToAllLocations(context: Context, bytes: ByteArray) {
        if (prefs == null) {
            init(context)
        }

        // 1. App internal filesDir (Always succeeds without any permission)
        try {
            val internalFile = File(context.filesDir, BACKUP_FILENAME)
            internalFile.writeBytes(bytes)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. External app Documents: context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) (Always succeeds on Android 10-15)
        try {
            val extDocs = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (extDocs != null) {
                if (!extDocs.exists()) extDocs.mkdirs()
                val extFile = File(extDocs, BACKUP_FILENAME)
                extFile.writeBytes(bytes)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. External app filesDir: context.getExternalFilesDir(null)
        try {
            val extFiles = context.getExternalFilesDir(null)
            if (extFiles != null) {
                if (!extFiles.exists()) extFiles.mkdirs()
                val extFile = File(extFiles, BACKUP_FILENAME)
                extFile.writeBytes(bytes)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. Public Downloads via MediaStore (Android 10-15 official Scoped Storage API - survives uninstall without permission!)
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, BACKUP_FILENAME)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AURALIX")
                }
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        out.write(bytes)
                        out.flush()
                    }
                }
            }
        } catch (e: Exception) {
            // Handled silently
        }

        // 5. Best-effort: Public Documents/AURALIX (survives app uninstall on permitted devices)
        try {
            val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val auralixDir = File(docs, "AURALIX")
            if (!auralixDir.exists()) {
                auralixDir.mkdirs()
            }
            val pubFile = File(auralixDir, BACKUP_FILENAME)
            pubFile.writeBytes(bytes)
        } catch (e: Exception) {
            // Silently handled: Scoped storage restrictions on modern Android
        }

        // 6. Best-effort: Public Downloads/auralix_backup.json
        try {
            val dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dlFile = File(dlDir, BACKUP_FILENAME)
            dlFile.writeBytes(bytes)
        } catch (e: Exception) {
            // Silently handled
        }
    }

    /**
     * Finds any existing backup file across app internal storage, external app storage, Documents, or Downloads.
     */
    fun findAnyExistingBackup(context: Context): File? {
        // 1. App internal filesDir (Most reliable)
        try {
            val internalFile = File(context.filesDir, BACKUP_FILENAME)
            if (internalFile.exists() && internalFile.length() > 0) return internalFile
        } catch (e: Exception) {}

        // 2. External app Documents
        try {
            val extDocs = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (extDocs != null) {
                val f = File(extDocs, BACKUP_FILENAME)
                if (f.exists() && f.length() > 0) return f
            }
        } catch (e: Exception) {}

        // 3. External app filesDir
        try {
            val extFiles = context.getExternalFilesDir(null)
            if (extFiles != null) {
                val f = File(extFiles, BACKUP_FILENAME)
                if (f.exists() && f.length() > 0) return f
            }
        } catch (e: Exception) {}

        // 4. Download/AURALIX/auralix_backup.json
        try {
            val dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dlAuralix = File(File(dlDir, "AURALIX"), BACKUP_FILENAME)
            if (dlAuralix.exists() && dlAuralix.length() > 0) return dlAuralix
        } catch (e: Exception) {}

        // 5. Documents/AURALIX/auralix_backup.json
        try {
            val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val auralixDir = File(docs, "AURALIX")
            val pub = File(auralixDir, BACKUP_FILENAME)
            if (pub.exists() && pub.length() > 0) return pub
        } catch (e: Exception) {}

        // 6. Download/auralix_backup.json
        try {
            val dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dlFile = File(dlDir, BACKUP_FILENAME)
            if (dlFile.exists() && dlFile.length() > 0) return dlFile
        } catch (e: Exception) {}

        // 7. Legacy backup filename
        try {
            val legacyInternal = File(context.filesDir, "auralix_drive_backup.json")
            if (legacyInternal.exists() && legacyInternal.length() > 0) return legacyInternal
        } catch (e: Exception) {}

        return null
    }

    private fun formatTimestamp(timeMs: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timeMs
        return when {
            diff < 60_000 -> "Just now"
            diff < 3600_000 -> "${diff / 60_000} mins ago"
            android.text.format.DateUtils.isToday(timeMs) -> {
                val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timeMs))
                "Today, $timeStr"
            }
            else -> SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault()).format(Date(timeMs))
        }
    }
}
