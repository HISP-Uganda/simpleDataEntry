package com.ash.simpledataentry.presentation.datasetInstances

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class SyncOptions(
    val uploadLocalData: Boolean = false,
    val localInstanceCount: Int = 0,
    val isEditEntryContext: Boolean = false // true when called from edit entry screen
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncConfirmationDialog(
    syncOptions: SyncOptions,
    onConfirm: (uploadFirst: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var showUploadConfirmation by remember { mutableStateOf(false) }

    if (!showUploadConfirmation) {
        // Initial sync dialog
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null
                    )
                    Text(if (syncOptions.isEditEntryContext) "Sync Data Values" else "Sync Dataset Instances")
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Choose your sync option:",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    if (syncOptions.localInstanceCount > 0) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "Local Data Detected",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Text(
                                    text = if (syncOptions.isEditEntryContext) {
                                        "You have ${syncOptions.localInstanceCount} data value(s) for this dataset instance that haven't been uploaded to the server."
                                    } else {
                                        "You have ${syncOptions.localInstanceCount} local dataset instance(s) that haven't been uploaded to the server."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    
                    Text(
                        text = if (syncOptions.localInstanceCount > 0) {
                            if (syncOptions.isEditEntryContext) {
                                "Would you like to upload your data values first before downloading updates from the server?"
                            } else {
                                "Would you like to upload your local data first before downloading updates from the server?"
                            }
                        } else {
                            if (syncOptions.isEditEntryContext) {
                                "This will download the latest data for this dataset instance from the server."
                            } else {
                                "This will download the latest dataset instances from the server."
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                if (syncOptions.localInstanceCount > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                onConfirm(false) // Download only
                            }
                        ) {
                            Text("Download Only")
                        }
                        Button(
                            onClick = {
                                showUploadConfirmation = true
                            }
                        ) {
                            Text("Upload & Download")
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            onConfirm(false) // Download only
                        }
                    ) {
                        Text("Sync Now")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    } else {
        // Upload confirmation dialog
        AlertDialog(
            onDismissRequest = { showUploadConfirmation = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null
                    )
                    Text("Confirm Upload")
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Upload Summary:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (syncOptions.isEditEntryContext) {
                                    "• ${syncOptions.localInstanceCount} data value(s) from this dataset instance will be uploaded"
                                } else {
                                    "• ${syncOptions.localInstanceCount} dataset instance(s) will be uploaded"
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "• Upload will happen before downloading updates",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "• Local data will be preserved and merged with server data",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    
                    Text(
                        text = "This operation cannot be undone. Are you sure you want to proceed?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onConfirm(true) // Upload first, then download
                    }
                ) {
                    Text("Confirm Upload")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUploadConfirmation = false }
                ) {
                    Text("Back")
                }
            }
        )
    }
}