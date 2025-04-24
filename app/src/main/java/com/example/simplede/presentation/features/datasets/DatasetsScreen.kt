package com.example.simplede.presentation.features.datasets

import com.example.simplede.data.SessionManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.NavController
import com.example.simplede.presentation.components.BaseScreen
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.hisp.dhis.mobile.ui.designsystem.component.*
import org.hisp.dhis.mobile.ui.designsystem.component.state.rememberAdditionalInfoColumnState
import org.hisp.dhis.mobile.ui.designsystem.component.state.rememberListCardState
import org.hisp.dhis.mobile.ui.designsystem.theme.TextColor
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.hisp.dhis.mobile.ui.designsystem.component.*
import org.hisp.dhis.mobile.ui.designsystem.component.state.rememberAdditionalInfoColumnState
import org.hisp.dhis.mobile.ui.designsystem.component.state.rememberListCardState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatasetsScreen(
    navController: NavController,
    viewModel: DatasetsViewModel = viewModel(factory = DatasetsViewModelFactory())
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val datasetsState by viewModel.datasetsState.collectAsState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Menu",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                    selected = false,
                    onClick = { /* TODO: Navigate to settings */ }
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    label = { Text("About") },
                    selected = false,
                    onClick = { /* TODO: Navigate to about */ }
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Logout, contentDescription = null) },
                    label = { Text("Logout") },
                    selected = false,
                    onClick = {
                        scope.launch {
                            SessionManager.logout()
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
                    onClick = { /* TODO: Implement account deletion */ },
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
            navController = navController,
            actions = {
                var showFilterDialog by remember { mutableStateOf(false) }

                IconButton(onClick = { viewModel.syncDatasets() }) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync",
                        tint = TextColor.OnSurface
                    )
                }

                IconButton(onClick = { showFilterDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Filter",
                        tint = TextColor.OnSurface
                    )
                }

                if (showFilterDialog) {
                    var selectedPeriod by remember { mutableStateOf("") }
                    var selectedSyncStatus by remember { mutableStateOf<Boolean?>(null) }
                    var showSyncDropdown by remember { mutableStateOf(false) }

                    AlertDialog(
                        onDismissRequest = { showFilterDialog = false },
                        title = { Text("Filter Datasets") },
                        text = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                OutlinedTextField(
                                    value = selectedPeriod,
                                    onValueChange = { selectedPeriod = it },
                                    label = { Text("Period ID") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    placeholder = { Text("Enter period ID") }
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = when(selectedSyncStatus) {
                                            true -> "Synced"
                                            false -> "Not Synced"
                                            null -> "All"
                                        },
                                        onValueChange = { },
                                        label = { Text("Sync Status") },
                                        readOnly = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showSyncDropdown = true },
                                        trailingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = "Select sync status"
                                            )
                                        }
                                    )

                                    DropdownMenu(
                                        expanded = showSyncDropdown,
                                        onDismissRequest = { showSyncDropdown = false },
                                        modifier = Modifier.fillMaxWidth(0.9f)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("All") },
                                            onClick = {
                                                selectedSyncStatus = null
                                                showSyncDropdown = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Synced") },
                                            onClick = {
                                                selectedSyncStatus = true
                                                showSyncDropdown = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Not Synced") },
                                            onClick = {
                                                selectedSyncStatus = false
                                                showSyncDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(
                                    onClick = {
                                        viewModel.clearFilters()
                                        showFilterDialog = false
                                    }
                                ) {
                                    Text("Clear")
                                }
                                Button(
                                    onClick = {
                                        viewModel.filterDatasets(
                                            period = selectedPeriod.takeIf { it.isNotEmpty() },
                                            syncStatus = selectedSyncStatus
                                        )
                                        showFilterDialog = false
                                    }
                                ) {
                                    Text("Apply")
                                }
                            }
                        },
                        dismissButton = null
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
                        tint = TextColor.OnSurface
                    )
                }
            }
        ) {
            when (datasetsState) {
                is DatasetsState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is DatasetsState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (datasetsState as DatasetsState.Error).message,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                is DatasetsState.Success -> {


                    val syncItem = AdditionalInfoItem(
                        key = "",
                        value = "",
                        isConstantItem = true
                    )

                    val additionalInfo = rememberAdditionalInfoColumnState(
                        additionalInfoList = emptyList(),
                        syncProgressItem = syncItem
                    )



                    val datasets = (datasetsState as DatasetsState.Success).datasets
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(datasets) { dataset ->
                            ListCard(
                                modifier = Modifier.fillMaxWidth(),
                                listCardState = rememberListCardState(
                                    title = ListCardTitleModel(text = dataset.name),
                                    description = ListCardDescriptionModel(text = dataset.description),
                                    shadow = true,
                                    additionalInfoColumnState = additionalInfo,

                                    ),
                                onCardClick = {
                                    navController.navigate("DatasetInstances/${dataset.id}/${dataset.name}")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}