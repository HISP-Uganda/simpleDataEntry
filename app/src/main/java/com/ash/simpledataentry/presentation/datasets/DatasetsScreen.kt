package com.ash.simpledataentry.presentation.datasets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.domain.model.FilterState
import com.ash.simpledataentry.domain.model.DatasetPeriodType
import com.ash.simpledataentry.domain.model.OrganizationUnitFilter
import com.ash.simpledataentry.domain.model.ProgramItem
import com.ash.simpledataentry.domain.model.ProgramType as DomainProgramType
import com.ash.simpledataentry.navigation.Screen
import com.ash.simpledataentry.presentation.core.BaseScreen
import kotlinx.coroutines.launch
import org.hisp.dhis.mobile.ui.designsystem.theme.TextColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.ash.simpledataentry.presentation.datasets.components.DatasetIcon
import com.ash.simpledataentry.presentation.datasets.components.ProgramType
import com.ash.simpledataentry.presentation.core.AdaptiveLoadingOverlay
import com.ash.simpledataentry.presentation.core.UiState
import com.ash.simpledataentry.presentation.core.LoadingOperation
import com.ash.simpledataentry.ui.theme.DHIS2BlueDeep
import com.ash.simpledataentry.ui.theme.DatasetAccent
import com.ash.simpledataentry.ui.theme.DatasetAccentLight
import com.ash.simpledataentry.ui.theme.EventAccent
import com.ash.simpledataentry.ui.theme.EventAccentLight
import com.ash.simpledataentry.ui.theme.TrackerAccent
import com.ash.simpledataentry.ui.theme.TrackerAccentLight
import android.text.format.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatasetsFilterSection(
    currentFilter: FilterState,
    onApplyFilter: (FilterState) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        var searchQuery by remember { mutableStateOf(currentFilter.searchQuery) }
        var datasetPeriodType by remember { mutableStateOf(currentFilter.datasetPeriodType) }
        var organizationUnit by remember { mutableStateOf(currentFilter.organizationUnit) }

        var showDatasetPeriodDropdown by remember { mutableStateOf(false) }
        var showOrgUnitDropdown by remember { mutableStateOf(false) }

        // Row 1: Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                onApplyFilter(
                    currentFilter.copy(
                        searchQuery = searchQuery
                    )
                )
            },
            label = { Text("Search datasets", color = Color.White) },
            placeholder = { Text("Enter dataset name...", color = Color.White.copy(alpha = 0.7f)) },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = Color.White)
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.7f),
                cursorColor = Color.White
            )
        )

        // Row 2: Period Type Filter
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = datasetPeriodType.displayName,
                onValueChange = { },
                label = { Text("Period Type", color = Color.White) },
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.7f)
                ),
                trailingIcon = {
                    IconButton(onClick = { showDatasetPeriodDropdown = !showDatasetPeriodDropdown }) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                    }
                }
            )

            DropdownMenu(
                expanded = showDatasetPeriodDropdown,
                onDismissRequest = { showDatasetPeriodDropdown = false }
            ) {
                DatasetPeriodType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.displayName) },
                        onClick = {
                            datasetPeriodType = type
                            showDatasetPeriodDropdown = false
                            onApplyFilter(
                                currentFilter.copy(
                                    datasetPeriodType = datasetPeriodType
                                )
                            )
                        }
                    )
                }
            }
        }

        // Row 3: Organization Unit Filter
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = organizationUnit.displayName,
                onValueChange = { },
                label = { Text("Organization Unit", color = Color.White) },
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.7f)
                ),
                trailingIcon = {
                    IconButton(onClick = { showOrgUnitDropdown = !showOrgUnitDropdown }) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                    }
                }
            )

            DropdownMenu(
                expanded = showOrgUnitDropdown,
                onDismissRequest = { showOrgUnitDropdown = false }
            ) {
                OrganizationUnitFilter.entries.forEach { unit ->
                    DropdownMenuItem(
                        text = { Text(unit.displayName) },
                        onClick = {
                            organizationUnit = unit
                            showOrgUnitDropdown = false
                            onApplyFilter(
                                currentFilter.copy(
                                    organizationUnit = organizationUnit
                                )
                            )
                        }
                    )
                }
            }
        }

        // Clear button
        if (searchQuery.isNotBlank() || datasetPeriodType != DatasetPeriodType.ALL ||
            organizationUnit != OrganizationUnitFilter.ALL
        ) {
            OutlinedButton(
                onClick = {
                    searchQuery = ""
                    datasetPeriodType = DatasetPeriodType.ALL
                    organizationUnit = OrganizationUnitFilter.ALL
                    onApplyFilter(FilterState())
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                border = BorderStroke(1.dp, Color.White)
            ) {
                Text("Clear All")
            }
        }
    }
}

