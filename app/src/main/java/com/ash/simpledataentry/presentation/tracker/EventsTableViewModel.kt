package com.ash.simpledataentry.presentation.tracker

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.data.sync.SyncStatusController
import com.ash.simpledataentry.domain.model.ProgramInstance
import com.ash.simpledataentry.domain.repository.DatasetInstancesRepository
import com.ash.simpledataentry.presentation.core.LoadingOperation
import com.ash.simpledataentry.presentation.core.LoadingPhase
import com.ash.simpledataentry.presentation.core.LoadingProgress
import com.ash.simpledataentry.presentation.core.NavigationProgress
import com.ash.simpledataentry.presentation.core.UiError
import com.ash.simpledataentry.presentation.core.UiState
import com.ash.simpledataentry.util.toUiError
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.hisp.dhis.android.core.D2
import java.text.SimpleDateFormat
import java.util.Locale

data class EventTableColumn(
    val id: String,
    val displayName: String,
    val sortable: Boolean = true
)

data class EventTableRow(
    val id: String,
    val programStageId: String?,
    val enrollmentId: String?,
    val cells: Map<String, String>
)

data class EventsTableData(
    val events: List<ProgramInstance.EventInstance> = emptyList(),
    val tableRows: List<EventTableRow> = emptyList(),
    val allTableRows: List<EventTableRow> = emptyList(),
    val columns: List<EventTableColumn> = emptyList(),
    val searchQuery: String = "",
    val sortColumnId: String? = null,
    val sortOrder: SortOrder = SortOrder.NONE,
    val successMessage: String? = null,
    val programName: String = ""
)

