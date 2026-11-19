package com.videoguard.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoguard.app.data.JamChatMessage
import com.videoguard.app.data.JamConnectionState
import com.videoguard.app.data.ListenTogetherEngine
import com.videoguard.app.service.PlaybackController
import kotlinx.coroutines.launch

private val JamPurple = Color(0xFF8B5CF6)
private val JamCyan = Color(0xFF06B6D4)
private val JamPink = Color(0xFFEC4899)
private val JamDarkBg = Color(0xFF121216)
private val JamCardBg = Color(0xFF1E1E26)
private val YTMusicRed = Color(0xFFFF0033)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenTogetherSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    val connectionState by ListenTogetherEngine.connectionState.collectAsState()
    val isInRoom by ListenTogetherEngine.isInRoom.collectAsState()
    val isHost by ListenTogetherEngine.isHost.collectAsState()
    val roomCode by ListenTogetherEngine.roomCode.collectAsState()
    val hostUsername by ListenTogetherEngine.hostUsername.collectAsState()
    val participants by ListenTogetherEngine.participants.collectAsState()
    val chatMessages by ListenTogetherEngine.chatMessages.collectAsState()
    val myUsername by ListenTogetherEngine.myUsername.collectAsState()
    val autoApprove by ListenTogetherEngine.autoApprove.collectAsState()
    val errorMessage by ListenTogetherEngine.errorMessage.collectAsState()
    val currentTrack by PlaybackController.currentTrack.collectAsState()

    var usernameInput by remember { mutableStateOf(myUsername) }
    var roomCodeInput by remember { mutableStateOf("") }
    var chatInput by remember { mutableStateOf("") }
    var activeTab by remember { mutableStateOf(0) } // 0: Jam Info/Members, 1: Chat

    val chatListState = rememberLazyListState()

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            chatListState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = JamDarkBg,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = Color.White.copy(alpha = 0.25f)
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(JamPurple, JamPink))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Podcasts,
                            contentDescription = "Jam",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Listen Together",
                                color = Color.White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (isInRoom) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(JamCyan.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "LIVE",
                                        color = JamCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (isInRoom) "Syncing with friends in real-time" else "Jam with friends anywhere in the world",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Body: If in room vs Not in room
            if (!isInRoom) {
                // Not in room: Setup Profile & Host/Join
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = JamCardBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Your Display Name",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = usernameInput,
                            onValueChange = {
                                usernameInput = it
                                ListenTogetherEngine.setUsername(context, it)
                            },
                            singleLine = true,
                            placeholder = { Text("Enter your nickname", color = Color.Gray) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = JamPurple,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                                unfocusedContainerColor = Color.Black.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Start Jam (Host) Card
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = JamCardBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PlayCircleFilled,
                                contentDescription = null,
                                tint = JamPurple,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Start a Jam Session",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Create a room and invite your friends. Everyone hears the music at the exact same second.",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                ListenTogetherEngine.createRoom(usernameInput)
                            },
                            enabled = connectionState != JamConnectionState.CONNECTING,
                            colors = ButtonDefaults.buttonColors(containerColor = JamPurple),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            if (connectionState == JamConnectionState.CONNECTING) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Create Jam Session",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Join Jam Card
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = JamCardBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GroupAdd,
                                contentDescription = null,
                                tint = JamCyan,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Join a Friend's Jam",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Enter the 8-character Room Code shared by your friend.",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = roomCodeInput,
                                onValueChange = {
                                    if (it.length <= 12) roomCodeInput = it.uppercase()
                                },
                                singleLine = true,
                                placeholder = { Text("e.g. 6Z9SY3LU", color = Color.Gray, fontSize = 14.sp) },
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Characters,
                                    imeAction = ImeAction.Join
                                ),
                                keyboardActions = KeyboardActions(
                                    onJoin = {
                                        if (roomCodeInput.isNotBlank()) {
                                            ListenTogetherEngine.joinRoom(roomCodeInput, usernameInput)
                                        }
                                    }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = JamCyan,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                    focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                                    unfocusedContainerColor = Color.Black.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = {
                                    if (roomCodeInput.isNotBlank()) {
                                        ListenTogetherEngine.joinRoom(roomCodeInput, usernameInput)
                                    }
                                },
                                enabled = roomCodeInput.trim().length >= 4 && connectionState != JamConnectionState.CONNECTING,
                                colors = ButtonDefaults.buttonColors(containerColor = JamCyan),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(52.dp)
                            ) {
                                Text(
                                    text = "Join",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            } else {
                // In Active Room UI
                // Room Code Banner
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = JamCardBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isHost) "👑 YOU ARE THE HOST" else "🎧 SYNCED WITH ${hostUsername ?: "HOST"}",
                            color = if (isHost) JamPurple else JamCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = roomCode ?: "------",
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 3.sp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            IconButton(
                                onClick = {
                                    roomCode?.let {
                                        clipboardManager.setText(AnnotatedString(it))
                                        Toast.makeText(context, "Room Code copied: $it", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = {
                                    roomCode?.let { code ->
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "Join my AURALIX Listen Together Jam session! Room Code: $code\nListen in real-time together on AURALIX: https://auralix-bice.vercel.app"
                                            )
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Share Jam Code"))
                                    }
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(JamPurple.copy(alpha = 0.2f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = JamPurple,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Current track indicator
                        currentTrack?.let { track ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Playing: ${track.title} • ${track.channelName}",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tabs: Participants vs Live Chat
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.3f))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (activeTab == 0) JamPurple else Color.Transparent)
                            .clickable { activeTab = 0 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Friends (${participants.size})",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (activeTab == 1) JamPurple else Color.Transparent)
                            .clickable { activeTab = 1 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Jam Chat (${chatMessages.size})",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (activeTab == 0) {
                    // Participants List
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    ) {
                        items(participants) { p ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (p.isHost) JamPurple else JamCyan.copy(alpha = 0.5f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = p.username.take(1).uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = p.username,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = if (p.isHost) "Session Host" else "Listener",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                if (p.isHost) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(JamPurple.copy(alpha = 0.25f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "HOST",
                                            color = JamPurple,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else if (isHost) {
                                    IconButton(
                                        onClick = {
                                            ListenTogetherEngine.kickUser(p.userId)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PersonRemove,
                                            contentDescription = "Remove",
                                            tint = Color.Red.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Chat Messages Stream
                    LazyColumn(
                        state = chatListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    ) {
                        items(chatMessages) { msg ->
                            val isMe = msg.username == myUsername
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isMe) JamPurple.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.1f)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Column {
                                        if (!isMe) {
                                            Text(
                                                text = msg.username,
                                                color = JamCyan,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = msg.message,
                                            color = Color.White,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Chat Input
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = chatInput,
                            onValueChange = { chatInput = it },
                            placeholder = { Text("Say something...", color = Color.Gray, fontSize = 13.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = JamPurple,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                                unfocusedContainerColor = Color.Black.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (chatInput.isNotBlank()) {
                                    ListenTogetherEngine.sendChatMessage(chatInput)
                                    chatInput = ""
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(JamPurple)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Quick Reactions Row
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val reactions = listOf("🔥", "❤️", "🎵", "🚀", "👏", "⚡")
                    reactions.forEach { emoji ->
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable {
                                    ListenTogetherEngine.sendChatMessage(emoji)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 16.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Leave / End Room Button
                Button(
                    onClick = {
                        ListenTogetherEngine.leaveRoom()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YTMusicRed.copy(alpha = 0.2f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, YTMusicRed.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = null,
                        tint = YTMusicRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isHost) "End Jam Session" else "Leave Jam Session",
                        color = YTMusicRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