@Composable
private fun SyncStatusChip(
    label: String,
    isError: Boolean
) {
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

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

private fun formatRelativeTime(timestamp: Long): String {
    return DateUtils.getRelativeTimeSpanString(
        timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatasetsScreen(
    navController: NavController,
    viewModel: DatasetsViewModel = hiltViewModel()
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showFilterSection by remember { mutableStateOf(false) }
    val activeAccountLabel by viewModel.activeAccountLabel.collectAsState()
    val activeAccountSubtitle by viewModel.activeAccountSubtitle.collectAsState()
    val subtitle = when ((uiState as? UiState.Success)?.data?.currentProgramType ?: DomainProgramType.ALL) {
        DomainProgramType.ALL -> "All programs"
        DomainProgramType.DATASET -> "Datasets"
        DomainProgramType.TRACKER -> "Tracker programs"
        DomainProgramType.EVENT -> "Event programs"
    }
    val syncState by viewModel.syncController.appSyncState.collectAsState()
    val backgroundSyncRunning by viewModel.backgroundSyncRunning.collectAsState()
    val isRefreshingAfterSync by viewModel.isRefreshingAfterSync.collectAsState()
    val lastSyncLabel = syncState.lastSync?.let { formatRelativeTime(it) } ?: "Never"
    val syncStatusLabel = when {
        backgroundSyncRunning || syncState.isRunning -> "Sync in progress"
        !syncState.error.isNullOrBlank() -> "Sync error"
        isRefreshingAfterSync -> "Updating list"
        else -> "Up to date"
    }

    // Do not auto-sync when navigating back; sync is login/ manual only.

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                val headerTitle = activeAccountLabel ?: "Menu"
                val headerSubtitle = activeAccountSubtitle.orEmpty()
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = headerTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (headerSubtitle.isNotBlank()) {
                        Text(
                            text = headerSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                    selected = false,
                    onClick = {
                        scope.launch {
                            drawerState.close()
                            navController.navigate("settings")
                        }
                    }
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    label = { Text("About") },
                    selected = false,
                    onClick = {
                        scope.launch {
                            drawerState.close()
                            navController.navigate(Screen.AboutScreen.route)
                        }
                    }
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Report Issues") },
                    selected = false,
                    onClick = {
                        scope.launch {
                            drawerState.close()
                            navController.navigate(Screen.ReportIssuesScreen.route)
                        }
                    }
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                    label = { Text("Logout") },
                    selected = false,
                    onClick = {
                        scope.launch {
                            viewModel.logout()
                            navController.navigate("login") {
                                popUpTo(0)
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.weight(1f))

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    label = { Text("Delete Account") },
                    selected = false,
                    onClick = { 
                        scope.launch {
                            drawerState.close()
                            showDeleteConfirmation = true
                        }
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedIconColor = MaterialTheme.colorScheme.error,
                        unselectedTextColor = MaterialTheme.colorScheme.error
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        },
        gesturesEnabled = true
    ) {
        BaseScreen(
            title = "Home",
            subtitle = subtitle,
            navController = navController,
            actions = {
                // Background loading indicator during sync
                if (syncState.isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Sync button
                IconButton(
                    onClick = {
                        if (uiState !is UiState.Loading) {
                            viewModel.downloadOnlySync()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Download latest data",
                        tint = TextColor.OnSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = { showFilterSection = !showFilterSection }) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Filter & Sort",
                        tint = if (showFilterSection) Color.White else TextColor.OnSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        scope.launch {
                            drawerState.open()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = TextColor.OnSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        ) {
            AdaptiveLoadingOverlay(
                uiState = uiState,
                modifier = Modifier.fillMaxSize()
            ) {
                // Extract data safely from UiState
                val data = when (val state = uiState) {
                    is UiState.Success -> state.data
                    is UiState.Error -> state.previousData ?: DatasetsData()
                    is UiState.Loading -> DatasetsData()
                }

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
                            shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                        ) {
                            DatasetsFilterSection(
                                currentFilter = data.currentFilter,
                                onApplyFilter = { newFilter ->
                                    viewModel.applyFilter(newFilter)
                                }
                            )
                        }
                    }

                    // Program type filter tabs
                    ScrollableTabRow(
                        selectedTabIndex = when (data.currentProgramType) {
                            DomainProgramType.ALL -> 0
                            DomainProgramType.DATASET -> 1
                            DomainProgramType.TRACKER -> 2
                            DomainProgramType.EVENT -> 3
                        },
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            indicator = { tabPositions ->
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[when (data.currentProgramType) {
                                        DomainProgramType.ALL -> 0
                                        DomainProgramType.DATASET -> 1
                                        DomainProgramType.TRACKER -> 2
                                        DomainProgramType.EVENT -> 3
                                    }]),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        ) {
                            // All Programs Tab
                            Tab(
                                selected = data.currentProgramType == DomainProgramType.ALL,
                                onClick = { viewModel.filterByProgramType(DomainProgramType.ALL) },
                                text = { Text(text = "All", style = MaterialTheme.typography.labelMedium) },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Apps,
                                        contentDescription = null
                                    )
                                }
                            )

                            // Datasets Tab
                            Tab(
                                selected = data.currentProgramType == DomainProgramType.DATASET,
                                onClick = { viewModel.filterByProgramType(DomainProgramType.DATASET) },
                                text = { Text(text = "Datasets", style = MaterialTheme.typography.labelMedium) },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Storage,
                                        contentDescription = null
                                    )
                                }
                            )

                            // Tracker Programs Tab
                            Tab(
                                selected = data.currentProgramType == DomainProgramType.TRACKER,
                                onClick = { viewModel.filterByProgramType(DomainProgramType.TRACKER) },
                                text = { Text(text = "Tracker", style = MaterialTheme.typography.labelMedium) },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.People,
                                        contentDescription = null
                                    )
                                }
                            )

                            // Event Programs Tab
                            Tab(
                                selected = data.currentProgramType == DomainProgramType.EVENT,
                                onClick = { viewModel.filterByProgramType(DomainProgramType.EVENT) },
                                text = { Text(text = "Events", style = MaterialTheme.typography.labelMedium) },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Event,
                                        contentDescription = null
                                    )
                                }
                            )
                        }

                    // Show sync success message
                    LaunchedEffect(data.syncMessage) {
                        data.syncMessage?.let { message ->
                            snackbarHostState.showSnackbar(message)
                            // Don't clear immediately - let snackbar show first
                            kotlinx.coroutines.delay(2000)
                            viewModel.clearSyncMessage()
                        }
                    }

                    // Main content - programs list
                    val programs = data.filteredPrograms
                    if (programs.isEmpty()) {
                        val hasFilters = data.currentFilter != FilterState() || data.currentProgramType != DomainProgramType.ALL
                        val headline = if (hasFilters) "No programs match your filters" else "No programs available"
                        val guidance = if (hasFilters) {
                            "Clear filters or change program type."
                        } else {
                            "Try syncing or confirm your account access."
                        }

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
                                    text = headline,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = guidance,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (hasFilters) {
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.applyFilter(FilterState())
                                                viewModel.filterByProgramType(DomainProgramType.ALL)
                                            }
                                        ) {
                                            Text("Clear filters")
                                        }
                                    }
                                    Button(
                                        onClick = { viewModel.downloadOnlySync() }
                                    ) {
                                        Text("Sync now")
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(items = programs, key = { it.id }) { program ->
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
                                        // Route to appropriate screen based on program type
                                        val route = when (program.programType) {
                                            DomainProgramType.TRACKER -> "TrackerEnrollments/${program.id}/${program.name}"
                                            DomainProgramType.EVENT -> "EventInstances/${program.id}/${program.name}"
                                            else -> "DatasetInstances/${program.id}/${program.name}"
                                        }
                                        navController.navigate(route)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        val (accentColor, accentLightColor) = when (program.programType) {
                                            DomainProgramType.DATASET -> DatasetAccent to DatasetAccentLight
                                            DomainProgramType.EVENT -> EventAccent to EventAccentLight
                                            DomainProgramType.TRACKER -> TrackerAccent to TrackerAccentLight
                                            else -> DatasetAccent to DatasetAccentLight
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .background(
                                                    color = accentLightColor,
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            DatasetIcon(
                                                style = when (program) {
                                                    is ProgramItem.DatasetProgram -> program.style
                                                    else -> null
                                                },
                                                size = 22.dp,
                                                programType = when (program.programType) {
                                                    DomainProgramType.DATASET -> ProgramType.DATASET
                                                    DomainProgramType.TRACKER -> ProgramType.TRACKER_PROGRAM
                                                    DomainProgramType.EVENT -> ProgramType.EVENT_PROGRAM
                                                    else -> ProgramType.DATASET
                                                },
                                                tint = accentColor
                                            )
                                        }

                                        // Content column
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            val (countSingular, countPlural) = when (program.programType) {
                                                DomainProgramType.DATASET -> "entry" to "entries"
                                                DomainProgramType.TRACKER -> "enrollment" to "enrollments"
                                                DomainProgramType.EVENT -> "event" to "events"
                                                else -> "item" to "items"
                                            }
                                            val countLabel = if (program.instanceCount == 1) countSingular else countPlural
                                            val countText = if (program.instanceCount > 0) {
                                                "${program.instanceCount} $countLabel"
                                            } else {
                                                "No $countPlural yet"
                                            }

                                            // Title with program type badge
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = program.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 2,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )

                                                Surface(
                                                    color = accentLightColor,
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Text(
                                                        text = program.instanceCount.toString(),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = accentColor,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }

                                                Icon(
                                                    imageVector = Icons.Default.ChevronRight,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            // Description (if available)
                                            program.description?.let { description ->
                                                if (description.isNotBlank()) {
                                                    Text(
                                                        text = description,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 2
                                                    )
                                                }
                                            }

                                            Text(
                                                text = countText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "Last sync: $lastSyncLabel",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                SyncStatusChip(
                                                    label = syncStatusLabel,
                                                    isError = !syncState.error.isNullOrBlank()
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

                Box(modifier = Modifier.fillMaxSize()) {
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
                                Text(
                                    data.visuals.message,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    )
                }
            }

            // Delete Account Confirmation Dialog
            if (showDeleteConfirmation) {
                val context = androidx.compose.ui.platform.LocalContext.current

                AlertDialog(
                    onDismissRequest = { showDeleteConfirmation = false },
                    title = {
                        Text(
                            "Delete Account",
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    text = {
                        Column {
                            Text("Are you sure you want to delete your account?")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "This will permanently delete:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("• All saved login credentials")
                            Text("• All downloaded data")
                            Text("• All unsaved draft entries")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "This action cannot be undone.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.deleteAccount(context)
                                showDeleteConfirmation = false
                                // Navigate to login after deletion
                                navController.navigate("login") {
                                    popUpTo(0)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Delete Account")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showDeleteConfirmation = false }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
