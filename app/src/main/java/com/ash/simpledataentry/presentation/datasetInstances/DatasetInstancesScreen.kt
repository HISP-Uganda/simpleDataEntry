package com.ash.simpledataentry.presentation.datasetInstances

import android.annotation.SuppressLint
import android.text.format.DateFormat
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ash.simpledataentry.domain.model.DatasetInstance
import com.ash.simpledataentry.domain.model.DatasetInstanceFilterState
import com.ash.simpledataentry.domain.model.InstanceSortBy
import com.ash.simpledataentry.domain.model.SortOrder
import com.ash.simpledataentry.domain.model.SyncStatus
import com.ash.simpledataentry.domain.model.CompletionStatus
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import com.ash.simpledataentry.domain.model.DatasetInstanceState
import com.ash.simpledataentry.presentation.core.BaseScreen
import com.ash.simpledataentry.presentation.core.FullScreenLoader
import com.ash.simpledataentry.presentation.core.OverlayLoader
import com.ash.simpledataentry.presentation.core.DetailedSyncOverlay
import com.ash.simpledataentry.ui.theme.DHIS2BlueDeep
import org.hisp.dhis.android.core.dataset.DataSetInstance
import org.hisp.dhis.mobile.ui.designsystem.component.AdditionalInfoItem
import org.hisp.dhis.mobile.ui.designsystem.component.ListCard
import org.hisp.dhis.mobile.ui.designsystem.component.ListCardDescriptionModel
import org.hisp.dhis.mobile.ui.designsystem.component.ListCardTitleModel
import org.hisp.dhis.mobile.ui.designsystem.component.state.rememberAdditionalInfoColumnState
import org.hisp.dhis.mobile.ui.designsystem.component.state.rememberListCardState
import org.hisp.dhis.mobile.ui.designsystem.theme.DHIS2Theme
import org.hisp.dhis.mobile.ui.designsystem.theme.TextColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.coroutineScope
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape

