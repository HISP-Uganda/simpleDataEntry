package com.ash.simpledataentry.presentation.dataEntry

import android.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ash.simpledataentry.domain.model.OrganisationUnit
import com.ash.simpledataentry.domain.model.Period
import com.ash.simpledataentry.presentation.core.BaseScreen
import com.ash.simpledataentry.ui.theme.DHIS2Blue
import com.ash.simpledataentry.ui.theme.DHIS2BlueDark
import com.ash.simpledataentry.ui.theme.DHIS2BlueLight
import java.net.URLDecoder
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNewEntryScreen(
    navController: NavController,
    datasetId: String,
    datasetName: String,
    viewModel: DataEntryViewModel = hiltViewModel()
) {
    var periods by remember { mutableStateOf<List<Period>>(emptyList()) }
    var selectedPeriod by remember { mutableStateOf("") }
    var orgUnits by remember { mutableStateOf<List<OrganisationUnit>>(emptyList()) }
    var selectedOrgUnit by remember { mutableStateOf<OrganisationUnit?>(null) }
    var defaultAttributeOptionCombo by remember { mutableStateOf("") }
    var expandedPeriod by remember { mutableStateOf(false) }
    var expandedOrgUnit by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var attributeOptionCombos by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var selectedAttributeOptionCombo by remember { mutableStateOf("") }
    var expandedAttributeOptionCombo by remember { mutableStateOf(false) }
    var showAllPeriods by remember { mutableStateOf(false) }
    val state by viewModel.state.collectAsState()
    val decodedDatasetName = remember(datasetName) { URLDecoder.decode(datasetName, "UTF-8") }

    LaunchedEffect(datasetId, showAllPeriods) {
        try {
            isLoading = true
            error = null
            periods = viewModel.getAvailablePeriods(datasetId, showAll = showAllPeriods)
            orgUnits = viewModel.getUserOrgUnits(datasetId) // Get multiple org units
            selectedOrgUnit = orgUnits.firstOrNull() // Select first org unit by default
            defaultAttributeOptionCombo = viewModel.getDefaultAttributeOptionCombo()
            attributeOptionCombos = viewModel.getAttributeOptionCombos(datasetId)
            selectedAttributeOptionCombo = attributeOptionCombos.firstOrNull()?.first
                ?: defaultAttributeOptionCombo
            isLoading = false
        } catch (e: Exception) {
            error = e.message ?: "Failed to load data"
            isLoading = false
        }
    }

    BaseScreen(
        title = "Create New Entry",
        subtitle = decodedDatasetName,
        navController = navController
    ) {
        val gradientBrush = Brush.verticalGradient(
            colors = listOf(DHIS2Blue, DHIS2BlueDark)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBrush)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else if (error != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = error!!, color = Color.White)
                }
            } else {
                val resolvedAttributeOptionCombo = selectedAttributeOptionCombo.ifBlank {
                    defaultAttributeOptionCombo
                }
                val canContinue = selectedOrgUnit != null &&
                    selectedPeriod.isNotEmpty() &&
                    resolvedAttributeOptionCombo.isNotEmpty()
                val selectedAttrComboName = attributeOptionCombos
                    .find { it.first == resolvedAttributeOptionCombo }
                    ?.second
                    .orEmpty()
                val isDefaultOnlyAttrCombo = attributeOptionCombos.isEmpty() ||
                    (attributeOptionCombos.size == 1 &&
                        selectedAttrComboName.equals("default", ignoreCase = true))

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(DHIS2BlueLight, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_menu_edit),
                                        contentDescription = "New Entry",
                                        modifier = Modifier.size(28.dp),
                                        tint = DHIS2Blue
                                    )
                                }
                                Column {
                                    Text(
                                        text = "New Entry",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Select the context to begin.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                color = DHIS2BlueLight.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Offline mode supported. Entries will sync when connected.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }

                            // Organization Unit Dropdown
                            ExposedDropdownMenuBox(
                                expanded = expandedOrgUnit,
                                onExpandedChange = { expandedOrgUnit = !expandedOrgUnit }
                            ) {
                                OutlinedTextField(
                                    value = selectedOrgUnit?.name ?: "Select Organization Unit",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Organization Unit") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedOrgUnit) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedOrgUnit,
                                    onDismissRequest = { expandedOrgUnit = false }
                                ) {
                                    orgUnits.forEach { orgUnit ->
                                        DropdownMenuItem(
                                            text = { Text(orgUnit.name) },
                                            onClick = {
                                                selectedOrgUnit = orgUnit
                                                expandedOrgUnit = false
                                            }
                                        )
                                    }
                                }
                            }

                            ExposedDropdownMenuBox(
                                expanded = expandedPeriod,
                                onExpandedChange = { expandedPeriod = !expandedPeriod }
                            ) {
                                OutlinedTextField(
                                    value = selectedPeriod,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Period") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPeriod) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedPeriod,
                                    onDismissRequest = { expandedPeriod = false }
                                ) {
                                    periods.forEach { period ->
                                        DropdownMenuItem(
                                            text = { Text(period.id) },
                                            onClick = {
                                                selectedPeriod = period.id
                                                expandedPeriod = false
                                            }
                                        )
                                    }
                                    if (!showAllPeriods) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    "Show more periods...",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            },
                                            onClick = {
                                                showAllPeriods = true
                                                expandedPeriod = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Attribute Option Combo Dropdown
                            ExposedDropdownMenuBox(
                                expanded = expandedAttributeOptionCombo && !isDefaultOnlyAttrCombo,
                                onExpandedChange = {
                                    if (!isDefaultOnlyAttrCombo) {
                                        expandedAttributeOptionCombo = !expandedAttributeOptionCombo
                                    }
                                }
                            ) {
                                OutlinedTextField(
                                    value = selectedAttrComboName.ifBlank {
                                        if (isDefaultOnlyAttrCombo) {
                                            "Default"
                                        } else {
                                            "Select Attribute Option Combo"
                                        }
                                    },
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = !isDefaultOnlyAttrCombo,
                                    label = { Text("Attribute Option Combo") },
                                    trailingIcon = {
                                        if (!isDefaultOnlyAttrCombo) {
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAttributeOptionCombo)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = !isDefaultOnlyAttrCombo)
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedAttributeOptionCombo && !isDefaultOnlyAttrCombo,
                                    onDismissRequest = { expandedAttributeOptionCombo = false }
                                ) {
                                    attributeOptionCombos.forEach { (uid, displayName) ->
                                        DropdownMenuItem(
                                            text = { Text(displayName) },
                                            onClick = {
                                                selectedAttributeOptionCombo = uid
                                                expandedAttributeOptionCombo = false
                                            }
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    val encodedDatasetName = java.net.URLEncoder.encode(datasetName, "UTF-8")
                                    val encodedPeriod = java.net.URLEncoder.encode(selectedPeriod, "UTF-8")
                                    val encodedOrgUnit = java.net.URLEncoder.encode(selectedOrgUnit!!.id, "UTF-8")
                                    val encodedAttributeOptionCombo = java.net.URLEncoder.encode(resolvedAttributeOptionCombo, "UTF-8")
                                    navController.navigate(
                                        "EditEntry/$datasetId/$encodedPeriod/$encodedOrgUnit/$encodedAttributeOptionCombo/$encodedDatasetName"
                                    ) {
                                        popUpTo("CreateDataEntry/$datasetId/$datasetName") { inclusive = true }
                                    }
                                },
                                enabled = canContinue,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text("Continue")
                            }

                            TextButton(
                                onClick = { navController.popBackStack() },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }
    }
}
