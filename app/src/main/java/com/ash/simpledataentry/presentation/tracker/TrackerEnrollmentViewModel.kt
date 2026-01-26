package com.ash.simpledataentry.presentation.tracker

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.data.sync.DetailedSyncProgress
import com.ash.simpledataentry.data.sync.SyncQueueManager
import com.ash.simpledataentry.data.sync.SyncStatusController
import com.ash.simpledataentry.data.sync.NetworkStateManager
import com.ash.simpledataentry.domain.model.*
import com.ash.simpledataentry.domain.repository.DatasetInstancesRepository
import com.ash.simpledataentry.presentation.core.NavigationProgress
import com.ash.simpledataentry.presentation.core.StepLoadingType
import com.ash.simpledataentry.presentation.core.UiState
import com.ash.simpledataentry.presentation.core.LoadingOperation
import com.ash.simpledataentry.presentation.core.LoadingPhase
import com.ash.simpledataentry.presentation.core.LoadingProgress
import com.ash.simpledataentry.presentation.core.emitError
import com.ash.simpledataentry.presentation.core.emitLoading
import com.ash.simpledataentry.presentation.core.emitSuccess
import com.ash.simpledataentry.util.toUiError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.enrollment.Enrollment
import org.hisp.dhis.android.core.enrollment.EnrollmentCreateProjection
import org.hisp.dhis.android.core.program.ProgramRuleAction
import org.hisp.dhis.android.core.program.ProgramRuleActionType
import org.hisp.dhis.android.core.program.ProgramRuleVariable
import org.hisp.dhis.android.core.program.ProgramRuleVariableSourceType
import org.hisp.dhis.android.core.trackedentity.TrackedEntityAttributeValue
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstanceCreateProjection
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
import org.hisp.dhis.rules.models.RuleAttributeValue
import org.hisp.dhis.rules.models.RuleEnrollment
import org.hisp.dhis.rules.models.RuleValueType
import org.hisp.dhis.rules.models.RuleVariable
import org.hisp.dhis.rules.models.RuleVariableAttribute
import org.hisp.dhis.rules.models.RuleVariableCalculatedValue
import org.hisp.dhis.rules.models.RuleVariableCurrentEvent
import org.hisp.dhis.rules.models.RuleVariableNewestEvent
import org.hisp.dhis.rules.models.RuleVariableNewestStageEvent
import org.hisp.dhis.rules.models.RuleVariablePreviousEvent
import java.util.*
import javax.inject.Inject

data class TrackerEnrollmentState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val saveSuccess: Boolean = false,
    val canSave: Boolean = false,

    // Program Information
    val programId: String = "",
    val programName: String = "",
    val supportsIncidentDate: Boolean = false,
    val incidentDateLabel: String? = null,

    // Form Data
    val selectedOrganisationUnitId: String? = null,
    val availableOrganisationUnits: List<OrganisationUnit> = emptyList(),
    val enrollmentDate: Date? = null,
    val incidentDate: Date? = null,
    val trackedEntityAttributes: List<TrackedEntityAttribute> = emptyList(),
    val attributeValues: Map<String, String> = emptyMap(),

    // Validation (Reuse DataEntry validation patterns)
    val validationState: ValidationState = ValidationState.VALID,
    val validationErrors: List<String> = emptyList(),
    val validationMessage: String? = null,
    val programRuleEffect: ProgramRuleEffect? = null,

    // Reuse DataEntry state management patterns
    val saveInProgress: Boolean = false,
    val saveResult: Result<Unit>? = null,
    val isSyncing: Boolean = false,
    val detailedSyncProgress: DetailedSyncProgress? = null,
    val successMessage: String? = null,
    val isValidating: Boolean = false,
    val navigationProgress: NavigationProgress? = null,

    // Form state
    val isEditMode: Boolean = false,
    val currentStep: Int = 0,
    val expandedSection: String? = null
)

