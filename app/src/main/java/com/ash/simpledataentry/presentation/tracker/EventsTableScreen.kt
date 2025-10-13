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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
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
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

/**
 * Table-based view for event program instances
 * Shows events in a line list/pivot table format
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsTableScreen(
    navController: NavController,
    programId: String,
    programName: String,
    viewModel: EventsTableViewModel = hiltViewModel()
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
        if (currentRoute?.contains("EventsTable") == true) {
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
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
                                viewModel.syncEvents()
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
                    // Navigate to CreateEvent screen for creating new event
                    navController.navigate("CreateEvent/$programId/$programName")
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Event")
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
                            Text("Loading events...")
                        }
                    }
                }
                state.events.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No events found",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap + to create a new event",
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
                        EventsTable(
                            state = state,
                            onSortColumn = { columnId -> viewModel.sortByColumn(columnId) },
                            onRowClick = { row ->
                                // Navigate to EditStandaloneEvent screen for editing standalone events
                                navController.navigate(
                                    "EditStandaloneEvent/$programId/$programName/${row.id}"
                                )
                            }
                        )
                    }
                }
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
        placeholder = { Text("Search events...") },
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
private fun EventsTable(
    state: EventsTableState,
    onSortColumn: (String) -> Unit,
    onRowClick: (EventTableRow) -> Unit
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
                    onClick = { onRowClick(row) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun TableHeaderRow(
    columns: List<EventTableColumn>,
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
    column: EventTableColumn,
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
    row: EventTableRow,
    columns: List<EventTableColumn>,
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
