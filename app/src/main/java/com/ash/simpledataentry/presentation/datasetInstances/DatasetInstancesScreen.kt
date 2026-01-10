package com.ash.simpledataentry.presentation.datasetInstances

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ash.simpledataentry.domain.model.DatasetInstanceFilterState
import com.ash.simpledataentry.domain.model.InstanceSortBy
import com.ash.simpledataentry.domain.model.SortOrder
import com.ash.simpledataentry.domain.model.SyncStatus
import com.ash.simpledataentry.domain.model.CompletionStatus
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import com.ash.simpledataentry.presentation.core.AdaptiveLoadingOverlay
import com.ash.simpledataentry.presentation.core.BaseScreen
import com.ash.simpledataentry.presentation.core.ErrorBanner
import com.ash.simpledataentry.presentation.core.ShimmerLoadingList
import com.ash.simpledataentry.presentation.core.UiError
import com.ash.simpledataentry.presentation.core.UiState
import com.ash.simpledataentry.presentation.core.LoadingOperation
import com.ash.simpledataentry.ui.theme.DHIS2BlueDeep
import com.ash.simpledataentry.ui.theme.StatusCompleted
import com.ash.simpledataentry.ui.theme.StatusCompletedLight
import com.ash.simpledataentry.ui.theme.StatusDraft
import com.ash.simpledataentry.ui.theme.StatusDraftLight
import com.ash.simpledataentry.ui.theme.StatusSynced
import com.ash.simpledataentry.ui.theme.StatusSyncedLight
import org.hisp.dhis.mobile.ui.designsystem.theme.TextColor
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Edit
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatasetInstancesPullDownFilter(
    currentFilter: DatasetInstanceFilterState,
    onApplyFilter: (DatasetInstanceFilterState) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        var syncStatus by remember { mutableStateOf(currentFilter.syncStatus) }
        var completionStatus by remember { mutableStateOf(currentFilter.completionStatus) }
        var sortBy by remember { mutableStateOf(currentFilter.sortBy) }
        var sortOrder by remember { mutableStateOf(currentFilter.sortOrder) }

        var showSyncDropdown by remember { mutableStateOf(false) }
        var showCompletionDropdown by remember { mutableStateOf(false) }
        var showSortByDropdown by remember { mutableStateOf(false) }
        var showSortOrderDropdown by remember { mutableStateOf(false) }

        // Sort Controls Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Sort By
            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = sortBy.displayName,
                    onValueChange = { },
                    label = { Text("Sort By") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { showSortByDropdown = !showSortByDropdown }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }
                )

                DropdownMenu(
                    expanded = showSortByDropdown,
                    onDismissRequest = { showSortByDropdown = false }
                ) {
                    InstanceSortBy.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(sort.displayName) },
                            onClick = {
                                sortBy = sort
                                showSortByDropdown = false
                                onApplyFilter(
                                    currentFilter.copy(
                                        sortBy = sortBy,
                                        sortOrder = sortOrder
                                    )
                                )
                            }
                        )
                    }
                }
            }

            // Sort Order
            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = sortOrder.displayName,
                    onValueChange = { },
                    label = { Text("Order") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { showSortOrderDropdown = !showSortOrderDropdown }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }
                )

                DropdownMenu(
                    expanded = showSortOrderDropdown,
                    onDismissRequest = { showSortOrderDropdown = false }
                ) {
                    SortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { Text(order.displayName) },
                            onClick = {
                                sortOrder = order
                                showSortOrderDropdown = false
                                onApplyFilter(
                                    currentFilter.copy(
                                        sortBy = sortBy,
                                        sortOrder = sortOrder
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }

        // Filter Controls Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Sync Status Filter
            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = syncStatus.displayName,
                    onValueChange = { },
                    label = { Text("Sync Status") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { showSyncDropdown = !showSyncDropdown }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }
                )

                DropdownMenu(
                    expanded = showSyncDropdown,
                    onDismissRequest = { showSyncDropdown = false }
                ) {
                    SyncStatus.entries.forEach { status ->
                        DropdownMenuItem(
                            text = { Text(status.displayName) },
                            onClick = {
                                syncStatus = status
                                showSyncDropdown = false
                                onApplyFilter(
                                    currentFilter.copy(
                                        syncStatus = syncStatus,
                                        sortBy = sortBy,
                                        sortOrder = sortOrder
                                    )
                                )
                            }
                        )
                    }
                }
            }

            // Completion Status Filter
            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = completionStatus.displayName,
                    onValueChange = { },
                    label = { Text("Completion") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { showCompletionDropdown = !showCompletionDropdown }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }
                )

                DropdownMenu(
                    expanded = showCompletionDropdown,
                    onDismissRequest = { showCompletionDropdown = false }
                ) {
                    CompletionStatus.entries.forEach { status ->
                        DropdownMenuItem(
                            text = { Text(status.displayName) },
                            onClick = {
                                completionStatus = status
                                showCompletionDropdown = false
                                onApplyFilter(
                                    currentFilter.copy(
                                        completionStatus = completionStatus,
                                        sortBy = sortBy,
                                        sortOrder = sortOrder
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }

        // Clear button
        if (currentFilter.searchQuery.isNotBlank() || syncStatus != SyncStatus.ALL || completionStatus != CompletionStatus.ALL ||
            sortBy != InstanceSortBy.PERIOD || sortOrder != SortOrder.DESCENDING) {
            OutlinedButton(
                onClick = {
                    syncStatus = SyncStatus.ALL
                    completionStatus = CompletionStatus.ALL
                    sortBy = InstanceSortBy.PERIOD
                    sortOrder = SortOrder.DESCENDING
                    onApplyFilter(DatasetInstanceFilterState())
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Clear All")
            }
        }
    }
}

@Composable
private fun DatasetInstancesContent(
    filteredInstances: List<com.ash.simpledataentry.domain.model.ProgramInstance>,
    attributeOptionCombos: List<Pair<String, String>>,
    instancesWithDrafts: Set<String>,
    bulkMode: Boolean,
    selectedInstances: Set<String>,
    isBlockingOperation: Boolean,
    hasBlockingOverlay: Boolean,
    uiState: UiState<DatasetInstancesData>,
    navController: NavController,
    datasetId: String,
    datasetName: String,
    viewModel: DatasetInstancesViewModel
) {
    val listContent: @Composable () -> Unit = {
        if (filteredInstances.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "No entries found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Try syncing or adjust your filters.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { viewModel.syncDatasetInstances() },
                        enabled = !isBlockingOperation
                    ) {
                        Text("Sync now")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredInstances) { instance ->
                val formattedDate = try {
                    instance.lastUpdated?.let { date ->
                        val outputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
                        outputFormat.format(date)
                    } ?: "N/A"
                } catch (e: Exception) {
                    Log.e("DatasetInstancesScreen", "Error parsing date: ", e)
                    "N/A"
                }
                val periodText = when (instance) {
                    is com.ash.simpledataentry.domain.model.ProgramInstance.DatasetInstance ->
                        instance.period.toString().replace("Period(id=", "").replace(")", "")
                    is com.ash.simpledataentry.domain.model.ProgramInstance.TrackerEnrollment ->
                        java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(instance.enrollmentDate)
                    is com.ash.simpledataentry.domain.model.ProgramInstance.EventInstance ->
                        instance.eventDate?.let { java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(it) } ?: "No date"
                }

                val attrComboName = when (instance) {
                    is com.ash.simpledataentry.domain.model.ProgramInstance.DatasetInstance -> {
                        attributeOptionCombos.find { it.first == instance.attributeOptionCombo }?.second
                            ?: instance.attributeOptionCombo
                    }
                    else -> ""
                }
                val showAttrCombo = when (instance) {
                    is com.ash.simpledataentry.domain.model.ProgramInstance.DatasetInstance ->
                        !attrComboName.equals("default", ignoreCase = true)
                    else -> false
                }
                val isComplete = instance.state == com.ash.simpledataentry.domain.model.ProgramInstanceState.COMPLETED
                val isSynced = instance.syncStatus == SyncStatus.SYNCED

                val instanceKey = when (instance) {
                    is com.ash.simpledataentry.domain.model.ProgramInstance.DatasetInstance ->
                        "${instance.programId}|${instance.period.id}|${instance.organisationUnit.id}|${instance.attributeOptionCombo}"
                    is com.ash.simpledataentry.domain.model.ProgramInstance.TrackerEnrollment ->
                        "${instance.programId}|${instance.trackedEntityInstance}|${instance.organisationUnit.id}"
                    is com.ash.simpledataentry.domain.model.ProgramInstance.EventInstance ->
                        "${instance.programId}|${instance.programStage}|${instance.organisationUnit.id}|${instance.eventDate?.time ?: 0}"
                }
                val hasLocalChanges = instancesWithDrafts.contains(instanceKey)

                val dateForTopRight = instance.lastUpdated?.let { formattedDate }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (bulkMode) {
                        Checkbox(
                            checked = selectedInstances.contains(instance.id) || isComplete,
                            onCheckedChange = {
                                if (!isBlockingOperation && !isComplete) {
                                    viewModel.toggleInstanceSelection(instance.id)
                                }
                            },
                            enabled = !isBlockingOperation && !isComplete,
                            colors = CheckboxDefaults.colors(
                                checkedColor = if (isComplete) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.primary,
                                uncheckedColor = if (isComplete) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 2.dp,
                                pressedElevation = 4.dp
                            ),
                            onClick = {
                                if (!isBlockingOperation && !bulkMode) {
                                    val encodedDatasetId = URLEncoder.encode(datasetId, "UTF-8")
                                    val encodedDatasetName = URLEncoder.encode(datasetName, "UTF-8")
                                    when (instance) {
                                        is com.ash.simpledataentry.domain.model.ProgramInstance.DatasetInstance -> {
                                            navController.navigate("EditEntry/$encodedDatasetId/${instance.period.id}/${instance.organisationUnit.id}/${instance.attributeOptionCombo}/$encodedDatasetName") {
                                                launchSingleTop = true
                                                popUpTo("DatasetInstances/{datasetId}/{datasetName}") {
                                                    saveState = true
                                                }
                                            }
                                        }
                                        is com.ash.simpledataentry.domain.model.ProgramInstance.TrackerEnrollment -> {
                                            navController.navigate("TrackerDashboard/${instance.id}/$encodedDatasetId/$encodedDatasetName") {
                                                launchSingleTop = true
                                                popUpTo("DatasetInstances/{datasetId}/{datasetName}") {
                                                    saveState = true
                                                }
                                            }
                                        }
                                        is com.ash.simpledataentry.domain.model.ProgramInstance.EventInstance -> {
                                            navController.navigate("EditStandaloneEvent/$encodedDatasetId/$encodedDatasetName/${instance.id}") {
                                                launchSingleTop = true
                                                popUpTo("DatasetInstances/{datasetId}/{datasetName}") {
                                                    saveState = true
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${instance.organisationUnit.name} • $periodText",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (dateForTopRight != null) {
                                            Text(
                                                text = dateForTopRight,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (hasLocalChanges && !isComplete) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp),
                                        color = StatusDraft,
                                        trackColor = StatusDraftLight
                                    )
                                }

                                if (showAttrCombo || hasLocalChanges || isComplete || isSynced) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (showAttrCombo) {
                                            Text(
                                                text = attrComboName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f)
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }

                                        if ((hasLocalChanges || isComplete || isSynced) && !bulkMode) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (hasLocalChanges) {
                                                    StatusChip(
                                                        label = "Unsynced",
                                                        containerColor = StatusDraftLight,
                                                        contentColor = StatusDraft
                                                    )
                                                }

                                                if (isComplete) {
                                                    StatusChip(
                                                        label = "Complete",
                                                        containerColor = StatusCompletedLight,
                                                        contentColor = StatusCompleted
                                                    )
                                                }

                                                if (!hasLocalChanges && !isComplete && isSynced) {
                                                    StatusChip(
                                                        label = "Up to date",
                                                        containerColor = StatusSyncedLight,
                                                        contentColor = StatusSynced
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
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
            listContent()
        }
    } else {
        listContent()
    }
}

@Composable
private fun StatusChip(
    label: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@SuppressLint("SuspiciousIndentation")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatasetInstancesScreen(
    navController: NavController,
    datasetId: String,
    datasetName: String,
    viewModel: DatasetInstancesViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsState()
    val bulkMode by viewModel.bulkCompletionMode.collectAsState()
    val selectedInstances by viewModel.selectedInstances.collectAsState()
    val filterState by viewModel.filterState.collectAsState()
    var bulkActionMessage by remember { mutableStateOf<String?>(null) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showSyncDialog by remember { mutableStateOf(false) }
    var showFilterSection by remember { mutableStateOf(false) }

    // CRITICAL FIX: Detect program type and call appropriate method
    LaunchedEffect(datasetId) {
        // First, we need to determine what type of program this is
        // We'll let the ViewModel detect the program type based on the ID
        viewModel.initializeWithProgramId(datasetId)
    }

    // Refresh data when the screen is resumed (e.g., coming back from EditEntryScreen)
    LaunchedEffect(Unit) {
        viewModel.refreshData()
    }

    var lastSuccessData by remember { mutableStateOf<DatasetInstancesData?>(null) }
    val currentUiState = uiState
    val currentData = when (currentUiState) {
        is UiState.Success -> currentUiState.data.also { lastSuccessData = it }
        is UiState.Error -> currentUiState.previousData?.also { lastSuccessData = it } ?: lastSuccessData
        is UiState.Loading -> lastSuccessData
    }
    val showInitialShimmer = currentUiState is UiState.Loading &&
        currentUiState.operation is LoadingOperation.Initial &&
        currentData == null
    val hasBlockingOverlay = currentUiState is UiState.Loading &&
        currentUiState.operation !is LoadingOperation.Initial &&
        currentData != null
    val isBlockingOperation = hasBlockingOverlay
    val isSyncInProgress = currentUiState is UiState.Loading &&
        currentUiState.operation is LoadingOperation.Syncing

    val subtitle = currentData?.let { data ->
        when (data.programType) {
            com.ash.simpledataentry.domain.model.ProgramType.DATASET -> "Data entries"
            com.ash.simpledataentry.domain.model.ProgramType.TRACKER -> "Enrollments"
            com.ash.simpledataentry.domain.model.ProgramType.EVENT -> "Events"
            else -> null
        }
    }

    LaunchedEffect(currentUiState) {
        if (currentUiState is UiState.Error) {
            val message = when (val error = currentUiState.error) {
                is UiError.Network -> error.message
                is UiError.Server -> error.message
                is UiError.Validation -> error.message
                is UiError.Authentication -> error.message
                is UiError.Local -> error.message
            }
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }

    LaunchedEffect(currentData?.successMessage) {
        currentData?.successMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
        }
    }

    BaseScreen(
        title = datasetName,
        subtitle = subtitle,
        navController = navController,
        syncStatusController = viewModel.syncController,
        actions = {
            IconButton(
                onClick = {
                    if (!isBlockingOperation && currentData != null) {
                        showSyncDialog = true
                    }
                },
                enabled = !isBlockingOperation && !bulkMode && currentData != null
            ) {
                if (isSyncInProgress) {
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
        floatingActionButton = if (!bulkMode && currentData != null) {
            {
                FloatingActionButton(
                    onClick = {
                        if (isBlockingOperation) {
                            return@FloatingActionButton
                        }
                        val encodedDatasetName = URLEncoder.encode(datasetName, "UTF-8")
                        when (currentData.programType) {
                            com.ash.simpledataentry.domain.model.ProgramType.DATASET -> {
                                navController.navigate("CreateDataEntry/$datasetId/$encodedDatasetName") {
                                    launchSingleTop = true
                                    popUpTo("DatasetInstances/{datasetId}/{datasetName}") {
                                        saveState = true
                                    }
                                }
                            }
                            com.ash.simpledataentry.domain.model.ProgramType.TRACKER -> {
                                navController.navigate("CreateEnrollment/$datasetId/$encodedDatasetName") {
                                    launchSingleTop = true
                                    popUpTo("DatasetInstances/{datasetId}/{datasetName}") {
                                        saveState = true
                                    }
                                }
                            }
                            com.ash.simpledataentry.domain.model.ProgramType.EVENT -> {
                                navController.navigate("CreateEvent/$datasetId/$encodedDatasetName") {
                                    launchSingleTop = true
                                    popUpTo("DatasetInstances/{datasetId}/{datasetName}") {
                                        saveState = true
                                    }
                                }
                            }
                            else -> {
                                // Default to dataset creation for unknown types
                                navController.navigate("CreateDataEntry/$datasetId/$encodedDatasetName") {
                                    launchSingleTop = true
                                    popUpTo("DatasetInstances/{datasetId}/{datasetName}") {
                                        saveState = true
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = when (currentData.programType) {
                            com.ash.simpledataentry.domain.model.ProgramType.TRACKER -> "Create Enrollment"
                            com.ash.simpledataentry.domain.model.ProgramType.EVENT -> "Create Event"
                            else -> "Create Data Entry"
                        }
                    )
                }
            }
        } else null
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = filterState.searchQuery,
                        onValueChange = { query ->
                            viewModel.updateFilterState(filterState.copy(searchQuery = query))
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("Search instances...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null
                            )
                        }
                    )

                    IconButton(
                        onClick = { showFilterSection = !showFilterSection },
                        enabled = !isBlockingOperation && !bulkMode && currentData != null
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter",
                            tint = if (filterState.hasActiveFilters() || showFilterSection) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    IconButton(
                        onClick = { viewModel.toggleBulkCompletionMode() },
                        enabled = !isBlockingOperation && currentData != null
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = if (bulkMode) "Exit Bulk Complete" else "Bulk Complete",
                            tint = if (bulkMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Pull-down filter section
                AnimatedVisibility(
                    visible = showFilterSection,
                    enter = slideInVertically(
                        initialOffsetY = { -it },
                        animationSpec = androidx.compose.animation.core.tween(200)
                    ),
                    exit = slideOutVertically(
                        targetOffsetY = { -it },
                        animationSpec = androidx.compose.animation.core.tween(150)
                    )
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = DHIS2BlueDeep
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        shape = RoundedCornerShape(
                            topStart = 0.dp,
                            topEnd = 0.dp,
                            bottomStart = 8.dp,
                            bottomEnd = 8.dp
                        )
                    ) {
                        DatasetInstancesPullDownFilter(
                            currentFilter = filterState,
                            onApplyFilter = { newFilter: DatasetInstanceFilterState ->
                                viewModel.updateFilterState(newFilter)
                            }
                        )
                    }
                }

                when {
                    showInitialShimmer -> {
                        ShimmerLoadingList(
                            itemCount = 8,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    currentData != null -> {
                        DatasetInstancesContent(
                            filteredInstances = currentData.filteredInstances,
                            attributeOptionCombos = currentData.attributeOptionCombos,
                            instancesWithDrafts = currentData.instancesWithDrafts,
                            bulkMode = bulkMode,
                            selectedInstances = selectedInstances,
                            isBlockingOperation = isBlockingOperation,
                            hasBlockingOverlay = hasBlockingOverlay,
                            uiState = currentUiState,
                            navController = navController,
                            datasetId = datasetId,
                            datasetName = datasetName,
                            viewModel = viewModel
                        )
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No dataset instances available.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (currentUiState is UiState.Error) {
                ErrorBanner(
                    error = currentUiState.error,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                snackbar = { data ->
                    Snackbar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = Color.White
                    ) {
                        Text(data.visuals.message)
                    }
                }
            )
            if (bulkActionMessage != null) {
                LaunchedEffect(bulkActionMessage) {
                    snackbarHostState.showSnackbar(bulkActionMessage!!)
                    bulkActionMessage = null
                }
            }

            // Bulk action buttons positioned at bottom of screen
            if (bulkMode) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .background(
                            MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                        )
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = {
                            viewModel.bulkCompleteSelectedInstances { success, error ->
                                bulkActionMessage =
                                    if (success) "Selected instances marked as complete." else (error
                                        ?: "Failed to complete selected instances.")
                            }
                        },
                        enabled = selectedInstances.isNotEmpty() && !isBlockingOperation,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Complete Selected")
                    }
                    Button(
                        onClick = { viewModel.clearBulkSelection() },
                        enabled = !isBlockingOperation,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text("Cancel")
                    }
                }
            }

            // Filter Dialog - only show when data is loaded
            if (showFilterDialog && currentData != null) {
                DatasetInstanceFilterDialog(
                    currentFilter = filterState,
                    attributeOptionCombos = currentData.attributeOptionCombos,
                    dataset = currentData.dataset,
                    onFilterChanged = { newFilter ->
                        viewModel.updateFilterState(newFilter)
                    },
                    onClearFilters = {
                        viewModel.clearFilters()
                    },
                    onDismiss = { showFilterDialog = false }
                )
            }

            // Sync Dialog
            if (showSyncDialog && currentData != null) {
                SyncConfirmationDialog(
                    syncOptions = SyncOptions(
                        uploadLocalData = currentData.localInstanceCount > 0,
                        localInstanceCount = currentData.localInstanceCount
                    ),
                    onConfirm = { uploadFirst ->
                        viewModel.syncDatasetInstances(uploadFirst)
                        showSyncDialog = false
                    },
                    onDismiss = { showSyncDialog = false }
                )
            }
        }
    }
}

private fun parseDhis2PeriodToDate(periodId: String): Date? {
    return try {
        when {
            // Yearly: 2023
            Regex("^\\d{4}$").matches(periodId) -> {
                SimpleDateFormat("yyyy", Locale.ENGLISH).parse(periodId)
            }
            // Monthly: 202306
            Regex("^\\d{6}$").matches(periodId) -> {
                SimpleDateFormat("yyyyMM", Locale.ENGLISH).parse(periodId)
            }
            // Daily: 2023-06-01
            Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(periodId) -> {
                SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(periodId)
            }
            // Weekly: 2023W23
            Regex("^\\d{4}W\\d{1,2}$").matches(periodId) -> {
                val year = periodId.substring(0, 4).toInt()
                val week = periodId.substring(5).toInt()
                val cal = Calendar.getInstance(Locale.ENGLISH)
                cal.clear()
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.WEEK_OF_YEAR, week)
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                cal.time
            }
            // Quarterly: 2023Q2
            Regex("^\\d{4}Q[1-4]$").matches(periodId) -> {
                val year = periodId.substring(0, 4).toInt()
                val quarter = periodId.substring(5).toInt()
                val month = (quarter - 1) * 3
                val cal = Calendar.getInstance(Locale.ENGLISH)
                cal.clear()
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, month)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.time
            }
            // Six-monthly: 2023S1 or 2023S2
            Regex("^\\d{4}S[1-2]$").matches(periodId) -> {
                val year = periodId.substring(0, 4).toInt()
                val semester = periodId.substring(5).toInt()
                val month = if (semester == 1) 0 else 6
                val cal = Calendar.getInstance(Locale.ENGLISH)
                cal.clear()
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, month)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.time
            }

            else -> null
        }
    } catch (e: Exception) {
        null
    }
}
