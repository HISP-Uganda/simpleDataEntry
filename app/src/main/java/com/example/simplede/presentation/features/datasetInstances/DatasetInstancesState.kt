package com.example.simplede.presentation.features.datasetInstances

import com.example.simplede.domain.model.DatasetInstance
import com.example.simplede.domain.model.DatasetMetadata

data class DatasetInstancesState(
    val instances: List<DatasetInstance> = emptyList(),
    val metadata: DatasetMetadata? = null,
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)