package com.ash.simpledataentry.presentation.tracker

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.data.cache.MetadataCacheService
import com.ash.simpledataentry.data.sync.DetailedSyncProgress
import com.ash.simpledataentry.data.sync.SyncQueueManager
import com.ash.simpledataentry.data.sync.SyncStatusController
import com.ash.simpledataentry.data.sync.NetworkStateManager
import com.ash.simpledataentry.domain.grouping.ImpliedCategoryInferenceService
import com.ash.simpledataentry.domain.model.*
import com.ash.simpledataentry.domain.repository.DatasetInstancesRepository
import com.ash.simpledataentry.presentation.core.NavigationProgress
import com.ash.simpledataentry.presentation.core.LoadingOperation
import com.ash.simpledataentry.presentation.core.LoadingProgress
import com.ash.simpledataentry.presentation.core.UiError
import com.ash.simpledataentry.presentation.core.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.event.Event
import org.hisp.dhis.android.core.event.EventCreateProjection
import org.hisp.dhis.android.core.program.ProgramRuleAction
import org.hisp.dhis.android.core.program.ProgramRuleActionType
import org.hisp.dhis.android.core.program.ProgramRuleVariable
import org.hisp.dhis.android.core.program.ProgramRuleVariableSourceType
import org.hisp.dhis.rules.RuleEngineContext
import org.hisp.dhis.rules.models.AttributeType
import org.hisp.dhis.rules.models.Rule
import org.hisp.dhis.rules.models.RuleAction
import org.hisp.dhis.rules.models.RuleActionAssign
import org.hisp.dhis.rules.models.RuleActionDisplayKeyValuePair
import org.hisp.dhis.rules.models.RuleActionDisplayText
import org.hisp.dhis.rules.models.RuleActionErrorOnCompletion
import org.hisp.dhis.rules.models.RuleActionHideField
import org.hisp.dhis.rules.models.RuleActionHideSection
import org.hisp.dhis.rules.models.RuleActionSetMandatoryField
import org.hisp.dhis.rules.models.RuleActionShowError
import org.hisp.dhis.rules.models.RuleActionShowWarning
import org.hisp.dhis.rules.models.RuleActionWarningOnCompletion
import org.hisp.dhis.rules.models.RuleDataValue
import org.hisp.dhis.rules.models.RuleEvent
import org.hisp.dhis.rules.models.RuleValueType
import org.hisp.dhis.rules.models.RuleVariable
import org.hisp.dhis.rules.models.RuleVariableAttribute
import org.hisp.dhis.rules.models.RuleVariableCalculatedValue
import org.hisp.dhis.rules.models.RuleVariableCurrentEvent
import org.hisp.dhis.rules.models.RuleVariableNewestEvent
import org.hisp.dhis.rules.models.RuleVariableNewestStageEvent
import org.hisp.dhis.rules.models.RuleVariablePreviousEvent
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.text.input.TextFieldValue
import java.util.*
import javax.inject.Inject

/**
 * EventCaptureViewModel - Reuses DataEntryViewModel patterns for tracker events
 * Adapts the successful EditEntryScreen architecture for tracker event data capture
 */
data class EventCaptureState(
    // Reuse DataEntry loading and error states
    val isLoading: Boolean = false,
    val error: String? = null,
    val saveSuccess: Boolean = false,
    val canSave: Boolean = false,

    // Event metadata (similar to dataset instance metadata)
    val eventId: String? = null,
    val programId: String = "",
    val programName: String = "",
    val programStageId: String = "",
    val programStageName: String = "",
    val enrollmentId: String? = null, // null for single event programs

    // Form data (reuse EditEntry form patterns)
    val selectedOrganisationUnitId: String? = null,
    val availableOrganisationUnits: List<OrganisationUnit> = emptyList(),
    val eventDate: Date? = null,
    val dueDate: Date? = null,
    val completedDate: Date? = null,

    // Data values - reuse existing DataValue model completely
    val dataValues: List<DataValue> = emptyList(),
    val currentDataValue: DataValue? = null,

    // IMPLIED CATEGORY INFERENCE: For event/tracker programs that don't have DHIS2 category combinations
    // This detects category structure from data element names (e.g., "ANC Visit - First Trimester - Blood Pressure")
    // Map of sectionName -> ImpliedCategoryCombination (since each section can have different patterns)
    val impliedCategoriesBySection: Map<String, ImpliedCategoryCombination> = emptyMap(),
    val impliedCategoryMappingsBySection: Map<String, List<ImpliedCategoryMapping>> = emptyMap(),
    val sectionHasData: Map<String, Boolean> = emptyMap(),

    // Reuse validation patterns from DataEntry
    val validationState: ValidationState = ValidationState.VALID,
    val validationErrors: List<String> = emptyList(),
    val validationMessage: String? = null,

    // Reuse sync and progress patterns
    val saveInProgress: Boolean = false,
    val saveResult: Result<Unit>? = null,
    val isSyncing: Boolean = false,
    val detailedSyncProgress: DetailedSyncProgress? = null,
    val successMessage: String? = null,
    val isValidating: Boolean = false,
    val navigationProgress: NavigationProgress? = null,

    // Reuse form management patterns
    val isEditMode: Boolean = false,
    val currentStep: Int = 0,
    val expandedSection: String? = null,
    val isCompleted: Boolean = false,

    // Program rules
    val programRuleEffect: ProgramRuleEffect? = null
)

