@file:OptIn(ExperimentalMaterial3Api::class)

package com.ash.simpledataentry.presentation.tracker

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ash.simpledataentry.presentation.core.DetailedSyncOverlay
import com.ash.simpledataentry.presentation.core.FullScreenLoader
import com.ash.simpledataentry.presentation.core.DatePickerDialog
import com.ash.simpledataentry.domain.model.DataValue
import com.ash.simpledataentry.domain.model.DataEntryType
import org.hisp.dhis.mobile.ui.designsystem.component.InputText
import org.hisp.dhis.mobile.ui.designsystem.component.InputNumber
import org.hisp.dhis.mobile.ui.designsystem.component.InputShellState
import androidx.compose.ui.text.input.TextFieldValue
import com.ash.simpledataentry.presentation.datasetInstances.SyncConfirmationDialog
import com.ash.simpledataentry.presentation.datasetInstances.SyncOptions
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun EventCaptureScreen(
    viewModel: EventCaptureViewModel = hiltViewModel(),
    navController: NavController,
    programId: String,
    programStageId: String? = null,
    enrollmentId: String? = null,
    eventId: String? = null
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var showSaveDialog by remember { mutableStateOf(false) }
    var showSyncDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showOrgUnitPicker by remember { mutableStateOf(false) }
    val pendingNavAction = remember { mutableStateOf<(() -> Unit)?>(null) }

    // Initialize event data
    LaunchedEffect(programId, programStageId, enrollmentId, eventId) {
        viewModel.initializeEvent(programId, programStageId, enrollmentId, eventId)
    }

    // Reuse EditEntry unsaved changes detection pattern
    val hasUnsavedChanges = state.dataValues.any { it.value != null && it.value!!.isNotBlank() } && !state.saveSuccess

    // Back handler for unsaved changes
    BackHandler(enabled = hasUnsavedChanges && !state.saveInProgress) {
        showSaveDialog = true
        pendingNavAction.value = { navController.popBackStack() }
    }

    // Navigation icon with unsaved changes protection
    val navigationIcon: @Composable (() -> Unit) = {
        IconButton(onClick = {
            if (state.saveInProgress) return@IconButton
            if (hasUnsavedChanges) {
                showSaveDialog = true
                pendingNavAction.value = { navController.popBackStack() }
            } else {
                navController.popBackStack()
            }
        }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back"
            )
        }
    }

    // Save confirmation dialog (reuse EditEntry pattern)
    if (showSaveDialog && hasUnsavedChanges) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved changes. Save before leaving?") },
            confirmButton = {
                TextButton(onClick = {
                    showSaveDialog = false
                    viewModel.saveEvent()
                    pendingNavAction.value?.invoke()
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showSaveDialog = false
                        pendingNavAction.value?.invoke()
                    }) { Text("Discard") }
                    TextButton(onClick = {
                        showSaveDialog = false
                    }) { Text("Cancel") }
                }
            }
        )
    }

    // Sync confirmation dialog (reuse EditEntry pattern)
    if (showSyncDialog) {
        SyncConfirmationDialog(
            syncOptions = SyncOptions(
                uploadLocalData = true,
                localInstanceCount = 1,
                isEditEntryContext = false
            ),
            onConfirm = { _ ->
                viewModel.syncEvent()
                showSyncDialog = false
            },
            onDismiss = {
                showSyncDialog = false
            }
        )
    }

    // Date picker dialog
    if (showDatePicker) {
        DatePickerDialog(
            onDateSelected = { date ->
                viewModel.updateEventDate(date)
                showDatePicker = false
            },
            onDismissRequest = { showDatePicker = false },
            initialDate = state.eventDate ?: Date()
        )
    }

    // Organization unit picker dialog
    if (showOrgUnitPicker) {
        AlertDialog(
            onDismissRequest = { showOrgUnitPicker = false },
            title = { Text("Select Organization Unit") },
            text = {
                LazyColumn {
                    items(state.availableOrganisationUnits.size) { index ->
                        val orgUnit = state.availableOrganisationUnits[index]
                        TextButton(
                            onClick = {
                                viewModel.updateOrganisationUnit(orgUnit.id)
                                showOrgUnitPicker = false
                            }
                        ) {
                            Text(orgUnit.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showOrgUnitPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Show snackbar messages (reuse EditEntry pattern)
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    // Main screen with Material 3 Scaffold (reuse TrackerEnrollment pattern)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.programName.ifBlank { "Event Capture" },
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (state.programStageName.isNotBlank()) {
                            Text(
                                text = state.programStageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = navigationIcon,
                actions = {
                    // Save button
                    IconButton(
                        onClick = { viewModel.saveEvent() },
                        enabled = state.canSave && !state.saveInProgress
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
                                contentDescription = "Save Event",
                                tint = if (state.canSave) MaterialTheme.colorScheme.onSurface
                                      else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }

                    // Sync button
                    IconButton(
                        onClick = { showSyncDialog = true },
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

                    // Complete button (if applicable)
                    if (state.isEditMode) {
                        IconButton(
                            onClick = {
                                // Toggle completion status - implement if needed
                            },
                            enabled = !state.saveInProgress
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = if (state.isCompleted) "Mark Incomplete" else "Mark Complete",
                                tint = if (state.isCompleted) MaterialTheme.colorScheme.primary
                                      else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        // Sync overlay (reuse EditEntry pattern)
        DetailedSyncOverlay(
            progress = state.detailedSyncProgress,
            onNavigateBack = { viewModel.clearMessages() },
            onCancel = { viewModel.clearMessages() },
            modifier = Modifier.fillMaxSize()
        ) {
            if (state.isLoading) {
                FullScreenLoader(
                    message = "Loading event...",
                    isVisible = true
                )
            } else if (state.error != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Error loading event",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = state.error ?: "Unknown error",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Event metadata section
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Event Details",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                // Event date picker
                                OutlinedTextField(
                                    value = state.eventDate?.let {
                                        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(it)
                                    } ?: "",
                                    onValueChange = { },
                                    label = { Text("Event Date *") },
                                    readOnly = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    trailingIcon = {
                                        TextButton(onClick = { showDatePicker = true }) {
                                            Text("Select")
                                        }
                                    }
                                )

                                // Organization unit picker (for standalone events)
                                if (state.enrollmentId == null && state.availableOrganisationUnits.isNotEmpty()) {
                                    OutlinedTextField(
                                        value = state.availableOrganisationUnits.find {
                                            it.id == state.selectedOrganisationUnitId
                                        }?.name ?: "",
                                        onValueChange = { },
                                        label = { Text("Organisation Unit *") },
                                        readOnly = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        trailingIcon = {
                                            TextButton(onClick = { showOrgUnitPicker = true }) {
                                                Text("Select")
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Data values section - reuse EditEntry DataValueField components
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Event Data",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                if (state.dataValues.isEmpty()) {
                                    Text(
                                        text = "No data elements found for this event",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    // Render each data value field using DHIS2 UI components
                                    state.dataValues.forEach { dataValue ->
                                        EventDataValueField(
                                            dataValue = dataValue,
                                            onValueChange = { value ->
                                                viewModel.updateDataValue(value, dataValue)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Validation errors
                    if (state.validationErrors.isNotEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Validation Errors",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    state.validationErrors.forEach { error ->
                                        Text(
                                            text = "• $error",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Success message
                    state.successMessage?.let { message ->
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventDataValueField(
    dataValue: DataValue,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val value = dataValue.value ?: ""
    var textFieldValue by remember(value) {
        mutableStateOf(TextFieldValue(value))
    }

    // Update text field when external value changes
    LaunchedEffect(value) {
        if (textFieldValue.text != value) {
            textFieldValue = TextFieldValue(value)
        }
    }

    when (dataValue.dataEntryType) {
        DataEntryType.NUMBER -> {
            InputNumber(
                title = dataValue.dataElementName,
                state = InputShellState.UNFOCUSED,
                inputTextFieldValue = textFieldValue,
                onValueChanged = { newValue: TextFieldValue? ->
                    newValue?.let { textField ->
                        textFieldValue = textField
                        onValueChange(textField.text)
                    }
                },
                modifier = modifier.padding(vertical = 4.dp)
            )
        }
        DataEntryType.PERCENTAGE -> {
            InputNumber(
                title = dataValue.dataElementName,
                state = InputShellState.UNFOCUSED,
                inputTextFieldValue = textFieldValue,
                onValueChanged = { newValue: TextFieldValue? ->
                    newValue?.let { textField ->
                        textFieldValue = textField
                        onValueChange(textField.text)
                    }
                },
                modifier = modifier.padding(vertical = 4.dp)
            )
        }
        DataEntryType.YES_NO -> {
            // Use a simple dropdown for Yes/No since YesNoField is not available
            var expanded by remember { mutableStateOf(false) }
            val yesNoOptions = listOf("", "true", "false")
            val selectedValue = when (value.lowercase()) {
                "true", "1", "yes" -> "true"
                "false", "0", "no" -> "false"
                else -> ""
            }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = modifier.padding(vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = when (selectedValue) {
                        "true" -> "Yes"
                        "false" -> "No"
                        else -> "Select..."
                    },
                    onValueChange = { },
                    readOnly = true,
                    label = { Text(dataValue.dataElementName) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Select...") },
                        onClick = {
                            onValueChange("")
                            expanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Yes") },
                        onClick = {
                            onValueChange("true")
                            expanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("No") },
                        onClick = {
                            onValueChange("false")
                            expanded = false
                        }
                    )
                }
            }
        }
        else -> {
            InputText(
                title = dataValue.dataElementName,
                state = InputShellState.UNFOCUSED,
                inputTextFieldValue = textFieldValue,
                onValueChanged = { newValue: TextFieldValue? ->
                    newValue?.let { textField ->
                        textFieldValue = textField
                        onValueChange(textField.text)
                    }
                },
                modifier = modifier.padding(vertical = 4.dp)
            )
        }
    }
}

