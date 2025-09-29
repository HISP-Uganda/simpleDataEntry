package com.ash.simpledataentry.presentation.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.data.sync.DetailedSyncProgress
import com.ash.simpledataentry.data.sync.SyncQueueManager
import com.ash.simpledataentry.data.sync.NetworkStateManager
import com.ash.simpledataentry.domain.model.Event
import com.ash.simpledataentry.domain.model.TrackedEntityAttributeValue
import com.ash.simpledataentry.domain.repository.DatasetInstancesRepository
import com.ash.simpledataentry.domain.model.ProgramType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.hisp.dhis.android.core.D2
import java.util.*
import javax.inject.Inject

data class TrackerDashboardUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,

    // Enrollment Information
    val enrollmentId: String = "",
    val programId: String = "",
    val programName: String = "",
    val trackedEntityInstanceId: String = "",
    val organisationUnitName: String? = null,
    val enrollmentDate: Date? = null,
    val incidentDate: Date? = null,
    val incidentDateLabel: String? = null,
    val enrollmentStatus: String = "",
    val canAddEvents: Boolean = false,

    // Tracked Entity Attributes
    val trackedEntityAttributes: List<TrackedEntityAttributeValue> = emptyList(),

    // Events
    val events: List<Event> = emptyList(),

    // Sync functionality (reuse DataEntry patterns)
    val isSyncing: Boolean = false,
    val detailedSyncProgress: DetailedSyncProgress? = null
)

@HiltViewModel
class TrackerDashboardViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val repository: DatasetInstancesRepository,
    private val syncQueueManager: SyncQueueManager,
    private val networkStateManager: NetworkStateManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrackerDashboardUiState())
    val uiState: StateFlow<TrackerDashboardUiState> = _uiState.asStateFlow()

    private var d2: D2? = null

    init {
        d2 = sessionManager.getD2()

        // Observe sync progress (reuse DataEntry pattern)
        viewModelScope.launch {
            syncQueueManager.detailedProgress.collect { progress ->
                _uiState.update { it.copy(
                    detailedSyncProgress = progress,
                    isSyncing = progress != null
                ) }
            }
        }
    }

    fun loadEnrollmentDashboard(enrollmentId: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null, successMessage = null) }

                val d2Instance = d2 ?: throw Exception("Not authenticated")

                // Load enrollment
                val enrollment = d2Instance.enrollmentModule().enrollments()
                    .uid(enrollmentId)
                    .blockingGet()
                    ?: throw Exception("Enrollment not found")

                // Load program
                val program = d2Instance.programModule().programs()
                    .uid(enrollment.program())
                    .blockingGet()
                    ?: throw Exception("Program not found")

                // Load organisation unit
                val orgUnit = d2Instance.organisationUnitModule()
                    .organisationUnits()
                    .uid(enrollment.organisationUnit())
                    .blockingGet()

                // Load tracked entity attributes
                val trackedEntityAttributes = loadTrackedEntityAttributes(
                    d2Instance,
                    enrollment.trackedEntityInstance()!!,
                    enrollment.program()!!
                )

                // Load events for this enrollment
                val events = loadEnrollmentEvents(d2Instance, enrollmentId)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    enrollmentId = enrollmentId,
                    programId = enrollment.program()!!,
                    programName = program.displayName() ?: program.name() ?: "",
                    trackedEntityInstanceId = enrollment.trackedEntityInstance()!!,
                    organisationUnitName = orgUnit?.displayName() ?: orgUnit?.name(),
                    enrollmentDate = enrollment.enrollmentDate(),
                    incidentDate = enrollment.incidentDate(),
                    incidentDateLabel = program.incidentDateLabel(),
                    enrollmentStatus = enrollment.status()?.name ?: "UNKNOWN",
                    canAddEvents = enrollment.status()?.name == "ACTIVE",
                    trackedEntityAttributes = trackedEntityAttributes,
                    events = events
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load enrollment dashboard: ${e.message}"
                )
            }
        }
    }

    private fun loadTrackedEntityAttributes(
        d2: D2,
        trackedEntityInstanceId: String,
        programId: String
    ): List<TrackedEntityAttributeValue> {
        // Load program tracked entity attributes
        val programTrackedEntityAttributes = d2.programModule()
            .programTrackedEntityAttributes()
            .byProgram().eq(programId)
            .blockingGet()

        // Load tracked entity attribute values
        val attributeValues = d2.trackedEntityModule()
            .trackedEntityAttributeValues()
            .byTrackedEntityInstance().eq(trackedEntityInstanceId)
            .blockingGet()

        return programTrackedEntityAttributes.mapNotNull { ptea ->
            val attributeId = ptea.trackedEntityAttribute()?.uid() ?: return@mapNotNull null

            // Get attribute metadata
            val attribute = d2.trackedEntityModule()
                .trackedEntityAttributes()
                .uid(attributeId)
                .blockingGet() ?: return@mapNotNull null

            // Get attribute value
            val value = attributeValues.find { it.trackedEntityAttribute() == attributeId }

            TrackedEntityAttributeValue(
                id = attributeId,
                displayName = attribute.displayName() ?: attribute.name() ?: "",
                value = value?.value()
            )
        }
    }

    private fun loadEnrollmentEvents(d2: D2, enrollmentId: String): List<Event> {
        val events = d2.eventModule().events()
            .byEnrollmentUid().eq(enrollmentId)
            .orderByEventDate(org.hisp.dhis.android.core.arch.repositories.scope.RepositoryScope.OrderByDirection.DESC)
            .blockingGet()

        return events.map { event ->
            // Load program stage for display name
            val programStage = d2.programModule().programStages()
                .uid(event.programStage())
                .blockingGet()

            Event(
                id = event.uid(),
                programId = event.program()!!,
                programStageId = event.programStage()!!,
                programStageName = programStage?.displayName() ?: programStage?.name(),
                enrollmentId = event.enrollment(),
                organisationUnitId = event.organisationUnit()!!,
                eventDate = event.eventDate(),
                status = event.status()?.name ?: "UNKNOWN",
                completedDate = event.completedDate(),
                lastUpdated = event.lastUpdated()
            )
        }
    }
}