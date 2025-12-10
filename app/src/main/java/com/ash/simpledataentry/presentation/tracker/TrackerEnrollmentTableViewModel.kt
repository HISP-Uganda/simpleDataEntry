package com.ash.simpledataentry.presentation.tracker

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.data.sync.DetailedSyncProgress
import com.ash.simpledataentry.domain.model.ProgramInstance
import com.ash.simpledataentry.domain.model.TrackedEntityAttributeValue
import com.ash.simpledataentry.domain.repository.DatasetInstancesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * Represents a table column configuration
 */
data class TableColumn(
    val id: String,
    val displayName: String,
    val sortable: Boolean = true
)

/**
 * Represents a row in the enrollment table
 */
data class EnrollmentTableRow(
    val enrollment: ProgramInstance.TrackerEnrollment,
    val cells: Map<String, String> // columnId -> display value
)

/**
 * Sort order for columns
 */
enum class SortOrder {
    ASCENDING,
    DESCENDING,
    NONE
}

data class TrackerEnrollmentTableState(
    val enrollments: List<ProgramInstance.TrackerEnrollment> = emptyList(),
    val tableRows: List<EnrollmentTableRow> = emptyList(),
    val columns: List<TableColumn> = emptyList(),
    val availableColumns: List<TableColumn> = emptyList(),
    val selectedColumnIds: Set<String> = emptySet(),
    val showColumnDialog: Boolean = false,
    val searchQuery: String = "",
    val sortColumnId: String? = null,
    val sortOrder: SortOrder = SortOrder.NONE,
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val programName: String = "",
    val detailedSyncProgress: DetailedSyncProgress? = null
)

