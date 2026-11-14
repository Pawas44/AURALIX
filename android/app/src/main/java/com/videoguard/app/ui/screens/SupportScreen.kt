package com.videoguard.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoguard.app.ui.theme.LiquidGlassDefaults

private const val RAZORPAY_URL = "https://razorpay.me/@pawas"
private const val BMC_PROFILE_URL = "https://buymeacoffee.com/FINDEX"

private val CoffeeGold = Color(0xFFFFDD00)
private val CoffeeDark = Color(0xFF261D02)
private val RazorpayAccent = Color(0xFF3395FF)
private val RazorpayDark = Color(0xFF0C2038)
private val MintAccent = Color(0xFF40DCA5)

@Composable
fun SupportScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    var selectedAmount by remember { mutableStateOf("100") }
    var customAmount by remember { mutableStateOf("") }

    val presetAmounts = listOf("50", "100", "250", "500")

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

        // ==========================================
        // 1. UPI & Razorpay Card (Native App Trigger)
        // ==========================================
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
                // Header inside card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(RazorpayAccent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Payment,
                                contentDescription = null,
                                tint = RazorpayAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Pay via UPI App / Razorpay",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "GPay, PhonePe, Paytm, BHIM (@pawas)",
                                color = Color(0xFFA0AEC0),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(RazorpayAccent.copy(alpha = 0.15f))
                            .clickable {
                                clipboardManager.setText(AnnotatedString(RAZORPAY_URL))
                                Toast.makeText(context, "Copied payment link to clipboard", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy link",
                            tint = RazorpayAccent,
                            modifier = Modifier.size(16.dp)
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

                val effectiveAmount = customAmount.ifBlank { selectedAmount }.ifBlank { "100" }

                // Action 1: Open UPI App Directly
                Button(
                    onClick = {
                        openUpiPayment(context, effectiveAmount)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RazorpayAccent),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ElectricBolt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pay ₹$effectiveAmount via Any UPI App",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Action 2: Open in Browser via Razorpay
                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RAZORPAY_URL)))
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

        // ==========================================
        // 2. Buy Me a Coffee Card (Opens in Browser)
        // ==========================================
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(CoffeeGold.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Coffee,
                                contentDescription = null,
                                tint = CoffeeGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Buy Me a Coffee",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "buymeacoffee.com/FINDEX",
                                color = Color(0xFFA0AEC0),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(CoffeeGold.copy(alpha = 0.15f))
                            .clickable {
                                clipboardManager.setText(AnnotatedString(BMC_PROFILE_URL))
                                Toast.makeText(context, "Copied BMC link to clipboard", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy link",
                            tint = CoffeeGold,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Text(
                    text = "Ideal for international supporters via Credit/Debit Cards, PayPal, and Apple Pay.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                // Direct Open in Browser button
                Button(
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BMC_PROFILE_URL)))
                        } catch (_: Exception) {
                            Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoffeeGold),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Coffee,
                        contentDescription = null,
                        tint = Color(0xFF1E1702),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Open Buy Me a Coffee in Browser ↗",
                        color = Color(0xFF1E1702),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ==========================================
        // 3. Why Support AURALIX Card
        // ==========================================
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
                           "• YouTube InnerTube Streaming & Downloads: Server maintenance and CDN bandwidth.",
                    color = Color(0xFF9E9EAF),
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

/**
 * Triggers installed Indian UPI apps (Google Pay, PhonePe, Paytm, BHIM)
 * or falls back to the Razorpay web payment page.
 */
private fun openUpiPayment(context: Context, amount: String) {
    val upiUri = Uri.parse(
        "upi://pay?pa=pawas@razorpay&pn=Pawas&am=$amount&cu=INR&tn=Support%20AURALIX"
    )
    val upiIntent = Intent(Intent.ACTION_VIEW, upiUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        val packageManager = context.packageManager
        val activities = packageManager.queryIntentActivities(upiIntent, 0)
        if (activities.isNotEmpty()) {
            context.startActivity(Intent.createChooser(upiIntent, "Pay ₹$amount using UPI"))
        } else {
            // Fallback directly to the Razorpay page which handles UPI QR and Web UPI
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RAZORPAY_URL)))
        }
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RAZORPAY_URL)))
        } catch (_: Exception) {
            Toast.makeText(context, "Could not open UPI or browser", Toast.LENGTH_SHORT).show()
        }
    }
}
