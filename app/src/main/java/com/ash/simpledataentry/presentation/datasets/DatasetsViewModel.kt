package com.ash.simpledataentry.presentation.datasets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.simpledataentry.domain.model.Dataset
import com.ash.simpledataentry.domain.model.FilterState
import com.ash.simpledataentry.domain.model.PeriodFilterType
import com.ash.simpledataentry.domain.model.SyncStatus
import com.ash.simpledataentry.domain.useCase.FilterDatasetsUseCase
import com.ash.simpledataentry.domain.useCase.GetDatasetsUseCase
import com.ash.simpledataentry.domain.useCase.LogoutUseCase
import com.ash.simpledataentry.domain.useCase.SyncDatasetsUseCase
import com.ash.simpledataentry.util.PeriodHelper
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
        val currentFilter: FilterState = FilterState(),
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

    fun applyFilter(filterState: FilterState) {
        val currentState = _uiState.value
        if (currentState is DatasetsState.Success) {
            val filteredDatasets = filterDatasets(currentState.datasets, filterState)
            _uiState.value = currentState.copy(
                filteredDatasets = filteredDatasets,
                currentFilter = filterState
            )
        }
    }

    private fun filterDatasets(datasets: List<Dataset>, filterState: FilterState): List<Dataset> {
        val periodHelper = PeriodHelper()
        val periodIds = when (filterState.periodType) {
            PeriodFilterType.RELATIVE -> filterState.relativePeriod?.let {
                periodHelper.getPeriodIds(it)
            } ?: emptyList()
            PeriodFilterType.CUSTOM_RANGE -> {
                if (filterState.customFromDate != null && filterState.customToDate != null) {
                    periodHelper.getPeriodIds(filterState.customFromDate, filterState.customToDate)
                } else emptyList()
            }
            else -> emptyList()
        }

        return datasets.filter { dataset ->
            // Search query filter
            val matchesSearch = if (filterState.searchQuery.isBlank()) {
                true
            } else {
                dataset.name.contains(filterState.searchQuery, ignoreCase = true) ||
                dataset.description?.contains(filterState.searchQuery, ignoreCase = true) == true
            }

            // Period filter
            val matchesPeriod = if (filterState.periodType == PeriodFilterType.ALL) {
                true
            } else {
                // This is a simplified check. A real implementation would need to check
                // if the dataset's period type is compatible with the selected periods.
                // For now, we assume all datasets can be filtered by any period.
                true
            }

            // Sync status filter
            val matchesSyncStatus = when (filterState.syncStatus) {
                SyncStatus.ALL -> true
                // In a real scenario, you would check the sync status of the dataset instances
                // related to this dataset. For now, we assume this information is not available
                // at the dataset level and we will not filter by sync status.
                else -> true
            }

            matchesSearch && matchesPeriod && matchesSyncStatus
        }
    }

    fun clearFilters() {
        val currentState = _uiState.value
        if (currentState is DatasetsState.Success) {
            _uiState.value = currentState.copy(
                filteredDatasets = currentState.datasets,
                currentFilter = FilterState()
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
