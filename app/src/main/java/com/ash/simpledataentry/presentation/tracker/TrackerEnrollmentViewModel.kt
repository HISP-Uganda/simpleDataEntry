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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.enrollment.Enrollment
import org.hisp.dhis.android.core.enrollment.EnrollmentCreateProjection
import org.hisp.dhis.android.core.trackedentity.TrackedEntityAttributeValue
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstanceCreateProjection
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
            }
        }
    }

    fun initializeNewEnrollment(programId: String) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true, error = null, isEditMode = false) }

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

            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to initialize enrollment: ${e.message}"
                    )
                }
            }
        }
    }

    fun loadEnrollment(enrollmentId: String) {
        this.enrollmentId = enrollmentId

        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true, error = null, isEditMode = true) }

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

            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load enrollment: ${e.message}"
                    )
                }
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
        updateCanSave()
    }

    private fun updateCanSave() {
        val currentState = _state.value
        val validationErrors = mutableListOf<String>()

        // Validate required fields
        if (currentState.selectedOrganisationUnitId.isNullOrBlank()) {
            validationErrors.add("Organisation unit is required")
        }

        if (currentState.enrollmentDate == null) {
            validationErrors.add("Enrollment date is required")
        }

        // Validate mandatory tracked entity attributes
        currentState.trackedEntityAttributes.filter { it.mandatory }.forEach { attribute ->
            val value = currentState.attributeValues[attribute.id]
            if (value.isNullOrBlank()) {
                validationErrors.add("${attribute.displayName} is required")
            }
        }

        _state.update {
            it.copy(
                validationErrors = validationErrors,
                validationState = if (validationErrors.isEmpty()) ValidationState.VALID else ValidationState.VALID,
                canSave = validationErrors.isEmpty()
            )
        }
    }

    fun saveEnrollment() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(saveInProgress = true, error = null) }

                val d2Instance = d2 ?: throw Exception("Not authenticated")
                val currentState = _state.value

                if (!currentState.canSave) {
                    throw Exception("Cannot save: validation errors exist")
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

            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        saveInProgress = false,
                        saveResult = Result.failure(e),
                        error = "Failed to save enrollment: ${e.message}"
                    )
                }
            }
        }
    }

    // Add sync functionality (reuse DataEntry pattern)
    fun syncEnrollment() {
        viewModelScope.launch {
            try {
                val currentState = _state.value
                if (currentState.programId.isBlank()) return@launch

                // Use repository sync method
                repository.syncProgramInstances(currentState.programId, ProgramType.TRACKER)
                    .onSuccess {
                        _state.update {
                            it.copy(successMessage = "Enrollment synced successfully")
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
