package com.videoguard.app.ui.components

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.videoguard.app.data.SpotifyConnectEngine
import com.videoguard.app.ui.SpotifyAuthActivity

@Composable
fun SpotifyLoginDialog(
    onDismiss: () -> Unit,
    onLoginSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val token = SpotifyConnectEngine.getSpDcCookie() ?: ""
            onLoginSuccess(token)
        }
        onDismiss()
    }

    LaunchedEffect(Unit) {
        launcher.launch(Intent(context, SpotifyAuthActivity::class.java))
    }
}
