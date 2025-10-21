package com.ash.simpledataentry.presentation.tracker

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.data.cache.MetadataCacheService
import com.ash.simpledataentry.data.sync.DetailedSyncProgress
import com.ash.simpledataentry.data.sync.SyncQueueManager
import com.ash.simpledataentry.data.sync.NetworkStateManager
import com.ash.simpledataentry.domain.grouping.ImpliedCategoryInferenceService
import com.ash.simpledataentry.domain.model.*
import com.ash.simpledataentry.domain.repository.DatasetInstancesRepository
import com.ash.simpledataentry.presentation.core.NavigationProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.event.Event
import org.hisp.dhis.android.core.event.EventCreateProjection
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
    private val metadataCacheService: MetadataCacheService
) : ViewModel() {

    private val _state = MutableStateFlow(EventCaptureState())
    val state: StateFlow<EventCaptureState> = _state.asStateFlow()

    private var d2: D2? = null

    // Field states for reusing DataValueField component
    val fieldStates = mutableStateMapOf<String, androidx.compose.ui.text.input.TextFieldValue>()

    init {
        d2 = sessionManager.getD2()

        // Observe sync progress (reuse DataEntry pattern)
        viewModelScope.launch {
            syncQueueManager.detailedProgress.collect { progress ->
                _state.update { it.copy(
                    detailedSyncProgress = progress,
                    isSyncing = progress != null
                ) }
            }
        }
    }

    fun initializeEvent(
        programId: String,
        programStageId: String?,
        enrollmentId: String? = null,
        eventId: String? = null
    ) {
        viewModelScope.launch {
            try {
                _state.update {
                    it.copy(
                        isLoading = true,
                        error = null,
                        isEditMode = eventId != null,
                        eventId = eventId,
                        enrollmentId = enrollmentId
                    )
                }

                val d2Instance = d2 ?: throw Exception("Not authenticated")

                // Load program and program stage metadata
                val program = d2Instance.programModule().programs()
                    .uid(programId).blockingGet()
                    ?: throw Exception("Program not found")

                val stageId = programStageId ?: run {
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
                val (eventDate, orgUnitId, completed) = if (eventId != null) {
                    val event = d2Instance.eventModule().events().uid(eventId).blockingGet()
                    Triple(
                        event?.eventDate() ?: Date(),
                        event?.organisationUnit(),
                        event?.status()?.name == "COMPLETED"
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

                _state.update {
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
                        impliedCategoryMappingsBySection = impliedMappingsBySection
                    )
                }

                updateCanSave()

                // Trigger program rules evaluation after loading existing event
                eventId?.let { evaluateProgramRules(it) }

            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to initialize event: ${e.message}"
                    )
                }
            }
        }
    }

    // Reuse exact same field update pattern from DataEntryViewModel
    fun updateDataValue(value: String, dataValue: DataValue) {
        _state.update { currentState ->
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
                currentDataValue = dataValue.copy(value = value.ifBlank { null })
            )
        }
        updateCanSave()

        // Trigger program rules evaluation after value change
        _state.value.eventId?.let { eventId ->
            evaluateProgramRules(eventId)
        }
    }

    fun updateEventDate(date: Date) {
        _state.update { it.copy(eventDate = date) }
        updateCanSave()
    }

    fun updateOrganisationUnit(orgUnitId: String) {
        _state.update { it.copy(selectedOrganisationUnitId = orgUnitId) }
        updateCanSave()
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

        _state.update {
            it.copy(
                validationErrors = validationErrors,
                validationState = if (validationErrors.isEmpty()) ValidationState.VALID else ValidationState.VALID,
                canSave = validationErrors.isEmpty()
            )
        }
    }

    fun saveEvent() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(saveInProgress = true, error = null) }

                val d2Instance = d2 ?: throw Exception("Not authenticated")
                val currentState = _state.value

                if (!currentState.canSave) {
                    throw Exception("Cannot save: validation errors exist")
                }

                if (currentState.isEditMode && currentState.eventId != null) {
                    updateExistingEvent(d2Instance, currentState.eventId, currentState)
                } else {
                    createNewEvent(d2Instance, currentState)
                }

                _state.update {
                    it.copy(
                        saveInProgress = false,
                        saveSuccess = true,
                        saveResult = Result.success(Unit),
                        successMessage = if (currentState.isEditMode) "Event updated successfully" else "Event created successfully"
                    )
                }

                // Trigger program rules evaluation after save
                _state.value.eventId?.let { eventId ->
                    evaluateProgramRules(eventId)
                }

            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        saveInProgress = false,
                        saveResult = Result.failure(e),
                        error = "Failed to save event: ${e.message}"
                    )
                }
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
        viewModelScope.launch {
            try {
                val currentState = _state.value
                if (currentState.programId.isBlank()) return@launch

                repository.syncProgramInstances(currentState.programId, ProgramType.EVENT)
                    .onSuccess {
                        _state.update {
                            it.copy(successMessage = "Event synced successfully")
                        }
                    }
                    .onFailure { error ->
                        _state.update {
                            it.copy(error = "Sync failed: ${error.message}")
                        }
                    }
            } catch (e: Exception) {
                _state.update {
                    it.copy(error = "Sync failed: ${e.message}")
                }
            }
        }
    }

    fun clearMessages() {
        _state.update {
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
        // COMMENTED OUT: Has compilation errors - needs correct DHIS2 SDK API
        /*
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            try {
                val d2Instance = d2 ?: return@launch

                // Use DHIS2 rule engine from org.hisp.dhis.rules package
                val ruleEngine = org.hisp.dhis.rules.RuleEngineContext.builder()
                    .build()

                // Get program rules
                val programId = _state.value.programId
                val programRules = d2Instance.programModule().programRules()
                    .byProgramUid().eq(programId)
                    .blockingGet()
                    .map { programRule ->
                        org.hisp.dhis.rules.models.Rule.create(
                            null, null, "", "",
                            programRule.uid(),
                            programRule.name() ?: "",
                            programRule.condition() ?: "",
                            emptyList()
                        )
                    }

                // Get data values for evaluation
                val dataValues = _state.value.dataValues
                    .mapNotNull { dv ->
                        dv.value?.let {
                            org.hisp.dhis.rules.models.RuleDataValue.create(
                                Date(),
                                "",
                                dv.dataElement,
                                it
                            )
                        }
                    }

                // Evaluate rules
                val ruleEffects = org.hisp.dhis.rules.RuleEngine.builder(ruleEngine)
                    .rules(programRules)
                    .build()
                    .evaluate(
                        org.hisp.dhis.rules.models.RuleEvent.create(
                            eventUid,
                            programId,
                            org.hisp.dhis.rules.models.RuleEvent.Status.ACTIVE,
                            Date(),
                            Date(),
                            "",
                            null,
                            null,
                            dataValues,
                            "",
                            null
                        )
                    )

                // Convert to our ProgramRuleEffect model
                val effect = convertToRuleEffect(ruleEffects)

                // Apply effects to state on main thread
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    applyRuleEffects(effect)
                }
            } catch (e: Exception) {
                android.util.Log.e("EventCaptureVM", "Program rule evaluation failed: ${e.message}", e)
            }
        }
        */
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
        _state.update { currentState ->
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
        if (cached != null) {
            android.util.Log.d("EventCaptureVM", "Using cached implied categories for $programId:$sectionName")
            return cached
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