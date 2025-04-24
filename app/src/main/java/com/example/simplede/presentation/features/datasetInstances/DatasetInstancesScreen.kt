package com.example.simplede.presentation.features.datasetInstances

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import org.hisp.dhis.mobile.ui.designsystem.theme.TextColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.simplede.presentation.components.BaseScreen
import org.hisp.dhis.mobile.ui.designsystem.component.*
import org.hisp.dhis.mobile.ui.designsystem.component.state.*
import java.net.URLEncoder

@Composable
fun DatasetInstancesScreen(
    navController: NavController,
    datasetId: String,
    datasetName: String,
    viewModel: DatasetInstancesViewModel
) {
    val state by viewModel.state.collectAsState()

    // Remove LaunchedEffect since loading is now handled in ViewModel's init

    BaseScreen(
        title = datasetName,
        navController = navController,
        actions = {
            IconButton(
                onClick = { viewModel.syncDatasetInstances() },
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
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.instances) { instance ->
                        ListCard(
                            modifier = Modifier.fillMaxWidth(),
                            listCardState = rememberListCardState(
                                title = ListCardTitleModel(
                                    text = instance.organisationUnit.name,
                                    modifier = Modifier.padding(0.dp)
                                ),
                                description = ListCardDescriptionModel(
                                    text = "Period: ${instance.period.id}"
                                ),
                                additionalInfoColumnState = rememberAdditionalInfoColumnState(
                                    additionalInfoList = listOf(
                                        AdditionalInfoItem(
                                            key = "Status",
                                            value = instance.state.name,
                                            isConstantItem = true
                                        )
                                    ),
                                    syncProgressItem = AdditionalInfoItem(value = "Syncing" ),
                                    expandLabelText = "Show more",
                                    shrinkLabelText = "Show Less",
                                    minItemsToShow = 2,
                                    scrollableContent = false
                                ),
                                shadow = true
                            ),
                            onCardClick = {
                                val encodedDatasetName = URLEncoder.encode(datasetName, "UTF-8")
                                val encodedPeriod = URLEncoder.encode(instance.period.id, "UTF-8")
                                val encodedAttributeOptionCombo = URLEncoder.encode(instance.attributeOptionCombo, "UTF-8")
                                navController.navigate(
                                    "EditEntry/$datasetId/${instance.id}/$encodedDatasetName/$encodedPeriod/$encodedAttributeOptionCombo"
                                )
                            }
                        )
                    }
                }
            }

            state.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text(error)
                }
            }

            state.successMessage?.let { message ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text(message)
                }
            }

            FloatingActionButton(
                onClick = {
                    val encodedDatasetName = URLEncoder.encode(datasetName, "UTF-8")
                    navController.navigate("CreateDataEntry/$datasetId/$encodedDatasetName")
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