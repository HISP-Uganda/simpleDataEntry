package com.ash.simpledataentry.presentation.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ash.simpledataentry.presentation.core.DetailedSyncOverlay
import java.net.URLEncoder

/**
 * Table-based view for tracker program enrollments
 * Replaces DatasetInstancesScreen for TRACKER program types
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerEnrollmentTableScreen(
    navController: NavController,
    programId: String,
    programName: String,
    viewModel: TrackerEnrollmentTableViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSearchBar by remember { mutableStateOf(false) }

    // Initialize
    LaunchedEffect(programId) {
        viewModel.initialize(programId, programName)
    }

    // Refresh when returning to screen
    LaunchedEffect(navController.currentBackStackEntry) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        if (currentRoute?.contains("TrackerEnrollments") == true) {
            viewModel.refreshData()
        }
    }

    // Show error messages
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }

    // Show success messages
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            viewModel.clearSuccessMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(programName) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
            // Column configuration button
            IconButton(
                onClick = { viewModel.showColumnDialog() }
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Configure Columns")
            }

            // Search icon
            IconButton(
                onClick = { showSearchBar = !showSearchBar }
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }

            // Sync button
            IconButton(
                onClick = {
                    if (!state.isSyncing) {
                        viewModel.syncEnrollments()
                    }
                },
                enabled = !state.isLoading && !state.isSyncing
            ) {
                if (state.isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Sync, contentDescription = "Sync")
                }
            }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Loading enrollments...")
                        }
                    }
                }
                state.enrollments.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No enrollments found",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap + to create a new enrollment",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Search bar
                        if (showSearchBar) {
                            SearchBar(
                                query = state.searchQuery,
                                onQueryChange = { viewModel.updateSearchQuery(it) },
                                onClearSearch = {
                                    viewModel.updateSearchQuery("")
                                    showSearchBar = false
                                }
                            )
                        }

                        // Table
                        EnrollmentTable(
                            state = state,
                            onSortColumn = { columnId -> viewModel.sortByColumn(columnId) },
                            onRowClick = { enrollment ->
                                val encodedProgramId = URLEncoder.encode(programId, "UTF-8")
                                val encodedProgramName = URLEncoder.encode(programName, "UTF-8")
                                navController.navigate(
                                    "TrackerDashboard/${enrollment.id}/$encodedProgramId/$encodedProgramName"
                                )
                            }
                        )
                    }
                }
            }

            // Column selection dialog
            if (state.showColumnDialog) {
                ColumnSelectionDialog(
                    availableColumns = state.availableColumns,
                    selectedColumnIds = state.selectedColumnIds,
                    fixedColumnIds = emptySet(), // No fixed columns - all are flexible
                    onToggleColumn = { columnId -> viewModel.toggleColumnSelection(columnId) },
                    onMoveColumn = { columnId, moveUp -> viewModel.moveColumn(columnId, moveUp) },
                    onSelectAll = { viewModel.selectAllColumns() },
                    onDeselectAll = { viewModel.deselectAllColumns() },
                    onApply = { viewModel.applyColumnSelection() },
                    onDismiss = { viewModel.hideColumnDialog() }
                )
            }
        }
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
    state: TrackerEnrollmentTableState,
    onSortColumn: (String) -> Unit,
    onRowClick: (com.ash.simpledataentry.domain.model.ProgramInstance.TrackerEnrollment) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header row
        TableHeaderRow(
            columns = state.columns,
            sortColumnId = state.sortColumnId,
            sortOrder = state.sortOrder,
            onSortColumn = onSortColumn
        )

        // Data rows
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp) // Space for FAB
        ) {
            items(state.tableRows) { row ->
                TableDataRow(
                    row = row,
                    columns = state.columns,
                    onClick = { onRowClick(row.enrollment) }
                )
                Divider()
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
            .background(MaterialTheme.colorScheme.surfaceVariant)
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
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Sort indicator
        if (column.sortable && isSorted) {
            Icon(
                imageVector = when (sortOrder) {
                    SortOrder.ASCENDING -> Icons.Default.ArrowUpward
                    SortOrder.DESCENDING -> Icons.Default.ArrowDownward
                    SortOrder.NONE -> Icons.Default.ArrowUpward
                },
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
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
            style = MaterialTheme.typography.bodyMedium,
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
    fixedColumnIds: Set<String>,
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
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                // Action buttons row
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

                // All columns section (no distinction between fixed and attributes)
                Text(
                    text = "Available Columns",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

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
                            // Reorder buttons
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
                                    onCheckedChange = { onToggleColumn(column.id) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = column.displayName,
                                    style = MaterialTheme.typography.bodyMedium
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
