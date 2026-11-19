package com.videoguard.app.ui.components

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoguard.app.data.AccountRepository
import com.videoguard.app.data.GoogleDriveBackupEngine
import com.videoguard.app.data.SpotifyConnectEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DriveBlue = Color(0xFF4285F4)
private val DriveYellow = Color(0xFFFBBC04)
private val DriveGreen = Color(0xFF34A853)
private val SpotifyGreen = Color(0xFF1DB954)
private val CardBackground = Color(0xFF141624)
private val CardSurface = Color(0xFF1B1E30)

@Composable
fun GoogleDriveBackupCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val backupState by GoogleDriveBackupEngine.backupState.collectAsState()
    val currentAccount by AccountRepository.currentAccount.collectAsState()
    val isSpotifyConnected by SpotifyConnectEngine.isConnected.collectAsState()
    val spotifyUser by SpotifyConnectEngine.userDisplayName.collectAsState()

    LaunchedEffect(Unit) {
        GoogleDriveBackupEngine.init(context)
    }

    // Google Drive SAF Exporter (Save copy to Google Drive)
    val exportToDriveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val outputStream = context.contentResolver.openOutputStream(uri)
                    val success = GoogleDriveBackupEngine.writeBackupToStream(context, outputStream)
                    if (success) {
                        Toast.makeText(context, "Full app backup saved to Google Drive!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Failed to save backup to Google Drive", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Error saving backup: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Google Drive SAF Importer (Open from Google Drive)
    val importFromDriveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val success = GoogleDriveBackupEngine.restoreFromStream(inputStream)
                    if (success) {
                        Toast.makeText(context, "Spotify account, playlists & recommendations restored!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Invalid backup file selected", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Error restoring backup: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = Color.Black.copy(alpha = 0.55f),
                spotColor = DriveBlue.copy(alpha = 0.25f)
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = androidx.compose.foundation.BorderStroke(
            1.2.dp,
            Brush.linearGradient(
                colors = listOf(
                    DriveBlue.copy(alpha = 0.55f),
                    Color.White.copy(alpha = 0.12f),
                    DriveGreen.copy(alpha = 0.40f)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Official Google Drive Badge & Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OfficialGoogleDriveBadge(modifier = Modifier.size(42.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cloud & App Backup",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Spotify, taste profile & playlists",
                        color = Color(0xFF9E9EAF),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Status Pill
                val statusColor = if (backupState.hasLocalBackup) DriveGreen else Color(0xFFF59E0B)
                val statusText = if (backupState.hasLocalBackup) "Ready" else "No Backup"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.08f),
                thickness = 1.dp
            )

            // Status Details Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardSurface.copy(alpha = 0.6f))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                // Spotify Status
                val spText = if (isSpotifyConnected) (spotifyUser?.takeIf { it.isNotBlank() } ?: "Connected") else "Not Connected"
                val spColor = if (isSpotifyConnected) SpotifyGreen else Color(0xFFA0AEC0)
                BackupDetailRow(
                    icon = Icons.Default.MusicNote,
                    label = "Spotify",
                    value = spText,
                    valueColor = spColor
                )

                // Recommendations & Playlists
                BackupDetailRow(
                    icon = Icons.Default.LibraryMusic,
                    label = "Library Data",
                    value = "${backupState.playlistsCount} Playlists • Taste"
                )

                // Auto Restore Status
                BackupDetailRow(
                    icon = Icons.Default.AutoMode,
                    label = "Auto-Restore",
                    value = "Active on Install",
                    valueColor = DriveBlue
                )

                // Last Backup & Size
                BackupDetailRow(
                    icon = Icons.Default.Schedule,
                    label = "Last Backup",
                    value = if (backupState.hasLocalBackup) "${backupState.lastBackupFormatted} (${backupState.formattedSize})" else "Never"
                )
            }

            // ACTION 1: Instant Full App Backup
            Button(
                onClick = {
                    if (!backupState.isBackingUp) {
                        coroutineScope.launch {
                            val success = GoogleDriveBackupEngine.backupLocally(context)
                            if (success) {
                                Toast.makeText(
                                    context,
                                    "Full app data backed up! Auto-restore enabled on reinstall.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(context, "Backup failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                enabled = !backupState.isBackingUp && !backupState.isRestoring,
                colors = ButtonDefaults.buttonColors(containerColor = DriveBlue),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                if (backupState.isBackingUp) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Backing up app...",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = "Back Up",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Instant Full Backup",
                        color = Color.White,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ACTION 2: Save Copy directly to Google Drive
            OutlinedButton(
                onClick = {
                    if (!backupState.isBackingUp && !backupState.isRestoring) {
                        val dateSuffix = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                        exportToDriveLauncher.launch("AURALIX_FullBackup_$dateSuffix.json")
                    }
                },
                enabled = !backupState.isBackingUp && !backupState.isRestoring,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DriveBlue.copy(alpha = 0.45f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = DriveBlue.copy(alpha = 0.08f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = "Drive Upload",
                    tint = DriveBlue,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Upload to Google Drive",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // ACTION 3: Restore Buttons (Quick Device Restore OR Google Drive File)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Quick Restore from Device (Instant, No Picker)
                OutlinedButton(
                    onClick = {
                        if (!backupState.isRestoring) {
                            coroutineScope.launch {
                                try {
                                    val backupFile = withContext(Dispatchers.IO) {
                                        GoogleDriveBackupEngine.findAnyExistingBackup(context)
                                    }
                                    if (backupFile != null && backupFile.exists()) {
                                        val success = GoogleDriveBackupEngine.restoreFromStream(backupFile.inputStream())
                                        if (success) {
                                            Toast.makeText(context, "Spotify & library restored from device!", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Failed to restore backup", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "No local backup found. Please select from Google Drive.", Toast.LENGTH_SHORT).show()
                                        importFromDriveLauncher.launch(arrayOf("application/json", "text/*"))
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(context, "Restore error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    enabled = !backupState.isBackingUp && !backupState.isRestoring,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White.copy(alpha = 0.04f)),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 42.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    if (backupState.isRestoring) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color(0xFF38BDF8),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Restoring...",
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = "Quick Restore",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Quick Restore",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }

                // Restore from Google Drive File
                OutlinedButton(
                    onClick = {
                        if (!backupState.isRestoring) {
                            importFromDriveLauncher.launch(arrayOf("application/json", "text/*"))
                        }
                    },
                    enabled = !backupState.isBackingUp && !backupState.isRestoring,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White.copy(alpha = 0.04f)),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 42.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "Drive Restore",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "From Drive",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }

            // Info Note
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = DriveGreen,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Spotify login, taste profile & playlists restore automatically on reinstall.",
                    color = Color(0xFFA0AEC0),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BackupDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = Color.White
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF8E8EA8),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                color = Color(0xFFCBD5E1),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End
        )
    }
}

/**
 * Authentic Google Drive Triangle Icon Badge (Yellow, Green, Blue).
 */
@Composable
fun OfficialGoogleDriveBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(26.dp)) {
            val w = size.width
            val h = size.height

            // Top yellow rhomboid
            val yellowPath = Path().apply {
                moveTo(w * 0.35f, h * 0.08f)
                lineTo(w * 0.65f, h * 0.08f)
                lineTo(w * 0.95f, h * 0.60f)
                lineTo(w * 0.65f, h * 0.60f)
                close()
            }
            drawPath(yellowPath, DriveYellow)

            // Right green trapezoid
            val greenPath = Path().apply {
                moveTo(w * 0.65f, h * 0.60f)
                lineTo(w * 0.95f, h * 0.60f)
                lineTo(w * 0.80f, h * 0.88f)
                lineTo(w * 0.20f, h * 0.88f)
                close()
            }
            drawPath(greenPath, DriveGreen)

            // Left blue rhomboid
            val bluePath = Path().apply {
                moveTo(w * 0.35f, h * 0.08f)
                lineTo(w * 0.20f, h * 0.35f)
                lineTo(w * 0.05f, h * 0.88f)
                lineTo(w * 0.35f, h * 0.88f)
                lineTo(w * 0.50f, h * 0.60f)
                close()
            }
            drawPath(bluePath, DriveBlue)
        }
    }
}
