package com.example.simplede.presentation.features.dataEntry

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.simplede.domain.model.*
import com.example.simplede.presentation.features.dataEntry.DataEntryViewModel

@Composable
fun EditEntryScreen(
    viewModel: DataEntryViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = state.datasetName,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        } else {
            // Group data values by their data elements to create sections
            val groupedValues = state.dataValues.groupBy { it.dataElement }
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(groupedValues.toList()) { (dataElement, values) ->
                    DataElementSection(
                        dataElement = dataElement,
                        values = values,
                        onValueChange = { value, dataValue ->
                            viewModel.updateCurrentValue(value)
                            viewModel.validateCurrentValue()
                            viewModel.saveCurrentValue()
                        }
                    )
                }
            }
        }

        // Error message
        state.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun DataElementSection(
    dataElement: String,
    values: List<DataValue>,
    onValueChange: (String, DataValue) -> Unit
) {
    val isSmallSection = values.size < 15
    var expanded by remember { mutableStateOf(isSmallSection) }
    val rotationState by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f
    )

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Section Header
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (!isSmallSection) {
                    Modifier.clickable { expanded = !expanded }
                } else {
                    Modifier
                }),
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dataElement,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (!isSmallSection) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand/Collapse",
                        modifier = Modifier.rotate(rotationState)
                    )
                }
            }
        }

        // Section Content
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                values.forEach { dataValue ->
                    DataValueField(
                        dataValue = dataValue,
                        onValueChange = { onValueChange(it, dataValue) }
                    )
                }
            }
        }
    }
}

@Composable
fun DataValueField(
    dataValue: DataValue,
    onValueChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = dataValue.categoryOptionCombo,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = dataValue.value ?: "",
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            isError = dataValue.validationState == ValidationState.ERROR,
            keyboardOptions = when {
                dataValue.dataElement.contains("AGE", ignoreCase = true) ->
                    KeyboardOptions(keyboardType = KeyboardType.Number)
                else -> KeyboardOptions.Default
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = when (dataValue.validationState) {
                    ValidationState.VALID -> MaterialTheme.colorScheme.primary
                    ValidationState.ERROR -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.outline
                }
            )
        )

        // Optional comment field
        if (dataValue.comment != null) {
            Text(
                text = dataValue.comment,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}