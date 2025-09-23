package com.ash.simpledataentry.domain.repository

import com.ash.simpledataentry.domain.model.Dataset
import com.ash.simpledataentry.domain.model.Program
import com.ash.simpledataentry.domain.model.ProgramItem
import com.ash.simpledataentry.domain.model.ProgramType
import kotlinx.coroutines.flow.Flow

interface DatasetsRepository {

    // Existing dataset methods
    fun getDatasets(): Flow<List<Dataset>>
    suspend fun syncDatasets(): Result<Unit>
    suspend fun filterDatasets(period: String?, syncStatus: Boolean?): Result<List<Dataset>>

    // New program methods for unified interface
    fun getTrackerPrograms(): Flow<List<Program>>
    fun getEventPrograms(): Flow<List<Program>>
    fun getAllPrograms(): Flow<List<ProgramItem>>
    suspend fun syncPrograms(): Result<Unit>
    suspend fun filterPrograms(programType: ProgramType?, searchQuery: String?): Result<List<ProgramItem>>

}