package com.example.simplede.presentation.features.datasetInstances

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.simplede.data.SessionManager
import com.example.simplede.data.repositoryImpl.DatasetInstancesRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "DatasetInstancesVM"

class DatasetInstancesViewModelFactory(private val datasetId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DatasetInstancesViewModel::class.java)) {
            Log.d(TAG, "Creating ViewModel for dataset: $datasetId")
            val d2 = SessionManager.getD2()
            val repository = DatasetInstancesRepositoryImpl(d2!!)
            @Suppress("UNCHECKED_CAST")
            return DatasetInstancesViewModel(repository, datasetId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class DatasetInstancesViewModel(
    private val repository: DatasetInstancesRepositoryImpl,
    private val datasetId: String
) : ViewModel() {

    private val _state = MutableStateFlow(DatasetInstancesState())
    val state: StateFlow<DatasetInstancesState> = _state.asStateFlow()

    init {
        Log.d(TAG, "Initializing ViewModel with dataset: $datasetId")
        loadData()
    }

    private fun loadData() {
        Log.d(TAG, "Loading dataset data")


        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                // First get metadata
                Log.d(TAG, "Fetching dataset metadata")
                val metadata = repository.getDatasetMetadata(datasetId)
                Log.d(TAG, "Metadata fetched successfully")
                
                // Then get instances
                Log.d(TAG, "Fetching dataset instances")
                val instances = repository.getDatasetInstances(datasetId)
                Log.d(TAG, "Received ${instances.size} instances")
                
                _state.value = _state.value.copy(
                    metadata = metadata,
                    instances = instances,
                    isLoading = false,
                    error = null
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading data", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load dataset data"
                )
            }
        }
    }

    fun syncDatasetInstances() {
        Log.d(TAG, "Starting sync")
        viewModelScope.launch {
            _state.value = _state.value.copy(isSyncing = true)
            try {
                repository.syncDatasetInstances()
                loadData() // Reload all data after sync
                _state.value = _state.value.copy(
                    isSyncing = false,
                    successMessage = "Dataset instances synced successfully"
                )
                Log.d(TAG, "Sync completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)
                _state.value = _state.value.copy(
                    isSyncing = false,
                    error = e.message ?: "Failed to sync dataset instances"
                )
            }
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(
            error = null,
            successMessage = null
        )
    }
}