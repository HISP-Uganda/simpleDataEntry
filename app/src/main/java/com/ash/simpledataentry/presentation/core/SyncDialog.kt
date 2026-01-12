package com.ash.simpledataentry.presentation.core

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ash.simpledataentry.ui.theme.DatasetAccent
import com.ash.simpledataentry.ui.theme.DatasetAccentLight
import com.ash.simpledataentry.ui.theme.StatusSynced
import com.ash.simpledataentry.ui.theme.StatusSyncedLight

/**
 * Sync mode options for data synchronization
 */
enum class SyncMode {
    DOWNLOAD_ONLY,
    UPLOAD_AND_DOWNLOAD
}

/**
 * Sync dialog with visual option buttons matching the design system
 *
 * @param showDialog Whether the dialog is visible
 * @param onDismiss Called when the dialog should close
 * @param onSync Called when a sync option is selected
 */
@Composable
fun SyncDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onSync: (SyncMode) -> Unit
) {
    if (!showDialog) return

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Header
                Text(
                    text = "Sync Data",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Choose how you want to sync your data with the server.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Download Only Option
                SyncOptionButton(
                    icon = Icons.Outlined.CloudDownload,
                    title = "Download Only",
                    description = "Fetch latest data from server without uploading local changes",
                    borderColor = DatasetAccent,
                    backgroundColor = DatasetAccentLight,
                    iconColor = DatasetAccent,
                    onClick = {
                        onSync(SyncMode.DOWNLOAD_ONLY)
                        onDismiss()
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Upload & Download Option
                SyncOptionButton(
                    icon = Icons.Outlined.CloudUpload,
                    title = "Upload & Download",
                    description = "Upload local changes and fetch latest data from server",
                    borderColor = StatusSynced,
                    backgroundColor = StatusSyncedLight,
                    iconColor = StatusSynced,
                    onClick = {
                        onSync(SyncMode.UPLOAD_AND_DOWNLOAD)
                        onDismiss()
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Cancel Button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

/**
 * Individual sync option button with icon, title, and description
 */
@Composable
private fun SyncOptionButton(
    icon: ImageVector,
    title: String,
    description: String,
    borderColor: Color,
    backgroundColor: Color,
    iconColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 2.dp,
                color = borderColor.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Icon in colored circle
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = backgroundColor,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Text content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
