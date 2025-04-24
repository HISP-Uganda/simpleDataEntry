package com.example.simplede.domain.useCases.datasets

import com.example.simplede.domain.model.Dataset
import com.example.simplede.domain.repository.DatasetRepository

class SyncDatasetsUseCase(private val repository: DatasetRepository) {
    suspend operator fun invoke(): Result<Unit> {
        return repository.syncDatasets()
    }
}