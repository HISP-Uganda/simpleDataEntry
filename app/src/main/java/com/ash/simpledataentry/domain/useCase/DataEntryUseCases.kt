package com.ash.simpledataentry.domain.useCase

import com.ash.simpledataentry.domain.model.DataValue
import com.ash.simpledataentry.domain.model.ValidationResult
import com.ash.simpledataentry.domain.repository.DataEntryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetDataValuesUseCase @Inject constructor(
    private val repository: DataEntryRepository
) {
    suspend operator fun invoke(
        datasetId: String,
        period: String,
        orgUnit: String,
        attributeOptionCombo: String
    ): Flow<List<DataValue>> {
        return repository.getDataValues(datasetId, period, orgUnit, attributeOptionCombo)
    }
}



class SaveDataValueUseCase @Inject constructor(
    private val repository: DataEntryRepository
) {
    suspend operator fun invoke(
        datasetId: String,
        period: String,
        orgUnit: String,
        attributeOptionCombo: String,
        dataElement: String,
        categoryOptionCombo: String,
        value: String?,
        comment: String?
    ): Result<DataValue> {
        return repository.saveDataValue(
            datasetId, period, orgUnit, attributeOptionCombo, dataElement, categoryOptionCombo, value, comment
        )
    }
}

class ValidateValueUseCase @Inject constructor(
    private val repository: DataEntryRepository
) {
    suspend operator fun invoke(
        datasetId: String,
        dataElement: String,
        value: String
    ): ValidationResult {
        return repository.validateValue(datasetId, dataElement, value)
    }
}

data class DataEntryUseCases @Inject constructor(
    val getDataValues: GetDataValuesUseCase,
    val saveDataValue: SaveDataValueUseCase,
    val validateValue: ValidateValueUseCase,
    val completeDatasetInstance: CompleteDatasetInstanceUseCase,
    val markDatasetInstanceIncomplete: MarkDatasetInstanceIncompleteUseCase
)