package com.videoguard.app.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.videoguard.app.data.SpotifyConnectEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SPOTIFY_ACCOUNTS_URL =
    "https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F"

private const val SPOTIFY_WEB_PLAYER_URL =
    "https://open.spotify.com"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpotifyLoginDialog(
    onDismiss: () -> Unit,
    onLoginSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    val localView = LocalView.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var currentUrl by remember { mutableStateOf(SPOTIFY_ACCOUNTS_URL) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isCaptured by remember { mutableStateOf(false) }
    var webErrorDesc by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var tokenInput by remember { mutableStateOf("") }
    var pageLoadTimer by remember { mutableIntStateOf(0) }

    // Ensure Dialog window has Hardware Acceleration enabled to prevent black screen bugs
    DisposableEffect(localView) {
        var p = localView.parent
        while (p != null) {
            if (p is DialogWindowProvider) {
                p.window.setFlags(
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                )
                break
            }
            p = p.parent
        }
        onDispose {}
    }

    // Ensure engine is initialized
    LaunchedEffect(Unit) {
        SpotifyConnectEngine.init(context)
    }

    // Track load timer to show helpful alternative pill if accounts page is delayed
    LaunchedEffect(isLoading) {
        if (isLoading) {
            pageLoadTimer = 0
            while (isLoading && pageLoadTimer < 8) {
                delay(1000L)
                pageLoadTimer++
            }
        }
    }

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
                    Toast.makeText(context, "Spotify connected successfully!", Toast.LENGTH_SHORT).show()
                    onLoginSuccess(spDc)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Auto-check for login cookie every 500ms
    LaunchedEffect(isCaptured) {
        while (!isCaptured) {
            delay(500L)
            checkAndExtractCookie(webViewInstance)
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
                // Header Bar
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
                        val host = try {
                            Uri.parse(currentUrl).host ?: "accounts.spotify.com"
                        } catch (e: Exception) {
                            "accounts.spotify.com"
                        }
                        Text(
                            text = if (isCaptured) "Connected!" else host,
                            color = if (isCaptured) Color(0xFF1DB954) else Color(0xFFAAAAAA),
                            fontSize = 11.sp
                        )
                    }

                    // Reload Button
                    IconButton(onClick = {
                        webErrorDesc = null
                        isLoading = true
                        webViewInstance?.reload()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Check / Done Button (Manual cookie flush & verify)
                    IconButton(
                        onClick = {
                            CookieManager.getInstance().flush()
                            checkAndExtractCookie(webViewInstance)
                            if (!isCaptured) {
                                Toast.makeText(context, "Checking login status...", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Done",
                            tint = Color(0xFF1DB954),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Overflow Menu
                    Box {
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = Color(0xFFAAAAAA),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(Color(0xFF242424))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open in Browser", color = Color.White) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        tint = Color(0xFFAAAAAA)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (currentUrl.contains("open.spotify.com")) "Load Accounts Login" else "Load Web Player",
                                        color = Color.White
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = null,
                                        tint = Color(0xFFAAAAAA)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    val target = if (currentUrl.contains("open.spotify.com")) SPOTIFY_ACCOUNTS_URL else SPOTIFY_WEB_PLAYER_URL
                                    currentUrl = target
                                    webViewInstance?.loadUrl(target)
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Paste Token (Manual)", color = Color.White) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = null,
                                        tint = Color(0xFF1DB954)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showTokenDialog = true
                                }
                            )

                            HorizontalDivider(color = Color(0xFF383838), modifier = Modifier.padding(vertical = 4.dp))

                            DropdownMenuItem(
                                text = { Text("Log Out & Switch Account", color = Color(0xFFFF6B6B)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = "Log Out",
                                        tint = Color(0xFFFF6B6B)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    try {
                                        CookieManager.getInstance().removeAllCookies(null)
                                        CookieManager.getInstance().flush()
                                        WebStorage.getInstance().deleteAllData()
                                        webViewInstance?.clearCache(true)
                                        webViewInstance?.clearHistory()
                                    } catch (e: Exception) {}
                                    SpotifyConnectEngine.disconnect(clearCookies = true)
                                    isCaptured = false
                                    currentUrl = "https://accounts.spotify.com/en/logout"
                                    webViewInstance?.loadUrl("https://accounts.spotify.com/en/logout")
                                    Toast.makeText(context, "Logged out from Spotify. You can now log into a different account.", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }

                // Smooth Progress Bar
                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { (loadProgress.coerceIn(5, 100)) / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp),
                        color = Color(0xFF1DB954),
                        trackColor = Color(0xFF181818)
                    )
                }

                // Error Banner if network fails
                if (webErrorDesc != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        color = Color(0xFF331515),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Failed to load: $webErrorDesc",
                                color = Color(0xFFFF8888),
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    webErrorDesc = null
                                    isLoading = true
                                    webViewInstance?.reload()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Retry", fontSize = 11.sp, color = Color.Black)
                            }
                        }
                    }
                }

                // Quick Mode Switch Bar (Allows instant 1-tap toggle between Accounts and Web Player)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF202020))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val isAccounts = currentUrl.contains("accounts.spotify.com")
                        Surface(
                            onClick = {
                                currentUrl = SPOTIFY_ACCOUNTS_URL
                                webViewInstance?.loadUrl(SPOTIFY_ACCOUNTS_URL)
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isAccounts) Color(0xFF1DB954) else Color(0xFF2C2C2C)
                        ) {
                            Text(
                                text = "Accounts Login",
                                color = if (isAccounts) Color.Black else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        val isWebPlayer = currentUrl.contains("open.spotify.com")
                        Surface(
                            onClick = {
                                currentUrl = SPOTIFY_WEB_PLAYER_URL
                                webViewInstance?.loadUrl(SPOTIFY_WEB_PLAYER_URL)
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isWebPlayer) Color(0xFF1DB954) else Color(0xFF2C2C2C)
                        ) {
                            Text(
                                text = "Web Player",
                                color = if (isWebPlayer) Color.Black else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = "Paste Token",
                        color = Color(0xFF1DB954),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable { showTokenDialog = true }
                            .padding(4.dp)
                    )
                }

                // The Interactive WebView
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(android.graphics.Color.parseColor("#121212"))
                            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                            webViewInstance = this

                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            // Sanitize User-Agent to mirror authentic Chrome while omitting WebView indicators
                            val defaultUa = WebSettings.getDefaultUserAgent(ctx)
                            val cleanChromeUa = defaultUa
                                .replace("; wv", "")
                                .replace("Version/4.0 ", "")

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                loadsImagesAutomatically = true
                                allowFileAccess = true
                                allowContentAccess = true
                                javaScriptCanOpenWindowsAutomatically = true
                                setSupportMultipleWindows(true)
                                cacheMode = WebSettings.LOAD_DEFAULT
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                userAgentString = cleanChromeUa
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                WebView.setWebContentsDebuggingEnabled(true)
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loadProgress = newProgress
                                    if (newProgress >= 70) {
                                        isLoading = false
                                    }
                                    checkAndExtractCookie(view)
                                }

                                override fun onCreateWindow(
                                    view: WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: android.os.Message?
                                ): Boolean {
                                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                                    val popupWebView = WebView(ctx).apply {
                                        webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(v: WebView?, req: WebResourceRequest?): Boolean {
                                                val u = req?.url?.toString() ?: ""
                                                view?.loadUrl(u)
                                                return true
                                            }
                                        }
                                    }
                                    transport?.webView = popupWebView
                                    resultMsg?.sendToTarget()
                                    return true
                                }

                                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                    android.util.Log.d(
                                        "SpotifyWebView",
                                        "[${consoleMessage?.messageLevel()}] ${consoleMessage?.message()} (${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()})"
                                    )
                                    return true
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url?.toString() ?: ""
                                    // Prevent jumping into external native Spotify app
                                    if (url.startsWith("intent:") || url.startsWith("spotify:")) {
                                        return true
                                    }
                                    currentUrl = url
                                    checkAndExtractCookie(view)
                                    return false
                                }

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

                                override fun onReceivedSslError(
                                    view: WebView?,
                                    handler: SslErrorHandler?,
                                    error: SslError?
                                ) {
                                    // Bypass minor cert or DNS issues on CDNs to prevent black/blank screens
                                    handler?.proceed()
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                    if (request?.isForMainFrame == true) {
                                        webErrorDesc = error?.description?.toString() ?: "Connection error"
                                    }
                                }
                            }

                            loadUrl(SPOTIFY_ACCOUNTS_URL)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }

    // Optional Token Dialog (Also accessible via 3-dot overflow menu & Quick Mode Bar)
    if (showTokenDialog) {
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            title = { Text("Manual sp_dc Token", color = Color.White) },
            text = {
                Column {
                    Text(
                        "Paste your Spotify sp_dc session cookie:",
                        color = Color(0xFFAAAAAA),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("AQB...", color = Color.Gray) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = tokenInput.trim().removePrefix("sp_dc=").trim()
                        if (clean.length >= 20) {
                            isCaptured = true
                            showTokenDialog = false
                            SpotifyConnectEngine.saveSession(clean)
                            scope.launch(Dispatchers.IO) { SpotifyConnectEngine.syncAll() }
                            scope.launch(Dispatchers.Main) {
                                Toast.makeText(context, "Connected!", Toast.LENGTH_SHORT).show()
                                onLoginSuccess(clean)
                            }
                        } else {
                            Toast.makeText(context, "Invalid sp_dc token", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954))
                ) {
                    Text("Connect", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTokenDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }
}
