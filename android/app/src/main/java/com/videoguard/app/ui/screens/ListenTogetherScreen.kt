package com.videoguard.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.videoguard.app.ui.components.ListenTogetherContent

@Composable
fun ListenTogetherScreen(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF101016))
    ) {
        ListenTogetherContent(
            onDismiss = onBack,
            isTabScreen = true
        )
    }
}
