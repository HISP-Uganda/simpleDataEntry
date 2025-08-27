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
import kotlinx.coroutines.coroutineScope
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.rememberCoroutineScope

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
            IconButton(
                onClick = { showFilterDialog = true },
                enabled = !state.isLoading && !state.isSyncing && !bulkMode
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "Filter",
                    tint = if (filterState.hasActiveFilters()) MaterialTheme.colorScheme.primary else TextColor.OnSurface
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
                        
                        // Build status text
                        val statusText = buildString {
                            if (isDraftInstance) {
                                append("Draft - not yet synced")
                            } else if (isComplete) {
                                append("Complete")
                                if (isSynced) append(" • Synced")
                            } else {
                                append("Incomplete")
                                if (!isSynced) append(" • Not synced")
                            }
                        }
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ListCard(
                                        listCardState = rememberListCardState(
                                            title = ListCardTitleModel(
                                                text = if (showAttrCombo) "$periodText $attrComboName" else periodText,
                                                modifier = Modifier.padding(0.dp)
                                            ),
                                            description = ListCardDescriptionModel(
                                                text = if (isLoading) "Loading..." else statusText,
                                                modifier = Modifier
                                            ),
                                            additionalInfoColumnState = rememberAdditionalInfoColumnState(
                                                additionalInfoList = listOf(
                                                    AdditionalInfoItem(
                                                        key = "Last Updated",
                                                        value = formattedDate,
                                                        isConstantItem = true
                                                    )
                                                ),
                                                syncProgressItem = AdditionalInfoItem(
                                                    key = "",
                                                    value = "",
                                                    isConstantItem = true
                                                ),
                                                expandLabelText = "Show more",
                                                shrinkLabelText = "Show Less",
                                                minItemsToShow = 1,
                                                scrollableContent = false
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
                                    if (!bulkMode) {
                                        when {
                                            instance.state == DatasetInstanceState.COMPLETE -> {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Completed",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier
                                                        .padding(start = 8.dp, end = 8.dp)
                                                        .size(28.dp)
                                                )
                                            }
                                            isDraftInstance -> {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Draft",
                                                    tint = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier
                                                        .padding(start = 8.dp, end = 8.dp)
                                                        .size(24.dp)
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
