package com.ash.simpledataentry.presentation.event

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.simpledataentry.domain.model.ProgramInstance
import com.ash.simpledataentry.domain.repository.DatasetInstancesRepository
import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.presentation.core.UiState
import com.ash.simpledataentry.presentation.core.LoadingOperation
import com.ash.simpledataentry.presentation.core.LoadingPhase
import com.ash.simpledataentry.presentation.core.LoadingProgress
import com.ash.simpledataentry.presentation.core.NavigationProgress
import com.ash.simpledataentry.presentation.core.StepLoadingType
import com.ash.simpledataentry.util.toUiError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class EventTableColumn(
    val id: String,
    val displayName: String,
    val sortable: Boolean = false
)

data class EventTableRow(
    val id: String,
    val cells: Map<String, String>
)

// Pure data model (no UI state like isLoading)
data class EventInstancesData(
    val events: List<ProgramInstance.EventInstance> = emptyList(),
    val program: com.ash.simpledataentry.domain.model.Program? = null,
    val syncMessage: String? = null,
    val lineListColumns: List<EventTableColumn> = emptyList(),
    val lineListRows: List<EventTableRow> = emptyList(),
    val lineListLoading: Boolean = false,
    val lineListEventIds: List<String> = emptyList(),
    val visibleLineListColumnIds: Set<String> = emptySet()
)