@HiltViewModel
class TrackerEnrollmentTableViewModel @Inject constructor(
    private val datasetInstancesRepository: DatasetInstancesRepository,
    private val sessionManager: SessionManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private var programId: String = ""
    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences("tracker_table_columns", Context.MODE_PRIVATE)
    }

    // Default columns shown on first load (intelligent defaults for tracker programs)
    private val defaultColumnIds = setOf("enrollmentDate", "orgUnit")

    private val _state = MutableStateFlow(TrackerEnrollmentTableState())
    val state: StateFlow<TrackerEnrollmentTableState> = _state.asStateFlow()

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
        _state.value = TrackerEnrollmentTableState()
        // Note: SharedPreferences column customizations are keyed by programId only
        // If new account has same programId, they'll see previous customizations
        // This is acceptable edge case (very unlikely)
    }

    fun initialize(id: String, programName: String) {
        if (id.isNotEmpty() && id != programId) {
            programId = id
            _state.value = _state.value.copy(programName = programName)
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

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            try {
                // Load enrollments
                datasetInstancesRepository.getTrackerEnrollments(programId)
                    .catch { exception ->
                        Log.e(TAG, "Error loading tracker enrollments", exception)
                        _state.value = _state.value.copy(
                            isLoading = false,
                            error = "Failed to load enrollments: ${exception.message}"
                        )
                    }
                    .collect { instances ->
                        val enrollments = instances.filterIsInstance<ProgramInstance.TrackerEnrollment>()
                        Log.d(TAG, "Loaded ${enrollments.size} tracker enrollments")

                        // Build all available columns
                        val availableColumns = buildAllColumns(enrollments)

                        // Apply saved column order to available columns
                        val orderedAvailableColumns = applyColumnOrder(availableColumns)

                        // Load saved column preferences
                        val selectedColumnIds = loadSelectedColumns(orderedAvailableColumns)

                        // Filter to only selected columns (maintaining order)
                        val displayedColumns = orderedAvailableColumns.filter { it.id in selectedColumnIds }

                        // Build table rows
                        val tableRows = buildTableRows(enrollments, displayedColumns)

                        _state.value = _state.value.copy(
                            enrollments = enrollments,
                            columns = displayedColumns,
                            availableColumns = orderedAvailableColumns,
                            selectedColumnIds = selectedColumnIds,
                            tableRows = tableRows,
                            isLoading = false,
                            error = null
                        )

                        // Apply search and sort if needed
                        applySearchAndSort()
                    }
            } catch (exception: Exception) {
                Log.e(TAG, "Error loading tracker enrollments", exception)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to load enrollments: ${exception.message}"
                )
            }
        }
    }

    /**
     * Build all available columns (both fixed and attributes)
     */
    private fun buildAllColumns(enrollments: List<ProgramInstance.TrackerEnrollment>): List<TableColumn> {
        val columns = mutableListOf<TableColumn>()

        // Add fixed columns (always present)
        columns.add(TableColumn("enrollmentDate", "Enrollment Date", sortable = true))
        columns.add(TableColumn("orgUnit", "Organization Unit", sortable = true))
        columns.add(TableColumn("status", "Status", sortable = true))
        columns.add(TableColumn("syncStatus", "Sync", sortable = true))

        // Add attribute columns from first enrollment (if any)
        if (enrollments.isNotEmpty()) {
            val firstEnrollment = enrollments.first()
            firstEnrollment.attributes.forEach { attr ->
                // Add each unique attribute as a column
                if (columns.none { it.id == attr.id }) {
                    columns.add(TableColumn(attr.id, attr.displayName, sortable = true))
                }
            }
        }

        return columns
    }

    /**
     * Load saved column selection from SharedPreferences
     * Returns intelligent defaults if no preference exists
     */
    private fun loadSelectedColumns(availableColumns: List<TableColumn>): Set<String> {
        val key = "selected_columns_$programId"
        val savedSelection = sharedPreferences.getStringSet(key, null)

        return if (savedSelection != null) {
            // Use saved selection
            savedSelection.toSet()
        } else {
            // Intelligent defaults: enrollment date, org unit, and common tracker attributes
            val defaults = mutableSetOf<String>()

            // Always include enrollment date and org unit
            defaults.addAll(defaultColumnIds)

            // Add common tracker attributes if they exist
            val commonAttributeNames = setOf(
                "first name", "firstname", "given name",
                "last name", "lastname", "surname", "family name",
                "name", "full name",
                "age", "date of birth", "dob", "birth date",
                "gender", "sex"
            )

            availableColumns.forEach { column ->
                val displayNameLower = column.displayName.lowercase()
                if (commonAttributeNames.any { displayNameLower.contains(it) }) {
                    defaults.add(column.id)
                }
            }

            defaults
        }
    }

    /**
     * Load saved column order from SharedPreferences
     * Returns null if no order preference exists
     */
    private fun loadColumnOrder(): List<String>? {
        val key = "column_order_$programId"
        val savedOrder = sharedPreferences.getString(key, null)
        return savedOrder?.split(",")?.filter { it.isNotEmpty() }
    }

    /**
     * Save column order to SharedPreferences
     */
    private fun saveColumnOrder(columnIds: List<String>) {
        val key = "column_order_$programId"
        sharedPreferences.edit()
            .putString(key, columnIds.joinToString(","))
            .apply()
        Log.d(TAG, "Saved column order for program $programId: $columnIds")
    }

    /**
     * Save column selection to SharedPreferences
     */
    private fun saveSelectedColumns(selectedIds: Set<String>) {
        val key = "selected_columns_$programId"
        sharedPreferences.edit()
            .putStringSet(key, selectedIds)
            .apply()
        Log.d(TAG, "Saved column selection for program $programId: $selectedIds")
    }

    /**
     * Apply saved order to columns list
     */
    private fun applyColumnOrder(columns: List<TableColumn>): List<TableColumn> {
        val savedOrder = loadColumnOrder() ?: return columns

        val columnMap = columns.associateBy { it.id }
        val orderedColumns = mutableListOf<TableColumn>()

        // Add columns in saved order
        savedOrder.forEach { columnId ->
            columnMap[columnId]?.let { orderedColumns.add(it) }
        }

        // Add any new columns that weren't in saved order (at the end)
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

            // Fill fixed column values
            cells["enrollmentDate"] = dateFormatter.format(enrollment.enrollmentDate)
            cells["orgUnit"] = enrollment.organisationUnit.name
            cells["status"] = enrollment.state.name
            cells["syncStatus"] = when (enrollment.syncStatus) {
                com.ash.simpledataentry.domain.model.SyncStatus.SYNCED -> "Synced"
                com.ash.simpledataentry.domain.model.SyncStatus.NOT_SYNCED -> "Not Synced"
                com.ash.simpledataentry.domain.model.SyncStatus.ALL -> "All"
            }

            // Fill attribute values
            enrollment.attributes.forEach { attr ->
                cells[attr.id] = attr.value ?: ""
            }

            EnrollmentTableRow(enrollment, cells)
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
        var rows = buildTableRows(currentState.enrollments, currentState.columns)

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

    fun syncEnrollments() {
        if (programId.isEmpty()) {
            Log.e(TAG, "Cannot sync: programId is empty")
            return
        }

        Log.d(TAG, "Starting sync for tracker program: $programId")

        viewModelScope.launch {
            _state.value = _state.value.copy(isSyncing = true, error = null)

            try {
                // Sync tracker enrollments and data
                val result = datasetInstancesRepository.syncProgramInstances(
                    programId = programId,
                    programType = com.ash.simpledataentry.domain.model.ProgramType.TRACKER
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
                        Log.e(TAG, "Error during tracker sync", exception)
                        _state.value = _state.value.copy(
                            isSyncing = false,
                            error = "Sync failed: ${exception.message}"
                        )
                    }
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Error during tracker sync", exception)
                _state.value = _state.value.copy(
                    isSyncing = false,
                    error = "Sync failed: ${exception.message}",
                    detailedSyncProgress = null
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

    /**
     * Show column selection dialog
     */
    fun showColumnDialog() {
        _state.value = _state.value.copy(showColumnDialog = true)
    }

    /**
     * Hide column selection dialog
     */
    fun hideColumnDialog() {
        _state.value = _state.value.copy(showColumnDialog = false)
    }

    /**
     * Toggle column selection (all columns are now flexible)
     */
    fun toggleColumnSelection(columnId: String) {
        val currentSelection = _state.value.selectedColumnIds.toMutableSet()

        if (columnId in currentSelection) {
            currentSelection.remove(columnId)
        } else {
            currentSelection.add(columnId)
        }

        _state.value = _state.value.copy(selectedColumnIds = currentSelection)
    }

    /**
     * Select all columns
     */
    fun selectAllColumns() {
        val allColumnIds = _state.value.availableColumns.map { it.id }.toSet()
        _state.value = _state.value.copy(selectedColumnIds = allColumnIds)
    }

    /**
     * Deselect all columns
     */
    fun deselectAllColumns() {
        _state.value = _state.value.copy(selectedColumnIds = emptySet())
    }

    /**
     * Move a column up or down in the list
     */
    fun moveColumn(columnId: String, moveUp: Boolean) {
        val currentColumns = _state.value.availableColumns.toMutableList()
        val currentIndex = currentColumns.indexOfFirst { it.id == columnId }

        if (currentIndex == -1) return

        val newIndex = if (moveUp) {
            (currentIndex - 1).coerceAtLeast(0)
        } else {
            (currentIndex + 1).coerceAtMost(currentColumns.size - 1)
        }

        if (currentIndex != newIndex) {
            val column = currentColumns.removeAt(currentIndex)
            currentColumns.add(newIndex, column)

            _state.value = _state.value.copy(availableColumns = currentColumns)
        }
    }

    /**
     * Apply column selection and persist preferences
     */
    fun applyColumnSelection() {
        val selectedIds = _state.value.selectedColumnIds
        val availableColumns = _state.value.availableColumns

        // Save selection to preferences
        saveSelectedColumns(selectedIds)

        // Save column order to preferences
        saveColumnOrder(availableColumns.map { it.id })

        // Filter displayed columns (maintaining order)
        val displayedColumns = availableColumns.filter { it.id in selectedIds }

        // Rebuild table with new columns
        val tableRows = buildTableRows(_state.value.enrollments, displayedColumns)

        _state.value = _state.value.copy(
            columns = displayedColumns,
            tableRows = tableRows,
            showColumnDialog = false
        )

        // Reapply search and sort
        applySearchAndSort()

        Log.d(TAG, "Applied column selection: ${selectedIds.size} columns selected")
    }

    companion object {
        private const val TAG = "TrackerEnrollTableVM"
    }
}
