package com.ash.simpledataentry.presentation.tracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ash.simpledataentry.presentation.core.BaseScreen
import com.ash.simpledataentry.presentation.core.DatePickerDialog
import com.ash.simpledataentry.presentation.core.DetailedSyncOverlay
import com.ash.simpledataentry.presentation.core.OverlayLoader
import com.ash.simpledataentry.domain.model.TrackedEntityAttributeValue
import org.hisp.dhis.mobile.ui.designsystem.component.*
import org.hisp.dhis.mobile.ui.designsystem.theme.DHIS2Theme
import org.hisp.dhis.mobile.ui.designsystem.theme.TextColor
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

/**
 * Screen for creating and editing tracker enrollments
 * Reuses EditEntryScreen patterns and UI components
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerEnrollmentScreen(
    navController: NavController,
    programId: String,
    programName: String,
    enrollmentId: String? = null, // null for new enrollment
    viewModel: TrackerEnrollmentViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showSaveDialog by remember { mutableStateOf(false) }

    // Initialize the view model with parameters
    LaunchedEffect(programId, enrollmentId) {
        if (enrollmentId != null) {
            viewModel.loadEnrollment(enrollmentId)
        } else {
            viewModel.initializeNewEnrollment(programId)
        }
    }

    // Handle save success navigation
    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) {
            navController.popBackStack()
        }
    }

    // Show snackbar for messages (reuse DataEntry pattern)
    LaunchedEffect(state.error, state.successMessage) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearMessages()
        }
        state.successMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditMode) "Edit Enrollment" else "New Enrollment") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Sync button (reuse DataEntry pattern)
                    IconButton(
                        onClick = { viewModel.syncEnrollment() },
                        enabled = !state.isSyncing
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (state.canSave) {
                        showSaveDialog = true
                    } else {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Please fill all required fields")
                        }
                    }
                },
                containerColor = if (state.canSave) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "Save Enrollment",
                    tint = if (state.canSave) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                state.isLoading -> {
                    OverlayLoader(
                        message = "Loading enrollment...",
                        progress = state.navigationProgress?.overallPercentage,
                        progressStep = state.navigationProgress?.phaseDetail
                    ) {
                        // Empty content for loading state
                    }
                }
                state.error != null -> {
                    ErrorContent(
                        error = state.error!!,
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
                        state = state,
                        onEnrollmentDateChanged = viewModel::updateEnrollmentDate,
                        onIncidentDateChanged = viewModel::updateIncidentDate,
                        onAttributeValueChanged = viewModel::updateAttributeValue,
                        onOrganisationUnitChanged = viewModel::updateOrganisationUnit
                    )
                }
            }

            // Enhanced sync overlay (reuse DataEntry pattern)
            DetailedSyncOverlay(
                progress = state.detailedSyncProgress,
                onNavigateBack = { navController.popBackStack() },
                onRetry = { viewModel.syncEnrollment() },
                onCancel = { viewModel.clearMessages() }
            ) {
                // Background content during sync
            }

            // Save progress overlay
            if (state.saveInProgress) {
                OverlayLoader(
                    message = if (state.isEditMode) "Updating enrollment..." else "Creating enrollment..."
                ) {
                    // Background content during save
                }
            }
        }
    }

    // Save confirmation dialog (reuse DataEntry pattern)
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(if (state.isEditMode) "Update Enrollment" else "Save Enrollment") },
            text = {
                Text(
                    if (state.isEditMode) {
                        "Update this enrollment with the current information?"
                    } else {
                        "Create new enrollment for ${state.programName}?"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveEnrollment()
                        showSaveDialog = false
                    }
                ) {
                    Text(if (state.isEditMode) "Update" else "Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EnrollmentFormContent(
    state: TrackerEnrollmentState,
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
        // Program Information Header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = state.programName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    if (state.isEditMode) {
                        Text(
                            text = "Editing existing enrollment",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

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

                    Spacer(modifier = Modifier.height(16.dp))

                    // Organisation Unit Selection
                    OrganisationUnitSelector(
                        selectedOrgUnitId = state.selectedOrganisationUnitId,
                        orgUnits = state.availableOrganisationUnits,
                        onOrgUnitSelected = onOrganisationUnitChanged,
                        enabled = !state.isEditMode // Disable editing org unit for existing enrollments
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Enrollment Date using DHIS2 UI components
                    DateField(
                        label = "Enrollment Date",
                        date = state.enrollmentDate,
                        onDateSelected = onEnrollmentDateChanged,
                        icon = Icons.Default.DateRange,
                        required = true,
                        error = state.validationErrors.any { it.contains("Enrollment date") }
                    )

                    // Incident Date (if program supports it)
                    if (state.supportsIncidentDate) {
                        Spacer(modifier = Modifier.height(16.dp))

                        DateField(
                            label = state.incidentDateLabel ?: "Incident Date",
                            date = state.incidentDate,
                            onDateSelected = { onIncidentDateChanged(it) },
                            icon = Icons.Default.DateRange,
                            required = false
                        )
                    }
                }
            }
        }

        // Tracked Entity Attributes Section (using DHIS2 UI patterns)
        if (state.trackedEntityAttributes.isNotEmpty()) {
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
                                text = "Personal Information",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Individual attribute items using DHIS2 UI components
            items(state.trackedEntityAttributes) { attribute ->
                TrackerAttributeField(
                    attribute = attribute,
                    value = state.attributeValues[attribute.id] ?: "",
                    onValueChanged = { newValue ->
                        onAttributeValueChanged(attribute.id, newValue)
                    },
                    hasError = state.validationErrors.any { it.contains(attribute.displayName) }
                )
            }
        }

        // Validation Summary (reuse DataEntry validation pattern)
        if (state.validationErrors.isNotEmpty()) {
            item {
                ValidationErrorCard(
                    errors = state.validationErrors,
                    validationState = state.validationState
                )
            }
        }

        // Bottom spacing for FAB
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrganisationUnitSelector(
    selectedOrgUnitId: String?,
    orgUnits: List<com.ash.simpledataentry.domain.model.OrganisationUnit>,
    onOrgUnitSelected: (String) -> Unit,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedOrgUnit = orgUnits.find { it.id == selectedOrgUnitId }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedOrgUnit?.name ?: "",
            onValueChange = { },
            readOnly = true,
            enabled = enabled,
            label = { Text("Organisation Unit *") },
            placeholder = { Text("Select organisation unit") },
            trailingIcon = {
                if (enabled) ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            isError = selectedOrgUnitId.isNullOrBlank()
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
    required: Boolean = false,
    error: Boolean = false
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
                contentDescription = null,
                tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDatePicker = true },
        isError = error
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDateSelected = { selectedDate ->
                onDateSelected(selectedDate)
                showDatePicker = false
            },
            onDismissRequest = { showDatePicker = false },
            initialDate = date ?: Date(),
            title = "Select $label",
            maxDate = Date() // Enrollment and incident dates cannot be in future
        )
    }
}

@Composable
private fun TrackerAttributeField(
    attribute: com.ash.simpledataentry.domain.model.TrackedEntityAttribute,
    value: String,
    onValueChanged: (String) -> Unit,
    hasError: Boolean = false
) {
    // Fix cursor jumping: Use internal state for TextFieldValue, sync only when external value changes
    var textFieldValue by remember { mutableStateOf(TextFieldValue(value)) }

    // Detect when external value changes (e.g., from ViewModel) and update internal state
    LaunchedEffect(value) {
        if (textFieldValue.text != value) {
            textFieldValue = TextFieldValue(
                text = value,
                selection = TextRange(value.length) // Place cursor at end
            )
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Use DHIS2 UI components for consistency
            when (attribute.valueType) {
                "INTEGER", "POSITIVE_INTEGER", "NEGATIVE_INTEGER", "ZERO_OR_POSITIVE_INTEGER" -> {
                    InputNumber(
                        title = if (attribute.mandatory) "${attribute.displayName} *" else attribute.displayName,
                        inputTextFieldValue = textFieldValue,
                        onValueChanged = { newValue ->
                            newValue?.let {
                                textFieldValue = it
                                onValueChanged(it.text)
                            }
                        },
                        state = if (hasError) InputShellState.ERROR else InputShellState.UNFOCUSED,
                        supportingText = listOfNotNull(
                            attribute.description?.let {
                                SupportingTextData(
                                    text = it,
                                    state = SupportingTextState.DEFAULT
                                )
                            }
                        )
                    )
                }
                "BOOLEAN", "TRUE_ONLY" -> {
                    // Switch for boolean values
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (attribute.mandatory) "${attribute.displayName} *" else attribute.displayName,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (attribute.description != null) {
                                Text(
                                    text = attribute.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = value.equals("true", ignoreCase = true),
                            onCheckedChange = { checked ->
                                onValueChanged(if (checked) "true" else "false")
                            }
                        )
                    }
                }
                else -> {
                    // Text input for all other types
                    InputText(
                        title = if (attribute.mandatory) "${attribute.displayName} *" else attribute.displayName,
                        inputTextFieldValue = textFieldValue,
                        onValueChanged = { newValue ->
                            newValue?.let {
                                textFieldValue = it
                                onValueChanged(it.text)
                            }
                        },
                        state = if (hasError) InputShellState.ERROR else InputShellState.UNFOCUSED,
                        supportingText = listOfNotNull(
                            attribute.description?.let {
                                SupportingTextData(
                                    text = it,
                                    state = SupportingTextState.DEFAULT
                                )
                            }
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ValidationErrorCard(
    errors: List<String>,
    validationState: com.ash.simpledataentry.domain.model.ValidationState
) {
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

            errors.forEach { error ->
                Text(
                    text = "• $error",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
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