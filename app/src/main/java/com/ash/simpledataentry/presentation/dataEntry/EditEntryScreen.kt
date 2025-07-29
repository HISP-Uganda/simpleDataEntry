@file:OptIn(ExperimentalMaterial3Api::class)

package com.ash.simpledataentry.presentation.dataEntry

import android.annotation.SuppressLint
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.platform.LocalContext
import kotlin.Pair
import androidx.compose.runtime.Composable
import com.google.common.collect.Lists.cartesianProduct
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import com.ash.simpledataentry.presentation.dataEntry.components.FrozenHeaderGrid
import com.ash.simpledataentry.presentation.dataEntry.components.SectionNavigator

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@OptIn(ExperimentalFoundationApi::class)
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
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    // Define onValueChange ONCE here, at the top
    val onValueChange: (String, DataValue) -> Unit = { value, dataValue ->
        viewModel.updateCurrentValue(value, dataValue.dataElement, dataValue.categoryOptionCombo)
    }

    // --- Robust unsaved changes detection ---
    val hasUnsavedChanges = remember(state.dataValues) {
        // True if any dataValue's value is different from its original (not null, not blank, and not equal to comment or original value)
        state.dataValues.any { dataValue ->
            val original = dataValue.comment // Replace with actual original value if available
            val value = dataValue.value
            value != null && value.isNotBlank() && value != original
        }
    }

    val shouldShowSaveDialog = hasUnsavedChanges && !viewModel.wasSavePressed() && !state.saveInProgress

    // Intercept back press: only show dialog if shouldShowSaveDialog
    BackHandler(enabled = shouldShowSaveDialog) {
        showSaveDialog.value = true
        pendingNavAction.value = { navController.popBackStack() }
    }

    // Intercept navigation via top bar back button: only show dialog if shouldShowSaveDialog
    val baseScreenNavIcon: @Composable (() -> Unit) = {
        IconButton(onClick = {
            if (state.saveInProgress || viewModel.wasSavePressed()) {
                // Block navigation while saving or after save pressed
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

    // Save confirmation dialog: only show if shouldShowSaveDialog
    if (showSaveDialog.value && shouldShowSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog.value = false },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved changes. Save before leaving?") },
            confirmButton = {
                TextButton(onClick = {
                    showSaveDialog.value = false
                    viewModel.saveAllDataValues(context)
                    pendingNavAction.value?.invoke()
                    // Reset save pressed after navigation
                    viewModel.resetSaveFeedback()
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showSaveDialog.value = false
                        // Discard: clear drafts/dirty values, then navigate
                        viewModel.clearDraftsForCurrentInstance()
                        pendingNavAction.value?.invoke()
                        // Reset save pressed after navigation
                        viewModel.resetSaveFeedback()
                    }) { Text("Discard") }
                    TextButton(onClick = {
                        showSaveDialog.value = false
                    }) { Text("Cancel") }
                }
            }
        )
    }

    // --- Accordion expansion state for nested accordions ---
    val expandedAccordions = remember { mutableStateOf<Map<List<String>, String?>>(emptyMap()) }

    // In EditEntryScreen, define the toggle handler ONCE:
    val onAccordionToggle: (List<String>, String) -> Unit = { parentPath, optionUid ->
        expandedAccordions.value = buildMap {
            putAll(expandedAccordions.value)
            val current = expandedAccordions.value[parentPath]
            put(parentPath, if (current == optionUid) null else optionUid)
        }
    }

    LaunchedEffect(state.currentSectionIndex, state.dataValues) {
        if (state.dataValues.isNotEmpty() && state.currentSectionIndex >= 0 && state.currentSectionIndex < state.totalSections) {
            coroutineScope.launch {
                // Ensure the list is populated before trying to scroll
                listState.animateScrollToItem(index = state.currentSectionIndex)
            }
        }
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

    LaunchedEffect(state.currentSectionIndex, state.dataValues) {
        if (state.dataValues.isNotEmpty() && state.totalSections > 0 && state.currentSectionIndex != -1) { // Check for != -1
            coroutineScope.launch {
                listState.animateScrollToItem(index = state.currentSectionIndex)
            }
        }
    }


    // Resolve attribute option combo display name for the title
    val attrComboName = state.attributeOptionCombos.find { it.first == attributeOptionCombo }?.second ?: attributeOptionCombo
    BaseScreen(
        title = "${java.net.URLDecoder.decode(datasetName, "UTF-8")} - ${period.replace("Period(id=", "").replace(")", "")} - $attrComboName",
        navController = navController,
        navigationIcon = baseScreenNavIcon,
        actions = {
            // Add save button to header bar
            IconButton(
                onClick = { viewModel.saveAllDataValues(context) },
                enabled = !state.saveInProgress
            ) {
                if (state.saveInProgress) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
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
            // Add checkmark for completion
            IconButton(
                onClick = {
                    viewModel.markDatasetComplete { success, error ->
                        coroutineScope.launch {
                            if (success) {
                                snackbarHostState.showSnackbar("Dataset marked as complete.")
                            } else {
                                snackbarHostState.showSnackbar(error ?: "Failed to mark as complete.")
                            }
                        }
                    }
                },
                enabled = !state.isLoading && !state.isCompleted
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Mark Complete",
                    tint = if (state.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
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

                val sections = remember(state.dataValues) { // Memoize to avoid recalculation
                    state.dataValues.groupBy { it.sectionName }.toList()
                }
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
                        val bringIntoViewRequester = remember { BringIntoViewRequester() }
                        val coroutineScope = rememberCoroutineScope()
                        val listState = rememberLazyListState()

                        LaunchedEffect(state.currentSectionIndex) {
                            if (state.currentSectionIndex != -1) {
                                listState.animateScrollToItem(state.currentSectionIndex)
                            }
                        }

                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f) // Occupy available space
                        ) {
                            itemsIndexed(sections, key = { _, pair -> pair.first /* sectionName */ }) { index, (sectionName, values) ->
                                // Section expansion is now driven by currentSectionIndex from ViewModel
                                val sectionIsExpanded = index == state.currentSectionIndex // <-- DERIVE EXPANSION STATE
                                val filledCount = values.count { !it.value.isNullOrBlank() }
                                val totalCount = values.size
                                // Section header

                                
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {

                                            viewModel.setCurrentSectionIndex(index)
                                            state.isExpandedSections.keys.forEach { key ->
                                                if (key != sectionName && state.isExpandedSections[key] == true) {
                                                    viewModel.toggleSection(key)
                                                }
                                            }
                                            viewModel.toggleSection(sectionName)
                                            if (!sectionIsExpanded) {
                                                coroutineScope.launch {
                                                    bringIntoViewRequester.bringIntoView()
                                                }
                                            }
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
                                            modifier = Modifier.rotate(if (sectionIsExpanded) 180f else 0f)
                                        )
                                    }
                                }
                                AnimatedVisibility(visible = sectionIsExpanded) {
                                    Column(modifier = Modifier
                                        .fillMaxWidth()
                                        .bringIntoViewRequester(bringIntoViewRequester)) {
                                        // --- STEP 2: Group by category combo structure ---
                                        val structureGroups = values.groupBy { dataValue ->
                                            val structure = categoryComboStructures[dataValue.categoryOptionCombo]
                                            structure?.joinToString(", ") { cat ->
                                                val options = cat.second.joinToString("/") { it.second }
                                                "${cat.first}: $options"
                                            } ?: "Default"
                                        }
                                        android.util.Log.d("EditEntryScreen", "Section '$sectionName' has ${structureGroups.size} structure groups: ${structureGroups.keys}")
                                        structureGroups.forEach { (structureString, groupValues) ->
                                            val structure = groupValues.firstOrNull()?.let { categoryComboStructures[it.categoryOptionCombo] } ?: emptyList()
                                            val categoryOptionComboUid = groupValues.firstOrNull()?.categoryOptionCombo
                                            val optionUidsToComboUidForGroup = state.optionUidsToComboUid[categoryOptionComboUid] ?: emptyMap()
                                            CategoryAccordionRecursive(
                                                categories = structure,
                                                values = groupValues,
                                                onValueChange = onValueChange,
                                                optionUidsToComboUid = optionUidsToComboUidForGroup,
                                                viewModel = viewModel,
                                                parentPath = emptyList(),
                                                expandedAccordions = expandedAccordions.value,
                                                onToggle = onAccordionToggle
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                        }
                                    }
                                }
                            }
                            item {
                                Spacer(modifier = Modifier.height(80.dp)) // Space for the section navigator
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
                // Section Navigator at the bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(vertical = 8.dp)
                ) {
                    SectionNavigator(
                        onPreviousClick = { viewModel.goToPreviousSection() },
                        onNextClick = { viewModel.goToNextSection() },
                        currentSectionIndex = state.currentSectionIndex,
                        totalSections = state.totalSections
                    )
                }
                // Snackbar
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    SnackbarHost(
                        hostState = snackbarHostState,
                        snackbar = { data ->
                            Snackbar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = Color.White
                            ) {
                                Text(
                                    data.visuals.message,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    )
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
    viewModel: DataEntryViewModel,
    renderedKeys: MutableSet<String>? = null,
    expandedAccordions: Map<List<String>, String?>,
    onToggle: (List<String>, String) -> Unit
) {
    android.util.Log.d(
        "SectionContent",
        "sectionName=$sectionName, structure=${structure?.map { it.first }}, values=${values.map { it.dataElement + ":" + it.categoryOptionCombo }}"
    )
    val state by viewModel.state.collectAsState()
    val categoryComboStructures = state.categoryComboStructures
    val dataElements = values.map { it.dataElement to it.dataElementName }.distinct()

    // --- New grouping logic per RENDERING_RULES.md ---
    val structureKeyFor = { dataValue: DataValue ->
        val structure = categoryComboStructures[dataValue.categoryOptionCombo]
        structure?.joinToString("|") { cat ->
            cat.first + ":" + cat.second.joinToString(",") { it.first }
        } ?: "__DEFAULT__"
    }
    val groups = values.groupBy(structureKeyFor)
    val allSameStructure = groups.size == 1
    val isAllDefault = groups.keys.singleOrNull() == "__DEFAULT__"

    if (allSameStructure) {
        val groupValues = values
        val structure = groupValues.firstOrNull()?.let { categoryComboStructures[it.categoryOptionCombo] } ?: emptyList()
        if (structure.isEmpty()) {
            // Flat list for default (zero category)
            Column(modifier = Modifier.fillMaxWidth()) {
                dataElements.forEach { (dataElement, dataElementName) ->
                    val dataValue = groupValues.find { it.dataElement == dataElement }
                    val key = dataValue?.dataElement + "_" + dataValue?.categoryOptionCombo
                    renderedKeys?.add(key)
                    DataElementRow(
                        dataElementName = dataElementName,
                        fields = listOf(dataValue),
                        onValueChange = onValueChange
                    )
                }
            }
        } else {
            // Use the new recursive accordion logic for all non-empty category structures
            CategoryAccordionRecursive(
                categories = structure,
                values = groupValues,
                onValueChange = onValueChange,
                optionUidsToComboUid = optionUidsToComboUid,
                viewModel = viewModel,
                parentPath = emptyList(),
                expandedAccordions = expandedAccordions,
                onToggle = onToggle
            )
        }
    }
    // If mixed category combos, handle per rule 5
    // 1. Render all default (zero category) elements as a flat list
    val defaultValues = groups["__DEFAULT__"] ?: emptyList()
    if (defaultValues.isNotEmpty()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            dataElements.forEach { (dataElement, dataElementName) ->
                val dataValue = defaultValues.find { it.dataElement == dataElement }
                val key = dataValue?.dataElement + "_" + dataValue?.categoryOptionCombo
                renderedKeys?.add(key)
                DataElementRow(
                    dataElementName = dataElementName,
                    fields = listOf(dataValue),
                    onValueChange = onValueChange
                )
            }
        }
        Divider(modifier = Modifier.padding(vertical = 8.dp))
    }
    // 2. For each non-default group, render using the correct rule
    groups.filterKeys { it != "__DEFAULT__" }.forEach { (groupKey, groupValues) ->
        val structure = groupValues.firstOrNull()?.let { categoryComboStructures[it.categoryOptionCombo] } ?: emptyList()
        val categoryOptionComboUid = groupValues.firstOrNull()?.categoryOptionCombo
        val optionUidsToComboUidForGroup = state.optionUidsToComboUid[categoryOptionComboUid] ?: emptyMap()
        CategoryAccordionRecursive(
            categories = structure,
            values = groupValues,
            onValueChange = onValueChange,
            optionUidsToComboUid = optionUidsToComboUidForGroup,
            viewModel = viewModel,
            parentPath = emptyList(),
            expandedAccordions = expandedAccordions,
            onToggle = onToggle
        )
        Divider(modifier = Modifier.padding(vertical = 8.dp))
    }
}

@Composable
private fun DataElementRow(
    dataElementName: String,
    fields: List<DataValue?>,
    onValueChange: (String, DataValue) -> Unit
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)) {
        Text(text = dataElementName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        fields.filterNotNull().forEach { dataValue ->
            DataValueField(
                dataValue = dataValue,
                onValueChange = { value -> onValueChange(value, dataValue) },
                viewModel = viewModel()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryAccordion(
    header: String,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    content: @Composable () -> Unit
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onToggleExpand()
                    if (!expanded) {
                        coroutineScope.launch {
                            bringIntoViewRequester.bringIntoView()
                        }
                    }
                },
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
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp)
                .bringIntoViewRequester(bringIntoViewRequester)) {
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
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)) {
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
                    Box(modifier = Modifier
                        .width(100.dp)
                        .padding(4.dp)) {
                        if (dataValue != null) {
                            DataValueField(
                                dataValue = dataValue,
                                onValueChange = { value -> onValueChange(value, dataValue) },
                                viewModel = viewModel()
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
                                onValueChange = { value -> onValueChange(value, dataValue) },
                                viewModel = viewModel()
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
                                onValueChange = { value -> onValueChange(value, dataValue) },
                                viewModel = viewModel()
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
                        onValueChange = { value -> onValueChange(value, dataValue) },
                        viewModel = viewModel()
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
                            onValueChange = { value -> onValueChange(value, dataValue) },
                            viewModel = viewModel()
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
    viewModel: DataEntryViewModel,
) {
    Log.d(
        "DataValueField",
        "Rendering field for dataElement='${dataValue.dataElement}', categoryOptionCombo='${dataValue.categoryOptionCombo}', value='${dataValue.value}'"
    )
    val key = remember(dataValue.dataElement, dataValue.categoryOptionCombo) {
        "${dataValue.dataElement}|${dataValue.categoryOptionCombo}"
    }
    // Initialize field state if not present
    LaunchedEffect(key) {
        viewModel.initializeFieldState(dataValue)
    }
    val fieldState = viewModel.fieldStates[key] ?: androidx.compose.ui.text.input.TextFieldValue(dataValue.value ?: "")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
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
                    inputTextFieldValue = fieldState,
                    onValueChanged = { newValue -> 
                        if (newValue != null) viewModel.onFieldValueChange(newValue, dataValue)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp)
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
                    inputTextFieldValue = fieldState,
                    onValueChanged = { newValue -> 
                        if (newValue != null) viewModel.onFieldValueChange(newValue, dataValue)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp)
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
                    inputTextFieldValue = fieldState,
                    onValueChanged = { newValue -> 
                        if (newValue != null) viewModel.onFieldValueChange(newValue, dataValue)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp)
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

        val columnHeaders = listOf(largerCat.first) + smallerCat.second.map { it.second }

        val rows = largerCat.second.filter { largeOpt ->
            selectedFilter == null || largeOpt.second == selectedFilter
        }.map { largeOpt ->
            val rowCells = mutableListOf<@Composable () -> Unit>()
            // First cell in row is the row header
            rowCells.add {
                Text(
                    text = largeOpt.second,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            // Add data value fields for each column
            smallerCat.second.filter { smallOpt ->
                selectedFilter == null || smallOpt.second == selectedFilter
            }.forEach { smallOpt ->
                rowCells.add {
                    val optionUids = setOf(largeOpt.first, smallOpt.first)
                    val comboUid = optionUidsToComboUid[optionUids]
                    val cellDataValues = dataValues.filter { it.categoryOptionCombo == comboUid }
                    Column {
                        cellDataValues.forEach { dataValue ->
                            key("${dataValue.dataElement}_${dataValue.categoryOptionCombo}") {
                                DataValueField(
                                    dataValue = dataValue,
                                    onValueChange = { value -> onValueChange(value, dataValue) },
                                    viewModel = viewModel
                                )
                            }
                        }
                    }
                }
            }
            rowCells.toList()
        }

        FrozenHeaderGrid(
            columnHeaders = columnHeaders,
            rows = rows,
            modifier = Modifier.fillMaxWidth()
        )
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

// --- Helper composable to render a group of data elements by category combo ---
@Composable
private fun renderCategoryComboGroup(
    sectionName: String,
    structure: List<Pair<String, List<Pair<String, String>>>>,
    values: List<DataValue>,
    expandedCategoryGroup: String?,
    onToggleCategoryGroup: (String, String) -> Unit,
    onValueChange: (String, DataValue) -> Unit,
    optionUidsToComboUid: Map<Set<String>, String>,
    viewModel: DataEntryViewModel
) {
    when (structure.size) {
        0 -> {
            // Flat list (should not happen here, handled above)
            values.forEach { dataValue ->
                DataElementRow(
                    dataElementName = dataValue.dataElementName,
                    fields = listOf(dataValue),
                    onValueChange = onValueChange
                )
            }
        }
        1 -> {
            val options = structure[0].second
            if (options.size == 2) {
                // Side-by-side fields (Rule 2)
                Row(modifier = Modifier.fillMaxWidth()) {
                    options.forEach { (optUid, optName) ->
                        val cocUid = optionUidsToComboUid.entries.find { it.key == setOf(optUid) }?.value
                        val fields = values.filter { it.categoryOptionCombo == cocUid }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(optName, fontWeight = FontWeight.Medium)
                            fields.forEach { dataValue ->
                                DataValueField(
                                    dataValue = dataValue,
                                    onValueChange = { value -> onValueChange(value, dataValue) },
                                    viewModel = viewModel
                                )
                            }
                        }
                    }
                }
            } else {
                // Accordion with list inside (Rule 2)
                CategoryAccordion(
                    header = structure[0].first,
                    expanded = expandedCategoryGroup == "$sectionName:${structure[0].first}",
                    onToggleExpand = {
                        if (expandedCategoryGroup == "$sectionName:${structure[0].first}")
                            onToggleCategoryGroup(sectionName, "")
                        else
                            onToggleCategoryGroup(sectionName, structure[0].first)
                    }
                ) {
                    values.forEach { dataValue ->
                        DataElementRow(
                            dataElementName = dataValue.dataElementName,
                            fields = listOf(dataValue),
                            onValueChange = onValueChange
                        )
                    }
                }
            }
        }
        2 -> {
            val catA = structure[0]
            val catB = structure[1]
            val aOptions = catA.second
            val bOptions = catB.second
            // --- Begin new logic for two categories (see RENDERING_RULES.md) ---
            // Always make sex/gender category the columns in grid
            val (rowCat, colCat) = when {
                isSexCategory(catA) -> catB to catA
                isSexCategory(catB) -> catA to catB
                else -> catA to catB
            }
            if (rowCat.second.size == 2 && colCat.second.size == 2) {
                // Both have 2 options: render as grid
                Column(modifier = Modifier.padding(start = 32.dp, bottom = 2.dp)) {
                    // Header row
                    Row {
                        Spacer(modifier = Modifier.width(100.dp)) // For row header
                        colCat.second.forEach { (_, colName) ->
                            Text(
                                text = colName,
                                modifier = Modifier.width(100.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    // Data rows
                    rowCat.second.forEach { (rowUid, rowName) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = rowName,
                                modifier = Modifier.width(100.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            colCat.second.forEach { (colUid, _) ->
                                val dataValuesForCell = values.filter { dv ->
                                    val comboStructure = viewModel.state.value.categoryComboStructures[dv.categoryOptionCombo]
                                    comboStructure?.any { cat ->
                                        cat.first == rowCat.first && cat.second.any { it.first == rowUid }
                                    } == true &&
                                    comboStructure.any { cat ->
                                        cat.first == colCat.first && cat.second.any { it.first == colUid }
                                    }
                                }
                                Column(modifier = Modifier
                                    .width(100.dp)
                                    .padding(4.dp)) {
                                    dataValuesForCell.forEach { dataValue ->
                                        Text(
                                            text = dataValue.dataElementName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (colCat.second.size == 2) {
                // For each option in the header category, render an accordion header
                rowCat.second.forEach { (headerUid, headerName) ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 8.dp, bottom = 4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = headerName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    // Side by side columns for contentCat options, only render elements matching both header and column
                    Row(modifier = Modifier.padding(start = 40.dp, bottom = 2.dp)) {
                        colCat.second.forEach { (optUid, optName) ->
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = optName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                                values.filter { dv ->
                                    val comboStructure = viewModel.state.value.categoryComboStructures[dv.categoryOptionCombo]
                                    comboStructure?.any { cat ->
                                        cat.first == rowCat.first && cat.second.any { it.first == headerUid }
                                    } == true &&
                                    comboStructure.any { cat ->
                                        cat.first == colCat.first && cat.second.any { it.first == optUid }
                                    }
                                }.forEach { dataValue ->
                                    Text(
                                        text = dataValue.dataElementName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Fallback: vertical list
                values.forEach { dataValue ->
                    Text(
                        text = dataValue.dataElementName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 32.dp, bottom = 2.dp)
                    )
                }
            }
            // --- End new logic for two categories ---
            Spacer(modifier = Modifier.height(12.dp))
        }
        else -> {
            // Fallback: render each element by its own combo (Rule 5)
            values.forEach { dataValue ->
                val elementStructure = viewModel.state.value.categoryComboStructures[dataValue.categoryOptionCombo] ?: emptyList()
                if (elementStructure.isEmpty()) {
                    DataElementRow(
                        dataElementName = dataValue.dataElementName,
                        fields = listOf(dataValue),
                        onValueChange = onValueChange
                    )
                } else if (elementStructure.size == 1 && elementStructure[0].second.size == 2) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        elementStructure[0].second.forEach { (optUid, optName) ->
                            val cocUid = optionUidsToComboUid.entries.find { it.key == setOf(optUid) }?.value
                            val fields = listOf(dataValue).filter { it.categoryOptionCombo == cocUid }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(optName, fontWeight = FontWeight.Medium)
                                fields.forEach { dv ->
                                    DataValueField(
                                        dataValue = dv,
                                        onValueChange = { value -> onValueChange(value, dv) },
                                        viewModel = viewModel
                                    )
                                }
                            }
                        }
                    }
                } else {
                    CategoryAccordion(
                        header = elementStructure[0].first,
                        expanded = expandedCategoryGroup == "$sectionName:${elementStructure[0].first}",
                        onToggleExpand = {
                            if (expandedCategoryGroup == "$sectionName:${elementStructure[0].first}")
                                onToggleCategoryGroup(sectionName, "")
                            else
                                onToggleCategoryGroup(sectionName, elementStructure[0].first)
                        }
                    ) {
                        DataElementRow(
                            dataElementName = dataValue.dataElementName,
                            fields = listOf(dataValue),
                            onValueChange = onValueChange
                        )
                    }
                }
            }
        }
    }
}

// Helper to detect sex/gender category
fun isSexCategory(cat: Pair<String, List<Pair<String, String>>>): Boolean {
    val name = cat.first.lowercase()
    val options = cat.second.map { it.second.lowercase() }
    return name.contains("sex") || name.contains("gender") ||
        options.any { it == "male" || it == "female" }
}

/**
 * Recursively renders nested accordions for N categories, except:
 * - If only one category with exactly two options (especially sex/gender), render side by side.
 * - If no categories, render flat list.
 * - Always collapse single-option categories.
 * - Never render sex/gender as an accordion header.
 */
@Composable
fun CategoryAccordionRecursive(
    categories: List<Pair<String, List<Pair<String, String>>>>,
    values: List<DataValue>,
    onValueChange: (String, DataValue) -> Unit,
    optionUidsToComboUid: Map<Set<String>, String>,
    viewModel: DataEntryViewModel,
    parentPath: List<String> = emptyList(),
    expandedAccordions: Map<List<String>, String?>,
    onToggle: (List<String>, String) -> Unit,
) {
    if (categories.isEmpty()) {
        values.forEach { dataValue ->
            DataElementRow(
                dataElementName = dataValue.dataElementName,
                fields = listOf(dataValue),
                onValueChange = onValueChange
            )
        }
        return
    }
    val currentCategory = categories.first()
    val restCategories = categories.drop(1)
    if (restCategories.isEmpty()) {
        // LAST CATEGORY: If <= 3 options, render as a row; if > 3, render each as a nested accordion
        if (currentCategory.second.size <= 3) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                currentCategory.second.forEach { (optionUid, optionName) ->
                    val fullPath = parentPath + optionUid
                    val comboUid = optionUidsToComboUid[fullPath.toSet()]
                    val filteredValues = values.filter { it.categoryOptionCombo == comboUid }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = optionName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        filteredValues.forEach { dataValue ->
                            DataElementRow(
                                dataElementName = dataValue.dataElementName,
                                fields = listOf(dataValue),
                                onValueChange = onValueChange
                            )
                        }
                    }
                }
            }
        } else {
            // More than 3 options: render each as a nested accordion
            currentCategory.second.forEach { (optionUid, optionName) ->
                val expanded = expandedAccordions[parentPath] == optionUid
                Box(modifier = Modifier.padding(bottom = 8.dp)) {
                    CategoryAccordion(
                        header = optionName,
                        expanded = expanded,
                        onToggleExpand = { onToggle(parentPath, optionUid) }
                    ) {
                        val fullPath = parentPath + optionUid
                        val comboUid = optionUidsToComboUid[fullPath.toSet()]
                        val filteredValues = values.filter { it.categoryOptionCombo == comboUid }
                        filteredValues.forEach { dataValue ->
                            DataElementRow(
                                dataElementName = dataValue.dataElementName,
                                fields = listOf(dataValue),
                                onValueChange = onValueChange
                            )
                        }
                    }
                }
            }
        }
        return
    }
    // For all other categories, render as accordions, filtering by current path
    currentCategory.second.forEach { (optionUid, optionName) ->
        val expanded = expandedAccordions[parentPath] == optionUid
        Box(modifier = Modifier.padding(bottom = 8.dp)) {
            CategoryAccordion(
                header = optionName,
                expanded = expanded,
                onToggleExpand = { onToggle(parentPath, optionUid) }
            ) {
                val newPath = parentPath + optionUid
                val comboUid = optionUidsToComboUid[newPath.toSet()]
                val filteredValues = if (comboUid != null) values.filter { it.categoryOptionCombo == comboUid } else values
                CategoryAccordionRecursive(
                    categories = restCategories,
                    values = filteredValues,
                    onValueChange = onValueChange,
                    optionUidsToComboUid = optionUidsToComboUid,
                    viewModel = viewModel,
                    parentPath = newPath,
                    expandedAccordions = expandedAccordions,
                    onToggle = onToggle
                )
            }
        }
    }
}
