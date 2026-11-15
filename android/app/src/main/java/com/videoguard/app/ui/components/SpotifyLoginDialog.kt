package com.videoguard.app.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.videoguard.app.data.SpotifyConnectEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpotifyLoginDialog(
    onDismiss: () -> Unit,
    onLoginSuccess: (String) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var currentUrl by remember { mutableStateOf("https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F") }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var showManualCookieInput by remember { mutableStateOf(false) }
    var manualCookieText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var isCaptured by remember { mutableStateOf(false) }

    fun checkAndExtractCookie(view: WebView?) {
        if (isCaptured) return
        try {
            val cm = CookieManager.getInstance()
            val c1 = cm.getCookie("https://open.spotify.com") ?: ""
            val c2 = cm.getCookie("https://spotify.com") ?: ""
            val c3 = cm.getCookie("https://accounts.spotify.com") ?: ""
            val fullCookies = "$c1; $c2; $c3"

            val match = Regex("""(?:^|;\s*)sp_dc=([^\s;]+)""").find(fullCookies)
            val spDc = match?.groupValues?.get(1)
            if (!spDc.isNullOrBlank() && spDc.length >= 20) {
                isCaptured = true
                SpotifyConnectEngine.saveSession(spDc)
                scope.launch(Dispatchers.IO) {
                    SpotifyConnectEngine.syncAll()
                }
                scope.launch(Dispatchers.Main) {
                    onLoginSuccess(spDc)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF121212)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF181818))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Secure",
                        tint = Color(0xFF1DB954),
                        modifier = Modifier.size(16.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Connect Spotify",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (currentUrl.contains("accounts.spotify.com")) "accounts.spotify.com" else "open.spotify.com",
                            color = Color(0xFFAAAAAA),
                            fontSize = 11.sp
                        )
                    }

                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color(0xFF1DB954),
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { webViewInstance?.reload() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Done / Check Button
                    IconButton(
                        onClick = {
                            CookieManager.getInstance().flush()
                            checkAndExtractCookie(webViewInstance)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Check Login",
                            tint = Color(0xFF1DB954),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Interactive WebView
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                webViewInstance = this
                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        url?.let { currentUrl = it }
                                        isLoading = true
                                        checkAndExtractCookie(view)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        url?.let { currentUrl = it }
                                        isLoading = false
                                        CookieManager.getInstance().flush()
                                        checkAndExtractCookie(view)
                                    }
                                }

                                loadUrl(currentUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Bottom bar with manual token option
                Surface(
                    color = Color(0xFF181818),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Sign in to your Spotify account above",
                                color = Color(0xFF888888),
                                fontSize = 11.sp
                            )
                            TextButton(
                                onClick = { showManualCookieInput = !showManualCookieInput },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = if (showManualCookieInput) "Hide" else "Paste sp_dc",
                                    color = Color(0xFF1DB954),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        if (showManualCookieInput) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = manualCookieText,
                                    onValueChange = { manualCookieText = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = {
                                        Text("Paste sp_dc cookie value...", fontSize = 11.sp, color = Color(0xFF666666))
                                    },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF1DB954),
                                        unfocusedBorderColor = Color(0xFF444444)
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val clean = manualCookieText.trim().removePrefix("sp_dc=").trim()
                                        if (clean.length >= 20) {
                                            SpotifyConnectEngine.saveSession(clean)
                                            scope.launch(Dispatchers.IO) {
                                                SpotifyConnectEngine.syncAll()
                                            }
                                            scope.launch(Dispatchers.Main) {
                                                onLoginSuccess(clean)
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Connect", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