@HiltViewModel
class EventsTableViewModel @Inject constructor(
    private val datasetInstancesRepository: DatasetInstancesRepository,
    private val sessionManager: SessionManager,
    private val syncStatusController: SyncStatusController,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private var programId: String = ""
    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private var d2Instance: D2? = null

    private val dataElementNameCache = mutableMapOf<String, String>()

    private val _uiState = MutableStateFlow<UiState<EventsTableData>>(
        UiState.Loading(LoadingOperation.Initial)
    )
    val uiState: StateFlow<UiState<EventsTableData>> = _uiState.asStateFlow()

    val syncController: SyncStatusController = syncStatusController

    init {
        viewModelScope.launch {
            sessionManager.currentAccountId.collect { accountId ->
                if (accountId == null) {
                    resetToInitialState()
                } else if (programId.isNotEmpty()) {
                    resetToInitialState()
                }
            }
        }
    }

    private fun resetToInitialState() {
        programId = ""
        dataElementNameCache.clear()
        _uiState.value = UiState.Loading(LoadingOperation.Initial)
    }

    private fun getCurrentData(): EventsTableData {
        return when (val current = _uiState.value) {
            is UiState.Success -> current.data
            is UiState.Error -> current.previousData ?: EventsTableData()
            is UiState.Loading -> EventsTableData()
        }
    }

    private fun updateData(transform: (EventsTableData) -> EventsTableData) {
        val newData = transform(getCurrentData())
        _uiState.value = UiState.Success(newData)
    }

    fun initialize(id: String, programName: String) {
        if (id.isNotEmpty() && id != programId) {
            programId = id
            updateData { it.copy(programName = programName) }
            Log.d(TAG, "Initializing EventsTableViewModel with program: $programId")

            d2Instance = sessionManager.getD2()
            if (d2Instance == null) {
                _uiState.value = UiState.Error(
                    UiError.Local("DHIS2 SDK not initialized"),
                    getCurrentData()
                )
                return
            }

            viewModelScope.launch {
                loadDataElementNamesForProgram(programId)
            }

            loadData()
        }
    }

    private suspend fun loadDataElementNamesForProgram(programId: String) {
        val d2 = d2Instance ?: return
        try {
            withContext(Dispatchers.IO) {
                val programStages = d2.programModule().programStages()
                    .byProgramUid().eq(programId)
                    .blockingGet()

                programStages.forEach { stage ->
                    val stageDataElements = d2.programModule().programStageDataElements()
                        .byProgramStage().eq(stage.uid())
                        .blockingGet()

                    stageDataElements.forEach { psde ->
                        val dataElementUid = psde.dataElement()?.uid()
                        if (dataElementUid != null) {
                            val dataElement = d2.dataElementModule().dataElements()
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

        val previousData = getCurrentData()
        val navigationProgress = NavigationProgress(
            phase = LoadingPhase.LOADING_DATA,
            message = "Loading events",
            percentage = 0,
            phaseTitle = "Loading events",
            phaseDetail = "Fetching latest data..."
        )
        _uiState.value = UiState.Loading(
            operation = LoadingOperation.Navigation(navigationProgress),
            progress = LoadingProgress(message = navigationProgress.phaseDetail)
        )

        viewModelScope.launch {
            try {
                datasetInstancesRepository.getEventInstances(programId)
                    .catch { exception ->
                        Log.e(TAG, "Error loading events", exception)
                        val uiError = exception.toUiError()
                        _uiState.value = UiState.Error(uiError, previousData)
                    }
                    .collect { events ->
                        val columns = buildColumns(events)
                        val tableRows = buildTableRows(events, columns)

                        val data = previousData.copy(
                            events = events,
                            columns = columns,
                            allTableRows = tableRows,
                            tableRows = tableRows,
                            successMessage = null
                        )
                        _uiState.value = UiState.Success(data)
                        applySearchAndSort()
                    }
            } catch (exception: Exception) {
                Log.e(TAG, "Error loading events", exception)
                val uiError = exception.toUiError()
                _uiState.value = UiState.Error(uiError, previousData)
            }
        }
    }

    private fun buildColumns(events: List<ProgramInstance.EventInstance>): List<EventTableColumn> {
        val columns = mutableListOf(
            EventTableColumn("eventDate", "Event Date"),
            EventTableColumn("orgUnit", "Organization Unit"),
            EventTableColumn("status", "Status"),
            EventTableColumn("syncStatus", "Sync")
        )

        if (events.isNotEmpty()) {
            val firstEvent = events.first()
            firstEvent.dataValues.forEach { dataValue ->
                if (columns.none { it.id == dataValue.dataElement }) {
                    val dataElementName = dataElementNameCache[dataValue.dataElement]
                        ?: dataValue.dataElement
                    columns.add(
                        EventTableColumn(
                            dataValue.dataElement,
                            dataElementName
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
            cells["eventDate"] = event.eventDate?.let { dateFormatter.format(it) } ?: "No date"
            cells["orgUnit"] = event.organisationUnit.name
            cells["status"] = event.state.name
            cells["syncStatus"] = when (event.syncStatus) {
                com.ash.simpledataentry.domain.model.SyncStatus.SYNCED -> "Synced"
                com.ash.simpledataentry.domain.model.SyncStatus.NOT_SYNCED -> "Not Synced"
                com.ash.simpledataentry.domain.model.SyncStatus.ALL -> "All"
            }

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
        updateData { it.copy(searchQuery = query) }
        applySearchAndSort()
    }

    fun sortByColumn(columnId: String) {
        val currentState = getCurrentData()
        val newOrder = if (currentState.sortColumnId == columnId) {
            when (currentState.sortOrder) {
                SortOrder.NONE -> SortOrder.ASCENDING
                SortOrder.ASCENDING -> SortOrder.DESCENDING
                SortOrder.DESCENDING -> SortOrder.NONE
            }
        } else {
            SortOrder.ASCENDING
        }

        updateData {
            it.copy(
                sortColumnId = if (newOrder == SortOrder.NONE) null else columnId,
                sortOrder = newOrder
            )
        }
        applySearchAndSort()
    }

    private fun applySearchAndSort() {
        val currentState = getCurrentData()
        var rows = currentState.allTableRows

        if (currentState.searchQuery.isNotBlank()) {
            val query = currentState.searchQuery.lowercase(Locale.getDefault())
            rows = rows.filter { row ->
                row.cells.values.any { cellValue ->
                    cellValue.lowercase(Locale.getDefault()).contains(query)
                }
            }
        }

        if (currentState.sortColumnId != null && currentState.sortOrder != SortOrder.NONE) {
            val columnId = currentState.sortColumnId
            rows = when (currentState.sortOrder) {
                SortOrder.ASCENDING -> rows.sortedBy { it.cells[columnId]?.lowercase(Locale.getDefault()) ?: "" }
                SortOrder.DESCENDING -> rows.sortedByDescending { it.cells[columnId]?.lowercase(Locale.getDefault()) ?: "" }
                SortOrder.NONE -> rows
            }
        }

        updateData { it.copy(tableRows = rows) }
    }

    fun syncEvents() {
        if (programId.isEmpty()) {
            Log.e(TAG, "Cannot sync: programId is empty")
            return
        }

        val currentData = getCurrentData()
        val syncProgress = NavigationProgress(
            phase = LoadingPhase.PROCESSING,
            message = "Syncing events",
            percentage = 0,
            phaseTitle = "Syncing events",
            phaseDetail = "Uploading and downloading data..."
        )
        _uiState.value = UiState.Loading(
            operation = LoadingOperation.Navigation(syncProgress),
            progress = LoadingProgress(message = syncProgress.phaseDetail)
        )

        viewModelScope.launch {
            try {
                val result = datasetInstancesRepository.syncProgramInstances(
                    programId = programId,
                    programType = com.ash.simpledataentry.domain.model.ProgramType.EVENT
                )

                if (result.isSuccess) {
                    _uiState.value = UiState.Success(
                        currentData.copy(successMessage = "Sync completed successfully")
                    )
                    loadData()
                } else {
                    result.exceptionOrNull()?.let { exception ->
                        Log.e(TAG, "Error during event sync", exception)
                        val uiError = exception.toUiError()
                        _uiState.value = UiState.Error(uiError, currentData)
                    }
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Error during event sync", exception)
                val uiError = exception.toUiError()
                _uiState.value = UiState.Error(uiError, currentData)
            }
        }
    }

    fun clearSuccessMessage() {
        updateData { it.copy(successMessage = null) }
    }

    companion object {
        private const val TAG = "EventsTableVM"
    }
}
