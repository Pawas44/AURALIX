package com.videoguard.app.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.videoguard.app.ui.theme.LiquidGlassDefaults

private const val RAZORPAY_URL = "https://razorpay.me/@pawas"
private const val BMC_PROFILE_URL = "https://buymeacoffee.com/FINDEX"
private const val BMC_WIDGET_URL =
    "https://www.buymeacoffee.com/widget/page/FINDEX?description=Support%20me%20on%20Buy%20me%20a%20coffee!&color=%2340DCA5"

private val MintAccent = Color(0xFF40DCA5)
private val RazorpayBlue = Color(0xFF0C2340)
private val RazorpayAccent = Color(0xFF3395FF)
private val CoffeeGold = Color(0xFFFFDD00)

enum class SupportPortal {
    RAZORPAY_UPI,
    BUY_ME_A_COFFEE
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SupportScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedPortal by remember { mutableStateOf(SupportPortal.RAZORPAY_UPI) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    val currentUrl = remember(selectedPortal) {
        if (selectedPortal == SupportPortal.RAZORPAY_UPI) RAZORPAY_URL else BMC_WIDGET_URL
    }

    LaunchedEffect(currentUrl) {
        isLoading = true
        hasError = false
        webViewRef?.loadUrl(currentUrl)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 14.dp)
            .padding(top = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(RazorpayAccent, MintAccent)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.ElectricBolt,
                        contentDescription = "Support",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = "Support AURALIX",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (selectedPortal == SupportPortal.RAZORPAY_UPI) "razorpay.me/@pawas (UPI & Cards)" else "buymeacoffee.com/FINDEX",
                        color = Color(0xFF9E9EAF),
                        fontSize = 11.5.sp
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = {
                        hasError = false
                        isLoading = true
                        webViewRef?.loadUrl(currentUrl)
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, LiquidGlassDefaults.BorderSubtle, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Reload",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = {
                        try {
                            val target = if (selectedPortal == SupportPortal.RAZORPAY_UPI) RAZORPAY_URL else BMC_PROFILE_URL
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                        } catch (_: Exception) {
                            Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, LiquidGlassDefaults.BorderSubtle, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInBrowser,
                        contentDescription = "Open in Browser",
                        tint = if (selectedPortal == SupportPortal.RAZORPAY_UPI) RazorpayAccent else MintAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Quick Direct Action Tiles: Razorpay / UPI & Buy Me a Coffee
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // UPI / Razorpay Direct Tile
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (selectedPortal == SupportPortal.RAZORPAY_UPI)
                            Brush.linearGradient(listOf(Color(0xFF0F2B48), Color(0xFF13365C)))
                        else
                            Brush.linearGradient(listOf(Color(0xFF161826), Color(0xFF1C1E30)))
                    )
                    .border(
                        1.2.dp,
                        if (selectedPortal == SupportPortal.RAZORPAY_UPI) RazorpayAccent else Color.White.copy(alpha = 0.08f),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable {
                        selectedPortal = SupportPortal.RAZORPAY_UPI
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Payment,
                            contentDescription = "UPI",
                            tint = RazorpayAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "UPI / Razorpay",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "GPay, PhonePe, Paytm (@pawas)",
                        color = Color(0xFFA0AEC0),
                        fontSize = 10.5.sp,
                        maxLines = 1
                    )
                }
            }

            // Buy Me a Coffee Direct Tile
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (selectedPortal == SupportPortal.BUY_ME_A_COFFEE)
                            Brush.linearGradient(listOf(Color(0xFF2E2405), Color(0xFF3D3008)))
                        else
                            Brush.linearGradient(listOf(Color(0xFF161826), Color(0xFF1C1E30)))
                    )
                    .border(
                        1.2.dp,
                        if (selectedPortal == SupportPortal.BUY_ME_A_COFFEE) CoffeeGold else Color.White.copy(alpha = 0.08f),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable {
                        selectedPortal = SupportPortal.BUY_ME_A_COFFEE
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Coffee,
                            contentDescription = "Coffee",
                            tint = CoffeeGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Buy Me a Coffee",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "buymeacoffee.com/FINDEX",
                        color = Color(0xFFA0AEC0),
                        fontSize = 10.5.sp,
                        maxLines = 1
                    )
                }
            }
        }

        // Fast Browser Launch Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .clickable {
                    try {
                        val target = if (selectedPortal == SupportPortal.RAZORPAY_UPI) RAZORPAY_URL else BMC_PROFILE_URL
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                    } catch (_: Exception) {}
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.OpenInBrowser,
                    contentDescription = null,
                    tint = if (selectedPortal == SupportPortal.RAZORPAY_UPI) RazorpayAccent else MintAccent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (selectedPortal == SupportPortal.RAZORPAY_UPI)
                        "Open https://razorpay.me/@pawas directly"
                    else
                        "Open buymeacoffee.com in browser",
                    color = Color(0xFFE2E8F0),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = "OPEN ↗",
                color = if (selectedPortal == SupportPortal.RAZORPAY_UPI) RazorpayAccent else MintAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Embedded Secure Web Portal (With native UPI deep-link routing)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .shadow(16.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black.copy(alpha = 0.55f))
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White)
                .border(1.5.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(22.dp))
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(android.graphics.Color.WHITE)
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            javaScriptCanOpenWindowsAutomatically = true
                            setSupportMultipleWindows(true)
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message?
                            ): Boolean {
                                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                                val tempWebView = WebView(ctx).apply {
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            v: WebView?,
                                            request: WebResourceRequest?
                                        ): Boolean {
                                            val url = request?.url?.toString() ?: return false
                                            return handlePaymentUrl(ctx, url, view)
                                        }
                                    }
                                }
                                transport.webView = tempWebView
                                resultMsg.sendToTarget()
                                return true
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                hasError = false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                if (request?.isForMainFrame == true) {
                                    isLoading = false
                                    hasError = true
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString() ?: return false
                                return handlePaymentUrl(ctx, url, view)
                            }
                        }

                        loadUrl(currentUrl)
                        webViewRef = this
                    }
                },
                update = { view ->
                    webViewRef = view
                }
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF9FAFB)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = if (selectedPortal == SupportPortal.RAZORPAY_UPI) RazorpayAccent else MintAccent,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = if (selectedPortal == SupportPortal.RAZORPAY_UPI)
                                "Loading Razorpay & UPI Portal..."
                            else
                                "Loading Buy Me a Coffee...",
                            color = Color(0xFF4B5563),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (hasError) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF13141F)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = "Unable to load portal right now",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selectedPortal == SupportPortal.RAZORPAY_UPI) RazorpayAccent else MintAccent)
                                    .clickable {
                                        hasError = false
                                        isLoading = true
                                        webViewRef?.loadUrl(currentUrl)
                                    }
                                    .padding(horizontal = 18.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "Retry",
                                    color = Color(0xFF0D1F18),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .clickable {
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)))
                                        } catch (_: Exception) {}
                                    }
                                    .padding(horizontal = 18.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "Open in Browser",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Handles deep links for Indian UPI apps (Google Pay, PhonePe, Paytm, BHIM, Cred)
 * and intent schemes triggered by Razorpay.
 */
private fun handlePaymentUrl(context: Context, url: String, webView: WebView?): Boolean {
    // Standard web navigation
    if (url.startsWith("http://") || url.startsWith("https://")) {
        // If razorpay or buymeacoffee navigation, let webview load
        if (url.contains("razorpay.com") || url.contains("razorpay.me") || url.contains("buymeacoffee.com")) {
            return false
        }
        // External links can be handled in external browser
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            true
        } catch (_: Exception) {
            false
        }
    }

    // UPI Deep Link (upi://pay?...)
    if (url.startsWith("upi://") || url.startsWith("phonepe://") || url.startsWith("tez://") || url.startsWith("paytmmp://")) {
        return try {
            val upiIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(upiIntent)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "No compatible UPI app found. Please open link in browser.", Toast.LENGTH_LONG).show()
            true
        }
    }

    // Android Intent URI scheme (intent://...)
    if (url.startsWith("intent://")) {
        return try {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val packageManager = context.packageManager
                val info = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                if (info != null) {
                    context.startActivity(intent)
                    true
                } else {
                    val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                    if (!fallbackUrl.isNullOrEmpty()) {
                        webView?.loadUrl(fallbackUrl)
                        true
                    } else {
                        val packageName = intent.`package`
                        if (!packageName.isNullOrEmpty()) {
                            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(marketIntent)
                            true
                        } else {
                            false
                        }
                    }
                }
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Other native custom schemes
    return try {
        val genericIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(genericIntent)
        true
    } catch (_: Exception) {
        true
    }
}
