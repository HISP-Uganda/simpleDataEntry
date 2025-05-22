@file:OptIn(ExperimentalMaterial3Api::class)

package com.ash.simpledataentry.presentation.dataEntry

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ash.simpledataentry.domain.model.*
import com.ash.simpledataentry.presentation.core.BaseScreen
import kotlinx.coroutines.delay
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import org.hisp.dhis.mobile.ui.designsystem.component.ButtonStyle
import org.hisp.dhis.mobile.ui.designsystem.component.SupportingTextData
import org.hisp.dhis.mobile.ui.designsystem.component.SupportingTextState
import org.hisp.dhis.mobile.ui.designsystem.component.InputShellState
import androidx.compose.ui.text.input.TextFieldValue
import org.hisp.dhis.mobile.ui.designsystem.component.ColorStyle
import org.hisp.dhis.mobile.ui.designsystem.component.InputText
import org.hisp.dhis.mobile.ui.designsystem.component.Button
import org.hisp.dhis.mobile.ui.designsystem.component.InputNumber

@Composable
fun EditEntryScreen(
    viewModel: DataEntryViewModel = hiltViewModel(),
    navController: NavController
) {
    val state by viewModel.state.collectAsState()
    var isLoading by remember { mutableStateOf(true) }

    // Show loading state immediately
    LaunchedEffect(Unit) {
        // Short delay to ensure smooth transition
        delay(100)
        isLoading = false
    }

    BaseScreen(
        title = "${java.net.URLDecoder.decode(state.datasetName, "UTF-8")} - ${state.period.replace("Period(id=", "").replace(")", "")} - ${state.attributeOptionComboName}",
        navController = navController
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading || state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
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
                            text = "Loading form...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    if (state.dataValues.isEmpty()) {
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
                            items(groupedValues.toList()) { (sectionName, values) ->
                                // For each section, get the first data value's categoryComboUid
                                val firstDataValue = values.firstOrNull()
                                val comboUid = firstDataValue?.categoryOptionCombo
                                val structure = comboUid?.let { categoryComboStructures[it] }
                                
                                SectionContent(
                                    sectionName = sectionName,
                                    isExpanded = sectionName == state.expandedSection,
                                    onToggleSection = { viewModel.toggleSection(sectionName) },
                                    structure = structure,
                                    values = values,
                                    expandedCategoryGroup = state.expandedCategoryGroup,
                                    onToggleCategoryGroup = { section, categoryGroup ->
                                        viewModel.toggleCategoryGroup(section, categoryGroup)
                                    },
                                    onValueChange = { value, dataValue ->
                                        viewModel.updateCurrentValue(value)
                                        viewModel.saveCurrentValue()
                                    },
                                    optionUidsToComboUid = state.optionUidsToComboUid[comboUid] ?: emptyMap()
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
    }
}

@Composable
private fun SectionContent(
    sectionName: String,
    isExpanded: Boolean,
    onToggleSection: () -> Unit,
    structure: List<Pair<String, List<Pair<String, String>>>>?,
    values: List<DataValue>,
    expandedCategoryGroup: String?,
    onToggleCategoryGroup: (String, String) -> Unit,
    onValueChange: (String, DataValue) -> Unit,
    optionUidsToComboUid: Map<Set<String>, String>
) {
    Column {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleSection),
            color = Color(0xFF40C2F5),
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
                    modifier = Modifier.rotate(if (isExpanded) 180f else 0f)
                )
            }
        }
        AnimatedVisibility(visible = isExpanded) {
            Column {
                if (structure != null && structure.size >= 2) {
                    DataElementGridSection(
                        sectionName = sectionName,
                        rowCategory = structure[0],
                        colCategory = structure[1],
                        dataValues = values,
                        onValueChange = onValueChange,
                        optionUidsToComboUid = optionUidsToComboUid
                    )
                } else {
                    DataElementSection(
                        sectionName = sectionName,
                        categoryGroups = values.groupBy { it.categoryOptionCombo },
                        isExpanded = true,
                        expandedCategoryGroup = expandedCategoryGroup,
                        onToggleSection = {},
                        onToggleCategoryGroup = onToggleCategoryGroup,
                        onValueChange = onValueChange
                    )
                }
            }
        }
    }
}

