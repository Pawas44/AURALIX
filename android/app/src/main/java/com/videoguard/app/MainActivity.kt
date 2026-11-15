package com.videoguard.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.videoguard.app.service.PlaybackController
import com.videoguard.app.ui.screens.MainScreen
import com.videoguard.app.ui.theme.VideoGuardTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission granted or denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        PlaybackController.init(this)

        CoroutineScope(Dispatchers.IO).launch {
            com.videoguard.app.data.YouTubeSyncEngine.syncAllUserData()
            com.videoguard.app.data.AppUpdateManager.checkForUpdates(this@MainActivity)
            com.videoguard.app.data.AppUpdateManager.startPeriodicChecks(this@MainActivity)
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
                MainScreen()
                com.videoguard.app.ui.components.AppUpdateDialog()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (com.videoguard.app.data.YouTubeSyncEngine.hasAuthCookies()) {
            CoroutineScope(Dispatchers.IO).launch {
                com.videoguard.app.data.YouTubeSyncEngine.syncAllUserData()
            }
        }
        com.videoguard.app.data.AppUpdateManager.checkForUpdates(this)
    }
}
