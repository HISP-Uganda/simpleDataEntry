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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ash.simpledataentry.domain.model.FilterState
import com.ash.simpledataentry.domain.model.DatasetPeriodType
import com.ash.simpledataentry.domain.model.OrganisationUnit
import com.ash.simpledataentry.domain.model.ProgramItem
import com.ash.simpledataentry.domain.model.ProgramType as DomainProgramType
import com.ash.simpledataentry.navigation.Screen
import com.ash.simpledataentry.presentation.core.OrgUnitTreeMultiPickerDialog
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.ash.simpledataentry.presentation.datasets.components.DatasetIcon
import com.ash.simpledataentry.presentation.datasets.components.ProgramType
import com.ash.simpledataentry.presentation.core.AdaptiveLoadingOverlay
import com.ash.simpledataentry.presentation.core.UiState
import com.ash.simpledataentry.ui.theme.DatasetAccent
import com.ash.simpledataentry.ui.theme.DatasetAccentLight
import com.ash.simpledataentry.ui.theme.EventAccent
import com.ash.simpledataentry.ui.theme.EventAccentLight
import com.ash.simpledataentry.ui.theme.TrackerAccent
import com.ash.simpledataentry.ui.theme.TrackerAccentLight
import android.text.format.DateUtils

@Composable
private fun HomeCategoryCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = MaterialTheme.colorScheme.surface
    val borderColor = if (isSelected) accentColor else MaterialTheme.colorScheme.outlineVariant

    Card(
        modifier = modifier
            .height(150.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = accentColor.copy(alpha = 0.12f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .height(4.dp)
                        .fillMaxWidth(0.4f)
                        .background(
                            color = accentColor,
                            shape = RoundedCornerShape(999.dp)
                        )
                )
            }
        }
    }
}

