package com.example.simplede.domain.useCases.datasetInstances


import com.example.simplede.domain.repository.DatasetInstancesRepository

class SyncDatasetInstancesUseCase(
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