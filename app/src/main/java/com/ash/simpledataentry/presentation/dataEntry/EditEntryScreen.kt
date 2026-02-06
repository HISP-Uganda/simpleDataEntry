@file:OptIn(ExperimentalMaterial3Api::class)

package com.ash.simpledataentry.presentation.dataEntry

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import android.text.format.DateUtils
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.ash.simpledataentry.presentation.datasetInstances.SyncConfirmationDialog
import com.ash.simpledataentry.presentation.core.DatePickerDialog
import com.ash.simpledataentry.presentation.core.DatePickerUtils
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
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
import com.ash.simpledataentry.presentation.core.AdaptiveLoadingOverlay
import com.ash.simpledataentry.presentation.core.BaseScreen
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.platform.LocalContext
import com.ash.simpledataentry.presentation.core.CompletionAction
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.platform.LocalFocusManager
import com.ash.simpledataentry.presentation.core.Section
import com.ash.simpledataentry.presentation.core.SectionNavigationBar
import com.ash.simpledataentry.presentation.core.Subsection
import com.ash.simpledataentry.presentation.core.LoadingOperation
import com.ash.simpledataentry.presentation.core.LoadingProgress
import com.ash.simpledataentry.presentation.core.UiState

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private val LocalShowFieldLabel = compositionLocalOf { true }

private data class ParsedGridField(
    val rowTitle: String,
    val rowKey: String,
    val columnTitle: String,
    val dataValue: DataValue
)

private fun parseGridLabel(name: String): Pair<String, String>? {
    // Only split on primary separators between row and column.
    // Avoid splitting on "/" which is commonly inside column titles like "Meeting/Workshop".
    val delimiterRegex = Regex("\\s*[-–—:|]\\s*")
    val matches = delimiterRegex.findAll(name).toList()
    if (matches.isNotEmpty()) {
        val last = matches.last()
        val index = last.range.first
        val delimiterLength = last.value.length
        if (index > 0 && index < name.length - delimiterLength) {
            val row = name.substring(0, index)
                .replace(Regex("\\s+"), " ")
                .trim()
                .trimEnd('-', ':', '|', '/', '\\')
                .trim()
            val col = name.substring(index + delimiterLength)
                .replace(Regex("\\s+"), " ")
                .trim()
            if (row.isNotBlank() && col.isNotBlank()) {
                return row to col
            }
        }
    }
    return null
}

private fun normalizeRowKey(value: String): String {
    return value
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .replace(Regex("\\s*\\(.*?\\)\\s*"), " ")
        .trim()
}

