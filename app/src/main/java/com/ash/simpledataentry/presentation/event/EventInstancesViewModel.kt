package com.ash.simpledataentry.presentation.event

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

data class EventInstancesState(
    val events: List<ProgramInstance.EventInstance> = emptyList(),
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val program: com.ash.simpledataentry.domain.model.Program? = null,
    val detailedSyncProgress: DetailedSyncProgress? = null
)

@HiltViewModel
class EventInstancesViewModel @Inject constructor(
    private val datasetInstancesRepository: DatasetInstancesRepository,
    private val sessionManager: SessionManager,
    private val app: Application
) : ViewModel() {

    private var programId: String = ""

    private val _state = MutableStateFlow(EventInstancesState())
    val state: StateFlow<EventInstancesState> = _state.asStateFlow()

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
            _state.value = _state.value.copy(isLoading = true, error = null)

            try {
                // Load event instances directly
                datasetInstancesRepository.getEventInstances(programId)
                    .catch { exception ->
                        Log.e(TAG, "Error loading event instances", exception)
                        _state.value = _state.value.copy(
                            isLoading = false,
                            error = "Failed to load events: ${exception.message}"
                        )
                    }
                    .collect { instances ->
                        val events = instances.filterIsInstance<ProgramInstance.EventInstance>()
                        Log.d(TAG, "Loaded ${events.size} event instances")

                        _state.value = _state.value.copy(
                            events = events,
                            isLoading = false,
                            error = null
                        )
                    }
            } catch (exception: Exception) {
                Log.e(TAG, "Error loading event instances", exception)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to load events: ${exception.message}"
                )
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
            _state.value = _state.value.copy(isSyncing = true, error = null)

            try {
                // Sync event instances and data
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
                    error = "Sync failed: ${exception.message}",
                    detailedSyncProgress = null
                )
            }
        }
    }

    companion object {
        private const val TAG = "EventInstancesVM"
    }
}
