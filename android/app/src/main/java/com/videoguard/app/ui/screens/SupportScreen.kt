package com.videoguard.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoguard.app.BuildConfig
import com.videoguard.app.data.AppUpdateManager

// ========================================================
// SUPPORT CONFIGURATION (Pawas Payment Handles)
// ========================================================
// Put your exact UPI VPA here (e.g. pawas@oksbi, pawas@paytm, etc.)
const val PAWAS_UPI_ID = "findex13@nyes"
const val PAWAS_NAME = "Pawas"
private const val RAZORPAY_URL = "https://razorpay.me/@pawas"
private const val BMC_PROFILE_URL = "https://buymeacoffee.com/findex"

private val CoffeeGold = Color(0xFFFFDD00)
private val BmcOrange = Color(0xFFFF813F)
private val CoffeeDark = Color(0xFF231B05)
private val RazorpayAccent = Color(0xFF3395FF)
private val RazorpayDark = Color(0xFF0C2038)
private val MintAccent = Color(0xFF40DCA5)
private val UpiOrange = Color(0xFFF47920)
private val UpiGreen = Color(0xFF00A859)

@Composable
fun SupportScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()
    val isCheckingUpdates by AppUpdateManager.isChecking.collectAsState()

    var selectedAmount by remember { mutableStateOf("100") }
    var customAmount by remember { mutableStateOf("") }

    val presetAmounts = listOf("50", "100", "250", "500")
    val effectiveAmount = customAmount.ifBlank { selectedAmount }.ifBlank { "100" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(RazorpayAccent, MintAccent)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Support",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column {
                Text(
                    text = "Support AURALIX",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Help keep the app fast, ad-free & updated",
                    color = Color(0xFF9E9EAF),
                    fontSize = 12.sp
                )
            }
        }

        // ========================================================
        // 1. UPI Payment Card (Direct Native UPI App Launch)
        // ========================================================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = RazorpayDark),
            border = androidx.compose.foundation.BorderStroke(1.2.dp, RazorpayAccent.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header with Authentic NPCI UPI Logo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                openUpiPayment(context, effectiveAmount)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OfficialUpiBadge()

                        Column {
                            Text(
                                text = "Pay with UPI",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "GPay, PhonePe, Paytm, BHIM, Cred",
                                color = Color(0xFFA0AEC0),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // UPI ID Details Pill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "UPI ID: $PAWAS_UPI_ID",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Tap button to pay",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }

                Text(
                    text = "Select Amount (INR):",
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                // Preset Amount Selector Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetAmounts.forEach { amt ->
                        val isSelected = selectedAmount == amt && customAmount.isBlank()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) RazorpayAccent else Color.White.copy(alpha = 0.08f)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) RazorpayAccent else Color.White.copy(alpha = 0.12f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    selectedAmount = amt
                                    customAmount = ""
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "₹$amt",
                                color = if (isSelected) Color.White else Color(0xFFE2E8F0),
                                fontSize = 13.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                // Custom amount field
                OutlinedTextField(
                    value = customAmount,
                    onValueChange = {
                        customAmount = it.filter { ch -> ch.isDigit() }
                        if (customAmount.isNotBlank()) selectedAmount = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Or enter custom amount (₹)", color = Color.Gray, fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = RazorpayAccent,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                // Action 1: Open Installed UPI App Directly
                Button(
                    onClick = {
                        openUpiPayment(context, effectiveAmount)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RazorpayAccent),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    OfficialUpiArrows(modifier = Modifier.size(width = 24.dp, height = 16.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Pay ₹$effectiveAmount via UPI App",
                        color = Color.White,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Action 2: Web Payment Gateway Fallback
                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RAZORPAY_URL)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } catch (_: Exception) {
                            Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInBrowser,
                        contentDescription = null,
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Open Razorpay Web Portal ↗",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // ========================================================
        // 2. Buy Me a Coffee Card (Opens in Browser)
        // ========================================================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CoffeeDark),
            border = androidx.compose.foundation.BorderStroke(1.2.dp, CoffeeGold.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header with Official Buy Me a Coffee Brand
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                openBrowserUrl(context, BMC_PROFILE_URL)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OfficialBmcBadge()

                        Column {
                            Text(
                                text = "Buy Me a Coffee",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "International cards, PayPal & Apple Pay",
                                color = Color(0xFFA0AEC0),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Text(
                    text = "Support AURALIX from anywhere in the world using your preferred international payment method.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                // Official Buy Me a Coffee Widget Button (matching screenshot)
                OfficialBmcWidgetButton(
                    onClick = {
                        openBrowserUrl(context, BMC_PROFILE_URL)
                    }
                )
            }
        }

        // ========================================================
        // 3. App Version & Updates Card
        // ========================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "AURALIX v${BuildConfig.APP_VERSION_NAME}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Build ${BuildConfig.VERSION_CODE} • Auto-Updates Active",
                        color = Color(0xFF9E9EAF),
                        fontSize = 11.5.sp
                    )
                }

                Button(
                    onClick = {
                        AppUpdateManager.checkForUpdates(context, isManualCheck = true, forceShowIfSame = true)
                    },
                    enabled = !isCheckingUpdates,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MintAccent.copy(alpha = 0.15f),
                        contentColor = MintAccent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    if (isCheckingUpdates) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MintAccent,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Check for Updates",
                                modifier = Modifier.size(16.dp),
                                tint = MintAccent
                            )
                            Text(
                                text = "Update",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // ========================================================
        // 4. Why Support AURALIX Card
        // ========================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Why Your Support Matters",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "• 100% Ad-Free & Privacy-First: No user tracking or injected advertisements.\n" +
                           "• Spotify-Level AI Recommendations: Continuous updates to the recommendation engine.\n" +
                           "• High-Fidelity Audio & Offline Playback: Free unlimited music for everyone.",
                    color = Color(0xFF9E9EAF),
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

// ========================================================
// AUTHENTIC BRAND ICONS (UPI & BMC)
// ========================================================

/**
 * Authentic NPCI UPI Logo Badge with orange & green directional chevrons
 * and bold italic typography.
 */
@Composable
fun OfficialUpiBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OfficialUpiArrows(modifier = Modifier.size(width = 20.dp, height = 15.dp))
            Text(
                text = "UPI",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * NPCI UPI Dual Slanted Chevrons drawn via Compose Canvas.
 */
@Composable
fun OfficialUpiArrows(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Left / Orange Chevron (45° slant)
        val orangePath = Path().apply {
            moveTo(0f, h)
            lineTo(w * 0.28f, h)
            lineTo(w * 0.55f, 0f)
            lineTo(w * 0.27f, 0f)
            close()
        }
        drawPath(orangePath, color = UpiOrange)

        // Right / Green Chevron (parallel slant)
        val greenPath = Path().apply {
            moveTo(w * 0.38f, h)
            lineTo(w * 0.66f, h)
            lineTo(w * 0.93f, 0f)
            lineTo(w * 0.65f, 0f)
            close()
        }
        drawPath(greenPath, color = UpiGreen)
    }
}

/**
 * Official Buy Me a Coffee Widget Button matching the exact orange badge,
 * takeaway coffee cup icon with sparkles, and script lettering.
 */
@Composable
fun OfficialBmcWidgetButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = BmcOrange,
        border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF1E1E1E).copy(alpha = 0.45f)),
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            BmcCupWidgetIcon(modifier = Modifier.size(width = 28.dp, height = 32.dp))

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "Buy me a coffee",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Cursive,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * Buy Me a Coffee Takeaway Cup Icon with yellow body, white sleeve, white lid & sparkles.
 */
@Composable
fun BmcCupWidgetIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // White Sparkles
        drawCircle(color = Color.White, radius = w * 0.06f, center = androidx.compose.ui.geometry.Offset(w * 0.88f, h * 0.22f))
        drawCircle(color = Color.White, radius = w * 0.045f, center = androidx.compose.ui.geometry.Offset(w * 0.12f, h * 0.38f))

        // Yellow Cup Body
        val cupPath = Path().apply {
            moveTo(w * 0.25f, h * 0.22f)
            lineTo(w * 0.75f, h * 0.22f)
            lineTo(w * 0.68f, h * 0.95f)
            lineTo(w * 0.32f, h * 0.95f)
            close()
        }
        drawPath(cupPath, color = Color(0xFFFFDD00))
        drawPath(cupPath, color = Color(0xFF1E1E1E), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f))

        // White Sleeve
        val sleevePath = Path().apply {
            moveTo(w * 0.22f, h * 0.40f)
            lineTo(w * 0.78f, h * 0.40f)
            lineTo(w * 0.73f, h * 0.68f)
            lineTo(w * 0.27f, h * 0.68f)
            close()
        }
        drawPath(sleevePath, color = Color.White)
        drawPath(sleevePath, color = Color(0xFF1E1E1E), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f))

        // White Lid
        val lidPath = Path().apply {
            moveTo(w * 0.20f, h * 0.22f)
            lineTo(w * 0.80f, h * 0.22f)
            lineTo(w * 0.75f, h * 0.10f)
            lineTo(w * 0.25f, h * 0.10f)
            close()
        }
        drawPath(lidPath, color = Color.White)
        drawPath(lidPath, color = Color(0xFF1E1E1E), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f))

        // Lid Cap
        val capPath = Path().apply {
            moveTo(w * 0.35f, h * 0.10f)
            lineTo(w * 0.65f, h * 0.10f)
            lineTo(w * 0.62f, h * 0.05f)
            lineTo(w * 0.38f, h * 0.05f)
            close()
        }
        drawPath(capPath, color = Color.White)
        drawPath(capPath, color = Color(0xFF1E1E1E), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
    }
}

/**
 * Authentic Buy Me A Coffee Logo Badge with iconic orange background.
 */
@Composable
fun OfficialBmcBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(BmcOrange),
        contentAlignment = Alignment.Center
    ) {
        BmcCupWidgetIcon(modifier = Modifier.size(width = 22.dp, height = 26.dp))
    }
}

// ========================================================
// PAYMENT & BROWSER HELPERS
// ========================================================

/**
 * Launches installed UPI apps (Google Pay, PhonePe, Paytm, BHIM, Cred)
 * with pre-filled payee name and amount. Falls back to Razorpay if no UPI apps exist.
 */
private fun openUpiPayment(context: Context, amount: String) {
    val upiUri = Uri.parse(
        "upi://pay?pa=$PAWAS_UPI_ID&pn=$PAWAS_NAME&am=$amount&cu=INR&tn=Support%20AURALIX"
    )
    val upiIntent = Intent(Intent.ACTION_VIEW, upiUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        val chooser = Intent.createChooser(upiIntent, "Pay ₹$amount using UPI App")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (_: Exception) {
        // Fallback to Razorpay web page
        openBrowserUrl(context, RAZORPAY_URL)
    }
}

private fun openBrowserUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
    }
}
