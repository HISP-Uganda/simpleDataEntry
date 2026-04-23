package com.ash.simpledataentry.presentation.dataEntry.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.ash.simpledataentry.domain.model.Option
import com.ash.simpledataentry.domain.model.OptionSet
import com.ash.simpledataentry.domain.model.DataValue
import com.ash.simpledataentry.ui.theme.LocalFormColors
import com.ash.simpledataentry.ui.theme.LocalFormDimensions

/**
 * Simple dropdown component for option sets with >4 options
 * Uses Material3 ExposedDropdownMenuBox
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionSetDropdown(
    optionSet: OptionSet,
    selectedCode: String?,
    title: String,
    isRequired: Boolean = false,
    enabled: Boolean = true,
    onOptionSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val sortedOptions = optionSet.options.sortedBy { it.sortOrder }
    val selectedOption = selectedCode?.let { code -> sortedOptions.firstOrNull { it.code == code } }

    var expanded by remember { mutableStateOf(false) }
    val formColors = LocalFormColors.current
    val formDimensions = LocalFormDimensions.current

    val labelText = if (isRequired) "$title *" else title

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedOption?.let { it.displayName ?: it.name } ?: "",
            onValueChange = {},
            readOnly = true,
            label = if (labelText.isNotBlank()) {
                { Text(labelText) }
            } else {
                null
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            enabled = enabled,
            shape = RoundedCornerShape(formDimensions.fieldCornerRadius),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = formColors.gridCellBackground,
                unfocusedContainerColor = formColors.gridCellBackground,
                disabledContainerColor = formColors.gridCellBackground.copy(alpha = 0.6f),
                focusedBorderColor = formColors.gridBorderFocused,
                unfocusedBorderColor = formColors.gridBorder,
                disabledBorderColor = formColors.gridBorder.copy(alpha = 0.4f),
                focusedLabelColor = formColors.gridHeaderText,
                unfocusedLabelColor = formColors.gridHeaderText,
                focusedTextColor = formColors.gridCellText,
                unfocusedTextColor = formColors.gridCellText,
                disabledTextColor = formColors.gridCellText.copy(alpha = 0.6f)
            ),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = enabled)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            sortedOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName ?: option.name) },
                    onClick = {
                        onOptionSelected(option.code)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Radio button group for option sets with ≤4 options
 * Uses Material3 RadioButton
 */
@Composable
fun OptionSetRadioGroup(
    optionSet: OptionSet,
    selectedCode: String?,
    title: String,
    isRequired: Boolean = false,
    enabled: Boolean = true,
    onOptionSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val sortedOptions = optionSet.options.sortedBy { it.sortOrder }

    Column(modifier = modifier) {
        val labelText = if (isRequired) "$title *" else title
        if (labelText.isNotBlank()) {
            Text(
                text = labelText,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        sortedOptions.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                RadioButton(
                    selected = option.code == selectedCode,
                    onClick = { if (enabled) onOptionSelected(option.code) },
                    enabled = enabled
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = option.displayName ?: option.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

/**
 * Simple checkbox for boolean YES/NO options
 * Checked = "1" (Yes), Unchecked = "0" (No)
 */
@Composable
fun YesNoCheckbox(
    selectedValue: String?,
    title: String,
    isRequired: Boolean = false,
    enabled: Boolean = true,
    onValueChanged: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    // Determine if checked (Yes = "1", "true", "yes")
    val isChecked = selectedValue?.lowercase() in listOf("yes", "true", "1")

    // Use remember to track local state for immediate UI feedback
    var localChecked by remember(selectedValue) { mutableStateOf(isChecked) }

    // Sync local state when selectedValue changes from parent
    LaunchedEffect(selectedValue) {
        localChecked = isChecked
    }

    val labelText = if (isRequired) "$title *" else title
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = localChecked,
            onCheckedChange = { checked ->
                if (enabled) {
                    // Update local state immediately for visual feedback
                    localChecked = checked
                    val newValue = if (checked) "1" else "0"
                    android.util.Log.d("YesNoCheckbox", "Checkbox ${if (checked) "checked" else "unchecked"} for $title, value=$newValue")
                    onValueChanged(newValue)
                } else {
                    android.util.Log.d("YesNoCheckbox", "Checkbox click ignored - disabled")
                }
            },
            enabled = enabled
        )
        Spacer(modifier = Modifier.width(8.dp))
        if (labelText.isNotBlank()) {
            Text(
                text = labelText,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Grouped radio buttons for mutually exclusive YES/NO fields
 * Used when multiple fields share the same prefix and option set
 * Only one field can be YES at a time
 */
@Composable
fun GroupedRadioButtons(
    groupTitle: String,
    fields: List<DataValue>,
    selectedFieldId: String?,  // ID of field that is "YES"
    optionSet: OptionSet,
    isRequired: Boolean = false,
    enabled: Boolean = true,
    onFieldSelected: (String?) -> Unit,  // Callback with selected field's dataElement ID
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = if (isRequired) "$groupTitle *" else groupTitle,
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Render each field as a radio option
        fields.forEach { field ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                RadioButton(
                    selected = field.dataElement == selectedFieldId,
                    onClick = {
                        if (enabled) {
                            android.util.Log.d("GroupedRadioButtons", "RadioButton clicked for ${field.dataElementName}, enabled=$enabled")
                            onFieldSelected(field.dataElement)
                        } else {
                            android.util.Log.d("GroupedRadioButtons", "RadioButton click ignored - disabled")
                        }
                    },
                    enabled = enabled
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = field.dataElementName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

/**
 * Grouped checkboxes for multi-select boolean fields detected from validation rules.
 */
@Composable
fun GroupedCheckboxes(
    groupTitle: String,
    fields: List<DataValue>,
    calculatedValues: Map<String, String>,
    isRequired: Boolean = false,
    enabled: Boolean = true,
    onFieldToggled: (DataValue, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = if (isRequired) "$groupTitle *" else groupTitle,
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(modifier = Modifier.height(8.dp))

        fields.forEach { field ->
            val resolvedValue = calculatedValues[field.dataElement] ?: field.value
            val isChecked = resolvedValue?.lowercase() in listOf("1", "true", "yes")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { checked ->
                        if (enabled) {
                            onFieldToggled(field, checked)
                        }
                    },
                    enabled = enabled
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = field.dataElementName,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
