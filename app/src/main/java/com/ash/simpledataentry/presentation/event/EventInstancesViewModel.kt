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
import com.ash.simpledataentry.presentation.core.BackgroundOperation
import com.ash.simpledataentry.util.toUiError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

// Pure data model (no UI state like isLoading)
data class EventInstancesData(
    val events: List<ProgramInstance.EventInstance> = emptyList(),
    val program: com.ash.simpledataentry.domain.model.Program? = null,
    val syncMessage: String? = null
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

                        val data = EventInstancesData(events = events)
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
                // Use backgroundOperation for non-blocking sync
                val currentData = getCurrentData()
                _uiState.value = UiState.Success(currentData, BackgroundOperation.Syncing)

                // Sync event instances and data
                val result = datasetInstancesRepository.syncProgramInstances(
                    programId = programId,
                    programType = com.ash.simpledataentry.domain.model.ProgramType.EVENT
                )

                if (result.isSuccess) {
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

    companion object {
        private const val TAG = "EventInstancesVM"
    }
}
