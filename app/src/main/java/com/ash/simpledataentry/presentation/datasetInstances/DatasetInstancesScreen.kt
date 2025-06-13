package com.ash.simpledataentry.presentation.datasetInstances

import android.annotation.SuppressLint
import android.text.format.DateFormat
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.ash.simpledataentry.domain.model.DatasetInstanceState
import com.ash.simpledataentry.presentation.core.BaseScreen
import org.hisp.dhis.mobile.ui.designsystem.component.AdditionalInfoItem
import org.hisp.dhis.mobile.ui.designsystem.component.ListCard
import org.hisp.dhis.mobile.ui.designsystem.component.ListCardDescriptionModel
import org.hisp.dhis.mobile.ui.designsystem.component.ListCardTitleModel
import org.hisp.dhis.mobile.ui.designsystem.component.state.rememberAdditionalInfoColumnState
import org.hisp.dhis.mobile.ui.designsystem.component.state.rememberListCardState
import org.hisp.dhis.mobile.ui.designsystem.theme.DHIS2Theme
import org.hisp.dhis.mobile.ui.designsystem.theme.TextColor
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import android.util.Log
import androidx.compose.foundation.background
import kotlinx.coroutines.delay

@SuppressLint("SuspiciousIndentation")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatasetInstancesScreen(
    navController: NavController,
    datasetId: String,
    datasetName: String,
    viewModel: DatasetInstancesViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(datasetId) {
        viewModel.setDatasetId(datasetId)
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
                onClick = { viewModel.manualRefresh() },
                enabled = !state.isLoading && !state.isSyncing
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Sync",
                    tint = TextColor.OnSurface
                )
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Splash overlay
            if (state.showSplash) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f)),
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
                            text = "Loading form...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
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
                    items(state.instances) { instance ->
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
                            Log.e("DatasetInstancesScreen", "Error parsing date: ${e.message}")
                            "N/A"
                        }
                        val periodText = instance.period.toString().replace("Period(id=", "").replace(")", "")
                        val attrComboName = state.attributeOptionCombos.find { it.first == instance.attributeOptionCombo }?.second ?: instance.attributeOptionCombo
                        val showAttrCombo = !attrComboName.equals("default", ignoreCase = true)
                        ListCard(
                            listCardState = rememberListCardState(
                                title = ListCardTitleModel(
                                    text = if (showAttrCombo) "$periodText $attrComboName" else periodText,
                                    modifier = Modifier.padding(0.dp)
                                ),
                                description = ListCardDescriptionModel(
                                    text = if (isLoading) "Loading..." else "",
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
                                if (!isLoading && !state.showSplash) {
                                    isLoading = true
                                    viewModel.setShowSplash(true)
                                    // Simulate loading splash, then navigate
                                    val encodedDatasetId = URLEncoder.encode(datasetId, "UTF-8")
                                    val encodedDatasetName = URLEncoder.encode(datasetName, "UTF-8")
                                    val navControllerCopy = navController
                                    val instanceCopy = instance
                                    val viewModelCopy = viewModel
                                    val showSplashCopy = state.showSplash
                                    // Launch in Compose scope

                                         // 700ms splash
                                        navControllerCopy.navigate("EditEntry/$encodedDatasetId/${instanceCopy.period.id}/${instanceCopy.organisationUnit.id}/${instanceCopy.attributeOptionCombo}/$encodedDatasetName") {
                                            launchSingleTop = true
                                            popUpTo("DatasetInstances/{datasetId}/{datasetName}") {
                                                saveState = true
                                            }
                                        }
                                        viewModelCopy.setShowSplash(false)
                                        isLoading = false
                                    }

                            }
                        )
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )

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