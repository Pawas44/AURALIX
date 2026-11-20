package com.videoguard.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoguard.app.data.AuthManager

@Composable
fun LoginScreen() {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val isLoading by AuthManager.isLoading.collectAsState()
    val errorMessage by AuthManager.errorMessage.collectAsState()

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val submitLogin = {
        if (!isLoading && username.isNotBlank() && password.isNotBlank()) {
            focusManager.clearFocus()
            AuthManager.login(context, username, password) { _, _ -> }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07070C))
    ) {
        // Soft Purple Ambient Radial Background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Top-center subtle purple glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF8B5CF6).copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.15f),
                    radius = w * 0.8f
                )
            )

            // Bottom-right subtle violet accent
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF6D28D9).copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(w * 0.85f, h * 0.85f),
                    radius = w * 0.7f
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Brand Logo Row: 3-Node Connected Molecule Icon + Brand Name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Connected Molecule Icon (Purple Gradient)
                Canvas(modifier = Modifier.size(24.dp)) {
                    val p1 = Offset(size.width * 0.25f, size.height * 0.35f)
                    val p2 = Offset(size.width * 0.75f, size.height * 0.35f)
                    val p3 = Offset(size.width * 0.50f, size.height * 0.80f)

                    val lineBrush = Brush.linearGradient(listOf(Color(0xFFC084FC), Color(0xFF8B5CF6)))
                    drawLine(lineBrush, p1, p2, strokeWidth = 2.5.dp.toPx())
                    drawLine(lineBrush, p1, p3, strokeWidth = 2.5.dp.toPx())
                    drawLine(lineBrush, p2, p3, strokeWidth = 2.5.dp.toPx())

                    drawCircle(Color(0xFFC084FC), radius = 3.5.dp.toPx(), center = p1)
                    drawCircle(Color(0xFFA855F7), radius = 3.5.dp.toPx(), center = p2)
                    drawCircle(Color(0xFF8B5CF6), radius = 4.dp.toPx(), center = p3)
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = "AURALIX",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.8.sp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Main Heading: "Welcome to AURALIX"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Welcome to ",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "AURALIX",
                    color = Color(0xFFA855F7),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(26.dp))

            // Main Card (Black and Purple Theme, Figma Style)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F101A)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF23223A))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp)
                ) {
                    Text(
                        text = "Sign In",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Enter your username & password to continue",
                        color = Color(0xFF8C92A8),
                        fontSize = 12.5.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Field 1: Username
                    Text(
                        text = "Username",
                        color = Color(0xFFD1D5DB),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(7.dp))

                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            if (errorMessage != null) AuthManager.clearError()
                        },
                        placeholder = {
                            Text(
                                "Enter your username",
                                color = Color(0xFF4B4F63),
                                fontSize = 14.sp
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFA855F7),
                            unfocusedBorderColor = Color(0xFF26253E),
                            focusedContainerColor = Color(0xFF090A12),
                            unfocusedContainerColor = Color(0xFF090A12),
                            cursorColor = Color(0xFFA855F7)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Field 2: Password
                    Text(
                        text = "Password",
                        color = Color(0xFFD1D5DB),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(7.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            if (errorMessage != null) AuthManager.clearError()
                        },
                        placeholder = {
                            Text(
                                "••••••••",
                                color = Color(0xFF4B4F63),
                                fontSize = 14.sp
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle password visibility",
                                    tint = Color(0xFF7E849E)
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFA855F7),
                            unfocusedBorderColor = Color(0xFF26253E),
                            focusedContainerColor = Color(0xFF090A12),
                            unfocusedContainerColor = Color(0xFF090A12),
                            cursorColor = Color(0xFFA855F7)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { submitLogin() }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Error Message Banner (if any error occurs)
                    AnimatedVisibility(
                        visible = !errorMessage.isNullOrBlank(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        errorMessage?.let { msg ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 14.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF2B1220))
                                    .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Error",
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = msg,
                                    color = Color(0xFFFFC5C5),
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Solid Purple Gradient Button ("Log In")
                    Button(
                        onClick = { submitLogin() },
                        enabled = !isLoading && username.isNotBlank() && password.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            disabledContainerColor = Color(0xFF3B2E60).copy(alpha = 0.4f)
                        ),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (!isLoading && username.isNotBlank() && password.isNotBlank()) {
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color(0xFFA855F7),
                                            Color(0xFF7C3AED),
                                            Color(0xFF6366F1)
                                        )
                                    )
                                } else {
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color(0xFF2B2244),
                                            Color(0xFF241C38)
                                        )
                                    )
                                }
                            )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Logging in...",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                text = "Log In",
                                color = if (username.isNotBlank() && password.isNotBlank()) Color.White else Color(0xFF88849E),
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
