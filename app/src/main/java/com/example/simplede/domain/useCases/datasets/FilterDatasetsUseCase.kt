package com.example.simplede.domain.useCases.datasets

import com.example.simplede.domain.model.Dataset
import com.example.simplede.domain.repository.DatasetRepository

class FilterDatasetsUseCase(private val repository: DatasetRepository) {
    suspend operator fun invoke(period: String?, syncStatus: Boolean?): Result<List<Dataset>> {
        return repository.filterDatasets(period, syncStatus)
    }
}