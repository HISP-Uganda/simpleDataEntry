package com.ash.simpledataentry.domain.repository

import com.ash.simpledataentry.domain.model.DatasetInstance

interface DatasetInstancesRepository {
    suspend fun getDatasetInstances(datasetId: String): List<DatasetInstance>
    suspend fun syncDatasetInstances()
}