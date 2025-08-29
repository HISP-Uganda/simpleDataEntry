package com.ash.simpledataentry.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ash.simpledataentry.domain.model.SavedAccount
import com.ash.simpledataentry.presentation.core.BaseScreen
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val haptic = LocalHapticFeedback.current
    var showDeleteAllConfirmation by remember { mutableStateOf(false) }
    var showDeleteAccountConfirmation by remember { mutableStateOf<SavedAccount?>(null) }
    var showEditAccountDialog by remember { mutableStateOf<SavedAccount?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadAccounts()
    }

    BaseScreen(
        title = "Settings",
        navController = navController
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SYNC CONFIGURATION SECTION
            item {
                SettingsSection(
                    title = "Sync Configuration",
                    icon = Icons.Default.Settings
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SyncFrequencySelector(
                            currentFrequency = state.syncFrequency,
                            onFrequencyChanged = viewModel::setSyncFrequency,
                            enabled = true // ENABLED: Now has persistence
                        )
                        
                    }
                }
            }
            
            // DATA MANAGEMENT SECTION
            item {
                SettingsSection(
                    title = "Data Management",
                    icon = Icons.Default.Security
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DataManagementActions(
                            isExporting = state.isExporting,
                            exportProgress = state.exportProgress,
                            isDeleting = state.isDeleting,
                            onExportData = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.exportData()
                            },
                            onDeleteData = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.deleteAllData()
                            },
                            enabled = true // ENABLED: Data deletion is now fully implemented
                        )
                    }
                }
            }
            
            // APP UPDATE SECTION
            item {
                SettingsSection(
                    title = "App Updates",
                    icon = Icons.Default.Settings
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        UpdateSection(
                            isChecking = state.updateCheckInProgress,
                            updateAvailable = state.updateAvailable,
                            latestVersion = state.latestVersion,
                            onCheckForUpdates = viewModel::checkForUpdates,
                            enabled = true // ENABLED: Update checking is now fully implemented
                        )
                    }
                }
            }
            
            // ACCOUNT MANAGEMENT SECTION
            item {
                SettingsSection(
                    title = "Account Management",
                    icon = Icons.Default.Person
                ) {
                    // Content is in the next items
                }
            }

            // Account Statistics
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${state.accounts.size}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Total Accounts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Divider(
                            modifier = Modifier
                                .height(48.dp)
                                .width(1.dp)
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val activeCount = state.accounts.count { it.isActive }
                            Text(
                                text = "$activeCount",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "Active Account",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Security Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Security",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        val encryptionStatus = if (state.isEncryptionAvailable) "Enabled" else "Not Available"
                        val encryptionColor = if (state.isEncryptionAvailable) 
                            MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                            
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Android Keystore Encryption",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = encryptionColor.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = encryptionStatus,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = encryptionColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        
                        Text(
                            text = "Your account passwords are encrypted using Android Keystore for maximum security.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Saved Accounts Section Header
            if (state.accounts.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Saved Accounts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(
                            onClick = { 
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showDeleteAllConfirmation = true 
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete All")
                        }
                    }
                }
            }

            // Saved Accounts List
            if (state.accounts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "No Saved Accounts",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Your saved accounts will appear here for easy management.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(state.accounts) { account ->
                    AccountManagementItem(
                        account = account,
                        dateFormatter = dateFormatter,
                        onEditClick = { showEditAccountDialog = account },
                        onDeleteClick = { showDeleteAccountConfirmation = account }
                    )
                }
            }
        }
    }

    // Delete All Accounts Confirmation Dialog
    if (showDeleteAllConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirmation = false },
            title = { Text("Delete All Accounts") },
            text = {
                Text("Are you sure you want to delete all saved accounts? This action cannot be undone and you will need to re-enter your login credentials.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.deleteAllAccounts()
                        showDeleteAllConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Single Account Confirmation Dialog
    showDeleteAccountConfirmation?.let { account ->
        AlertDialog(
            onDismissRequest = { showDeleteAccountConfirmation = null },
            title = { Text("Delete Account") },
            text = {
                Text("Are you sure you want to delete the account \"${account.displayName}\"? This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.deleteAccount(account.id)
                        showDeleteAccountConfirmation = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountConfirmation = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Account Dialog
    showEditAccountDialog?.let { account ->
        EditAccountDialog(
            account = account,
            onDismiss = { showEditAccountDialog = null },
            onSave = { newDisplayName ->
                viewModel.updateAccountDisplayName(account.id, newDisplayName)
                showEditAccountDialog = null
            }
        )
    }

    // Error Snackbar
    state.error?.let { error ->
        LaunchedEffect(error) {
            // Handle error display
        }
    }
}

@Composable
private fun AccountManagementItem(
    account: SavedAccount,
    dateFormatter: SimpleDateFormat,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (account.isActive) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (account.isActive) 8.dp else 4.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Account Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = account.displayName.take(2).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Account Details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = account.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (account.isActive) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                
                Text(
                    text = account.username,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = account.serverUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = "Last used: ${dateFormatter.format(Date(account.lastUsed))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Action Buttons
            Row {
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Account",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Account",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditAccountDialog(
    account: SavedAccount,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var displayName by remember { mutableStateOf(account.displayName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Account") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Edit the display name for this account.",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Account Details:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Username: ${account.username}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Server: ${account.serverUrl}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(displayName.trim()) },
                enabled = displayName.trim().isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// === NEW UI COMPONENTS FOR SETTINGS FEATURES ===

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable 
private fun SyncFrequencySelector(
    currentFrequency: SyncFrequency,
    onFrequencyChanged: (SyncFrequency) -> Unit,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Metadata sync frequency",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        ExposedDropdownMenuBox(
            expanded = expanded && enabled,
            onExpandedChange = { if (enabled) expanded = !expanded }
        ) {
            OutlinedTextField(
                readOnly = true,
                value = currentFrequency.displayName,
                onValueChange = { },
                label = { Text("Sync Frequency") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled)
                },
                enabled = enabled,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                SyncFrequency.values().forEach { frequency ->
                    DropdownMenuItem(
                        text = { Text(frequency.displayName) },
                        onClick = {
                            if (enabled) {
                                onFrequencyChanged(frequency)
                                expanded = false
                            }
                        },
                        enabled = enabled
                    )
                }
            }
        }
    }
}

@Composable
private fun DataManagementActions(
    isExporting: Boolean,
    exportProgress: Float,
    isDeleting: Boolean,
    onExportData: () -> Unit,
    onDeleteData: () -> Unit,
    enabled: Boolean = true
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Export Data Button
        Button(
            onClick = onExportData,
            enabled = enabled && !isExporting && !isDeleting,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isExporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Exporting... ${(exportProgress * 100).toInt()}%")
            } else {
                Text("Export Data (Offline ZIP)")
            }
        }
        
        if (isExporting) {
            LinearProgressIndicator(
                progress = exportProgress,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Delete Data Button  
        OutlinedButton(
            onClick = { if (enabled) showDeleteConfirmation = true },
            enabled = enabled && !isExporting && !isDeleting,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        ) {
            if (isDeleting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.error,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Deleting...")
            } else {
                Text("Delete All Data")
            }
        }
    }
    
    // Delete Confirmation Dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete All Data") },
            text = { 
                Text("This will permanently delete all data including saved accounts, entries, and settings. This cannot be undone.") 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteData()
                        showDeleteConfirmation = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun UpdateSection(
    isChecking: Boolean,
    updateAvailable: Boolean,
    latestVersion: String?,
    onCheckForUpdates: () -> Unit,
    enabled: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Current version: 1.0.0",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        if (updateAvailable && latestVersion != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Update Available!",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Version $latestVersion is available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        
        Button(
            onClick = onCheckForUpdates,
            enabled = enabled && !isChecking,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Checking...")
            } else {
                Text("Check for Updates")
            }
        }
    }
}