@HiltViewModel
class EventInstancesViewModel @Inject constructor(
    private val datasetInstancesRepository: DatasetInstancesRepository,
    private val sessionManager: SessionManager,
    private val app: Application
) : ViewModel() {

    private var programId: String = ""

    private val _uiState = MutableStateFlow<UiState<EventInstancesData>>(
        UiState.Success(EventInstancesData())
    )
    val uiState: StateFlow<UiState<EventInstancesData>> = _uiState.asStateFlow()

    private val d2 = sessionManager.getD2()
    private val dataElementNameCache = mutableMapOf<String, String>()

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
        _uiState.value = UiState.Success(EventInstancesData())
    }

    private fun getCurrentData(): EventInstancesData {
        return when (val current = _uiState.value) {
            is UiState.Success -> current.data
            is UiState.Error -> current.previousData ?: EventInstancesData()
            is UiState.Loading -> EventInstancesData()
        }
    }

    fun initialize(id: String) {
        if (id.isNotEmpty() && id != programId) {
            programId = id
            Log.d(TAG, "Initializing EventInstancesViewModel with program: $programId")
            loadData()
        }
    }

    fun refreshData() {
        Log.d(TAG, "Refreshing event instances data")
        loadData()
    }

    private fun loadData() {
        if (programId.isEmpty()) {
            Log.e(TAG, "Cannot load data: programId is empty")
            return
        }

        Log.d(TAG, "Loading event instances for program: $programId")

        viewModelScope.launch {
            try {
                _uiState.value = UiState.Loading(LoadingOperation.Initial)

                // Load event instances directly
                datasetInstancesRepository.getEventInstances(programId)
                    .catch { exception ->
                        Log.e(TAG, "Error loading event instances", exception)
                        val uiError = exception.toUiError()
                        _uiState.value = UiState.Error(uiError, getCurrentData())
                    }
                    .collect { instances ->
                        val events = instances.filterIsInstance<ProgramInstance.EventInstance>()
                        Log.d(TAG, "Loaded ${events.size} event instances")

                        val currentData = getCurrentData()
                        val data = currentData.copy(
                            events = events,
                            syncMessage = null,
                            lineListColumns = emptyList(),
                            lineListRows = emptyList(),
                            lineListLoading = false,
                            lineListEventIds = emptyList()
                        )
                        _uiState.value = UiState.Success(data)
                    }
            } catch (exception: Exception) {
                Log.e(TAG, "Error loading event instances", exception)
                val uiError = exception.toUiError()
                _uiState.value = UiState.Error(uiError, getCurrentData())
            }
        }
    }

    fun syncEvents() {
        if (programId.isEmpty()) {
            Log.e(TAG, "Cannot sync: programId is empty")
            return
        }

        Log.d(TAG, "Starting sync for event program: $programId")

        viewModelScope.launch {
            try {
                val currentData = getCurrentData()
                _uiState.value = UiState.Loading(
                    LoadingOperation.Navigation(
                        NavigationProgress(
                            phase = LoadingPhase.INITIALIZING,
                            overallPercentage = 5,
                            phaseTitle = "Preparing sync",
                            phaseDetail = "Preparing event sync...",
                            loadingType = StepLoadingType.SYNC
                        )
                    ),
                    LoadingProgress(message = "Preparing event sync...")
                )

                // Sync event instances and data
                val result = datasetInstancesRepository.syncProgramInstances(
                    programId = programId,
                    programType = com.ash.simpledataentry.domain.model.ProgramType.EVENT
                )

                if (result.isSuccess) {
                    _uiState.value = UiState.Loading(
                        LoadingOperation.Navigation(
                            NavigationProgress(
                                phase = LoadingPhase.PROCESSING_DATA,
                                overallPercentage = 85,
                                phaseTitle = "Refreshing data",
                                phaseDetail = "Updating local event data...",
                                loadingType = StepLoadingType.SYNC
                            )
                        ),
                        LoadingProgress(message = "Updating local event data...")
                    )
                    val syncData = currentData.copy(syncMessage = "Sync completed successfully")
                    _uiState.value = UiState.Success(syncData)
                    // Refresh data after sync
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
                _uiState.value = UiState.Error(uiError, getCurrentData())
            }
        }
    }

    fun loadLineList(events: List<ProgramInstance.EventInstance>) {
        val currentData = getCurrentData()
        val eventIds = events.map { it.id }
        if (eventIds == currentData.lineListEventIds && currentData.lineListColumns.isNotEmpty()) {
            return
        }

        _uiState.value = UiState.Success(currentData.copy(lineListLoading = true))
        viewModelScope.launch {
            val (columns, rows) = withContext(Dispatchers.IO) {
                buildEventTableData(events)
            }
            val updatedData = getCurrentData().copy(
                lineListColumns = columns,
                lineListRows = rows,
                lineListLoading = false,
                lineListEventIds = eventIds,
                visibleLineListColumnIds = mergeVisibleColumns(columns.map { it.id }.toSet())
            )
            _uiState.value = UiState.Success(updatedData)
        }
    }

    fun updateVisibleLineListColumns(visibleColumnIds: Set<String>) {
        val current = getCurrentData()
        val filtered = visibleColumnIds.intersect(current.lineListColumns.map { it.id }.toSet())
        _uiState.value = UiState.Success(
            current.copy(visibleLineListColumnIds = filtered)
        )
    }

    private fun mergeVisibleColumns(columnIds: Set<String>): Set<String> {
        val current = getCurrentData().visibleLineListColumnIds
        if (columnIds.isEmpty()) return emptySet()
        if (current.isEmpty()) return columnIds
        val kept = current.intersect(columnIds)
        return if (kept.isEmpty()) {
            columnIds
        } else {
            kept + (columnIds - current)
        }
    }

    fun buildEventTableData(
        events: List<ProgramInstance.EventInstance>
    ): Pair<List<EventTableColumn>, List<EventTableRow>> {
        val d2Instance = d2 ?: return Pair(emptyList(), emptyList())
        if (events.isEmpty()) return Pair(emptyList(), emptyList())

        val stageIds = events.mapNotNull { it.programStage }.filter { it.isNotBlank() }.distinct()
        val stageId = if (stageIds.size == 1) stageIds.first() else null

        val columns = mutableListOf<EventTableColumn>()
        columns.add(EventTableColumn("eventDate", "Date", sortable = false))
        columns.add(EventTableColumn("status", "Status", sortable = false))

        if (stageId != null) {
            try {
                val programStageDataElements = d2Instance.programModule()
                    .programStageDataElements()
                    .byProgramStage().eq(stageId)
                    .blockingGet()

                programStageDataElements.forEach { psde ->
                    val dataElementId = psde.dataElement()?.uid() ?: return@forEach
                    val dataElementName = dataElementNameCache.getOrPut(dataElementId) {
                        try {
                            val de = d2Instance.dataElementModule().dataElements()
                                .uid(dataElementId)
                                .blockingGet()
                            de?.displayName() ?: de?.name() ?: dataElementId
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to load data element name: ${e.message}")
                            dataElementId
                        }
                    }

                    columns.add(EventTableColumn(dataElementId, dataElementName, sortable = false))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to build event line list columns: ${e.message}")
            }
        }

        val dateFormatter = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
        val rows = events.map { event ->
            val cells = mutableMapOf<String, String>()
            cells["eventDate"] = event.eventDate?.let { dateFormatter.format(it) } ?: "No date"
            cells["status"] = event.state.name

            if (stageId != null) {
                try {
                    val dataValues = d2Instance.trackedEntityModule()
                        .trackedEntityDataValues()
                        .byEvent().eq(event.id)
                        .blockingGet()
                    dataValues.forEach { dataValue ->
                        val dataElementId = dataValue.dataElement() ?: return@forEach
                        cells[dataElementId] = dataValue.value() ?: ""
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load data values for event ${event.id}: ${e.message}")
                }
            }

            EventTableRow(
                id = event.id,
                cells = cells
            )
        }

        return columns to rows
    }

    companion object {
        private const val TAG = "EventInstancesVM"
    }
}
