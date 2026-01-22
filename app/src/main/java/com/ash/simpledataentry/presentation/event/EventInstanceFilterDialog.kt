package com.ash.simpledataentry.presentation.event

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ash.simpledataentry.domain.model.CompletionStatus
import com.ash.simpledataentry.domain.model.EventInstanceFilterState
import com.ash.simpledataentry.domain.model.PeriodFilterType
import com.ash.simpledataentry.domain.model.SyncStatus
import com.ash.simpledataentry.presentation.core.DatePickerUtils
import com.ash.simpledataentry.presentation.core.DateRangePickerDialog
import org.hisp.dhis.android.core.common.RelativePeriod

@Composable
fun EventInstanceFilterDialog(
    currentFilter: EventInstanceFilterState,
    onFilterChanged: (EventInstanceFilterState) -> Unit,
    onClearFilters: () -> Unit,
    onDismiss: () -> Unit
) {
    var filterState by remember { mutableStateOf(currentFilter) }
    var showDateRangePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = null
                )
                Text("Filter Events")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Period Filter",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Column(modifier = Modifier.selectableGroup()) {
                            PeriodFilterType.values().forEach { periodType ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = filterState.periodType == periodType,
                                            onClick = {
                                                filterState = filterState.copy(
                                                    periodType = periodType,
                                                    relativePeriod = if (periodType != PeriodFilterType.RELATIVE) null else filterState.relativePeriod
                                                )
                                            },
                                            role = Role.RadioButton
                                        )
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = filterState.periodType == periodType,
                                        onClick = null
                                    )
                                    Text(
                                        text = when (periodType) {
                                            PeriodFilterType.ALL -> "All Periods"
                                            PeriodFilterType.RELATIVE -> "Relative Period"
                                            PeriodFilterType.CUSTOM_RANGE -> "Custom Range"
                                        },
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }

                        if (filterState.periodType == PeriodFilterType.RELATIVE) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp)
                                    .selectableGroup()
                            ) {
                                RelativePeriod.values().forEach { relativePeriod ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .selectable(
                                                selected = filterState.relativePeriod == relativePeriod,
                                                onClick = {
                                                    filterState = filterState.copy(relativePeriod = relativePeriod)
                                                },
                                                role = Role.RadioButton
                                            )
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = filterState.relativePeriod == relativePeriod,
                                            onClick = null
                                        )
                                        Text(
                                            text = relativePeriodLabel(relativePeriod),
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (filterState.periodType == PeriodFilterType.CUSTOM_RANGE) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = filterState.customFromDate?.let { DatePickerUtils.formatDateForDisplay(it) }
                                        ?: "Start date",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("From") },
                                    trailingIcon = {
                                        IconButton(onClick = { showDateRangePicker = true }) {
                                            Icon(
                                                imageVector = Icons.Default.DateRange,
                                                contentDescription = "Select date range"
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = filterState.customToDate?.let { DatePickerUtils.formatDateForDisplay(it) }
                                        ?: "End date",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("To") },
                                    trailingIcon = {
                                        IconButton(onClick = { showDateRangePicker = true }) {
                                            Icon(
                                                imageVector = Icons.Default.DateRange,
                                                contentDescription = "Select date range"
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Sync Status",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Column(modifier = Modifier.selectableGroup()) {
                            SyncStatus.values().forEach { syncStatus ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = filterState.syncStatus == syncStatus,
                                            onClick = {
                                                filterState = filterState.copy(syncStatus = syncStatus)
                                            },
                                            role = Role.RadioButton
                                        )
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = filterState.syncStatus == syncStatus,
                                        onClick = null
                                    )
                                    Text(
                                        text = syncStatus.displayName,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Completion Status",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Column(modifier = Modifier.selectableGroup()) {
                            CompletionStatus.values().forEach { completionStatus ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = filterState.completionStatus == completionStatus,
                                            onClick = {
                                                filterState = filterState.copy(completionStatus = completionStatus)
                                            },
                                            role = Role.RadioButton
                                        )
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = filterState.completionStatus == completionStatus,
                                        onClick = null
                                    )
                                    Text(
                                        text = completionStatus.displayName,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        onClearFilters()
                        onDismiss()
                    }
                ) {
                    Text("Clear All")
                }
                Button(
                    onClick = {
                        onFilterChanged(filterState)
                        onDismiss()
                    }
                ) {
                    Text("Apply")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showDateRangePicker) {
        DateRangePickerDialog(
            initialStartDate = filterState.customFromDate,
            initialEndDate = filterState.customToDate,
            onDateRangeSelected = { startDate, endDate ->
                filterState = filterState.copy(
                    periodType = PeriodFilterType.CUSTOM_RANGE,
                    customFromDate = startDate,
                    customToDate = endDate,
                    relativePeriod = null
                )
                showDateRangePicker = false
            },
            onDismissRequest = { showDateRangePicker = false }
        )
    }
}

private fun relativePeriodLabel(period: RelativePeriod): String {
    return when (period) {
        RelativePeriod.TODAY -> "Today"
        RelativePeriod.YESTERDAY -> "Yesterday"
        RelativePeriod.LAST_3_DAYS -> "Last 3 days"
        RelativePeriod.LAST_7_DAYS -> "Last 7 days"
        RelativePeriod.LAST_14_DAYS -> "Last 14 days"
        RelativePeriod.LAST_30_DAYS -> "Last 30 days"
        RelativePeriod.LAST_60_DAYS -> "Last 60 days"
        RelativePeriod.LAST_90_DAYS -> "Last 90 days"
        RelativePeriod.LAST_180_DAYS -> "Last 180 days"
        RelativePeriod.THIS_WEEK -> "This week"
        RelativePeriod.LAST_WEEK -> "Last week"
        RelativePeriod.LAST_4_WEEKS -> "Last 4 weeks"
        RelativePeriod.LAST_12_WEEKS -> "Last 12 weeks"
        RelativePeriod.LAST_52_WEEKS -> "Last 52 weeks"
        RelativePeriod.THIS_MONTH -> "This month"
        RelativePeriod.LAST_MONTH -> "Last month"
        RelativePeriod.LAST_3_MONTHS -> "Last 3 months"
        RelativePeriod.LAST_6_MONTHS -> "Last 6 months"
        RelativePeriod.LAST_12_MONTHS -> "Last 12 months"
        RelativePeriod.THIS_BIMONTH -> "This bi-month"
        RelativePeriod.LAST_BIMONTH -> "Last bi-month"
        RelativePeriod.LAST_6_BIMONTHS -> "Last 6 bi-months"
        RelativePeriod.THIS_QUARTER -> "This quarter"
        RelativePeriod.LAST_QUARTER -> "Last quarter"
        RelativePeriod.LAST_4_QUARTERS -> "Last 4 quarters"
        RelativePeriod.THIS_SIX_MONTH -> "This six-month"
        RelativePeriod.LAST_SIX_MONTH -> "Last six-month"
        RelativePeriod.LAST_2_SIXMONTHS -> "Last 2 six-months"
        RelativePeriod.THIS_YEAR -> "This year"
        RelativePeriod.LAST_YEAR -> "Last year"
        RelativePeriod.LAST_5_YEARS -> "Last 5 years"
        RelativePeriod.LAST_10_YEARS -> "Last 10 years"
        RelativePeriod.THIS_FINANCIAL_YEAR -> "This financial year"
        RelativePeriod.LAST_FINANCIAL_YEAR -> "Last financial year"
        RelativePeriod.LAST_5_FINANCIAL_YEARS -> "Last 5 financial years"
        RelativePeriod.LAST_10_FINANCIAL_YEARS -> "Last 10 financial years"
        RelativePeriod.WEEKS_THIS_YEAR -> "Weeks this year"
        RelativePeriod.MONTHS_THIS_YEAR -> "Months this year"
        RelativePeriod.BIMONTHS_THIS_YEAR -> "Bi-months this year"
        RelativePeriod.QUARTERS_THIS_YEAR -> "Quarters this year"
        RelativePeriod.MONTHS_LAST_YEAR -> "Months last year"
        RelativePeriod.QUARTERS_LAST_YEAR -> "Quarters last year"
        RelativePeriod.THIS_BIWEEK -> "This bi-week"
        RelativePeriod.LAST_BIWEEK -> "Last bi-week"
        RelativePeriod.LAST_4_BIWEEKS -> "Last 4 bi-weeks"
    }
}
