package com.ash.simpledataentry.data.repositoryImpl

import com.ash.simpledataentry.domain.model.*
import com.ash.simpledataentry.domain.repository.DataEntryRepository
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.ash.simpledataentry.data.SessionManager
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.arch.helpers.Result.Failure
import org.hisp.dhis.android.core.arch.helpers.Result.Success
import org.hisp.dhis.android.core.dataelement.DataElement
import org.hisp.dhis.android.core.common.ValueType
import javax.inject.Inject
import android.util.Log

@ViewModelScoped
class DataEntryRepositoryImpl @Inject constructor(
    private val sessionManager: SessionManager
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
        
        // Add required validation if needed
        if (dataElement.optionSet() == null && dataElement.valueType() != ValueType.BOOLEAN) {
            rules.add(ValidationRule(
                rule = "required",
                message = "This field is required",
                severity = ValidationState.ERROR
            ))
        }

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
//
//                // Add min/max value validations if they exist
//                dataElement.minValue?.let { minValue ->
//                    rules.add(ValidationRule(
//                        rule = "min",
//                        message = "Value must be greater than or equal to $minValue",
//                        severity = ValidationState.ERROR
//                    ))
//                }
//
//                dataElement.maxValue?.let { maxValue ->
//                    rules.add(ValidationRule(
//                        rule = "max",
//                        message = "Value must be less than or equal to $maxValue",
//                        severity = ValidationState.ERROR
//                    ))
//                }
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

    override suspend fun getDataValues(
        datasetId: String,
        period: String,
        orgUnit: String,
        attributeOptionCombo: String
    ): Flow<List<DataValue>> = flow {
        try {
            // Get dataset and sections in a single query
            val dataSet = d2.dataSetModule().dataSets()
                .withDataSetElements()
                .uid(datasetId)
                .blockingGet() ?: throw Exception("Dataset not found")

            // Get all existing values in a single query
            val existingValues = d2.dataValueModule().dataValues()
                .byPeriod().eq(period)
                .byOrganisationUnitUid().eq(orgUnit)
                .byDataSetUid(datasetId)
                .byAttributeOptionComboUid().eq(attributeOptionCombo)
                .blockingGet()

            // Get all sections in a single query
            val sections = d2.dataSetModule().sections()
                .byDataSetUid().eq(datasetId)
                .withDataElements()
                .blockingGet()

            val allDataValues = mutableListOf<DataValue>()

            // Process sections
            sections.forEach { section ->
                val sectionName = section.displayName() ?: "Default Section"
                section.dataElements()?.forEach { sectionDataElement ->
                    val dataElement = d2.dataElementModule().dataElements()
                        .uid(sectionDataElement.uid())
                        .blockingGet()

                    if (dataElement != null) {
                        // Get form name from data set element, fall back to display name, then short name, then uid
                        val dataElementName = dataElement.shortName()
                            ?: dataElement.displayName()
                            ?: dataElement.uid()

                        val categoryComboUid = dataElement.categoryComboUid()

                        if (categoryComboUid != null) {
                            // Get category option combos in a single query
                            val categoryOptionCombos = d2.categoryModule().categoryOptionCombos()
                                .byCategoryComboUid().eq(categoryComboUid)
                                .blockingGet()

                            categoryOptionCombos.forEach { coc ->
                                val existingValue = existingValues.find {
                                    it.dataElement() == dataElement.uid() &&
                                    it.categoryOptionCombo() == coc.uid()
                                }

                                allDataValues.add(
                                    DataValue(
                                        dataElement = dataElement.uid(),
                                        dataElementName = dataElementName.toString(),
                                        sectionName = sectionName,
                                        categoryOptionCombo = coc.uid(),
                                        categoryOptionComboName = coc.displayName() ?: coc.uid(),
                                        value = existingValue?.value(),
                                        comment = existingValue?.comment(),
                                        storedBy = existingValue?.storedBy(),
                                        validationState = ValidationState.VALID,
                                        dataEntryType = getDataEntryType(dataElement),
                                        isRequired = dataElement.optionSet() == null && 
                                                   dataElement.valueType() != ValueType.BOOLEAN,
                                        lastModified = existingValue?.lastUpdated()?.time ?: System.currentTimeMillis(),
                                        validationRules = getValidationRules(dataElement)
                                    )
                                )
                            }
                        } else {
                            val existingValue = existingValues.find {
                                it.dataElement() == dataElement.uid()
                            }

                            allDataValues.add(
                                DataValue(
                                    dataElement = dataElement.uid(),
                                    dataElementName = dataElementName.toString(),
                                    sectionName = sectionName,
                                    categoryOptionCombo = "",
                                    categoryOptionComboName = "Default",
                                    value = existingValue?.value(),
                                    comment = existingValue?.comment(),
                                    storedBy = existingValue?.storedBy(),
                                    validationState = ValidationState.VALID,
                                    dataEntryType = getDataEntryType(dataElement),
                                    isRequired = dataElement.optionSet() == null && 
                                               dataElement.valueType() != ValueType.BOOLEAN,
                                    lastModified = existingValue?.lastUpdated()?.time ?: System.currentTimeMillis(),
                                    validationRules = getValidationRules(dataElement)
                                )
                            )
                        }
                    }
                }
            }

            // Handle unassigned data elements
            val sectionDataElementUids = sections.flatMap { it.dataElements()?.map { it.uid() } ?: emptyList() }.toSet()
            val unassignedDataElements = dataSet.dataSetElements()?.filter { !sectionDataElementUids.contains(it.dataElement().uid()) }

            if (!unassignedDataElements.isNullOrEmpty()) {
                unassignedDataElements.forEach { dataSetElement ->
                    val dataElementUid = dataSetElement.dataElement().uid()
                    val dataElement = d2.dataElementModule().dataElements()
                        .uid(dataElementUid)
                        .blockingGet() ?: return@forEach

                    // Get form name from data set element, fall back to display name, then short name, then uid
                    val dataElementName = dataElement.shortName()
                        ?: dataElement.displayName()
                        ?: dataElementUid

                    val existingValue = existingValues.find {
                        it.dataElement() == dataElementUid
                    }

                    allDataValues.add(
                        DataValue(
                            dataElement = dataElementUid,
                            dataElementName = dataElementName,
                            sectionName = "Unassigned",
                            categoryOptionCombo = "",
                            categoryOptionComboName = "Default",
                            value = existingValue?.value(),
                            comment = existingValue?.comment(),
                            storedBy = existingValue?.storedBy(),
                            validationState = ValidationState.VALID,
                            dataEntryType = getDataEntryType(dataElement),
                            isRequired = dataElement.optionSet() == null && 
                                       dataElement.valueType() != ValueType.BOOLEAN,
                            lastModified = existingValue?.lastUpdated()?.time ?: System.currentTimeMillis(),
                            validationRules = getValidationRules(dataElement)
                        )
                    )
                }
            }

            emit(allDataValues)
        } catch (e: Exception) {
            Log.e("DataEntryRepositoryImpl", "Failed to fetch data values", e)
            throw e
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
                dataValueObjectRepository.set(value)
                if (comment != null) {
                    dataValueObjectRepository.setComment(comment)
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
                    isRequired = dataElementObj.optionSet() == null && 
                               dataElementObj.valueType() != ValueType.BOOLEAN,
//                    minValue = dataElementObj.minValue,
//                    maxValue = dataElementObj.maxValue(),
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
                dataElementObj.valueType() != ValueType.BOOLEAN) {
                return ValidationResult(false, ValidationState.ERROR, "This field is required")
            }

            // Validate based on value type
            when (val result = dataElementObj.valueType()!!.validator.validate(value)) {
                is Success -> ValidationResult(true, ValidationState.VALID, null)
                is Failure -> ValidationResult(false, ValidationState.ERROR, result.failure.message)
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
            Log.d("DataEntryRepositoryImpl", "Starting category combo structure fetch for UID: $categoryComboUid")
            
            // First get the category option combo to find its category combo
            val categoryOptionCombo = d2.categoryModule().categoryOptionCombos()
                .uid(categoryComboUid)
                .blockingGet()

            if (categoryOptionCombo == null) {
                Log.d("DataEntryRepositoryImpl", "Category option combo not found for UID: $categoryComboUid")
                return emptyList()
            }

            val actualCategoryComboUid = categoryOptionCombo.categoryCombo()
            Log.d("DataEntryRepositoryImpl", "Found category combo UID: $actualCategoryComboUid for category option combo: $categoryComboUid")
            
            // Now get the category combo with its categories
            val categoryCombo = d2.categoryModule().categoryCombos()
                .withCategories()
                .uid(actualCategoryComboUid!!.uid())
                .blockingGet()

            if (categoryCombo == null) {
                Log.d("DataEntryRepositoryImpl", "Category combo not found for UID: $actualCategoryComboUid")
                return emptyList()
            }

            Log.d("DataEntryRepositoryImpl", "Found category combo: ${categoryCombo.uid()}")
            val categories = categoryCombo.categories() ?: emptyList()
            Log.d("DataEntryRepositoryImpl", "Found ${categories.size} categories for combo: $actualCategoryComboUid")
            
            if (categories.isEmpty()) {
                Log.d("DataEntryRepositoryImpl", "No categories found in category combo")
                return emptyList()
            }

            return categories.mapNotNull { catRef ->
                Log.d("DataEntryRepositoryImpl", "Processing category reference: ${catRef.uid()}")
                
                // Get category with its options
                val category = d2.categoryModule().categories()
                    .withCategoryOptions()
                    .uid(catRef.uid())
                    .blockingGet()

                if (category == null) {
                    Log.d("DataEntryRepositoryImpl", "Category not found for reference: ${catRef.uid()}")
                    return@mapNotNull null
                }

                Log.d("DataEntryRepositoryImpl", "Found category: ${category.uid()}")
                val options = category.categoryOptions() ?: emptyList()
                Log.d("DataEntryRepositoryImpl", "Found ${options.size} options for category: ${category.uid()}")

                val optionPairs = options.map { optRef ->
                    val optionUid = optRef.uid()
                    val optionName = optRef.displayName() ?: optionUid
                    Log.d("DataEntryRepositoryImpl", "Option: $optionUid -> $optionName")
                    optionUid to optionName
                }

                Log.d("DataEntryRepositoryImpl", "Category: ${category.displayName() ?: category.uid()}")
                Log.d("DataEntryRepositoryImpl", "Options: ${optionPairs.map { it.second }}")

                (category.displayName() ?: category.uid()) to optionPairs
            }
        } catch (e: Exception) {
            Log.e("DataEntryRepositoryImpl", "Error fetching category combo structure", e)
            e.printStackTrace()
            return emptyList()
        }
    }

    override suspend fun getCategoryOptionCombos(categoryComboUid: String): List<Pair<String, List<String>>> {
        try {
            Log.d("DataEntryRepositoryImpl", "Fetching category option combos for UID: $categoryComboUid")
            
            // First get the category option combo to find its category combo
            val categoryOptionCombo = d2.categoryModule().categoryOptionCombos()
                .uid(categoryComboUid)
                .blockingGet()

            if (categoryOptionCombo == null) {
                Log.d("DataEntryRepositoryImpl", "Category option combo not found for UID: $categoryComboUid")
                return emptyList()
            }

            val actualCategoryComboUid = categoryOptionCombo.categoryCombo()
            Log.d("DataEntryRepositoryImpl", "Found category combo UID: $actualCategoryComboUid for category option combo: $categoryComboUid")
            
            val combos = d2.categoryModule().categoryOptionCombos()
                .byCategoryComboUid().eq(actualCategoryComboUid!!.uid())
                .withCategoryOptions()
                .blockingGet()
            
            Log.d("DataEntryRepositoryImpl", "Found ${combos.size} category option combos")
            
            return combos.map { coc ->
                val optionUids = coc.categoryOptions()?.map { it.uid() } ?: emptyList()
                
                Log.d("DataEntryRepositoryImpl", "Combo: ${coc.uid()}")
                Log.d("DataEntryRepositoryImpl", "Options: $optionUids")
                
                coc.uid() to optionUids
            }
        } catch (e: Exception) {
            Log.e("DataEntryRepositoryImpl", "Error fetching category option combos", e)
            e.printStackTrace()
            return emptyList()
        }
    }
}