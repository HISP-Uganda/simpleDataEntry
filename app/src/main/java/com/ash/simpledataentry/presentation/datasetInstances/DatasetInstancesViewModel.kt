package com.ash.simpledataentry.presentation.datasetInstances

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.simpledataentry.domain.model.DatasetInstance
import com.ash.simpledataentry.domain.useCase.GetDatasetInstancesUseCase
import com.ash.simpledataentry.domain.useCase.SyncDatasetInstancesUseCase
import com.ash.simpledataentry.domain.repository.DataEntryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DatasetInstancesState(
    val instances: List<DatasetInstance> = emptyList(),
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val attributeOptionCombos: List<Pair<String, String>> = emptyList(),
    val showSplash: Boolean = false
)

@HiltViewModel
class DatasetInstancesViewModel @Inject constructor(
    private val getDatasetInstancesUseCase: GetDatasetInstancesUseCase,
    private val syncDatasetInstancesUseCase: SyncDatasetInstancesUseCase,
    private val dataEntryRepository: DataEntryRepository
) : ViewModel() {
    private var datasetId: String = ""

    private val _state = MutableStateFlow(DatasetInstancesState())
    val state: StateFlow<DatasetInstancesState> = _state.asStateFlow()

    fun setDatasetId(id: String) {
        if (id.isNotEmpty() && id != datasetId) {
            datasetId = id
            Log.d("DatasetInstancesVM", "Initializing ViewModel with dataset: $datasetId")
            loadData()
        }
    }

    private fun loadData() {
        if (datasetId.isEmpty()) {
            Log.e("DatasetInstancesVM", "Cannot load data: datasetId is empty")
            return
        }

        Log.d("DatasetInstancesVM", "Loading dataset data for ID: $datasetId")
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                Log.d("DatasetInstancesVM", "Fetching dataset instances")
                val attributeOptionCombos = dataEntryRepository.getAttributeOptionCombos(datasetId)
                val instancesResult = getDatasetInstancesUseCase(datasetId)
                instancesResult.fold(
                    onSuccess = { instances ->
                        Log.d("DatasetInstancesVM", "Received "+instances.size+" instances")
                        _state.value = _state.value.copy(
                            instances = instances,
                            isLoading = false,
                            error = null,
                            attributeOptionCombos = attributeOptionCombos
                        )
                    },
                    onFailure = { error ->
                        Log.e("DatasetInstancesVM", "Error loading data", error)
                        _state.value = _state.value.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load dataset data",
                            attributeOptionCombos = attributeOptionCombos
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e("DatasetInstancesVM", "Error loading data", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load dataset data"
                )
            }
        }
    }

    fun syncDatasetInstances() {
        if (datasetId.isEmpty()) {
            Log.e("DatasetInstancesVM", "Cannot sync: datasetId is empty")
            return
        }

        Log.d("DatasetInstancesVM", "Starting sync for dataset: $datasetId")
        viewModelScope.launch {
            _state.value = _state.value.copy(isSyncing = true, error = null)
            try {
                val result = syncDatasetInstancesUseCase()
                result.fold(
                    onSuccess = {
                        loadData() // Reload all data after sync
                        _state.value = _state.value.copy(
                            isSyncing = false,
                            successMessage = "Dataset instances synced successfully"
                        )
                        Log.d("DatasetInstancesVM", "Sync completed successfully")
                    },
                    onFailure = { error ->
                        Log.e("DatasetInstancesVM", "Sync failed", error)
                        _state.value = _state.value.copy(
                            isSyncing = false,
                            error = error.message ?: "Failed to sync dataset instances"
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e("DatasetInstancesVM", "Sync failed", e)
                _state.value = _state.value.copy(
                    isSyncing = false,
                    error = e.message ?: "Failed to sync dataset instances"
                )
            }
        }
    }

    fun manualRefresh() {
        loadData()
    }

    fun setShowSplash(show: Boolean) {
        _state.value = _state.value.copy(showSplash = show)
    }
}