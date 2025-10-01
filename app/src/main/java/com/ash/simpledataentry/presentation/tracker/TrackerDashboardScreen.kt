package com.ash.simpledataentry.presentation.tracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ash.simpledataentry.presentation.core.BaseScreen
import com.ash.simpledataentry.domain.model.Event
import com.ash.simpledataentry.domain.model.TrackedEntityAttributeValue
import java.text.SimpleDateFormat
import java.util.*

/**
 * Dashboard screen showing enrollment details and associated events
 * Provides overview of tracker enrollment with options to add/edit events
 */
@Composable
fun TrackerDashboardScreen(
    navController: NavController,
    enrollmentId: String,
    programId: String,
    programName: String,
    viewModel: TrackerDashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Initialize the view model
    LaunchedEffect(enrollmentId) {
        viewModel.loadEnrollmentDashboard(enrollmentId)
    }

    BaseScreen(
        title = "Tracker Dashboard",
        navController = navController,
        actions = {
            // Add new event action
            if (uiState.canAddEvents) {
                IconButton(
                    onClick = {
                        navController.navigate("CreateEvent/$programId/$programName")
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add new event"
                    )
                }
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Loading enrollment dashboard...")
                        }
                    }
                }
                uiState.error != null -> {
                    ErrorContent(
                        error = uiState.error!!,
                        onRetry = {
                            viewModel.loadEnrollmentDashboard(enrollmentId)
                        }
                    )
                }
                else -> {
                    DashboardContent(
                        uiState = uiState,
                        onEditEnrollment = {
                            navController.navigate("EditEnrollment/$programId/$programName/$enrollmentId")
                        },
                        onEditEvent = { eventId ->
                            navController.navigate("EditEvent/$programId/$programName/$eventId/$enrollmentId")
                        },
                        onAddEvent = {
                            viewModel.showStageSelectionDialog()
                        },
                        viewModel = viewModel
                    )

                    // Program Stage Selection Dialog
                    if (uiState.showStageSelectionDialog) {
                        ProgramStageSelectionDialog(
                            programStages = uiState.programStages,
                            existingEventCounts = uiState.eventCountsByStage,
                            onStageSelected = { stage ->
                                viewModel.hideStageSelectionDialog()
                                navController.navigate("CreateEvent/$programId/$programName/${stage.id}/$enrollmentId")
                            },
                            onDismiss = {
                                viewModel.hideStageSelectionDialog()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardContent(
    uiState: TrackerDashboardUiState,
    onEditEnrollment: () -> Unit,
    onEditEvent: (String) -> Unit,
    onAddEvent: () -> Unit,
    viewModel: TrackerDashboardViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Enrollment Information Card
        item {
            EnrollmentInfoCard(
                uiState = uiState,
                onEditEnrollment = onEditEnrollment
            )
        }

        // Tracked Entity Attributes Card (if available)
        if (uiState.trackedEntityAttributes.isNotEmpty()) {
            item {
                TrackedEntityAttributesCard(
                    attributes = uiState.trackedEntityAttributes
                )
            }
        }

        // Events Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Events",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                if (uiState.canAddEvents) {
                    FilledTonalButton(
                        onClick = { onAddEvent() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Event")
                    }
                }
            }
        }

        // Events List
        if (uiState.events.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No events recorded",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (uiState.canAddEvents) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { onAddEvent() }
                            ) {
                                Text("Add First Event")
                            }
                        }
                    }
                }
            }
        } else {
            items(uiState.events) { event ->
                EventCard(
                    event = event,
                    onEditEvent = { onEditEvent(event.id) }
                )
            }
        }
    }
}

@Composable
private fun EnrollmentInfoCard(
    uiState: TrackerDashboardUiState,
    onEditEnrollment: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Enrollment Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onEditEnrollment) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit enrollment"
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Enrollment Date
            InfoRow(
                label = "Enrollment Date",
                value = uiState.enrollmentDate?.let {
                    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(it)
                } ?: "N/A"
            )

            // Incident Date (if applicable)
            if (uiState.incidentDate != null) {
                Spacer(modifier = Modifier.height(8.dp))
                InfoRow(
                    label = uiState.incidentDateLabel ?: "Incident Date",
                    value = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(uiState.incidentDate)
                )
            }

            // Organisation Unit
            Spacer(modifier = Modifier.height(8.dp))
            InfoRow(
                label = "Organisation Unit",
                value = uiState.organisationUnitName ?: "N/A"
            )

            // Enrollment Status
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Status:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                EnrollmentStatusChip(status = uiState.enrollmentStatus)
            }
        }
    }
}

@Composable
private fun TrackedEntityAttributesCard(
    attributes: List<TrackedEntityAttributeValue>
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Tracked Entity Attributes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            attributes.forEach { attribute ->
                InfoRow(
                    label = attribute.displayName,
                    value = attribute.value ?: "N/A"
                )
                if (attribute != attributes.last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun EventCard(
    event: Event,
    onEditEvent: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = event.programStageName ?: "Event",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onEditEvent) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit event"
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Event Date
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = event.eventDate?.let {
                        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(it)
                    } ?: "N/A",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Event Status
            Spacer(modifier = Modifier.height(8.dp))
            EventStatusChip(status = event.status)
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun EnrollmentStatusChip(status: String) {
    val backgroundColor = when (status.uppercase()) {
        "ACTIVE" -> MaterialTheme.colorScheme.primaryContainer
        "COMPLETED" -> MaterialTheme.colorScheme.tertiaryContainer
        "CANCELLED" -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when (status.uppercase()) {
        "ACTIVE" -> MaterialTheme.colorScheme.onPrimaryContainer
        "COMPLETED" -> MaterialTheme.colorScheme.onTertiaryContainer
        "CANCELLED" -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = backgroundColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.padding(0.dp)
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EventStatusChip(status: String) {
    val backgroundColor = when (status.uppercase()) {
        "ACTIVE" -> MaterialTheme.colorScheme.primaryContainer
        "COMPLETED" -> MaterialTheme.colorScheme.tertiaryContainer
        "VISITED" -> MaterialTheme.colorScheme.secondaryContainer
        "SCHEDULE" -> MaterialTheme.colorScheme.surfaceVariant
        "OVERDUE" -> MaterialTheme.colorScheme.errorContainer
        "SKIPPED" -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when (status.uppercase()) {
        "ACTIVE" -> MaterialTheme.colorScheme.onPrimaryContainer
        "COMPLETED" -> MaterialTheme.colorScheme.onTertiaryContainer
        "VISITED" -> MaterialTheme.colorScheme.onSecondaryContainer
        "SCHEDULE" -> MaterialTheme.colorScheme.onSurfaceVariant
        "OVERDUE" -> MaterialTheme.colorScheme.onErrorContainer
        "SKIPPED" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = backgroundColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.padding(0.dp)
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Error loading enrollment",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}