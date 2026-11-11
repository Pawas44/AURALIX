package com.videoguard.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoguard.app.R
import kotlinx.coroutines.delay

private const val BMC_URL = "https://buymeacoffee.com/FINDEX"
private const val BMC_DISPLAY = "buymeacoffee.com/FINDEX"

private data class SupportOption(
    val cups: Int,
    val emoji: String,
    val label: String,
    val price: String
)

private val supportOptions = listOf(
    SupportOption(1, "☕", "Buy 1 Coffee", "$5"),
    SupportOption(3, "🫶", "Buy 3 Coffees", "$15"),
    SupportOption(5, "⚡", "Buy 5 Coffees", "$25")
)

@Composable
fun SupportScreen(
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current
    var selectedCups by remember { mutableIntStateOf(1) }
    var copiedLink by remember { mutableStateOf(false) }

    LaunchedEffect(copiedLink) {
        if (copiedLink) {
            delay(1800L)
            copiedLink = false
        }
    }

    val selectedOption = supportOptions.firstOrNull { it.cups == selectedCups } ?: supportOptions.first()

    val openBmc: () -> Unit = {
        try {
            uriHandler.openUri(BMC_URL)
        } catch (_: Exception) {
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp, bottom = 110.dp)
                .defaultMinSize(minHeight = (maxHeight - 130.dp).coerceAtLeast(0.dp)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 400.dp)
            ) {
            // Card Body (offset down so circular logo overlaps top center cleanly)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 36.dp)
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = Color.Black.copy(alpha = 0.65f),
                        spotColor = Color.Black.copy(alpha = 0.75f)
                    )
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF151621))
                    .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(24.dp))
                    .padding(start = 22.dp, end = 22.dp, top = 50.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Support Findex",
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Creator of AURALIX",
                    color = Color(0xFF8C8FA3),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(22.dp))

                // Clean Selectable Rows
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    supportOptions.forEach { option ->
                        val isSelected = selectedCups == option.cups
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) Color(0xFF211E33) else Color(0xFF1D1E2A)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color(0xFF8B5CF6).copy(alpha = 0.75f)
                                    else Color.White.copy(alpha = 0.05f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedCups = option.cups }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = option.emoji,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = option.label,
                                    color = if (isSelected) Color.White else Color(0xFFD6D8E5),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                text = option.price,
                                color = if (isSelected) Color(0xFFFFDD00) else Color.White,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Discreet Link Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1B1C27))
                        .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                        .clickable {
                            clipboardManager.setText(AnnotatedString(BMC_URL))
                            copiedLink = true
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = BMC_DISPLAY,
                        color = Color(0xFF9EA2B7),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = if (copiedLink) Icons.Filled.Check else Icons.Filled.ContentCopy,
                            contentDescription = "Copy Link",
                            tint = if (copiedLink) Color(0xFF4ADE80) else Color(0xFF9EA2B7),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (copiedLink) "Copied" else "Copy",
                            color = if (copiedLink) Color(0xFF4ADE80) else Color(0xFF9EA2B7),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Clean Primary Action Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color(0xFFFFDD00))
                        .clickable { openBmc() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${selectedOption.label} for ${selectedOption.price}",
                        color = Color(0xFF111111),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Overlapping Circular Avatar (Matches Reference Silhouette)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(72.dp)
                    .shadow(12.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .border(2.5.dp, Color(0xFF282A3A), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_adaptive_foreground),
                    contentDescription = "AURALIX",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                )
            }
            }
        }
    }
}
