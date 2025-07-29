package com.ash.simpledataentry.presentation.datasetInstances

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ash.simpledataentry.domain.model.CompletionStatus
import com.ash.simpledataentry.domain.model.DatasetInstanceFilterState
import com.ash.simpledataentry.domain.model.PeriodFilterType
import com.ash.simpledataentry.domain.model.RelativePeriod
import com.ash.simpledataentry.domain.model.SyncStatus

@Composable
fun DatasetInstanceFilterDialog(
    currentFilter: DatasetInstanceFilterState,
    onFilterChanged: (DatasetInstanceFilterState) -> Unit,
    onDismiss: () -> Unit
) {
    val tempFilter = remember { mutableStateOf(currentFilter) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Filter by Period")
                PeriodFilterType.values().forEach { periodType ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = tempFilter.value.periodType == periodType,
                            onClick = { tempFilter.value = tempFilter.value.copy(periodType = periodType) }
                        )
                        Text(periodType.name)
                    }
                }

                if (tempFilter.value.periodType == PeriodFilterType.RELATIVE) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Select Relative Period")
                    RelativePeriod.values().forEach { relativePeriod ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = tempFilter.value.relativePeriod == relativePeriod,
                                onClick = { tempFilter.value = tempFilter.value.copy(relativePeriod = relativePeriod) }
                            )
                            Text(relativePeriod.displayName)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Filter by Sync Status")
                SyncStatus.values().forEach { syncStatus ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = tempFilter.value.syncStatus == syncStatus,
                            onClick = { tempFilter.value = tempFilter.value.copy(syncStatus = syncStatus) }
                        )
                        Text(syncStatus.displayName)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Filter by Completion Status")
                CompletionStatus.values().forEach { completionStatus ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = tempFilter.value.completionStatus == completionStatus,
                            onClick = { tempFilter.value = tempFilter.value.copy(completionStatus = completionStatus) }
                        )
                        Text(completionStatus.displayName)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row {
                    Button(onClick = { onFilterChanged(tempFilter.value); onDismiss() }) {
                        Text("Apply")
                    }
                    Button(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}
