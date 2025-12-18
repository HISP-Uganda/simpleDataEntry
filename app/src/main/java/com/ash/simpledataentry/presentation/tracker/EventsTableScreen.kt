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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
fun EventsTableScreen(
    navController: NavController,
    programId: String,
    programName: String,
    viewModel: EventsTableViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSearchBar by remember { mutableStateOf(false) }

    LaunchedEffect(programId) {
        viewModel.initialize(programId, programName)
    }

    LaunchedEffect(navController.currentBackStackEntry) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        if (currentRoute?.contains("EventsTable") == true) {
            viewModel.refreshData()
        }
    }

    val currentUiState = uiState
    val data = when (currentUiState) {
        is UiState.Success -> currentUiState.data
        is UiState.Error -> currentUiState.previousData
        else -> null
    }

    val successMessage = data?.successMessage
    LaunchedEffect(successMessage) {
        if (!successMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(successMessage, duration = SnackbarDuration.Short)
            viewModel.clearSuccessMessage()
        }
    }

    val isSyncing = currentUiState is UiState.Loading && currentUiState.operation !is LoadingOperation.Initial
    val showInitialShimmer = currentUiState is UiState.Loading &&
        currentUiState.operation is LoadingOperation.Initial &&
        data == null

    BaseScreen(
        title = data?.programName?.takeIf { it.isNotBlank() } ?: programName,
        navController = navController,
        syncStatusController = viewModel.syncController,
        actions = {
            IconButton(
                onClick = { showSearchBar = !showSearchBar },
                enabled = data != null
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
            IconButton(
                onClick = { viewModel.syncEvents() },
                enabled = data != null && !isSyncing
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
                    val encodedProgramName = URLEncoder.encode(programName, "UTF-8")
                    navController.navigate("CreateEvent/$programId/$encodedProgramName")
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Event")
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
                data != null -> {
                    val content: @Composable () -> Unit = {
                        EventsTableContent(
                            data = data,
                            showSearchBar = showSearchBar,
                            onSearchQueryChange = viewModel::updateSearchQuery,
                            onSortColumn = viewModel::sortByColumn,
                            onRowClick = { row ->
                                val encodedProgramName = URLEncoder.encode(programName, "UTF-8")
                                navController.navigate(
                                    "EditStandaloneEvent/$programId/$encodedProgramName/${row.id}"
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
                }
                else -> {
                    EmptyStateMessage(
                        modifier = Modifier.align(Alignment.Center),
                        message = "No events available. Tap + to create a new event."
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
private fun EventsTableContent(
    data: EventsTableData,
    showSearchBar: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSortColumn: (String) -> Unit,
    onRowClick: (EventTableRow) -> Unit
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

        if (data.events.isEmpty()) {
            EmptyStateMessage(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                message = "No events found. Tap + to create a new event."
            )
        } else {
            EventsTable(
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
    data: EventsTableData,
    onSortColumn: (String) -> Unit,
    onRowClick: (EventTableRow) -> Unit
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
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (column.sortable && isSorted) {
            val icon = when (sortOrder) {
                SortOrder.ASCENDING -> Icons.Default.ArrowUpward
                SortOrder.DESCENDING -> Icons.Default.ArrowDownward
                SortOrder.NONE -> Icons.Default.ArrowUpward
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
