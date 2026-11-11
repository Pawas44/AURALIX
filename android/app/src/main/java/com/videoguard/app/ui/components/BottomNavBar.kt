package com.videoguard.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.videoguard.app.data.AccountRepository
import com.videoguard.app.model.AppMode
import com.videoguard.app.model.YouTubeMusicTab
import com.videoguard.app.model.YouTubeTab
import com.videoguard.app.ui.theme.LiquidGlassDefaults

@Composable
fun BottomNavBar(
    currentMode: AppMode,
    selectedYTTab: YouTubeTab = YouTubeTab.HOME,
    selectedMusicTab: YouTubeMusicTab = YouTubeMusicTab.HOME,
    isSearchActive: Boolean = false,
    isSupportActive: Boolean = false,
    onHomeClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onAccountClick: () -> Unit = {},
    onSupportClick: () -> Unit = {},
    onYTTabSelected: (YouTubeTab) -> Unit = {},
    onMusicTabSelected: (YouTubeMusicTab) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isMusic = currentMode == AppMode.YOUTUBE_MUSIC

    val isHomeSelected = !isSearchActive && !isSupportActive && if (!isMusic) {
        selectedYTTab == YouTubeTab.HOME
    } else {
        selectedMusicTab == YouTubeMusicTab.HOME
    }

    val isSearchSelected = isSearchActive

    val isAccountSelected = !isSearchActive && !isSupportActive && if (!isMusic) {
        selectedYTTab == YouTubeTab.YOU
    } else {
        selectedMusicTab == YouTubeMusicTab.LIBRARY
    }

    val isSupportSelected = !isSearchActive && isSupportActive

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // Floating Liquid Glass Capsule Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(26.dp),
                    ambientColor = Color.Black.copy(alpha = 0.5f),
                    spotColor = Color.Black.copy(alpha = 0.6f)
                )
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xE0171828),
                            Color(0xCC0E0F1A)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = LiquidGlassDefaults.BorderSpecular,
                    shape = RoundedCornerShape(26.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Home
                BottomNavItem(
                    title = "Home",
                    icon = if (isHomeSelected) Icons.Filled.Home else Icons.Outlined.Home,
                    isSelected = isHomeSelected,
                    onClick = {
                        onHomeClick()
                        onYTTabSelected(YouTubeTab.HOME)
                        onMusicTabSelected(YouTubeMusicTab.HOME)
                    }
                )

                // 2. Search
                BottomNavItem(
                    title = "Search",
                    icon = if (isSearchSelected) Icons.Filled.Search else Icons.Outlined.Search,
                    isSelected = isSearchSelected,
                    onClick = onSearchClick
                )

                // 3. Account
                BottomAccountNavItem(
                    title = "Account",
                    isSelected = isAccountSelected,
                    onClick = {
                        onAccountClick()
                        onYTTabSelected(YouTubeTab.YOU)
                        onMusicTabSelected(YouTubeMusicTab.LIBRARY)
                    }
                )

                // 4. Support
                BottomNavItem(
                    title = "Support",
                    icon = if (isSupportSelected) Icons.Filled.Coffee else Icons.Outlined.Coffee,
                    isSelected = isSupportSelected,
                    selectedTint = Color(0xFFFFDD00),
                    onClick = onSupportClick
                )
            }
        }
    }
}

@Composable
private fun RowScope.BottomNavItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    selectedTint: Color = Color.White,
    onClick: () -> Unit
) {
    val iconTint by animateColorAsState(if (isSelected) selectedTint else Color(0xFF9E9EAF), label = "tint")
    val pillBg = if (isSelected) selectedTint.copy(alpha = 0.14f) else Color.Transparent

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(18.dp))
            .background(pillBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(23.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                color = iconTint,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun RowScope.BottomAccountNavItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val currentAccount by AccountRepository.currentAccount.collectAsState()
    val isIncognito by AccountRepository.isIncognito.collectAsState()
    val account = currentAccount
    val iconTint by animateColorAsState(if (isSelected) Color.White else Color(0xFF9E9EAF), label = "accTint")
    val pillBg = if (isSelected) Color.White.copy(alpha = 0.12f) else Color.Transparent

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(18.dp))
            .background(pillBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isIncognito) {
                    Icon(
                        imageVector = Icons.Default.VisibilityOff,
                        contentDescription = title,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                } else if (account != null && !account.avatarUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = account.avatarUrl,
                        contentDescription = account.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .then(
                                if (isSelected) Modifier.border(1.5.dp, Color.White, CircleShape)
                                else Modifier.border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                            )
                    )
                } else if (account != null) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(account.avatarBgColorHex))
                            .then(
                                if (isSelected) Modifier.border(1.5.dp, Color.White, CircleShape)
                                else Modifier.border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = account.initial,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                } else {
                    Icon(
                        imageVector = if (isSelected) Icons.Filled.AccountCircle else Icons.Outlined.AccountCircle,
                        contentDescription = title,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                color = iconTint,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
