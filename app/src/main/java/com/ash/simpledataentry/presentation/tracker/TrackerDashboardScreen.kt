package com.ash.simpledataentry.presentation.tracker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ash.simpledataentry.presentation.core.BaseScreen
import com.ash.simpledataentry.domain.model.Event
import com.ash.simpledataentry.domain.model.TrackedEntityAttributeValue
import com.ash.simpledataentry.ui.theme.TrackerAccent
import com.ash.simpledataentry.ui.theme.TrackerAccentLight
import java.text.SimpleDateFormat
import java.util.*
import java.net.URLDecoder

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
    var selectedTab by rememberSaveable { mutableStateOf(1) }

    // Initialize the view model
    LaunchedEffect(enrollmentId) {
        viewModel.loadEnrollmentDashboard(enrollmentId)
    }

    val decodedProgramName = remember(programName) { URLDecoder.decode(programName, "UTF-8") }

    BaseScreen(
        title = "Tracker Dashboard",
        subtitle = decodedProgramName,
        navController = navController,
        actions = {
            if (selectedTab == 0) {
                IconButton(
                    onClick = {
                        navController.navigate("EditEnrollment/$programId/$programName/$enrollmentId")
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit profile"
                    )
                }
            }
            // Add new event action
            if (uiState.canAddEvents) {
                IconButton(
                    onClick = {
                        // If a stage is selected in dropdown, navigate with that stage pre-selected
                        if (uiState.selectedStageId != null) {
                            navController.navigate("CreateEvent/$programId/$programName/${uiState.selectedStageId}/$enrollmentId")
                        } else {
                            // Show stage selection dialog
                            viewModel.showStageSelectionDialog()
                        }
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
                    val eventsCount = uiState.events.size
                    Column(modifier = Modifier.fillMaxSize()) {
                        TabRow(selectedTabIndex = selectedTab) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("Profile") }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("Events ($eventsCount)") }
                            )
                        }

                        when (selectedTab) {
                            0 -> {
                                ProfileTabContent(
                                    uiState = uiState,
                                    onEditEnrollment = {
                                        navController.navigate("EditEnrollment/$programId/$programName/$enrollmentId")
                                    }
                                )
                            }
                            else -> {
                                EventsTabContent(
                                    uiState = uiState,
                                    onEditEvent = { eventId ->
                                        navController.navigate("EditEvent/$programId/$programName/$eventId/$enrollmentId")
                                    },
                                    onAddEventForStage = { stageId ->
                                        navController.navigate("CreateEvent/$programId/$programName/$stageId/$enrollmentId")
                                    },
                                    viewModel = viewModel
                                )
                            }
                        }
                    }

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
private fun ProfileTabContent(
    uiState: TrackerDashboardUiState,
    onEditEnrollment: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            val displayName = uiState.trackedEntityAttributes.firstOrNull { attribute ->
                attribute.displayName.contains("name", ignoreCase = true) && !attribute.value.isNullOrBlank()
            }?.value ?: "Tracked Entity"

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(TrackerAccentLight, shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = TrackerAccent,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        EnrollmentStatusChip(status = uiState.enrollmentStatus)
                    }

                    if (uiState.trackedEntityAttributes.any { !it.value.isNullOrBlank() }) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        uiState.trackedEntityAttributes
                            .filter { !it.value.isNullOrBlank() }
                            .forEach { attribute ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = attributeIcon(attribute.displayName),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = attribute.displayName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = attribute.value ?: "N/A",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                    }
                }
            }
        }

        item {
            EnrollmentInfoCard(
                uiState = uiState,
                eventsCount = uiState.events.size,
                onEditEnrollment = onEditEnrollment
            )
        }
    }
}

