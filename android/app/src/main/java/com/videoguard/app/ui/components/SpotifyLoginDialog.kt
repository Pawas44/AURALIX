package com.videoguard.app.ui.components

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.*
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
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

private const val MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

enum class SpotifyLoginTab {
    WEB_SIGNIN,
    DIRECT_TOKEN,
    QUICK_HELP
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
    var isDesktopMode by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf("https://open.spotify.com") }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var manualCookieText by remember { mutableStateOf("") }
    var isCaptured by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var webErrorDesc by remember { mutableStateOf<String?>(null) }

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
                            text = if (selectedTab == SpotifyLoginTab.WEB_SIGNIN) {
                                if (isDesktopMode) "Desktop Web Player" else "Mobile Web Player"
                            } else if (selectedTab == SpotifyLoginTab.DIRECT_TOKEN) {
                                "Direct sp_dc Token"
                            } else {
                                "Mobile Guide"
                            },
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
                                    Uri.parse("https://open.spotify.com")
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
                        // Toggle Desktop / Mobile UA
                        IconButton(
                            onClick = {
                                isDesktopMode = !isDesktopMode
                                webViewInstance?.settings?.userAgentString =
                                    if (isDesktopMode) DESKTOP_USER_AGENT else MOBILE_USER_AGENT
                                webViewInstance?.reload()
                                Toast.makeText(
                                    context,
                                    if (isDesktopMode) "Desktop Mode enabled" else "Mobile Mode enabled",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Icon(
                                imageVector = if (isDesktopMode) Icons.Default.DesktopWindows else Icons.Default.PhoneAndroid,
                                contentDescription = "Toggle Mode",
                                tint = if (isDesktopMode) Color(0xFF1DB954) else Color(0xFFAAAAAA),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color(0xFF1DB954),
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(end = 6.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(onClick = {
                                webErrorDesc = null
                                webViewInstance?.reload()
                            }) {
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

                // Tab Switcher Bar: Web Login vs Direct Token vs Guide
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
                        label = { Text("Web Sign-In", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
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
                        label = { Text("Direct Token", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
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
                        selected = selectedTab == SpotifyLoginTab.QUICK_HELP,
                        onClick = { selectedTab = SpotifyLoginTab.QUICK_HELP },
                        label = { Text("Mobile Help 📖", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
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
                                            allowFileAccess = true
                                            allowContentAccess = true
                                            cacheMode = WebSettings.LOAD_DEFAULT
                                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                            userAgentString = if (isDesktopMode) DESKTOP_USER_AGENT else MOBILE_USER_AGENT
                                        }

                                        webChromeClient = object : WebChromeClient() {
                                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                                loadProgress = newProgress
                                                if (newProgress >= 80) {
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
                                                if (url.startsWith("intent:") || url.startsWith("spotify:")) {
                                                    try {
                                                        val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {}
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

                                            override fun onReceivedError(
                                                view: WebView?,
                                                request: WebResourceRequest?,
                                                error: WebResourceError?
                                            ) {
                                                super.onReceivedError(view, request, error)
                                                if (request?.isForMainFrame == true) {
                                                    webErrorDesc = error?.description?.toString()
                                                }
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
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (webErrorDesc != null) "Load error: $webErrorDesc" else "Screen black or not loading?",
                                            color = Color(0xFFAAAAAA),
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            TextButton(
                                                onClick = {
                                                    val next = if (currentUrl.contains("open.spotify.com")) {
                                                        "https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F"
                                                    } else {
                                                        "https://open.spotify.com"
                                                    }
                                                    currentUrl = next
                                                    webViewInstance?.loadUrl(next)
                                                },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (currentUrl.contains("open.spotify.com")) "Try Login Page" else "Try Web Player",
                                                    color = Color.White,
                                                    fontSize = 11.sp
                                                )
                                            }

                                            TextButton(
                                                onClick = { selectedTab = SpotifyLoginTab.DIRECT_TOKEN },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text("Direct Token", color = Color(0xFF1DB954), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
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
                                .verticalScroll(rememberScrollState())
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
                                                text = "100% Guaranteed Spotube-style Login",
                                                color = Color(0xFF1DB954),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

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

                                    Spacer(modifier = Modifier.height(12.dp))

                                    OutlinedTextField(
                                        value = manualCookieText,
                                        onValueChange = { manualCookieText = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = {
                                            Text(
                                                "Paste your sp_dc token here (e.g. AQD...)",
                                                fontSize = 11.sp,
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

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Button(
                                        onClick = { submitDirectCookie(manualCookieText) },
                                        modifier = Modifier.fillMaxWidth().height(42.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF282828)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "Connect with Token",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }

                                    if (statusMessage != null) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = statusMessage ?: "",
                                            color = Color(0xFF1DB954),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Easy Instructions Card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF181818)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "📱 Mobile par token kaise nikalein?",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "1. Play Store se free 'Kiwi Browser' install karein.\n" +
                                                "2. Kiwi me open.spotify.com khol kar login karein.\n" +
                                                "3. Kiwi ke 3 dots (⋮) -> 'Developer Tools' -> 'Application' -> 'Cookies' -> 'sp_dc' copy karein.\n" +
                                                "4. AURALIX me aakar upar wala button dabayein!",
                                        color = Color(0xFFBBBBBB),
                                        fontSize = 11.sp,
                                        lineHeight = 17.sp
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text(
                                        text = "💻 Computer / Laptop se (Sabse Fast):",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "1. PC Chrome/Edge me open.spotify.com par login karein.\n" +
                                                "2. F12 dabayein -> Application -> Cookies -> 'sp_dc' value copy karein.\n" +
                                                "3. Phone par WhatsApp/Notes se copy karke connect karein!",
                                        color = Color(0xFFBBBBBB),
                                        fontSize = 11.sp,
                                        lineHeight = 17.sp
                                    )
                                }
                            }
                        }
                    }

                    SpotifyLoginTab.QUICK_HELP -> {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A24)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = Color(0xFF1DB954),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Aapko Login karne ki zaroorat bhi nahi!",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Agar aap kisi bhi Spotify Playlist ko AURALIX mein sunna chahte hain:\n\n" +
                                                "1. Spotify App mein apni playlist ka 'Share Link' copy karein.\n" +
                                                "2. AURALIX mein Library tab mein jayein.\n" +
                                                "3. 'Import' -> 'Spotify' chunein aur link paste kar dein!\n\n" +
                                                "Saare gaane automatically match hoke aapke AURALIX mein aa jayenge bina kisi account login ke!",
                                        color = Color(0xFFDDDDDD),
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Web Sign-In Black Kyun Aata Hai?",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Spotify ne Android WebViews par automated bot protection (Cloudflare / reCAPTCHA) lagaya hua hai taaki koi third-party app credentials na le sake.\n\n" +
                                                "Isi wajah se Spotube, Spicetify aur sabhi popular music apps 'sp_dc' token system use karte hain jo 100% safe aur stable hai.",
                                        color = Color(0xFFAAAAAA),
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
