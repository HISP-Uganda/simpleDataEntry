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
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.hisp.dhis.mobile.ui.designsystem.component.Button
import org.hisp.dhis.mobile.ui.designsystem.component.ButtonStyle
import org.hisp.dhis.mobile.ui.designsystem.component.ColorStyle
import org.hisp.dhis.mobile.ui.designsystem.component.InputNumber
import org.hisp.dhis.mobile.ui.designsystem.component.InputShellState
import org.hisp.dhis.mobile.ui.designsystem.component.InputText
import org.hisp.dhis.mobile.ui.designsystem.component.SupportingTextData
import org.hisp.dhis.mobile.ui.designsystem.component.SupportingTextState
import com.ash.simpledataentry.domain.model.*
import com.ash.simpledataentry.presentation.core.BaseScreen
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Sync
import androidx.compose.ui.platform.LocalContext
import kotlin.Pair
import androidx.compose.runtime.Composable

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun EditEntryScreen(
    viewModel: DataEntryViewModel = hiltViewModel(),
    navController: NavController,
    datasetId: String,
    datasetName: String,
    period: String,
    orgUnit: String,
    attributeOptionCombo: String
) {
    val state by viewModel.state.collectAsState()
    var lastLoadedParams by remember { mutableStateOf(Quadruple("", "", "", "")) }
    val currentParams = Quadruple(datasetId, period, orgUnit, attributeOptionCombo)
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val showSaveDialog = remember { mutableStateOf(false) }
    val pendingNavAction = remember { mutableStateOf<(() -> Unit)?>(null) }
    val context = LocalContext.current

    // Detect unsaved changes: compare ViewModel dirtyDataValues or drafts
    val hasUnsavedChanges = remember(state.dataValues) {
        // If any dataValue has a value different from its original (e.g., not null and not equal to the last saved value)
        state.dataValues.any { it.value != null && it.value != it.comment && it.value != "" } // Simplified; ideally compare to original loaded values
    }

    // Intercept back press: block if saving or save was pressed
    BackHandler(enabled = hasUnsavedChanges && !state.saveInProgress && !viewModel.wasSavePressed()) {
        showSaveDialog.value = true
        pendingNavAction.value = { navController.popBackStack() }
    }

    // Intercept navigation via top bar back button: block if saving
    val baseScreenNavIcon: @Composable (() -> Unit) = {
        IconButton(onClick = {
            if (state.saveInProgress || viewModel.wasSavePressed()) {
                // Block navigation while saving
                return@IconButton
            }
            if (hasUnsavedChanges) {
                showSaveDialog.value = true
                pendingNavAction.value = { navController.popBackStack() }
            } else {
                navController.popBackStack()
            }
        }) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back"
            )
        }
    }

    // Save confirmation dialog: only show if not saving and save not pressed
    if (showSaveDialog.value && !state.saveInProgress && !viewModel.wasSavePressed()) {
        AlertDialog(
            onDismissRequest = { showSaveDialog.value = false },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved changes. Save before leaving?") },
            confirmButton = {
                TextButton(onClick = {
                    showSaveDialog.value = false
                    viewModel.saveAllDataValues(context)
                    pendingNavAction.value?.invoke()
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showSaveDialog.value = false
                        // Discard: clear drafts/dirty values, then navigate
                        viewModel.clearDraftsForCurrentInstance()
                        pendingNavAction.value?.invoke()
                    }) { Text("Discard") }
                    TextButton(onClick = {
                        showSaveDialog.value = false
                    }) { Text("Cancel") }
                }
            }
        )
    }

    LaunchedEffect(currentParams) {
        if (state.dataValues.isEmpty() || lastLoadedParams != currentParams) {
            viewModel.loadDataValues(
                datasetId = datasetId,
                datasetName = datasetName,
                period = period,
                orgUnitId = orgUnit,
                attributeOptionCombo = attributeOptionCombo,
                isEditMode = true
            )
            lastLoadedParams = currentParams
        }
    }
    fun manualRefresh() {
        viewModel.loadDataValues(
            datasetId = datasetId,
            datasetName = datasetName,
            period = period,
            orgUnitId = orgUnit,
            attributeOptionCombo = attributeOptionCombo,
            isEditMode = true
        )
        lastLoadedParams = currentParams
    }
    // Show Snackbar on save result
    LaunchedEffect(state.saveResult) {
        state.saveResult?.let {
            if (it.isSuccess) {
                snackbarHostState.showSnackbar("All data saved successfully.")
            } else {
                snackbarHostState.showSnackbar(it.exceptionOrNull()?.message ?: "Failed to save some fields.")
            }
            viewModel.resetSaveFeedback()
        }
    }
    // Resolve attribute option combo display name for the title
    val attrComboName = state.attributeOptionCombos.find { it.first == attributeOptionCombo }?.second ?: attributeOptionCombo
    BaseScreen(
        title = "${java.net.URLDecoder.decode(datasetName, "UTF-8")} - ${period.replace("Period(id=", "").replace(")", "")} - $attrComboName",
        navController = navController,
        navigationIcon = baseScreenNavIcon,
        actions = {
            // Add sync button to top bar
            IconButton(
                onClick = {
                    coroutineScope.launch {
                        viewModel.syncCurrentEntryForm()
                    }
                },
                enabled = !state.isLoading
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (state.isLoading) {
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
                        val groupedValues = state.dataValues.groupBy { it.sectionName }
                        val categoryComboStructures = state.categoryComboStructures
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(groupedValues.toList(), key = { it.first }) { (sectionName, values) ->
                                val firstDataValue = values.firstOrNull()
                                val comboUid = firstDataValue?.categoryOptionCombo
                                val structure = comboUid?.let { categoryComboStructures[it] }
                                val isSectionExpanded = state.isExpandedSections[sectionName] == true
                                // Count fields with a value
                                val filledCount = values.count { !it.value.isNullOrBlank() }
                                val totalCount = values.size
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            // Only one section open at a time
                                            state.isExpandedSections.keys.forEach { key ->
                                                if (key != sectionName && state.isExpandedSections[key] == true) {
                                                    viewModel.toggleSection(key)
                                                }
                                            }
                                            viewModel.toggleSection(sectionName)
                                        },
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = sectionName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "($filledCount/$totalCount)",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Expand/Collapse Section",
                                            modifier = Modifier.rotate(if (isSectionExpanded) 180f else 0f)
                                        )
                                    }
                                }
                                AnimatedVisibility(visible = isSectionExpanded) {
                                    SectionContent(
                                        sectionName = sectionName,
                                        structure = structure,
                                        values = values,
                                        expandedCategoryGroup = state.expandedCategoryGroup,
                                        onToggleCategoryGroup = { section, categoryGroup ->
                                            viewModel.toggleCategoryGroup(section, categoryGroup)
                                        },
                                        onValueChange = { value, dataValue ->
                                            viewModel.updateCurrentValue(value, dataValue.dataElement, dataValue.categoryOptionCombo)
                                        },
                                        optionUidsToComboUid = state.optionUidsToComboUid[comboUid] ?: emptyMap(),
                                        viewModel = viewModel
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
                // FAB and Snackbar
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                    FloatingActionButton(
                        onClick = { viewModel.saveAllDataValues(context) },
                        //enabled = !state.saveInProgress,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        if (state.saveInProgress) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Save, contentDescription = "Save")
                        }
                    }
                    SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
    // Show Snackbar on sync error
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
        }
    }
}

