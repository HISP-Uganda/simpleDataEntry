package com.ash.simpledataentry.presentation.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ash.simpledataentry.domain.model.ProgramInstance
import com.ash.simpledataentry.presentation.core.AdaptiveLoadingOverlay
import com.ash.simpledataentry.presentation.core.BaseScreen
import com.ash.simpledataentry.presentation.core.ErrorBanner
import com.ash.simpledataentry.presentation.core.LoadingOperation
import com.ash.simpledataentry.presentation.core.ShimmerLoadingTable
import com.ash.simpledataentry.presentation.core.UiState
import java.net.URLEncoder

@Composable
fun TrackerEnrollmentTableScreen(
    navController: NavController,
    programId: String,
    programName: String,
    viewModel: TrackerEnrollmentTableViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSearchBar by remember { mutableStateOf(false) }

    LaunchedEffect(programId) {
        viewModel.initialize(programId, programName)
    }

    LaunchedEffect(navController.currentBackStackEntry) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        if (currentRoute?.contains("TrackerEnrollments") == true) {
            viewModel.refreshData()
        }
    }

    val currentUiState = uiState
    val successData = when (currentUiState) {
        is UiState.Success -> currentUiState.data
        is UiState.Error -> currentUiState.previousData
        else -> null
    }

    val successMessage = successData?.successMessage
    LaunchedEffect(successMessage) {
        if (!successMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(successMessage, duration = SnackbarDuration.Short)
            viewModel.clearSuccessMessage()
        }
    }

    val isSyncing = currentUiState is UiState.Loading && currentUiState.operation !is LoadingOperation.Initial
    val showInitialShimmer =
        currentUiState is UiState.Loading && currentUiState.operation is LoadingOperation.Initial && successData == null

    BaseScreen(
        title = successData?.programName?.takeIf { it.isNotBlank() } ?: programName,
        navController = navController,
        syncStatusController = viewModel.syncController,
        actions = {
            IconButton(
                onClick = { viewModel.showColumnDialog() },
                enabled = successData != null
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Configure Columns")
            }
            IconButton(
                onClick = { showSearchBar = !showSearchBar },
                enabled = successData != null
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
            IconButton(
                onClick = { viewModel.syncEnrollments() },
                enabled = successData != null && !isSyncing
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Sync, contentDescription = "Sync")
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val encodedProgramId = URLEncoder.encode(programId, "UTF-8")
                    val encodedProgramName = URLEncoder.encode(programName, "UTF-8")
                    navController.navigate("CreateEnrollment/$encodedProgramId/$encodedProgramName")
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Enrollment")
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                showInitialShimmer -> {
                    ShimmerLoadingTable(
                        columnCount = 4,
                        rowCount = 8,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                successData != null -> {
                    val content: @Composable () -> Unit = {
                        TrackerEnrollmentTableContent(
                            data = successData,
                            showSearchBar = showSearchBar,
                            onSearchQueryChange = viewModel::updateSearchQuery,
                            onSortColumn = viewModel::sortByColumn,
                            onRowClick = { enrollment ->
                                val encodedProgramId = URLEncoder.encode(programId, "UTF-8")
                                val encodedProgramName = URLEncoder.encode(programName, "UTF-8")
                                navController.navigate(
                                    "TrackerDashboard/${enrollment.id}/$encodedProgramId/$encodedProgramName"
                                )
                            }
                        )
                    }

                    if (isSyncing && currentUiState is UiState.Loading) {
                        AdaptiveLoadingOverlay(
                            uiState = currentUiState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            content()
                        }
                    } else {
                        content()
                    }

                    if (successData.showColumnDialog) {
                        ColumnSelectionDialog(
                            availableColumns = successData.availableColumns,
                            selectedColumnIds = successData.selectedColumnIds,
                            onToggleColumn = viewModel::toggleColumnSelection,
                            onMoveColumn = viewModel::moveColumn,
                            onSelectAll = viewModel::selectAllColumns,
                            onDeselectAll = viewModel::deselectAllColumns,
                            onApply = viewModel::applyColumnSelection,
                            onDismiss = viewModel::hideColumnDialog
                        )
                    }
                }
                else -> {
                    EmptyStateMessage(
                        modifier = Modifier.align(Alignment.Center),
                        message = "No enrollments available."
                    )
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
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun TrackerEnrollmentTableContent(
    data: TrackerEnrollmentTableData,
    showSearchBar: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSortColumn: (String) -> Unit,
    onRowClick: (ProgramInstance.TrackerEnrollment) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (showSearchBar) {
            SearchBar(
                query = data.searchQuery,
                onQueryChange = onSearchQueryChange,
                onClearSearch = {
                    onSearchQueryChange("")
                }
            )
        }

        if (data.enrollments.isEmpty()) {
            EmptyStateMessage(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                message = "No enrollments found. Tap + to create a new enrollment."
            )
        } else {
            EnrollmentTable(
                data = data,
                onSortColumn = onSortColumn,
                onRowClick = onRowClick
            )
        }
    }
}

@Composable
private fun EmptyStateMessage(
    modifier: Modifier = Modifier,
    message: String
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        placeholder = { Text("Search enrollments...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearSearch) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true
    )
}

@Composable
private fun EnrollmentTable(
    data: TrackerEnrollmentTableData,
    onSortColumn: (String) -> Unit,
    onRowClick: (ProgramInstance.TrackerEnrollment) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TableHeaderRow(
            columns = data.columns,
            sortColumnId = data.sortColumnId,
            sortOrder = data.sortOrder,
            onSortColumn = onSortColumn
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(data.tableRows) { row ->
                TableDataRow(
                    row = row,
                    columns = data.columns,
                    onClick = { onRowClick(row.enrollment) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun TableHeaderRow(
    columns: List<TableColumn>,
    sortColumnId: String?,
    sortOrder: SortOrder,
    onSortColumn: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 12.dp)
    ) {
        columns.forEach { column ->
            TableHeaderCell(
                column = column,
                isSorted = sortColumnId == column.id,
                sortOrder = sortOrder,
                onClick = { if (column.sortable) onSortColumn(column.id) }
            )
        }
    }
}

@Composable
private fun TableHeaderCell(
    column: TableColumn,
    isSorted: Boolean,
    sortOrder: SortOrder,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .width(150.dp)
            .clickable(enabled = column.sortable) { onClick() }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = column.displayName,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (column.sortable && isSorted) {
            val icon = when (sortOrder) {
                SortOrder.ASCENDING -> Icons.Default.KeyboardArrowUp
                SortOrder.DESCENDING -> Icons.Default.KeyboardArrowDown
                SortOrder.NONE -> Icons.Default.KeyboardArrowUp
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = androidx.compose.material3.MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun TableDataRow(
    row: EnrollmentTableRow,
    columns: List<TableColumn>,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 12.dp)
    ) {
        columns.forEach { column ->
            TableDataCell(
                value = row.cells[column.id] ?: "",
                columnId = column.id
            )
        }
    }
}

@Composable
private fun TableDataCell(
    value: String,
    columnId: String
) {
    Box(
        modifier = Modifier
            .width(150.dp)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = value,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (columnId == "syncStatus" || columnId == "status") {
                TextAlign.Center
            } else {
                TextAlign.Start
            }
        )
    }
}

@Composable
private fun ColumnSelectionDialog(
    availableColumns: List<TableColumn>,
    selectedColumnIds: Set<String>,
    onToggleColumn: (String) -> Unit,
    onMoveColumn: (String, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Configure Columns",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSelectAll,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Select All")
                    }
                    OutlinedButton(
                        onClick = onDeselectAll,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Deselect All")
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(availableColumns.size) { index ->
                        val column = availableColumns[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { onMoveColumn(column.id, true) },
                                enabled = index > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Move up",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { onMoveColumn(column.id, false) },
                                enabled = index < availableColumns.size - 1,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Move down",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onToggleColumn(column.id) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = column.id in selectedColumnIds,
                                    onCheckedChange = { onToggleColumn(column.id) },
                                    colors = CheckboxDefaults.colors()
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = column.displayName,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onApply) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
