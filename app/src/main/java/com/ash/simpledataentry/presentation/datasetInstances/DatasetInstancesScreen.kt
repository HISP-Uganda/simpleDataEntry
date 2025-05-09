package com.ash.simpledataentry.presentation.datasetInstances

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatasetInstancesScreen(
    navController: NavController,
    datasetId: String,
    datasetName: String,
    viewModel: DatasetInstancesViewModel = hiltViewModel()
) {
    LaunchedEffect(datasetId) {
        viewModel.setDatasetId(datasetId)
    }

    val state by viewModel.state.collectAsState()

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
                            text = "Loading datasets...",
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
                        
                        ListCard(
                            listCardState = rememberListCardState(
                                title = ListCardTitleModel(
                                    text = instance.period.toString(),
                                    modifier = Modifier.padding(0.dp)
                                ),
                                description = ListCardDescriptionModel(
                                    text = if (isLoading) "Loading..." else instance.attributeOptionCombo,
                                    modifier = Modifier
                                ),
                                additionalInfoColumnState = rememberAdditionalInfoColumnState(
                                    additionalInfoList = listOf(
                                        AdditionalInfoItem(
                                            key = "Organization Unit",
                                            value = instance.organisationUnit.name,
                                            isConstantItem = true
                                        ),
                                        AdditionalInfoItem(
                                            key = "Last Updated",
                                            value = instance.lastUpdated?.toString() ?: "N/A",
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
                                isLoading = true
                                val encodedDatasetId = URLEncoder.encode(datasetId, "UTF-8")
                                val encodedDatasetName = URLEncoder.encode(datasetName, "UTF-8")
                                navController.navigate("EditEntry/$encodedDatasetId/${instance.period}/${instance.organisationUnit}/${instance.attributeOptionCombo}/$encodedDatasetName")
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