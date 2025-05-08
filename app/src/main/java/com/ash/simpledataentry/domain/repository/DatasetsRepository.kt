package com.ash.simpledataentry.domain.repository

import com.ash.simpledataentry.domain.model.Dataset
import kotlinx.coroutines.flow.Flow

interface DatasetsRepository {

    fun getDatasets(): Flow<List<Dataset>>
    suspend fun syncDatasets(): Result<Unit>
    suspend fun filterDatasets(period: String?, syncStatus: Boolean?): Result<List<Dataset>>

}