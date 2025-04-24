package com.example.simplede.presentation.features.dataEntry


import android.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.simplede.presentation.components.BaseScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNewEntryScreen(
    navController: NavController,
    datasetId: String,
    datasetName: String,
    viewModel: DataEntryViewModel
) {
    BaseScreen(
        title = "Create New Entry",
        navController = navController
    ) {
        var orgUnit by remember { mutableStateOf("") }
        var period by remember { mutableStateOf("") }
        var attribute by remember { mutableStateOf("") }
        var expandedOrgUnit by remember { mutableStateOf(false) }
        var expandedPeriod by remember { mutableStateOf(false) }
        var expandedAttribute by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Clipboard with plus icon
            Icon(
                painter = painterResource(id = R.drawable.ic_menu_edit),
                contentDescription = "New Entry",
                modifier = Modifier.size(48.dp)
            )

            // Organization Unit Dropdown
            ExposedDropdownMenuBox(
                expanded = expandedOrgUnit,
                onExpandedChange = { expandedOrgUnit = !expandedOrgUnit }
            ) {
                OutlinedTextField(
                    value = orgUnit,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Organization Unit") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedOrgUnit) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expandedOrgUnit,
                    onDismissRequest = { expandedOrgUnit = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Sample Org Unit") },
                        onClick = {
                            orgUnit = "Sample Org Unit"
                            expandedOrgUnit = false
                        }
                    )
                }
            }

            // Period Picker Dropdown
            ExposedDropdownMenuBox(
                expanded = expandedPeriod,
                onExpandedChange = { expandedPeriod = !expandedPeriod }
            ) {
                OutlinedTextField(
                    value = period,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Period") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPeriod) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expandedPeriod,
                    onDismissRequest = { expandedPeriod = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Daily") },
                        onClick = {
                            period = "Daily"
                            expandedPeriod = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Weekly") },
                        onClick = {
                            period = "Weekly"
                            expandedPeriod = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Monthly") },
                        onClick = {
                            period = "Monthly"
                            expandedPeriod = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Yearly") },
                        onClick = {
                            period = "Yearly"
                            expandedPeriod = false
                        }
                    )
                }
            }

            // Attribute Dropdown (Optional)
            ExposedDropdownMenuBox(
                expanded = expandedAttribute,
                onExpandedChange = { expandedAttribute = !expandedAttribute }
            ) {
                OutlinedTextField(
                    value = attribute,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Attribute (Optional)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAttribute) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expandedAttribute,
                    onDismissRequest = { expandedAttribute = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Default Value") },
                        onClick = {
                            attribute = "Default Value"
                            expandedAttribute = false
                        }
                    )
                }
            }
        }

        // Next button - only shown when all required fields are filled
        if (orgUnit.isNotEmpty() && period.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomEnd
            ) {
                Button(
                    onClick = {
                        val state = viewModel.state.value
                        val encodedDatasetName = java.net.URLEncoder.encode(datasetName, "UTF-8")
                        val encodedPeriod = java.net.URLEncoder.encode(period, "UTF-8")
                        val encodedAttributeOptionCombo = java.net.URLEncoder.encode(attribute.ifEmpty { "default" }, "UTF-8")

                        navController.navigate(
                            "EditEntry/$datasetId/${state.instanceId}/$encodedDatasetName/$encodedPeriod/$encodedAttributeOptionCombo"
                        )
                    },
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text("Next")
                }
            }
        }
    }
}