@Composable
private fun EventsTabContent(
    uiState: TrackerDashboardUiState,
    onEditEvent: (String) -> Unit,
    onAddEventForStage: (String) -> Unit,
    viewModel: TrackerDashboardViewModel
) {
    var stageQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val searchTargetIndex = remember(uiState.programStages, stageQuery) {
        val query = stageQuery.trim()
        if (query.isBlank()) {
            -1
        } else {
            uiState.programStages.indexOfFirst { stage ->
                val name = stage.name ?: ""
                name.contains(query, ignoreCase = true)
            }
        }
    }

    LaunchedEffect(searchTargetIndex, uiState.programStages.size) {
        if (searchTargetIndex >= 0) {
            listState.animateScrollToItem(searchTargetIndex + 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (uiState.programStages.isNotEmpty()) {
            item {
                OutlinedTextField(
                    value = stageQuery,
                    onValueChange = { stageQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Search stages...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null
                        )
                    }
                )
            }
        }
        if (uiState.programStages.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
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
                            text = "No program stages configured",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(uiState.programStages) { stage ->
                StageEventListCard(
                    stage = stage,
                    events = uiState.events.filter { it.programStageId == stage.id },
                    eventCount = uiState.eventCountsByStage[stage.id] ?: 0,
                    onViewAllEvents = { stageId ->
                        viewModel.filterEventsByStage(stageId)
                    },
                    onAddEvent = { stageId ->
                        onAddEventForStage(stageId)
                    },
                    onEditEvent = { eventId -> onEditEvent(eventId) },
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
private fun EnrollmentInfoCard(
    uiState: TrackerDashboardUiState,
    eventsCount: Int,
    onEditEnrollment: () -> Unit
) {
    var showAttributes by remember { mutableStateOf(false) }

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

            Spacer(modifier = Modifier.height(8.dp))
            InfoRow(
                label = "Events",
                value = eventsCount.toString()
            )

            // Show Attributes Button (only if attributes exist)
            if (uiState.trackedEntityAttributes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showAttributes = !showAttributes },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (showAttributes) "Hide Details" else "Show Details")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.rotate(if (showAttributes) 180f else 0f)
                    )
                }

                // Collapsible Attributes Section
                AnimatedVisibility(visible = showAttributes) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Tracked Entity Attributes",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        uiState.trackedEntityAttributes.forEach { attribute ->
                            InfoRow(
                                label = attribute.displayName,
                                value = attribute.value ?: "N/A"
                            )
                            if (attribute != uiState.trackedEntityAttributes.last()) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
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
private fun StageEventListCard(
    stage: com.ash.simpledataentry.domain.model.ProgramStage,
    events: List<Event>,
    eventCount: Int,
    onViewAllEvents: (String) -> Unit,
    onAddEvent: (String) -> Unit,
    onEditEvent: (String) -> Unit,
    viewModel: TrackerDashboardViewModel
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showAllEvents by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Stage Header - Always visible
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stage.name ?: stage.id,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Event count badge
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = eventCount.toString(),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.rotate(if (isExpanded) 180f else 0f)
                    )
                }
            }

            // Event Table - Shown when expanded
            androidx.compose.animation.AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    if (events.isEmpty()) {
                        // No events for this stage
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No events recorded for this stage",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            FilledTonalButton(
                                onClick = { onAddEvent(stage.id) }
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
                    } else {
                        // Build table data - show top 3 or all events based on showAllEvents state
                        val displayEvents = if (showAllEvents) events else events.take(3)
                        val (columns, rows) = remember(displayEvents, stage.id, showAllEvents) {
                            viewModel.buildEventTableData(displayEvents, stage.id)
                        }

                        // Show table if we have data
                        if (columns.isNotEmpty() && rows.isNotEmpty()) {
                            CompactEventTable(
                                columns = columns,
                                rows = rows,
                                onRowClick = { row -> onEditEvent(row.id) }
                            )
                        }

                        // Action buttons row
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (events.size > 3) {
                                OutlinedButton(
                                    onClick = { showAllEvents = !showAllEvents },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (showAllEvents) "Show Less" else "View All (${events.size})")
                                }
                            }
                            FilledTonalButton(
                                onClick = { onAddEvent(stage.id) },
                                modifier = Modifier.weight(1f)
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
            }
        }
    }
}

