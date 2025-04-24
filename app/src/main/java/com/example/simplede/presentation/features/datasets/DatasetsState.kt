package com.example.simplede.presentation.features.datasets

import com.example.simplede.domain.model.Dataset

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