package com.ash.simpledataentry.domain.useCase

import com.ash.simpledataentry.domain.model.DatasetInstance
import com.ash.simpledataentry.domain.repository.DatasetInstancesRepository
import javax.inject.Inject

class GetDatasetInstancesUseCase @Inject constructor(
    private val repository: DatasetInstancesRepository
) {
    suspend operator fun invoke(datasetId: String): Result<List<DatasetInstance>> {
        return try {
            val instances = repository.getDatasetInstances(datasetId)
            Result.success(instances)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// Use case for syncing dataset instances
class SyncDatasetInstancesUseCase @Inject constructor(
    private val repository: DatasetInstancesRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return try {
            repository.syncDatasetInstances()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}