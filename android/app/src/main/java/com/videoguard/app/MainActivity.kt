package com.videoguard.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.videoguard.app.service.PlaybackController
import com.videoguard.app.ui.screens.MainScreen
import com.videoguard.app.ui.theme.VideoGuardTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        val incomingSharedPlaylist = kotlinx.coroutines.flow.MutableStateFlow<com.videoguard.app.model.UserPlaylist?>(null)
        val clipboardSharedPlaylist = kotlinx.coroutines.flow.MutableStateFlow<com.videoguard.app.model.UserPlaylist?>(null)
        private var lastCheckedClipboardText = ""
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission granted or denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDeepLinkIntent(intent)

        // Process-wide crash protector for UI, Compose, and media playback errors
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("AURALIX_CRASH", "Uncaught exception in thread ${thread.name}", throwable)
            val isRecoverable = throwable is IllegalStateException ||
                                throwable is IllegalArgumentException ||
                                throwable is IndexOutOfBoundsException ||
                                throwable is NullPointerException ||
                                throwable is NoSuchElementException ||
                                throwable is java.io.IOException
            if (isRecoverable) {
                android.util.Log.w("AURALIX_CRASH", "Suppressed non-fatal exception on ${thread.name}: ${throwable.message}")
            } else {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        // Initialize Coil with HighQualityArtworkInterceptor, MemoryCache, and DiskCache
        try {
            val imageLoader = coil.ImageLoader.Builder(this)
                .components {
                    add(com.videoguard.app.data.HighQualityArtworkInterceptor())
                }
                .memoryCache {
                    coil.memory.MemoryCache.Builder(this)
                        .maxSizePercent(0.25)
                        .build()
                }
                .diskCache {
                    coil.disk.DiskCache.Builder()
                        .directory(cacheDir.resolve("image_cache"))
                        .maxSizeBytes(128L * 1024 * 1024)
                        .build()
                }
                .crossfade(true)
                .build()
            coil.Coil.setImageLoader(imageLoader)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        PlaybackController.init(this)
        com.videoguard.app.data.AuthManager.init(this)
        com.videoguard.app.data.GoogleDriveBackupEngine.init(this)
        com.videoguard.app.data.ListenTogetherEngine.init(this)

        if (com.videoguard.app.data.AuthManager.verifySessionToken(this)) {
            CoroutineScope(Dispatchers.IO).launch {
                com.videoguard.app.data.GoogleDriveBackupEngine.checkAndAutoRestoreOnInstall(this@MainActivity)
                com.videoguard.app.data.YouTubeSyncEngine.syncAllUserData()
                com.videoguard.app.data.AppUpdateManager.checkForUpdates(this@MainActivity)
                com.videoguard.app.data.AppUpdateManager.startPeriodicChecks(this@MainActivity)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        enableEdgeToEdge()
        setContent {
            VideoGuardTheme {
                val isLoggedIn by com.videoguard.app.data.AuthManager.isLoggedIn.collectAsState()
                val isSessionValid = com.videoguard.app.data.AuthManager.verifySessionToken(this@MainActivity)
                if (isLoggedIn && isSessionValid) {
                    MainScreen()
                    com.videoguard.app.ui.components.AppUpdateDialog()
                } else {
                    com.videoguard.app.ui.screens.LoginScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkClipboardForSharedPlaylist()
        if (com.videoguard.app.data.AuthManager.verifySessionToken(this)) {
            if (com.videoguard.app.data.YouTubeSyncEngine.hasAuthCookies()) {
                CoroutineScope(Dispatchers.IO).launch {
                    com.videoguard.app.data.YouTubeSyncEngine.syncAllUserData()
                }
            }
            com.videoguard.app.data.AppUpdateManager.checkForUpdates(this)
        } else if (com.videoguard.app.data.AuthManager.isLoggedIn.value) {
            com.videoguard.app.data.AuthManager.logout(this)
        }
    }

    private fun checkClipboardForSharedPlaylist() {
        try {
            val clipManager = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return
            val clip = clipManager.primaryClip ?: return
            if (clip.itemCount > 0) {
                val rawText = clip.getItemAt(0)?.text?.toString()?.trim() ?: ""
                if (rawText.isNotBlank() && rawText != lastCheckedClipboardText) {
                    if (rawText.contains("p=") || rawText.contains("auralix") || rawText.contains("/playlist") || rawText.contains("spotify.com") || rawText.contains("youtube.com")) {
                        lastCheckedClipboardText = rawText
                        val playlist = com.videoguard.app.data.PlaylistShareEngine.parseTextForPlaylist(rawText)
                        if (playlist != null) {
                            clipboardSharedPlaylist.value = playlist
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: android.content.Intent?) {
        val uri = intent?.data ?: return
        try {
            val parsedPlaylist = com.videoguard.app.data.PlaylistShareEngine.parseIncomingDeepLink(uri)
            if (parsedPlaylist != null) {
                incomingSharedPlaylist.value = parsedPlaylist
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
