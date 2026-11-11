package com.videoguard.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoguard.app.ui.theme.LiquidGlassDefaults
import com.videoguard.app.ui.theme.liquidGlass
import kotlinx.coroutines.delay

private const val BMC_URL = "https://buymeacoffee.com/FINDEX"
private const val BMC_DISPLAY = "buymeacoffee.com/FINDEX"
private const val GITHUB_URL = "https://github.com/Pawas44/AURALIX"

private val CoffeeGold = Color(0xFFFFDD00)
private val CoffeeAmber = Color(0xFFFFB703)
private val AuralixPurple = Color(0xFF9D4EDD)

@Composable
fun SupportScreen(
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current
    var copiedLink by remember { mutableStateOf(false) }
    var selectedCups by remember { mutableIntStateOf(1) }

    LaunchedEffect(copiedLink) {
        if (copiedLink) {
            delay(2000L)
            copiedLink = false
        }
    }

    val openSupportPage: () -> Unit = {
        try {
            uriHandler.openUri(BMC_URL)
        } catch (_: Exception) {
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Support AURALIX",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Created by Findex",
                    color = Color(0xFF9E9EAF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(CoffeeGold.copy(alpha = 0.14f))
                    .border(1.dp, CoffeeGold.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Coffee,
                        contentDescription = null,
                        tint = CoffeeGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "FINDEX",
                        color = CoffeeGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }

        // Main Hero Buy Me a Coffee Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = CoffeeGold.copy(alpha = 0.25f))
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF23192D),
                            Color(0xFF15121E),
                            Color(0xFF121017)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            CoffeeGold.copy(alpha = 0.65f),
                            AuralixPurple.copy(alpha = 0.40f),
                            Color.White.copy(alpha = 0.18f)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(22.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Glowing Coffee Emblem
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    CoffeeGold.copy(alpha = 0.28f),
                                    AuralixPurple.copy(alpha = 0.18f)
                                )
                            )
                        )
                        .border(1.5.dp, CoffeeGold.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Coffee,
                        contentDescription = "Buy Me a Coffee",
                        tint = CoffeeGold,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Fuel AURALIX's Development",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Building, testing, and maintaining smooth Android & iOS builds takes time and resources. If AURALIX helps you enjoy music and videos seamlessly, consider buying me a coffee!",
                    color = Color(0xFFB8B8C8),
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Quick Coffee Cup Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(
                        Triple(1, "1 Cup", "$5"),
                        Triple(3, "3 Cups", "$15"),
                        Triple(5, "5 Cups", "$25")
                    ).forEach { (cups, label, price) ->
                        val isSelected = selectedCups == cups
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSelected) CoffeeGold.copy(alpha = 0.18f)
                                    else Color.White.copy(alpha = 0.06f)
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) CoffeeGold else Color.White.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { selectedCups = cups }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = label,
                                    color = if (isSelected) CoffeeGold else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = price,
                                    color = if (isSelected) Color.White else Color(0xFF9E9EAF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Primary Buy Me a Coffee CTA Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .shadow(10.dp, RoundedCornerShape(16.dp), ambientColor = CoffeeGold.copy(alpha = 0.4f))
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(CoffeeGold, CoffeeAmber)
                            )
                        )
                        .clickable { openSupportPage() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Coffee,
                            contentDescription = null,
                            tint = Color(0xFF14110B),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (selectedCups == 1) "Buy Me a Coffee" else "Buy $selectedCups Coffees",
                            color = Color(0xFF14110B),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Icon(
                            imageVector = Icons.Filled.OpenInNew,
                            contentDescription = null,
                            tint = Color(0xFF14110B),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Direct Link + Copy Link Pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, LiquidGlassDefaults.BorderSubtle, RoundedCornerShape(14.dp))
                        .clickable {
                            clipboardManager.setText(AnnotatedString(BMC_URL))
                            copiedLink = true
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = BMC_DISPLAY,
                        color = Color(0xFFE2E2EE),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    AnimatedContent(targetState = copiedLink, label = "copyState") { copied ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                                contentDescription = "Copy Link",
                                tint = if (copied) Color(0xFF4ADE80) else CoffeeGold,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (copied) "Copied!" else "Copy",
                                color = if (copied) Color(0xFF4ADE80) else CoffeeGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // What Your Support Powers
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(shape = RoundedCornerShape(22.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "What Your Support Powers",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            SupportFeatureRow(
                icon = Icons.Filled.MusicNote,
                accent = AuralixPurple,
                title = "Ad-Free Streaming",
                subtitle = "Clean YouTube & YT Music experience without interruptions."
            )

            SupportFeatureRow(
                icon = Icons.Filled.Headphones,
                accent = CoffeeGold,
                title = "Background & Lock Screen",
                subtitle = "Reliable audio playback, media controls, and synced progress."
            )

            SupportFeatureRow(
                icon = Icons.Filled.AutoAwesome,
                accent = Color(0xFF38BDF8),
                title = "Android & iOS Releases",
                subtitle = "Ongoing updates, UI polish, and automated builds for both platforms."
            )
        }

        // Open Source Project Link
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(shape = RoundedCornerShape(18.dp))
                .clickable {
                    try {
                        uriHandler.openUri(GITHUB_URL)
                    } catch (_: Exception) {
                    }
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "AURALIX on GitHub",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "github.com/Pawas44/AURALIX",
                    color = Color(0xFF9E9EAF),
                    fontSize = 12.sp
                )
            }
            Icon(
                imageVector = Icons.Filled.OpenInNew,
                contentDescription = "Open GitHub",
                tint = Color(0xFF9E9EAF),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SupportFeatureRow(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.16f))
                .border(1.dp, accent.copy(alpha = 0.40f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = Color(0xFF9E9EAF),
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

