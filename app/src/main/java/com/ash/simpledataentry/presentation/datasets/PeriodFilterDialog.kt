package com.ash.simpledataentry.presentation.datasets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ash.simpledataentry.domain.model.FilterState
import com.ash.simpledataentry.domain.model.PeriodFilterType
import com.ash.simpledataentry.domain.model.SyncStatus
import com.ash.simpledataentry.presentation.core.DateRangePickerDialog
import org.hisp.dhis.android.core.common.RelativePeriod
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodFilterDialog(
    currentFilter: FilterState,
    onFilterChanged: (FilterState) -> Unit,
    onDismiss: () -> Unit
) {
    var filterState by remember { mutableStateOf(currentFilter) }
    val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    var showDateRangePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null
                )
                Text("Filter Datasets")
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
                // Period Filter Section
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
                            // All Periods
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = filterState.periodType == PeriodFilterType.ALL,
                                        onClick = {
                                            filterState = filterState.copy(
                                                periodType = PeriodFilterType.ALL,
                                                relativePeriod = null,
                                                customFromDate = null,
                                                customToDate = null
                                            )
                                        },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = filterState.periodType == PeriodFilterType.ALL,
                                    onClick = null
                                )
                                Text(
                                    text = "All Periods",
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                            
                            // Relative Periods
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = filterState.periodType == PeriodFilterType.RELATIVE,
                                        onClick = {
                                            filterState = filterState.copy(
                                                periodType = PeriodFilterType.RELATIVE,
                                                relativePeriod = RelativePeriod.THIS_MONTH,
                                                customFromDate = null,
                                                customToDate = null
                                            )
                                        },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = filterState.periodType == PeriodFilterType.RELATIVE,
                                    onClick = null
                                )
                                Text(
                                    text = "Relative Period",
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                            
                            // Relative Period Dropdown
                            if (filterState.periodType == PeriodFilterType.RELATIVE) {
                                var expanded by remember { mutableStateOf(false) }
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp, top = 8.dp)
                                ) {
                                    ExposedDropdownMenuBox(
                                        expanded = expanded,
                                        onExpandedChange = { expanded = !expanded }
                                    ) {
                                        OutlinedTextField(
                                            value = filterState.relativePeriod?.let { relativePeriodLabel(it) }
                                                ?: "Select Period",
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Period") },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                                        )
                                        
                                        ExposedDropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false }
                                        ) {
                                            RelativePeriod.values().forEach { period ->
                                                DropdownMenuItem(
                                                    text = { Text(relativePeriodLabel(period)) },
                                                    onClick = {
                                                        filterState = filterState.copy(relativePeriod = period)
                                                        expanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // Custom Date Range
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = filterState.periodType == PeriodFilterType.CUSTOM_RANGE,
                                        onClick = {
                                            filterState = filterState.copy(
                                                periodType = PeriodFilterType.CUSTOM_RANGE,
                                                relativePeriod = null,
                                                customFromDate = Date(),
                                                customToDate = Date()
                                            )
                                        },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = filterState.periodType == PeriodFilterType.CUSTOM_RANGE,
                                    onClick = null
                                )
                                Text(
                                    text = "Custom Date Range",
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                            
                            // Custom Date Range Inputs
                            if (filterState.periodType == PeriodFilterType.CUSTOM_RANGE) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp, top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = filterState.customFromDate?.let { dateFormatter.format(it) } ?: "",
                                        onValueChange = {},
                                        label = { Text("From Date") },
                                        readOnly = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        trailingIcon = {
                                            IconButton(onClick = { showDateRangePicker = true }) {
                                                Icon(Icons.Default.DateRange, contentDescription = "Select date")
                                            }
                                        }
                                    )
                                    
                                    OutlinedTextField(
                                        value = filterState.customToDate?.let { dateFormatter.format(it) } ?: "",
                                        onValueChange = {},
                                        label = { Text("To Date") },
                                        readOnly = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        trailingIcon = {
                                            IconButton(onClick = { showDateRangePicker = true }) {
                                                Icon(Icons.Default.DateRange, contentDescription = "Select date")
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Sync Status Filter Section
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
                        
                        var syncExpanded by remember { mutableStateOf(false) }
                        
                        ExposedDropdownMenuBox(
                            expanded = syncExpanded,
                            onExpandedChange = { syncExpanded = !syncExpanded }
                        ) {
                            OutlinedTextField(
                                value = filterState.syncStatus.displayName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Status") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = syncExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                            )
                            
                            ExposedDropdownMenu(
                                expanded = syncExpanded,
                                onDismissRequest = { syncExpanded = false }
                            ) {
                                SyncStatus.values().forEach { status ->
                                    DropdownMenuItem(
                                        text = { Text(status.displayName) },
                                        onClick = {
                                            filterState = filterState.copy(syncStatus = status)
                                            syncExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Search Query Section
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
                            text = "Search",
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        OutlinedTextField(
                            value = filterState.searchQuery,
                            onValueChange = { filterState = filterState.copy(searchQuery = it) },
                            label = { Text("Search datasets") },
                            placeholder = { Text("Enter dataset name...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = {
                        filterState = FilterState()
                        onFilterChanged(filterState)
                        onDismiss()
                    }
                ) {
                    Text("Clear")
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
