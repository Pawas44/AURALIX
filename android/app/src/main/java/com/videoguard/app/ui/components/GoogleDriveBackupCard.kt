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
import androidx.compose.ui.geometry.Offset
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DriveBlue = Color(0xFF4285F4)
private val DriveYellow = Color(0xFFFBBC04)
private val DriveGreen = Color(0xFF34A853)
private val DriveRed = Color(0xFFEA4335)

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

    // Google Drive SAF File Exporter (Create Document in Google Drive)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val outputStream = context.contentResolver.openOutputStream(uri)
                val success = GoogleDriveBackupEngine.writeBackupToStream(context, outputStream)
                if (success) {
                    Toast.makeText(context, "Recommendations & Library backed up to Google Drive!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Failed to save backup to Google Drive", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Google Drive SAF File Importer (Open Document from Google Drive)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val inputStream = context.contentResolver.openInputStream(uri)
                val success = GoogleDriveBackupEngine.restoreFromStream(inputStream)
                if (success) {
                    Toast.makeText(context, "Recommendations & Playlists restored from Google Drive!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Invalid backup file selected", Toast.LENGTH_SHORT).show()
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
                        text = "Backup & restore recommendations & playlists",
                        color = Color(0xFF9E9EAF),
                        fontSize = 11.5.sp
                    )
                }

                // Cloud Ready Badge Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(DriveGreen.copy(alpha = 0.15f))
                        .border(1.dp, DriveGreen.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
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
                                .background(DriveGreen)
                        )
                        Text(
                            text = "Cloud Ready",
                            color = DriveGreen,
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

            // Info Details
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardSurface.copy(alpha = 0.6f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BackupDetailRow(
                    icon = Icons.Default.Schedule,
                    label = "Last Backup",
                    value = backupState.lastBackupFormatted
                )

                BackupDetailRow(
                    icon = Icons.Default.Storage,
                    label = "Library Size",
                    value = backupState.formattedSize
                )

                val accountEmail = currentAccount?.email?.takeIf { it.isNotBlank() } ?: "Google Account Ready"
                BackupDetailRow(
                    icon = Icons.Default.AccountCircle,
                    label = "Drive Account",
                    value = accountEmail
                )
            }

            // Action 1: Back Up to Google Drive (Primary Vibrant Blue Button)
            Button(
                onClick = {
                    if (!backupState.isBackingUp) {
                        val dateSuffix = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                        exportLauncher.launch("AURALIX_Backup_$dateSuffix.json")
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
                        text = "Backing up to Google Drive...",
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
                        text = "Back Up to Google Drive",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Action 2: Restore from Google Drive (Secondary Sleek Glass Button)
            OutlinedButton(
                onClick = {
                    if (!backupState.isRestoring) {
                        importLauncher.launch(arrayOf("application/json", "text/*"))
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
                        text = "Restoring taste & library...",
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
                        text = "Restore from Google Drive",
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Security & Privacy Note
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFF818CF8),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "100% Free & Private. Stored directly on your personal Google Drive.",
                    color = Color(0xFF818CF8),
                    fontSize = 11.sp
                )
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
