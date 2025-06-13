package com.ash.simpledataentry.domain.repository

import com.ash.simpledataentry.domain.model.DataValue
import com.ash.simpledataentry.domain.model.OrganisationUnit
import com.ash.simpledataentry.domain.model.Period
import com.ash.simpledataentry.domain.model.ValidationResult
import kotlinx.coroutines.flow.Flow

interface DataEntryRepository {
    suspend fun getDataValues(
        datasetId: String,
        period: String,
        orgUnit: String,
        attributeOptionCombo: String
    ): Flow<List<DataValue>>

    suspend fun saveDataValue(
        datasetId: String,
        period: String,
        orgUnit: String,
        attributeOptionCombo: String,
        dataElement: String,
        categoryOptionCombo: String,
        value: String?,
        comment: String?
    ): Result<DataValue>

    suspend fun validateValue(
        datasetId: String,
        dataElement: String,
        value: String
    ): ValidationResult

    suspend fun getAvailablePeriods(datasetId: String): List<Period>
    suspend fun getUserOrgUnit(datasetId: String): OrganisationUnit
    suspend fun getDefaultAttributeOptionCombo(): String
    suspend fun getAttributeOptionCombos(datasetId: String): List<Pair<String, String>>
    suspend fun getCategoryComboStructure(categoryComboUid: String): List<Pair<String, List<Pair<String, String>>>>
    suspend fun getCategoryOptionCombos(categoryComboUid: String): List<Pair<String, List<String>>>

    // Push all local drafts/unsynced data to the server
    suspend fun pushAllLocalData()
    suspend fun syncCurrentEntryForm()
}

