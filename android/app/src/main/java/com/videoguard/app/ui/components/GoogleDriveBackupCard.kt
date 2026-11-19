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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoguard.app.data.AccountRepository
import com.videoguard.app.data.GoogleDriveBackupEngine
import kotlinx.coroutines.launch

private val DriveBlue = Color(0xFF4285F4)
private val DriveYellow = Color(0xFFFBBC04)
private val DriveGreen = Color(0xFF34A853)
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

    LaunchedEffect(Unit) {
        GoogleDriveBackupEngine.init(context)
    }

    // Google Drive Tree Folder Linker (One-time link: creates "AURALIX Data" on Drive & auto-backs up)
    val folderTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val success = GoogleDriveBackupEngine.linkDriveFolder(context, uri)
                if (success) {
                    Toast.makeText(
                        context,
                        "Google Drive connected! 'AURALIX Data' folder created & synced!",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(context, "Could not access selected folder", Toast.LENGTH_SHORT).show()
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
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Official Google Drive Badge & Cloud Sync Headline
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OfficialGoogleDriveBadge(modifier = Modifier.size(44.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Google Drive Cloud Sync",
                        color = Color.White,
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (backupState.isDriveConnected) "Auto-synced to 'AURALIX Data' folder" else "Auto-backup recommendations & library",
                        color = Color(0xFF9E9EAF),
                        fontSize = 11.5.sp
                    )
                }

                // Cloud Status Badge Pill
                val statusText = if (backupState.isDriveConnected) "Connected" else "Not Linked"
                val statusColor = if (backupState.isDriveConnected) DriveGreen else Color(0xFFF59E0B)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.08f),
                thickness = 1.dp
            )

            if (!backupState.isDriveConnected) {
                // NOT LINKED YET: One-click Setup Banner
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardSurface.copy(alpha = 0.6f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderSpecial,
                            contentDescription = null,
                            tint = DriveBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Automatic 'AURALIX Data' Cloud Folder",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "Select your Google Drive once. AURALIX will automatically create an 'AURALIX Data' folder and silently keep all your recommendations, Daily Mixes, and playlists backed up with zero manual file saving.",
                        color = Color(0xFFA0AEC0),
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                }

                // Connect Drive Button (Launches Folder Selector once)
                Button(
                    onClick = {
                        folderTreeLauncher.launch(null)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DriveBlue),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = "Connect",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Select Google Drive & Auto-Create Folder",
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // CONNECTED: Active Cloud Sync Hub
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardSurface.copy(alpha = 0.6f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BackupDetailRow(
                        icon = Icons.Default.Folder,
                        label = "Drive Folder",
                        value = "Google Drive > AURALIX Data"
                    )

                    BackupDetailRow(
                        icon = Icons.Default.Schedule,
                        label = "Last Sync",
                        value = backupState.lastBackupFormatted
                    )

                    BackupDetailRow(
                        icon = Icons.Default.Storage,
                        label = "Backup Size",
                        value = backupState.formattedSize
                    )
                }

                // Action 1: Silent Instant Back Up Now (ZERO files app opens!)
                Button(
                    onClick = {
                        if (!backupState.isBackingUp) {
                            coroutineScope.launch {
                                val success = GoogleDriveBackupEngine.backupToDrive(context)
                                if (success) {
                                    Toast.makeText(
                                        context,
                                        "Backed up to 'AURALIX Data' on Google Drive!",
                                        Toast.LENGTH_SHORT
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
                        .height(48.dp)
                ) {
                    if (backupState.isBackingUp) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Backing up to 'AURALIX Data'...",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Back Up",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Back Up Now (Silent Auto-Sync)",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Action 2: Silent Instant Restore Now (ZERO files app opens!)
                OutlinedButton(
                    onClick = {
                        if (!backupState.isRestoring) {
                            coroutineScope.launch {
                                val success = GoogleDriveBackupEngine.restoreFromDrive(context)
                                if (success) {
                                    Toast.makeText(
                                        context,
                                        "Restored recommendations & library from Google Drive!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "No backup found in 'AURALIX Data'. Back up first!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    },
                    enabled = !backupState.isBackingUp && !backupState.isRestoring,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White.copy(alpha = 0.04f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    if (backupState.isRestoring) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color(0xFFCBD5E1),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Restoring taste & playlists...",
                            color = Color(0xFFCBD5E1),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Restore",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = "Restore from 'AURALIX Data'",
                            color = Color(0xFFCBD5E1),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Switch / Change Drive Account Option
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "100% Private & Free",
                            color = Color(0xFF818CF8),
                            fontSize = 11.sp
                        )
                    }

                    Text(
                        text = "Change Drive Folder",
                        color = Color(0xFF9E9EAF),
                        fontSize = 11.5.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                folderTreeLauncher.launch(null)
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupDetailRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFA0AEC0),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                color = Color(0xFFCBD5E1),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = value,
            color = Color.White,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold
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
