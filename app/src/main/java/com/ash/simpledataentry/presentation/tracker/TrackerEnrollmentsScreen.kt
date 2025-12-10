package com.ash.simpledataentry.presentation.tracker

import android.annotation.SuppressLint
import android.text.format.DateFormat
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ash.simpledataentry.domain.model.CompletionStatus
import com.ash.simpledataentry.domain.model.SyncStatus
import com.ash.simpledataentry.presentation.core.BaseScreen
import com.ash.simpledataentry.presentation.core.AdaptiveLoadingOverlay
import com.ash.simpledataentry.presentation.core.UiState
import com.ash.simpledataentry.ui.theme.DHIS2BlueDeep
import org.hisp.dhis.mobile.ui.designsystem.theme.TextColor
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

/**
 * Screen for displaying and managing tracker program enrollments
 * Separated from DatasetInstancesScreen to handle tracker-specific needs
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerEnrollmentsScreen(
    navController: NavController,
    programId: String,
    programName: String,
    viewModel: TrackerEnrollmentsViewModel = hiltViewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsState()
    var showSyncDialog by remember { mutableStateOf(false) }

    // Initialize with program ID
    LaunchedEffect(programId) {
        viewModel.initialize(programId)
    }

    // Refresh when screen resumes
    LaunchedEffect(Unit) {
        viewModel.refreshData()
    }

    // Extract data safely from UiState
    val data = when (val state = uiState) {
        is UiState.Success -> state.data
        is UiState.Error -> state.previousData ?: com.ash.simpledataentry.presentation.tracker.TrackerEnrollmentsData()
        is UiState.Loading -> com.ash.simpledataentry.presentation.tracker.TrackerEnrollmentsData()
    }

    // Show success messages via snackbar
    LaunchedEffect(data.syncMessage) {
        data.syncMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
        }
    }

    BaseScreen(
        title = programName,
        navController = navController,
        actions = {
            // Sync button
            val isLoading = uiState is UiState.Loading
            val isSyncing = (uiState as? UiState.Success)?.backgroundOperation != null

            IconButton(
                onClick = {
                    if (!isSyncing) {
                        showSyncDialog = true
                    }
                },
                enabled = !isLoading && !isSyncing
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync",
                        tint = TextColor.OnSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val encodedProgramName = URLEncoder.encode(programName, "UTF-8")
                    navController.navigate("CreateEnrollment/$programId/$encodedProgramName") {
                        launchSingleTop = true
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create Enrollment"
                )
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AdaptiveLoadingOverlay(
                uiState = uiState,
                modifier = Modifier.fillMaxSize()
            ) {
                // Content with data
                when {
                    data.enrollments.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "No enrollments found",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap + to create a new enrollment",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(data.enrollments) { enrollment ->
                                EnrollmentCard(
                                    enrollment = enrollment,
                                    onClick = {
                                        val encodedProgramId = URLEncoder.encode(programId, "UTF-8")
                                        val encodedProgramName = URLEncoder.encode(programName, "UTF-8")
                                        navController.navigate("TrackerDashboard/${enrollment.id}/$encodedProgramId/$encodedProgramName") {
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Sync dialog
            if (showSyncDialog) {
                AlertDialog(
                    onDismissRequest = { showSyncDialog = false },
                    title = { Text("Sync Enrollments") },
                    text = { Text("Download enrollments from server and upload local changes?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showSyncDialog = false
                                viewModel.syncEnrollments()
                            }
                        ) {
                            Text("Sync")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSyncDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Snackbar at the bottom
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun EnrollmentCard(
    enrollment: com.ash.simpledataentry.domain.model.ProgramInstance.TrackerEnrollment,
    onClick: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val enrollmentDate = enrollment.enrollmentDate?.let { dateFormatter.format(it) } ?: "No date"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp
        ),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Title row with date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${enrollment.organisationUnit.name}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = enrollmentDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Status indicators
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Enrollment status
                StatusChip(
                    text = enrollment.state.name,
                    color = when (enrollment.state) {
                        com.ash.simpledataentry.domain.model.ProgramInstanceState.COMPLETED -> Color(0xFF4CAF50)
                        com.ash.simpledataentry.domain.model.ProgramInstanceState.ACTIVE -> Color(0xFFFFA726)
                        com.ash.simpledataentry.domain.model.ProgramInstanceState.CANCELLED -> Color(0xFFEF5350)
                        else -> Color(0xFF9E9E9E)
                    }
                )

                // Sync status
                StatusChip(
                    text = enrollment.syncStatus.name,
                    color = when (enrollment.syncStatus) {
                        SyncStatus.SYNCED -> DHIS2BlueDeep
                        SyncStatus.NOT_SYNCED -> Color(0xFFFFA726)
                        else -> Color(0xFF9E9E9E)
                    }
                )
            }
        }
    }
}

@Composable
private fun StatusChip(
    text: String,
    color: Color
) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.padding(0.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
