package com.example.simplede.presentation.features.datasets


import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.simplede.data.SessionManager
import com.example.simplede.data.repositoryImpl.DatasetsRepositoryImpl
import com.example.simplede.domain.useCases.datasets.FilterDatasetsUseCase
import com.example.simplede.domain.useCases.datasets.GetDatasetsUseCase
import com.example.simplede.domain.useCases.datasets.SyncDatasetsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DatasetsViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DatasetsViewModel::class.java)) {
            val repository = DatasetsRepositoryImpl()
            @Suppress("UNCHECKED_CAST")
            return DatasetsViewModel(
                getDatasetsUseCase = GetDatasetsUseCase(repository),
                syncDatasetsUseCase = SyncDatasetsUseCase(repository),
                filterDatasetsUseCase = FilterDatasetsUseCase(repository)
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}


class DatasetsViewModel(
    private val getDatasetsUseCase: GetDatasetsUseCase,
    private val syncDatasetsUseCase: SyncDatasetsUseCase,
    private val filterDatasetsUseCase: FilterDatasetsUseCase
) : ViewModel() {

    private val _datasetsState = MutableStateFlow<DatasetsState>(DatasetsState.Loading)
    val datasetsState: StateFlow<DatasetsState> = _datasetsState.asStateFlow()

    init {
        loadDatasets()
    }

    fun loadDatasets() {
        viewModelScope.launch {
            _datasetsState.value = DatasetsState.Loading

            var d2 = SessionManager.getD2()

            d2?.metadataModule()?.blockingDownload()

            try {
                val result = getDatasetsUseCase()
                result.fold(
                    onSuccess = { datasets ->
                        _datasetsState.value = DatasetsState.Success(
                            datasets = datasets,
                            filteredDatasets = datasets
                        )
                    },
                    onFailure = { exception ->
                        _datasetsState.value = DatasetsState.Error(
                            message = exception.message ?: "Failed to load datasets"
                        )
                    }
                )
            } catch (e: Exception) {
                _datasetsState.value = DatasetsState.Error(
                    message = "Failed to load datasets: ${e.message}"
                )
            }
        }
    }

    fun syncDatasets() {
        viewModelScope.launch {
            val currentState = _datasetsState.value
            if (currentState is DatasetsState.Success) {
                _datasetsState.value = currentState.copy(isSyncing = true)

                try {
                    val result = syncDatasetsUseCase()
                    result.fold(
                        onSuccess = {
                            // Reload datasets after successful sync
                            loadDatasets()
                        },
                        onFailure = { exception ->
                            _datasetsState.value = DatasetsState.Error(
                                message = exception.message ?: "Failed to sync datasets"
                            )
                        }
                    )
                } catch (e: Exception) {
                    _datasetsState.value = DatasetsState.Error(
                        message = "Failed to sync datasets: ${e.message}"
                    )
                }
            }
        }
    }

    fun filterDatasets(period: String? = null, syncStatus: Boolean? = null) {
        viewModelScope.launch {
            val currentState = _datasetsState.value
            if (currentState is DatasetsState.Success) {
                try {
                    val result = filterDatasetsUseCase(period, syncStatus)
                    result.fold(
                        onSuccess = { filteredDatasets ->
                            _datasetsState.value = currentState.copy(
                                filteredDatasets = filteredDatasets,
                                selectedPeriod = period,
                                syncStatus = syncStatus
                            )
                        },
                        onFailure = { exception ->
                            _datasetsState.value = DatasetsState.Error(
                                message = exception.message ?: "Failed to filter datasets"
                            )
                        }
                    )
                } catch (e: Exception) {
                    _datasetsState.value = DatasetsState.Error(
                        message = "Failed to filter datasets: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearFilters() {
        val currentState = _datasetsState.value
        if (currentState is DatasetsState.Success) {
            _datasetsState.value = currentState.copy(
                filteredDatasets = currentState.datasets,
                selectedPeriod = null,
                syncStatus = null
            )
        }
    }
}