package com.example.simplede.domain.repository

import com.example.simplede.domain.model.DataValue
import com.example.simplede.domain.model.ValidationResult
import com.example.simplede.domain.model.ValueHistory
import kotlinx.coroutines.flow.Flow

interface DataEntryRepository {
    suspend fun getDataValues(
        instanceId: String,
        section: String? = null
    ): Flow<List<DataValue>>

    suspend fun saveDataValue(
        instanceId: String,
        dataElement: String,
        categoryOptionCombo: String,
        value: String?,
        comment: String? = null
    ): Result<DataValue>

//    suspend fun getValueHistory(
//        instanceId: String,
//        dataElement: String,
//        categoryOptionCombo: String
//    ): Flow<List<ValueHistory>>

    suspend fun validateValue(
        instanceId: String,
        dataElement: String,
        categoryOptionCombo: String,
        value: String
    ): ValidationResult

//    suspend fun getCompletionStatus(
//        instanceId: String
//    ): Flow<CompletionStatus>
//
//    suspend fun syncDataValues(
//        instanceId: String
//    ): Result<Unit>
}

data class CompletionStatus(
    val totalFields: Int,
    val completedFields: Int,
    val mandatoryFields: Int,
    val completedMandatoryFields: Int,
    val isComplete: Boolean
)
