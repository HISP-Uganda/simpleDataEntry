package com.ash.simpledataentry.presentation.dataEntry.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ash.simpledataentry.domain.model.Option
import com.ash.simpledataentry.domain.model.OptionSet
import com.ash.simpledataentry.domain.model.DataValue

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

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedOption?.let { it.displayName ?: it.name } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(if (isRequired) "$title *" else title) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            enabled = enabled,
            modifier = Modifier
                .menuAnchor()
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
        Text(
            text = if (isRequired) "$title *" else title,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

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
 * YES/NO toggle for boolean options
 */
@Composable
fun YesNoButtons(
    selectedValue: String?,
    title: String,
    isRequired: Boolean = false,
    enabled: Boolean = true,
    onValueChanged: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = if (isRequired) "$title *" else title,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Determine selection state
        val isYesSelected = selectedValue?.lowercase() in listOf("yes", "true", "1")
        val isNoSelected = selectedValue?.lowercase() in listOf("no", "false", "0")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // YES button
            OutlinedButton(
                onClick = { if (enabled) onValueChanged("true") },
                enabled = enabled,
                colors = if (isYesSelected) {
                    ButtonDefaults.buttonColors()  // Filled when selected
                } else {
                    ButtonDefaults.outlinedButtonColors()  // Outlined when not selected
                },
                border = if (isYesSelected) null else ButtonDefaults.outlinedButtonBorder,
                modifier = Modifier.weight(1f)
            ) {
                Text("YES")
            }

            // NO button
            OutlinedButton(
                onClick = { if (enabled) onValueChanged("false") },
                enabled = enabled,
                colors = if (isNoSelected) {
                    ButtonDefaults.buttonColors()  // Filled when selected
                } else {
                    ButtonDefaults.outlinedButtonColors()  // Outlined when not selected
                },
                border = if (isNoSelected) null else ButtonDefaults.outlinedButtonBorder,
                modifier = Modifier.weight(1f)
            ) {
                Text("NO")
            }
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

        // Add "None" option
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            RadioButton(
                selected = selectedFieldId == null,
                onClick = { if (enabled) onFieldSelected(null) },
                enabled = enabled
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "None",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        // Render each field as a radio option
        fields.forEach { field ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                RadioButton(
                    selected = field.dataElement == selectedFieldId,
                    onClick = { if (enabled) onFieldSelected(field.dataElement) },
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
