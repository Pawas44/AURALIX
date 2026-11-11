package com.videoguard.app.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private const val BMC_URL = "https://buymeacoffee.com/FINDEX"
private const val BMC_DISPLAY = "buymeacoffee.com/FINDEX"

private val CoffeeGold = Color(0xFFFFDD00)
private val CoffeeAmber = Color(0xFFFFB703)
private val AuralixPurple = Color(0xFFA855F7)
private val AuralixViolet = Color(0xFF7C3AED)

private data class CoffeeTier(
    val cups: Int,
    val title: String,
    val price: String,
    val note: String
)

private val coffeeTiers = listOf(
    CoffeeTier(1, "1 Cup", "$5", "A quick caffeine boost for late-night coding."),
    CoffeeTier(3, "3 Cups", "$15", "Fuels a full weekend of polishing & updates."),
    CoffeeTier(5, "5 Cups", "$25", "Pure legend status — keeps the builds flowing!")
)

@Composable
fun SupportScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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

    val infiniteTransition = rememberInfiniteTransition(label = "supportPulse")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val activeTier = coffeeTiers.firstOrNull { it.cups == selectedCups } ?: coffeeTiers.first()

    val openSupportPage: () -> Unit = {
        try {
            uriHandler.openUri(BMC_URL)
        } catch (_: Exception) {
        }
    }

    val shareSupportLink: () -> Unit = {
        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Support Findex (creator of AURALIX) on Buy Me a Coffee: $BMC_URL")
            }
            context.startActivity(Intent.createChooser(sendIntent, "Share Support Link"))
        } catch (_: Exception) {
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 18.dp)
            .padding(top = 18.dp, bottom = 120.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Creator Tag Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                AuralixPurple.copy(alpha = 0.22f),
                                CoffeeGold.copy(alpha = 0.14f)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(
                                AuralixPurple.copy(alpha = 0.6f),
                                CoffeeGold.copy(alpha = 0.6f)
                            )
                        ),
                        RoundedCornerShape(50)
                    )
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(CoffeeGold)
                    )
                    Text(
                        text = "SUPPORT THE CREATOR",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }
            }

            // Main Floating Glass Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = AuralixViolet.copy(alpha = 0.35f),
                        spotColor = CoffeeGold.copy(alpha = 0.22f)
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1F1630),
                                Color(0xFF14111E),
                                Color(0xFF0C0B13)
                            )
                        )
                    )
                    .border(
                        width = 1.2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                AuralixPurple.copy(alpha = 0.75f),
                                CoffeeGold.copy(alpha = 0.55f),
                                Color.White.copy(alpha = 0.18f)
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .padding(horizontal = 22.dp, vertical = 26.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Pulsing Ambient Emblem
                    Box(
                        modifier = Modifier.size(104.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .scale(glowScale)
                                .alpha(glowAlpha)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            CoffeeGold.copy(alpha = 0.45f),
                                            AuralixPurple.copy(alpha = 0.22f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF2B1E3E),
                                            Color(0xFF1A1426)
                                        )
                                    )
                                )
                                .border(
                                    width = 1.5.dp,
                                    brush = Brush.linearGradient(
                                        listOf(CoffeeGold, AuralixPurple)
                                    ),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Coffee,
                                contentDescription = "Buy Me a Coffee",
                                tint = CoffeeGold,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Handle Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "@FINDEX",
                            color = CoffeeGold,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Enjoying the vibe?",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "AURALIX is 100% free for everyone — crafted solo with late nights, good music, and lots of coffee. If it made your day better, feel free to drop a coffee. Zero pressure, always appreciated.",
                        color = Color(0xFFBDBDD0),
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    // Interactive Cup Picker
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        coffeeTiers.forEach { tier ->
                            val isSelected = selectedCups == tier.cups
                            val borderColor by animateColorAsState(
                                if (isSelected) CoffeeGold else Color.White.copy(alpha = 0.14f),
                                label = "tierBorder"
                            )
                            val bgColor by animateColorAsState(
                                if (isSelected) CoffeeGold.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f),
                                label = "tierBg"
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(bgColor)
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = borderColor,
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                    .clickable { selectedCups = tier.cups }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = tier.title,
                                        color = if (isSelected) CoffeeGold else Color.White,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = tier.price,
                                        color = if (isSelected) Color.White else Color(0xFF9E9EAF),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    AnimatedContent(targetState = activeTier.note, label = "tierNote") { note ->
                        Text(
                            text = note,
                            color = Color(0xFF9E9EAF),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Primary Golden CTA
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .shadow(14.dp, RoundedCornerShape(18.dp), ambientColor = CoffeeGold.copy(alpha = 0.45f))
                            .clip(RoundedCornerShape(18.dp))
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
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = Color(0xFF14110B),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Copy & Share Actions Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(BMC_URL))
                                    copiedLink = true
                                }
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (copiedLink) Icons.Filled.Check else Icons.Filled.ContentCopy,
                                contentDescription = "Copy Link",
                                tint = if (copiedLink) Color(0xFF4ADE80) else CoffeeGold,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (copiedLink) "Link Copied!" else BMC_DISPLAY,
                                color = if (copiedLink) Color(0xFF4ADE80) else Color(0xFFE5E5F2),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                                .clickable { shareSupportLink() }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "Share",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Share",
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Minimal Aesthetic Footer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = "Crafted with",
                    color = Color(0xFF7E7E92),
                    fontSize = 12.sp
                )
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = AuralixPurple,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "& caffeine by Findex",
                    color = Color(0xFF7E7E92),
                    fontSize = 12.sp
                )
            }
        }
    }
}
