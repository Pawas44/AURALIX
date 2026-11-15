package com.videoguard.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoguard.app.data.AppSettingsRepository
import com.videoguard.app.service.PlaybackController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsBottomSheet(
    onDismiss: () -> Unit
) {
    val isBackgroundPlay by PlaybackController.isBackgroundPlayEnabled.collectAsState()
    val isLoopEnabled by PlaybackController.isLoopEnabled.collectAsState()
    val isAutoplayEnabled by AppSettingsRepository.isAutoplay.collectAsState()
    val dontRepeatSuggestions by AppSettingsRepository.dontRepeatSuggestions.collectAsState()
    val streamingQuality by AppSettingsRepository.streamingQuality.collectAsState()
    val downloadQuality by AppSettingsRepository.downloadQuality.collectAsState()
    val volumeLeveling by AppSettingsRepository.volumeLeveling.collectAsState()
    val currentQuality by PlaybackController.playbackQuality.collectAsState()
    val isCaptionsEnabled by PlaybackController.isCaptionsEnabled.collectAsState()
    val currentSpeed by PlaybackController.playbackSpeed.collectAsState()
    val sleepTimer by PlaybackController.sleepTimerMinutes.collectAsState()
    val sleepTimerRemaining by PlaybackController.sleepTimerRemainingSeconds.collectAsState()
    val sleepTimerEndOnTrackFinish by PlaybackController.sleepTimerEndOnTrackFinish.collectAsState()
    val currentTrack by PlaybackController.currentTrack.collectAsState()

    var showQualityDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var customSleepMinutes by remember { mutableIntStateOf(20) }
    var showStatsDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF212131F),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.35f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Sound & Suggestions",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF262838), thickness = 0.8.dp)

            Spacer(modifier = Modifier.height(10.dp))

            // 1. Suggestions Section (ByTune)
            SettingsSectionHeader("Suggestions")
            SettingsGroupCard {
                SettingsCardSwitchRow(
                    icon = Icons.Default.AllInclusive,
                    title = "Autoplay",
                    subtitle = "When your songs run out, keep playing music like them",
                    checked = isAutoplayEnabled,
                    onCheckedChange = { AppSettingsRepository.setAutoplay(it) }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.8.dp, modifier = Modifier.padding(vertical = 4.dp))
                SettingsCardSwitchRow(
                    icon = Icons.Default.Sync,
                    title = "Don't repeat suggestions",
                    subtitle = "Keep recently played tracks out of the home feed",
                    checked = dontRepeatSuggestions,
                    onCheckedChange = { AppSettingsRepository.setDontRepeatSuggestions(it) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Video Resolution Section
            SettingsSectionHeader("Video Resolution")
            SettingsGroupCard {
                SettingsRow(
                    icon = Icons.Default.HighQuality,
                    title = "Video Quality (Resolution)",
                    subtitle = currentQuality,
                    onClick = { showQualityDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Streaming Section (Audio)
            SettingsSectionHeader("Streaming")
            SettingsSegmentedCard(
                icon = Icons.Default.Wifi,
                title = "Streaming quality",
                subtitle = "The highest quality Auralix streams",
                options = listOf("Low", "Medium", "High", "Lossless"),
                selectedOption = streamingQuality,
                onOptionSelected = { AppSettingsRepository.setStreamingQuality(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Downloads Section
            SettingsSectionHeader("Downloads")
            SettingsSegmentedCard(
                icon = Icons.Default.Download,
                title = "Download quality",
                subtitle = when (downloadQuality.lowercase()) {
                    "low" -> "128 kbps • Fast download & saves storage"
                    "medium" -> "192 kbps • Balanced quality"
                    else -> "320 kbps • Studio lossless audio"
                },
                options = listOf("Low", "Medium", "High"),
                selectedOption = downloadQuality,
                onOptionSelected = { AppSettingsRepository.setDownloadQuality(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 5. Loudness Section (ByTune)
            SettingsSectionHeader("Loudness")
            SettingsSegmentedCard(
                icon = Icons.Default.VolumeUp,
                title = "Volume leveling",
                subtitle = "Bring every song to a similar loudness",
                options = listOf("Off", "Quiet", "Normal", "Loud"),
                selectedOption = volumeLeveling,
                onOptionSelected = { AppSettingsRepository.setVolumeLeveling(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Playback Controls
            SettingsSectionHeader("Playback")
            SettingsGroupCard {
                SettingsCardSwitchRow(
                    icon = Icons.Default.Headphones,
                    title = "Background playback",
                    subtitle = "Keep audio playing when screen is off",
                    checked = isBackgroundPlay,
                    onCheckedChange = { PlaybackController.toggleBackgroundPlay() }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.8.dp, modifier = Modifier.padding(vertical = 4.dp))
                SettingsCardSwitchRow(
                    icon = Icons.Default.Repeat,
                    title = "Loop video",
                    subtitle = "Automatically replay when finished",
                    checked = isLoopEnabled,
                    onCheckedChange = { PlaybackController.toggleLoop() }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.8.dp, modifier = Modifier.padding(vertical = 4.dp))
                SettingsCardSwitchRow(
                    icon = Icons.Default.ClosedCaption,
                    title = "Captions (Subtitles)",
                    subtitle = "Display closed captions on video",
                    checked = isCaptionsEnabled,
                    onCheckedChange = { PlaybackController.toggleCaptions() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 5. Advanced Controls
            SettingsSectionHeader("Advanced")
            SettingsGroupCard {
                val speedText = if (currentSpeed == 1.0f) "Normal (1.0x)" else "${currentSpeed}x"
                SettingsRow(
                    icon = Icons.Default.Speed,
                    title = "Playback speed",
                    subtitle = speedText,
                    onClick = { showSpeedDialog = true }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.8.dp, modifier = Modifier.padding(vertical = 4.dp))
                val timerText = when {
                    sleepTimerRemaining != null -> "🌙 ${PlaybackController.formatSleepTimerRemaining(sleepTimerRemaining)} remaining"
                    else -> "Off"
                }
                SettingsRow(
                    icon = Icons.Default.Timer,
                    title = "Sleep timer",
                    subtitle = timerText,
                    onClick = { showSleepTimerDialog = true }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.8.dp, modifier = Modifier.padding(vertical = 4.dp))
                SettingsRow(
                    icon = Icons.Default.Info,
                    title = "Stats & video info",
                    subtitle = "Resolution, ID, codec & channel",
                    onClick = { showStatsDialog = true }
                )
            }
        }
    }

    // Video Quality Selection Dialog
    if (showQualityDialog) {
        val qualities = listOf(
            "Auto (1080p)" to "Auto (Best available)",
            "1080p" to "1080p Full HD",
            "720p" to "720p HD",
            "480p" to "480p Standard",
            "360p" to "360p Data Saver"
        )
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = {
                Text(
                    text = "Video Resolution",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    qualities.forEach { (qKey, qLabel) ->
                        val isSelected = currentQuality == qKey || (qKey.startsWith("Auto") && currentQuality.startsWith("Auto"))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    PlaybackController.setPlaybackQuality(qKey)
                                    showQualityDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = qLabel,
                                color = if (isSelected) Color(0xFF38BDF8) else Color.White,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text("Close", color = Color(0xFFAAAAAA))
                }
            },
            containerColor = Color(0xFF1E212B),
            shape = RoundedCornerShape(18.dp)
        )
    }

    // Speed Selection Dialog
    if (showSpeedDialog) {
        val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Playback Speed", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    speeds.forEach { sp ->
                        val label = if (sp == 1.0f) "Normal" else "${sp}x"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    PlaybackController.setPlaybackSpeed(sp)
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, color = Color.White, fontSize = 16.sp)
                            if (currentSpeed == sp) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF3EA6FF))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("Close", color = Color(0xFFAAAAAA))
                }
            },
            containerColor = Color(0xFF282828),
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Sleep Timer Dialog with Live Countdown & Custom Timer
    if (showSleepTimerDialog) {
        val presets = listOf(
            5 to "5 minutes",
            15 to "15 minutes",
            30 to "30 minutes",
            45 to "45 minutes",
            60 to "60 minutes"
        )
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = null,
                        tint = Color(0xFFB39DDB),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sleep Timer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (sleepTimerRemaining != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1E2138),
                            border = BorderStroke(1.dp, Color(0xFFB39DDB).copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Active Countdown", color = Color(0xFFB39DDB), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "${PlaybackController.formatSleepTimerRemaining(sleepTimerRemaining)} remaining",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Button(
                                    onClick = {
                                        PlaybackController.setSleepTimer(null)
                                        showSleepTimerDialog = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                                    shape = RoundedCornerShape(16.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Turn Off", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Text("Presets", color = Color(0xFFAAAAAA), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))

                    presets.forEach { (mins, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    PlaybackController.setSleepTimer(mins)
                                    showSleepTimerDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, color = Color.White, fontSize = 15.sp)
                            if (sleepTimer == mins && !sleepTimerEndOnTrackFinish) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF40DCA5), modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // End of Track option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                PlaybackController.setSleepTimer(null, endOnTrackFinish = true)
                                showSleepTimerDialog = false
                            }
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("End of this track", color = Color.White, fontSize = 15.sp)
                        if (sleepTimerEndOnTrackFinish) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF40DCA5), modifier = Modifier.size(18.dp))
                        }
                    }

                    HorizontalDivider(color = Color(0xFF383838), thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))

                    Text("Custom Duration", color = Color(0xFFAAAAAA), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Custom Minutes Stepper
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { customSleepMinutes = (customSleepMinutes - 5).coerceAtLeast(1) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("–5", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        IconButton(
                            onClick = { customSleepMinutes = (customSleepMinutes - 1).coerceAtLeast(1) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("–1", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }

                        Text(
                            text = "$customSleepMinutes min",
                            color = Color(0xFF40DCA5),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(
                            onClick = { customSleepMinutes = (customSleepMinutes + 1).coerceAtMost(360) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("+1", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }

                        IconButton(
                            onClick = { customSleepMinutes = (customSleepMinutes + 5).coerceAtMost(360) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("+5", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            PlaybackController.setSleepTimer(customSleepMinutes)
                            showSleepTimerDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3EA6FF)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                    ) {
                        Text("Set $customSleepMinutes Min Timer", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) {
                    Text("Close", color = Color(0xFFAAAAAA))
                }
            },
            containerColor = Color(0xFF222432),
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Stats Dialog
    if (showStatsDialog) {
        AlertDialog(
            onDismissRequest = { showStatsDialog = false },
            title = { Text("Video Stats & Info", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Title: ${currentTrack?.title ?: "N/A"}", color = Color.White, fontSize = 13.sp)
                    Text("Channel: ${currentTrack?.channelName ?: "N/A"}", color = Color(0xFFAAAAAA), fontSize = 13.sp)
                    Text("Video ID: ${currentTrack?.id ?: "N/A"}", color = Color(0xFFAAAAAA), fontSize = 13.sp)
                    Text("Quality: ${AppSettingsRepository.streamingQuality.value}", color = Color(0xFFAAAAAA), fontSize = 13.sp)
                    Text("Audio: High Bitrate WebM/Opus", color = Color(0xFFAAAAAA), fontSize = 13.sp)
                    Text("Background Play: ${if (isBackgroundPlay) "Active" else "Disabled"}", color = Color(0xFFAAAAAA), fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showStatsDialog = false }) {
                    Text("Done", color = Color(0xFF3EA6FF), fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF282828),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        color = Color(0xFFA0A3B5),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
    )
}

@Composable
private fun SettingsGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1B1C28),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsCardSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF262838)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Color(0xFF8E90A2),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF1B1C28),
                checkedTrackColor = Color.White,
                uncheckedThumbColor = Color(0xFF8E90A2),
                uncheckedTrackColor = Color(0xFF262838),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun SettingsSegmentedCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    SettingsGroupCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF262838)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color(0xFF8E90A2),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF12131C))
                .padding(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEach { opt ->
                    val isSelected = opt.equals(selectedOption, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF383A4E) else Color.Transparent)
                            .clickable { onOptionSelected(opt) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = opt,
                            color = if (isSelected) Color.White else Color(0xFF8E90A2),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color(0xFFAAAAAA), fontSize = 12.sp)
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color(0xFF888888),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color(0xFFAAAAAA), fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF3EA6FF),
                uncheckedThumbColor = Color(0xFFAAAAAA),
                uncheckedTrackColor = Color(0xFF333333)
            )
        )
    }
}