@HiltViewModel
class TrackerEnrollmentViewModel @Inject constructor(
    private val application: Application,
    private val sessionManager: SessionManager,
    private val repository: DatasetInstancesRepository,
    private val syncQueueManager: SyncQueueManager,
    private val networkStateManager: NetworkStateManager,
    private val syncStatusController: SyncStatusController
) : ViewModel() {

    private val _state = MutableStateFlow(TrackerEnrollmentState())
    val state: StateFlow<TrackerEnrollmentState> = _state.asStateFlow()
    private val _uiState = MutableStateFlow<UiState<TrackerEnrollmentState>>(
        UiState.Success(TrackerEnrollmentState())
    )
    val uiState: StateFlow<UiState<TrackerEnrollmentState>> = _uiState.asStateFlow()
    val syncController: SyncStatusController = syncStatusController

    private var d2: D2? = null
    private var enrollmentId: String? = null

    init {
        d2 = sessionManager.getD2()

        // Observe sync progress from SyncQueueManager (reuse DataEntry pattern)
        viewModelScope.launch {
            syncQueueManager.detailedProgress.collect { progress ->
                _state.update { currentState ->
                    currentState.copy(
                        detailedSyncProgress = progress,
                        isSyncing = progress != null
                    )
                }
                if (progress != null) {
                    _uiState.emitLoading(LoadingOperation.Syncing(progress))
                } else if (_uiState.value is UiState.Loading) {
                    _uiState.emitSuccess(_state.value)
                }
            }
        }
    }

    fun initializeNewEnrollment(programId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val progress = NavigationProgress(
                    phase = LoadingPhase.INITIALIZING,
                    overallPercentage = 10,
                    phaseTitle = "Initializing",
                    phaseDetail = "Preparing enrollment form...",
                    loadingType = StepLoadingType.ENTRY
                )
                _state.update { it.copy(isLoading = true, error = null, isEditMode = false, navigationProgress = progress) }
                _uiState.value = UiState.Loading(
                    LoadingOperation.Navigation(progress),
                    LoadingProgress(message = progress.phaseDetail)
                )

                val d2Instance = d2 ?: throw Exception("Not authenticated")

                // Load program metadata
                val program = d2Instance.programModule().programs()
                    .uid(programId)
                    .blockingGet()
                    ?: throw Exception("Program not found")

                // Load program tracked entity attributes
                val programTrackedEntityAttributes = d2Instance.programModule()
                    .programTrackedEntityAttributes()
                    .byProgram().eq(programId)
                    .blockingGet()

                val trackedEntityAttributes = programTrackedEntityAttributes.mapNotNull { ptea ->
                    val tea = d2Instance.trackedEntityModule()
                        .trackedEntityAttributes()
                        .uid(ptea.trackedEntityAttribute()?.uid())
                        .blockingGet()

                    tea?.let {
                        com.ash.simpledataentry.domain.model.TrackedEntityAttribute(
                            id = it.uid(),
                            displayName = it.displayName() ?: it.name() ?: "",
                            description = it.displayDescription(),
                            valueType = it.valueType()?.name ?: "TEXT",
                            mandatory = ptea.mandatory() ?: false
                        )
                    }
                }

                // Load available organisation units
                val orgUnits = d2Instance.organisationUnitModule()
                    .organisationUnits()
                    .byProgramUids(listOf(programId))
                    .blockingGet()
                    .map { orgUnit ->
                        OrganisationUnit(
                            id = orgUnit.uid(),
                            name = orgUnit.displayName() ?: orgUnit.name() ?: ""
                        )
                    }

                _state.update {
                    it.copy(
                        isLoading = false,
                        programId = programId,
                        programName = program.displayName() ?: program.name() ?: "",
                        supportsIncidentDate = program.incidentDateLabel() != null,
                        incidentDateLabel = program.incidentDateLabel(),
                        trackedEntityAttributes = trackedEntityAttributes,
                        availableOrganisationUnits = orgUnits,
                        enrollmentDate = Date() // Default to today
                    )
                }

                updateCanSave()
                evaluateProgramRules()
                _state.update { it.copy(navigationProgress = null) }
                _uiState.emitSuccess(_state.value)

            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to initialize enrollment: ${e.message}"
                    )
                }
                _uiState.emitError(e.toUiError(), _state.value)
            }
        }
    }

    fun loadEnrollment(enrollmentId: String) {
        this.enrollmentId = enrollmentId

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val progress = NavigationProgress(
                    phase = LoadingPhase.LOADING_DATA,
                    overallPercentage = 20,
                    phaseTitle = "Loading enrollment",
                    phaseDetail = "Fetching enrollment data...",
                    loadingType = StepLoadingType.ENTRY
                )
                _state.update { it.copy(isLoading = true, error = null, isEditMode = true, navigationProgress = progress) }
                _uiState.value = UiState.Loading(
                    LoadingOperation.Navigation(progress),
                    LoadingProgress(message = progress.phaseDetail)
                )

                val d2Instance = d2 ?: throw Exception("Not authenticated")

                // Load enrollment
                val enrollment = d2Instance.enrollmentModule().enrollments()
                    .uid(enrollmentId)
                    .blockingGet()
                    ?: throw Exception("Enrollment not found")

                // Load program metadata
                val program = d2Instance.programModule().programs()
                    .uid(enrollment.program())
                    .blockingGet()
                    ?: throw Exception("Program not found")

                // Load tracked entity attributes for the program
                val programTrackedEntityAttributes = d2Instance.programModule()
                    .programTrackedEntityAttributes()
                    .byProgram().eq(enrollment.program())
                    .blockingGet()

                val trackedEntityAttributes = programTrackedEntityAttributes.mapNotNull { ptea ->
                    val tea = d2Instance.trackedEntityModule()
                        .trackedEntityAttributes()
                        .uid(ptea.trackedEntityAttribute()?.uid())
                        .blockingGet()

                    tea?.let {
                        com.ash.simpledataentry.domain.model.TrackedEntityAttribute(
                            id = it.uid(),
                            displayName = it.displayName() ?: it.name() ?: "",
                            description = it.displayDescription(),
                            valueType = it.valueType()?.name ?: "TEXT",
                            mandatory = ptea.mandatory() ?: false
                        )
                    }
                }

                // Load tracked entity attribute values
                val attributeValues = d2Instance.trackedEntityModule()
                    .trackedEntityAttributeValues()
                    .byTrackedEntityInstance().eq(enrollment.trackedEntityInstance())
                    .blockingGet()
                    .associate { it.trackedEntityAttribute()!! to (it.value() ?: "") }

                // Load available organisation units
                val orgUnits = d2Instance.organisationUnitModule()
                    .organisationUnits()
                    .byProgramUids(listOf(enrollment.program()!!))
                    .blockingGet()
                    .map { orgUnit ->
                        OrganisationUnit(
                            id = orgUnit.uid(),
                            name = orgUnit.displayName() ?: orgUnit.name() ?: ""
                        )
                    }

                _state.update {
                    it.copy(
                        isLoading = false,
                        programId = enrollment.program()!!,
                        programName = program.displayName() ?: program.name() ?: "",
                        supportsIncidentDate = program.incidentDateLabel() != null,
                        incidentDateLabel = program.incidentDateLabel(),
                        selectedOrganisationUnitId = enrollment.organisationUnit(),
                        enrollmentDate = enrollment.enrollmentDate(),
                        incidentDate = enrollment.incidentDate(),
                        trackedEntityAttributes = trackedEntityAttributes,
                        attributeValues = attributeValues,
                        availableOrganisationUnits = orgUnits
                    )
                }

                updateCanSave()
                evaluateProgramRules()
                _state.update { it.copy(navigationProgress = null) }
                _uiState.emitSuccess(_state.value)

            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load enrollment: ${e.message}"
                    )
                }
                _uiState.emitError(e.toUiError(), _state.value)
            }
        }
    }

    fun updateOrganisationUnit(orgUnitId: String) {
        _state.update { it.copy(selectedOrganisationUnitId = orgUnitId) }
        updateCanSave()
    }

    fun updateEnrollmentDate(date: Date) {
        _state.update { it.copy(enrollmentDate = date) }
        updateCanSave()
    }

    fun updateIncidentDate(date: Date?) {
        _state.update { it.copy(incidentDate = date) }
        updateCanSave()
    }

    fun updateAttributeValue(attributeId: String, value: String) {
        _state.update { currentState ->
            val currentValues = currentState.attributeValues.toMutableMap()
            currentValues[attributeId] = value
            currentState.copy(attributeValues = currentValues)
        }
        evaluateProgramRules()
        updateCanSave()
    }

    private fun updateCanSave() {
        val currentState = _state.value
        val validationErrors = mutableListOf<String>()
        val programRuleEffect = currentState.programRuleEffect
        val hiddenFields = programRuleEffect?.hiddenFields ?: emptySet()
        val mandatoryFields = programRuleEffect?.mandatoryFields ?: emptySet()

        // Validate required fields
        if (currentState.selectedOrganisationUnitId.isNullOrBlank()) {
            validationErrors.add("Organisation unit is required")
        }

        if (currentState.enrollmentDate == null) {
            validationErrors.add("Enrollment date is required")
        }

        // Validate mandatory tracked entity attributes
        currentState.trackedEntityAttributes
            .filter { (it.mandatory || mandatoryFields.contains(it.id)) && !hiddenFields.contains(it.id) }
            .forEach { attribute ->
            val value = currentState.attributeValues[attribute.id]
            if (value.isNullOrBlank()) {
                validationErrors.add("${attribute.displayName} is required")
            }
        }
        programRuleEffect?.fieldErrors?.values?.forEach { errorMessage ->
            validationErrors.add(errorMessage)
        }

        _state.update {
            it.copy(
                validationErrors = validationErrors,
                validationState = if (validationErrors.isEmpty()) ValidationState.VALID else ValidationState.VALID,
                canSave = validationErrors.isEmpty(),
                validationMessage = if (validationErrors.isEmpty()) null else it.validationMessage
            )
        }
        if (_uiState.value is UiState.Success) {
            _uiState.emitSuccess(_state.value)
        }
    }

    private fun evaluateProgramRules() {
        val programId = _state.value.programId
        if (programId.isBlank()) return

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val d2Instance = d2 ?: return@launch
                val currentState = _state.value

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

                val enrollmentDate = currentState.enrollmentDate ?: Date()
                val incidentDate = currentState.incidentDate ?: enrollmentDate
                val orgUnit = currentState.selectedOrganisationUnitId ?: ""
                val attributeValues = currentState.attributeValues
                    .filterValues { it.isNotBlank() }
                    .map { (attributeId, value) ->
                        RuleAttributeValue.create(attributeId, value)
                    }

                val enrollmentUid = enrollmentId ?: "NEW_ENROLLMENT"
                val ruleEnrollment = RuleEnrollment.create(
                    enrollmentUid,
                    incidentDate,
                    enrollmentDate,
                    RuleEnrollment.Status.ACTIVE,
                    orgUnit,
                    null,
                    attributeValues,
                    currentState.programName
                )

                val ruleEngine = ruleEngineContext.toEngineBuilder()
                    .enrollment(ruleEnrollment)
                    .build()

                val ruleEffects = ruleEngine.evaluate(ruleEnrollment).call()
                val effect = convertToRuleEffect(ruleEffects)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    applyRuleEffects(effect)
                }
            } catch (e: Exception) {
                android.util.Log.e("TrackerEnrollmentVM", "Program rule evaluation failed: ${e.message}", e)
            }
        }
    }

    private fun applyRuleEffects(effect: ProgramRuleEffect) {
        _state.update { currentState ->
            val updatedAttributes = currentState.attributeValues.toMutableMap()
            effect.calculatedValues.forEach { (field, value) ->
                updatedAttributes[field] = value
            }
            currentState.copy(
                attributeValues = updatedAttributes,
                programRuleEffect = effect
            )
        }
        updateCanSave()
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
                    val value = effect.ruleAction().data() ?: ""
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

    fun saveEnrollment() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _state.update { it.copy(saveInProgress = true, error = null) }
                if (_uiState.value is UiState.Success) {
                    _uiState.emitSuccess(_state.value)
                }

                val d2Instance = d2 ?: throw Exception("Not authenticated")
                val currentState = _state.value

                if (!currentState.canSave) {
                    _state.update {
                        it.copy(
                            saveInProgress = false,
                            validationMessage = "Please fix the highlighted fields before saving."
                        )
                    }
                    if (_uiState.value is UiState.Success) {
                        _uiState.emitSuccess(_state.value)
                    }
                    return@launch
                }

                if (enrollmentId != null) {
                    // Update existing enrollment
                    updateExistingEnrollment(d2Instance, enrollmentId!!, currentState)
                } else {
                    // Create new enrollment
                    createNewEnrollment(d2Instance, currentState)
                }

                _state.update {
                    it.copy(
                        saveInProgress = false,
                        saveSuccess = true,
                        saveResult = Result.success(Unit),
                        successMessage = if (enrollmentId != null) "Enrollment updated successfully" else "Enrollment created successfully"
                    )
                }
                if (_uiState.value is UiState.Success) {
                    _uiState.emitSuccess(_state.value)
                }

            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        saveInProgress = false,
                        saveResult = Result.failure(e),
                        error = "Failed to save enrollment: ${e.message}"
                    )
                }
                if (_uiState.value is UiState.Success) {
                    _uiState.emitSuccess(_state.value)
                }
            }
        }
    }

    fun clearValidationMessage() {
        _state.update {
            it.copy(validationMessage = null)
        }
        if (_uiState.value is UiState.Success) {
            _uiState.emitSuccess(_state.value)
        }
    }

    // Add sync functionality (reuse DataEntry pattern)
    fun syncEnrollment() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentState = _state.value
                if (currentState.programId.isBlank()) return@launch

                _state.update {
                    it.copy(
                        navigationProgress = NavigationProgress(
                            phase = com.ash.simpledataentry.presentation.core.LoadingPhase.INITIALIZING,
                            overallPercentage = 10,
                            phaseTitle = "Preparing sync",
                            phaseDetail = "Preparing enrollment sync...",
                            loadingType = StepLoadingType.SYNC
                        )
                    )
                }

                // Use repository sync method
                repository.syncProgramInstances(currentState.programId, ProgramType.TRACKER)
                    .onSuccess {
                        _state.update {
                            it.copy(
                                navigationProgress = NavigationProgress(
                                    phase = com.ash.simpledataentry.presentation.core.LoadingPhase.PROCESSING_DATA,
                                    overallPercentage = 85,
                                    phaseTitle = "Refreshing data",
                                    phaseDetail = "Updating local enrollments...",
                                    loadingType = StepLoadingType.SYNC
                                )
                            )
                        }
                        _state.update {
                            it.copy(successMessage = "Enrollment synced successfully")
                        }
                    }
                    .onFailure { error ->
                        _state.update {
                            it.copy(error = "Sync failed: ${error.message}")
                        }
                    }
                _state.update { it.copy(navigationProgress = null) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(error = "Sync failed: ${e.message}")
                }
                _state.update { it.copy(navigationProgress = null) }
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

    private fun createNewEnrollment(d2: D2, state: TrackerEnrollmentState) {
        // Get tracked entity type for the program (reuse existing helper)
        val trackedEntityTypeUid = getTrackedEntityTypeForProgram(d2, state.programId)

        // Create tracked entity instance using TrackedEntityInstanceCreateProjection
        val teiUid = d2.trackedEntityModule().trackedEntityInstances().add(
            TrackedEntityInstanceCreateProjection.builder()
                .organisationUnit(state.selectedOrganisationUnitId!!)
                .trackedEntityType(trackedEntityTypeUid)
                .build()
        ).blockingGet()

        // Add tracked entity attribute values
        state.attributeValues.forEach { (attributeId, value) ->
            if (value.isNotBlank()) {
                d2.trackedEntityModule().trackedEntityAttributeValues()
                    .value(attributeId, teiUid)
                    .set(value)
            }
        }

        // Create enrollment using EnrollmentCreateProjection
        val enrollmentUid = d2.enrollmentModule().enrollments().add(
            EnrollmentCreateProjection.builder()
                .trackedEntityInstance(teiUid)
                .program(state.programId)
                .organisationUnit(state.selectedOrganisationUnitId!!)
                .build()
        ).blockingGet()

        // Set enrollment date
        d2.enrollmentModule().enrollments().uid(enrollmentUid).setEnrollmentDate(state.enrollmentDate!!)

        // Set incident date if provided
        if (state.incidentDate != null) {
            d2.enrollmentModule().enrollments().uid(enrollmentUid).setIncidentDate(state.incidentDate)
        }
    }

    private fun updateExistingEnrollment(d2: D2, enrollmentId: String, state: TrackerEnrollmentState) {
        // Update enrollment dates
        d2.enrollmentModule().enrollments().uid(enrollmentId)
            .setEnrollmentDate(state.enrollmentDate!!)

        if (state.incidentDate != null) {
            d2.enrollmentModule().enrollments().uid(enrollmentId)
                .setIncidentDate(state.incidentDate)
        }

        // Get tracked entity instance from enrollment
        val enrollment = d2.enrollmentModule().enrollments().uid(enrollmentId).blockingGet()
        val teiUid = enrollment?.trackedEntityInstance()
            ?: throw Exception("Tracked entity instance not found for enrollment")

        // Update tracked entity attribute values
        state.attributeValues.forEach { (attributeId, value) ->
            if (value.isNotBlank()) {
                d2.trackedEntityModule().trackedEntityAttributeValues()
                    .value(attributeId, teiUid)
                    .set(value)
            } else {
                // Remove empty values
                d2.trackedEntityModule().trackedEntityAttributeValues()
                    .value(attributeId, teiUid)
                    .delete()
            }
        }
    }

    private fun getTrackedEntityTypeForProgram(d2: D2, programId: String): String {
        val program = d2.programModule().programs().uid(programId).blockingGet()
        return program?.trackedEntityType()?.uid()
            ?: throw Exception("Tracked entity type not found for program")
    }
}
