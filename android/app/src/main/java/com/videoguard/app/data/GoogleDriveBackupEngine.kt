package com.videoguard.app.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.videoguard.app.model.UserPlaylist
import com.videoguard.app.model.VideoItem
import com.videoguard.app.recommendation.RecommendationEngine
import com.videoguard.app.recommendation.UserTasteProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    val lastBackupFormatted: String = "Never",
    val backupSizeBytes: Long = 0L,
    val isBackingUp: Boolean = false,
    val isRestoring: Boolean = false,
    val autoBackupDaily: Boolean = true
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

    private const val PREFS_NAME = "auralix_cloud_backup_prefs"
    private const val KEY_LAST_BACKUP_TIME = "last_backup_time"
    private const val KEY_LAST_BACKUP_SIZE = "last_backup_size"
    private const val KEY_AUTO_BACKUP = "auto_backup_daily"
    private const val LOCAL_BACKUP_FILENAME = "auralix_drive_backup.json"

    private var prefs: SharedPreferences? = null

    private val _backupState = MutableStateFlow(BackupState())
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp

        val lastTime = sp.getLong(KEY_LAST_BACKUP_TIME, 0L)
        val lastSize = sp.getLong(KEY_LAST_BACKUP_SIZE, 0L)
        val autoBackup = sp.getBoolean(KEY_AUTO_BACKUP, true)

        val formattedDate = if (lastTime > 0) formatTimestamp(lastTime) else "Never"
        _backupState.value = BackupState(
            lastBackupFormatted = formattedDate,
            backupSizeBytes = lastSize,
            autoBackupDaily = autoBackup
        )
    }

    fun setAutoBackup(enabled: Boolean) {
        _backupState.value = _backupState.value.copy(autoBackupDaily = enabled)
        prefs?.edit()?.putBoolean(KEY_AUTO_BACKUP, enabled)?.apply()
    }

    /**
     * Serializes complete taste profile, playlists, and history into a clean JSON payload.
     */
    fun createBackupJson(): JSONObject {
        val root = JSONObject()
        root.put("app", "AURALIX")
        root.put("version", 1)
        root.put("timestamp", System.currentTimeMillis())
        root.put("formattedDate", SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault()).format(Date()))

        // 1. Personalized Taste Profile (artists, affinities, hidden tracks)
        root.put("tasteProfile", UserTasteProfile.get().exportTasteProfileJson())

        // 2. Playlists
        val playlistsArr = JSONArray()
        UserDataRepository.playlists.value.forEach {
            playlistsArr.put(UserDataRepository.playlistToJson(it))
        }
        root.put("playlists", playlistsArr)

        // 3. Listening / Watch History
        val historyArr = JSONArray()
        UserDataRepository.history.value.forEach {
            historyArr.put(UserDataRepository.videoToJson(it))
        }
        root.put("history", historyArr)

        return root
    }

    /**
     * Writes backup JSON to given OutputStream (e.g. Google Drive document URI via SAF).
     */
    suspend fun writeBackupToStream(context: Context, outputStream: OutputStream?): Boolean = withContext(Dispatchers.IO) {
        if (outputStream == null) return@withContext false
        try {
            _backupState.value = _backupState.value.copy(isBackingUp = true)

            val json = createBackupJson()
            val bytes = json.toString(2).toByteArray(Charsets.UTF_8)

            outputStream.use { out ->
                out.write(bytes)
                out.flush()
            }

            // Also keep a local snapshot in app internal files directory
            saveLocalSnapshot(context, bytes)

            // Update metadata
            val now = System.currentTimeMillis()
            val formatted = formatTimestamp(now)
            prefs?.edit()
                ?.putLong(KEY_LAST_BACKUP_TIME, now)
                ?.putLong(KEY_LAST_BACKUP_SIZE, bytes.size.toLong())
                ?.apply()

            _backupState.value = _backupState.value.copy(
                lastBackupFormatted = formatted,
                backupSizeBytes = bytes.size.toLong(),
                isBackingUp = false
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            _backupState.value = _backupState.value.copy(isBackingUp = false)
            false
        }
    }

    /**
     * Performs an immediate silent backup to app internal storage.
     */
    suspend fun backupLocally(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            _backupState.value = _backupState.value.copy(isBackingUp = true)
            val json = createBackupJson()
            val bytes = json.toString(2).toByteArray(Charsets.UTF_8)
            saveLocalSnapshot(context, bytes)

            val now = System.currentTimeMillis()
            val formatted = formatTimestamp(now)
            prefs?.edit()
                ?.putLong(KEY_LAST_BACKUP_TIME, now)
                ?.putLong(KEY_LAST_BACKUP_SIZE, bytes.size.toLong())
                ?.apply()

            _backupState.value = _backupState.value.copy(
                lastBackupFormatted = formatted,
                backupSizeBytes = bytes.size.toLong(),
                isBackingUp = false
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            _backupState.value = _backupState.value.copy(isBackingUp = false)
            false
        }
    }

    /**
     * Restores complete data from given InputStream (Google Drive document or local file).
     */
    suspend fun restoreFromStream(inputStream: InputStream?): Boolean = withContext(Dispatchers.IO) {
        if (inputStream == null) return@withContext false
        try {
            _backupState.value = _backupState.value.copy(isRestoring = true)

            val content = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(content)

            // 1. Restore Taste Profile
            root.optJSONObject("tasteProfile")?.let {
                UserTasteProfile.get().importTasteProfileJson(it)
            }

            // 2. Restore Playlists
            val playlistsArr = root.optJSONArray("playlists")
            val restoredPlaylists = mutableListOf<UserPlaylist>()
            if (playlistsArr != null) {
                for (i in 0 until playlistsArr.length()) {
                    playlistsArr.optJSONObject(i)?.let {
                        restoredPlaylists.add(UserDataRepository.jsonToPlaylist(it))
                    }
                }
            }

            // 3. Restore History
            val historyArr = root.optJSONArray("history")
            val restoredHistory = mutableListOf<VideoItem>()
            if (historyArr != null) {
                for (i in 0 until historyArr.length()) {
                    historyArr.optJSONObject(i)?.let {
                        restoredHistory.add(UserDataRepository.jsonToVideo(it))
                    }
                }
            }

            UserDataRepository.restoreFullData(restoredHistory, restoredPlaylists)

            // 4. Force refresh home recommendation feeds with restored taste profile
            RecommendationEngine.refreshHomeFeed(forceRefresh = true)

            _backupState.value = _backupState.value.copy(isRestoring = false)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            _backupState.value = _backupState.value.copy(isRestoring = false)
            false
        }
    }

    /**
     * Checks if a local backup file exists in app storage.
     */
    fun hasLocalBackup(context: Context): Boolean {
        val file = File(context.filesDir, LOCAL_BACKUP_FILENAME)
        return file.exists() && file.length() > 0
    }

    suspend fun restoreFromLocalBackup(context: Context): Boolean = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, LOCAL_BACKUP_FILENAME)
        if (!file.exists()) return@withContext false
        restoreFromStream(file.inputStream())
    }

    private fun saveLocalSnapshot(context: Context, bytes: ByteArray) {
        try {
            val file = File(context.filesDir, LOCAL_BACKUP_FILENAME)
            file.writeBytes(bytes)
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
