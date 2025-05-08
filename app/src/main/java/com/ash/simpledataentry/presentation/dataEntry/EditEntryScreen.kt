package com.ash.simpledataentry.presentation.dataEntry

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ash.simpledataentry.domain.model.*
import com.ash.simpledataentry.presentation.core.BaseScreen

@Composable
fun EditEntryScreen(
    viewModel: DataEntryViewModel,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val title = "${state.datasetName} - ${state.period} - ${state.attributeOptionComboName}"

    BaseScreen(
        title = title,
        navController = navController
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else if (state.dataValues.isEmpty()) {
                Text(
                    text = "No data elements found for this dataset/period/org unit.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                // Group data values by their sections and category combinations
                val groupedValues = state.dataValues.groupBy { it.sectionName }
                    .mapValues { (_, values) ->
                        values.groupBy { it.categoryOptionCombo }
                    }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupedValues.forEach { (sectionName, categoryGroups) ->
                        item {
                            DataElementSection(
                                sectionName = sectionName,
                                categoryGroups = categoryGroups,
                                isExpanded = sectionName in state.expandedSections,
                                expandedCategoryGroups = state.expandedCategoryGroups,
                                onToggleSection = { viewModel.toggleSection(sectionName) },
                                onToggleCategoryGroup = { categoryGroup ->
                                    viewModel.toggleCategoryGroup(sectionName, categoryGroup)
                                },
                                onValueChange = { value, dataValue ->
                                    viewModel.updateCurrentValue(value)
                                    viewModel.saveCurrentValue()
                                }
                            )
                        }
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

            // Validation message
            state.validationMessage?.let { message ->
                Text(
                    text = message,
                    color = when (state.validationState) {
                        ValidationState.ERROR -> MaterialTheme.colorScheme.error
                        ValidationState.WARNING -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                        ValidationState.VALID -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataElementSection(
    sectionName: String,
    categoryGroups: Map<String, List<DataValue>>,
    isExpanded: Boolean,
    expandedCategoryGroups: Set<String>,
    onToggleSection: () -> Unit,
    onToggleCategoryGroup: (String) -> Unit,
    onValueChange: (String, DataValue) -> Unit
) {
    val sectionRotationState by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f
    )
    
    var selectedCategory by remember { mutableStateOf("") }
    var expandedFilter by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleSection() },
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = sectionName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand/Collapse Section",
                    modifier = Modifier.rotate(sectionRotationState)
                )
            }
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp)
            ) {
                // Category Filter Dropdown
                if (categoryGroups.size > 1) {
                    ExposedDropdownMenuBox(
                        expanded = expandedFilter,
                        onExpandedChange = { expandedFilter = !expandedFilter }
                    ) {
                        OutlinedTextField(
                            value = if (selectedCategory.isEmpty()) "All Categories" else selectedCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Filter by Category") },
                            trailingIcon = { 
                                if (selectedCategory.isNotEmpty()) {
                                    IconButton(
                                        onClick = { 
                                            selectedCategory = ""
                                            expandedFilter = false
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear Filter"
                                        )
                                    }
                                } else {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFilter)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedFilter,
                            onDismissRequest = { expandedFilter = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Categories") },
                                onClick = {
                                    selectedCategory = ""
                                    expandedFilter = false
                                }
                            )
                            categoryGroups.keys.forEach { category ->
                                val categoryName = categoryGroups[category]?.firstOrNull()?.categoryOptionComboName ?: category
                                DropdownMenuItem(
                                    text = { Text(categoryName) },
                                    onClick = {
                                        selectedCategory = categoryName
                                        expandedFilter = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Display data values based on selected category
                if (selectedCategory.isEmpty()) {
                    // Show all categories
                    categoryGroups.forEach { (categoryGroup, values) ->
                        if (categoryGroup == "default") {
                            // For default category, show values directly without a header
                            values.forEach { dataValue ->
                                DataValueField(
                                    dataValue = dataValue,
                                    onValueChange = { onValueChange(it, dataValue) }
                                )
                            }
                        } else {
                            CategoryGroup(
                                categoryGroup = values.firstOrNull()?.categoryOptionComboName ?: categoryGroup,
                                values = values,
                                isExpanded = "$sectionName:$categoryGroup" in expandedCategoryGroups,
                                onToggleExpand = { onToggleCategoryGroup(categoryGroup) },
                                onValueChange = onValueChange
                            )
                        }
                    }
                } else {
                    // Show only selected category
                    categoryGroups.entries.find { (_, values) -> 
                        values.firstOrNull()?.categoryOptionComboName == selectedCategory 
                    }?.let { (categoryGroup, values) ->
                        if (categoryGroup == "default") {
                            values.forEach { dataValue ->
                                DataValueField(
                                    dataValue = dataValue,
                                    onValueChange = { onValueChange(it, dataValue) }
                                )
                            }
                        } else {
                            CategoryGroup(
                                categoryGroup = values.firstOrNull()?.categoryOptionComboName ?: selectedCategory,
                                values = values,
                                isExpanded = true,
                                onToggleExpand = { },
                                onValueChange = onValueChange
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryGroup(
    categoryGroup: String,
    values: List<DataValue>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onValueChange: (String, DataValue) -> Unit
) {
    val categoryRotationState by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f
    )

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() },
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.small
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = categoryGroup,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand/Collapse Category",
                    modifier = Modifier.rotate(categoryRotationState)
                )
            }
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataValueField(
    dataValue: DataValue,
    onValueChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dataValue.dataElementName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (dataValue.isRequired) {
                Text(
                    text = "*",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        when (dataValue.dataEntryType) {
            DataEntryType.NUMBER, 
            DataEntryType.INTEGER,
            DataEntryType.POSITIVE_INTEGER,
            DataEntryType.NEGATIVE_INTEGER -> {
                OutlinedTextField(
                    value = dataValue.value ?: "",
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = dataValue.validationState == ValidationState.ERROR,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = when (dataValue.validationState) {
                            ValidationState.ERROR -> MaterialTheme.colorScheme.error
                            ValidationState.WARNING -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                            ValidationState.VALID -> MaterialTheme.colorScheme.primary
                        }
                    ),
                    textStyle = MaterialTheme.typography.bodySmall
                )
            }
            DataEntryType.YES_NO -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        onClick = { onValueChange("true") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (dataValue.value == "true") 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Yes", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = { onValueChange("false") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (dataValue.value == "false") 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("No", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            DataEntryType.PERCENTAGE -> {
                OutlinedTextField(
                    value = dataValue.value ?: "",
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    suffix = { Text("%", style = MaterialTheme.typography.bodySmall) },
                    textStyle = MaterialTheme.typography.bodySmall
                )
            }
            else -> {
                OutlinedTextField(
                    value = dataValue.value ?: "",
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Show validation messages
        dataValue.validationRules.forEach { rule ->
            if (dataValue.validationState == ValidationState.ERROR) {
                Text(
                    text = rule.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}