@Composable
private fun HomeRecentItem(
    program: ProgramItem,
    onClick: () -> Unit
) {
    val (accentColor, accentLightColor) = when (program.programType) {
        DomainProgramType.DATASET -> DatasetAccent to DatasetAccentLight
        DomainProgramType.EVENT -> EventAccent to EventAccentLight
        DomainProgramType.TRACKER -> TrackerAccent to TrackerAccentLight
        else -> DatasetAccent to DatasetAccentLight
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color = accentLightColor, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                DatasetIcon(
                    style = when (program) {
                        is ProgramItem.DatasetProgram -> program.style
                        else -> null
                    },
                    size = 18.dp,
                    programType = when (program.programType) {
                        DomainProgramType.DATASET -> ProgramType.DATASET
                        DomainProgramType.TRACKER -> ProgramType.TRACKER_PROGRAM
                        DomainProgramType.EVENT -> ProgramType.EVENT_PROGRAM
                        else -> ProgramType.DATASET
                    },
                    tint = accentColor
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = program.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = "${program.instanceCount} entries",
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatasetsFilterSection(
    currentFilter: FilterState,
    orgUnits: List<OrganisationUnit>,
    attachedOrgUnitIds: Set<String>,
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
        var selectedOrgUnitIds by remember(currentFilter.orgUnitIds) { mutableStateOf(currentFilter.orgUnitIds) }
        var selectedOrgUnitNames by remember(currentFilter.orgUnitNames) { mutableStateOf(currentFilter.orgUnitNames) }

        var showDatasetPeriodDropdown by remember { mutableStateOf(false) }
        var showOrgUnitPicker by remember { mutableStateOf(false) }

        val orgUnitMap = remember(orgUnits) { orgUnits.associateBy { it.id } }
        val descendantCount = remember(selectedOrgUnitIds, orgUnits, attachedOrgUnitIds) {
            computeDescendantCount(
                selectedOrgUnitIds = selectedOrgUnitIds,
                orgUnits = orgUnits,
                allowedOrgUnitIds = attachedOrgUnitIds
            )
        }
        val orgUnitLabel = when {
            selectedOrgUnitNames.isEmpty() -> "All organization units"
            selectedOrgUnitNames.size == 1 -> selectedOrgUnitNames.first()
            else -> "${selectedOrgUnitNames.size} organization units selected"
        }
        val orgUnitLabelWithDescendants = if (selectedOrgUnitIds.isNotEmpty() && descendantCount > 0) {
            "$orgUnitLabel (+$descendantCount descendants)"
        } else {
            orgUnitLabel
        }

        // Row 1: Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                onApplyFilter(
                    currentFilter.copy(
                        searchQuery = searchQuery,
                        orgUnitIds = selectedOrgUnitIds,
                        orgUnitNames = selectedOrgUnitNames
                    )
                )
            },
            label = { Text("Search programs", color = Color.White) },
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
                                    datasetPeriodType = datasetPeriodType,
                                    orgUnitIds = selectedOrgUnitIds,
                                    orgUnitNames = selectedOrgUnitNames
                                )
                            )
                        }
                    )
                }
            }
        }

        // Row 3: Organization Unit Filter
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = orgUnits.isNotEmpty()) { showOrgUnitPicker = true }
            ) {
                OutlinedTextField(
                    value = orgUnitLabelWithDescendants,
                    onValueChange = { },
                    label = { Text("Organization Unit", color = Color.White) },
                    readOnly = true,
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.7f)
                    ),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                )
            }
        }

        // Clear button
        if (searchQuery.isNotBlank() || datasetPeriodType != DatasetPeriodType.ALL || selectedOrgUnitIds.isNotEmpty()) {
            OutlinedButton(
                onClick = {
                    searchQuery = ""
                    datasetPeriodType = DatasetPeriodType.ALL
                    selectedOrgUnitIds = emptySet()
                    selectedOrgUnitNames = emptyList()
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

        if (showOrgUnitPicker) {
            OrgUnitTreeMultiPickerDialog(
                orgUnits = orgUnits,
                initiallySelectedIds = selectedOrgUnitIds,
                onConfirmSelection = { newIds ->
                    selectedOrgUnitIds = newIds
                    selectedOrgUnitNames = newIds.mapNotNull { orgUnitMap[it]?.name }.sorted()
                    showOrgUnitPicker = false
                    onApplyFilter(
                        currentFilter.copy(
                            orgUnitIds = selectedOrgUnitIds,
                            orgUnitNames = selectedOrgUnitNames
                        )
                    )
                },
                onDismiss = { showOrgUnitPicker = false }
            )
        }
    }
}

private fun computeDescendantCount(
    selectedOrgUnitIds: Set<String>,
    orgUnits: List<OrganisationUnit>,
    allowedOrgUnitIds: Set<String>
): Int {
    if (selectedOrgUnitIds.isEmpty() || orgUnits.isEmpty()) return 0

    val selectedById = orgUnits
        .filter { it.id in selectedOrgUnitIds }
        .associateBy { it.id }

    val descendantIds = mutableSetOf<String>()
    selectedById.values.forEach { selected ->
        val selectedPath = selected.uidPath ?: return@forEach
        val prefix = "$selectedPath/"
        orgUnits.forEach { candidate ->
            val candidatePath = candidate.uidPath ?: return@forEach
            val allowed = allowedOrgUnitIds.isEmpty() || candidate.id in allowedOrgUnitIds
            if (allowed && candidate.id != selected.id && candidatePath.startsWith(prefix)) {
                descendantIds.add(candidate.id)
            }
        }
    }

    return descendantIds.size
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
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.Home) }
    var searchQuery by remember { mutableStateOf("") }
    val activeAccountLabel by viewModel.activeAccountLabel.collectAsState()
    val activeAccountSubtitle by viewModel.activeAccountSubtitle.collectAsState()
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

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == HomeTab.Activities,
                    onClick = { selectedTab = HomeTab.Activities },
                    icon = { Icon(Icons.Default.AccessTime, contentDescription = null) },
                    label = { Text("Activities") }
                )
                NavigationBarItem(
                    selected = selectedTab == HomeTab.Home,
                    onClick = { selectedTab = HomeTab.Home },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("DHIS2 Home") }
                )
                NavigationBarItem(
                    selected = selectedTab == HomeTab.Account,
                    onClick = { selectedTab = HomeTab.Account },
                    icon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                    label = { Text("My Account") }
                )
            }
        }
    ) { innerPadding ->
        AdaptiveLoadingOverlay(
            uiState = uiState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val data = when (val state = uiState) {
                is UiState.Success -> state.data
                is UiState.Error -> state.previousData ?: DatasetsData()
                is UiState.Loading -> DatasetsData()
            }

            LaunchedEffect(data.syncMessage) {
                data.syncMessage?.let { message ->
                    snackbarHostState.showSnackbar(message)
                    viewModel.clearSyncMessage()
                }
            }

            LaunchedEffect(data.currentFilter.searchQuery) {
                if (searchQuery != data.currentFilter.searchQuery) {
                    searchQuery = data.currentFilter.searchQuery
                }
            }

            val applySearch: (String) -> Unit = { query ->
                val trimmed = query.trim()
                viewModel.applyFilter(data.currentFilter.copy(searchQuery = trimmed))
                if (trimmed.isBlank()) {
                    viewModel.filterByProgramType(DomainProgramType.ALL)
                } else {
                    val matchingTypes = data.programs.filter { program ->
                        program.name.contains(trimmed, ignoreCase = true) ||
                            (program.description?.contains(trimmed, ignoreCase = true) == true)
                    }.map { it.programType }.distinct()
                    if (matchingTypes.size == 1) {
                        viewModel.filterByProgramType(matchingTypes.first())
                    }
                }
            }

            val recentPrograms = remember(data.programs) {
                data.programs
                    .filter { it.instanceCount > 0 }
                    .sortedByDescending { it.instanceCount }
                    .take(8)
            }

            when (selectedTab) {
                HomeTab.Home -> {
                    HomeContent(
                        navController = navController,
                        data = data,
                        recentPrograms = recentPrograms,
                        searchQuery = searchQuery,
                        onSearchChange = {
                            searchQuery = it
                            applySearch(it)
                        },
                        onProgramTypeSelected = { viewModel.filterByProgramType(it) },
                        onProgramSelected = { viewModel.prefetchProgramIfNeeded(it) },
                        onSyncClick = {
                            if (uiState !is UiState.Loading) {
                                viewModel.downloadOnlySync()
                            }
                        },
                        syncInProgress = syncState.isRunning,
                        activeAccountLabel = activeAccountLabel,
                        lastSyncLabel = lastSyncLabel,
                        syncStatusLabel = syncStatusLabel,
                        syncState = syncState
                    )
                }

                HomeTab.Activities -> {
                    ActivitiesContent(
                        recentPrograms = recentPrograms,
                        navController = navController,
                        activeAccountLabel = activeAccountLabel
                    )
                }

                HomeTab.Account -> {
                    AccountContent(
                        activeAccountLabel = activeAccountLabel,
                        activeAccountSubtitle = activeAccountSubtitle,
                        onEditAccount = { navController.navigate(Screen.EditAccountScreen.route) },
                        onSettings = { navController.navigate(Screen.SettingsScreen.route) },
                        onAbout = { navController.navigate(Screen.AboutScreen.route) },
                        onReportIssues = { navController.navigate(Screen.ReportIssuesScreen.route) },
                        onLogout = {
                            viewModel.logout()
                            navController.navigate("login") { popUpTo(0) }
                        }
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .fillMaxSize()
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

}

private enum class HomeTab {
    Activities,
    Home,
    Account
}

@Composable
private fun HomeContent(
    navController: NavController,
    data: DatasetsData,
    recentPrograms: List<ProgramItem>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onProgramTypeSelected: (DomainProgramType) -> Unit,
    onProgramSelected: (ProgramItem) -> Unit,
    onSyncClick: () -> Unit,
    syncInProgress: Boolean,
    activeAccountLabel: String?,
    lastSyncLabel: String,
    syncStatusLabel: String,
    syncState: com.ash.simpledataentry.data.sync.AppSyncState
) {
    val welcomeName = activeAccountLabel ?: "User"
    val programs = data.filteredPrograms
    val showProgramList = searchQuery.isNotBlank() || data.currentProgramType != DomainProgramType.ALL

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "DHIS2 Home",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = onSyncClick) {
                            if (syncInProgress) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Sync data",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Text(
                        text = "Welcome, $welcomeName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HomeCategoryCard(
                        title = "Datasets",
                        subtitle = "Data Collection",
                        icon = Icons.Default.Storage,
                        isSelected = data.currentProgramType == DomainProgramType.DATASET,
                        accentColor = DatasetAccent,
                        onClick = { onProgramTypeSelected(DomainProgramType.DATASET) },
                        modifier = Modifier.weight(1f)
                    )
                    HomeCategoryCard(
                        title = "Tracker",
                        subtitle = "Follow Up",
                        icon = Icons.Default.People,
                        isSelected = data.currentProgramType == DomainProgramType.TRACKER,
                        accentColor = TrackerAccent,
                        onClick = { onProgramTypeSelected(DomainProgramType.TRACKER) },
                        modifier = Modifier.weight(1f)
                    )
                    HomeCategoryCard(
                        title = "Events",
                        subtitle = "Event Entry",
                        icon = Icons.Default.Event,
                        isSelected = data.currentProgramType == DomainProgramType.EVENT,
                        accentColor = EventAccent,
                        onClick = { onProgramTypeSelected(DomainProgramType.EVENT) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (showProgramList) {
                if (programs.isEmpty()) {
                    item {
                        val showMessage = data.currentProgramType != DomainProgramType.ALL ||
                            data.currentFilter.searchQuery.isNotBlank()
                        if (showMessage) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "No programs found",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Try re-sync to retrieve metadata.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    TextButton(onClick = onSyncClick) {
                                        Text("Sync metadata")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(items = programs, key = { it.id }) { program ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 2.dp,
                                pressedElevation = 4.dp
                            ),
                        onClick = {
                            onProgramSelected(program)
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

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search programs...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(18.dp)
            )

            IconButton(
                onClick = { navController.navigate(Screen.SettingsScreen.route) }
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }


    }
}

@Composable
private fun ActivitiesContent(
    recentPrograms: List<ProgramItem>,
    navController: NavController,
    activeAccountLabel: String?
) {
    val welcomeName = activeAccountLabel ?: "User"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Text(
                text = "Activities",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Welcome, $welcomeName",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (recentPrograms.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "No recent activity yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Your latest activity will appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(items = recentPrograms, key = { it.id }) { program ->
                HomeRecentItem(
                    program = program,
                    onClick = {
                        val route = when (program.programType) {
                            DomainProgramType.TRACKER -> "TrackerEnrollments/${program.id}/${program.name}"
                            DomainProgramType.EVENT -> "EventInstances/${program.id}/${program.name}"
                            else -> "DatasetInstances/${program.id}/${program.name}"
                        }
                        navController.navigate(route)
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AccountContent(
    activeAccountLabel: String?,
    activeAccountSubtitle: String?,
    onEditAccount: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onReportIssues: () -> Unit,
    onLogout: () -> Unit
) {
    val accountName = activeAccountLabel ?: "My Account"
    val accountSubtitle = activeAccountSubtitle.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Text(
                text = "My Account",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = accountName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (accountSubtitle.isNotBlank()) {
                            Text(
                                text = accountSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            AccountRow(
                icon = Icons.Default.Edit,
                title = "Edit Account",
                onClick = onEditAccount
            )
            AccountRow(
                icon = Icons.Default.Settings,
                title = "Settings",
                onClick = onSettings
            )
            AccountRow(
                icon = Icons.Default.Info,
                title = "About",
                onClick = onAbout
            )
            AccountRow(
                icon = Icons.Default.BugReport,
                title = "Report Issues",
                onClick = onReportIssues
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Version ${com.ash.simpledataentry.BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Logout")
            }
        }
    }
}

@Composable
private fun AccountRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = titleColor
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = titleColor
            )
        }
    }
}