@Composable
private fun CompactEventCard(
    event: Event,
    onEditEvent: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
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
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                EventStatusChip(status = event.status)
            }

            IconButton(onClick = onEditEvent) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit event",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StageFilterDropdown(
    programStages: List<com.ash.simpledataentry.domain.model.ProgramStage>,
    selectedStageId: String?,
    eventCountsByStage: Map<String, Int>,
    totalEventCount: Int,
    onStageSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val selectedStageName = if (selectedStageId == null) {
        "All Stages ($totalEventCount)"
    } else {
        val stage = programStages.find { it.id == selectedStageId }
        val count = eventCountsByStage[selectedStageId] ?: 0
        "${stage?.name ?: "Unknown"} ($count)"
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedStageName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Filter by Stage") },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Dropdown"
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
            colors = OutlinedTextFieldDefaults.colors()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // "All Stages" option
            DropdownMenuItem(
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("All Stages")
                        Text(
                            text = "($totalEventCount)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                onClick = {
                    onStageSelected(null)
                    expanded = false
                },
                leadingIcon = if (selectedStageId == null) {
                    { Icon(Icons.Default.Event, contentDescription = null) }
                } else null
            )

            // Individual stage options
            programStages.forEach { stage ->
                val count = eventCountsByStage[stage.id] ?: 0
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stage.name)
                            Text(
                                text = "($count)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onStageSelected(stage.id)
                        expanded = false
                    },
                    leadingIcon = if (selectedStageId == stage.id) {
                        { Icon(Icons.Default.Event, contentDescription = null) }
                    } else null
                )
            }
        }
    }
}

private fun attributeIcon(label: String): ImageVector {
    val key = label.lowercase(Locale.getDefault())
    return when {
        key.contains("id") || key.contains("identifier") || key.contains("national") -> Icons.Default.Tag
        key.contains("phone") || key.contains("mobile") || key.contains("tel") -> Icons.Default.Phone
        key.contains("birth") || key.contains("dob") || key.contains("date") -> Icons.Default.CalendarMonth
        key.contains("address") || key.contains("location") || key.contains("village") || key.contains("ward") -> Icons.Default.LocationOn
        else -> Icons.AutoMirrored.Filled.Label
    }
}

/**
 * Compact table view for displaying events in the tracker dashboard
 * Shows top 3 events with data values in a line list format
 */
@Composable
private fun CompactEventTable(
    columns: List<EventTableColumn>,
    rows: List<EventTableRow>,
    onRowClick: (EventTableRow) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxWidth()) {
        // Header row
        CompactTableHeaderRow(columns = columns, scrollState = scrollState)

        // Data rows
        rows.forEach { row ->
            CompactTableDataRow(
                row = row,
                columns = columns,
                scrollState = scrollState,
                onClick = { onRowClick(row) }
            )
            if (row != rows.last()) {
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun CompactTableHeaderRow(
    columns: List<EventTableColumn>,
    scrollState: ScrollState
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(scrollState)
            .padding(vertical = 8.dp)
    ) {
        columns.forEach { column ->
            CompactTableHeaderCell(column = column)
        }
    }
}

@Composable
private fun CompactTableHeaderCell(column: EventTableColumn) {
    Box(
        modifier = Modifier
            .width(120.dp)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = column.displayName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CompactTableDataRow(
    row: EventTableRow,
    columns: List<EventTableColumn>,
    scrollState: ScrollState,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .horizontalScroll(scrollState)
            .padding(vertical = 8.dp)
    ) {
        columns.forEach { column ->
            CompactTableDataCell(
                value = row.cells[column.id] ?: "",
                columnId = column.id
            )
        }
    }
}

@Composable
private fun CompactTableDataCell(
    value: String,
    columnId: String
) {
    Box(
        modifier = Modifier
            .width(120.dp)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (columnId == "status") {
                TextAlign.Center
            } else {
                TextAlign.Start
            }
        )
    }
}
