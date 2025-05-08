package com.ash.simpledataentry.domain.useCase

import com.ash.simpledataentry.domain.model.Dataset
import com.ash.simpledataentry.domain.repository.DatasetsRepository
import kotlinx.coroutines.flow.Flow

class GetDatasetsUseCase(private val repository: DatasetsRepository) {
    suspend operator fun invoke(): Flow<List<Dataset>> {
        return repository.getDatasets()
}

}

class FilterDatasetsUseCase(private val repository: DatasetsRepository) {
    suspend operator fun invoke(period: String?, syncStatus: Boolean?): Result<List<Dataset>> {
        return repository.filterDatasets(period, syncStatus)
    }
}

class SyncDatasetsUseCase(private val repository: DatasetsRepository) {
    suspend operator fun invoke(): Result<Unit> {
        return repository.syncDatasets()
    }
}