@Composable
fun DataElementSection(
    sectionName: String,
    categoryGroups: Map<String, List<DataValue>>,
    isExpanded: Boolean,
    expandedCategoryGroup: String?,
    onToggleSection: () -> Unit,
    onToggleCategoryGroup: (String, String) -> Unit,
    onValueChange: (String, DataValue) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("") }
    var expandedFilter by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 16.dp)
    ) {
        if (categoryGroups.size > 1) {
            CategoryFilter(
                categoryGroups = categoryGroups,
                selectedCategory = selectedCategory,
                onCategorySelected = { selectedCategory = it },
                expanded = expandedFilter,
                onExpandedChange = { expandedFilter = it }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        CategoryContent(
            categoryGroups = categoryGroups,
            selectedCategory = selectedCategory,
            sectionName = sectionName,
            expandedCategoryGroup = expandedCategoryGroup,
            onToggleCategoryGroup = onToggleCategoryGroup,
            onValueChange = onValueChange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilter(
    categoryGroups: Map<String, List<DataValue>>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange
    ) {
        OutlinedTextField(
            value = if (selectedCategory.isEmpty()) "All Categories" else (categoryGroups[selectedCategory]?.firstOrNull()?.categoryOptionComboName ?: selectedCategory),
            onValueChange = {},
            readOnly = true,
            label = { Text("Filter by Category") },
            trailingIcon = { 
                if (selectedCategory.isNotEmpty()) {
                    IconButton(
                        onClick = { 
                            onCategorySelected("")
                            onExpandedChange(false)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear Filter"
                        )
                    }
                } else {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            DropdownMenuItem(
                text = { Text("All Categories") },
                onClick = {
                    onCategorySelected("")
                    onExpandedChange(false)
                }
            )
            categoryGroups.keys.forEach { categoryUid ->
                val categoryName = categoryGroups[categoryUid]?.firstOrNull()?.categoryOptionComboName ?: categoryUid
                DropdownMenuItem(
                    text = { Text(categoryName) },
                    onClick = {
                        onCategorySelected(categoryUid)
                        onExpandedChange(false)
                    }
                )
            }
        }
    }
}

@Composable
private fun CategoryContent(
    categoryGroups: Map<String, List<DataValue>>,
    selectedCategory: String,
    sectionName: String,
    expandedCategoryGroup: String?,
    onToggleCategoryGroup: (String, String) -> Unit,
    onValueChange: (String, DataValue) -> Unit
) {
    if (selectedCategory.isEmpty()) {
        categoryGroups.forEach { (categoryGroup, values) ->
            if (categoryGroup == "default") {
                // Always open, no title
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
                    isExpanded = ("$sectionName:$categoryGroup" == expandedCategoryGroup),
                    onToggleExpand = { onToggleCategoryGroup(sectionName, categoryGroup) },
                    onValueChange = onValueChange
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    } else {
        categoryGroups[selectedCategory]?.let { values ->
            val categoryGroup = selectedCategory
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

@Composable
fun CategoryGroup(
    categoryGroup: String,
    values: List<DataValue>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onValueChange: (String, DataValue) -> Unit
) {
    if (categoryGroup == "default") {
        // Always open, no title
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            values.forEach { dataValue ->
                DataValueField(
                    dataValue = dataValue,
                    onValueChange = { onValueChange(it, dataValue) }
                )
            }
        }
        return
    }
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
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(
                        bounded = true,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    onClick = onToggleExpand
                ),
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
        when (dataValue.dataEntryType) {
            DataEntryType.YES_NO -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        text = "Yes",
                        style = if (dataValue.value == "true") ButtonStyle.FILLED else ButtonStyle.OUTLINED,
                        colorStyle = ColorStyle.DEFAULT,
                        modifier = Modifier.weight(1f),
                        onClick = { onValueChange("true") }
                    )
                    Button(
                        text = "No",
                        style = if (dataValue.value == "false") ButtonStyle.FILLED else ButtonStyle.OUTLINED,
                        colorStyle = ColorStyle.DEFAULT,
                        modifier = Modifier.weight(1f),
                        onClick = { onValueChange("false") }
                    )
                }
            }
            DataEntryType.NUMBER, 
            DataEntryType.INTEGER,
            DataEntryType.POSITIVE_INTEGER,
            DataEntryType.NEGATIVE_INTEGER -> {
                InputNumber(
                    title = dataValue.dataElementName,
                    state = when (dataValue.validationState) {
                        ValidationState.ERROR -> InputShellState.ERROR
                        ValidationState.WARNING -> InputShellState.WARNING
                        ValidationState.VALID -> InputShellState.UNFOCUSED
                    },
                    supportingText = listOf(
                        SupportingTextData(
                            text = dataValue.validationRules.firstOrNull()?.message ?: "",
                            state = when (dataValue.validationState) {
                                ValidationState.ERROR -> SupportingTextState.ERROR
                                ValidationState.WARNING -> SupportingTextState.WARNING
                                ValidationState.VALID -> SupportingTextState.DEFAULT
                            }
                        )
                    ),
                    inputTextFieldValue = TextFieldValue(dataValue.value ?: ""),
                    //isRequiredField = dataValue.isRequired,
                    onValueChanged = { newValue -> 
                        onValueChange(newValue?.text ?: "")
                    },
                //    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            DataEntryType.PERCENTAGE -> {
                InputText(
                    title = dataValue.dataElementName,
                    state = when (dataValue.validationState) {
                        ValidationState.ERROR -> InputShellState.ERROR
                        ValidationState.WARNING -> InputShellState.WARNING
                        ValidationState.VALID -> InputShellState.UNFOCUSED
                    },
                    supportingText = listOf(
                        SupportingTextData(
                            text = dataValue.validationRules.firstOrNull()?.message ?: "",
                            state = when (dataValue.validationState) {
                                ValidationState.ERROR -> SupportingTextState.ERROR
                                ValidationState.WARNING -> SupportingTextState.WARNING
                                ValidationState.VALID -> SupportingTextState.DEFAULT
                            }
                        )
                    ),
                    inputTextFieldValue = TextFieldValue(dataValue.value ?: ""),
                    //isRequiredField = dataValue.isRequired,
                    onValueChanged = { newValue -> 
                        onValueChange(newValue?.text ?: "")
                    },
                //    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                //    suffix = { Text("%") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            else -> {
                InputText(
                    title = dataValue.dataElementName,
                    state = when (dataValue.validationState) {
                        ValidationState.ERROR -> InputShellState.ERROR
                        ValidationState.WARNING -> InputShellState.WARNING
                        ValidationState.VALID -> InputShellState.UNFOCUSED
                    },
                    supportingText = listOf(
                        SupportingTextData(
                            text = dataValue.validationRules.firstOrNull()?.message ?: "",
                            state = when (dataValue.validationState) {
                                ValidationState.ERROR -> SupportingTextState.ERROR
                                ValidationState.WARNING -> SupportingTextState.WARNING
                                ValidationState.VALID -> SupportingTextState.DEFAULT
                            }
                        )
                    ),
                    inputTextFieldValue = TextFieldValue(dataValue.value ?: ""),
                    //isRequiredField = dataValue.isRequired,
                    onValueChanged = { newValue -> 
                        onValueChange(newValue?.text ?: "")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun DataElementGridSection(
    sectionName: String,
    rowCategory: Pair<String, List<Pair<String, String>>>,
    colCategory: Pair<String, List<Pair<String, String>>>,
    dataValues: List<DataValue>,
    onValueChange: (String, DataValue) -> Unit,
    optionUidsToComboUid: Map<Set<String>, String>
) {
    // Helper to detect gender categories
    fun isGenderCategory(category: Pair<String, List<Pair<String, String>>>): Boolean {
        val genderKeywords = listOf("gender", "sex", "male", "female")
        val name = category.first.lowercase()
        val options = category.second.map { it.second.lowercase() }
        return genderKeywords.any { k ->
            name.contains(k) || options.any { it.contains(k) }
        }
    }

    // Force gender category to be the columns (smallerCat)
    val (smallerCat, largerCat) = when {
        isGenderCategory(rowCategory) -> rowCategory to colCategory
        isGenderCategory(colCategory) -> colCategory to rowCategory
        rowCategory.second.size <= colCategory.second.size -> rowCategory to colCategory
        else -> colCategory to rowCategory
    }

    var selectedFilter by remember { mutableStateOf<String?>(null) }
    var expandedFilter by remember { mutableStateOf(false) }
    val expandedStates = remember { mutableStateMapOf<String, Boolean>() }

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
            val expanded = expandedStates.getOrPut(largeOpt.first) { false }
            
            // Skip if filter is active and this option doesn't match
            if (selectedFilter != null && largeOpt.second != selectedFilter) {
                return@forEach
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(
                            bounded = true,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        onClick = { expandedStates[largeOpt.first] = !expanded }
                    ),
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

private fun validateDataValue(dataValue: DataValue): ValidationState {
    // Check validation rules
    for (rule in dataValue.validationRules) {
        when (rule.rule) {
            "number" -> {
                if (dataValue.value != null && dataValue.value.isNotBlank()) {
                    if (!dataValue.value.matches(Regex("^-?\\d*\\.?\\d+$"))) {
                        return ValidationState.ERROR
                    }
                }
            }
            "coordinates" -> {
                if (dataValue.value != null && dataValue.value.isNotBlank()) {
                    if (!dataValue.value.matches(Regex("^-?\\d+\\.\\d+,-?\\d+\\.\\d+$"))) {
                        return ValidationState.ERROR
                    }
                }
            }
        }
    }
    return ValidationState.VALID
}