@Composable
private fun SectionContent(
    sectionName: String,
    structure: List<Pair<String, List<Pair<String, String>>>>?,
    values: List<DataValue>,
    expandedCategoryGroup: String?,
    onToggleCategoryGroup: (String, String) -> Unit,
    onValueChange: (String, DataValue) -> Unit,
    optionUidsToComboUid: Map<Set<String>, String>,
    viewModel: DataEntryViewModel
) {
    val state by viewModel.state.collectAsState()
    val categoryComboStructures = state.categoryComboStructures
    val optionUidsToComboUidMap = state.optionUidsToComboUid
    val dataElements = values.map { it.dataElement to it.dataElementName }.distinct()
            val categoryList = structure ?: emptyList()
    // Debug logs
    android.util.Log.d("SectionContent", "sectionName=$sectionName, categoryList.size=${categoryList.size}")
            when (categoryList.size) {
        0 -> { // No categories: just list data elements
            Column(modifier = Modifier.fillMaxWidth()) {
                dataElements.forEach { (dataElement, dataElementName) ->
                    val dataValue = values.find { it.dataElement == dataElement }
                    DataElementRow(
                        dataElementName = dataElementName,
                        fields = listOf(dataValue),
                        onValueChange = onValueChange
                    )
                            }
                    }
                }
        1 -> { // One category: accordion per option, rows are data elements
                    val catName = categoryList[0].first
                    val options = categoryList[0].second
            android.util.Log.d("SectionContent", "[1-cat] $catName options: ${options.map { it.second }}")
            android.util.Log.d("SectionContent", "[1-cat] optionUidsToComboUid keys: ${optionUidsToComboUid.keys}")
            options.forEach { (optUid, optName) ->
                val cocUid = optionUidsToComboUid.entries.find { it.key == setOf(optUid) }?.value
                val expandedKey = "$sectionName:$optName"
                CategoryAccordion(
                    header = optName,
                    expanded = expandedCategoryGroup == expandedKey,
                    onToggleExpand = {
                        // Only one open at a time: set expandedCategoryGroup to this, or null if already open
                        if (expandedCategoryGroup == expandedKey) {
                            onToggleCategoryGroup(sectionName, "") // close all
                        } else {
                            onToggleCategoryGroup(sectionName, optName)
                        }
                    }
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        dataElements.forEach { (dataElement, dataElementName) ->
                            val dataValue = values.find { it.dataElement == dataElement && it.categoryOptionCombo == cocUid }
                            DataElementRow(
                                dataElementName = dataElementName,
                                fields = listOf(dataValue),
                                onValueChange = onValueChange
                            )
                        }
                    }
                }
            }
        }
        2 -> { // Two categories: outer accordion for the category with more than two options (or non-gender if both have two)
            val (catA, catB) = categoryList
                    val genderKeywords = listOf("sex", "gender")
            val isCatAGender = genderKeywords.any { catA.first.lowercase().contains(it) }
            val isCatBGender = genderKeywords.any { catB.first.lowercase().contains(it) }
            val catAOptions = catA.second
            val catBOptions = catB.second
            android.util.Log.d("SectionContent", "[2-cat] catA: ${catA.first} options: ${catAOptions.map { it.second }}")
            android.util.Log.d("SectionContent", "[2-cat] catB: ${catB.first} options: ${catBOptions.map { it.second }}")
            android.util.Log.d("SectionContent", "[2-cat] optionUidsToComboUid keys: ${optionUidsToComboUid.keys}")
            // Decide which is outer (accordion) and which is columns
            val (outerCat, innerCat, outerIsGender) = when {
                catAOptions.size > 2 -> Triple(catA, catB, isCatAGender)
                catBOptions.size > 2 -> Triple(catB, catA, isCatBGender)
                isCatAGender -> Triple(catB, catA, false)
                isCatBGender -> Triple(catA, catB, false)
                else -> Triple(catA, catB, false)
            }
            val outerCatName = outerCat.first
            val outerOptions = outerCat.second
            val innerCatName = innerCat.first
            val innerOptions = innerCat.second
            Column(modifier = Modifier.fillMaxWidth()) {
                outerOptions.forEach { (outerOptUid, outerOptName) ->
                    val expandedKey = "$sectionName:$outerOptName"
                    android.util.Log.d("SectionContent", "[2-cat] OUTER: $outerCatName: $outerOptName (UID: $outerOptUid)")
                    CategoryAccordion(
                        header = outerOptName,
                        expanded = expandedCategoryGroup == expandedKey,
                        onToggleExpand = {
                            if (expandedCategoryGroup == expandedKey) {
                                onToggleCategoryGroup(sectionName, "")
                            } else {
                                onToggleCategoryGroup(sectionName, outerOptName)
                    }
                        }
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Spacer(modifier = Modifier.width(120.dp))
                                innerOptions.forEach { (_, innerOptName) ->
                                    Text(
                                        text = innerOptName,
                                        modifier = Modifier.width(100.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            dataElements.forEach { (dataElement, dataElementName) ->
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = dataElementName,
                                        modifier = Modifier.width(120.dp),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    innerOptions.forEach { (innerOptUid, innerOptName) ->
                                        val keySet = setOf(outerOptUid, innerOptUid)
                                        val cocUid = optionUidsToComboUid.entries.find { it.key == keySet }?.value
                                        android.util.Log.d("SectionContent", "[2-cat] Pair: ($outerOptName/$outerOptUid, $innerOptName/$innerOptUid) -> setOf($outerOptUid, $innerOptUid), cocUid=$cocUid")
                                        val dataValue = values.find {
                                            it.dataElement == dataElement && it.categoryOptionCombo == cocUid
                                        }
                                        android.util.Log.d("SectionContent", "[2-cat] DataValue for dataElement=$dataElement, cocUid=$cocUid: ${if (dataValue != null) "FOUND" else "NOT FOUND"}")
                                Box(modifier = Modifier.weight(1f).padding(4.dp)) {
                                            if (dataValue != null) {
                                    DataValueField(
                                                    dataValue = dataValue,
                                                    onValueChange = { value -> onValueChange(value, dataValue) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
        else -> { // >2 categories: fallback to grouped list
            Column(modifier = Modifier.fillMaxWidth()) {
                dataElements.forEach { (dataElement, dataElementName) ->
                    val dataValues = values.filter { it.dataElement == dataElement }
                    DataElementRow(
                        dataElementName = dataElementName,
                        fields = dataValues,
                        onValueChange = onValueChange
                                    )
                                }
                            }
                        }
                    }
                }

@Composable
private fun DataElementRow(
    dataElementName: String,
    fields: List<DataValue?>,
    onValueChange: (String, DataValue) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = dataElementName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        fields.filterNotNull().forEach { dataValue ->
                        DataValueField(
                dataValue = dataValue,
                onValueChange = { value -> onValueChange(value, dataValue) }
            )
        }
    }
}

@Composable
private fun CategoryAccordion(
    header: String,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand),
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.small
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = header,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand/Collapse Category",
                    modifier = Modifier.rotate(if (expanded) 180f else 0f)
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun DataElementGridRow(
    dataElementName: String,
    colCategory: Pair<String, List<Pair<String, String>>>,
    rowCategory: Pair<String, List<Pair<String, String>>>,
    dataValues: List<DataValue>,
    comboMap: Map<Set<String>, String>,
    onValueChange: (String, DataValue) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = dataElementName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.width(100.dp)) // For row header
            colCategory.second.forEach { colOpt ->
                Text(
                    text = colOpt.second,
                    modifier = Modifier.width(100.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        rowCategory.second.forEach { rowOpt ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = rowOpt.second,
                    modifier = Modifier.width(100.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
                colCategory.second.forEach { colOpt ->
                    val cocUid = comboMap.entries.find { it.key == setOf(rowOpt.first, colOpt.first) || it.key == setOf(colOpt.first, rowOpt.first) }?.value
                    val dataValue = dataValues.find { it.categoryOptionCombo == cocUid }
                    Box(modifier = Modifier.width(100.dp).padding(4.dp)) {
                        if (dataValue != null) {
                            DataValueField(
                                dataValue = dataValue,
                                onValueChange = { value -> onValueChange(value, dataValue) }
                        )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DataElementSection(
    sectionName: String,
    categoryGroups: Map<String, List<DataValue>>,
    isExpandedSections: Map<String, Boolean>,
    onToggleCategoryGroup: (String, String) -> Unit,
    onValueChange: (String, DataValue) -> Unit
) {
    // Add logging for troubleshooting
    android.util.Log.d("EditEntryScreen", "DataElementSection: sectionName=$sectionName, categoryGroups=${categoryGroups.map { it.key to it.value.map { v -> v.dataElementName } }}")
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
            isExpandedSections = isExpandedSections,
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
    isExpandedSections: Map<String, Boolean>,
    onToggleCategoryGroup: (String, String) -> Unit,
    onValueChange: (String, DataValue) -> Unit
) {
    if (selectedCategory.isEmpty()) {
        categoryGroups.forEach { (categoryGroup, values) ->
            key("${sectionName}_$categoryGroup") {
                if (categoryGroup == "default" || categoryGroup.isBlank()) {
                    values.forEach { dataValue ->
                        key("${dataValue.dataElement}_${dataValue.categoryOptionCombo}") {
                            DataValueField(
                                dataValue = dataValue,
                                onValueChange = { value -> onValueChange(value, dataValue) }
                            )
                        }
                    }
                } else {
                    CategoryGroup(
                        categoryGroup = values.firstOrNull()?.categoryOptionComboName ?: categoryGroup,
                        values = values,
                        isExpanded = isExpandedSections["$sectionName:$categoryGroup"] == true,
                        onToggleExpand = { onToggleCategoryGroup(sectionName, categoryGroup) },
                        onValueChange = onValueChange
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    } else {
        categoryGroups[selectedCategory]?.let { values ->
            val categoryGroup = selectedCategory
            key("${sectionName}_$categoryGroup") {
                if (categoryGroup == "default" || categoryGroup.isBlank()) {
                    values.forEach { dataValue ->
                        key("${dataValue.dataElement}_${dataValue.categoryOptionCombo}") {
                            DataValueField(
                                dataValue = dataValue,
                                onValueChange = { value -> onValueChange(value, dataValue) }
                            )
                        }
                    }
                } else {
                    CategoryGroup(
                        categoryGroup = values.firstOrNull()?.categoryOptionComboName ?: selectedCategory,
                        values = values,
                        isExpanded = isExpandedSections["$sectionName:$categoryGroup"] == true,
                        onToggleExpand = { onToggleCategoryGroup(sectionName, categoryGroup) },
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
    if (categoryGroup == "default" || categoryGroup.isBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            values.forEach { dataValue ->
                key("${dataValue.dataElement}_${dataValue.categoryOptionCombo}") {
                    DataValueField(
                        dataValue = dataValue,
                        onValueChange = { value -> onValueChange(value, dataValue) }
                    )
                }
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
                    key("${dataValue.dataElement}_${dataValue.categoryOptionCombo}") {
                        DataValueField(
                            dataValue = dataValue,
                            onValueChange = { value -> onValueChange(value, dataValue) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DataValueField(
    dataValue: DataValue,
    onValueChange: (String) -> Unit,

) {
    Log.d(
        "DataValueField",
        "Rendering field for dataElement='${dataValue.dataElement}', categoryOptionCombo='${dataValue.categoryOptionCombo}', value='${dataValue.value}'"
    )
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
                    onValueChanged = { newValue -> 
                        onValueChange(newValue?.text ?: "")
                    },
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
                    onValueChanged = { newValue -> 
                        onValueChange(newValue?.text ?: "")
                    },
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
    optionUidsToComboUid: Map<Set<String>, String>,
    viewModel: DataEntryViewModel
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
            val expanded = viewModel.isGridRowExpanded(sectionName, largeOpt.first)
            
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
                        onClick = { viewModel.toggleGridRow(sectionName, largeOpt.first) }
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
                                key("${dataValue.dataElement}_${dataValue.categoryOptionCombo}") {
                                    DataValueField(
                                        dataValue = dataValue,
                                        onValueChange = { value -> onValueChange(value, dataValue) }
                                    )
                                }
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