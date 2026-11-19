package com.videoguard.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoguard.app.data.AuthManager
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LoginScreen() {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val isLoading by AuthManager.isLoading.collectAsState()
    val errorMessage by AuthManager.errorMessage.collectAsState()

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val infiniteTransition = rememberInfiniteTransition(label = "AuraPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val auraRotate by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotate"
    )

    val submitLogin = {
        if (!isLoading && username.isNotBlank() && password.isNotBlank()) {
            focusManager.clearFocus()
            AuthManager.login(context, username, password) { _, _ -> }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07080E))
    ) {
        // Futuristic Cosmic Canvas with Starfield and Atmospheric Gradient Mesh
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Deep background gradient
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF06070B), Color(0xFF0D101C), Color(0xFF080911))
                )
            )

            // Dynamic Nebular Radiance (Top-Left Cyan)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF00E5FF).copy(alpha = 0.16f * pulseScale), Color.Transparent),
                    center = Offset(w * 0.15f, h * 0.18f),
                    radius = w * 0.75f
                )
            )

            // Dynamic Nebular Radiance (Center-Right Violet)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF8B5CF6).copy(alpha = 0.14f * pulseScale), Color.Transparent),
                    center = Offset(w * 0.90f, h * 0.45f),
                    radius = w * 0.80f
                )
            )

            // Dynamic Nebular Radiance (Bottom Coral Fire)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFF5436).copy(alpha = 0.15f), Color.Transparent),
                    center = Offset(w * 0.50f, h * 0.92f),
                    radius = w * 0.85f
                )
            )

            // Starfield / Micro-Dust Constellation Points
            val stars = listOf(
                Offset(w * 0.12f, h * 0.08f),
                Offset(w * 0.85f, h * 0.12f),
                Offset(w * 0.72f, h * 0.22f),
                Offset(w * 0.22f, h * 0.32f),
                Offset(w * 0.92f, h * 0.70f),
                Offset(w * 0.18f, h * 0.78f),
                Offset(w * 0.40f, h * 0.15f),
                Offset(w * 0.65f, h * 0.88f),
                Offset(w * 0.30f, h * 0.92f)
            )

            for ((i, star) in stars.withIndex()) {
                val r = if (i % 2 == 0) 2.2.dp.toPx() else 1.5.dp.toPx()
                val alpha = if (i % 3 == 0) 0.9f else 0.55f
                drawCircle(Color.White.copy(alpha = alpha), radius = r, center = star)
            }

            // Orbital Ring Accent
            val center = Offset(w * 0.5f, h * 0.22f)
            drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = 0.08f),
                radius = 110.dp.toPx() * pulseScale,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // Floating Holographic Brand Emblem
            Box(
                modifier = Modifier.size(92.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer Pulse Halo
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                listOf(
                                    Color(0xFF00E5FF).copy(alpha = 0.35f),
                                    Color(0xFFFF5436).copy(alpha = 0.35f),
                                    Color(0xFF8B5CF6).copy(alpha = 0.35f),
                                    Color(0xFF00E5FF).copy(alpha = 0.35f)
                                )
                            )
                        )
                )

                // Inner Glass Core
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color(0xFF101320))
                        .border(
                            1.5.dp,
                            Brush.linearGradient(
                                listOf(Color(0xFF00E5FF), Color(0xFFFF5436))
                            ),
                            RoundedCornerShape(26.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Security Core",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Frosted Pill Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF141828).copy(alpha = 0.90f))
                    .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E5FF))
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = "VIP GATEWAY · FINDEXAUTH",
                        color = Color(0xFF00E5FF),
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Brand Typography
            Text(
                text = "AURALIX",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp
            )

            Text(
                text = "Sign in to unlock unlimited streaming",
                color = Color(0xFF909BB2),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(26.dp))

            // Cyberpunk Glassmorphic Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xF2131625)),
                border = androidx.compose.foundation.BorderStroke(
                    1.2.dp,
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF00E5FF).copy(alpha = 0.40f),
                            Color(0xFF282C42),
                            Color(0xFFFF5436).copy(alpha = 0.35f)
                        )
                    )
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp)
                ) {
                    Text(
                        text = "Authentication Required",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Enter your ID & password. Once authenticated, this app will stay permanently unlocked on this device.",
                        color = Color(0xFF8692A8),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Field 1: License / Username
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E5FF))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LICENSE ID / USERNAME",
                            color = Color(0xFF9AA8C2),
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(7.dp))

                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            if (errorMessage != null) AuthManager.clearError()
                        },
                        placeholder = {
                            Text(
                                "Enter username",
                                color = Color(0xFF555F77),
                                fontSize = 14.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "User",
                                tint = if (username.isNotBlank()) Color(0xFF00E5FF) else Color(0xFF5E6A85)
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0xFF282D42),
                            focusedContainerColor = Color(0xFF181C2E),
                            unfocusedContainerColor = Color(0xFF161928),
                            cursorColor = Color(0xFF00E5FF)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Field 2: Password
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF5436))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SECURITY PASSWORD",
                            color = Color(0xFF9AA8C2),
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(7.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            if (errorMessage != null) AuthManager.clearError()
                        },
                        placeholder = {
                            Text(
                                "Enter password",
                                color = Color(0xFF555F77),
                                fontSize = 14.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Password",
                                tint = if (password.isNotBlank()) Color(0xFFFF5436) else Color(0xFF5E6A85)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle password visibility",
                                    tint = Color(0xFF8692A8)
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFF5436),
                            unfocusedBorderColor = Color(0xFF282D42),
                            focusedContainerColor = Color(0xFF181C2E),
                            unfocusedContainerColor = Color(0xFF161928),
                            cursorColor = Color(0xFFFF5436)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { submitLogin() }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Error Alert Banner
                    AnimatedVisibility(
                        visible = !errorMessage.isNullOrBlank(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        errorMessage?.let { msg ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF321217))
                                    .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Error",
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = msg,
                                    color = Color(0xFFFFC5C5),
                                    fontSize = 12.sp,
                                    lineHeight = 16.5.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Solid High-Energy Gradient Button ("Unlock AURALIX")
                    Button(
                        onClick = { submitLogin() },
                        enabled = !isLoading && username.isNotBlank() && password.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF5436),
                            disabledContainerColor = Color(0xFFFF5436).copy(alpha = 0.35f)
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Verifying Credentials...",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Unlock AURALIX",
                                    color = Color.White,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Unlock",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Footer Security Chip
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF10121C))
                    .border(0.8.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Hardware Locked",
                    tint = Color(0xFF6F7A94),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Hardware Locked · 256-Bit Encrypted Session",
                    color = Color(0xFF6F7A94),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}
