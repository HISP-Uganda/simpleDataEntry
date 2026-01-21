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
import com.ash.simpledataentry.presentation.core.UiState
import com.ash.simpledataentry.presentation.core.LoadingOperation
import com.ash.simpledataentry.presentation.core.emitLoading
import com.ash.simpledataentry.presentation.core.emitSuccess
import com.ash.simpledataentry.presentation.core.emitError
import com.ash.simpledataentry.util.toUiError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.common.State
import java.util.*
import javax.inject.Inject

/**
 * Data class for event table columns used in compact table display
 */
data class EventTableColumn(
    val id: String,
    val displayName: String,
    val sortable: Boolean = false
)

/**
 * Data class for event table rows used in compact table display
 */
data class EventTableRow(
    val id: String,
    val programStageId: String,
    val enrollmentId: String?,
    val cells: Map<String, String>
)

data class TrackerDashboardData(
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
    val syncState: State? = null,
    val canAddEvents: Boolean = false,

    // Tracked Entity Attributes
    val trackedEntityAttributes: List<TrackedEntityAttributeValue> = emptyList(),

    // Events
    val events: List<Event> = emptyList(),
    val filteredEvents: List<Event> = emptyList(),
    val eventsByStage: Map<String, List<Event>> = emptyMap(),

    // Program Stages (for event creation and filtering)
    val programStages: List<com.ash.simpledataentry.domain.model.ProgramStage> = emptyList(),
    val selectedStageId: String? = null,  // null = "All Stages"
    val eventCountsByStage: Map<String, Int> = emptyMap(),  // programStageId -> count
    val showStageSelectionDialog: Boolean = false,

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

    private val _uiState = MutableStateFlow<UiState<TrackerDashboardData>>(
        UiState.Loading(LoadingOperation.Initial)
    )
    val uiState: StateFlow<UiState<TrackerDashboardData>> = _uiState.asStateFlow()

    private var lastSuccessfulData: TrackerDashboardData? = null

    private var d2: D2? = null

    // Cache for data element display names to avoid repeated database lookups
    private val dataElementNameCache = mutableMapOf<String, String>()

    init {
        d2 = sessionManager.getD2()

        // Observe sync progress (reuse DataEntry pattern)
        viewModelScope.launch {
            syncQueueManager.detailedProgress.collect { progress ->
                val current = lastSuccessfulData ?: return@collect
                val updated = current.copy(
                    detailedSyncProgress = progress,
                    isSyncing = progress != null
                )
                lastSuccessfulData = updated
                if (_uiState.value is UiState.Success) {
                    _uiState.emitSuccess(updated)
                }
            }
        }
    }

    fun loadEnrollmentDashboard(enrollmentId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.emitLoading(LoadingOperation.Initial)

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

                // Load program stages
                val programStages = loadProgramStages(d2Instance, enrollment.program()!!)

                // Calculate event counts by stage
                val eventCountsByStage = events.groupBy { it.programStageId }
                    .mapValues { it.value.size }

                val eventsByStage = events.groupBy { it.programStageId }
                val data = TrackerDashboardData(
                    enrollmentId = enrollmentId,
                    programId = enrollment.program()!!,
                    programName = program.displayName() ?: program.name() ?: "",
                    trackedEntityInstanceId = enrollment.trackedEntityInstance()!!,
                    organisationUnitName = orgUnit?.displayName() ?: orgUnit?.name(),
                    enrollmentDate = enrollment.enrollmentDate(),
                    incidentDate = enrollment.incidentDate(),
                    incidentDateLabel = program.incidentDateLabel(),
                    enrollmentStatus = enrollment.status()?.name ?: "UNKNOWN",
                    syncState = enrollment.aggregatedSyncState(),
                    canAddEvents = enrollment.status()?.name == "ACTIVE",
                    trackedEntityAttributes = trackedEntityAttributes,
                    events = events,
                    filteredEvents = events,  // Initially show all events
                    eventsByStage = eventsByStage,
                    programStages = programStages,
                    selectedStageId = null,  // Initially "All Stages"
                    eventCountsByStage = eventCountsByStage
                )
                lastSuccessfulData = data
                _uiState.emitSuccess(data)

            } catch (e: Exception) {
                _uiState.emitError(e.toUiError(), lastSuccessfulData)
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

    private fun loadProgramStages(d2: D2, programId: String): List<com.ash.simpledataentry.domain.model.ProgramStage> {
        val stages = d2.programModule().programStages()
            .byProgramUid().eq(programId)
            .orderBySortOrder(org.hisp.dhis.android.core.arch.repositories.scope.RepositoryScope.OrderByDirection.ASC)
            .blockingGet()

        return stages.map { stage ->
            com.ash.simpledataentry.domain.model.ProgramStage(
                id = stage.uid(),
                name = stage.displayName() ?: stage.name() ?: "",
                description = stage.description(),
                programId = programId,
                repeatable = stage.repeatable() ?: false,
                sortOrder = stage.sortOrder() ?: 0,
                executionDateLabel = stage.executionDateLabel(),
                dueDateLabel = stage.dueDateLabel()
            )
        }
    }

    fun showStageSelectionDialog() {
        val current = lastSuccessfulData ?: return
        val updated = current.copy(showStageSelectionDialog = true)
        lastSuccessfulData = updated
        _uiState.emitSuccess(updated)
    }

    fun hideStageSelectionDialog() {
        val current = lastSuccessfulData ?: return
        val updated = current.copy(showStageSelectionDialog = false)
        lastSuccessfulData = updated
        _uiState.emitSuccess(updated)
    }

    /**
     * Filter events by program stage
     * @param stageId The program stage ID to filter by, or null for "All Stages"
     */
    fun filterEventsByStage(stageId: String?) {
        val currentState = lastSuccessfulData ?: return
        val filteredEvents = if (stageId == null) {
            // Show all events
            currentState.events
        } else {
            // Filter by selected stage
            currentState.events.filter { it.programStageId == stageId }
        }

        val updated = currentState.copy(
            selectedStageId = stageId,
            filteredEvents = filteredEvents
        )
        lastSuccessfulData = updated
        _uiState.emitSuccess(updated)
    }

    /**
     * Build table columns and rows for event display
     * Reuses EventsTableViewModel pattern with caching for performance
     */
    suspend fun buildEventTableData(
        events: List<Event>,
        programStageId: String
    ): Pair<List<EventTableColumn>, List<EventTableRow>> {
        return withContext(Dispatchers.IO) {
            val d2Instance = d2 ?: return@withContext Pair(emptyList(), emptyList())

            if (events.isEmpty()) {
                return@withContext Pair(emptyList(), emptyList())
            }

            val dateFormatter = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
            val columns = mutableListOf<EventTableColumn>()

            // Add fixed columns
            columns.add(EventTableColumn("eventDate", "Date", sortable = false))
            columns.add(EventTableColumn("status", "Status", sortable = false))

            // Load data elements for this program stage and build columns
            try {
                val programStageDataElements = d2Instance.programModule()
                    .programStageDataElements()
                    .byProgramStage().eq(programStageId)
                    .blockingGet()

                programStageDataElements.forEach { psde ->
                    val dataElementId = psde.dataElement()?.uid() ?: return@forEach

                    // Use cached data element name
                    val dataElementName = dataElementNameCache.getOrPut(dataElementId) {
                        try {
                            val de = d2Instance.dataElementModule().dataElements()
                                .uid(dataElementId)
                                .blockingGet()
                            de?.displayName() ?: de?.name() ?: dataElementId
                        } catch (e: Exception) {
                            android.util.Log.w("TrackerDashboardVM", "Failed to load data element name: ${e.message}")
                            dataElementId
                        }
                    }

                    columns.add(
                        EventTableColumn(
                            dataElementId,
                            dataElementName,
                            sortable = false
                        )
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("TrackerDashboardVM", "Failed to build columns: ${e.message}")
            }

            // Build table rows
            val tableRows = events.map { event ->
                val cells = mutableMapOf<String, String>()

                // Fill fixed column values
                cells["eventDate"] = event.eventDate?.let { dateFormatter.format(it) } ?: "No date"
                cells["status"] = event.status

                // Load data values for this event
                try {
                    val dataValues = d2Instance.trackedEntityModule()
                        .trackedEntityDataValues()
                        .byEvent().eq(event.id)
                        .blockingGet()

                    dataValues.forEach { dataValue ->
                        cells[dataValue.dataElement()!!] = dataValue.value() ?: ""
                    }
                } catch (e: Exception) {
                    android.util.Log.w("TrackerDashboardVM", "Failed to load data values for event: ${e.message}")
                }

                EventTableRow(
                    id = event.id,
                    programStageId = event.programStageId,
                    enrollmentId = event.enrollmentId,
                    cells = cells
                )
            }

            Pair(columns, tableRows)
        }
    }
}
