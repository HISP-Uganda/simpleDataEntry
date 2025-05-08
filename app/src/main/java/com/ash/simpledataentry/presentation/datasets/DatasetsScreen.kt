package com.ash.simpledataentry.presentation.datasets

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.domain.repository.AuthRepository
import com.ash.simpledataentry.presentation.core.BaseScreen
import com.ash.simpledataentry.presentation.login.LoginViewModel
import kotlinx.coroutines.launch
import org.hisp.dhis.mobile.ui.designsystem.component.AdditionalInfoItem
import org.hisp.dhis.mobile.ui.designsystem.component.ListCard
import org.hisp.dhis.mobile.ui.designsystem.component.ListCardDescriptionModel
import org.hisp.dhis.mobile.ui.designsystem.component.ListCardTitleModel
import org.hisp.dhis.mobile.ui.designsystem.component.state.rememberAdditionalInfoColumnState
import org.hisp.dhis.mobile.ui.designsystem.component.state.rememberListCardState
import org.hisp.dhis.mobile.ui.designsystem.theme.TextColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatasetsScreen(
    navController: NavController,
    viewModel: DatasetsViewModel = hiltViewModel()
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val datasetsState by viewModel.uiState.collectAsState()

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

                    val datasets = (datasetsState as DatasetsState.Success).filteredDatasets
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
                                    description = ListCardDescriptionModel(text = dataset.description ?: ""),
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