// Status information for better UI presentation
data class StatusInfo(
    val text: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: androidx.compose.ui.graphics.Color,
    val backgroundColor: androidx.compose.ui.graphics.Color
)

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
        var searchQuery by remember { mutableStateOf(currentFilter.searchQuery) }
        var syncStatus by remember { mutableStateOf(currentFilter.syncStatus) }
        var completionStatus by remember { mutableStateOf(currentFilter.completionStatus) }
        var sortBy by remember { mutableStateOf(currentFilter.sortBy) }
        var sortOrder by remember { mutableStateOf(currentFilter.sortOrder) }

        var showSyncDropdown by remember { mutableStateOf(false) }
        var showCompletionDropdown by remember { mutableStateOf(false) }
        var showSortByDropdown by remember { mutableStateOf(false) }
        var showSortOrderDropdown by remember { mutableStateOf(false) }

        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                onApplyFilter(
                    currentFilter.copy(
                        searchQuery = searchQuery,
                        sortBy = sortBy,
                        sortOrder = sortOrder
                    )
                )
            },
            label = { Text("Search dataset instances") },
            placeholder = { Text("Enter org unit, period...") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                Icon(Icons.Default.FilterList, contentDescription = null)
            }
        )

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
        if (searchQuery.isNotBlank() || syncStatus != SyncStatus.ALL || completionStatus != CompletionStatus.ALL ||
            sortBy != InstanceSortBy.PERIOD || sortOrder != SortOrder.DESCENDING) {
            OutlinedButton(
                onClick = {
                    searchQuery = ""
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

@SuppressLint("SuspiciousIndentation")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatasetInstancesScreen(
    navController: NavController,
    datasetId: String,
    datasetName: String,
    viewModel: DatasetInstancesViewModel = hiltViewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val state by viewModel.state.collectAsState()
    val bulkMode by viewModel.bulkCompletionMode.collectAsState()
    val selectedInstances by viewModel.selectedInstances.collectAsState()
    val filterState by viewModel.filterState.collectAsState()
    var bulkActionMessage by remember { mutableStateOf<String?>(null) }
    var bulkActionSuccess by remember { mutableStateOf<Boolean?>(null) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showSyncDialog by remember { mutableStateOf(false) }
    var showFilterSection by remember { mutableStateOf(false) }

    LaunchedEffect(datasetId) {
        viewModel.setDatasetId(datasetId)
    }

    // Refresh data when the screen is resumed (e.g., coming back from EditEntryScreen)
    LaunchedEffect(Unit) {
        viewModel.refreshData()
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
        }
    }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
        }
    }

    BaseScreen(
        title = datasetName,
        navController = navController,
        actions = {
            // Keep icons visible during loading - just disable them
            IconButton(
                onClick = { showFilterSection = !showFilterSection },
                enabled = !state.isSyncing && !bulkMode
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "Filter",
                    tint = if (filterState.hasActiveFilters() || showFilterSection) Color.White else TextColor.OnSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(
                onClick = {
                    if (!state.isSyncing) {
                        showSyncDialog = true
                    }
                },
                enabled = !state.isLoading && !state.isSyncing && !bulkMode
            ) {
                if (state.isSyncing) {
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
            IconButton(
                onClick = { viewModel.toggleBulkCompletionMode() },
                enabled = !state.isLoading && !state.isSyncing
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = if (bulkMode) "Exit Bulk Complete" else "Bulk Complete",
                    tint = if (bulkMode) MaterialTheme.colorScheme.primary else TextColor.OnSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        floatingActionButton = if (!bulkMode) {
            {
                FloatingActionButton(
                    onClick = {
                        val encodedDatasetName = URLEncoder.encode(datasetName, "UTF-8")
                        navController.navigate("CreateDataEntry/$datasetId/$encodedDatasetName") {
                            launchSingleTop = true
                            popUpTo("DatasetInstances/{datasetId}/{datasetName}") {
                                saveState = true
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create Data Entry"
                    )
                }
            }
        } else null
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column {
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

                // Main content with enhanced sync progress overlay
                DetailedSyncOverlay(
                    progress = state.detailedSyncProgress,
                    onNavigateBack = { navController.popBackStack() },
                    onRetry = {
                        viewModel.syncDatasetInstances(uploadFirst = false)
                    },
                    onCancel = { viewModel.dismissSyncOverlay() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (state.isLoading) {
                        FullScreenLoader(
                            message = state.navigationProgress?.phaseDetail ?: "Loading dataset instances...",
                            isVisible = true,
                            animationType = com.ash.simpledataentry.presentation.core.LoadingAnimationType.BOUNCING_DOTS,
                            progress = state.navigationProgress?.overallPercentage,
                            progressStep = state.navigationProgress?.phaseTitle
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.filteredInstances) { instance ->
                                var isLoading by remember { mutableStateOf(false) }
                                val formattedDate = try {
                                    instance.lastUpdated?.let { dateStr ->
                                        if (dateStr is String) {
                                            val inputFormat = SimpleDateFormat(
                                                "EEE MMM dd HH:mm:ss zzz yyyy",
                                                Locale.ENGLISH
                                            )
                                            val outputFormat =
                                                SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
                                            val date = inputFormat.parse(dateStr)
                                            date?.let { outputFormat.format(it) } ?: "N/A"
                                        } else {
                                            "N/A"
                                        }
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
                                        state.attributeOptionCombos.find { it.first == instance.attributeOptionCombo }?.second
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

                                // Check if this instance has local draft values (real sync status)
                                val instanceKey = when (instance) {
                                    is com.ash.simpledataentry.domain.model.ProgramInstance.DatasetInstance ->
                                        "${instance.programId}|${instance.period.id}|${instance.organisationUnit.id}|${instance.attributeOptionCombo}"
                                    is com.ash.simpledataentry.domain.model.ProgramInstance.TrackerEnrollment ->
                                        "${instance.programId}|${instance.trackedEntityInstance}|${instance.organisationUnit.id}"
                                    is com.ash.simpledataentry.domain.model.ProgramInstance.EventInstance ->
                                        "${instance.programId}|${instance.programStage}|${instance.organisationUnit.id}|${instance.eventDate?.time ?: 0}"
                                }
                                val hasLocalChanges =
                                    state.instancesWithDrafts.contains(instanceKey)

                                // For the top-right date, we'll use the ListCard's lastUpdated parameter
                                val dateForTopRight =
                                    if (instance.lastUpdated != null) formattedDate else null
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (bulkMode) {
                                        Checkbox(
                                            checked = selectedInstances.contains(instance.id) || isComplete,
                                            onCheckedChange = { checked ->
                                                if (!state.isLoading && !state.isSyncing && !isComplete) {
                                                    viewModel.toggleInstanceSelection(instance.id)
                                                }
                                            },
                                            enabled = !state.isLoading && !state.isSyncing && !isComplete,
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = if (isComplete) MaterialTheme.colorScheme.onSurface.copy(
                                                    alpha = 0.38f
                                                ) else MaterialTheme.colorScheme.primary,
                                                uncheckedColor = if (isComplete) MaterialTheme.colorScheme.onSurface.copy(
                                                    alpha = 0.38f
                                                ) else MaterialTheme.colorScheme.onSurface
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
                                                if (!isLoading && !bulkMode) {
                                                    val encodedDatasetId =
                                                        URLEncoder.encode(datasetId, "UTF-8")
                                                    val encodedDatasetName =
                                                        URLEncoder.encode(datasetName, "UTF-8")
                                                    // Only navigate to data entry for dataset instances for now
                                                    if (instance is com.ash.simpledataentry.domain.model.ProgramInstance.DatasetInstance) {
                                                        navController.navigate("EditEntry/$encodedDatasetId/${instance.period.id}/${instance.organisationUnit.id}/${instance.attributeOptionCombo}/$encodedDatasetName") {
                                                            launchSingleTop = true
                                                            popUpTo("DatasetInstances/{datasetId}/{datasetName}") {
                                                                saveState = true
                                                            }
                                                        }
                                                    }
                                                    // TODO: Add navigation for tracker enrollments and events
                                                }
                                            }
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(16.dp)
                                            ) {
                                                // Title row with date on the right
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

                                                    // Date only in top-right
                                                    if (dateForTopRight != null) {
                                                        Text(
                                                            text = dateForTopRight,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }

                                                // Second row: Attribute option combo + status chips
                                                if (showAttrCombo || hasLocalChanges || isComplete) {
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

                                                        // Status chips (icon-only with left-to-right positioning in second row)
                                                        if ((hasLocalChanges || isComplete) && !bulkMode) {
                                                            Row(
                                                                horizontalArrangement = Arrangement.spacedBy(
                                                                    8.dp
                                                                ),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                // Sync status chip (warning color for local changes)
                                                                if (hasLocalChanges) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Sync,
                                                                        contentDescription = "Not synced",
                                                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                                        modifier = Modifier
                                                                            .size(20.dp)
                                                                            .background(
                                                                                MaterialTheme.colorScheme.secondaryContainer,
                                                                                CircleShape
                                                                            )
                                                                            .padding(4.dp)
                                                                    )
                                                                }

                                                                // Completion status chip
                                                                if (isComplete) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.CheckCircle,
                                                                        contentDescription = "Complete",
                                                                        tint = MaterialTheme.colorScheme.primary,
                                                                        modifier = Modifier.size(20.dp)
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

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(16.dp),
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
                                bulkActionSuccess = success
                                bulkActionMessage =
                                    if (success) "Selected instances marked as complete." else (error
                                        ?: "Failed to complete selected instances.")
                            }
                        },
                        enabled = selectedInstances.isNotEmpty() && !state.isLoading && !state.isSyncing,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Complete Selected")
                    }
                    Button(
                        onClick = { viewModel.clearBulkSelection() },
                        enabled = !state.isLoading && !state.isSyncing,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text("Cancel")
                    }
                }
            }

            // Filter Dialog - only show when data is loaded
            if (showFilterDialog && !state.isLoading) {
                DatasetInstanceFilterDialog(
                    currentFilter = filterState,
                    attributeOptionCombos = state.attributeOptionCombos,
                    dataset = state.dataset,
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
            if (showSyncDialog) {
                SyncConfirmationDialog(
                    syncOptions = SyncOptions(
                        uploadLocalData = state.localInstanceCount > 0,
                        localInstanceCount = state.localInstanceCount
                    ),
                    onConfirm = { uploadFirst ->
                        viewModel.syncDatasetInstances(uploadFirst)
                        showSyncDialog = false
                    },
                    onDismiss = { showSyncDialog = false }
                )
            }
        }

        @Composable
        fun LoadingIndicator() {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        }

        fun parseDhis2PeriodToDate(periodId: String): Date? {
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
    }
}
