@file:OptIn(ExperimentalMaterial3Api::class)

package com.ash.simpledataentry.presentation.dataEntry

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.ash.simpledataentry.presentation.datasetInstances.SyncConfirmationDialog
import com.ash.simpledataentry.presentation.datasetInstances.SyncOptions
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.platform.LocalContext
import com.ash.simpledataentry.presentation.core.DetailedSyncOverlay
import com.ash.simpledataentry.presentation.core.CompletionProgressOverlay
import com.ash.simpledataentry.presentation.core.CompletionAction
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.platform.LocalFocusManager
import com.ash.simpledataentry.presentation.dataEntry.components.SectionNavigator
import com.ash.simpledataentry.presentation.core.FullScreenLoader
import com.ash.simpledataentry.presentation.core.ShimmerFormSection

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)


@Composable
fun SectionContent(
    sectionName: String,
    values: List<DataValue>,
    categoryComboStructures: Map<String, List<Pair<String, List<Pair<String, String>>>>>,
    optionUidsToComboUidByCombo: Map<String, Map<Set<String>, String>>,
    onValueChange: (String, DataValue) -> Unit,
    viewModel: DataEntryViewModel,
    expandedAccordions: Map<List<String>, String?>,
    onToggle: (List<String>, String) -> Unit
) {
    // Get distinct data elements in order
    val dataElements = values.map { it.dataElement to it.dataElementName }.distinct()

    // Render each data element as an accordion (first level)
    dataElements.forEach { (dataElement, dataElementName) ->
        val dataElementValues = values.filter { it.dataElement == dataElement }
        val firstValue = dataElementValues.firstOrNull() ?: return@forEach
        val structure = categoryComboStructures[firstValue.categoryOptionCombo] ?: emptyList()
        val optionMap = optionUidsToComboUidByCombo[firstValue.categoryOptionCombo] ?: emptyMap()

        val elementKey = "element_$dataElement"
        val isExpanded = expandedAccordions[emptyList()] == elementKey
        val hasData = dataElementValues.any { !it.value.isNullOrBlank() }

        // Data Element Accordion Wrapper (FIRST level) - White card with left accent
        DataElementAccordion(
            header = dataElementName,
            hasData = hasData,
            expanded = isExpanded,
            onToggleExpand = { onToggle(emptyList(), elementKey) }
        ) {
            if (structure.isEmpty()) {
                // No category combo - render field directly
                DataValueField(
                    dataValue = firstValue,
                    onValueChange = { value -> onValueChange(value, firstValue) },
                    viewModel = viewModel
                )
            } else {
                // Has category combo - render nested category accordions
                CategoryAccordionRecursive(
                    categories = structure,
                    values = dataElementValues,
                    onValueChange = onValueChange,
                    optionUidsToComboUid = optionMap,
                    viewModel = viewModel,
                    parentPath = listOf(elementKey),
                    expandedAccordions = expandedAccordions,
                    onToggle = onToggle
                )
            }
        }
    }
}

@Composable
fun DataElementRow(
    dataElementName: String,
    fields: List<DataValue?>,
    onValueChange: (String, DataValue) -> Unit,
    viewModel: DataEntryViewModel
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)) {
        Text(text = dataElementName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        fields.filterNotNull().forEach { dataValue ->
            DataValueField(
                dataValue = dataValue,
                onValueChange = { value -> onValueChange(value, dataValue) },
                viewModel = viewModel
            )
        }
    }
}

@Composable

