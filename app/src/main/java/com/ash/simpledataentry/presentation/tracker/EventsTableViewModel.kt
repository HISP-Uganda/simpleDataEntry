package com.ash.simpledataentry.presentation.tracker

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.domain.model.ProgramInstance
import com.ash.simpledataentry.domain.repository.DatasetInstancesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.hisp.dhis.android.core.D2
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * Represents an event table column configuration
 */
data class EventTableColumn(
    val id: String,
    val displayName: String,
    val sortable: Boolean = true
)

/**
 * Represents a row in the event table
 */
data class EventTableRow(
    val id: String,
    val programStageId: String?,
    val enrollmentId: String?,
    val cells: Map<String, String> // columnId -> display value
)

data class EventsTableState(
    val events: List<ProgramInstance.EventInstance> = emptyList(),
    val tableRows: List<EventTableRow> = emptyList(),
    val allTableRows: List<EventTableRow> = emptyList(), // Unfiltered rows for search/sort base
    val columns: List<EventTableColumn> = emptyList(),
    val searchQuery: String = "",
    val sortColumnId: String? = null,
    val sortOrder: SortOrder = SortOrder.NONE,
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val programName: String = ""
)

@HiltViewModel
class EventsTableViewModel @Inject constructor(
    private val datasetInstancesRepository: DatasetInstancesRepository,
    private val sessionManager: SessionManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private var programId: String = ""
    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private lateinit var d2Instance: D2

    // Cache for data element display names to avoid repeated database lookups
    private val dataElementNameCache = mutableMapOf<String, String>()

    private val _state = MutableStateFlow(EventsTableState())
    val state: StateFlow<EventsTableState> = _state.asStateFlow()

    init {
        // Account change observer
        viewModelScope.launch {
            sessionManager.currentAccountId.collect { accountId ->
                if (accountId == null) {
                    resetToInitialState()
                } else {
                    val previouslyInitialized = programId.isNotEmpty()
                    if (previouslyInitialized) {
                        resetToInitialState()
                    }
                }
            }
        }
    }

    private fun resetToInitialState() {
        programId = ""
        dataElementNameCache.clear() // CRITICAL: Clear cached names from previous account
        _state.value = EventsTableState()
    }

    fun initialize(id: String, programName: String) {
        if (id.isNotEmpty() && id != programId) {
            programId = id
            _state.value = _state.value.copy(programName = programName)
            Log.d(TAG, "Initializing EventsTableViewModel with program: $programId")

            // Initialize D2
            d2Instance = sessionManager.getD2() ?: run {
                Log.e(TAG, "D2 instance not available")
                _state.value = _state.value.copy(error = "DHIS2 SDK not initialized")
                return
            }

            // Pre-load data element names for this program
            viewModelScope.launch {
                loadDataElementNamesForProgram(programId)
            }

            loadData()
        }
    }

    /**
     * Pre-load all data element names for the program to avoid blocking calls later
     */
    private suspend fun loadDataElementNamesForProgram(programId: String) {
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                // Get program stages for this program
                val programStages = d2Instance.programModule().programStages()
                    .byProgramUid().eq(programId)
                    .blockingGet()

                // For each stage, get its data elements
                programStages.forEach { stage ->
                    val stageDataElements = d2Instance.programModule().programStageDataElements()
                        .byProgramStage().eq(stage.uid())
                        .blockingGet()

                    stageDataElements.forEach { psde ->
                        val dataElementUid = psde.dataElement()?.uid()
                        if (dataElementUid != null) {
                            val dataElement = d2Instance.dataElementModule().dataElements()
                                .uid(dataElementUid)
                                .blockingGet()

                            dataElement?.let { de ->
                                dataElementNameCache[de.uid()] = de.displayName() ?: de.uid()
                            }
                        }
                    }
                }
                Log.d(TAG, "Pre-loaded ${dataElementNameCache.size} data element names")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to pre-load data element names: ${e.message}")
        }
    }

    fun refreshData() {
        Log.d(TAG, "Refreshing event table data")
        loadData()
    }

    private fun loadData() {
        if (programId.isEmpty()) {
            Log.e(TAG, "Cannot load data: programId is empty")
            return
        }

        Log.d(TAG, "Loading events for program: $programId")

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            try {
                // Load events
                datasetInstancesRepository.getEventInstances(programId)
                    .catch { exception ->
                        Log.e(TAG, "Error loading events", exception)
                        _state.value = _state.value.copy(
                            isLoading = false,
                            error = "Failed to load events: ${exception.message}"
                        )
                    }
                    .collect { instances ->
                        // getEventInstances() already returns only EventInstance types
                        val events = instances
                        Log.d(TAG, "Loaded ${events.size} events")

                        // Build columns from events
                        val columns = buildColumns(events)

                        // Build table rows
                        val tableRows = buildTableRows(events, columns)

                        _state.value = _state.value.copy(
                            events = events,
                            columns = columns,
                            allTableRows = tableRows, // Store unfiltered rows
                            tableRows = tableRows,
                            isLoading = false,
                            error = null
                        )

                        // Apply search and sort if needed
                        applySearchAndSort()
                    }
            } catch (exception: Exception) {
                Log.e(TAG, "Error loading events", exception)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to load events: ${exception.message}"
                )
            }
        }
    }

    /**
     * Build columns from events
     * Includes fixed columns plus data elements from the program stage
     */
    private fun buildColumns(events: List<ProgramInstance.EventInstance>): List<EventTableColumn> {
        val columns = mutableListOf<EventTableColumn>()

        // Add fixed columns
        columns.add(EventTableColumn("eventDate", "Event Date", sortable = true))
        columns.add(EventTableColumn("orgUnit", "Organization Unit", sortable = true))
        columns.add(EventTableColumn("status", "Status", sortable = true))
        columns.add(EventTableColumn("syncStatus", "Sync", sortable = true))

        // Add data element columns from first event (if any)
        if (events.isNotEmpty()) {
            val firstEvent = events.first()

            // Get data elements from the event's data values
            firstEvent.dataValues.forEach { dataValue ->
                if (columns.none { it.id == dataValue.dataElement }) {
                    // Use cached data element name (pre-loaded in initialize())
                    // Fallback to data element ID if not found in cache
                    val dataElementName = dataElementNameCache[dataValue.dataElement]
                        ?: dataValue.dataElement

                    columns.add(
                        EventTableColumn(
                            dataValue.dataElement,
                            dataElementName,
                            sortable = true
                        )
                    )
                }
            }
        }

        return columns
    }

    private fun buildTableRows(
        events: List<ProgramInstance.EventInstance>,
        columns: List<EventTableColumn>
    ): List<EventTableRow> {
        return events.map { event ->
            val cells = mutableMapOf<String, String>()

            // Fill fixed column values
            cells["eventDate"] = event.eventDate?.let { dateFormatter.format(it) } ?: "No date"
            cells["orgUnit"] = event.organisationUnit.name
            cells["status"] = event.state.name
            cells["syncStatus"] = when (event.syncStatus) {
                com.ash.simpledataentry.domain.model.SyncStatus.SYNCED -> "Synced"
                com.ash.simpledataentry.domain.model.SyncStatus.NOT_SYNCED -> "Not Synced"
                com.ash.simpledataentry.domain.model.SyncStatus.ALL -> "All"
            }

            // Fill data element values
            event.dataValues.forEach { dataValue ->
                cells[dataValue.dataElement] = dataValue.value ?: ""
            }

            EventTableRow(
                id = event.id,
                programStageId = event.programStage,
                enrollmentId = null,
                cells = cells
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        applySearchAndSort()
    }

    fun sortByColumn(columnId: String) {
        val currentSort = _state.value.sortColumnId
        val currentOrder = _state.value.sortOrder

        val newOrder = if (currentSort == columnId) {
            when (currentOrder) {
                SortOrder.NONE -> SortOrder.ASCENDING
                SortOrder.ASCENDING -> SortOrder.DESCENDING
                SortOrder.DESCENDING -> SortOrder.NONE
            }
        } else {
            SortOrder.ASCENDING
        }

        _state.value = _state.value.copy(
            sortColumnId = if (newOrder == SortOrder.NONE) null else columnId,
            sortOrder = newOrder
        )

        applySearchAndSort()
    }

    private fun applySearchAndSort() {
        val currentState = _state.value
        // Start with all unfiltered rows (cached, not rebuilt each time)
        var rows = currentState.allTableRows

        // Apply search filter
        if (currentState.searchQuery.isNotBlank()) {
            val query = currentState.searchQuery.lowercase()
            rows = rows.filter { row ->
                row.cells.values.any { cellValue ->
                    cellValue.lowercase().contains(query)
                }
            }
        }

        // Apply sorting
        if (currentState.sortColumnId != null && currentState.sortOrder != SortOrder.NONE) {
            val columnId = currentState.sortColumnId
            rows = when (currentState.sortOrder) {
                SortOrder.ASCENDING -> {
                    rows.sortedBy { it.cells[columnId]?.lowercase() ?: "" }
                }
                SortOrder.DESCENDING -> {
                    rows.sortedByDescending { it.cells[columnId]?.lowercase() ?: "" }
                }
                SortOrder.NONE -> rows
            }
        }

        _state.value = currentState.copy(tableRows = rows)
    }

    fun syncEvents() {
        if (programId.isEmpty()) {
            Log.e(TAG, "Cannot sync: programId is empty")
            return
        }

        Log.d(TAG, "Starting sync for event program: $programId")

        viewModelScope.launch {
            _state.value = _state.value.copy(isSyncing = true, error = null)

            try {
                // Sync events
                val result = datasetInstancesRepository.syncProgramInstances(
                    programId = programId,
                    programType = com.ash.simpledataentry.domain.model.ProgramType.EVENT
                )

                if (result.isSuccess) {
                    _state.value = _state.value.copy(
                        isSyncing = false,
                        successMessage = "Sync completed successfully"
                    )
                    // Refresh data after sync
                    loadData()
                } else {
                    result.exceptionOrNull()?.let { exception ->
                        Log.e(TAG, "Error during event sync", exception)
                        _state.value = _state.value.copy(
                            isSyncing = false,
                            error = "Sync failed: ${exception.message}"
                        )
                    }
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Error during event sync", exception)
                _state.value = _state.value.copy(
                    isSyncing = false,
                    error = "Sync failed: ${exception.message}"
                )
            }
        }
    }

    fun clearSuccessMessage() {
        _state.value = _state.value.copy(successMessage = null)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    companion object {
        private const val TAG = "EventsTableVM"
    }
}
