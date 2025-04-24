package com.example.simplede.domain.repository

import com.example.simplede.domain.model.Dataset

interface DatasetRepository {
    suspend fun getDatasets(): Result<List<Dataset>>
    suspend fun syncDatasets(): Result<Unit>
    suspend fun filterDatasets(period: String?, syncStatus: Boolean?): Result<List<Dataset>>
}