private fun formatRelativeTime(timestamp: Long): String {
    return DateUtils.getRelativeTimeSpanString(
        timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()
}


@Composable
fun SectionContent(
    sectionName: String,
    values: List<DataValue>,
    valuesByCombo: Map<String, List<DataValue>>,
    valuesByElement: Map<String, List<DataValue>>,
    dataElementsForSection: List<Pair<String, String>>,
    categoryComboStructures: Map<String, List<Pair<String, List<Pair<String, String>>>>>,
    optionUidsToComboUidByCombo: Map<String, Map<Set<String>, String>>,
    onValueChange: (String, DataValue) -> Unit,
    viewModel: DataEntryViewModel,
    expandedAccordions: Map<List<String>, String?>,
    onToggle: (List<String>, String) -> Unit,
    onElementSelected: (String) -> Unit
) {
    // Get distinct data elements in order
    val dataElements = if (dataElementsForSection.isNotEmpty()) dataElementsForSection
    else values.map { it.dataElement to it.dataElementName }.distinct()

    // Render each data element as an accordion (first level)
    dataElements.forEach { (dataElement, dataElementName) ->
        val dataElementValues = valuesByElement[dataElement].orEmpty()
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
            onToggleExpand = {
                onToggle(emptyList(), elementKey)
                onElementSelected(dataElement)
            }
        ) {
            if (structure.isEmpty()) {
                // No category combo - render field directly
                DataValueField(
                    dataValue = firstValue,
                    onValueChange = { value -> onValueChange(value, firstValue) },
                    viewModel = viewModel,
                    showLabel = false
                )
            } else {
                // Has category combo - render nested category accordions
                CategoryAccordionRecursive(
                    categories = structure,
                    values = dataElementValues,
                    valuesByCombo = valuesByCombo,
                    onValueChange = onValueChange,
                    optionUidsToComboUid = optionMap,
                    viewModel = viewModel,
                    parentPath = listOf(elementKey),
                    expandedAccordions = expandedAccordions,
                    onToggle = onToggle,
                    showElementHeader = false
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
    viewModel: DataEntryViewModel,
    showHeader: Boolean = true
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)) {
        if (showHeader) {
            Text(
                text = dataElementName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        fields.filterNotNull().forEach { dataValue ->
            DataValueField(
                dataValue = dataValue,
                onValueChange = { value -> onValueChange(value, dataValue) },
                viewModel = viewModel,
                showLabel = false
            )
        }
    }
}

@Composable

fun DataValueField(
    dataValue: DataValue,
    onValueChange: (String) -> Unit,
    viewModel: DataEntryViewModel,
    enabled: Boolean = true,
    showLabel: Boolean = true,
    labelOverride: String? = null,
    compact: Boolean = false
) {
    val effectiveShowLabel = showLabel && LocalShowFieldLabel.current
    val key = remember(dataValue.dataElement, dataValue.categoryOptionCombo) {
        "${dataValue.dataElement}|${dataValue.categoryOptionCombo}"
    }
    LaunchedEffect(key) {
        viewModel.initializeFieldState(dataValue)
    }
    val state by viewModel.state.collectAsState()
    val fieldStates by viewModel.fieldStates.collectAsState()
    val fieldState = fieldStates[key] ?: TextFieldValue(dataValue.value ?: "")
    val optionSet = state.optionSets[dataValue.dataElement]
    val renderType = state.renderTypes[dataValue.dataElement] ?: optionSet?.computeRenderType()
    val isHidden = state.hiddenFields.contains(dataValue.dataElement)
    val isDisabledByRule = state.disabledFields.contains(dataValue.dataElement)
    val isDisabledByMetadata = state.metadataDisabledFields.contains(dataValue.dataElement)
    val isMandatoryByRule = state.mandatoryFields.contains(dataValue.dataElement)
    val warning = state.fieldWarnings[dataValue.dataElement]
    val error = state.fieldErrors[dataValue.dataElement]
    val calculatedValue = state.calculatedValues[dataValue.dataElement]

    if (isHidden) return

    val effectiveEnabled = enabled &&
        state.isEntryEditable &&
        !state.isCompleted &&
        !isDisabledByRule &&
        !isDisabledByMetadata

    val labelText = if (effectiveShowLabel) {
        val baseLabel = labelOverride?.ifBlank { null } ?: dataValue.dataElementName
        baseLabel + if (isMandatoryByRule) " *" else ""
    } else {
        ""
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (compact) 0.dp else 16.dp,
                vertical = if (compact) 2.dp else 4.dp
            )
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
                val selectedOptionCode = if (!dataValue.value.isNullOrBlank()) {
                    dataValue.value
                } else {
                    calculatedValue
                }
                when (renderType) {
                    RenderType.DROPDOWN -> {
                        com.ash.simpledataentry.presentation.dataEntry.components.OptionSetDropdown(
                            optionSet = optionSet,
                            selectedCode = selectedOptionCode,
                            title = labelText,
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
                            selectedCode = selectedOptionCode,
                            title = labelText,
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
                            selectedValue = selectedOptionCode,
                            title = labelText,
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
                            selectedCode = selectedOptionCode,
                            title = labelText,
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
                val yesNoLabel = if (labelText.isNotBlank()) labelText else dataValue.dataElementName
                if (yesNoLabel.isNotBlank()) {
                    Text(
                        text = yesNoLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
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
            dataValue.dataEntryType == DataEntryType.DATE -> {
                var showDatePicker by remember { mutableStateOf(false) }
                val isoFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false } }
                val displayFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply { isLenient = false } }
                val fallbackFormat = remember { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).apply { isLenient = false } }
                val fallbackFormatNoMillis = remember { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { isLenient = false } }
                val rawDateText = fieldState.text
                val parsedDate = remember(rawDateText) {
                    listOf(isoFormat, displayFormat, fallbackFormat, fallbackFormatNoMillis)
                        .firstNotNullOfOrNull { formatter ->
                            runCatching { formatter.parse(rawDateText) }.getOrNull()
                        }
                }
                val displayValue = if (rawDateText.isBlank()) {
                    ""
                } else {
                    parsedDate?.let { DatePickerUtils.formatDateForDisplay(it) } ?: rawDateText
                }

                OutlinedTextField(
                    value = displayValue,
                    onValueChange = {},
                    readOnly = true,
                    enabled = effectiveEnabled,
                    label = { if (labelText.isNotBlank()) Text(labelText) },
                    isError = dataValue.validationState == ValidationState.ERROR || error != null,
                    trailingIcon = {
                        IconButton(
                            onClick = { showDatePicker = true },
                            enabled = effectiveEnabled
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Pick date"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = effectiveEnabled) { showDatePicker = true }
                )

                if (showDatePicker) {
                    DatePickerDialog(
                        onDateSelected = { date ->
                            val isoValue = isoFormat.format(date)
                            viewModel.onFieldValueChange(TextFieldValue(isoValue), dataValue)
                            showDatePicker = false
                        },
                        onDismissRequest = { showDatePicker = false },
                        initialDate = parsedDate ?: Date(),
                        title = if (labelText.isNotBlank()) labelText else "Select date"
                    )
                }
            }
            dataValue.dataEntryType == DataEntryType.YES_ONLY -> {
                val yesOnlyLabel = if (labelText.isNotBlank()) labelText else dataValue.dataElementName
                val isChecked = (calculatedValue ?: dataValue.value)?.lowercase() in listOf("true", "1", "yes")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { checked ->
                            if (effectiveEnabled) {
                                val newValue = if (checked) "true" else ""
                                onValueChange(newValue)
                            }
                        },
                        enabled = effectiveEnabled
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = yesOnlyLabel,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            dataValue.dataEntryType == DataEntryType.PHONE_NUMBER -> {
                OutlinedTextField(
                    value = fieldState,
                    onValueChange = { newValue ->
                        if (effectiveEnabled) {
                            val cleaned = newValue.text.filter { it.isDigit() || it == '+' }
                            viewModel.onFieldValueChange(
                                newValue.copy(text = cleaned),
                                dataValue
                            )
                        }
                    },
                    label = { if (labelText.isNotBlank()) Text(labelText) },
                    isError = dataValue.validationState == ValidationState.ERROR || error != null,
                    enabled = effectiveEnabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            dataValue.dataEntryType == DataEntryType.NUMBER ||
                    dataValue.dataEntryType == DataEntryType.INTEGER ||
                    dataValue.dataEntryType == DataEntryType.POSITIVE_INTEGER ||
                    dataValue.dataEntryType == DataEntryType.NEGATIVE_INTEGER -> {
                InputNumber(
                    title = labelText,
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
                    title = labelText,
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
                    title = labelText,
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
    LaunchedEffect(expanded) {
        if (expanded) {
            coroutineScope.launch {
                bringIntoViewRequester.bringIntoView()
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasData) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
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
                        .padding(horizontal = 16.dp, vertical = 14.dp),
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
                            .padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
                            .bringIntoViewRequester(bringIntoViewRequester)
                    ) {
                        CompositionLocalProvider(LocalShowFieldLabel provides false) {
                            content()
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun GridRowCard(
    rowTitle: String,
    columns: List<ParsedGridField>,
    onValueChange: (String, DataValue) -> Unit,
    viewModel: DataEntryViewModel,
    enabled: Boolean
) {
    val maxItems = when {
        columns.size >= 3 -> 3
        else -> 2
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = rowTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            CompositionLocalProvider(LocalShowFieldLabel provides true) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = maxItems
                ) {
                    columns.forEach { field ->
                        Column(
                            modifier = Modifier
                                .widthIn(min = 120.dp)
                        ) {
                            DataValueField(
                                dataValue = field.dataValue,
                                onValueChange = { value -> onValueChange(value, field.dataValue) },
                                viewModel = viewModel,
                                enabled = enabled,
                                showLabel = true,
                                labelOverride = field.columnTitle,
                                compact = true
                            )
                        }
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
    hasData: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    content: @Composable () -> Unit
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable {
                    onToggleExpand()
                    if (!expanded) {
                        coroutineScope.launch {
                            bringIntoViewRequester.bringIntoView()
                        }
                    }
                },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = header,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (hasData) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
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
                .padding(start = 12.dp, top = 8.dp)
                .bringIntoViewRequester(bringIntoViewRequester)) {
                content()
            }
        }
    }
}

private fun valuesForCombo(
    valuesByCombo: Map<String, List<DataValue>>,
    comboUid: String?
): List<DataValue> = comboUid?.let { valuesByCombo[it].orEmpty() }.orEmpty()

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
    valuesByCombo: Map<String, List<DataValue>>,
    onValueChange: (String, DataValue) -> Unit,
    optionUidsToComboUid: Map<Set<String>, String>,
    viewModel: DataEntryViewModel,
    parentPath: List<String> = emptyList(),
    expandedAccordions: Map<List<String>, String?>,
    onToggle: (List<String>, String) -> Unit,
    showElementHeader: Boolean = true
) {
    if (categories.size == 1 &&
        categories.first().second.size == 1 &&
        categories.first().second.first().second.equals("default", ignoreCase = true)
    ) {
        values.forEach { dataValue ->
            DataElementRow(
                dataElementName = dataValue.dataElementName,
                fields = listOf(dataValue),
                onValueChange = onValueChange,
                viewModel = viewModel,
                showHeader = showElementHeader
            )
        }
        return
    }
    if (categories.isEmpty()) {
        values.forEach { dataValue ->
            DataElementRow(
                dataElementName = dataValue.dataElementName,
                fields = listOf(dataValue),
                onValueChange = onValueChange,
                viewModel = viewModel,
                showHeader = showElementHeader
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
    fun hasDataForOption(optionUid: String): Boolean {
        val combos = optionUidsToComboUid.filterKeys { it.contains(optionUid) }.values.toSet()
        return if (combos.isNotEmpty()) {
            values.any { !it.value.isNullOrBlank() && combos.contains(it.categoryOptionCombo) }
        } else {
            values.any { !it.value.isNullOrBlank() }
        }
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
                    // Filter from element-scoped `values` (not section-scoped `valuesByCombo`)
                    val filteredValues = if (comboUid != null)
                        values.filter { it.categoryOptionCombo == comboUid }
                    else
                        emptyList()
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
                                viewModel = viewModel,
                                showLabel = false
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
                        hasData = hasDataForOption(optionUid),
                        expanded = expanded,
                        onToggleExpand = { onToggle(parentPath, optionUid) }
                    ) {
                        val fullPath = parentPath + optionUid
                        // Use only option UIDs for combo lookup (exclude element_ prefix)
                        val comboUid = optionUidsToComboUid[optionOnlyPath(fullPath)]
                        // Filter from element-scoped `values` (not section-scoped `valuesByCombo`)
                        val filteredValues = if (comboUid != null)
                            values.filter { it.categoryOptionCombo == comboUid }
                        else
                            emptyList()
                        // Render DataValueField directly (element name already shown as first accordion)
                        filteredValues.forEach { dataValue ->
                            DataValueField(
                                dataValue = dataValue,
                                onValueChange = { value -> onValueChange(value, dataValue) },
                                viewModel = viewModel,
                                showLabel = false
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
                hasData = hasDataForOption(optionUid),
                expanded = expanded,
                onToggleExpand = { onToggle(parentPath, optionUid) }
            ) {
                val newPath = parentPath + optionUid
                // Use only option UIDs for combo lookup (exclude element_ prefix)
                val comboUid = optionUidsToComboUid[optionOnlyPath(newPath)]
                // Filter from element-scoped `values` (not section-scoped `valuesByCombo`)
                val filteredValues = if (comboUid != null)
                    values.filter { it.categoryOptionCombo == comboUid }
                else
                    values  // Keep full element values for intermediate levels
                CategoryAccordionRecursive(
                    categories = restCategories,
                    values = filteredValues,
                    valuesByCombo = valuesByCombo,
                    onValueChange = onValueChange,
                    optionUidsToComboUid = optionUidsToComboUid,
                    viewModel = viewModel,
                    parentPath = newPath,
                    expandedAccordions = expandedAccordions,
                    onToggle = onToggle,
                    showElementHeader = showElementHeader
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
    val showSyncDialog = remember { mutableStateOf(false) }
    var showPostSaveDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val syncProgress = state.detailedSyncProgress
    val navigationProgress = state.navigationProgress
    val completionProgress = state.completionProgress
    val overlayUiState: UiState<*> = remember(
        syncProgress,
        navigationProgress,
        state.completionProgress,
        state.saveInProgress,
        state.isEditMode
    ) {
        when {
            syncProgress != null -> UiState.Loading(
                LoadingOperation.Syncing(syncProgress)
            )
            navigationProgress != null -> UiState.Loading(
                LoadingOperation.Navigation(navigationProgress),
                LoadingProgress(message = navigationProgress.phaseDetail.ifBlank { navigationProgress.phaseTitle })
            )
            completionProgress != null -> UiState.Loading(
                LoadingOperation.Completing(completionProgress),
                LoadingProgress(message = completionProgress.phaseDetail.ifBlank { completionProgress.phaseTitle })
            )
            state.saveInProgress -> UiState.Loading(
                LoadingOperation.Saving(),
                LoadingProgress(
                    message = if (state.isEditMode) "Saving changes..." else "Saving data..."
                )
            )
            else -> UiState.Success(Unit)
        }
    }
    // Define onValueChange ONCE here, at the top
    val onValueChange: (String, DataValue) -> Unit = { value, dataValue ->
        viewModel.updateCurrentValue(value, dataValue.dataElement, dataValue.categoryOptionCombo)
    }

    val baseScreenNavIcon: @Composable (() -> Unit) = {
        IconButton(onClick = {
            if (!state.saveInProgress) {
                navController.popBackStack()
            }
        }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back"
            )
        }
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

    if (showPostSaveDialog) {
        AlertDialog(
            onDismissRequest = { showPostSaveDialog = false },
            title = { Text("Saved!") },
            text = { Text("Do you want to check data quality?") },
            confirmButton = {
                TextButton(onClick = {
                    showPostSaveDialog = false
                    viewModel.startValidationForCompletion()
                }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPostSaveDialog = false }) {
                    Text("No")
                }
            }
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

    LaunchedEffect(state.currentSectionIndex) {
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
    
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
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
                showPostSaveDialog = true
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
    val attrComboName = state.attributeOptionComboName.ifBlank {
        state.attributeOptionCombos.find { it.first == attributeOptionCombo }?.second.orEmpty()
    }
    val showAttrComboName = attrComboName.isNotBlank() &&
        !attrComboName.equals("default", ignoreCase = true)
    val entryTitle = buildString {
        append(java.net.URLDecoder.decode(datasetName, "UTF-8"))
        append(" - ")
        append(period.replace("Period(id=", "").replace(")", ""))
        if (showAttrComboName) {
            append(" - ")
            append(attrComboName)
        }
    }
    BaseScreen(
        title = entryTitle,
        subtitle = null,
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
            // Keep sync button in the top bar
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
        },
        floatingActionButton = {
            if (state.isCompleted) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (!state.saveInProgress && state.isEntryEditable) {
                            viewModel.markDatasetIncomplete { success, message ->
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        message ?: if (success) "Report reopened for editing." else "Failed to reopen report."
                                    )
                                }
                            }
                        }
                    },
                    text = { Text("Reopen for editing") },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Reopen report"
                        )
                    },
                    containerColor = if (state.isEntryEditable) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (state.isEntryEditable) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    expanded = true
                )
            } else {
                FloatingActionButton(
                    onClick = {
                        if (!state.saveInProgress && state.isEntryEditable) {
                            viewModel.saveAllDataValues(context)
                        }
                    },
                    containerColor = if (state.saveInProgress || !state.isEntryEditable) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    contentColor = if (state.saveInProgress || !state.isEntryEditable) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Save entry"
                    )
                }
            }
        }
    ) {
        AdaptiveLoadingOverlay(
            uiState = overlayUiState,
            modifier = Modifier.fillMaxSize()
        ) {
            val sectionNames = remember(state.dataElementGroupedSections) {
                state.dataElementGroupedSections.keys.toList()
            }
            val currentSectionName = sectionNames.getOrNull(state.currentSectionIndex) ?: "Section"
            val subsectionGroups = remember(state.sectionGroupingStrategies, currentSectionName) {
                state.sectionGroupingStrategies[currentSectionName]
                    ?.filter { it.shouldRenderAsGroup() }
                    ?.filter { it.groupTitle.isNotBlank() }
                    ?.filter {
                        it.groupType != GroupType.RADIO_GROUP &&
                            it.groupType != GroupType.CHECKBOX_GROUP
                    }
                    ?.filter { !it.groupTitle.equals("related fields", ignoreCase = true) }
                    ?.distinctBy { it.groupTitle }
                    ?: emptyList()
            }
            val subsectionTitles = remember(subsectionGroups) {
                subsectionGroups.map { it.groupTitle }
            }
            var subsectionIndex by remember(currentSectionName, subsectionTitles) { mutableStateOf(0) }
            if (subsectionIndex !in subsectionTitles.indices) {
                subsectionIndex = 0
            }
            val focusSubsection: (Int) -> Unit = focusSubsection@{ index ->
                val group = subsectionGroups.getOrNull(index) ?: return@focusSubsection
                val targetElementId = group.members.firstOrNull()?.dataElement ?: return@focusSubsection
                val elementKey = "element_$targetElementId"
                expandedAccordions.value = mapOf(emptyList<String>() to elementKey)
            }
            var initializedSectionIndex by remember(currentParams) { mutableStateOf<Int?>(null) }
            LaunchedEffect(state.currentSectionIndex, currentParams, state.dataElementGroupedSections.isNotEmpty()) {
                if (state.dataElementGroupedSections.isEmpty()) return@LaunchedEffect
                val sectionName = sectionNames.getOrNull(state.currentSectionIndex) ?: return@LaunchedEffect
                val elementGroups = state.dataElementGroupedSections[sectionName] ?: return@LaunchedEffect
                val firstElementId = elementGroups.keys.firstOrNull() ?: return@LaunchedEffect
                val elementKey = "element_$firstElementId"
                val shouldReset = initializedSectionIndex != state.currentSectionIndex ||
                    expandedAccordions.value[emptyList()] == null
                if (shouldReset && expandedAccordions.value[emptyList()] != elementKey) {
                    expandedAccordions.value = mapOf(emptyList<String>() to elementKey)
                }
                initializedSectionIndex = state.currentSectionIndex
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    val nonEditableMessage = when (state.nonEditableReason) {
                        org.hisp.dhis.android.core.dataset.DataSetNonEditableReason.CLOSED ->
                            "Reporting period is closed. This report is read-only."
                        org.hisp.dhis.android.core.dataset.DataSetNonEditableReason.EXPIRED ->
                            "Reporting period is expired. This report is read-only."
                        org.hisp.dhis.android.core.dataset.DataSetNonEditableReason.NO_DATASET_DATA_WRITE_ACCESS ->
                            "You do not have write access for this dataset."
                        org.hisp.dhis.android.core.dataset.DataSetNonEditableReason.NO_ATTRIBUTE_OPTION_COMBO_ACCESS ->
                            "You do not have access to the selected attribute option combo."
                        org.hisp.dhis.android.core.dataset.DataSetNonEditableReason.ORGUNIT_IS_NOT_IN_CAPTURE_SCOPE ->
                            "Organisation unit is not in data capture scope."
                        org.hisp.dhis.android.core.dataset.DataSetNonEditableReason.ATTRIBUTE_OPTION_COMBO_NO_ASSIGN_TO_ORGUNIT ->
                            "Attribute option combo is not assigned to this organisation unit."
                        org.hisp.dhis.android.core.dataset.DataSetNonEditableReason.PERIOD_IS_NOT_IN_ORGUNIT_RANGE ->
                            "Period is not within the organisation unit range."
                        org.hisp.dhis.android.core.dataset.DataSetNonEditableReason.PERIOD_IS_NOT_IN_ATTRIBUTE_OPTION_RANGE ->
                            "Period is not within the attribute option range."
                        null -> null
                    }

                    if (!state.isEntryEditable) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Editing locked",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = nonEditableMessage ?: "This report is read-only based on server metadata.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (state.isCompleted) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Report completed",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Reopen to edit this report.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    SectionNavigationBar(
                        currentSection = Section(currentSectionName),
                        currentSubsection = subsectionTitles.getOrNull(subsectionIndex)
                            ?.let { Subsection(it) },
                        sectionIndex = state.currentSectionIndex.coerceAtLeast(0),
                        totalSections = state.totalSections.coerceAtLeast(1),
                        onPreviousSection = { viewModel.goToPreviousSection() },
                        onNextSection = { viewModel.goToNextSection() },
                        onPreviousSubsection = {
                            val nextIndex = (subsectionIndex - 1).coerceAtLeast(0)
                            subsectionIndex = nextIndex
                            focusSubsection(nextIndex)
                        },
                        onNextSubsection = {
                            val nextIndex = (subsectionIndex + 1).coerceAtMost(subsectionTitles.lastIndex)
                            subsectionIndex = nextIndex
                            focusSubsection(nextIndex)
                        },
                        hasSubsections = subsectionTitles.isNotEmpty()
                    )
                    state.lastSyncTime?.let { lastSync ->
                        Text(
                            text = "Last sync: ${formatRelativeTime(lastSync)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 8.dp)
                        )
                    }

                    val showFormContent = !state.isLoading && isUIReady
                    when {
                        !showFormContent -> {
                            Box(modifier = Modifier.weight(1f))
                        }
                        state.dataValues.isEmpty() -> {
                            Text(
                                text = "No data elements found for this dataset/period/org unit.",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                        else -> {
                            LazyColumn(
                                state = listState,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(bottom = 64.dp)
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

                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .bringIntoViewRequester(bringIntoViewRequester),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surface
                                            ),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            // Section Header Accordion
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 72.dp, max = 96.dp)
                                                    .clickable {
                                                        viewModel.setCurrentSectionIndex(sectionIndex)
                                                        subsectionIndex = 0
                                                        if (!sectionIsExpanded) {
                                                            coroutineScope.launch {
                                                                bringIntoViewRequester.bringIntoView()
                                                            }
                                                        }
                                                    }
                                                    .padding(horizontal = 16.dp, vertical = 14.dp),
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
                                                        lineHeight = 20.sp,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "($elementsWithData/$totalElements elements)",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
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

                                            // Section Content - Use key to prevent unnecessary recomposition
                                            if (sectionIsExpanded) {
                                                AnimatedVisibility(
                                                    visible = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    // Memoize the section content to prevent recomposition on every animation frame
                                                    val sectionContent = remember(sectionName, elementGroups, state.radioButtonGroups) {
                                                        elementGroups
                                                    }

                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
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

                                                                val parsedFields = remember(individualFields) {
                                                                    individualFields.mapNotNull { dataValue ->
                                                                        val parsed = parseGridLabel(dataValue.dataElementName) ?: return@mapNotNull null
                                                                        val rowKey = normalizeRowKey(parsed.first)
                                                                        ParsedGridField(
                                                                            rowTitle = parsed.first,
                                                                            rowKey = rowKey,
                                                                            columnTitle = parsed.second,
                                                                            dataValue = dataValue
                                                                        )
                                                                    }
                                                                }
                                                                val gridRowCounts = remember(parsedFields) {
                                                                    parsedFields.groupingBy { it.rowKey }.eachCount()
                                                                }
                                                                val gridRowTitles = remember(gridRowCounts) {
                                                                    gridRowCounts.filter { it.value >= 2 }.keys
                                                                }
                                                                val parsedByKey = remember(parsedFields) {
                                                                    parsedFields.associateBy {
                                                                        "${it.dataValue.dataElement}|${it.dataValue.categoryOptionCombo}"
                                                                    }
                                                                }
                                                                val gridColumnsByRow = remember(individualFields, parsedByKey, gridRowTitles) {
                                                                    val map = LinkedHashMap<String, MutableList<ParsedGridField>>()
                                                                    individualFields.forEach { dataValue ->
                                                                        val key = "${dataValue.dataElement}|${dataValue.categoryOptionCombo}"
                                                                        val parsed = parsedByKey[key]
                                                                        if (parsed != null && parsed.rowKey in gridRowTitles) {
                                                                            map.getOrPut(parsed.rowKey) { mutableListOf() }.add(parsed)
                                                                        }
                                                                    }
                                                                    map
                                                                }

                                                                val renderedRows = mutableSetOf<String>()
                                                                individualFields.forEach { dataValue ->
                                                                    val key = "${dataValue.dataElement}|${dataValue.categoryOptionCombo}"
                                                                    val parsed = parsedByKey[key]
                                                                    if (parsed != null && parsed.rowKey in gridRowTitles) {
                                                                        if (renderedRows.add(parsed.rowKey)) {
                                                                            val columns = gridColumnsByRow[parsed.rowKey].orEmpty()
                                                                            val rowTitle = columns.firstOrNull()?.rowTitle ?: parsed.rowTitle
                                                                            key("grid_${parsed.rowKey}") {
                                                                                GridRowCard(
                                                                                    rowTitle = rowTitle,
                                                                                    columns = columns,
                                                                                    onValueChange = onValueChange,
                                                                                    viewModel = viewModel,
                                                                                    enabled = state.isEntryEditable && !state.isCompleted
                                                                                )
                                                                            }
                                                                        }
                                                                    } else {
                                                                        key("field_${dataValue.dataElement}_${dataValue.categoryOptionCombo}") {
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
                                                            val sectionValues = sectionContent.values.flatten()
                                                            SectionContent(
                                                                sectionName = sectionName,
                                                                values = sectionValues,
                                                                valuesByCombo = state.valuesByCombo,
                                                                valuesByElement = state.valuesByElement,
                                                                dataElementsForSection = state.dataElementsBySection[sectionName].orEmpty(),
                                                                categoryComboStructures = state.categoryComboStructures,
                                                                optionUidsToComboUidByCombo = state.optionUidsToComboUid,
                                                                onValueChange = onValueChange,
                                                                viewModel = viewModel,
                                                                expandedAccordions = expandedAccordions.value,
                                                                onToggle = onAccordionToggle,
                                                                onElementSelected = { dataElementId ->
                                                                    val targetTitle = subsectionGroups
                                                                        .firstOrNull { group ->
                                                                            group.members.any { it.dataElement == dataElementId }
                                                                        }
                                                                        ?.groupTitle
                                                                    val targetIndex = subsectionTitles.indexOf(targetTitle)
                                                                    if (targetIndex >= 0) {
                                                                        subsectionIndex = targetIndex
                                                                    }
                                                                }
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
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
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
