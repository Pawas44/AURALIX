package com.videoguard.app.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
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
    val isDriveConnected: Boolean = false,
    val linkedFolderName: String = "AURALIX Data",
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
    private const val KEY_LINKED_TREE_URI = "linked_tree_uri"
    private const val KEY_LAST_BACKUP_TIME = "last_backup_time"
    private const val KEY_LAST_BACKUP_SIZE = "last_backup_size"
    private const val KEY_AUTO_BACKUP = "auto_backup_daily"

    const val CLOUD_FOLDER_NAME = "AURALIX Data"
    private const val BACKUP_FILENAME = "auralix_backup.json"
    private const val LOCAL_BACKUP_FILENAME = "auralix_drive_backup.json"

    private var prefs: SharedPreferences? = null

    private val _backupState = MutableStateFlow(BackupState())
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp

        val savedTreeUri = sp.getString(KEY_LINKED_TREE_URI, null)
        val lastTime = sp.getLong(KEY_LAST_BACKUP_TIME, 0L)
        val lastSize = sp.getLong(KEY_LAST_BACKUP_SIZE, 0L)
        val autoBackup = sp.getBoolean(KEY_AUTO_BACKUP, true)

        val hasValidTree = !savedTreeUri.isNullOrBlank()
        val formattedDate = if (lastTime > 0) formatTimestamp(lastTime) else "Never"

        _backupState.value = BackupState(
            isDriveConnected = hasValidTree,
            linkedFolderName = CLOUD_FOLDER_NAME,
            lastBackupFormatted = formattedDate,
            backupSizeBytes = lastSize,
            autoBackupDaily = autoBackup
        )
    }

    /**
     * Connects Google Drive root folder chosen once by user, takes persistent permissions,
     * automatically creates "AURALIX Data" folder, and triggers the first silent backup.
     */
    suspend fun linkDriveFolder(context: Context, treeUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Take persistent permissions
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(treeUri, flags)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            prefs?.edit()?.putString(KEY_LINKED_TREE_URI, treeUri.toString())?.apply()

            // 2. Resolve or create "AURALIX Data" directory
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext false
            var auralixFolder = rootDoc.findFile(CLOUD_FOLDER_NAME)
            if (auralixFolder == null || !auralixFolder.isDirectory) {
                auralixFolder = rootDoc.createDirectory(CLOUD_FOLDER_NAME)
            }

            if (auralixFolder == null) {
                return@withContext false
            }

            _backupState.value = _backupState.value.copy(
                isDriveConnected = true,
                linkedFolderName = CLOUD_FOLDER_NAME
            )

            // 3. Immediately perform initial backup silently into "AURALIX Data"
            backupToDrive(context)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Disconnects / unlinks the current Drive folder so the user can switch accounts or folders.
     */
    fun unlinkDrive() {
        prefs?.edit()?.remove(KEY_LINKED_TREE_URI)?.apply()
        _backupState.value = _backupState.value.copy(
            isDriveConnected = false,
            lastBackupFormatted = "Never",
            backupSizeBytes = 0L
        )
    }

    /**
     * 100% SILENT DIRECT CLOUD BACKUP
     * Automatically writes recommendations, history, and playlists into the "AURALIX Data" folder
     * on the user's Google Drive. NO Files app opens!
     */
    suspend fun backupToDrive(context: Context): Boolean = withContext(Dispatchers.IO) {
        val sp = prefs ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val treeUriStr = sp.getString(KEY_LINKED_TREE_URI, null) ?: return@withContext false
        val treeUri = Uri.parse(treeUriStr)

        try {
            _backupState.value = _backupState.value.copy(isBackingUp = true)

            val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            if (rootDoc == null) {
                _backupState.value = _backupState.value.copy(isBackingUp = false)
                return@withContext false
            }

            var auralixFolder = rootDoc.findFile(CLOUD_FOLDER_NAME)
            if (auralixFolder == null || !auralixFolder.isDirectory) {
                auralixFolder = rootDoc.createDirectory(CLOUD_FOLDER_NAME)
            }

            if (auralixFolder == null) {
                _backupState.value = _backupState.value.copy(isBackingUp = false)
                return@withContext false
            }

            // Find or create auralix_backup.json
            var backupFile = auralixFolder.findFile(BACKUP_FILENAME)
            if (backupFile == null || !backupFile.isFile) {
                backupFile = auralixFolder.createFile("application/json", BACKUP_FILENAME)
            }

            if (backupFile == null) {
                _backupState.value = _backupState.value.copy(isBackingUp = false)
                return@withContext false
            }

            val json = createBackupJson()
            val bytes = json.toString(2).toByteArray(Charsets.UTF_8)

            context.contentResolver.openOutputStream(backupFile.uri, "wt")?.use { out ->
                out.write(bytes)
                out.flush()
            }

            // Save local cache snapshot as well
            saveLocalSnapshot(context, bytes)

            val now = System.currentTimeMillis()
            val formatted = formatTimestamp(now)
            sp.edit()
                .putLong(KEY_LAST_BACKUP_TIME, now)
                .putLong(KEY_LAST_BACKUP_SIZE, bytes.size.toLong())
                .apply()

            _backupState.value = _backupState.value.copy(
                isDriveConnected = true,
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
     * 100% SILENT DIRECT CLOUD RESTORE
     * Automatically reads the latest backup directly from "AURALIX Data" on Google Drive.
     * NO Files app opens!
     */
    suspend fun restoreFromDrive(context: Context): Boolean = withContext(Dispatchers.IO) {
        val sp = prefs ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val treeUriStr = sp.getString(KEY_LINKED_TREE_URI, null) ?: return@withContext false
        val treeUri = Uri.parse(treeUriStr)

        try {
            _backupState.value = _backupState.value.copy(isRestoring = true)

            val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            if (rootDoc == null) {
                _backupState.value = _backupState.value.copy(isRestoring = false)
                return@withContext false
            }

            val auralixFolder = rootDoc.findFile(CLOUD_FOLDER_NAME)
            val backupFile = auralixFolder?.findFile(BACKUP_FILENAME)

            if (backupFile == null) {
                _backupState.value = _backupState.value.copy(isRestoring = false)
                return@withContext false
            }

            val inputStream = context.contentResolver.openInputStream(backupFile.uri)
            val success = restoreFromStream(inputStream)

            _backupState.value = _backupState.value.copy(isRestoring = false)
            success
        } catch (e: Exception) {
            e.printStackTrace()
            _backupState.value = _backupState.value.copy(isRestoring = false)
            false
        }
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
     * Fallback manual export via SAF stream.
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
     * Restores complete data from given InputStream.
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
