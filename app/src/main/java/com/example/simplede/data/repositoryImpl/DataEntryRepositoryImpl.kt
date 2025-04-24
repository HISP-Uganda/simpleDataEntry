package com.example.simplede.data.repositoryImpl


import com.example.simplede.domain.model.DataValue
import com.example.simplede.domain.model.ValidationResult
import com.example.simplede.domain.model.ValidationState
import com.example.simplede.domain.repository.DataEntryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class DataEntryRepositoryImpl : DataEntryRepository {
    private val dummyDataValues = mutableMapOf<String, List<DataValue>>()

    init {
        // Initialize with dummy data for testing
        val sampleDataValues = listOf(
            DataValue(
                dataElement = "DE_NAME",
                categoryOptionCombo = "COC_1",
                value = null,
                comment = null,
                storedBy = "System",
                validationState = ValidationState.VALID
            ),
            DataValue(
                dataElement = "DE_AGE",
                categoryOptionCombo = "COC_1",
                value = null,
                comment = null,
                storedBy = "System",
                validationState = ValidationState.VALID
            ),
            DataValue(
                dataElement = "DE_GENDER",
                categoryOptionCombo = "COC_1",
                value = null,
                comment = null,
                storedBy = "System",
                validationState = ValidationState.VALID
            ),
            DataValue(
                dataElement = "DE_DOB",
                categoryOptionCombo = "COC_1",
                value = null,
                comment = null,
                storedBy = "System",
                validationState = ValidationState.VALID
            ),
            DataValue(
                dataElement = "DE_SYMPTOMS",
                categoryOptionCombo = "COC_1",
                value = null,
                comment = null,
                storedBy = "System",
                validationState = ValidationState.VALID
            )
        )

        // Store dummy data for each instance
        dummyDataValues["test_instance_1"] = sampleDataValues
    }

    override suspend fun getDataValues(instanceId: String, section: String?): Flow<List<DataValue>> = flow {
        // Create new list for new instances or return existing data
        val dataValues = dummyDataValues[instanceId] ?: createNewDataValuesList()
        dummyDataValues[instanceId] = dataValues
        emit(dataValues)
    }

    override suspend fun saveDataValue(
        instanceId: String,
        dataElement: String,
        categoryOptionCombo: String,
        value: String?,
        comment: String?
    ): Result<DataValue> = runCatching {
        val currentList = dummyDataValues[instanceId] ?: createNewDataValuesList()
        val updatedList = currentList.map { dataValue ->
            if (dataValue.dataElement == dataElement && dataValue.categoryOptionCombo == categoryOptionCombo) {
                dataValue.copy(
                    value = value,
                    comment = comment,
                    storedBy = "current_user", // This should come from auth/session
                    validationState = ValidationState.VALID // Should be properly validated
                )
            } else {
                dataValue
            }
        }
        dummyDataValues[instanceId] = updatedList
        updatedList.first { it.dataElement == dataElement && it.categoryOptionCombo == categoryOptionCombo }
    }

    override suspend fun validateValue(
        instanceId: String,
        dataElement: String,
        categoryOptionCombo: String,
        value: String
    ): ValidationResult {
        // For demo purposes, just do basic validation
        return when {
            value.isBlank() -> ValidationResult(false, ValidationState.ERROR, "This field cannot be empty")
            dataElement == "DE_AGE" && value.toIntOrNull() == null ->
                ValidationResult(false, ValidationState.ERROR, "Age must be a number")
            dataElement == "DE_GENDER" && !listOf("Male", "Female", "Other").contains(value) ->
                ValidationResult(false, ValidationState.ERROR, "Invalid gender value")
            else -> ValidationResult(true, ValidationState.VALID, null)
        }
    }

    private fun createNewDataValuesList(): List<DataValue> {
        return dummyDataValues["test_instance_1"]?.map {
            it.copy(
                value = null,
                comment = null,
                validationState = ValidationState.VALID
            )
        } ?: emptyList()
    }
}