fun DataValueField(
    dataValue: DataValue,
    onValueChange: (String) -> Unit,
    viewModel: DataEntryViewModel,
    enabled: Boolean = true
) {
    val key = remember(dataValue.dataElement, dataValue.categoryOptionCombo) {
        "${dataValue.dataElement}|${dataValue.categoryOptionCombo}"
    }
    LaunchedEffect(key) {
        viewModel.initializeFieldState(dataValue)
    }
    val state by viewModel.state.collectAsState()
    val fieldState = viewModel.fieldStates[key] ?: TextFieldValue(dataValue.value ?: "")
    val optionSet = state.optionSets[dataValue.dataElement]
    val renderType = state.renderTypes[dataValue.dataElement] ?: optionSet?.computeRenderType()
    val isHidden = state.hiddenFields.contains(dataValue.dataElement)
    val isDisabledByRule = state.disabledFields.contains(dataValue.dataElement)
    val isMandatoryByRule = state.mandatoryFields.contains(dataValue.dataElement)
    val warning = state.fieldWarnings[dataValue.dataElement]
    val error = state.fieldErrors[dataValue.dataElement]
    val calculatedValue = state.calculatedValues[dataValue.dataElement]

    if (isHidden) return

    val effectiveEnabled = enabled && !isDisabledByRule

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .let { base ->
                if (!effectiveEnabled) {
                    base.background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        MaterialTheme.shapes.small
                    )
                } else base
            }
    ) {
        when {
            optionSet != null && renderType != null -> {
                when (renderType) {
                    RenderType.DROPDOWN -> {
                        com.ash.simpledataentry.presentation.dataEntry.components.OptionSetDropdown(
                            optionSet = optionSet,
                            selectedCode = calculatedValue ?: dataValue.value,
                            title = dataValue.dataElementName + if (isMandatoryByRule) " *" else "",
                            isRequired = isMandatoryByRule,
                            enabled = effectiveEnabled,
                            onOptionSelected = { selectedCode ->
                                if (selectedCode != null) {
                                    onValueChange(selectedCode)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    RenderType.RADIO_BUTTONS -> {
                        com.ash.simpledataentry.presentation.dataEntry.components.OptionSetRadioGroup(
                            optionSet = optionSet,
                            selectedCode = calculatedValue ?: dataValue.value,
                            title = dataValue.dataElementName + if (isMandatoryByRule) " *" else "",
                            isRequired = isMandatoryByRule,
                            enabled = effectiveEnabled,
                            onOptionSelected = { selectedCode ->
                                if (selectedCode != null) {
                                    onValueChange(selectedCode)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    RenderType.YES_NO_BUTTONS -> {
                        com.ash.simpledataentry.presentation.dataEntry.components.YesNoCheckbox(
                            selectedValue = calculatedValue ?: dataValue.value,
                            title = dataValue.dataElementName + if (isMandatoryByRule) " *" else "",
                            isRequired = isMandatoryByRule,
                            enabled = effectiveEnabled,
                            onValueChanged = { newValue ->
                                if (newValue != null) {
                                    onValueChange(newValue)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    else -> {
                        com.ash.simpledataentry.presentation.dataEntry.components.OptionSetDropdown(
                            optionSet = optionSet,
                            selectedCode = calculatedValue ?: dataValue.value,
                            title = dataValue.dataElementName + if (isMandatoryByRule) " *" else "",
                            isRequired = isMandatoryByRule,
                            enabled = effectiveEnabled,
                            onOptionSelected = { selectedCode ->
                                if (selectedCode != null) {
                                    onValueChange(selectedCode)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            dataValue.dataEntryType == DataEntryType.YES_NO -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        text = "Yes",
                        style = if (dataValue.value == "true") ButtonStyle.FILLED else ButtonStyle.OUTLINED,
                        colorStyle = ColorStyle.DEFAULT,
                        modifier = Modifier.weight(1f),
                        enabled = effectiveEnabled,
                        onClick = { if (effectiveEnabled) onValueChange("true") }
                    )
                    Button(
                        text = "No",
                        style = if (dataValue.value == "false") ButtonStyle.FILLED else ButtonStyle.OUTLINED,
                        colorStyle = ColorStyle.DEFAULT,
                        modifier = Modifier.weight(1f),
                        enabled = effectiveEnabled,
                        onClick = { if (effectiveEnabled) onValueChange("false") }
                    )
                }
            }
            dataValue.dataEntryType == DataEntryType.NUMBER ||
                    dataValue.dataEntryType == DataEntryType.INTEGER ||
                    dataValue.dataEntryType == DataEntryType.POSITIVE_INTEGER ||
                    dataValue.dataEntryType == DataEntryType.NEGATIVE_INTEGER -> {
                InputNumber(
                    title = dataValue.dataElementName + if (isMandatoryByRule) " *" else "",
                    state = when {
                        !effectiveEnabled -> InputShellState.DISABLED
                        dataValue.validationState == ValidationState.ERROR || error != null -> InputShellState.ERROR
                        dataValue.validationState == ValidationState.WARNING || warning != null -> InputShellState.WARNING
                        else -> InputShellState.UNFOCUSED
                    },
                    inputTextFieldValue = fieldState,
                    onValueChanged = { newValue ->
                        if (newValue != null && effectiveEnabled) viewModel.onFieldValueChange(newValue, dataValue)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            dataValue.dataEntryType == DataEntryType.PERCENTAGE -> {
                InputText(
                    title = dataValue.dataElementName + if (isMandatoryByRule) " *" else "",
                    state = when {
                        !effectiveEnabled -> InputShellState.DISABLED
                        dataValue.validationState == ValidationState.ERROR || error != null -> InputShellState.ERROR
                        dataValue.validationState == ValidationState.WARNING || warning != null -> InputShellState.WARNING
                        else -> InputShellState.UNFOCUSED
                    },
                    inputTextFieldValue = fieldState,
                    onValueChanged = { newValue ->
                        if (newValue != null && effectiveEnabled) viewModel.onFieldValueChange(newValue, dataValue)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            else -> {
                InputText(
                    title = dataValue.dataElementName + if (isMandatoryByRule) " *" else "",
                    state = when {
                        !effectiveEnabled -> InputShellState.DISABLED
                        dataValue.validationState == ValidationState.ERROR || error != null -> InputShellState.ERROR
                        dataValue.validationState == ValidationState.WARNING || warning != null -> InputShellState.WARNING
                        else -> InputShellState.UNFOCUSED
                    },
                    inputTextFieldValue = fieldState,
                    onValueChanged = { newValue ->
                        if (newValue != null && effectiveEnabled) viewModel.onFieldValueChange(newValue, dataValue)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Data Element Accordion - First level accordion with white card styling and left accent border
 * Shows "Has data" / "No data" indicator
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DataElementAccordion(
    header: String,
    hasData: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    content: @Composable () -> Unit
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 1.dp
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left accent border
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .heightIn(min = 64.dp)
                    .background(
                        if (hasData) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                    )
            )

            Column(modifier = Modifier.weight(1f)) {
                // Header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onToggleExpand()
                            if (!expanded) {
                                coroutineScope.launch {
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = header,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (hasData) "Has data" else "No data",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasData) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand/Collapse",
                        modifier = Modifier
                            .rotate(if (expanded) 180f else 0f)
                            .size(24.dp)
                    )
                }

                // Content
                AnimatedVisibility(visible = expanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, bottom = 12.dp)
                            .bringIntoViewRequester(bringIntoViewRequester)
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

/**
 * Category Option Accordion - Nested level accordion with lavender/purple background
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryAccordion(
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
                .heightIn(min = 48.dp)
                .clickable {
                    onToggleExpand()
                    if (!expanded) {
                        coroutineScope.launch {
                            bringIntoViewRequester.bringIntoView()
                        }
                    }
                },
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            shape = MaterialTheme.shapes.small
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = header,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand/Collapse Category",
                    modifier = Modifier
                        .rotate(if (expanded) 180f else 0f)
                        .size(20.dp)
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 8.dp)
                .bringIntoViewRequester(bringIntoViewRequester)) {
                content()
            }
        }
    }
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
                onValueChange = onValueChange,
                viewModel = viewModel
            )
        }
        return
    }
    val currentCategory = categories.first()
    val restCategories = categories.drop(1)
    // Helper to extract only option UIDs from path (filter out element_ prefix keys)
    fun optionOnlyPath(path: List<String>): Set<String> {
        return path.filter { !it.startsWith("element_") }.toSet()
    }

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
                    // Use only option UIDs for combo lookup (exclude element_ prefix)
                    val comboUid = optionUidsToComboUid[optionOnlyPath(fullPath)]
                    val filteredValues = values.filter { it.categoryOptionCombo == comboUid }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = optionName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        // Render DataValueField directly (element name already shown as first accordion)
                        filteredValues.forEach { dataValue ->
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
                        // Use only option UIDs for combo lookup (exclude element_ prefix)
                        val comboUid = optionUidsToComboUid[optionOnlyPath(fullPath)]
                        val filteredValues = values.filter { it.categoryOptionCombo == comboUid }
                        // Render DataValueField directly (element name already shown as first accordion)
                        filteredValues.forEach { dataValue ->
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
                // Use only option UIDs for combo lookup (exclude element_ prefix)
                val comboUid = optionUidsToComboUid[optionOnlyPath(newPath)]
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
    var isUIReady by remember { mutableStateOf(false) }
    val showSaveDialog = remember { mutableStateOf(false) }
    val showSyncDialog = remember { mutableStateOf(false) }
    var showCompleteDialog by remember { mutableStateOf(false) }
    val pendingNavAction = remember { mutableStateOf<(() -> Unit)?>(null) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    // Define onValueChange ONCE here, at the top
    val onValueChange: (String, DataValue) -> Unit = { value, dataValue ->
        viewModel.updateCurrentValue(value, dataValue.dataElement, dataValue.categoryOptionCombo)
    }

    // --- Proper unsaved changes detection using ViewModel's dirty tracking ---
    val hasUnsavedChanges = viewModel.hasUnsavedChanges()

    val shouldShowSaveDialog = hasUnsavedChanges && !state.saveInProgress

    // Intercept back press: only show dialog if shouldShowSaveDialog
    BackHandler(enabled = shouldShowSaveDialog) {
        showSaveDialog.value = true
        pendingNavAction.value = { navController.popBackStack() }
    }

    // Intercept navigation via top bar back button: only show dialog if shouldShowSaveDialog
    val baseScreenNavIcon: @Composable (() -> Unit) = {
        IconButton(onClick = {
            if (state.saveInProgress) {
                // Only block navigation while actively saving
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
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showSaveDialog.value = false
                        // Discard: clear only current session changes, preserve existing drafts
                        viewModel.clearCurrentSessionChanges()
                        pendingNavAction.value?.invoke()
                    }) { Text("Discard") }
                    TextButton(onClick = {
                        showSaveDialog.value = false
                    }) { Text("Cancel") }
                }
            }
        )
    }

    // Sync confirmation dialog - using the same one as datasetInstances
    if (showSyncDialog.value) {
        SyncConfirmationDialog(
            syncOptions = SyncOptions(
                uploadLocalData = state.localDraftCount > 0,
                localInstanceCount = state.localDraftCount,
                isEditEntryContext = true
            ),
            onConfirm = { uploadFirst ->
                viewModel.syncDataEntry(uploadFirst)
                showSyncDialog.value = false
            },
            onDismiss = {
                showSyncDialog.value = false
            }
        )
    }

    // Enhanced completion action dialog
    if (showCompleteDialog) {
        CompletionActionDialog(
            isCurrentlyComplete = state.isCompleted,
            onAction = { action ->
                showCompleteDialog = false
                when (action) {
                    CompletionAction.VALIDATE_AND_COMPLETE -> {
                        viewModel.startValidationForCompletion()
                    }
                    CompletionAction.COMPLETE_WITHOUT_VALIDATION -> {
                        viewModel.completeDatasetAfterValidation { success, message ->
                            coroutineScope.launch {
                                if (success) {
                                    snackbarHostState.showSnackbar(message ?: "Dataset marked as complete.")
                                } else {
                                    snackbarHostState.showSnackbar(message ?: "Failed to mark as complete.")
                                }
                            }
                        }
                    }
                    CompletionAction.RERUN_VALIDATION -> {
                        viewModel.startValidationForCompletion()
                    }
                    CompletionAction.MARK_INCOMPLETE -> {
                        viewModel.markDatasetIncomplete { success, message ->
                            coroutineScope.launch {
                                if (success) {
                                    snackbarHostState.showSnackbar(message ?: "Dataset marked as incomplete.")
                                } else {
                                    snackbarHostState.showSnackbar(message ?: "Failed to mark as incomplete.")
                                }
                            }
                        }
                    }
                }
            },
            onDismiss = { showCompleteDialog = false }
        )
    }

    // Validation result dialog for completion
    state.validationSummary?.let { validationSummary ->
        ValidationResultDialog(
            validationSummary = validationSummary,
            showCompletionOption = !state.isCompleted, // Only show completion options if not already completed
            onComplete = {
                viewModel.completeDatasetAfterValidation { success, message ->
                    coroutineScope.launch {
                        if (success) {
                            snackbarHostState.showSnackbar(message ?: "Dataset marked as complete.")
                        } else {
                            snackbarHostState.showSnackbar(message ?: "Failed to mark as complete.")
                        }
                    }
                }
                viewModel.clearValidationResult()
            },
            onCompleteAnyway = {
                viewModel.completeDatasetAfterValidation { success, message ->
                    coroutineScope.launch {
                        if (success) {
                            snackbarHostState.showSnackbar(message ?: "Dataset marked as complete (validation warnings ignored).")
                        } else {
                            snackbarHostState.showSnackbar(message ?: "Failed to mark as complete.")
                        }
                    }
                }
                viewModel.clearValidationResult()
            },
            onDismiss = {
                viewModel.clearValidationResult()
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
            isUIReady = false
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
    
    LaunchedEffect(state.isLoading, state.dataValues) {
        if (!state.isLoading && state.dataValues.isNotEmpty()) {
            delay(300)
            isUIReady = true
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

    // Handle sync success messages
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    // Handle sync error messages
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    // Resolve attribute option combo display name for the title
    val attrComboName = state.attributeOptionCombos.find { it.first == attributeOptionCombo }?.second ?: attributeOptionCombo
    BaseScreen(
        title = "${java.net.URLDecoder.decode(datasetName, "UTF-8")} - ${period.replace("Period(id=", "").replace(")", "")} - $attrComboName",
        navController = navController,
        navigationIcon = baseScreenNavIcon,
        // PHASE 4: Wire up progress indicator for form loading and sync operations
        showProgress = state.isLoading || state.isSyncing,
        progress = state.detailedSyncProgress?.let { p ->
            p.overallPercentage.toFloat() / 100f
        } ?: state.navigationProgress?.let { p ->
            p.overallPercentage.toFloat() / 100f
        },
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
                    showSyncDialog.value = true
                },
                enabled = !state.isSyncing
            ) {
                if (state.isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            // Complete button with enhanced completion dialog
            IconButton(
                onClick = {
                    showCompleteDialog = true
                },
                enabled = !state.isLoading && !state.isValidating && state.completionProgress == null
            ) {
                if (state.isValidating || state.completionProgress != null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = if (state.isCompleted) Icons.Default.CheckCircle else Icons.Default.Check,
                        contentDescription = if (state.isCompleted) "Already Complete" else "Mark Complete",
                        tint = if (state.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    ) {
        // Enhanced sync overlay with dismissal capability
        DetailedSyncOverlay(
            progress = state.detailedSyncProgress,
            onNavigateBack = { viewModel.dismissSyncOverlay() },
            onCancel = { viewModel.dismissSyncOverlay() },
            modifier = Modifier.fillMaxSize()
        ) {
            CompletionProgressOverlay(
                progress = state.completionProgress,
                onCancel = { viewModel.dismissSyncOverlay() },
                modifier = Modifier.fillMaxSize()
            ) {
                if (state.isLoading || !isUIReady) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 16.dp)
                    ) {
                        repeat(3) {
                            ShimmerFormSection(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(bottom = 96.dp)
                        ) {
                            if (state.dataValues.isEmpty()) {
                                Text(
                                    text = "No data elements found for this dataset/period/org unit.",
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                            } else {
                                LazyColumn(
                                    state = listState,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                            // Render sections as top-level accordions with proper scrolling integration
                            itemsIndexed(
                                items = state.dataElementGroupedSections.entries.toList(),
                                key = { _, (sectionName, _) -> "section_$sectionName" }
                            ) { sectionIndex, (sectionName, elementGroups) ->
                                // PERFORMANCE OPTIMIZATION: Use pre-computed data element ordering from ViewModel state
                                // This eliminates expensive filtering/grouping/mapping on every render
                                val dataElementOrder = state.dataElementOrdering[sectionName] ?: emptyMap()

                                val sectionIsExpanded = sectionIndex == state.currentSectionIndex
                                // Count data elements that have any data entered (not individual fields)
                                val elementsWithData = elementGroups.count { (_, dataValues) ->
                                    dataValues.any { !it.value.isNullOrBlank() }
                                }
                                val totalElements = elementGroups.size
                                val bringIntoViewRequester = remember { BringIntoViewRequester() }
                                
                                // Check if ALL data elements in this section have default category combinations
                                val allElementsHaveDefaultCategories = elementGroups.values.all { dataValues ->
                                    dataValues.all { dataValue ->
                                        val structure = state.categoryComboStructures[dataValue.categoryOptionCombo] ?: emptyList()
                                        structure.isEmpty() || (structure.size == 1 && structure[0].first.lowercase().contains("default"))
                                    }
                                }
                                
                                // Section Header Accordion
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 72.dp, max = 96.dp) // Allow some growth for long titles
                                        .clickable {
                                            viewModel.setCurrentSectionIndex(sectionIndex)
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
                                        modifier = Modifier
                                            .padding(16.dp)
                                            .bringIntoViewRequester(bringIntoViewRequester)
                                            .fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = sectionName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                lineHeight = 20.sp, // Explicit line height for consistency
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "($elementsWithData/$totalElements elements)",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                maxLines = 1
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Expand/Collapse Section",
                                            modifier = Modifier
                                                .rotate(if (sectionIsExpanded) 180f else 0f)
                                                .size(24.dp)
                                        )
                                    }
                                }
                                
                                // Section Content - Use key to prevent unnecessary recomposition
                                AnimatedVisibility(
                                    visible = sectionIsExpanded,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Memoize the section content to prevent recomposition on every animation frame
                                    val sectionContent = remember(sectionName, elementGroups, state.radioButtonGroups) {
                                        elementGroups
                                    }

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (allElementsHaveDefaultCategories) {
                                            // All elements have default categories - render as simple vertical list
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // Collect all data elements for this section
                                                val allDataElements = sectionContent.values.flatten()

                                                // Track which fields are part of grouped radio buttons
                                                val fieldsInGroups = state.radioButtonGroups.values.flatten().toSet()

                                                // Render grouped radio buttons first
                                                state.radioButtonGroups.forEach { (groupTitle, dataElementIds) ->
                                                    // Only render groups where at least one field is in this section
                                                    val groupFields = allDataElements.filter { it.dataElement in dataElementIds }
                                                    if (groupFields.isNotEmpty()) {
                                                        // Get optionSet if available, otherwise provide empty one
                                                        // Note: GroupedRadioButtons doesn't actually use optionSet, it's just for API compatibility
                                                        val optionSet = groupFields.firstOrNull()?.let { state.optionSets[it.dataElement] }
                                                            ?: com.ash.simpledataentry.domain.model.OptionSet(id = "", name = "", options = emptyList())

                                                        // Find which field (if any) has value "YES" or "true"
                                                        val selectedFieldId = groupFields.firstOrNull {
                                                            it.value?.lowercase() in listOf("yes", "true", "1")
                                                        }?.dataElement

                                                        com.ash.simpledataentry.presentation.dataEntry.components.GroupedRadioButtons(
                                                            groupTitle = groupTitle,
                                                            fields = groupFields,
                                                            selectedFieldId = selectedFieldId,
                                                            optionSet = optionSet,
                                                            enabled = true,
                                                            onFieldSelected = { selectedDataElementId ->
                                                                // Set selected field to YES, others to NO
                                                                groupFields.forEach { field ->
                                                                    val newValue = if (field.dataElement == selectedDataElementId) "true" else "false"
                                                                    onValueChange(newValue, field)
                                                                }
                                                            },
                                                            modifier = Modifier.fillMaxWidth()
                                                        )
                                                    }
                                                }

                                                // Then render individual fields (excluding grouped ones)
                                                // Use remember to prevent recomputation on every recomposition
                                                val individualFields = remember(sectionContent, fieldsInGroups, dataElementOrder) {
                                                    sectionContent.entries
                                                        .sortedBy { dataElementOrder[it.key] ?: Int.MAX_VALUE }
                                                        .flatMap { (_, dataValues) ->
                                                            dataValues.filter { it.dataElement !in fieldsInGroups }
                                                        }
                                                }

                                                individualFields.forEach { dataValue ->
                                                    key("field_${dataValue.dataElement}_${dataValue.categoryOptionCombo}") {
                                                        DataValueField(
                                                            dataValue = dataValue,
                                                            onValueChange = { value -> onValueChange(value, dataValue) },
                                                            viewModel = viewModel
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            val sectionValues = sectionContent.values.flatten()
                                            SectionContent(
                                                sectionName = sectionName,
                                                values = sectionValues,
                                                categoryComboStructures = state.categoryComboStructures,
                                                optionUidsToComboUidByCombo = state.optionUidsToComboUid,
                                                onValueChange = onValueChange,
                                                viewModel = viewModel,
                                                expandedAccordions = expandedAccordions.value,
                                                onToggle = onAccordionToggle
                                            )
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

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(vertical = 8.dp, horizontal = 16.dp)
                ) {
                    SectionNavigator(
                        onPreviousClick = { viewModel.goToPreviousSection() },
                        onNextClick = { viewModel.goToNextSection() },
                        currentSectionIndex = state.currentSectionIndex,
                        totalSections = state.totalSections
                    )
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
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
        }}}}}

