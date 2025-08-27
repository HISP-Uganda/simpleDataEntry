package com.ash.simpledataentry.domain.repository

import com.ash.simpledataentry.domain.model.DatasetInstance

interface DatasetInstancesRepository {
    suspend fun getDatasetInstances(datasetId: String): List<DatasetInstance>
    suspend fun getDatasetInstanceCount(datasetId: String): Int
    suspend fun syncDatasetInstances()
    suspend fun completeDatasetInstance(datasetId: String, period: String, orgUnit: String, attributeOptionCombo: String): Result<Unit>
    suspend fun markDatasetInstanceIncomplete(datasetId: String, period: String, orgUnit: String, attributeOptionCombo: String): Result<Unit>
}