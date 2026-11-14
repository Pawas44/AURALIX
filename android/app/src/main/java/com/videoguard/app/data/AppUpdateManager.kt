package com.videoguard.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.videoguard.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class AppReleaseInfo(
    val tagName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val apkUrl: String,
    val apkSize: Long
)

object AppUpdateManager {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _updateInfo = MutableStateFlow<AppReleaseInfo?>(null)
    val updateInfo = _updateInfo.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking = _isChecking.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _downloadBytesText = MutableStateFlow("")
    val downloadBytesText = _downloadBytesText.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog = _showUpdateDialog.asStateFlow()

    fun dismissDialog() {
        _showUpdateDialog.value = false
    }

    /**
     * Checks GitHub API for the latest published release.
     */
    fun checkForUpdates(context: Context, isManualCheck: Boolean = false) {
        if (_isChecking.value || _isDownloading.value) return

        scope.launch {
            _isChecking.value = true
            try {
                val apiUrl = URL("https://api.github.com/repos/Pawas44/AURALIX/releases/latest")
                val conn = apiUrl.openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("User-Agent", "AURALIX-Android")
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")

                if (conn.responseCode == 200) {
                    val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseText)
                    val tagName = json.optString("tag_name", "")
                    val title = json.optString("name", tagName)
                    val notes = json.optString("body", "")

                    var apkUrl: String? = null
                    var apkSize: Long = 0

                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.optJSONObject(i) ?: continue
                            val assetName = asset.optString("name", "")
                            if (assetName.endsWith(".apk", ignoreCase = true)) {
                                apkUrl = asset.optString("browser_download_url")
                                apkSize = asset.optLong("size", 0)
                                break
                            }
                        }
                    }

                    if (apkUrl != null && isNewerVersion(tagName)) {
                        val release = AppReleaseInfo(
                            tagName = tagName,
                            releaseTitle = title,
                            releaseNotes = notes,
                            apkUrl = apkUrl,
                            apkSize = apkSize
                        )
                        _updateInfo.value = release
                        _showUpdateDialog.value = true
                    } else if (isManualCheck) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "AURALIX is up to date (${BuildConfig.APP_VERSION_NAME}) ✓",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else if (isManualCheck) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Could not check for updates (HTTP ${conn.responseCode})", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                if (isManualCheck) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Update check failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                _isChecking.value = false
            }
        }
    }

    /**
     * Compares remote tag (e.g. "v1.0.89") with current build code/name.
     */
    private fun isNewerVersion(remoteTag: String): Boolean {
        val cleanRemote = remoteTag.removePrefix("v").trim()
        val currentName = BuildConfig.APP_VERSION_NAME.removePrefix("v").trim()

        // Extract run numbers if in 1.0.XX format
        val remoteRun = cleanRemote.split(".").lastOrNull()?.toIntOrNull()
        val currentRun = currentName.split(".").lastOrNull()?.toIntOrNull()

        return if (remoteRun != null && currentRun != null) {
            remoteRun > currentRun
        } else {
            cleanRemote != currentName
        }
    }

    /**
     * Downloads the APK file and triggers the native Android package installer.
     */
    fun downloadAndInstallUpdate(context: Context) {
        val release = _updateInfo.value ?: return
        if (_isDownloading.value) return

        scope.launch {
            _isDownloading.value = true
            _downloadProgress.value = 0
            _downloadBytesText.value = "Starting download..."

            try {
                val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
                if (!targetDir.exists()) targetDir.mkdirs()
                val targetFile = File(targetDir, "AURALIX_update.apk")
                if (targetFile.exists()) targetFile.delete()

                val url = URL(release.apkUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "AURALIX-Android")

                val code = conn.responseCode
                if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP || code == 307 || code == 308) {
                    val redirectUrl = conn.getHeaderField("Location")
                    val redirectConn = URL(redirectUrl).openConnection() as HttpURLConnection
                    redirectConn.connectTimeout = 15000
                    redirectConn.readTimeout = 30000
                    downloadStream(context, redirectConn, targetFile)
                } else {
                    downloadStream(context, conn, targetFile)
                }

                // Download completed, launch Android Package Installer
                _isDownloading.value = false
                _downloadProgress.value = 100
                _downloadBytesText.value = "Download complete! Opening installer..."

                withContext(Dispatchers.Main) {
                    launchPackageInstaller(context, targetFile)
                }
            } catch (e: Exception) {
                _isDownloading.value = false
                _downloadBytesText.value = "Download failed: ${e.message}"
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Update download failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun downloadStream(context: Context, conn: HttpURLConnection, targetFile: File) {
        val totalLength = conn.contentLength.toLong()
        var downloadedBytes = 0L

        conn.inputStream.use { input ->
            FileOutputStream(targetFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var lastUpdate = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastUpdate > 100) {
                        lastUpdate = now
                        val percent = if (totalLength > 0) {
                            ((downloadedBytes * 100) / totalLength).toInt().coerceIn(1, 99)
                        } else 50

                        val mbDownloaded = String.format(java.util.Locale.US, "%.1f", downloadedBytes / (1024f * 1024f))
                        val mbTotal = if (totalLength > 0) {
                            String.format(java.util.Locale.US, "%.1f MB", totalLength / (1024f * 1024f))
                        } else "..."

                        _downloadProgress.value = percent
                        _downloadBytesText.value = "$mbDownloaded MB / $mbTotal ($percent%)"
                    }
                }
            }
        }
    }

    private fun launchPackageInstaller(context: Context, apkFile: File) {
        try {
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