@HiltViewModel
class EventCaptureViewModel @Inject constructor(
    private val application: Application,
    private val sessionManager: SessionManager,
    private val repository: DatasetInstancesRepository,
    private val syncQueueManager: SyncQueueManager,
    private val networkStateManager: NetworkStateManager,
    private val impliedCategoryService: ImpliedCategoryInferenceService,
    private val metadataCacheService: MetadataCacheService,
    private val syncStatusController: SyncStatusController
) : ViewModel() {

    private val _state = MutableStateFlow(EventCaptureState())
    val state: StateFlow<EventCaptureState> = _state.asStateFlow()
    private val _uiState = MutableStateFlow<UiState<EventCaptureState>>(UiState.Loading(LoadingOperation.Initial))
    val uiState: StateFlow<UiState<EventCaptureState>> = _uiState.asStateFlow()
private var lastSuccessfulState: EventCaptureState? = null
private var isShowingSyncOverlay = false
    private var initialValues: Map<String, String?> = emptyMap()
    private var initialEventDate: Date? = null
    private var initialOrganisationUnitId: String? = null
    val syncController: SyncStatusController = syncStatusController

    private fun emitSuccessState() {
        val current = _state.value
        lastSuccessfulState = current
        _uiState.value = UiState.Success(current)
    }

    private fun updateState(
        autoEmit: Boolean = true,
        transform: (EventCaptureState) -> EventCaptureState
    ) {
        _state.update(transform)
        if (autoEmit && _uiState.value is UiState.Success) {
            emitSuccessState()
        }
    }

    private fun setUiLoading(operation: LoadingOperation, progress: LoadingProgress? = null) {
        _uiState.value = UiState.Loading(operation, progress)
    }

    private fun setUiError(error: UiError) {
        _uiState.value = UiState.Error(error, previousData = lastSuccessfulState)
    }

    private var d2: D2? = null

    // Field states for reusing DataValueField component
    val fieldStates = mutableStateMapOf<String, androidx.compose.ui.text.input.TextFieldValue>()

    init {
        d2 = sessionManager.getD2()

        // Observe sync progress (reuse DataEntry pattern)
        viewModelScope.launch {
            syncQueueManager.detailedProgress.collect { progress ->
                updateState {
                    it.copy(
                        detailedSyncProgress = progress,
                        isSyncing = progress != null
                    )
                }

                if (progress != null) {
                    isShowingSyncOverlay = true
                    setUiLoading(LoadingOperation.Syncing(progress))
                } else if (isShowingSyncOverlay) {
                    isShowingSyncOverlay = false
                    emitSuccessState()
                }
            }
        }
    }

    fun initializeEvent(
        programId: String,
        programStageId: String?,
        enrollmentId: String? = null,
        eventId: String? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                updateState {
                    it.copy(
                        isLoading = true,
                        error = null,
                        isEditMode = eventId != null,
                        eventId = eventId,
                        enrollmentId = enrollmentId
                    )
                }
                val navigationProgress = NavigationProgress(
                    phase = com.ash.simpledataentry.presentation.core.LoadingPhase.LOADING_DATA,
                    overallPercentage = 10,
                    phaseTitle = "Loading event",
                    phaseDetail = "Preparing event form...",
                    loadingType = com.ash.simpledataentry.presentation.core.StepLoadingType.ENTRY
                )
                setUiLoading(
                    operation = LoadingOperation.Navigation(navigationProgress),
                    progress = LoadingProgress(message = navigationProgress.phaseDetail)
                )

                val d2Instance = d2 ?: throw Exception("Not authenticated")

                // Load program and program stage metadata
                val program = d2Instance.programModule().programs()
                    .uid(programId).blockingGet()
                    ?: throw Exception("Program not found")

                val existingEvent = if (eventId != null) {
                    d2Instance.eventModule().events().uid(eventId).blockingGet()
                        ?: throw Exception("Event not found")
                } else null

                val stageId = programStageId
                    ?: existingEvent?.programStage()
                    ?: run {
                    val stages = d2Instance.programModule().programStages()
                        .byProgramUid().eq(programId).blockingGet()
                    stages.firstOrNull()?.uid() ?: throw Exception("No program stage found")
                    }

                val programStage = d2Instance.programModule().programStages()
                    .uid(stageId).blockingGet()
                    ?: throw Exception("Program stage not found")

                // Load program stage sections (if any) - this will be the first accordion level
                val programStageSections = d2Instance.programModule()
                    .programStageSections()
                    .byProgramStageUid().eq(stageId)
                    .withDataElements()
                    .blockingGet()

                android.util.Log.d("EventCaptureVM", "Program stage sections found: ${programStageSections.size}")

                // Transform program stage data elements to DataValue objects (reuse existing model)
                val dataElementsFromStage = d2Instance.programModule()
                    .programStageDataElements()
                    .byProgramStage().eq(stageId)
                    .blockingGet()

                val dataValues = dataElementsFromStage.mapNotNull { psde ->
                    val dataElement = d2Instance.dataElementModule()
                        .dataElements()
                        .uid(psde.dataElement()?.uid())
                        .blockingGet()

                    dataElement?.let { de ->
                        // Find section for this data element (if sections exist)
                        val sectionName = programStageSections
                            .firstOrNull { section ->
                                section.dataElements()?.any { it.uid() == de.uid() } == true
                            }
                            ?.displayName()
                            ?: programStage.displayName() ?: "Event Data"

                        // Convert to DataValue format to reuse all existing input components
                        DataValue(
                            dataElement = de.uid(),
                            dataElementName = de.displayName() ?: de.name() ?: "",
                            sectionName = sectionName,
                            categoryOptionCombo = de.categoryCombo()?.uid() ?: "HllvX50cXC0",
                            categoryOptionComboName = "default",
                            value = null, // Will be loaded if editing existing event
                            comment = null,
                            storedBy = null,
                            validationState = ValidationState.VALID,
                            dataEntryType = when (de.valueType()?.name) {
                                "INTEGER", "POSITIVE_INTEGER", "NEGATIVE_INTEGER" -> DataEntryType.NUMBER
                                "PERCENTAGE" -> DataEntryType.PERCENTAGE
                                "TRUE_ONLY" -> DataEntryType.YES_NO
                                "BOOLEAN" -> DataEntryType.YES_NO
                                else -> DataEntryType.TEXT
                            },
                            lastModified = System.currentTimeMillis()
                        )
                    }
                }

                // Load existing data values if editing
                val existingDataValues = if (eventId != null) {
                    d2Instance.trackedEntityModule().trackedEntityDataValues()
                        .byEvent().eq(eventId)
                        .blockingGet()
                        .associate { it.dataElement()!! to it.value() }
                } else emptyMap()

                // Load option sets for data elements
                val dataValuesWithOptionSets = dataValues.map { dataValue ->
                    val optionSet = try {
                        // Get option set from data element
                        val de = d2Instance.dataElementModule().dataElements()
                            .uid(dataValue.dataElement)
                            .blockingGet()

                        val optionSetUid = de?.optionSet()?.uid()

                        if (optionSetUid != null) {
                            val optionSetObj = d2Instance.optionModule().optionSets()
                                .uid(optionSetUid)
                                .blockingGet()

                            val sdkOptions = d2Instance.optionModule().options()
                                .byOptionSetUid().eq(optionSetUid)
                                .blockingGet()

                            val options = sdkOptions.mapIndexed { index, option ->
                                Option(
                                    code = option.code() ?: option.uid(),
                                    name = option.name() ?: option.code() ?: option.uid(),
                                    displayName = option.displayName(),
                                    sortOrder = option.sortOrder() ?: index
                                )
                            }

                            OptionSet(
                                id = optionSetObj?.uid() ?: optionSetUid,
                                name = optionSetObj?.name() ?: "",
                                displayName = optionSetObj?.displayName(),
                                options = options
                            )
                        } else null
                    } catch (e: Exception) {
                        android.util.Log.w("EventCaptureVM", "Failed to load option set for ${dataValue.dataElement}: ${e.message}")
                        null
                    }

                    dataValue.copy(optionSet = optionSet)
                }

                // Update data values with existing values
                val updatedDataValues = dataValuesWithOptionSets.map { dataValue ->
                    existingDataValues[dataValue.dataElement]?.let { existingValue ->
                        dataValue.copy(value = existingValue)
                    } ?: dataValue
                }

                // Load org units (only for standalone events)
                val orgUnits = if (enrollmentId == null) {
                    d2Instance.organisationUnitModule().organisationUnits()
                        .byProgramUids(listOf(programId))
                        .blockingGet()
                        .map { orgUnit ->
                            OrganisationUnit(
                                id = orgUnit.uid(),
                                name = orgUnit.displayName() ?: orgUnit.name() ?: ""
                            )
                        }
                } else emptyList()

                // Load existing event details if editing
                val (eventDate, orgUnitId, completed) = if (existingEvent != null) {
                    Triple(
                        existingEvent.eventDate() ?: Date(),
                        existingEvent.organisationUnit(),
                        existingEvent.status()?.name == "COMPLETED"
                    )
                } else {
                    Triple(Date(), null, false)
                }

                // INFER IMPLIED CATEGORY STRUCTURE from data element names per section
                // This restores the previously-removed nested accordion functionality
                // Sections are the first accordion level, implied categories are nested within sections
                val sections = updatedDataValues.map { it.sectionName }.distinct()
                val impliedCategoriesBySection = mutableMapOf<String, ImpliedCategoryCombination>()
                val impliedMappingsBySection = mutableMapOf<String, List<ImpliedCategoryMapping>>()

                sections.forEach { sectionName ->
                    val sectionDataValues = updatedDataValues.filter { it.sectionName == sectionName }
                    val impliedCombination = inferImpliedCategories(programId, sectionName, sectionDataValues)

                    if (impliedCombination != null) {
                        impliedCategoriesBySection[sectionName] = impliedCombination
                        impliedMappingsBySection[sectionName] =
                            impliedCategoryService.createMappings(sectionDataValues, impliedCombination)

                        android.util.Log.d("EventCaptureVM", "Section '$sectionName': " +
                            "inferred ${impliedCombination.categories.size} category levels, " +
                            "confidence=${impliedCombination.confidence}")
                    } else {
                        android.util.Log.d("EventCaptureVM", "Section '$sectionName': No category pattern detected")
                    }
                }

                updateState {
                    it.copy(
                        isLoading = false,
                        programId = programId,
                        programName = program.displayName() ?: program.name() ?: "",
                        programStageId = stageId,
                        programStageName = programStage.displayName() ?: programStage.name() ?: "",
                        dataValues = updatedDataValues,
                        availableOrganisationUnits = orgUnits,
                        eventDate = eventDate,
                        selectedOrganisationUnitId = orgUnitId,
                        isCompleted = completed,
                        impliedCategoriesBySection = impliedCategoriesBySection,
                        impliedCategoryMappingsBySection = impliedMappingsBySection,
                        sectionHasData = calculateSectionHasData(updatedDataValues)
                    )
                }
                cacheInitialState(updatedDataValues, eventDate, orgUnitId)

                updateCanSave()

                // Trigger program rules evaluation after loading existing event
                eventId?.let { evaluateProgramRules(it) }
                emitSuccessState()

            } catch (e: Exception) {
                updateState {
                    it.copy(
                        isLoading = false,
                        error = "Failed to initialize event: ${e.message}"
                    )
                }
                setUiError(UiError.Local(e.message ?: "Failed to initialize event"))
            }
        }
    }

    // Reuse exact same field update pattern from DataEntryViewModel
    fun updateDataValue(value: String, dataValue: DataValue) {
        updateState { currentState ->
            val updatedDataValues = currentState.dataValues.map { existingDataValue ->
                if (existingDataValue.dataElement == dataValue.dataElement &&
                    existingDataValue.categoryOptionCombo == dataValue.categoryOptionCombo) {
                    existingDataValue.copy(
                        value = value.ifBlank { null },
                        lastModified = System.currentTimeMillis()
                    )
                } else {
                    existingDataValue
                }
            }

            currentState.copy(
                dataValues = updatedDataValues,
                currentDataValue = dataValue.copy(value = value.ifBlank { null }),
                sectionHasData = calculateSectionHasData(updatedDataValues)
            )
        }
        updateCanSave()

        // Trigger program rules evaluation after value change
        _state.value.eventId?.let { eventId ->
            evaluateProgramRules(eventId)
        }
    }

    fun updateEventDate(date: Date) {
        updateState { it.copy(eventDate = date) }
        updateCanSave()
    }

    fun updateOrganisationUnit(orgUnitId: String) {
        updateState { it.copy(selectedOrganisationUnitId = orgUnitId) }
        updateCanSave()
    }

    fun hasUnsavedChanges(): Boolean {
        val currentState = _state.value
        val currentValues = currentState.dataValues.associate { dataValue ->
            "${dataValue.dataElement}|${dataValue.categoryOptionCombo}" to dataValue.value?.ifBlank { null }
        }
        val normalizedInitial = initialValues.mapValues { it.value?.ifBlank { null } }
        if (currentValues != normalizedInitial) {
            return true
        }
        if (currentState.eventDate != initialEventDate) {
            return true
        }
        if (currentState.selectedOrganisationUnitId != initialOrganisationUnitId) {
            return true
        }
        return false
    }

    private fun cacheInitialState(
        dataValues: List<DataValue>,
        eventDate: Date?,
        organisationUnitId: String?
    ) {
        initialValues = dataValues.associate { dataValue ->
            "${dataValue.dataElement}|${dataValue.categoryOptionCombo}" to dataValue.value?.ifBlank { null }
        }
        initialEventDate = eventDate
        initialOrganisationUnitId = organisationUnitId
    }

    private fun calculateSectionHasData(dataValues: List<DataValue>): Map<String, Boolean> {
        return dataValues
            .groupBy { it.sectionName }
            .mapValues { (_, values) -> values.any { !it.value.isNullOrBlank() } }
    }

    private fun updateCanSave() {
        val currentState = _state.value
        val validationErrors = mutableListOf<String>()

        // Validate required fields
        if (currentState.enrollmentId == null && currentState.selectedOrganisationUnitId.isNullOrBlank()) {
            validationErrors.add("Organisation unit is required")
        }

        if (currentState.eventDate == null) {
            validationErrors.add("Event date is required")
        }
        currentState.programRuleEffect?.fieldErrors?.values?.forEach { message ->
            validationErrors.add(message)
        }

        updateState {
            it.copy(
                validationErrors = validationErrors,
                validationState = if (validationErrors.isEmpty()) ValidationState.VALID else ValidationState.VALID,
                canSave = validationErrors.isEmpty(),
                validationMessage = if (validationErrors.isEmpty()) null else it.validationMessage
            )
        }
    }

    fun saveEvent() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                updateState { it.copy(saveInProgress = true, error = null) }
                val currentState = _state.value
                val savingProgress = LoadingProgress(
                    message = if (currentState.isEditMode) "Updating event..." else "Creating event..."
                )
                setUiLoading(
                    operation = LoadingOperation.Saving(),
                    progress = savingProgress
                )

                val d2Instance = d2 ?: throw Exception("Not authenticated")

                if (!currentState.canSave) {
                    updateState {
                        it.copy(
                            saveInProgress = false,
                            validationMessage = "Please fix the highlighted fields before saving."
                        )
                    }
                    emitSuccessState()
                    return@launch
                }

                if (currentState.isEditMode && currentState.eventId != null) {
                    updateExistingEvent(d2Instance, currentState.eventId, currentState)
                } else {
                    createNewEvent(d2Instance, currentState)
                }

                updateState {
                    it.copy(
                        saveInProgress = false,
                        saveSuccess = true,
                        saveResult = Result.success(Unit),
                        successMessage = if (currentState.isEditMode) "Event updated successfully" else "Event created successfully"
                    )
                }
                cacheInitialState(_state.value.dataValues, _state.value.eventDate, _state.value.selectedOrganisationUnitId)

                // Trigger program rules evaluation after save
                _state.value.eventId?.let { eventId ->
                    evaluateProgramRules(eventId)
                }
                emitSuccessState()

            } catch (e: Exception) {
                updateState {
                    it.copy(
                        saveInProgress = false,
                        saveResult = Result.failure(e),
                        error = "Failed to save event: ${e.message}"
                    )
                }
                emitSuccessState()
            }
        }
    }

    fun clearValidationMessage() {
        updateState {
            it.copy(validationMessage = null)
        }
    }

    fun validateEvent() {
        viewModelScope.launch {
            val preparing = NavigationProgress(
                phase = com.ash.simpledataentry.presentation.core.LoadingPhase.INITIALIZING,
                overallPercentage = 15,
                phaseTitle = "Preparing validation",
                phaseDetail = "Gathering form data...",
                loadingType = com.ash.simpledataentry.presentation.core.StepLoadingType.VALIDATION
            )
            setUiLoading(
                operation = LoadingOperation.Navigation(preparing),
                progress = LoadingProgress(message = preparing.phaseDetail)
            )
            delay(300)

            val validating = NavigationProgress(
                phase = com.ash.simpledataentry.presentation.core.LoadingPhase.PROCESSING_DATA,
                overallPercentage = 55,
                phaseTitle = "Running validation",
                phaseDetail = "Checking required fields...",
                loadingType = com.ash.simpledataentry.presentation.core.StepLoadingType.VALIDATION
            )
            setUiLoading(
                operation = LoadingOperation.Navigation(validating),
                progress = LoadingProgress(message = validating.phaseDetail)
            )
            updateCanSave()
            delay(300)

            val processing = NavigationProgress(
                phase = com.ash.simpledataentry.presentation.core.LoadingPhase.COMPLETING,
                overallPercentage = 85,
                phaseTitle = "Processing results",
                phaseDetail = "Finalizing validation...",
                loadingType = com.ash.simpledataentry.presentation.core.StepLoadingType.VALIDATION
            )
            setUiLoading(
                operation = LoadingOperation.Navigation(processing),
                progress = LoadingProgress(message = processing.phaseDetail)
            )
            delay(200)

            val errors = _state.value.validationErrors
            updateState {
                it.copy(
                    validationMessage = if (errors.isEmpty()) {
                        "All required fields are filled."
                    } else {
                        "Please review the following issues:"
                    }
                )
            }
            emitSuccessState()
        }
    }

    fun completeEvent() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val eventId = _state.value.eventId ?: return@launch
                updateState { it.copy(saveInProgress = true, error = null) }
                val completionProgress = LoadingProgress(message = "Completing event...")
                setUiLoading(
                    operation = LoadingOperation.Saving(),
                    progress = completionProgress
                )

                val result = repository.completeEvent(eventId)
                if (result.isFailure) {
                    throw result.exceptionOrNull() ?: Exception("Failed to complete event")
                }

                updateState {
                    it.copy(
                        saveInProgress = false,
                        isCompleted = true,
                        successMessage = "Event marked complete"
                    )
                }
                emitSuccessState()
            } catch (e: Exception) {
                updateState {
                    it.copy(
                        saveInProgress = false,
                        error = "Failed to complete event: ${e.message}"
                    )
                }
                emitSuccessState()
            }
        }
    }

    private fun createNewEvent(d2: D2, state: EventCaptureState) {
        // Get default attribute option combo
        val defaultCombo = d2.categoryModule().categoryOptionCombos()
            .byDisplayName().eq("default").one().blockingGet()?.uid()
            ?: "HllvX50cXC0"

        // Create event
        val eventUid = d2.eventModule().events().add(
            EventCreateProjection.create(
                state.enrollmentId, // Can be null for standalone events
                state.programId,
                state.programStageId,
                state.selectedOrganisationUnitId ?: "",
                defaultCombo
            )
        ).blockingGet()

        // Set event date
        d2.eventModule().events().uid(eventUid).setEventDate(state.eventDate)

        // Save data values - reuse exact same pattern as datasets
        state.dataValues.forEach { dataValue ->
            dataValue.value?.let { value ->
                if (value.isNotBlank()) {
                    d2.trackedEntityModule().trackedEntityDataValues()
                        .value(eventUid, dataValue.dataElement)
                        .set(value)
                }
            }
        }
    }

    private fun updateExistingEvent(d2: D2, eventId: String, state: EventCaptureState) {
        // Update event date
        d2.eventModule().events().uid(eventId).setEventDate(state.eventDate)

        // Update data values
        state.dataValues.forEach { dataValue ->
            dataValue.value?.let { value ->
                if (value.isNotBlank()) {
                    d2.trackedEntityModule().trackedEntityDataValues()
                        .value(eventId, dataValue.dataElement)
                        .set(value)
                } else {
                    // Remove empty values
                    d2.trackedEntityModule().trackedEntityDataValues()
                        .value(eventId, dataValue.dataElement)
                        .delete()
                }
            }
        }
    }

    // Add sync functionality (reuse DataEntry pattern)
    fun syncEvent() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentState = _state.value
                if (currentState.programId.isBlank()) return@launch

                val syncProgress = NavigationProgress(
                    phase = com.ash.simpledataentry.presentation.core.LoadingPhase.INITIALIZING,
                    overallPercentage = 10,
                    phaseTitle = "Preparing sync",
                    phaseDetail = "Preparing event sync...",
                    loadingType = com.ash.simpledataentry.presentation.core.StepLoadingType.SYNC
                )
                setUiLoading(
                    operation = LoadingOperation.Navigation(syncProgress),
                    progress = LoadingProgress(message = syncProgress.phaseDetail)
                )

                repository.syncProgramInstances(currentState.programId, ProgramType.EVENT)
                    .onSuccess {
                        val refreshProgress = NavigationProgress(
                            phase = com.ash.simpledataentry.presentation.core.LoadingPhase.PROCESSING_DATA,
                            overallPercentage = 85,
                            phaseTitle = "Refreshing data",
                            phaseDetail = "Updating local event data...",
                            loadingType = com.ash.simpledataentry.presentation.core.StepLoadingType.SYNC
                        )
                        setUiLoading(
                            operation = LoadingOperation.Navigation(refreshProgress),
                            progress = LoadingProgress(message = refreshProgress.phaseDetail)
                        )
                        updateState {
                            it.copy(successMessage = "Event synced successfully")
                        }
                        emitSuccessState()
                    }
                    .onFailure { error ->
                        updateState {
                            it.copy(error = "Sync failed: ${error.message}")
                        }
                        emitSuccessState()
                    }
            } catch (e: Exception) {
                updateState {
                    it.copy(error = "Sync failed: ${e.message}")
                }
                emitSuccessState()
            }
        }
    }

    fun clearMessages() {
        updateState {
            it.copy(
                error = null,
                successMessage = null,
                saveResult = null
            )
        }
    }

    // Methods for DataValueField compatibility
    fun initializeFieldState(dataValue: DataValue) {
        val key = "${dataValue.dataElement}|${dataValue.categoryOptionCombo}"
        if (!fieldStates.containsKey(key)) {
            fieldStates[key] = TextFieldValue(dataValue.value ?: "")
        }
    }

    fun onFieldValueChange(newValue: TextFieldValue, dataValue: DataValue) {
        val key = "${dataValue.dataElement}|${dataValue.categoryOptionCombo}"
        fieldStates[key] = newValue
        updateDataValue(newValue.text, dataValue)
    }

    /**
     * Evaluate program rules for the current event
     * Uses DHIS2 rule-engine library for offline evaluation
     *
     * TODO: Fix API usage - currently has compilation errors
     * The correct DHIS2 SDK API for program rule evaluation needs to be researched
     */
    private fun evaluateProgramRules(eventUid: String) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val d2Instance = d2 ?: return@launch
                val currentState = _state.value
                val programId = currentState.programId
                if (programId.isBlank()) return@launch

                val programRules = d2Instance.programModule().programRules()
                    .withProgramRuleActions()
                    .byProgramUid().eq(programId)
                    .blockingGet()

                if (programRules.isEmpty()) return@launch

                val programRuleVariables = d2Instance.programModule().programRuleVariables()
                    .byProgramUid().eq(programId)
                    .blockingGet()

                val ruleVariables = programRuleVariables.mapNotNull { variable ->
                    mapProgramRuleVariable(variable, d2Instance)
                }

                val rules = programRules.map { programRule ->
                    val actions = programRule.programRuleActions().orEmpty().mapNotNull { action ->
                        mapProgramRuleAction(action)
                    }
                    Rule.create(
                        programRule.programStage()?.uid(),
                        programRule.priority(),
                        programRule.condition() ?: "true",
                        actions,
                        programRule.name() ?: programRule.displayName() ?: "",
                        programRule.uid()
                    )
                }

                val constants = d2Instance.constantModule().constants()
                    .blockingGet()
                    .associate { constant ->
                        constant.uid() to (constant.value()?.toString() ?: "")
                    }

                val ruleEngineContext = RuleEngineContext.builder()
                    .rules(rules)
                    .ruleVariables(ruleVariables)
                    .supplementaryData(emptyMap())
                    .constantsValue(constants)
                    .build()

                val programStageId = currentState.programStageId
                if (programStageId.isBlank()) return@launch
                val eventDate = currentState.eventDate ?: Date()
                val programStageName = d2Instance.programModule().programStages()
                    .uid(programStageId)
                    .blockingGet()
                    ?.displayName()
                    ?: ""

                val ruleDataValues = currentState.dataValues.mapNotNull { dataValue ->
                    val value = dataValue.value?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    RuleDataValue.create(eventDate, programStageId, dataValue.dataElement, value)
                }

                val ruleEvent = RuleEvent.create(
                    eventUid,
                    programStageId,
                    if (currentState.isCompleted) RuleEvent.Status.COMPLETED else RuleEvent.Status.ACTIVE,
                    eventDate,
                    eventDate,
                    currentState.selectedOrganisationUnitId ?: "",
                    null,
                    ruleDataValues,
                    programStageName,
                    if (currentState.isCompleted) eventDate else null
                )

                val ruleEngine = ruleEngineContext.toEngineBuilder()
                    .events(listOf(ruleEvent))
                    .build()

                val ruleEffects = ruleEngine.evaluate(ruleEvent).call()
                val effect = convertToRuleEffect(ruleEffects)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    applyRuleEffects(effect)
                }
            } catch (e: Exception) {
                android.util.Log.e("EventCaptureVM", "Program rule evaluation failed: ${e.message}", e)
            }
        }
    }

    private fun mapProgramRuleAction(action: ProgramRuleAction): RuleAction? {
        val attributeType = resolveAttributeType(action)
        val fieldUid = action.dataElement()?.uid() ?: action.trackedEntityAttribute()?.uid()
        val content = action.displayContent() ?: action.content()
        val data = action.data()

        return when (action.programRuleActionType()) {
            ProgramRuleActionType.HIDEFIELD ->
                fieldUid?.let { RuleActionHideField.create(content, it, attributeType) }
            ProgramRuleActionType.SHOWWARNING ->
                if (fieldUid != null && (content != null || data != null)) {
                    RuleActionShowWarning.create(content, data, fieldUid, attributeType)
                } else {
                    null
                }
            ProgramRuleActionType.SHOWERROR ->
                if (fieldUid != null && (content != null || data != null)) {
                    RuleActionShowError.create(content, data, fieldUid, attributeType)
                } else {
                    null
                }
            ProgramRuleActionType.WARNINGONCOMPLETE ->
                if (content != null || data != null || fieldUid != null) {
                    RuleActionWarningOnCompletion.create(content, data, fieldUid, attributeType)
                } else {
                    null
                }
            ProgramRuleActionType.ERRORONCOMPLETE ->
                if (content != null || data != null || fieldUid != null) {
                    RuleActionErrorOnCompletion.create(content, data, fieldUid, attributeType)
                } else {
                    null
                }
            ProgramRuleActionType.ASSIGN ->
                if (data != null && fieldUid != null) {
                    RuleActionAssign.create(content, data, fieldUid, attributeType)
                } else {
                    null
                }
            ProgramRuleActionType.SETMANDATORYFIELD ->
                fieldUid?.let { RuleActionSetMandatoryField.create(it, attributeType) }
            ProgramRuleActionType.DISPLAYTEXT ->
                if (content != null || data != null) {
                    RuleActionDisplayText.createForFeedback(content, data)
                } else {
                    null
                }
            ProgramRuleActionType.DISPLAYKEYVALUEPAIR ->
                if (content != null || data != null) {
                    RuleActionDisplayKeyValuePair.createForFeedback(content, data)
                } else {
                    null
                }
            ProgramRuleActionType.HIDESECTION ->
                action.programStageSection()?.uid()?.let { RuleActionHideSection.create(it) }
            else -> null
        }
    }

    private fun resolveAttributeType(action: ProgramRuleAction): AttributeType {
        return when {
            action.dataElement() != null -> AttributeType.DATA_ELEMENT
            action.trackedEntityAttribute() != null -> AttributeType.TRACKED_ENTITY_ATTRIBUTE
            else -> AttributeType.UNKNOWN
        }
    }

    private fun mapProgramRuleVariable(
        variable: ProgramRuleVariable,
        d2Instance: D2
    ): RuleVariable? {
        val name = variable.name() ?: variable.uid()
        val sourceType = variable.programRuleVariableSourceType() ?: return null
        val dataElementUid = variable.dataElement()?.uid()
        val attributeUid = variable.trackedEntityAttribute()?.uid()
        val valueType = when {
            dataElementUid != null -> d2Instance.dataElementModule().dataElements()
                .uid(dataElementUid)
                .blockingGet()
                ?.valueType()
                ?.name
            attributeUid != null -> d2Instance.trackedEntityModule().trackedEntityAttributes()
                .uid(attributeUid)
                .blockingGet()
                ?.valueType()
                ?.name
            else -> null
        }
        val ruleValueType = toRuleValueType(valueType)

        return when (sourceType) {
            ProgramRuleVariableSourceType.DATAELEMENT_CURRENT_EVENT ->
                dataElementUid?.let { RuleVariableCurrentEvent.create(name, it, ruleValueType) }
            ProgramRuleVariableSourceType.DATAELEMENT_NEWEST_EVENT_PROGRAM_STAGE ->
                if (dataElementUid != null && variable.programStage()?.uid() != null) {
                    RuleVariableNewestStageEvent.create(
                        name,
                        dataElementUid,
                        variable.programStage()?.uid() ?: "",
                        ruleValueType
                    )
                } else {
                    null
                }
            ProgramRuleVariableSourceType.DATAELEMENT_NEWEST_EVENT_PROGRAM ->
                dataElementUid?.let { RuleVariableNewestEvent.create(name, it, ruleValueType) }
            ProgramRuleVariableSourceType.DATAELEMENT_PREVIOUS_EVENT ->
                dataElementUid?.let { RuleVariablePreviousEvent.create(name, it, ruleValueType) }
            ProgramRuleVariableSourceType.CALCULATED_VALUE ->
                RuleVariableCalculatedValue.create(name, name, ruleValueType)
            ProgramRuleVariableSourceType.TEI_ATTRIBUTE ->
                attributeUid?.let { RuleVariableAttribute.create(name, it, ruleValueType) }
        }
    }

    private fun toRuleValueType(valueTypeName: String?): RuleValueType {
        return when (valueTypeName) {
            "INTEGER", "INTEGER_POSITIVE", "INTEGER_NEGATIVE", "INTEGER_ZERO_OR_POSITIVE",
            "NUMBER", "UNIT_INTERVAL", "PERCENTAGE" -> RuleValueType.NUMERIC
            "BOOLEAN", "TRUE_ONLY" -> RuleValueType.BOOLEAN
            "DATE", "DATETIME" -> RuleValueType.DATE
            else -> RuleValueType.TEXT
        }
    }

    /**
     * Convert DHIS2 SDK RuleEffect list to ProgramRuleEffect model
     *
     * TODO: Will be re-enabled when evaluateProgramRules is fixed
     */
    @Suppress("unused")
    private fun convertToRuleEffect(
        sdkEffects: List<org.hisp.dhis.rules.models.RuleEffect>
    ): ProgramRuleEffect {
        val hiddenFields = mutableSetOf<String>()
        val mandatoryFields = mutableSetOf<String>()
        val fieldWarnings = mutableMapOf<String, String>()
        val fieldErrors = mutableMapOf<String, String>()
        val calculatedValues = mutableMapOf<String, String>()
        val displayTexts = mutableListOf<String>()
        val displayKeyValuePairs = mutableMapOf<String, String>()
        val hiddenSections = mutableSetOf<String>()

        sdkEffects.forEach { effect ->
            when (effect.ruleAction().javaClass.simpleName) {
                "RuleActionHideField" -> {
                    effect.data()?.let { hiddenFields.add(it) }
                }
                "RuleActionShowWarning" -> {
                    val field = effect.data() ?: ""
                    val message = effect.ruleAction().data() ?: ""
                    if (field.isNotEmpty()) fieldWarnings[field] = message
                }
                "RuleActionShowError" -> {
                    val field = effect.data() ?: ""
                    val message = effect.ruleAction().data() ?: ""
                    if (field.isNotEmpty()) fieldErrors[field] = message
                }
                "RuleActionAssign" -> {
                    val field = effect.data() ?: ""
                    val value = effect.ruleAction().data() ?: ""
                    if (field.isNotEmpty()) calculatedValues[field] = value
                }
                "RuleActionSetMandatoryField" -> {
                    effect.data()?.let { mandatoryFields.add(it) }
                }
                "RuleActionDisplayText" -> {
                    effect.ruleAction().data()?.let { displayTexts.add(it) }
                }
                "RuleActionDisplayKeyValuePair" -> {
                    //val key = effect.ruleAction().location() ?: ""
                    val value = effect.ruleAction().data() ?: ""
                    //if (key.isNotEmpty()) displayKeyValuePairs[key] = value
                }
                "RuleActionHideSection" -> {
                    effect.data()?.let { hiddenSections.add(it) }
                }
            }
        }

        return ProgramRuleEffect(
            hiddenFields = hiddenFields,
            mandatoryFields = mandatoryFields,
            fieldWarnings = fieldWarnings,
            fieldErrors = fieldErrors,
            calculatedValues = calculatedValues,
            displayTexts = displayTexts,
            displayKeyValuePairs = displayKeyValuePairs,
            hiddenSections = hiddenSections
        )
    }

    /**
     * Apply program rule effects to form state
     *
     * TODO: Will be re-enabled when evaluateProgramRules is fixed
     */
    @Suppress("unused")
    private fun applyRuleEffects(effect: ProgramRuleEffect) {
        updateState { currentState ->
            // Apply calculated values to data values
            val updatedDataValues = currentState.dataValues.map { dv ->
                val calculatedValue = effect.calculatedValues[dv.dataElement]
                if (calculatedValue != null) {
                    dv.copy(value = calculatedValue)
                } else {
                    dv
                }
            }

            currentState.copy(
                dataValues = updatedDataValues,
                programRuleEffect = effect
            )
        }
        updateCanSave()
    }

    /**
     * Infer implied category structure from data element names
     * Uses caching via MetadataCacheService to avoid re-computation
     *
     * This restores the nested accordion functionality that was previously removed
     * in commits 2b78f6e and c11092e
     */
    private fun inferImpliedCategories(
        programId: String,
        sectionName: String,
        dataValues: List<DataValue>
    ): ImpliedCategoryCombination? {
        // Check cache first
        val cached = metadataCacheService.getImpliedCategories(programId, sectionName)
        if (cached != null && cached.categories.any { it.options.size > 1 }) {
            android.util.Log.d("EventCaptureVM", "Using cached implied categories for $programId:$sectionName")
            return cached
        }
        if (cached != null) {
            android.util.Log.d(
                "EventCaptureVM",
                "Cached implied categories lack meaningful options for $programId:$sectionName - recomputing"
            )
        }

        // Infer from data element names
        val inferred = impliedCategoryService.inferCategoryStructure(dataValues, sectionName)

        // Store in cache if inference was successful
        if (inferred != null) {
            metadataCacheService.setImpliedCategories(programId, sectionName, inferred)
        }

        return inferred
    }
}
