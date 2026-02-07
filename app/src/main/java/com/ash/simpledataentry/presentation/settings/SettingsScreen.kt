package com.ash.simpledataentry.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ash.simpledataentry.presentation.core.BaseScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var lastData by remember { mutableStateOf(SettingsData()) }
    val state = when (val current = uiState) {
        is com.ash.simpledataentry.presentation.core.UiState.Success -> {
            lastData = current.data
            current.data
        }
        is com.ash.simpledataentry.presentation.core.UiState.Error -> current.previousData ?: lastData
        is com.ash.simpledataentry.presentation.core.UiState.Loading -> lastData
    }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        viewModel.loadAccounts()
    }

    BaseScreen(
        title = "Settings",
        subtitle = "Preferences and accounts",
        navController = navController
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // SYNC CONFIGURATION SECTION
            item {
                SettingsSection(
                    title = "Sync Configuration",
                    description = "Control how often metadata is refreshed.",
                    icon = Icons.Default.Settings
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SyncFrequencySelector(
                            currentFrequency = state.syncFrequency,
                            onFrequencyChanged = viewModel::setSyncFrequency,
                            enabled = true // ENABLED: Now has persistence
                        )
                        Button(
                            onClick = { viewModel.syncMetadataNow() },
                            enabled = !state.isMetadataSyncing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.isMetadataSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Sync metadata now")
                        }
                        state.metadataSyncMessage?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            // DATA MANAGEMENT SECTION
            item {
                SettingsSection(
                    title = "Data Management",
                    description = "Export data for backup or clear local storage.",
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
                    description = "Check for new versions and release notes.",
                    icon = Icons.Default.Settings
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        UpdateSection(
                            isChecking = state.updateCheckInProgress,
                            updateAvailable = state.updateAvailable,
                            latestVersion = state.latestVersion,
                            currentVersion = state.currentVersion,
                            onCheckForUpdates = viewModel::checkForUpdates,
                            enabled = true // ENABLED: Update checking is now fully implemented
                        )
                    }
                }
            }
            
        }
    }

    // Error Snackbar
    (uiState as? com.ash.simpledataentry.presentation.core.UiState.Error)?.error?.let { error ->
        LaunchedEffect(error) {
            // Handle error display
        }
    }
}

// === NEW UI COMPONENTS FOR SETTINGS FEATURES ===

@Composable
private fun SettingsSection(
    title: String,
    description: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
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
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
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
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = enabled)
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
                progress = { exportProgress },
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
    currentVersion: String,
    onCheckForUpdates: () -> Unit,
    enabled: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Current version: $currentVersion",
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
