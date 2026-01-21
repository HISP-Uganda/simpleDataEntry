package com.ash.simpledataentry.presentation.dataEntry.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FrozenHeaderGrid(
    columnHeaders: List<String>,
    rows: List<List<@Composable () -> Unit>>,
    modifier: Modifier = Modifier
) {
    val horizontalScrollState = rememberScrollState()

    Column(modifier = modifier) {
        // Column Headers (Frozen)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(horizontalScrollState)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(vertical = 8.dp)
                .height(IntrinsicSize.Min) // Ensure header cells take min height
        ) {
            columnHeaders.forEach { header ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = header,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Data Rows (Scrollable)
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(rows, key = { index, _ -> index }) { _, rowCells ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(horizontalScrollState)
                        .padding(vertical = 4.dp)
                        .height(IntrinsicSize.Min) // Ensure row cells take min height
                ) {
                    rowCells.forEach { cellContent ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                        ) {
                            cellContent()
                        }
                    }
                }
            }
        }
    }
}
