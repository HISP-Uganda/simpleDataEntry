package com.ash.simpledataentry.presentation.tracker

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.simpledataentry.domain.model.ProgramInstance
import com.ash.simpledataentry.domain.repository.DatasetInstancesRepository
import com.ash.simpledataentry.data.sync.DetailedSyncProgress
import com.ash.simpledataentry.data.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrackerEnrollmentsState(
    val enrollments: List<ProgramInstance.TrackerEnrollment> = emptyList(),
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val program: com.ash.simpledataentry.domain.model.Program? = null,
    val detailedSyncProgress: DetailedSyncProgress? = null
)

@HiltViewModel
class TrackerEnrollmentsViewModel @Inject constructor(
    private val datasetInstancesRepository: DatasetInstancesRepository,
    private val sessionManager: SessionManager,
    private val app: Application
) : ViewModel() {

    private var programId: String = ""

    private val _state = MutableStateFlow(TrackerEnrollmentsState())
    val state: StateFlow<TrackerEnrollmentsState> = _state.asStateFlow()

    fun initialize(id: String) {
        if (id.isNotEmpty() && id != programId) {
            programId = id
            Log.d(TAG, "Initializing TrackerEnrollmentsViewModel with program: $programId")
            loadData()
        }
    }

    fun refreshData() {
        Log.d(TAG, "Refreshing tracker enrollments data")
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
                // Load enrollments directly
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

                        _state.value = _state.value.copy(
                            enrollments = enrollments,
                            isLoading = false,
                            error = null
                        )
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

    companion object {
        private const val TAG = "TrackerEnrollmentsVM"
    }
}
