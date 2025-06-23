package com.ash.simpledataentry.data.repositoryImpl

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.runtime.snapshots.SnapshotApplyResult.Failure
import com.ash.simpledataentry.domain.model.*
import com.ash.simpledataentry.domain.repository.DataEntryRepository
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.ash.simpledataentry.data.SessionManager
import org.hisp.dhis.android.core.D2
import com.ash.simpledataentry.data.local.DataValueDraftDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ash.simpledataentry.data.local.DataElementDao
import com.ash.simpledataentry.data.local.CategoryComboDao
import com.ash.simpledataentry.data.local.CategoryOptionComboDao
import com.ash.simpledataentry.data.local.OrganisationUnitDao
import com.ash.simpledataentry.util.NetworkUtils
import com.ash.simpledataentry.data.local.DataValueEntity
import com.ash.simpledataentry.data.local.DataValueDao
import com.ash.simpledataentry.presentation.datasets.DatasetsState.Success
import javax.inject.Inject
import kotlin.Pair
import kotlin.Result
import org.hisp.dhis.android.core.dataelement.DataElement
import org.hisp.dhis.android.core.common.ValueType
import android.content.Context

@ViewModelScoped
class DataEntryRepositoryImpl @Inject constructor(
    private val sessionManager: SessionManager,
    private val draftDao: DataValueDraftDao,
    private val dataElementDao: DataElementDao,
    private val categoryComboDao: CategoryComboDao,
    private val categoryOptionComboDao: CategoryOptionComboDao,
    private val organisationUnitDao: OrganisationUnitDao,
    private val context: android.content.Context,
    private val dataValueDao: DataValueDao
) : DataEntryRepository {

    private val d2 get() = sessionManager.getD2()!!

    private fun getDataEntryType(dataElement: DataElement): DataEntryType {
        return when (dataElement.valueType()) {
            ValueType.TEXT -> DataEntryType.TEXT
            ValueType.LONG_TEXT -> DataEntryType.TEXT
            ValueType.NUMBER -> DataEntryType.NUMBER
            ValueType.INTEGER -> DataEntryType.INTEGER
            ValueType.INTEGER_POSITIVE -> DataEntryType.POSITIVE_INTEGER
            ValueType.INTEGER_NEGATIVE -> DataEntryType.NEGATIVE_INTEGER
            ValueType.INTEGER_ZERO_OR_POSITIVE -> DataEntryType.POSITIVE_INTEGER
            ValueType.PERCENTAGE -> DataEntryType.PERCENTAGE
            ValueType.DATE -> DataEntryType.DATE
            ValueType.BOOLEAN -> DataEntryType.YES_NO
            ValueType.COORDINATE -> DataEntryType.COORDINATES
            //ValueType.OPTION_SET -> DataEntryType.MULTIPLE_CHOICE
            else -> DataEntryType.TEXT
        }
    }

    private fun getValidationRules(dataElement: DataElement): List<ValidationRule> {
        val rules = mutableListOf<ValidationRule>()
        
        // Add value type specific validations
        when (dataElement.valueType()) {
            ValueType.NUMBER,
            ValueType.INTEGER,
            ValueType.INTEGER_POSITIVE,
            ValueType.INTEGER_NEGATIVE,
            ValueType.INTEGER_ZERO_OR_POSITIVE,
            ValueType.PERCENTAGE -> {
                rules.add(ValidationRule(
                    rule = "number",
                    message = "Please enter a valid number",
                    severity = ValidationState.ERROR
                ))
            }
            ValueType.COORDINATE -> {
                rules.add(ValidationRule(
                    rule = "coordinates",
                    message = "Please enter valid coordinates",
                    severity = ValidationState.ERROR
                ))
            }
            else -> {}
        }

        return rules
    }

    @SuppressLint("SuspiciousIndentation")
    override suspend fun getDataValues(
        datasetId: String,
        period: String,
        orgUnit: String,
        attributeOptionCombo: String
    ): Flow<List<DataValue>> = flow {
        val dataElements = withContext(Dispatchers.IO) { dataElementDao.getAll() }
        val categoryCombos = withContext(Dispatchers.IO) { categoryComboDao.getAll() }
        val categoryOptionCombos = withContext(Dispatchers.IO) { categoryOptionComboDao.getAll() }
        val orgUnits = withContext(Dispatchers.IO) { organisationUnitDao.getAll() }
        val isOffline = !NetworkUtils.isNetworkAvailable(context)
        if (dataElements.isNotEmpty() && categoryCombos.isNotEmpty() && categoryOptionCombos.isNotEmpty() && orgUnits.isNotEmpty()) {
            if (isOffline) {
                // Load drafts for this instance
                val drafts = withContext(Dispatchers.IO) {
                    draftDao.getDraftsForInstance(datasetId, period, orgUnit, attributeOptionCombo)
                }
                val draftMap = drafts.associateBy { it.dataElement to it.categoryOptionCombo }
                // Merge drafts with metadata
                val merged = dataElements.flatMap { de ->
                    val comboId = de.categoryComboId ?: ""
                    val combos = categoryOptionCombos.filter { it.categoryComboId == comboId }
                    if (combos.isEmpty()) {
                        // No category option combos, treat as default
                        val draft = draftMap[de.id to ""]
                            listOf(DataValue(
                            dataElement = de.id,
                            dataElementName = de.name,
                            sectionName = "Unassigned",
                                categoryOptionCombo = "",
                                categoryOptionComboName = "Default",
                            value = draft?.value,
                            comment = draft?.comment,
                            storedBy = null,
                            validationState = ValidationState.VALID,
                            dataEntryType = DataEntryType.TEXT, // Could be mapped from valueType
                            lastModified = draft?.lastModified ?: System.currentTimeMillis(),
                            validationRules = emptyList()
                        ))
                    } else {
                        combos.map { coc ->
                            val draft = draftMap[de.id to coc.id]
                            DataValue(
                                dataElement = de.id,
                                dataElementName = de.name,
                                sectionName = "Unassigned",
                                categoryOptionCombo = coc.id,
                                categoryOptionComboName = coc.name,
                                value = draft?.value,
                                comment = draft?.comment,
                                storedBy = null,
                                validationState = ValidationState.VALID,
                                dataEntryType = DataEntryType.TEXT, // Could be mapped from valueType
                                lastModified = draft?.lastModified ?: System.currentTimeMillis(),
                                validationRules = emptyList()
                            )
                        }
                    }
                }
                emit(merged)
                return@flow
            }
            // 1. Fetch all sections for the dataset and build a map from dataElement UID to section name
            val sections = d2.dataSetModule().sections()
                .withDataElements()
                .byDataSetUid().eq(datasetId)
                .blockingGet()
            Log.d("DataEntryRepositoryImpl", "Fetched sections: ${sections.map { it.displayName() to it.dataElements()?.map { de -> de.uid() } }}")
            val sectionDataElements = sections.flatMap { section ->
                section.dataElements()?.map { deRef -> (section.displayName() ?: "Unassigned") to deRef.uid() } ?: emptyList()
            }
            val allDataElementUids = sectionDataElements.map { it.second }.distinct()

            // 2. Fetch all data elements metadata
            val allDataElements = d2.dataElementModule().dataElements()
                .byUid().`in`(allDataElementUids)
                .blockingGet()
                .associateBy { it.uid() }

            // 3. Fetch all data values for this instance
            val sdkDataValues = d2.dataValueModule().dataValues()
                .byDataSetUid(datasetId)
                .byPeriod().eq(period)
                .byOrganisationUnitUid().eq(orgUnit)
                .byAttributeOptionComboUid().eq(attributeOptionCombo)
                .blockingGet()
                .associateBy { it.dataElement() to it.categoryOptionCombo() }

            Log.d("DataEntryRepositoryImpl", "Fetched sdkDataValues: ${sdkDataValues.map { it.key to it.value.value() }}")

            // 4. For each (section, dataElement) in sectionDataElements, build DataValue for every possible category option combo
            val mappedDataValues = sectionDataElements.flatMap { (sectionName, deUid) ->
                val dataElement = allDataElements[deUid]
                val comboId = dataElement?.categoryCombo()?.uid() ?: "default"
                // Fetch all possible category option combos for this data element's category combo
                val allCombos = d2.categoryModule().categoryOptionCombos()
                    .byCategoryComboUid().eq(comboId)
                    .blockingGet()
                if (allCombos.isEmpty()) {
                    // No combos, just default
                    val valueObj = sdkDataValues[deUid to ""]
                    listOf(DataValue(
                        dataElement = deUid,
                        dataElementName = dataElement?.displayName() ?: deUid,
                        sectionName = sectionName,
                        categoryOptionCombo = "",
                        categoryOptionComboName = "Default",
                        value = valueObj?.value(),
                        comment = valueObj?.comment(),
                        storedBy = valueObj?.storedBy(),
                        validationState = ValidationState.VALID,
                        dataEntryType = dataElement?.let { getDataEntryType(it) } ?: DataEntryType.TEXT,
                        lastModified = valueObj?.lastUpdated()?.time ?: System.currentTimeMillis(),
                        validationRules = dataElement?.let { getValidationRules(it) } ?: emptyList()
                    ))
                } else {
                    allCombos.map { coc ->
                        val valueObj = sdkDataValues[deUid to coc.uid()]
                        DataValue(
                            dataElement = deUid,
                            dataElementName = dataElement?.displayName() ?: deUid,
                            sectionName = sectionName,
                            categoryOptionCombo = coc.uid(),
                            categoryOptionComboName = coc.displayName() ?: coc.uid(),
                            value = valueObj?.value(),
                            comment = valueObj?.comment(),
                            storedBy = valueObj?.storedBy(),
                            validationState = ValidationState.VALID,
                            dataEntryType = dataElement?.let { getDataEntryType(it) } ?: DataEntryType.TEXT,
                            lastModified = valueObj?.lastUpdated()?.time ?: System.currentTimeMillis(),
                            validationRules = dataElement?.let { getValidationRules(it) } ?: emptyList()
                        )
                    }
                }
            }
            Log.d("DataEntryRepositoryImpl", "mappedDataValues: $mappedDataValues")

            // 5. Save to Room (for caching as finalized values)
            val valueEntities = mappedDataValues.map { dataValue ->
                DataValueEntity(
                    datasetId = datasetId,
                    period = period,
                    orgUnit = orgUnit,
                    attributeOptionCombo = attributeOptionCombo,
                    dataElement = dataValue.dataElement,
                    categoryOptionCombo = dataValue.categoryOptionCombo,
                    value = dataValue.value,
                    comment = dataValue.comment,
                    lastModified = dataValue.lastModified
                )
            }
            dataValueDao.insertAll(valueEntities)

            // 6. Emit the mapped data values
            emit(mappedDataValues)
        } else if (!isOffline) {
            // If Room is empty and online, fetch from SDK, update Room, and return
            // ... existing SDK fetch logic ...
        } else {
            // Offline and Room is empty
            emit(emptyList())
        }
    }

    override suspend fun saveDataValue(
        datasetId: String,
        period: String,
        orgUnit: String,
        attributeOptionCombo: String,
        dataElement: String,
        categoryOptionCombo: String,
        value: String?,
        comment: String?
    ): Result<DataValue> {
        return try {
            val dataValueObjectRepository = d2.dataValueModule().dataValues()
                .value(period, orgUnit, dataElement, categoryOptionCombo, attributeOptionCombo)
            
            if (value != null) {
                try {
                    dataValueObjectRepository.blockingSet(value)
//                    if (comment != null) {
//                        dataValueObjectRepository.blockingSetComment(comment)
//                    }
                    Log.d("DataEntryRepositoryImpl", "Staged value for upload: $value, comment: $comment")
                } catch (e: Exception) {
                    Log.e("DataEntryRepositoryImpl", "Error staging value for upload", e)
                    throw e
                }
            } else {
                dataValueObjectRepository.blockingDeleteIfExist()
            }

            val dataElementObj = d2.dataElementModule().dataElements()
                .uid(dataElement)
                .blockingGet() ?: throw Exception("Data element not found")

            val dataSet = d2.dataSetModule().dataSets()
                .withDataSetElements()
                .uid(datasetId)
                .blockingGet() ?: throw Exception("Dataset not found")

            // Get form name from data set element, fall back to display name, then short name, then uid
            val dataElementName = dataElementObj.shortName()
                ?: dataElementObj.displayName()
                ?: dataElement

            val sectionName = d2.dataSetModule().sections()
                .byDataSetUid().eq(datasetId)
                .blockingGet()
                .find { section ->
                    section.dataElements()?.any { it.uid() == dataElement } == true
                }
                ?.displayName() ?: "Default Section"

            val categoryOptionComboName = if (categoryOptionCombo.isNotEmpty()) {
                d2.categoryModule().categoryOptionCombos()
                    .uid(categoryOptionCombo)
                    .blockingGet()
                    ?.displayName() ?: categoryOptionCombo
            } else {
                "Default"
            }

            // Get value history
            val valueHistory = d2.dataValueModule().dataValues()
                .byDataElementUid().eq(dataElement)
                .byPeriod().eq(period)
                .byOrganisationUnitUid().eq(orgUnit)
                .byCategoryOptionComboUid().eq(categoryOptionCombo)
                .byAttributeOptionComboUid().eq(attributeOptionCombo)
                .blockingGet()
                .map { value ->
                    ValueHistory(
                        value = value.value() ?: "",
                        timestamp = value.lastUpdated()?.time ?: System.currentTimeMillis(),
                        storedBy = value.storedBy() ?: "Unknown",
                        comment = value.comment()
                    )
                }

            Result.success(
                DataValue(
                    dataElement = dataElement,
                    dataElementName = dataElementName.toString(),
                    sectionName = sectionName,
                    categoryOptionCombo = categoryOptionCombo,
                    categoryOptionComboName = categoryOptionComboName,
                    value = value,
                    comment = comment,
                    storedBy = d2.userModule().user().blockingGet()?.username() ?: "Unknown",
                    validationState = ValidationState.VALID,
                    dataEntryType = getDataEntryType(dataElementObj),
                    lastModified = System.currentTimeMillis(),
                    valueHistory = valueHistory,
                    validationRules = getValidationRules(dataElementObj)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun validateValue(
        datasetId: String,
        dataElement: String,
        value: String
    ): ValidationResult {
        return try {
            val dataElementObj = d2.dataElementModule().dataElements()
                .uid(dataElement)
                .blockingGet() ?: return ValidationResult(false, ValidationState.ERROR, "Unknown data element type")

            // Check if value is required
            if (value.isBlank() && dataElementObj.optionSet() == null && 
                dataElementObj.valueType() != org.hisp.dhis.android.core.common.ValueType.BOOLEAN) {
                return ValidationResult(false, ValidationState.ERROR, "This field is required")
            }

            // Explicit validation for each value type
            val valueType = dataElementObj.valueType()
            return when (valueType) {
                org.hisp.dhis.android.core.common.ValueType.NUMBER,
                org.hisp.dhis.android.core.common.ValueType.INTEGER,
                org.hisp.dhis.android.core.common.ValueType.INTEGER_POSITIVE,
                org.hisp.dhis.android.core.common.ValueType.INTEGER_NEGATIVE,
                org.hisp.dhis.android.core.common.ValueType.INTEGER_ZERO_OR_POSITIVE,
                org.hisp.dhis.android.core.common.ValueType.PERCENTAGE -> {
                    if (value.toDoubleOrNull() == null) {
                        ValidationResult(false, ValidationState.ERROR, "Please enter a valid number")
                    } else {
                        ValidationResult(true, ValidationState.VALID, null)
                    }
                }
                org.hisp.dhis.android.core.common.ValueType.BOOLEAN -> {
                    if (value != "true" && value != "false") {
                        ValidationResult(false, ValidationState.ERROR, "Please enter true or false")
                    } else {
                        ValidationResult(true, ValidationState.VALID, null)
                    }
                }
                else -> ValidationResult(true, ValidationState.VALID, null)
            }
        } catch (e: Exception) {
            ValidationResult(false, ValidationState.ERROR, e.message)
        }
    }

    override suspend fun getAvailablePeriods(datasetId: String): List<Period> {
        return d2.periodModule().periodHelper().getPeriodsForDataSet(datasetId).blockingGet().map {
            Period(id = it.periodId().toString())
        }
    }

    override suspend fun getUserOrgUnit(datasetId: String): OrganisationUnit {
        val orgUnits = d2.organisationUnitModule().organisationUnits()
            .byOrganisationUnitScope(org.hisp.dhis.android.core.organisationunit.OrganisationUnit.Scope.SCOPE_DATA_CAPTURE)
            .blockingGet()
        
        if (orgUnits.isEmpty()) {
            throw Exception("No organization units available for data capture")
        }
        
        return OrganisationUnit(
            id = orgUnits.first().uid(),
            name = orgUnits.first().displayName() ?: orgUnits.first().uid()
        )
    }

    override suspend fun getDefaultAttributeOptionCombo(): String {
        return d2.categoryModule().categoryOptionCombos()
            .byCategoryComboUid().eq("default")
            .blockingGet()
            .firstOrNull()?.uid() ?: "default"
    }

    override suspend fun getAttributeOptionCombos(datasetId: String): List<Pair<String, String>> {
        val dataSet = d2.dataSetModule().dataSets()
            .uid(datasetId)
            .blockingGet() ?: return emptyList()
        val categoryComboUid = dataSet.categoryCombo()?.uid() ?: return emptyList()
        return d2.categoryModule().categoryOptionCombos()
            .byCategoryComboUid().eq(categoryComboUid)
            .blockingGet()
            .map { it.uid() to (it.displayName() ?: it.uid()) }
    }

    override suspend fun getCategoryComboStructure(categoryComboUid: String): List<Pair<String, List<Pair<String, String>>>> {
        try {
            // Get the category option combo to find its category combo
            val categoryOptionCombo = d2.categoryModule().categoryOptionCombos()
                .uid(categoryComboUid)
                .blockingGet() ?: return emptyList()

            val actualCategoryComboUid = categoryOptionCombo.categoryCombo()
            
            // Get the category combo with its categories
            val categoryCombo = d2.categoryModule().categoryCombos()
                .withCategories()
                .uid(actualCategoryComboUid!!.uid())
                .blockingGet() ?: return emptyList()

            val categories = categoryCombo.categories() ?: emptyList()
            if (categories.isEmpty()) return emptyList()

            return categories.mapNotNull { catRef ->
                // Get category with its options
                val category = d2.categoryModule().categories()
                    .withCategoryOptions()
                    .uid(catRef.uid())
                    .blockingGet() ?: return@mapNotNull null

                val options = category.categoryOptions() ?: emptyList()
                val optionPairs = options.map { optRef ->
                    optRef.uid() to (optRef.displayName() ?: optRef.uid())
                }

                (category.displayName() ?: category.uid()) to optionPairs
            }
        } catch (e: Exception) {
            return emptyList()
        }
    }

    override suspend fun getCategoryOptionCombos(categoryComboUid: String): List<Pair<String, List<String>>> {
        try {
            // Get the category option combo to find its category combo
            val categoryOptionCombo = d2.categoryModule().categoryOptionCombos()
                .uid(categoryComboUid)
                .blockingGet() ?: return emptyList()

            val actualCategoryComboUid = categoryOptionCombo.categoryCombo()
            
            val combos = d2.categoryModule().categoryOptionCombos()
                .byCategoryComboUid().eq(actualCategoryComboUid!!.uid())
                .withCategoryOptions()
                .blockingGet()
            
            return combos.map { coc ->
                val optionUids = coc.categoryOptions()?.map { it.uid() } ?: emptyList()
                coc.uid() to optionUids
            }
        } catch (e: Exception) {
            return emptyList()
        }
    }

    override suspend fun pushAllLocalData() {
        try {
            val drafts = draftDao.getAllDrafts()
            for (draft in drafts) {
                try {
                    val result = saveDataValue(
                        datasetId = draft.datasetId,
                        period = draft.period,
                        orgUnit = draft.orgUnit,
                        attributeOptionCombo = draft.attributeOptionCombo,
                        dataElement = draft.dataElement,
                        categoryOptionCombo = draft.categoryOptionCombo,
                        value = draft.value,
                        comment = draft.comment
                    )
                    if (result.isSuccess) {
                        draftDao.deleteDraft(draft)
                    }
                } catch (e: Exception) {
                    Log.e("DataEntryRepositoryImpl", "Exception uploading draft: ${draft.dataElement}", e)
                }
            }
            // Upload all local data values
            val uploadResult = d2.dataValueModule().dataValues().blockingUpload()
            Log.d("DataEntryRepositoryImpl", "blockingUpload result: $uploadResult")
            // Only delete drafts if uploadResult is not null/empty (basic check)
            if (uploadResult != null && uploadResult.toString().isNotBlank()) {
                // Download/sync all data values from server
                d2.dataValueModule().dataValues().get()
            } else {
                Log.e("DataEntryRepositoryImpl", "Upload failed or returned empty result: $uploadResult")
            }
        } catch (e: Exception) {
            Log.e("DataEntryRepositoryImpl", "Error pushing local drafts", e)
        }
    }

    override suspend fun syncCurrentEntryForm() {
        withContext(Dispatchers.IO) {
            try {
                val uploadResult = d2.dataValueModule().dataValues().blockingUpload()
                Log.d("DataEntryRepositoryImpl", "blockingUpload result: $uploadResult")
                // Download all aggregated data values
                d2.aggregatedModule().data().blockingDownload()
            } catch (e: Exception) {
                Log.e("DataEntryRepositoryImpl", "Error uploading data values", e)
            }
        }
    }
}