package com.videoguard.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoguard.app.data.FloatingReaction
import com.videoguard.app.data.ListenTogetherEngine
import kotlin.math.sin

@Composable
fun FloatingReactionsOverlay(
    modifier: Modifier = Modifier
) {
    val reactions by ListenTogetherEngine.floatingReactions.collectAsState()

    if (reactions.isEmpty()) return

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomStart
    ) {
        val maxWidthPx = constraints.maxWidth.toFloat()
        val maxHeightPx = constraints.maxHeight.toFloat()

        reactions.forEach { reaction ->
            SingleFloatingReaction(
                reaction = reaction,
                maxWidthPx = maxWidthPx,
                maxHeightPx = maxHeightPx,
                onFinished = {
                    ListenTogetherEngine.removeFloatingReaction(reaction.id)
                }
            )
        }
    }
}

@Composable
private fun SingleFloatingReaction(
    reaction: FloatingReaction,
    maxWidthPx: Float,
    maxHeightPx: Float,
    onFinished: () -> Unit
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(reaction.id) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2200, easing = FastOutSlowInEasing)
        )
        onFinished()
    }

    val p = progress.value
    // Floating upwards: from 0 up to -65% of screen height
    val yOffsetPx = -p * (maxHeightPx * 0.65f).coerceAtLeast(600f)

    // Sinusoidal wobble: drifts left/right as it floats up
    val xOffsetPx = (maxWidthPx * reaction.startXFraction) + (sin(p * Math.PI.toFloat() * 3f) * 45f)

    // Scale pop-in and stabilize
    val scale = when {
        p < 0.15f -> 0.4f + (p / 0.15f) * 0.9f
        p < 0.7f -> 1.3f - ((p - 0.15f) / 0.55f) * 0.2f
        else -> 1.1f - ((p - 0.7f) / 0.3f) * 0.3f
    }

    // Alpha fade-out near top
    val alpha = when {
        p < 0.65f -> 1f
        else -> (1f - (p - 0.65f) / 0.35f).coerceIn(0f, 1f)
    }

    Box(
        modifier = Modifier
            .offset(
                x = androidx.compose.ui.platform.LocalDensity.current.run { xOffsetPx.toDp() },
                y = androidx.compose.ui.platform.LocalDensity.current.run { yOffsetPx.toDp() }
            )
            .scale(scale)
            .alpha(alpha)
    ) {
        Text(
            text = reaction.emoji,
            fontSize = 32.sp
        )
    }
}
