@file:OptIn(ExperimentalMaterial3Api::class)

package com.ash.simpledataentry.presentation.dataEntry

import android.util.Log
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

    // Add debug logging
    LaunchedEffect(state.categoryComboStructures) {
        Log.d("EditEntryScreen", "Category Combo Structures: ${state.categoryComboStructures}")
    }

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
                val categoryComboStructures = state.categoryComboStructures

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupedValues.forEach { (sectionName, values) ->
                        // For each section, get the first data value's categoryComboUid
                        val firstDataValue = values.firstOrNull()
                        val comboUid = firstDataValue?.categoryOptionCombo
                        val structure = comboUid?.let { categoryComboStructures[it] }
                        
                        // Add debug logging
                        Log.d("EditEntryScreen", "Section: $sectionName")
                        Log.d("EditEntryScreen", "ComboUid: $comboUid")
                        Log.d("EditEntryScreen", "Structure: $structure")
                        
                        item {
                            val isSectionExpanded = sectionName in state.expandedSections
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleSection(sectionName) },
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
                                        modifier = Modifier.rotate(if (isSectionExpanded) 180f else 0f)
                                    )
                                }
                            }
                            AnimatedVisibility(visible = isSectionExpanded) {
                                Column {
                                    if (structure != null && structure.size >= 2) {
                                        DataElementGridSection(
                                            sectionName = sectionName,
                                            rowCategory = structure[0],
                                            colCategory = structure[1],
                                            dataValues = values,
                                            onValueChange = { value, dataValue ->
                                                viewModel.updateCurrentValue(value)
                                                viewModel.saveCurrentValue()
                                            },
                                            optionUidsToComboUid = state.optionUidsToComboUid[comboUid] ?: emptyMap()
                                        )
                                    } else {
                                        DataElementSection(
                                            sectionName = sectionName,
                                            categoryGroups = values.groupBy { it.categoryOptionCombo },
                                            isExpanded = true, // Always expanded inside section accordion
                                            expandedCategoryGroups = state.expandedCategoryGroups,
                                            onToggleSection = {}, // No-op, handled by section accordion
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
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
                    Spacer(modifier = Modifier.height(8.dp))
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
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

// Add a new composable for the grid layout
@Composable
fun DataElementGridSection(
    sectionName: String,
    rowCategory: Pair<String, List<Pair<String, String>>>,
    colCategory: Pair<String, List<Pair<String, String>>>,
    dataValues: List<DataValue>,
    onValueChange: (String, DataValue) -> Unit,
    optionUidsToComboUid: Map<Set<String>, String>
) {
    // Determine which is larger
    val (smallerCat, largerCat) = if (rowCategory.second.size <= colCategory.second.size) {
        rowCategory to colCategory
    } else {
        colCategory to rowCategory
    }

    var selectedFilter by remember { mutableStateOf<String?>(null) }
    var expandedFilter by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Filter dropdown
        ExposedDropdownMenuBox(
            expanded = expandedFilter,
            onExpandedChange = { expandedFilter = !expandedFilter }
        ) {
            OutlinedTextField(
                value = selectedFilter ?: "All",
                onValueChange = {},
                readOnly = true,
                label = { Text("Filter") },
                trailingIcon = { 
                    if (selectedFilter != null) {
                        IconButton(
                            onClick = { 
                                selectedFilter = null
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
                    .padding(bottom = 8.dp)
            )
            ExposedDropdownMenu(
                expanded = expandedFilter,
                onDismissRequest = { expandedFilter = false }
            ) {
                DropdownMenuItem(
                    text = { Text("All") },
                    onClick = {
                        selectedFilter = null
                        expandedFilter = false
                    }
                )
                (smallerCat.second + largerCat.second).map { it.second }.distinct().forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            selectedFilter = option
                            expandedFilter = false
                        }
                    )
                }
            }
        }

        // For each option in the larger category, render an accordion
        largerCat.second.forEach { largeOpt ->
            val expandedStates = remember { mutableStateMapOf<String, Boolean>() }
            val expanded = expandedStates.getOrPut(largeOpt.first) { false }
            
            // Skip if filter is active and this option doesn't match
            if (selectedFilter != null && largeOpt.second != selectedFilter) {
                return@forEach
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clickable { expandedStates[largeOpt.first] = !expanded },
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = largeOpt.second,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand/Collapse Section",
                        modifier = Modifier.rotate(if (expanded) 180f else 0f)
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    smallerCat.second.forEach { smallOpt ->
                        // Skip if filter is active and this option doesn't match
                        if (selectedFilter != null && smallOpt.second != selectedFilter) {
                            return@forEach
                        }

                        val optionUids = setOf(largeOpt.first, smallOpt.first)
                        val comboUid = optionUidsToComboUid[optionUids]
                        val cellDataValues = dataValues.filter { it.categoryOptionCombo == comboUid }
                        
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = smallOpt.second,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            cellDataValues.forEach { dataValue ->
                                DataValueField(
                                    dataValue = dataValue,
                                    onValueChange = { onValueChange(it, dataValue) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}