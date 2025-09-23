package com.ash.simpledataentry.presentation.tracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ash.simpledataentry.presentation.core.BaseScreen
import com.ash.simpledataentry.presentation.core.DatePickerDialog
import com.ash.simpledataentry.presentation.core.LoadingState
import com.ash.simpledataentry.domain.model.TrackedEntityAttributeValue
import java.text.SimpleDateFormat
import java.util.*

/**
 * Screen for creating and editing tracker enrollments
 * Handles both new enrollments and editing existing ones
 */
@Composable
fun TrackerEnrollmentScreen(
    navController: NavController,
    programId: String,
    programName: String,
    enrollmentId: String? = null, // null for new enrollment
    viewModel: TrackerEnrollmentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Initialize the view model with parameters
    LaunchedEffect(programId, enrollmentId) {
        if (enrollmentId != null) {
            viewModel.loadEnrollment(enrollmentId)
        } else {
            viewModel.initializeNewEnrollment(programId)
        }
    }

    BaseScreen(
        title = if (enrollmentId != null) "Edit Enrollment" else "New Enrollment",
        subtitle = programName,
        navController = navController,
        actions = {
            if (uiState.canSave) {
                IconButton(
                    onClick = {
                        viewModel.saveEnrollment()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save enrollment"
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    LoadingState(message = "Loading enrollment...")
                }
                uiState.error != null -> {
                    ErrorContent(
                        error = uiState.error!!,
                        onRetry = {
                            if (enrollmentId != null) {
                                viewModel.loadEnrollment(enrollmentId)
                            } else {
                                viewModel.initializeNewEnrollment(programId)
                            }
                        }
                    )
                }
                else -> {
                    EnrollmentFormContent(
                        uiState = uiState,
                        onEnrollmentDateChanged = viewModel::updateEnrollmentDate,
                        onIncidentDateChanged = viewModel::updateIncidentDate,
                        onAttributeValueChanged = viewModel::updateAttributeValue,
                        onOrganisationUnitChanged = viewModel::updateOrganisationUnit
                    )
                }
            }

            // Show save success message
            if (uiState.saveSuccess) {
                LaunchedEffect(uiState.saveSuccess) {
                    navController.popBackStack()
                }
            }
        }
    }
}

@Composable
private fun EnrollmentFormContent(
    uiState: TrackerEnrollmentUiState,
    onEnrollmentDateChanged: (Date) -> Unit,
    onIncidentDateChanged: (Date?) -> Unit,
    onAttributeValueChanged: (String, String) -> Unit,
    onOrganisationUnitChanged: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Enrollment Information Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Enrollment Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Organisation Unit Selection
                    OrganisationUnitSelector(
                        selectedOrgUnitId = uiState.selectedOrganisationUnitId,
                        orgUnits = uiState.availableOrganisationUnits,
                        onOrgUnitSelected = onOrganisationUnitChanged
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Enrollment Date
                    DateField(
                        label = "Enrollment Date",
                        date = uiState.enrollmentDate,
                        onDateSelected = onEnrollmentDateChanged,
                        icon = Icons.Default.DateRange,
                        required = true
                    )

                    // Incident Date (if program supports it)
                    if (uiState.supportsIncidentDate) {
                        Spacer(modifier = Modifier.height(12.dp))

                        DateField(
                            label = uiState.incidentDateLabel ?: "Incident Date",
                            date = uiState.incidentDate,
                            onDateSelected = onIncidentDateChanged,
                            icon = Icons.Default.DateRange,
                            required = false
                        )
                    }
                }
            }
        }

        // Tracked Entity Attributes Section
        if (uiState.trackedEntityAttributes.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tracked Entity Attributes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Individual attribute items
            items(uiState.trackedEntityAttributes) { attribute ->
                AttributeField(
                    attribute = attribute,
                    value = uiState.attributeValues[attribute.id] ?: "",
                    onValueChanged = { newValue ->
                        onAttributeValueChanged(attribute.id, newValue)
                    }
                )
            }
        }

        // Validation errors
        if (uiState.validationErrors.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Please fix the following errors:",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        uiState.validationErrors.forEach { error ->
                            Text(
                                text = "• $error",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrganisationUnitSelector(
    selectedOrgUnitId: String?,
    orgUnits: List<com.ash.simpledataentry.domain.model.OrganisationUnit>,
    onOrgUnitSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedOrgUnit = orgUnits.find { it.id == selectedOrgUnitId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedOrgUnit?.name ?: "Select Organisation Unit",
            onValueChange = { },
            readOnly = true,
            label = { Text("Organisation Unit") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            orgUnits.forEach { orgUnit ->
                DropdownMenuItem(
                    text = { Text(orgUnit.name) },
                    onClick = {
                        onOrgUnitSelected(orgUnit.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun DateField(
    label: String,
    date: Date?,
    onDateSelected: (Date) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    required: Boolean = false
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    OutlinedTextField(
        value = date?.let { dateFormatter.format(it) } ?: "",
        onValueChange = { },
        readOnly = true,
        label = {
            Text(if (required) "$label *" else label)
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null
            )
        },
        modifier = Modifier
            .fillMaxWidth(),
        onClick = { showDatePicker = true }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDateSelected = { selectedDate ->
                onDateSelected(selectedDate)
                showDatePicker = false
            },
            onDismissRequest = { showDatePicker = false },
            initialDate = date ?: Date()
        )
    }
}

@Composable
private fun AttributeField(
    attribute: com.ash.simpledataentry.domain.model.TrackedEntityAttribute,
    value: String,
    onValueChanged: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChanged,
                label = {
                    Text(
                        if (attribute.mandatory) "${attribute.displayName} *"
                        else attribute.displayName
                    )
                },
                placeholder = {
                    Text(attribute.description ?: "Enter ${attribute.displayName}")
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = attribute.valueType != "LONG_TEXT"
            )

            if (attribute.description != null && attribute.description != attribute.displayName) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = attribute.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Error loading enrollment",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}