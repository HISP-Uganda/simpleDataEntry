package com.ash.simpledataentry.presentation.tracker

import android.annotation.SuppressLint
import android.text.format.DateFormat
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
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
import com.ash.simpledataentry.domain.model.SyncStatus
import com.ash.simpledataentry.domain.model.TrackedEntityAttributeValue
import com.ash.simpledataentry.presentation.core.BaseScreen
import com.ash.simpledataentry.presentation.core.AdaptiveLoadingOverlay
import com.ash.simpledataentry.presentation.core.UiState
import com.ash.simpledataentry.ui.theme.StatusCompleted
import com.ash.simpledataentry.ui.theme.StatusCompletedLight
import com.ash.simpledataentry.ui.theme.StatusDraft
import com.ash.simpledataentry.ui.theme.StatusDraftLight
import com.ash.simpledataentry.ui.theme.StatusSynced
import com.ash.simpledataentry.ui.theme.StatusSyncedLight
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
    var searchQuery by remember { mutableStateOf("") }

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
    val filteredEnrollments = if (searchQuery.isBlank()) {
        data.enrollments
    } else {
        val query = searchQuery.trim().lowercase(Locale.getDefault())
        data.enrollments.filter { enrollment ->
            enrollment.organisationUnit.name.lowercase(Locale.getDefault()).contains(query) ||
                enrollment.state.name.lowercase(Locale.getDefault()).contains(query) ||
                enrollment.syncStatus.name.lowercase(Locale.getDefault()).contains(query)
        }
    }
    val subtitle = if (filteredEnrollments.size == 1) "1 enrollment" else "${filteredEnrollments.size} enrollments"

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
        subtitle = subtitle,
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
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("Search enrollments...") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null
                                )
                            }
                        )

                        IconButton(
                            onClick = { },
                            enabled = false
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { },
                            enabled = false
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Bulk",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                // Content with data
                when {
                    filteredEnrollments.isEmpty() -> {
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
                            Text(
                                text = "If enrollments are missing, sync to refresh.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { viewModel.syncEnrollments() }
                            ) {
                                Text("Sync now")
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredEnrollments) { enrollment ->
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
    val displayName = remember(enrollment.attributes, enrollment.trackedEntityInstance) {
        resolveTrackedEntityName(enrollment.attributes, enrollment.trackedEntityInstance)
    }
    val idValue = remember(enrollment.attributes) {
        resolveAttributeValue(enrollment.attributes, listOf("id", "identifier", "national"))
    }
    val phoneValue = remember(enrollment.attributes) {
        resolveAttributeValue(enrollment.attributes, listOf("phone", "mobile", "tel"))
    }

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
            // Title row with tracked entity name
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    if (!idValue.isNullOrBlank()) {
                        Text(
                            text = idValue,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    if (!phoneValue.isNullOrBlank()) {
                        Text(
                            text = phoneValue,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = enrollment.organisationUnit.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Enrolled $enrollmentDate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (enrollment.syncStatus == SyncStatus.NOT_SYNCED &&
                enrollment.state != com.ash.simpledataentry.domain.model.ProgramInstanceState.COMPLETED) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = StatusDraft,
                    trackColor = StatusDraftLight
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
                    containerColor = when (enrollment.state) {
                        com.ash.simpledataentry.domain.model.ProgramInstanceState.COMPLETED -> StatusCompletedLight
                        else -> StatusDraftLight
                    },
                    contentColor = when (enrollment.state) {
                        com.ash.simpledataentry.domain.model.ProgramInstanceState.COMPLETED -> StatusCompleted
                        else -> StatusDraft
                    }
                )

                // Sync status
                StatusChip(
                    text = enrollment.syncStatus.name,
                    containerColor = when (enrollment.syncStatus) {
                        SyncStatus.SYNCED -> StatusSyncedLight
                        else -> StatusDraftLight
                    },
                    contentColor = when (enrollment.syncStatus) {
                        SyncStatus.SYNCED -> StatusSynced
                        else -> StatusDraft
                    }
                )
            }
        }
    }
}

private fun resolveTrackedEntityName(
    attributes: List<TrackedEntityAttributeValue>,
    fallbackId: String
): String {
    val nonEmpty = attributes.filter { !it.value.isNullOrBlank() }
    if (nonEmpty.isEmpty()) {
        return fallbackId.ifBlank { "Tracked Entity" }
    }

    val firstName = nonEmpty.firstOrNull { matchesAttribute(it.displayName, listOf("first", "given")) }?.value
    val lastName = nonEmpty.firstOrNull { matchesAttribute(it.displayName, listOf("last", "surname", "family")) }?.value
    val fullName = listOfNotNull(firstName, lastName).joinToString(" ").trim()
    if (fullName.isNotBlank()) {
        return fullName
    }

    val nameValue = nonEmpty.firstOrNull { matchesAttribute(it.displayName, listOf("name")) }?.value
    if (!nameValue.isNullOrBlank()) {
        return nameValue
    }

    return nonEmpty.firstOrNull()?.value ?: fallbackId.ifBlank { "Tracked Entity" }
}

private fun resolveAttributeValue(
    attributes: List<TrackedEntityAttributeValue>,
    keywords: List<String>
): String? {
    return attributes.firstOrNull { attribute ->
        !attribute.value.isNullOrBlank() && matchesAttribute(attribute.displayName, keywords)
    }?.value
}

private fun matchesAttribute(label: String, keywords: List<String>): Boolean {
    val key = label.lowercase(Locale.getDefault())
    return keywords.any { key.contains(it) }
}

@Composable
private fun StatusChip(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.padding(0.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
