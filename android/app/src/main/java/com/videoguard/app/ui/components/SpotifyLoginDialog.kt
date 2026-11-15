package com.videoguard.app.ui.components

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.videoguard.app.data.SpotifyConnectEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

enum class SpotifyLoginTab {
    WEB_SIGNIN,
    DIRECT_TOKEN
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpotifyLoginDialog(
    onDismiss: () -> Unit,
    onLoginSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(SpotifyLoginTab.WEB_SIGNIN) }
    var isLoading by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var currentUrl by remember { mutableStateOf("https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F") }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var manualCookieText by remember { mutableStateOf("") }
    var isCaptured by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

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
                statusMessage = "Connecting session..."
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

    fun submitDirectCookie(rawToken: String) {
        val clean = rawToken.trim().removePrefix("sp_dc=").trim()
        if (clean.length < 20) {
            Toast.makeText(context, "Please enter a valid sp_dc cookie token", Toast.LENGTH_SHORT).show()
            return
        }
        isCaptured = true
        statusMessage = "Verifying & syncing Spotify library..."
        SpotifyConnectEngine.saveSession(clean)
        scope.launch(Dispatchers.IO) {
            SpotifyConnectEngine.syncAll()
        }
        scope.launch(Dispatchers.Main) {
            Toast.makeText(context, "Spotify connected successfully!", Toast.LENGTH_SHORT).show()
            onLoginSuccess(clean)
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
                            text = if (selectedTab == SpotifyLoginTab.WEB_SIGNIN) "Desktop Web Login" else "Direct sp_dc Token",
                            color = Color(0xFFAAAAAA),
                            fontSize = 11.sp
                        )
                    }

                    // Open in Chrome / External browser button
                    IconButton(
                        onClick = {
                            try {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F")
                                )
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open in Chrome",
                            tint = Color(0xFFAAAAAA),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (selectedTab == SpotifyLoginTab.WEB_SIGNIN) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color(0xFF1DB954),
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(end = 6.dp),
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

                        IconButton(
                            onClick = {
                                CookieManager.getInstance().flush()
                                checkAndExtractCookie(webViewInstance)
                                if (!isCaptured) {
                                    Toast.makeText(context, "Checking login cookies...", Toast.LENGTH_SHORT).show()
                                }
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
                }

                // Tab Switcher Bar: Web Login vs Direct Token
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF202020))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTab == SpotifyLoginTab.WEB_SIGNIN,
                        onClick = { selectedTab = SpotifyLoginTab.WEB_SIGNIN },
                        label = { Text("Web Sign-In", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF1DB954),
                            selectedLabelColor = Color.Black,
                            selectedLeadingIconColor = Color.Black,
                            containerColor = Color(0xFF2C2C2C),
                            labelColor = Color.White,
                            iconColor = Color.White
                        )
                    )

                    FilterChip(
                        selected = selectedTab == SpotifyLoginTab.DIRECT_TOKEN,
                        onClick = { selectedTab = SpotifyLoginTab.DIRECT_TOKEN },
                        label = { Text("Direct sp_dc Token (Fast)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF1DB954),
                            selectedLabelColor = Color.Black,
                            selectedLeadingIconColor = Color.Black,
                            containerColor = Color(0xFF2C2C2C),
                            labelColor = Color.White,
                            iconColor = Color.White
                        )
                    )
                }

                if (isLoading && selectedTab == SpotifyLoginTab.WEB_SIGNIN) {
                    LinearProgressIndicator(
                        progress = { loadProgress / 100f },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = Color(0xFF1DB954),
                        trackColor = Color(0xFF222222)
                    )
                }

                // Content Area
                when (selectedTab) {
                    SpotifyLoginTab.WEB_SIGNIN -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
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
                                            javaScriptCanOpenWindowsAutomatically = true
                                            loadsImagesAutomatically = true
                                            blockNetworkImage = false
                                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                            userAgentString = DESKTOP_USER_AGENT
                                        }

                                        webChromeClient = object : WebChromeClient() {
                                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                                loadProgress = newProgress
                                                if (newProgress >= 85) {
                                                    isLoading = false
                                                }
                                                checkAndExtractCookie(view)
                                            }
                                        }

                                        webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(
                                                view: WebView?,
                                                request: WebResourceRequest?
                                            ): Boolean {
                                                val url = request?.url?.toString() ?: ""
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
                                        }

                                        loadUrl(currentUrl)
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Quick troubleshooting banner at bottom of WebView
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xEE1E1E1E)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Screen blank or login issue?",
                                        color = Color(0xFFAAAAAA),
                                        fontSize = 11.sp
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TextButton(
                                            onClick = { selectedTab = SpotifyLoginTab.DIRECT_TOKEN },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Use Direct Token", color = Color(0xFF1DB954), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        TextButton(
                                            onClick = {
                                                webViewInstance?.loadUrl("https://open.spotify.com")
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Open Spotify Web", color = Color.White, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SpotifyLoginTab.DIRECT_TOKEN -> {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Top
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF1DB954)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Key,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "Direct sp_dc Connection",
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "100% reliable bypass for WebView issues",
                                                color = Color(0xFF1DB954),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Text(
                                        text = "How to get your sp_dc cookie (takes 30 seconds):",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "1. Open chrome/browser and log into open.spotify.com\n" +
                                                "2. Open Developer Tools (or cookie settings) and find 'sp_dc'\n" +
                                                "3. Copy the value and tap 'Paste & Connect' below!",
                                        color = Color(0xFFBBBBBB),
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // 1-Click Paste & Connect Button
                                    Button(
                                        onClick = {
                                            try {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                val clip = clipboard?.primaryClip
                                                if (clip != null && clip.itemCount > 0) {
                                                    val text = clip.getItemAt(0).text?.toString() ?: ""
                                                    if (text.isNotBlank()) {
                                                        manualCookieText = text
                                                        submitDirectCookie(text)
                                                    } else {
                                                        Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Could not read clipboard", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Paste from Clipboard & Connect",
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Or paste token manually:",
                                color = Color(0xFFAAAAAA),
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = manualCookieText,
                                onValueChange = { manualCookieText = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text(
                                        "AQD...",
                                        fontSize = 12.sp,
                                        color = Color(0xFF666666)
                                    )
                                },
                                maxLines = 3,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF1DB954),
                                    unfocusedBorderColor = Color(0xFF444444)
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = { submitDirectCookie(manualCookieText) },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF282828)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Connect with Token",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            if (statusMessage != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = statusMessage ?: "",
                                    color = Color(0xFF1DB954),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
