package com.ash.simpledataentry.presentation.datasets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ash.simpledataentry.domain.model.FilterState
import com.ash.simpledataentry.domain.model.PeriodFilterType
import com.ash.simpledataentry.domain.model.RelativePeriod
import com.ash.simpledataentry.domain.model.SyncStatus
import com.ash.simpledataentry.util.PeriodHelper
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
                                            value = filterState.relativePeriod?.displayName ?: "Select Period",
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Period") },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .menuAnchor()
                                        )
                                        
                                        ExposedDropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false }
                                        ) {
                                            RelativePeriod.values().forEach { period ->
                                                DropdownMenuItem(
                                                    text = { Text(period.displayName) },
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
                                            IconButton(onClick = { /* TODO: Open date picker */ }) {
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
                                            IconButton(onClick = { /* TODO: Open date picker */ }) {
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
                                    .menuAnchor()
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
}
