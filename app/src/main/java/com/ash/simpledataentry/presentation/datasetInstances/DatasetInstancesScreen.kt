package com.ash.simpledataentry.presentation.datasetInstances

import android.annotation.SuppressLint
import android.text.format.DateFormat
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ash.simpledataentry.domain.model.DatasetInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import com.ash.simpledataentry.domain.model.DatasetInstanceState
import com.ash.simpledataentry.presentation.core.BaseScreen
import org.hisp.dhis.android.core.dataset.DataSetInstance
import org.hisp.dhis.mobile.ui.designsystem.component.AdditionalInfoItem
import org.hisp.dhis.mobile.ui.designsystem.component.ListCard
import org.hisp.dhis.mobile.ui.designsystem.component.ListCardDescriptionModel
import org.hisp.dhis.mobile.ui.designsystem.component.ListCardTitleModel
import org.hisp.dhis.mobile.ui.designsystem.component.state.rememberAdditionalInfoColumnState
import org.hisp.dhis.mobile.ui.designsystem.component.state.rememberListCardState
import org.hisp.dhis.mobile.ui.designsystem.theme.DHIS2Theme
import org.hisp.dhis.mobile.ui.designsystem.theme.TextColor

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
                    val sortedInstances = state.instances.sortedByDescending { instance ->
                        parseDhis2PeriodToDate(instance.period.id)?.time ?: 0L
                    }
                    items(sortedInstances) { instance ->
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
                            Log.e("DatasetInstancesScreen", "Error parsing date: ", e)
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
                                if (!isLoading) {
                                    val encodedDatasetId = URLEncoder.encode(datasetId, "UTF-8")
                                    val encodedDatasetName = URLEncoder.encode(datasetName, "UTF-8")
                                    navController.navigate("EditEntry/$encodedDatasetId/${instance.period.id}/${instance.organisationUnit.id}/${instance.attributeOptionCombo}/$encodedDatasetName") {
                                        launchSingleTop = true
                                        popUpTo("DatasetInstances/{datasetId}/{datasetName}") {
                                            saveState = true
                                        }
                                    }
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

fun parseDhis2PeriodToDate(periodId: String): Date? {
    return try {
        when {
            // Yearly: 2023
            Regex("^\\d{4}$").matches(periodId) -> {
                SimpleDateFormat("yyyy", Locale.ENGLISH).parse(periodId)
            }
            // Monthly: 202306
            Regex("^\\d{6}$").matches(periodId) -> {
                SimpleDateFormat("yyyyMM", Locale.ENGLISH).parse(periodId)
            }
            // Daily: 2023-06-01
            Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(periodId) -> {
                SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(periodId)
            }
            // Weekly: 2023W23
            Regex("^\\d{4}W\\d{1,2}$").matches(periodId) -> {
                val year = periodId.substring(0, 4).toInt()
                val week = periodId.substring(5).toInt()
                val cal = Calendar.getInstance(Locale.ENGLISH)
                cal.clear()
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.WEEK_OF_YEAR, week)
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                cal.time
            }
            // Quarterly: 2023Q2
            Regex("^\\d{4}Q[1-4]$").matches(periodId) -> {
                val year = periodId.substring(0, 4).toInt()
                val quarter = periodId.substring(5).toInt()
                val month = (quarter - 1) * 3
                val cal = Calendar.getInstance(Locale.ENGLISH)
                cal.clear()
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, month)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.time
            }
            // Six-monthly: 2023S1 or 2023S2
            Regex("^\\d{4}S[1-2]$").matches(periodId) -> {
                val year = periodId.substring(0, 4).toInt()
                val semester = periodId.substring(5).toInt()
                val month = if (semester == 1) 0 else 6
                val cal = Calendar.getInstance(Locale.ENGLISH)
                cal.clear()
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, month)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.time
            }
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}