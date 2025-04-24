package com.example.simplede.domain.useCases.datasetInstances


import com.example.simplede.domain.model.DatasetInstance
import com.example.simplede.domain.repository.DatasetInstancesRepository

class FilterDatasetInstancesUseCase(
    private val repository: DatasetInstancesRepository
) {
    suspend operator fun invoke(datasetId: String, period: String? = null, state: String? = null): Result<List<DatasetInstance>> {
        return try {
            val instances = repository.getDatasetInstances(datasetId)
            val filteredInstances = instances.filter { instance ->
                val matchesPeriod = period == null || instance.period.id == period
                val matchesState = state == null || instance.state.name == state
                matchesPeriod && matchesState
            }
            Result.success(filteredInstances)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}