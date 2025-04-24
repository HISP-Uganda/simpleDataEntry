package com.example.simplede.domain.useCases.dataEntry

import com.example.simplede.domain.model.DataValue
import com.example.simplede.domain.model.ValidationResult
import com.example.simplede.domain.repository.DataEntryRepository
import kotlinx.coroutines.flow.Flow

class DataEntryUseCases(
    private val repository: DataEntryRepository
) {
    suspend fun getDataValues(instanceId: String): Flow<List<DataValue>> {
        return repository.getDataValues(instanceId)
    }

    suspend fun saveDataValue(
        instanceId: String,
        dataElement: String,
        categoryOptionCombo: String,
        value: String?,
        comment: String? = null
    ): Result<DataValue> {
        return repository.saveDataValue(
            instanceId = instanceId,
            dataElement = dataElement,
            categoryOptionCombo = categoryOptionCombo,
            value = value,
            comment = comment
        )
    }

    suspend fun validateValue(
        instanceId: String,
        dataElement: String,
        categoryOptionCombo: String,
        value: String
    ): ValidationResult {
        return repository.validateValue(
            instanceId = instanceId,
            dataElement = dataElement,
            categoryOptionCombo = categoryOptionCombo,
            value = value
        )
    }
}