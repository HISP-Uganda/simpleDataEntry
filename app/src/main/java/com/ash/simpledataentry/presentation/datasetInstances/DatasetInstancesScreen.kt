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
import com.ash.simpledataentry.domain.model.SyncStatus
import com.ash.simpledataentry.domain.model.CompletionStatus
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import com.ash.simpledataentry.domain.model.DatasetInstanceState
import com.ash.simpledataentry.presentation.core.BaseScreen
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

// Status information for better UI presentation
data class StatusInfo(
    val text: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: androidx.compose.ui.graphics.Color,
    val backgroundColor: androidx.compose.ui.graphics.Color
)

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
                    tint = if (filterState.hasActiveFilters() || showFilterSection) MaterialTheme.colorScheme.primary else TextColor.OnSurface
                )
            }
            IconButton(
                onClick = { showSyncDialog = true },
                enabled = !state.isLoading && !state.isSyncing && !bulkMode
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Sync",
                    tint = TextColor.OnSurface
                )
            }
            IconButton(
                onClick = { viewModel.toggleBulkCompletionMode() },
                enabled = !state.isLoading && !state.isSyncing
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = if (bulkMode) "Exit Bulk Complete" else "Bulk Complete",
                    tint = if (bulkMode) MaterialTheme.colorScheme.primary else TextColor.OnSurface
                )
            }
        }
    ) {
        Column {
            // Pull-down filter section
            AnimatedVisibility(
                visible = showFilterSection,
                enter = slideInVertically(initialOffsetY = { -it }),
                exit = slideOutVertically(targetOffsetY = { -it })
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    DatasetInstancesPullDownFilter(
                        currentFilter = filterState,
                        onApplyFilter = { newFilter ->
                            viewModel.updateFilterState(newFilter)
                        }
                    )
                }
            }
            
            // Main content
            Box(modifier = Modifier.fillMaxSize()) {
                if (state.isLoading || state.isSyncing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (state.isSyncing) "Syncing..." else "Loading datasets...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
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
                                    val inputFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH)
                                    val outputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
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
                        val periodText = instance.period.toString().replace("Period(id=", "").replace(")", "")
                        val attrComboName = state.attributeOptionCombos.find { it.first == instance.attributeOptionCombo }?.second ?: instance.attributeOptionCombo
                        val showAttrCombo = !attrComboName.equals("default", ignoreCase = true)
                        val isDraftInstance = instance.id.startsWith("draft-")
                        val isComplete = instance.state == DatasetInstanceState.COMPLETE
                        val isSynced = isComplete && !isDraftInstance
                        
                        // Fix sync status logic - check actual sync state, not just completion
                        val hasBeenSynced = !isDraftInstance // If it's not a draft, it has been synced
                        
                        // For the top-right date, we'll use the ListCard's lastUpdated parameter
                        val dateForTopRight = if (instance.lastUpdated != null) formattedDate else null
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
                                        checkedColor = if (isComplete) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.primary,
                                        uncheckedColor = if (isComplete) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                // Create additional info items for status indicators
                                val additionalInfoItems = mutableListOf<AdditionalInfoItem>()
                                
                                if (!bulkMode) {
                                    when {
                                        // For completed datasets: green "Complete" text + checkmarks
                                        isComplete -> {
                                            additionalInfoItems.add(
                                                AdditionalInfoItem(
                                                    key = "",
                                                    value = "Complete ✓✓",
                                                    color = org.hisp.dhis.mobile.ui.designsystem.theme.SurfaceColor.Primary
                                                )
                                            )
                                        }
                                        // For datasets that haven't been synced: sync indicator
                                        !hasBeenSynced -> {
                                            additionalInfoItems.add(
                                                AdditionalInfoItem(
                                                    key = "",
                                                    value = "Not synced ⟳",
                                                    color = org.hisp.dhis.mobile.ui.designsystem.theme.SurfaceColor.Primary
                                                )
                                            )
                                        }
                                        // For incomplete datasets: ensure consistent card sizing
                                        else -> {
                                            // Add empty placeholder to maintain consistent height
                                            additionalInfoItems.add(
                                                AdditionalInfoItem(
                                                    key = "",
                                                    value = "",
                                                    isConstantItem = true
                                                )
                                            )
                                        }
                                    }
                                }
                                
                                ListCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    listCardState = rememberListCardState(
                                        title = ListCardTitleModel(
                                            text = "${instance.organisationUnit.name} • $periodText", // Org unit and period in title
                                            modifier = Modifier.padding(0.dp)
                                        ),
                                        description = if (showAttrCombo) ListCardDescriptionModel(
                                            text = attrComboName, // Attribute option combo in second row as requested
                                            modifier = Modifier
                                        ) else null,
                                        lastUpdated = dateForTopRight, // Date in top right corner, no colon
                                        additionalInfoColumnState = rememberAdditionalInfoColumnState(
                                            additionalInfoList = additionalInfoItems,
                                            syncProgressItem = AdditionalInfoItem(
                                                key = "",
                                                value = "",
                                                isConstantItem = true
                                            )
                                        ),
                                        shadow = true
                                    ),
                                    onCardClick = {
                                        if (!isLoading && !bulkMode) {
                                            val encodedDatasetId = URLEncoder.encode(datasetId, "UTF-8")
                                            val encodedDatasetName = URLEncoder.encode(datasetName, "UTF-8")
                                            navController.navigate("EditEntry/$encodedDatasetId/${instance.period.id}/${instance.organisationUnit.id}/${instance.attributeOptionCombo}/$encodedDatasetName") {
                                                launchSingleTop = true
                                                popUpTo("DatasetInstances/{datasetId}/{datasetName}") {
                                                    saveState = true
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
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
            if (bulkMode) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.bulkCompleteSelectedInstances { success, error ->
                                bulkActionSuccess = success
                                bulkActionMessage = if (success) "Selected instances marked as complete." else (error ?: "Failed to complete selected instances.")
                            }
                        },
                        enabled = selectedInstances.isNotEmpty() && !state.isLoading && !state.isSyncing
                    ) {
                        Text("Complete Selected")
                    }
                    Button(
                        onClick = { viewModel.clearBulkSelection() },
                        enabled = !state.isLoading && !state.isSyncing
                    ) {
                        Text("Cancel")
                    }
                }
            }
            if (!bulkMode) {
                FloatingActionButton(
                    onClick = {
                        val encodedDatasetName = URLEncoder.encode(datasetName, "UTF-8")
                        navController.navigate("CreateDataEntry/$datasetId/$encodedDatasetName") {
                            launchSingleTop = true
                            popUpTo("DatasetInstances/{datasetId}/{datasetName}") {
                                saveState = true
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create Data Entry"
                    )
                }
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
}

@Composable
private fun LoadingIndicator() {
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
        
        var showSyncDropdown by remember { mutableStateOf(false) }
        var showCompletionDropdown by remember { mutableStateOf(false) }
        
        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { 
                searchQuery = it
                onApplyFilter(
                    currentFilter.copy(searchQuery = searchQuery)
                )
            },
            label = { Text("Search dataset instances") },
            placeholder = { Text("Enter org unit, period...") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                Icon(Icons.Default.FilterList, contentDescription = null)
            }
        )
        
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
                                onApplyFilter(currentFilter.copy(syncStatus = syncStatus))
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
                                onApplyFilter(currentFilter.copy(completionStatus = completionStatus))
                            }
                        )
                    }
                }
            }
        }
        
        // Clear button
        if (searchQuery.isNotBlank() || syncStatus != SyncStatus.ALL || completionStatus != CompletionStatus.ALL) {
            OutlinedButton(
                onClick = {
                    searchQuery = ""
                    syncStatus = SyncStatus.ALL
                    completionStatus = CompletionStatus.ALL
                    onApplyFilter(DatasetInstanceFilterState())
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Clear Filters")
            }
        }
    }
}
