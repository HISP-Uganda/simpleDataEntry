package com.ash.simpledataentry.presentation.tracker

import android.content.Context
import android.content.SharedPreferences
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
import com.ash.simpledataentry.presentation.core.UiState
import com.ash.simpledataentry.util.toUiError
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.HashSet
import java.util.Locale

data class TableColumn(
    val id: String,
    val displayName: String,
    val sortable: Boolean = true
)

data class EnrollmentTableRow(
    val enrollment: ProgramInstance.TrackerEnrollment,
    val cells: Map<String, String>
)

enum class SortOrder {
    ASCENDING,
    DESCENDING,
    NONE
}

data class TrackerEnrollmentTableData(
    val enrollments: List<ProgramInstance.TrackerEnrollment> = emptyList(),
    val tableRows: List<EnrollmentTableRow> = emptyList(),
    val columns: List<TableColumn> = emptyList(),
    val availableColumns: List<TableColumn> = emptyList(),
    val selectedColumnIds: Set<String> = emptySet(),
    val showColumnDialog: Boolean = false,
    val searchQuery: String = "",
    val sortColumnId: String? = null,
    val sortOrder: SortOrder = SortOrder.NONE,
    val successMessage: String? = null,
    val programName: String = ""
)

@HiltViewModel
class TrackerEnrollmentTableViewModel @Inject constructor(
    private val datasetInstancesRepository: DatasetInstancesRepository,
    private val sessionManager: SessionManager,
    private val syncStatusController: SyncStatusController,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private var programId: String = ""
    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences("tracker_table_columns", Context.MODE_PRIVATE)
    }
    private val defaultColumnIds = setOf("enrollmentDate", "orgUnit")

    private val _uiState = MutableStateFlow<UiState<TrackerEnrollmentTableData>>(
        UiState.Loading(LoadingOperation.Initial)
    )
    val uiState: StateFlow<UiState<TrackerEnrollmentTableData>> = _uiState.asStateFlow()

    val syncState = syncStatusController.appSyncState
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
        _uiState.value = UiState.Loading(LoadingOperation.Initial)
    }

    private fun getCurrentData(): TrackerEnrollmentTableData {
        return when (val current = _uiState.value) {
            is UiState.Success -> current.data
            is UiState.Error -> current.previousData ?: TrackerEnrollmentTableData()
            is UiState.Loading -> TrackerEnrollmentTableData()
        }
    }

    private fun updateData(transform: (TrackerEnrollmentTableData) -> TrackerEnrollmentTableData) {
        val newData = transform(getCurrentData())
        _uiState.value = UiState.Success(newData)
    }

    fun initialize(id: String, programName: String) {
        if (id.isNotEmpty() && id != programId) {
            programId = id
            updateData { it.copy(programName = programName) }
            Log.d(TAG, "Initializing TrackerEnrollmentTableViewModel with program: $programId")
            loadData()
        }
    }

    fun refreshData() {
        Log.d(TAG, "Refreshing tracker enrollment table data")
        loadData()
    }

    private fun loadData() {
        if (programId.isEmpty()) {
            Log.e(TAG, "Cannot load data: programId is empty")
            return
        }

        Log.d(TAG, "Loading tracker enrollments for program: $programId")
        val previousData = getCurrentData()
        _uiState.value = UiState.Loading(LoadingOperation.Initial)

        viewModelScope.launch {
            try {
                datasetInstancesRepository.getTrackerEnrollments(programId)
                    .catch { exception ->
                        Log.e(TAG, "Error loading tracker enrollments", exception)
                        val uiError = exception.toUiError()
                        _uiState.value = UiState.Error(uiError, previousData)
                    }
                    .collect { instances ->
                        val enrollments = instances.filterIsInstance<ProgramInstance.TrackerEnrollment>()
                        val availableColumns = buildAllColumns(enrollments)
                        val orderedAvailableColumns = applyColumnOrder(availableColumns)
                        val selectedColumnIds = loadSelectedColumns(orderedAvailableColumns)
                        val displayedColumns = orderedAvailableColumns.filter { it.id in selectedColumnIds }
                        val tableRows = buildTableRows(enrollments, displayedColumns)

                        val baseData = previousData.copy(
                            enrollments = enrollments,
                            columns = displayedColumns,
                            availableColumns = orderedAvailableColumns,
                            selectedColumnIds = selectedColumnIds,
                            tableRows = tableRows,
                            successMessage = null
                        )
                        _uiState.value = UiState.Success(baseData)
                        applySearchAndSort()
                    }
            } catch (exception: Exception) {
                Log.e(TAG, "Error loading tracker enrollments", exception)
                val uiError = exception.toUiError()
                _uiState.value = UiState.Error(uiError, previousData)
            }
        }
    }

    private fun buildAllColumns(enrollments: List<ProgramInstance.TrackerEnrollment>): List<TableColumn> {
        val columns = mutableListOf(
            TableColumn("enrollmentDate", "Enrollment Date"),
            TableColumn("orgUnit", "Organization Unit"),
            TableColumn("status", "Status"),
            TableColumn("syncStatus", "Sync")
        )

        if (enrollments.isNotEmpty()) {
            val firstEnrollment = enrollments.first()
            firstEnrollment.attributes.forEach { attr ->
                if (columns.none { it.id == attr.id }) {
                    columns.add(TableColumn(attr.id, attr.displayName))
                }
            }
        }

        return columns
    }

    private fun loadSelectedColumns(availableColumns: List<TableColumn>): Set<String> {
        val key = "selected_columns_$programId"
        val savedSelection = sharedPreferences.getStringSet(key, null)

        if (savedSelection != null && savedSelection.isNotEmpty()) {
            return savedSelection.toSet()
        }

        val defaults = mutableSetOf<String>()
        defaults.addAll(defaultColumnIds)

        val commonAttributeNames = setOf(
            "first name", "firstname", "given name",
            "last name", "lastname", "surname", "family name",
            "name", "full name",
            "age", "date of birth", "dob", "birth date",
            "gender", "sex"
        )

        availableColumns.forEach { column ->
            val displayNameLower = column.displayName.lowercase(Locale.getDefault())
            if (commonAttributeNames.any { displayNameLower.contains(it) }) {
                defaults.add(column.id)
            }
        }

        if (defaults.isEmpty()) {
            defaults.addAll(availableColumns.take(4).map { it.id })
        }

        return defaults
    }

    private fun loadColumnOrder(): List<String>? {
        val key = "column_order_$programId"
        val savedOrder = sharedPreferences.getString(key, null)
        return savedOrder?.split(",")?.filter { it.isNotEmpty() }
    }

    private fun saveColumnOrder(columnIds: List<String>) {
        val key = "column_order_$programId"
        sharedPreferences.edit()
            .putString(key, columnIds.joinToString(","))
            .apply()
    }

    private fun saveSelectedColumns(selectedIds: Set<String>) {
        val key = "selected_columns_$programId"
        sharedPreferences.edit()
            .putStringSet(key, HashSet(selectedIds))
            .apply()
    }

    private fun applyColumnOrder(columns: List<TableColumn>): List<TableColumn> {
        val savedOrder = loadColumnOrder() ?: return columns
        val columnMap = columns.associateBy { it.id }
        val orderedColumns = mutableListOf<TableColumn>()

        savedOrder.forEach { columnId ->
            columnMap[columnId]?.let { orderedColumns.add(it) }
        }

        columns.forEach { column ->
            if (column.id !in savedOrder) {
                orderedColumns.add(column)
            }
        }

        return orderedColumns
    }

    private fun buildTableRows(
        enrollments: List<ProgramInstance.TrackerEnrollment>,
        columns: List<TableColumn>
    ): List<EnrollmentTableRow> {
        return enrollments.map { enrollment ->
            val cells = mutableMapOf<String, String>()
            cells["enrollmentDate"] = dateFormatter.format(enrollment.enrollmentDate)
            cells["orgUnit"] = enrollment.organisationUnit.name
            cells["status"] = enrollment.state.name
            cells["syncStatus"] = when (enrollment.syncStatus) {
                com.ash.simpledataentry.domain.model.SyncStatus.SYNCED -> "Synced"
                com.ash.simpledataentry.domain.model.SyncStatus.NOT_SYNCED -> "Not Synced"
                com.ash.simpledataentry.domain.model.SyncStatus.ALL -> "All"
            }

            enrollment.attributes.forEach { attr ->
                cells[attr.id] = attr.value ?: ""
            }

            EnrollmentTableRow(enrollment, cells)
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
        var rows = buildTableRows(currentState.enrollments, currentState.columns)

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

    fun syncEnrollments() {
        if (programId.isEmpty()) {
            Log.e(TAG, "Cannot sync: programId is empty")
            return
        }

        Log.d(TAG, "Starting sync for tracker program: $programId")
        val currentData = getCurrentData()
        val syncProgress = NavigationProgress(
            phase = LoadingPhase.PROCESSING,
            message = "Syncing enrollments",
            percentage = 0,
            phaseTitle = "Syncing enrollments",
            phaseDetail = "Uploading and downloading tracker data..."
        )
        _uiState.value = UiState.Loading(
            operation = LoadingOperation.Navigation(syncProgress),
            progress = LoadingProgress(message = syncProgress.phaseDetail)
        )

        viewModelScope.launch {
            try {
                val result = datasetInstancesRepository.syncProgramInstances(
                    programId = programId,
                    programType = com.ash.simpledataentry.domain.model.ProgramType.TRACKER
                )

                if (result.isSuccess) {
                    _uiState.value = UiState.Success(
                        currentData.copy(successMessage = "Sync completed successfully")
                    )
                    loadData()
                } else {
                    result.exceptionOrNull()?.let { exception ->
                        Log.e(TAG, "Error during tracker sync", exception)
                        val uiError = exception.toUiError()
                        _uiState.value = UiState.Error(uiError, currentData)
                    }
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Error during tracker sync", exception)
                val uiError = exception.toUiError()
                _uiState.value = UiState.Error(uiError, currentData)
            }
        }
    }

    fun clearSuccessMessage() {
        updateData { it.copy(successMessage = null) }
    }

    fun showColumnDialog() {
        updateData { it.copy(showColumnDialog = true) }
    }

    fun hideColumnDialog() {
        updateData { it.copy(showColumnDialog = false) }
    }

    fun toggleColumnSelection(columnId: String) {
        val currentSelection = getCurrentData().selectedColumnIds.toMutableSet()
        if (columnId in currentSelection) {
            currentSelection.remove(columnId)
        } else {
            currentSelection.add(columnId)
        }
        updateData { it.copy(selectedColumnIds = currentSelection) }
    }

    fun selectAllColumns() {
        val allColumnIds = getCurrentData().availableColumns.map { it.id }.toSet()
        updateData { it.copy(selectedColumnIds = allColumnIds) }
    }

    fun deselectAllColumns() {
        updateData { it.copy(selectedColumnIds = emptySet()) }
    }

    fun moveColumn(columnId: String, moveUp: Boolean) {
        val availableColumns = getCurrentData().availableColumns.toMutableList()
        val currentIndex = availableColumns.indexOfFirst { it.id == columnId }
        if (currentIndex == -1) return

        val newIndex = if (moveUp) {
            (currentIndex - 1).coerceAtLeast(0)
        } else {
            (currentIndex + 1).coerceAtMost(availableColumns.size - 1)
        }

        if (currentIndex != newIndex) {
            val column = availableColumns.removeAt(currentIndex)
            availableColumns.add(newIndex, column)
            updateData { it.copy(availableColumns = availableColumns) }
        }
    }

    fun applyColumnSelection() {
        val currentData = getCurrentData()
        val selectedIds = currentData.selectedColumnIds
        val availableColumns = currentData.availableColumns

        saveSelectedColumns(selectedIds)
        saveColumnOrder(availableColumns.map { it.id })

        val displayedColumns = availableColumns.filter { it.id in selectedIds }
        val tableRows = buildTableRows(currentData.enrollments, displayedColumns)

        val newData = currentData.copy(
            columns = displayedColumns,
            tableRows = tableRows,
            showColumnDialog = false
        )
        _uiState.value = UiState.Success(newData)
        applySearchAndSort()

        Log.d(TAG, "Applied column selection: ${selectedIds.size} columns selected")
    }

    companion object {
        private const val TAG = "TrackerEnrollTableVM"
    }
}
