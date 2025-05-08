package com.ash.simpledataentry.presentation.datasets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.simpledataentry.domain.model.Dataset
import com.ash.simpledataentry.domain.useCase.FilterDatasetsUseCase
import com.ash.simpledataentry.domain.useCase.GetDatasetsUseCase
import com.ash.simpledataentry.domain.useCase.LogoutUseCase
import com.ash.simpledataentry.domain.useCase.SyncDatasetsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DatasetsState {
    data object Loading : DatasetsState()
    data class Error(val message: String) : DatasetsState()
    data class Success(
        val datasets: List<Dataset>,
        val filteredDatasets: List<Dataset> = datasets,
        val selectedPeriod: String? = null,
        val syncStatus: Boolean? = null,
        val isSyncing: Boolean = false
    ) : DatasetsState()
}
@HiltViewModel
class DatasetsViewModel @Inject constructor(
    private val getDatasetsUseCase: GetDatasetsUseCase,
    private val syncDatasetsUseCase: SyncDatasetsUseCase,
    private val filterDatasetsUseCase: FilterDatasetsUseCase,
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<DatasetsState>(DatasetsState.Loading)
    val uiState: StateFlow<DatasetsState> = _uiState.asStateFlow()

    init {
        loadDatasets()
    }

    fun loadDatasets() {
        viewModelScope.launch {
            _uiState.value = DatasetsState.Loading
            getDatasetsUseCase()
                .catch { exception ->
                    _uiState.value = DatasetsState.Error(
                        message = exception.message ?: "Failed to load datasets"
                    )
                }
                .collect { datasets ->
                    _uiState.value = DatasetsState.Success(
                        datasets = datasets,
                        filteredDatasets = datasets
                    )
                }
        }
    }

    fun syncDatasets() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is DatasetsState.Success) {
                _uiState.value = currentState.copy(isSyncing = true)
                try {
                    val result = syncDatasetsUseCase()
                    result.fold(
                        onSuccess = {
                            // Reload datasets after successful sync
                            loadDatasets()
                        },
                        onFailure = { exception ->
                            _uiState.value = DatasetsState.Error(
                                message = exception.message ?: "Failed to sync datasets"
                            )
                        }
                    )
                } catch (e: Exception) {
                    _uiState.value = DatasetsState.Error(
                        message = "Failed to sync datasets: ${e.message}"
                    )
                }
            }
        }
    }

    fun filterDatasets(period: String? = null, syncStatus: Boolean? = null) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is DatasetsState.Success) {
                try {
                    val result = filterDatasetsUseCase(period, syncStatus)
                    result.fold(
                        onSuccess = { filteredDatasets ->
                            _uiState.value = currentState.copy(
                                filteredDatasets = filteredDatasets,
                                selectedPeriod = period,
                                syncStatus = syncStatus
                            )
                        },
                        onFailure = { exception ->
                            _uiState.value = DatasetsState.Error(
                                message = exception.message ?: "Failed to filter datasets"
                            )
                        }
                    )
                } catch (e: Exception) {
                    _uiState.value = DatasetsState.Error(
                        message = "Failed to filter datasets: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearFilters() {
        val currentState = _uiState.value
        if (currentState is DatasetsState.Success) {
            _uiState.value = currentState.copy(
                filteredDatasets = currentState.datasets,
                selectedPeriod = null,
                syncStatus = null
            )
        }
    }


    fun logout() {
        viewModelScope.launch {
            try {
                logoutUseCase()

            } catch (e: Exception) {
                _uiState.value = DatasetsState.Error(
                    message = "Failed to logout: ${e.message}"
                )
            }
        }

    }


}