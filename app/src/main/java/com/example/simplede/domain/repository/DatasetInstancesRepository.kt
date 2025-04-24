package com.example.simplede.domain.repository

import com.example.simplede.domain.model.DatasetInstance
import com.example.simplede.domain.model.DatasetMetadata

interface DatasetInstancesRepository {
    suspend fun getDatasetInstances(datasetId: String): List<DatasetInstance>
    suspend fun getDatasetMetadata(datasetId: String): DatasetMetadata
    suspend fun syncDatasetInstances()
}