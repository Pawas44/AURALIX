package com.videoguard.app.ui

import android.annotation.SuppressLint
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.videoguard.app.data.SpotifyConnectEngine
import com.videoguard.app.ui.theme.VideoGuardTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SpotifyAuthActivity : ComponentActivity() {

    companion object {
        const val SPOTIFY_ACCOUNTS_URL =
            "https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F"
        const val SPOTIFY_WEB_PLAYER_URL =
            "https://open.spotify.com"
        const val CHROME_UA =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SpotifyConnectEngine.init(this)

        setContent {
            VideoGuardTheme {
                SpotifyAuthScreen(
                    onFinish = { finish() },
                    onLoginSuccess = {
                        setResult(RESULT_OK)
                        finish()
                    }
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpotifyAuthScreen(
    onFinish: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var currentUrl by remember { mutableStateOf(SpotifyAuthActivity.SPOTIFY_ACCOUNTS_URL) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isCaptured by remember { mutableStateOf(false) }
    var webErrorDesc by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var tokenInput by remember { mutableStateOf("") }

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
                    onLoginSuccess()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Auto-check for login cookie every 400ms
    LaunchedEffect(isCaptured) {
        while (!isCaptured) {
            delay(400L)
            checkAndExtractCookie(webViewInstance)
        }
    }

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
                IconButton(onClick = onFinish) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
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
                                val target = if (currentUrl.contains("open.spotify.com")) SpotifyAuthActivity.SPOTIFY_ACCOUNTS_URL else SpotifyAuthActivity.SPOTIFY_WEB_PLAYER_URL
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
                                Toast.makeText(context, "Logged out from Spotify. You can now log into another account.", Toast.LENGTH_SHORT).show()
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
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val isAccounts = currentUrl.contains("accounts.spotify.com")
                    Surface(
                        onClick = {
                            currentUrl = SpotifyAuthActivity.SPOTIFY_ACCOUNTS_URL
                            webViewInstance?.loadUrl(SpotifyAuthActivity.SPOTIFY_ACCOUNTS_URL)
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
                            currentUrl = SpotifyAuthActivity.SPOTIFY_WEB_PLAYER_URL
                            webViewInstance?.loadUrl(SpotifyAuthActivity.SPOTIFY_WEB_PLAYER_URL)
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

                Surface(
                    onClick = { showTokenDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF2C2C2C)
                ) {
                    Text(
                        text = "Paste Token",
                        color = Color(0xFF1DB954),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // The Full Screen Interactive WebView inside Activity
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF121212))
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(android.graphics.Color.parseColor("#121212"))
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
                                loadsImagesAutomatically = true
                                allowFileAccess = true
                                allowContentAccess = true
                                javaScriptCanOpenWindowsAutomatically = true
                                setSupportMultipleWindows(true)
                                cacheMode = WebSettings.LOAD_DEFAULT
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                userAgentString = SpotifyAuthActivity.CHROME_UA
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
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url?.toString() ?: ""
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

                            loadUrl(SpotifyAuthActivity.SPOTIFY_ACCOUNTS_URL)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Optional loading indicator overlay
                if (isLoading && loadProgress < 40) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF121212).copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF1DB954),
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                    }
                }
            }
        }
    }

    // Manual sp_dc Token Dialog with 1-tap clipboard paste
    if (showTokenDialog) {
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            title = { Text("Direct sp_dc Token", color = Color.White) },
            text = {
                Column {
                    Text(
                        "Agar web login me problem ho toh browser se sp_dc cookie paste karein:",
                        color = Color(0xFFAAAAAA),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("AQB...", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF1DB954),
                            unfocusedBorderColor = Color(0xFF444444),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            try {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                if (clipboard != null && clipboard.hasPrimaryClip() &&
                                    (clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true ||
                                     clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML) == true)
                                ) {
                                    val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                    val clean = text.trim().removePrefix("sp_dc=").trim()
                                    if (clean.length >= 20) {
                                        tokenInput = clean
                                        Toast.makeText(context, "Pasted from clipboard!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Clipboard me valid token nahi mila", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, tint = Color(0xFF1DB954), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paste from Clipboard", color = Color.White, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = tokenInput.trim().removePrefix("sp_dc=").trim()
                        if (clean.length >= 20) {
                            showTokenDialog = false
                            SpotifyConnectEngine.saveSession(clean)
                            scope.launch(Dispatchers.IO) { SpotifyConnectEngine.syncAll() }
                            scope.launch(Dispatchers.Main) {
                                Toast.makeText(context, "Connected successfully!", Toast.LENGTH_SHORT).show()
                                onLoginSuccess()
                            }
                        } else {
                            Toast.makeText(context, "Invalid sp_dc token (length < 20)", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954))
                ) {
                    Text("Connect", color = Color.Black, fontWeight = FontWeight.Bold)
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
