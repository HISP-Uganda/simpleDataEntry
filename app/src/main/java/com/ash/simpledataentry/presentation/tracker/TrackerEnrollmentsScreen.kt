package com.ash.simpledataentry.presentation.tracker

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ash.simpledataentry.domain.model.OrganisationUnit
import com.ash.simpledataentry.domain.model.PeriodFilterType
import com.ash.simpledataentry.domain.model.SyncStatus
import com.ash.simpledataentry.domain.model.TrackedEntityAttributeValue
import com.ash.simpledataentry.presentation.core.BaseScreen
import com.ash.simpledataentry.presentation.core.AdaptiveLoadingOverlay
import com.ash.simpledataentry.presentation.core.DatePickerUtils
import com.ash.simpledataentry.presentation.core.DateRangePickerDialog
import com.ash.simpledataentry.presentation.core.LoadingOperation
import com.ash.simpledataentry.presentation.core.OrgUnitTreePickerDialog
import com.ash.simpledataentry.presentation.core.ShimmerLoadingList
import com.ash.simpledataentry.presentation.core.UiState
import com.ash.simpledataentry.ui.theme.StatusCompleted
import com.ash.simpledataentry.ui.theme.StatusCompletedLight
import com.ash.simpledataentry.ui.theme.StatusDraft
import com.ash.simpledataentry.ui.theme.StatusDraftLight
import com.ash.simpledataentry.ui.theme.StatusSynced
import com.ash.simpledataentry.ui.theme.StatusSyncedLight
import com.ash.simpledataentry.util.PeriodHelper
import org.hisp.dhis.android.core.common.RelativePeriod
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
    var isLineList by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showOrgUnitPicker by remember { mutableStateOf(false) }
    var filterState by remember { mutableStateOf(TrackerEnrollmentFilterState()) }
    var orgUnits by remember { mutableStateOf<List<OrganisationUnit>>(emptyList()) }
    val periodHelper = remember { PeriodHelper() }

    // Initialize with program ID
    LaunchedEffect(programId) {
        viewModel.initialize(programId)
    }

    // Refresh when screen resumes
    LaunchedEffect(Unit) {
        viewModel.refreshData()
    }

    var lastSuccessData by remember { mutableStateOf<TrackerEnrollmentsData?>(null) }
    val currentUiState = uiState
    val data = when (currentUiState) {
        is UiState.Success -> currentUiState.data.also { lastSuccessData = it }
        is UiState.Error -> currentUiState.previousData?.also { lastSuccessData = it } ?: lastSuccessData
        is UiState.Loading -> lastSuccessData
    }
    val showInitialShimmer = currentUiState is UiState.Loading &&
        currentUiState.operation is LoadingOperation.Initial &&
        data == null
    val hasBlockingOverlay = currentUiState is UiState.Loading &&
        currentUiState.operation !is LoadingOperation.Initial &&
        data != null
    LaunchedEffect(programId) {
        orgUnits = runCatching { viewModel.getUserOrgUnits(programId) }.getOrDefault(emptyList())
    }

    val filteredEnrollments = remember(data?.enrollments, searchQuery, filterState) {
        val base = data?.enrollments.orEmpty()
        applyTrackerEnrollmentFilters(
            enrollments = base,
            searchQuery = searchQuery,
            filterState = filterState,
            periodHelper = periodHelper
        )
    }
    val subtitle = if (filteredEnrollments.size == 1) "1 enrollment" else "${filteredEnrollments.size} enrollments"

    // Show success messages via snackbar
    LaunchedEffect(data?.syncMessage) {
        data?.syncMessage?.let {
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
            val isSyncing = uiState is UiState.Loading &&
                (uiState as UiState.Loading).operation !is LoadingOperation.Initial

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
            val content: @Composable () -> Unit = {
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
                            onClick = { showFilterDialog = true },
                            enabled = true
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter",
                                tint = if (filterState.hasActiveFilters()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !isLineList,
                            onClick = { isLineList = false },
                            label = { Text("Cards") }
                        )
                        FilterChip(
                            selected = isLineList,
                            onClick = { isLineList = true },
                            label = { Text("Line list") }
                        )
                    }
                    // Content with data
                    when {
                        showInitialShimmer -> {
                            ShimmerLoadingList(
                                itemCount = 6,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
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
                            if (isLineList) {
                                EnrollmentLineList(
                                    enrollments = filteredEnrollments,
                                    onEnrollmentClick = { enrollment ->
                                        val encodedProgramId = URLEncoder.encode(programId, "UTF-8")
                                        val encodedProgramName = URLEncoder.encode(programName, "UTF-8")
                                        navController.navigate("TrackerDashboard/${enrollment.id}/$encodedProgramId/$encodedProgramName") {
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(items = filteredEnrollments, key = { it.id }) { enrollment ->
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
            }

            if (hasBlockingOverlay) {
                AdaptiveLoadingOverlay(
                    uiState = uiState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    content()
                }
            } else {
                content()
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

            if (showFilterDialog) {
                TrackerEnrollmentFilterDialog(
                    currentFilter = filterState,
                    orgUnits = orgUnits,
                    onFilterChanged = { updated ->
                        filterState = updated
                    },
                    onOpenOrgUnitPicker = { showOrgUnitPicker = true },
                    onClearFilters = {
                        filterState = TrackerEnrollmentFilterState()
                    },
                    onDismiss = { showFilterDialog = false }
                )
            }

            if (showOrgUnitPicker) {
                OrgUnitTreePickerDialog(
                    orgUnits = orgUnits,
                    selectedOrgUnitId = filterState.orgUnitId,
                    onOrgUnitSelected = { orgUnit ->
                        filterState = filterState.copy(
                            orgUnitId = orgUnit.id,
                            orgUnitName = orgUnit.name
                        )
                        showOrgUnitPicker = false
                    },
                    onDismiss = { showOrgUnitPicker = false }
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
    val visitsCount = enrollment.events.size

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
                StatusChip(
                    text = "Events $visitsCount",
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
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
                val completionLabel = when (enrollment.state) {
                    com.ash.simpledataentry.domain.model.ProgramInstanceState.COMPLETED -> "Completed"
                    else -> "Active"
                }
                val syncLabel = if (enrollment.syncStatus == SyncStatus.SYNCED) "Synced" else "Not synced"

                // Enrollment status
                StatusChip(
                    text = completionLabel,
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
                    text = syncLabel,
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

private data class TrackerEnrollmentFilterState(
    val periodType: PeriodFilterType = PeriodFilterType.ALL,
    val relativePeriod: RelativePeriod? = null,
    val customFromDate: Date? = null,
    val customToDate: Date? = null,
    val syncStatus: SyncStatus = SyncStatus.ALL,
    val enrollmentStatus: EnrollmentStatusFilter = EnrollmentStatusFilter.ALL,
    val orgUnitId: String? = null,
    val orgUnitName: String? = null
) {
    fun hasActiveFilters(): Boolean {
        return periodType != PeriodFilterType.ALL ||
            syncStatus != SyncStatus.ALL ||
            enrollmentStatus != EnrollmentStatusFilter.ALL ||
            orgUnitId != null
    }
}

private enum class EnrollmentStatusFilter(val displayName: String) {
    ALL("All"),
    ACTIVE("Active"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled")
}

private fun applyTrackerEnrollmentFilters(
    enrollments: List<com.ash.simpledataentry.domain.model.ProgramInstance.TrackerEnrollment>,
    searchQuery: String,
    filterState: TrackerEnrollmentFilterState,
    periodHelper: PeriodHelper
): List<com.ash.simpledataentry.domain.model.ProgramInstance.TrackerEnrollment> {
    val query = searchQuery.trim().lowercase(Locale.getDefault())
    val periodRange = when (filterState.periodType) {
        PeriodFilterType.RELATIVE -> filterState.relativePeriod?.let { periodHelper.getDateRange(it) }
        PeriodFilterType.CUSTOM_RANGE -> {
            if (filterState.customFromDate != null && filterState.customToDate != null) {
                Pair(filterState.customFromDate, filterState.customToDate)
            } else {
                null
            }
        }
        else -> null
    }

    return enrollments.filter { enrollment ->
        val name = resolveTrackedEntityName(enrollment.attributes, enrollment.trackedEntityInstance)
        val searchMatches = if (query.isBlank()) {
            true
        } else {
            enrollment.organisationUnit.name.lowercase(Locale.getDefault()).contains(query) ||
                enrollment.state.name.lowercase(Locale.getDefault()).contains(query) ||
                enrollment.syncStatus.name.lowercase(Locale.getDefault()).contains(query) ||
                name.lowercase(Locale.getDefault()).contains(query)
        }

        val syncMatches = when (filterState.syncStatus) {
            SyncStatus.ALL -> true
            SyncStatus.SYNCED -> enrollment.syncStatus == SyncStatus.SYNCED
            SyncStatus.NOT_SYNCED -> enrollment.syncStatus == SyncStatus.NOT_SYNCED
        }

        val statusMatches = when (filterState.enrollmentStatus) {
            EnrollmentStatusFilter.ALL -> true
            EnrollmentStatusFilter.ACTIVE ->
                enrollment.state == com.ash.simpledataentry.domain.model.ProgramInstanceState.ACTIVE
            EnrollmentStatusFilter.COMPLETED ->
                enrollment.state == com.ash.simpledataentry.domain.model.ProgramInstanceState.COMPLETED
            EnrollmentStatusFilter.CANCELLED ->
                enrollment.state == com.ash.simpledataentry.domain.model.ProgramInstanceState.CANCELLED
        }

        val orgUnitMatches = filterState.orgUnitId?.let { it == enrollment.organisationUnit.id } ?: true

        val periodMatches = periodRange?.let { range ->
            enrollment.enrollmentDate?.let { date ->
                !date.before(range.first) && !date.after(range.second)
            } ?: false
        } ?: true

        searchMatches && syncMatches && statusMatches && orgUnitMatches && periodMatches
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackerEnrollmentFilterDialog(
    currentFilter: TrackerEnrollmentFilterState,
    orgUnits: List<OrganisationUnit>,
    onFilterChanged: (TrackerEnrollmentFilterState) -> Unit,
    onOpenOrgUnitPicker: () -> Unit,
    onClearFilters: () -> Unit,
    onDismiss: () -> Unit
) {
    var filterState by remember { mutableStateOf(currentFilter) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    val orgUnitEnabled = orgUnits.isNotEmpty()
    val relativePeriods = remember {
        listOf(
            RelativePeriod.TODAY,
            RelativePeriod.YESTERDAY,
            RelativePeriod.LAST_7_DAYS,
            RelativePeriod.LAST_30_DAYS,
            RelativePeriod.THIS_MONTH,
            RelativePeriod.LAST_3_MONTHS,
            RelativePeriod.THIS_YEAR,
            RelativePeriod.LAST_YEAR
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter Enrollments") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Period",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Column(modifier = Modifier.selectableGroup()) {
                            PeriodFilterType.values().forEach { periodType ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = filterState.periodType == periodType,
                                            onClick = {
                                                filterState = filterState.copy(
                                                    periodType = periodType,
                                                    relativePeriod = if (periodType == PeriodFilterType.RELATIVE) {
                                                        filterState.relativePeriod ?: relativePeriods.first()
                                                    } else {
                                                        null
                                                    },
                                                    customFromDate = if (periodType == PeriodFilterType.CUSTOM_RANGE) {
                                                        filterState.customFromDate
                                                    } else {
                                                        null
                                                    },
                                                    customToDate = if (periodType == PeriodFilterType.CUSTOM_RANGE) {
                                                        filterState.customToDate
                                                    } else {
                                                        null
                                                    }
                                                )
                                            },
                                            role = Role.RadioButton
                                        )
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = filterState.periodType == periodType,
                                        onClick = null
                                    )
                                    Text(
                                        text = when (periodType) {
                                            PeriodFilterType.ALL -> "All periods"
                                            PeriodFilterType.RELATIVE -> "Relative period"
                                            PeriodFilterType.CUSTOM_RANGE -> "Custom range"
                                        },
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }

                        if (filterState.periodType == PeriodFilterType.RELATIVE) {
                            var expanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = !expanded }
                            ) {
                                OutlinedTextField(
                                    value = filterState.relativePeriod?.let { relativePeriodLabel(it) }
                                        ?: "Select period",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Relative period") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                                )

                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    relativePeriods.forEach { period ->
                                        DropdownMenuItem(
                                            text = { Text(relativePeriodLabel(period)) },
                                            onClick = {
                                                filterState = filterState.copy(relativePeriod = period)
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (filterState.periodType == PeriodFilterType.CUSTOM_RANGE) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = filterState.customFromDate?.let { DatePickerUtils.formatDateForDisplay(it) }
                                        ?: "Start date",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("From") },
                                    trailingIcon = {
                                        IconButton(onClick = { showDateRangePicker = true }) {
                                            Icon(
                                                imageVector = Icons.Default.DateRange,
                                                contentDescription = null
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = filterState.customToDate?.let { DatePickerUtils.formatDateForDisplay(it) }
                                        ?: "End date",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("To") },
                                    trailingIcon = {
                                        IconButton(onClick = { showDateRangePicker = true }) {
                                            Icon(
                                                imageVector = Icons.Default.DateRange,
                                                contentDescription = null
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Organisation Unit",
                            style = MaterialTheme.typography.titleMedium
                        )
                        OutlinedTextField(
                            value = filterState.orgUnitName ?: "All organisation units",
                            onValueChange = {},
                            readOnly = true,
                            enabled = orgUnitEnabled,
                            label = { Text("Organisation Unit") },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = orgUnitEnabled) { onOpenOrgUnitPicker() }
                        )
                        if (!orgUnitEnabled) {
                            Text(
                                text = "No organisation units available.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (filterState.orgUnitId != null) {
                            TextButton(onClick = {
                                filterState = filterState.copy(orgUnitId = null, orgUnitName = null)
                            }) {
                                Text("Clear organisation unit")
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Sync Status",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Column(modifier = Modifier.selectableGroup()) {
                            SyncStatus.values().forEach { status ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = filterState.syncStatus == status,
                                            onClick = { filterState = filterState.copy(syncStatus = status) },
                                            role = Role.RadioButton
                                        )
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = filterState.syncStatus == status, onClick = null)
                                    Text(text = status.displayName, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Enrollment Status",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Column(modifier = Modifier.selectableGroup()) {
                            EnrollmentStatusFilter.values().forEach { status ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = filterState.enrollmentStatus == status,
                                            onClick = { filterState = filterState.copy(enrollmentStatus = status) },
                                            role = Role.RadioButton
                                        )
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = filterState.enrollmentStatus == status, onClick = null)
                                    Text(text = status.displayName, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        onClearFilters()
                        onDismiss()
                    }
                ) {
                    Text("Clear")
                }
                Button(
                    onClick = {
                        onFilterChanged(filterState)
                        onDismiss()
                    }
                ) {
                    Text("Apply")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showDateRangePicker) {
        DateRangePickerDialog(
            initialStartDate = filterState.customFromDate,
            initialEndDate = filterState.customToDate,
            onDateRangeSelected = { startDate, endDate ->
                filterState = filterState.copy(
                    periodType = PeriodFilterType.CUSTOM_RANGE,
                    customFromDate = startDate,
                    customToDate = endDate,
                    relativePeriod = null
                )
                showDateRangePicker = false
            },
            onDismissRequest = { showDateRangePicker = false }
        )
    }
}

@Composable
private fun EnrollmentLineList(
    enrollments: List<com.ash.simpledataentry.domain.model.ProgramInstance.TrackerEnrollment>,
    onEnrollmentClick: (com.ash.simpledataentry.domain.model.ProgramInstance.TrackerEnrollment) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Name",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.35f)
                )
                Text(
                    text = "Org Unit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.25f)
                )
                Text(
                    text = "Status",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.2f)
                )
                Text(
                    text = "Sync",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.2f)
                )
            }
            Divider()
        }

        items(items = enrollments, key = { it.id }) { enrollment ->
            val displayName = resolveTrackedEntityName(enrollment.attributes, enrollment.trackedEntityInstance)
            val completionLabel = when (enrollment.state) {
                com.ash.simpledataentry.domain.model.ProgramInstanceState.COMPLETED -> "Completed"
                com.ash.simpledataentry.domain.model.ProgramInstanceState.CANCELLED -> "Cancelled"
                else -> "Active"
            }
            val syncLabel = if (enrollment.syncStatus == SyncStatus.SYNCED) "Synced" else "Not synced"

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEnrollmentClick(enrollment) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(0.35f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = enrollment.organisationUnit.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.25f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(modifier = Modifier.weight(0.2f)) {
                    StatusChip(
                        text = completionLabel,
                        containerColor = when (enrollment.state) {
                            com.ash.simpledataentry.domain.model.ProgramInstanceState.COMPLETED -> StatusCompletedLight
                            com.ash.simpledataentry.domain.model.ProgramInstanceState.CANCELLED -> MaterialTheme.colorScheme.errorContainer
                            else -> StatusDraftLight
                        },
                        contentColor = when (enrollment.state) {
                            com.ash.simpledataentry.domain.model.ProgramInstanceState.COMPLETED -> StatusCompleted
                            com.ash.simpledataentry.domain.model.ProgramInstanceState.CANCELLED -> MaterialTheme.colorScheme.onErrorContainer
                            else -> StatusDraft
                        }
                    )
                }
                Box(modifier = Modifier.weight(0.2f)) {
                    StatusChip(
                        text = syncLabel,
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
            Divider()
        }
    }
}

private fun relativePeriodLabel(period: RelativePeriod): String {
    return when (period) {
        RelativePeriod.TODAY -> "Today"
        RelativePeriod.YESTERDAY -> "Yesterday"
        RelativePeriod.LAST_3_DAYS -> "Last 3 days"
        RelativePeriod.LAST_7_DAYS -> "Last 7 days"
        RelativePeriod.LAST_14_DAYS -> "Last 14 days"
        RelativePeriod.LAST_30_DAYS -> "Last 30 days"
        RelativePeriod.LAST_60_DAYS -> "Last 60 days"
        RelativePeriod.LAST_90_DAYS -> "Last 90 days"
        RelativePeriod.LAST_180_DAYS -> "Last 180 days"
        RelativePeriod.THIS_WEEK -> "This week"
        RelativePeriod.LAST_WEEK -> "Last week"
        RelativePeriod.LAST_4_WEEKS -> "Last 4 weeks"
        RelativePeriod.LAST_12_WEEKS -> "Last 12 weeks"
        RelativePeriod.LAST_52_WEEKS -> "Last 52 weeks"
        RelativePeriod.THIS_MONTH -> "This month"
        RelativePeriod.LAST_MONTH -> "Last month"
        RelativePeriod.LAST_3_MONTHS -> "Last 3 months"
        RelativePeriod.LAST_6_MONTHS -> "Last 6 months"
        RelativePeriod.LAST_12_MONTHS -> "Last 12 months"
        RelativePeriod.THIS_BIMONTH -> "This bi-month"
        RelativePeriod.LAST_BIMONTH -> "Last bi-month"
        RelativePeriod.LAST_6_BIMONTHS -> "Last 6 bi-months"
        RelativePeriod.THIS_QUARTER -> "This quarter"
        RelativePeriod.LAST_QUARTER -> "Last quarter"
        RelativePeriod.LAST_4_QUARTERS -> "Last 4 quarters"
        RelativePeriod.THIS_SIX_MONTH -> "This six-month"
        RelativePeriod.LAST_SIX_MONTH -> "Last six-month"
        RelativePeriod.LAST_2_SIXMONTHS -> "Last 2 six-months"
        RelativePeriod.THIS_YEAR -> "This year"
        RelativePeriod.LAST_YEAR -> "Last year"
        RelativePeriod.LAST_5_YEARS -> "Last 5 years"
        RelativePeriod.LAST_10_YEARS -> "Last 10 years"
        RelativePeriod.THIS_FINANCIAL_YEAR -> "This financial year"
        RelativePeriod.LAST_FINANCIAL_YEAR -> "Last financial year"
        RelativePeriod.LAST_5_FINANCIAL_YEARS -> "Last 5 financial years"
        RelativePeriod.LAST_10_FINANCIAL_YEARS -> "Last 10 financial years"
        RelativePeriod.WEEKS_THIS_YEAR -> "Weeks this year"
        RelativePeriod.MONTHS_THIS_YEAR -> "Months this year"
        RelativePeriod.BIMONTHS_THIS_YEAR -> "Bi-months this year"
        RelativePeriod.QUARTERS_THIS_YEAR -> "Quarters this year"
        RelativePeriod.MONTHS_LAST_YEAR -> "Months last year"
        RelativePeriod.QUARTERS_LAST_YEAR -> "Quarters last year"
        RelativePeriod.THIS_BIWEEK -> "This bi-week"
        RelativePeriod.LAST_BIWEEK -> "Last bi-week"
        RelativePeriod.LAST_4_BIWEEKS -> "Last 4 bi-weeks"
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
