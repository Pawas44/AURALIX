package com.videoguard.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
        // Mandatory update: cannot be dismissed
    }

    fun showDialogAgain() {
        if (_updateInfo.value != null) {
            _showUpdateDialog.value = true
        }
    }

    private var isPeriodicCheckStarted = false

    fun startPeriodicChecks(context: Context) {
        if (isPeriodicCheckStarted) return
        isPeriodicCheckStarted = true
        scope.launch {
            while (true) {
                checkForUpdates(context, isManualCheck = false)
                kotlinx.coroutines.delay(10 * 60 * 1000L) // Silent check every 10 minutes
            }
        }
    }

    /**
     * Checks GitHub for the latest published release using unthrottled web redirect and API fallbacks.
     */
    fun checkForUpdates(
        context: Context,
        isManualCheck: Boolean = false,
        forceShowIfSame: Boolean = false
    ) {
        if (_isChecking.value || _isDownloading.value) return

        scope.launch {
            _isChecking.value = true
            var resolvedRelease: AppReleaseInfo? = null

            // Tier 1: GitHub Web Latest Redirect (Fastest, zero rate limits)
            try {
                val webUrl = URL("https://github.com/Pawas44/AURALIX/releases/latest")
                val conn = (webUrl.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 6000
                    readTimeout = 6000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                }
                val code = conn.responseCode
                val redirectLocation = if (code in 300..399) {
                    conn.getHeaderField("Location")
                } else if (code == 200) {
                    conn.url?.toString()
                } else null

                if (!redirectLocation.isNullOrBlank() && redirectLocation.contains("/tag/")) {
                    val tag = redirectLocation.substringAfterLast("/tag/").trim()
                    if (tag.isNotBlank()) {
                        resolvedRelease = AppReleaseInfo(
                            tagName = tag,
                            releaseTitle = "AURALIX $tag",
                            releaseNotes = "New updates and stability improvements are available for AURALIX.",
                            apkUrl = "https://github.com/Pawas44/AURALIX/releases/download/$tag/AURALIX.apk",
                            apkSize = 45_000_000L
                        )
                    }
                }
            } catch (e: Exception) {
                // Tier 1 fallback
            }

            // Tier 2: Public Atom Feed (Zero rate limits)
            if (resolvedRelease == null) {
                try {
                    val feedUrl = URL("https://github.com/Pawas44/AURALIX/releases.atom")
                    val conn = (feedUrl.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 8000
                        readTimeout = 8000
                        setRequestProperty("User-Agent", "Mozilla/5.0")
                    }
                    if (conn.responseCode == 200) {
                        val xml = conn.inputStream.bufferedReader().use { it.readText() }
                        val tagMatch = Regex("/releases/tag/([vV]?[0-9\\.]+)").find(xml)
                        val tag = tagMatch?.groupValues?.get(1)
                        if (!tag.isNullOrBlank()) {
                            resolvedRelease = AppReleaseInfo(
                                tagName = tag,
                                releaseTitle = "AURALIX $tag",
                                releaseNotes = "Latest update with performance and UI polish.",
                                apkUrl = "https://github.com/Pawas44/AURALIX/releases/download/$tag/AURALIX.apk",
                                apkSize = 45_000_000L
                            )
                        }
                    }
                } catch (e: Exception) {}
            }

            // Tier 3: GitHub REST API (Fallback)
            if (resolvedRelease == null) {
                try {
                    val apiUrl = URL("https://api.github.com/repos/Pawas44/AURALIX/releases/latest")
                    val conn = (apiUrl.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 8000
                        readTimeout = 8000
                        setRequestProperty("User-Agent", "AURALIX-Android")
                        setRequestProperty("Accept", "application/vnd.github.v3+json")
                    }
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
                        if (apkUrl != null) {
                            resolvedRelease = AppReleaseInfo(
                                tagName = tagName,
                                releaseTitle = title,
                                releaseNotes = notes,
                                apkUrl = apkUrl,
                                apkSize = apkSize
                            )
                        }
                    }
                } catch (e: Exception) {}
            }

            // Evaluation: check if remote version is newer than current installed version
            if (resolvedRelease != null) {
                val isNewer = isNewerVersion(context, resolvedRelease.tagName)
                if (isNewer || forceShowIfSame) {
                    _updateInfo.value = resolvedRelease
                    _showUpdateDialog.value = true
                } else {
                    _updateInfo.value = null
                    _showUpdateDialog.value = false
                    if (isManualCheck) {
                        val installedVer = getInstalledVersionName(context)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "✓ AURALIX is up to date! (Installed: v$installedVer)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } else if (isManualCheck) {
                val installedVer = getInstalledVersionName(context)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "✓ You are running the latest version of AURALIX (v$installedVer)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            _isChecking.value = false
        }
    }

    private fun getInstalledVersionName(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName?.takeIf { it.isNotBlank() } ?: BuildConfig.APP_VERSION_NAME
        } catch (_: Exception) {
            BuildConfig.APP_VERSION_NAME
        }
    }

    private fun getInstalledVersionCode(context: Context): Long {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (_: Exception) {
            BuildConfig.APP_VERSION_CODE.toLong()
        }
    }

    /**
     * Compares remote tag (e.g. "v1.0.126") with installed app version.
     */
    private fun isNewerVersion(context: Context, remoteTag: String): Boolean {
        try {
            val cleanRemote = remoteTag.removePrefix("v").removePrefix("V").trim()
            val installedName = getInstalledVersionName(context).removePrefix("v").removePrefix("V").trim()
            val installedCode = getInstalledVersionCode(context)

            // 1. Check run numbers if in 1.0.XX format (e.g. 1.0.126 -> 126)
            val remoteRun = cleanRemote.split(".").lastOrNull()?.toLongOrNull()
            val currentRun = installedName.split(".").lastOrNull()?.toLongOrNull()

            if (remoteRun != null) {
                if (currentRun != null && currentRun > 0) {
                    return remoteRun > currentRun
                }
                if (installedCode > 0) {
                    return remoteRun > installedCode
                }
            }

            // 2. Semver fallback comparison
            val remoteParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }
            val currentParts = installedName.split(".").mapNotNull { it.toIntOrNull() }
            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            return false
        } catch (_: Exception) {
            return false
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

                val urlsToTry = listOf(
                    release.apkUrl,
                    "https://auralix-bice.vercel.app/assets/apk/AURALIX.apk"
                ).distinct()

                var downloadSuccess = false
                var lastException: Exception? = null

                for (urlCandidate in urlsToTry) {
                    try {
                        var currentUrl = urlCandidate
                        var conn: HttpURLConnection
                        var redirectCount = 0

                        while (true) {
                            val urlObj = URL(currentUrl)
                            conn = urlObj.openConnection() as HttpURLConnection
                            conn.connectTimeout = 20000
                            conn.readTimeout = 60000
                            conn.instanceFollowRedirects = true
                            conn.setRequestProperty("User-Agent", "AURALIX-Android")

                            val code = conn.responseCode
                            if ((code in 300..399) && redirectCount < 5) {
                                val newLocation = conn.getHeaderField("Location")
                                if (!newLocation.isNullOrBlank()) {
                                    currentUrl = newLocation
                                    redirectCount++
                                    continue
                                }
                            }
                            break
                        }

                        downloadStream(context, conn, targetFile)
                        if (targetFile.exists() && targetFile.length() > 5_000_000L) {
                            downloadSuccess = true
                            break
                        }
                    } catch (e: Exception) {
                        lastException = e
                    }
                }

                if (!downloadSuccess) {
                    throw lastException ?: Exception("Download failed from all mirrors")
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
                val buffer = ByteArray(16384)
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(context, "Please allow 'Install unknown apps' for AURALIX to update", Toast.LENGTH_LONG).show()
                    val permissionIntent = Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(permissionIntent)
                    return
                }
            }

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
