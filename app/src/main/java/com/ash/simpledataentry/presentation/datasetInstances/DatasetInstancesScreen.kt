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
                                    text = "${instance.period.id}",
                                    modifier = Modifier.padding(0.dp)
                                ),
                                description = if (instance.state == DatasetInstanceState.COMPLETE) {
                                    ListCardDescriptionModel(
                                        color= TextColor.OnSurface,
                                        text = "",
                                       modifier = Modifier,
                                    )
                                } else {
                                    ListCardDescriptionModel(text = "")
                                },
                                additionalInfoColumnState = rememberAdditionalInfoColumnState(
                                    additionalInfoList = listOf(
                                        AdditionalInfoItem(
                                            key = "Last Updated",
                                            value = instance.lastUpdated?.toString()?.take(10) ?: "N/A",
                                            isConstantItem = true
                                        )
                                    ),
                                    syncProgressItem = AdditionalInfoItem(value = ""),
                                    expandLabelText = "Show more",
                                    shrinkLabelText = "Show Less",
                                    minItemsToShow = 1,
                                    scrollableContent = false
                                ),
                                shadow = true
                            ),
                            onCardClick = {
                                val encodedDatasetName = URLEncoder.encode(datasetName, "UTF-8")
                                val encodedPeriod = URLEncoder.encode(instance.period.id, "UTF-8")
                                val encodedAttributeOptionCombo = URLEncoder.encode(instance.attributeOptionCombo, "UTF-8")
                                navController.navigate(
                                    "EditEntry/$datasetId/${instance.period.id}/${instance.organisationUnit.id}/$encodedAttributeOptionCombo/$encodedDatasetName"
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