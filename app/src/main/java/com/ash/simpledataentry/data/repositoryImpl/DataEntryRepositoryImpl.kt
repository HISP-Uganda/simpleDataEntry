package com.ash.simpledataentry.data.repositoryImpl

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.runtime.snapshots.SnapshotApplyResult.Failure
import com.ash.simpledataentry.domain.model.*
import com.ash.simpledataentry.domain.repository.DataEntryRepository
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.ash.simpledataentry.data.DatabaseProvider
import com.ash.simpledataentry.data.SessionManager
import org.hisp.dhis.android.core.D2
import com.ash.simpledataentry.data.local.DataValueDraftDao
import com.ash.simpledataentry.data.local.DataValueDraftEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ash.simpledataentry.data.local.DataElementDao
import com.ash.simpledataentry.data.local.CategoryComboDao
import com.ash.simpledataentry.data.local.CategoryOptionComboDao
import com.ash.simpledataentry.data.local.OrganisationUnitDao
import com.ash.simpledataentry.util.NetworkUtils
import com.ash.simpledataentry.data.local.DataValueEntity
import com.ash.simpledataentry.data.local.DataValueDao
import com.ash.simpledataentry.data.cache.MetadataCacheService
import com.ash.simpledataentry.data.sync.NetworkStateManager
import com.ash.simpledataentry.data.sync.SyncQueueManager
import javax.inject.Inject
import kotlin.Pair
import kotlin.Result
import org.hisp.dhis.android.core.dataelement.DataElement
import org.hisp.dhis.android.core.common.ValueType
import android.content.Context

/**
 * CRITICAL: Uses DatabaseProvider for dynamic DAO access to ensure we always use
 * the correct account-specific database, not stale DAOs from app startup.
 */
@ViewModelScoped
class DataEntryRepositoryImpl @Inject constructor(
    private val sessionManager: SessionManager,
    private val databaseProvider: DatabaseProvider,
    private val context: android.content.Context,
    private val metadataCacheService: MetadataCacheService,
    private val networkStateManager: NetworkStateManager,
    private val syncQueueManager: SyncQueueManager
) : DataEntryRepository {

    // Dynamic DAO accessors - always get from current database
    private val draftDao: DataValueDraftDao get() = databaseProvider.getCurrentDatabase().dataValueDraftDao()
    private val dataElementDao: DataElementDao get() = databaseProvider.getCurrentDatabase().dataElementDao()
    private val categoryComboDao: CategoryComboDao get() = databaseProvider.getCurrentDatabase().categoryComboDao()
    private val categoryOptionComboDao: CategoryOptionComboDao get() = databaseProvider.getCurrentDatabase().categoryOptionComboDao()
    private val organisationUnitDao: OrganisationUnitDao get() = databaseProvider.getCurrentDatabase().organisationUnitDao()
    private val dataValueDao: DataValueDao get() = databaseProvider.getCurrentDatabase().dataValueDao()

    private val d2 get() = sessionManager.getD2()!!

    // Track current period offset per dataset for incremental "Show more" loading
    private val periodOffsets = mutableMapOf<String, Int>()

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
            ValueType.TRUE_ONLY -> DataEntryType.YES_ONLY
            ValueType.PHONE_NUMBER -> DataEntryType.PHONE_NUMBER
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
    
    /**
     * Convert string value type to DataEntryType enum
     */
    private fun getDataEntryTypeFromString(valueTypeString: String): DataEntryType {
        return when (valueTypeString.uppercase()) {
            "TEXT" -> DataEntryType.TEXT
            "LONG_TEXT" -> DataEntryType.TEXT
            "NUMBER" -> DataEntryType.NUMBER
            "INTEGER" -> DataEntryType.INTEGER
            "INTEGER_POSITIVE" -> DataEntryType.POSITIVE_INTEGER
            "INTEGER_NEGATIVE" -> DataEntryType.NEGATIVE_INTEGER
            "INTEGER_ZERO_OR_POSITIVE" -> DataEntryType.POSITIVE_INTEGER
            "PERCENTAGE" -> DataEntryType.PERCENTAGE
            "DATE" -> DataEntryType.DATE
            "BOOLEAN" -> DataEntryType.YES_NO
            "TRUE_ONLY" -> DataEntryType.YES_ONLY
            "PHONE_NUMBER" -> DataEntryType.PHONE_NUMBER
            "COORDINATE" -> DataEntryType.COORDINATES
            else -> DataEntryType.TEXT
        }
    }
    
    /**
     * Get validation rules from string value type
     */
    private fun getValidationRulesFromString(valueTypeString: String): List<ValidationRule> {
        val rules = mutableListOf<ValidationRule>()
        
        when (valueTypeString.uppercase()) {
            "NUMBER",
            "INTEGER",
            "INTEGER_POSITIVE",
            "INTEGER_NEGATIVE", 
            "INTEGER_ZERO_OR_POSITIVE",
            "PERCENTAGE" -> {
                rules.add(ValidationRule(
                    rule = "number",
                    message = "Please enter a valid number",
                    severity = ValidationState.ERROR
                ))
            }
            "PHONE_NUMBER" -> {
                rules.add(ValidationRule(
                    rule = "phone",
                    message = "Please enter a valid phone number",
                    severity = ValidationState.ERROR
                ))
            }
            "COORDINATE" -> {
                rules.add(ValidationRule(
                    rule = "coordinates",
                    message = "Please enter valid coordinates",
                    severity = ValidationState.ERROR
                ))
            }
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
        Log.d("DataEntryRepositoryImpl", "Starting optimized getDataValues for dataset: $datasetId")
        
        // OFFLINE-FIRST APPROACH: Always use cached data for instant loading
        // Only fetch fresh data during explicit sync operations, not on screen loads
        val isOffline = !NetworkUtils.isNetworkAvailable(context)
        
        // Check if we have basic metadata in Room (hydrated during login)
        val hasBasicMetadata = withContext(Dispatchers.IO) {
            dataElementDao.getAll().isNotEmpty() && 
            categoryComboDao.getAll().isNotEmpty() && 
            categoryOptionComboDao.getAll().isNotEmpty() && 
            organisationUnitDao.getAll().isNotEmpty()
        }
        
        if (!hasBasicMetadata) {
            // If no metadata cached, user needs to go online and login first
            Log.w("DataEntryRepositoryImpl", "No metadata cached - requires initial online login and sync")
            emit(emptyList())
            return@flow
        }
        
        // OFFLINE-FIRST: Always use cached data for instant loading (both online and offline)
        Log.d("DataEntryRepositoryImpl", "Using cached data for instant loading (offline-first approach)")

        // Get drafts (unsaved user changes)
        val drafts = withContext(Dispatchers.IO) {
            draftDao.getDraftsForInstance(datasetId, period, orgUnit, attributeOptionCombo)
        }
        val draftMap = drafts.associateBy { it.dataElement to it.categoryOptionCombo }

        // Get cached data values (from previous sync/login)
        val cachedDataValues = withContext(Dispatchers.IO) {
            val values = dataValueDao.getValuesForInstance(datasetId, period, orgUnit, attributeOptionCombo)
            values.associateBy { it.dataElement to it.categoryOptionCombo }
        }
        
        // Get metadata from cache with pre-fetched data values (fast)
        val optimizedData = metadataCacheService.getOptimizedDataForEntry(datasetId, period, orgUnit, attributeOptionCombo)

        Log.d("DataEntryRepositoryImpl", "Fresh SDK data loaded: ${optimizedData.sdkDataValues.size} values")

        // DIAGNOSTIC: Log categoryOptionCombos state
        Log.d("DataEntryRepositoryImpl", "=== CATEGORY COMBO DIAGNOSTIC ===")
        Log.d("DataEntryRepositoryImpl", "Total categoryOptionCombos in Room: ${optimizedData.categoryOptionCombos.size}")
        Log.d("DataEntryRepositoryImpl", "Total dataElements in Room: ${optimizedData.dataElements.size}")
        if (optimizedData.categoryOptionCombos.isNotEmpty()) {
            val sampleCocs = optimizedData.categoryOptionCombos.values.take(3)
            sampleCocs.forEach { coc ->
                Log.d("DataEntryRepositoryImpl", "  Sample COC: id=${coc.id}, name=${coc.name}, categoryComboId=${coc.categoryComboId}")
            }
        }
        if (optimizedData.dataElements.isNotEmpty()) {
            val sampleDes = optimizedData.dataElements.values.take(3)
            sampleDes.forEach { de ->
                Log.d("DataEntryRepositoryImpl", "  Sample DE: id=${de.id}, name=${de.name}, categoryComboId=${de.categoryComboId}")
            }
        }

        // Build DataValue objects using cached metadata and data, prioritizing drafts
        val mappedDataValues = optimizedData.sections.flatMap { section ->
            val sectionResults = section.dataElementUids.flatMap { deUid ->
                val dataElement = optimizedData.dataElements[deUid]
                val comboId = dataElement?.categoryComboId ?: ""
                val combos = optimizedData.categoryOptionCombos.values.filter { it.categoryComboId == comboId }

                // Get display name with proper fallback hierarchy
                val dataElementName = getDataElementDisplayName(deUid) ?: dataElement?.name ?: deUid

                if (combos.isEmpty()) {
                    val draft = draftMap[deUid to ""]
                    val cached = cachedDataValues[deUid to ""]
                    val sdkValue = optimizedData.sdkDataValues[deUid to ""]

                    val finalValue = draft?.value ?: sdkValue?.value() ?: cached?.value

                    listOf(DataValue(
                        dataElement = deUid,
                        dataElementName = dataElementName,
                        sectionName = section.name,
                        categoryOptionCombo = "",
                        categoryOptionComboName = "Default",
                        value = finalValue, // Draft > SDK > cached
                        comment = draft?.comment ?: cached?.comment,
                        storedBy = null, // Cached data doesn't store storedBy info
                        validationState = ValidationState.VALID,
                        dataEntryType = getDataEntryTypeFromString(dataElement?.valueType ?: "TEXT"),
                        lastModified = draft?.lastModified ?: cached?.lastModified ?: System.currentTimeMillis(),
                        validationRules = getValidationRulesFromString(dataElement?.valueType ?: "TEXT")
                    ))
                } else {
                    combos.map { coc ->
                        val draft = draftMap[deUid to coc.id]
                        val cached = cachedDataValues[deUid to coc.id]
                        val sdkValue = optimizedData.sdkDataValues[deUid to coc.id]

                        val finalValue = draft?.value ?: sdkValue?.value() ?: cached?.value

                        DataValue(
                            dataElement = deUid,
                            dataElementName = dataElementName,
                            sectionName = section.name,
                            categoryOptionCombo = coc.id,
                            categoryOptionComboName = coc.name,
                            value = finalValue, // Draft > SDK > cached
                            comment = draft?.comment ?: cached?.comment,
                            storedBy = null, // Cached data doesn't store storedBy info
                            validationState = ValidationState.VALID,
                            dataEntryType = getDataEntryTypeFromString(dataElement?.valueType ?: "TEXT"),
                            lastModified = draft?.lastModified ?: cached?.lastModified ?: System.currentTimeMillis(),
                            validationRules = getValidationRulesFromString(dataElement?.valueType ?: "TEXT")
                        )
                    }
                }
            }

            sectionResults
        }
        
        Log.d("DataEntryRepositoryImpl", "Loaded ${mappedDataValues.size} data values from cache (instant loading)")
        emit(mappedDataValues)
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

                    // CRITICAL FIX: Create draft record for sync queue
                    val draft = DataValueDraftEntity(
                        datasetId = datasetId,
                        period = period,
                        orgUnit = orgUnit,
                        attributeOptionCombo = attributeOptionCombo,
                        dataElement = dataElement,
                        categoryOptionCombo = categoryOptionCombo,
                        value = value,
                        comment = comment,
                        lastModified = System.currentTimeMillis()
                    )
                    draftDao.upsertDraft(draft)
                    Log.d("DataEntryRepositoryImpl", "Created draft record for sync queue")
                } catch (e: Exception) {
                    Log.e("DataEntryRepositoryImpl", "Error staging value for upload", e)
                    throw e
                }
            } else {
                dataValueObjectRepository.blockingDeleteIfExist()
                // Also remove draft if value is deleted
                draftDao.deleteDraft(datasetId, period, orgUnit, attributeOptionCombo, dataElement, categoryOptionCombo)
                Log.d("DataEntryRepositoryImpl", "Deleted value and removed from draft queue")
            }

            val dataElementObj = d2.dataElementModule().dataElements()
                .uid(dataElement)
                .blockingGet() ?: throw Exception("Data element not found")

            val dataSet = d2.dataSetModule().dataSets()
                .withDataSetElements()
                .uid(datasetId)
                .blockingGet() ?: throw Exception("Dataset not found")

            // Get form name from data set element, fall back to display name, then short name, then uid
            val dataElementName = dataElementObj.formName()
                ?:dataElementObj.shortName()
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
    ): DataValueValidationResult {
        return try {
            val dataElementObj = d2.dataElementModule().dataElements()
                .uid(dataElement)
                .blockingGet() ?: return DataValueValidationResult(false, ValidationState.ERROR, "Unknown data element type")

            // Check if value is required
            if (value.isBlank() && dataElementObj.optionSet() == null &&
                dataElementObj.valueType() != org.hisp.dhis.android.core.common.ValueType.BOOLEAN &&
                dataElementObj.valueType() != org.hisp.dhis.android.core.common.ValueType.TRUE_ONLY) {
                return DataValueValidationResult(false, ValidationState.ERROR, "This field is required")
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
                        DataValueValidationResult(false, ValidationState.ERROR, "Please enter a valid number")
                    } else {
                        DataValueValidationResult(true, ValidationState.VALID, null)
                    }
                }
                org.hisp.dhis.android.core.common.ValueType.BOOLEAN -> {
                    if (value != "true" && value != "false") {
                        DataValueValidationResult(false, ValidationState.ERROR, "Please enter true or false")
                    } else {
                        DataValueValidationResult(true, ValidationState.VALID, null)
                    }
                }
                org.hisp.dhis.android.core.common.ValueType.TRUE_ONLY -> {
                    if (value.isNotBlank() && value != "true") {
                        DataValueValidationResult(false, ValidationState.ERROR, "Value must be true or empty")
                    } else {
                        DataValueValidationResult(true, ValidationState.VALID, null)
                    }
                }
                org.hisp.dhis.android.core.common.ValueType.DATE -> {
                    try {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                        sdf.isLenient = false
                        sdf.parse(value)
                        DataValueValidationResult(true, ValidationState.VALID, null)
                    } catch (e: Exception) {
                        DataValueValidationResult(false, ValidationState.ERROR, "Use date format YYYY-MM-DD")
                    }
                }
                org.hisp.dhis.android.core.common.ValueType.PHONE_NUMBER -> {
                    val regex = Regex("^\\+?[0-9]{6,15}$")
                    if (!regex.matches(value)) {
                        DataValueValidationResult(false, ValidationState.ERROR, "Please enter a valid phone number")
                    } else {
                        DataValueValidationResult(true, ValidationState.VALID, null)
                    }
                }
                else -> DataValueValidationResult(true, ValidationState.VALID, null)
            }
        } catch (e: Exception) {
            DataValueValidationResult(false, ValidationState.ERROR, e.message)
        }
    }

    override suspend fun getAvailablePeriods(datasetId: String, limit: Int, showAll: Boolean): List<Period> {
        return withContext(Dispatchers.IO) {
            // CRITICAL FIX: NEVER generate full period list to prevent ANR
            // Use incremental loading with tracked offset per dataset

            val currentOffset = if (showAll) {
                // "Show more" clicked - increment offset to fetch more periods
                val current = periodOffsets[datasetId] ?: limit
                val newOffset = current + limit
                periodOffsets[datasetId] = newOffset
                newOffset
            } else {
                // Initial load - reset to default limit
                periodOffsets[datasetId] = limit
                limit
            }

            // Generate ONLY the periods we need (prevents ANR on daily/weekly datasets)
            d2.periodModule().periodHelper()
                .getPeriodsForDataSet(datasetId)
                .blockingGet()
                .sortedByDescending { it.periodId() } // Sort before limiting
                .take(currentOffset) // CRITICAL: Limit BEFORE mapping to prevent full materialization
                .map { Period(id = it.periodId().toString()) }
        }
    }

    override suspend fun getUserOrgUnit(datasetId: String): OrganisationUnit {
        return withContext(Dispatchers.IO) {
            val orgUnits = getUserOrgUnits(datasetId)
            orgUnits.firstOrNull()
                ?: throw Exception("No organization units available for data capture")
        }
    }

    override suspend fun getUserOrgUnits(datasetId: String): List<OrganisationUnit> {
        return withContext(Dispatchers.IO) {
            val scopedOrgUnits = d2.organisationUnitModule().organisationUnits()
                .byOrganisationUnitScope(org.hisp.dhis.android.core.organisationunit.OrganisationUnit.Scope.SCOPE_DATA_CAPTURE)
                .blockingGet()

            if (scopedOrgUnits.isEmpty()) {
                return@withContext emptyList()
            }

            val targetType = resolveTargetType(datasetId)
            val attachedOrgUnits = getAttachedOrgUnits(datasetId, targetType)

            val attachedIds = attachedOrgUnits.map { it.uid() }.toSet()
            val intersected = scopedOrgUnits.filter { it.uid() in attachedIds }

            val relevantOrgUnits = if (attachedIds.isNotEmpty()) intersected else scopedOrgUnits

            mapAndSortOrgUnits(relevantOrgUnits)
        }
    }

    override suspend fun getScopedOrgUnits(): List<OrganisationUnit> {
        return withContext(Dispatchers.IO) {
            val scopedOrgUnits = d2.organisationUnitModule().organisationUnits()
                .byOrganisationUnitScope(org.hisp.dhis.android.core.organisationunit.OrganisationUnit.Scope.SCOPE_DATA_CAPTURE)
                .blockingGet()
            mapAndSortOrgUnits(scopedOrgUnits)
        }
    }

    override suspend fun getOrgUnitsAttachedToDataSets(datasetIds: List<String>): Set<String> {
        if (datasetIds.isEmpty()) return emptySet()
        return withContext(Dispatchers.IO) {
            d2.organisationUnitModule().organisationUnits()
                .byDataSetUids(datasetIds)
                .blockingGet()
                .map { it.uid() }
                .toSet()
        }
    }

    override suspend fun getDatasetIdsAttachedToOrgUnits(
        orgUnitIds: Set<String>,
        datasetIds: List<String>
    ): Set<String> {
        if (orgUnitIds.isEmpty() || datasetIds.isEmpty()) return emptySet()
        return withContext(Dispatchers.IO) {
            datasetIds.filter { datasetId ->
                val attachedOrgUnits = d2.organisationUnitModule().organisationUnits()
                    .byDataSetUids(listOf(datasetId))
                    .blockingGet()
                    .map { it.uid() }
                    .toSet()
                attachedOrgUnits.any { it in orgUnitIds }
            }.toSet()
        }
    }

    override suspend fun expandOrgUnitSelection(targetId: String, orgUnitId: String): Set<String> {
        return withContext(Dispatchers.IO) {
            val targetType = resolveTargetType(targetId)

            val scopedOrgUnits = d2.organisationUnitModule().organisationUnits()
                .byOrganisationUnitScope(org.hisp.dhis.android.core.organisationunit.OrganisationUnit.Scope.SCOPE_DATA_CAPTURE)
                .blockingGet()
            if (scopedOrgUnits.isEmpty()) {
                return@withContext emptySet()
            }

            val attachedOrgUnits = getAttachedOrgUnits(targetId, targetType)
            val attachedIds = attachedOrgUnits.map { it.uid() }.toSet()
            val scopedIds = scopedOrgUnits.map { it.uid() }.toSet()
            val allowedIds = scopedIds.intersect(attachedIds).ifEmpty { scopedIds }

            val selected = d2.organisationUnitModule().organisationUnits()
                .uid(orgUnitId)
                .blockingGet()
                ?: return@withContext emptySet()

            val selectedPath = selected.path()
            val descendants = if (!selectedPath.isNullOrBlank()) {
                d2.organisationUnitModule().organisationUnits()
                    .byPath().like("$selectedPath/%")
                    .blockingGet()
            } else {
                emptyList()
            }

            val expandedIds = buildSet {
                add(orgUnitId)
                descendants.forEach { add(it.uid()) }
            }

            expandedIds.intersect(allowedIds)
        }
    }

    private enum class TargetType {
        DATASET,
        PROGRAM
    }

    private fun resolveTargetType(targetId: String): TargetType {
        val isDataset = d2.dataSetModule().dataSets()
            .uid(targetId)
            .blockingGet() != null
        if (isDataset) {
            return TargetType.DATASET
        }

        val isProgram = d2.programModule().programs()
            .uid(targetId)
            .blockingGet() != null
        return if (isProgram) TargetType.PROGRAM else TargetType.DATASET
    }

    private fun getAttachedOrgUnits(targetId: String, targetType: TargetType): List<org.hisp.dhis.android.core.organisationunit.OrganisationUnit> {
        val repository = d2.organisationUnitModule().organisationUnits()
        return when (targetType) {
            TargetType.DATASET -> repository
                .byDataSetUids(listOf(targetId))
                .blockingGet()
            TargetType.PROGRAM -> repository
                .byProgramUids(listOf(targetId))
                .blockingGet()
        }
    }

    private fun mapAndSortOrgUnits(
        orgUnits: List<org.hisp.dhis.android.core.organisationunit.OrganisationUnit>
    ): List<OrganisationUnit> {
        return orgUnits.map { orgUnit ->
            OrganisationUnit(
                id = orgUnit.uid(),
                name = orgUnit.displayName() ?: orgUnit.uid(),
                path = orgUnit.displayNamePath()?.joinToString(" / "),
                uidPath = orgUnit.path(),
                parentId = orgUnit.parent()?.uid(),
                level = orgUnit.level()
            )
        }.sortedWith(
            compareBy<OrganisationUnit> { it.path ?: it.name }
                .thenBy { it.name }
        )
    }

    override suspend fun getDefaultAttributeOptionCombo(): String {
        return withContext(Dispatchers.IO) {
            d2.categoryModule().categoryOptionCombos()
                .byDisplayName().eq("default")
                .one()
                .blockingGet()
                ?.uid()
                ?: "HllvX50cXC0"
        }
    }

    override suspend fun getAttributeOptionCombos(datasetId: String): List<Pair<String, String>> {
        return withContext(Dispatchers.IO) {
            val dataSet = d2.dataSetModule().dataSets()
                .uid(datasetId)
                .blockingGet() ?: return@withContext emptyList()
            val categoryComboUid = dataSet.categoryCombo()?.uid() ?: return@withContext emptyList()
            val combos = d2.categoryModule().categoryOptionCombos()
                .byCategoryComboUid().eq(categoryComboUid)
                .blockingGet()
            combos
                .sortedBy { (it.displayName() ?: it.uid()).lowercase() }
                .map { it.uid() to (it.displayName() ?: it.uid()) }
        }
    }

    override suspend fun refreshDataValues(
        datasetId: String,
        period: String,
        orgUnit: String,
        attributeOptionCombo: String
    ): Int {
        return metadataCacheService.refreshDataValues(datasetId, period, orgUnit, attributeOptionCombo)
    }

    override suspend fun hasCachedDataValues(
        datasetId: String,
        period: String,
        orgUnit: String,
        attributeOptionCombo: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            dataValueDao.getValuesForInstance(datasetId, period, orgUnit, attributeOptionCombo).isNotEmpty()
        }
    }

    override suspend fun getCategoryComboStructure(categoryComboUid: String): List<Pair<String, List<Pair<String, String>>>> {
        return metadataCacheService.getCategoryComboStructure(categoryComboUid)
    }

    override suspend fun getCategoryOptionCombos(categoryComboUid: String): List<Pair<String, List<String>>> {
        return metadataCacheService.getCategoryOptionCombos(categoryComboUid)
    }

    override suspend fun pushAllLocalData() {
        if (!networkStateManager.isOnline()) {
            Log.i("DataEntryRepositoryImpl", "Offline - queuing data for sync when connection available")
            syncQueueManager.queueForSync()
            return
        }

        val syncResult = syncQueueManager.startSync()
        if (syncResult.isFailure) {
            Log.e("DataEntryRepositoryImpl", "Sync failed: ${syncResult.exceptionOrNull()?.message}")
            throw syncResult.exceptionOrNull() ?: Exception("Sync failed")
        }
    }

    override suspend fun pushDataForInstance(
        datasetId: String,
        period: String,
        orgUnit: String,
        attributeOptionCombo: String
    ) {
        if (!networkStateManager.isOnline()) {
            Log.i("DataEntryRepositoryImpl", "Offline - queuing instance data for sync when connection available")
            syncQueueManager.queueForSync()
            return
        }

        val syncResult = syncQueueManager.startSyncForInstance(
            datasetId, period, orgUnit, attributeOptionCombo
        )
        if (syncResult.isFailure) {
            Log.e("DataEntryRepositoryImpl", "Instance sync failed: ${syncResult.exceptionOrNull()?.message}")
            throw syncResult.exceptionOrNull() ?: Exception("Instance sync failed")
        }
    }

    override suspend fun syncCurrentEntryForm() {
        withContext(Dispatchers.IO) {
            if (!networkStateManager.isOnline()) {
                Log.i("DataEntryRepositoryImpl", "Offline - queuing current form for sync when connection available")
                syncQueueManager.queueForSync()
                return@withContext
            }

            try {
                // First, upload any local data changes
                val syncResult = syncQueueManager.startSync()
                if (syncResult.isFailure) {
                    throw syncResult.exceptionOrNull() ?: Exception("Upload sync failed")
                }

                // Then download the latest aggregated data from server
                Log.d("DataEntryRepositoryImpl", "Downloading latest aggregated data from server...")
                d2.aggregatedModule().data().blockingDownload()

                Log.d("DataEntryRepositoryImpl", "Current entry form synced successfully - upload completed and latest data downloaded")
            } catch (e: Exception) {
                Log.e("DataEntryRepositoryImpl", "Error syncing current entry form", e)
                throw e
            }
        }
    }

    /**
     * Get display name for data element with proper fallback hierarchy.
     * Uses DHIS2 SDK to fetch: formName > shortName > displayName > technical name
     */
    private suspend fun getDataElementDisplayName(dataElementUid: String): String? {
        return try {
            val dataElementObj = d2.dataElementModule().dataElements()
                .uid(dataElementUid)
                .blockingGet()

            dataElementObj?.formName()
                ?: dataElementObj?.shortName()
                ?: dataElementObj?.displayName()
        } catch (e: Exception) {
            Log.w("DataEntryRepositoryImpl", "Failed to fetch display name for data element $dataElementUid: ${e.message}")
            null
        }
    }

    override suspend fun getOptionSetForDataElement(dataElementId: String): com.ash.simpledataentry.domain.model.OptionSet? {
        return withContext(Dispatchers.IO) {
            try {
                // Get data element and check if it has an option set
                val dataElement = d2.dataElementModule().dataElements()
                    .uid(dataElementId)
                    .blockingGet() ?: return@withContext null

                val optionSetUid = dataElement.optionSet()?.uid() ?: return@withContext null

                // Fetch option set
                val optionSet = d2.optionModule().optionSets()
                    .uid(optionSetUid)
                    .blockingGet() ?: return@withContext null

                // Fetch options separately
                val sdkOptions = d2.optionModule().options()
                    .byOptionSetUid().eq(optionSetUid)
                    .blockingGet()

                // Map options
                val options = sdkOptions.mapIndexed { index, option ->
                    com.ash.simpledataentry.domain.model.Option(
                        code = option.code() ?: option.uid(),
                        name = option.name() ?: option.code() ?: option.uid(),
                        displayName = option.displayName(),
                        sortOrder = option.sortOrder() ?: index
                    )
                }

                val result = com.ash.simpledataentry.domain.model.OptionSet(
                    id = optionSet.uid(),
                    name = optionSet.name() ?: optionSet.uid(),
                    displayName = optionSet.displayName(),
                    options = options,
                    valueType = mapValueType(dataElement.valueType())
                )

                Log.d("DataEntryRepositoryImpl", "Loaded option set for $dataElementId: ${result.name} with ${result.options.size} options")
                result
            } catch (e: Exception) {
                Log.e("DataEntryRepositoryImpl", "Error fetching option set for data element $dataElementId", e)
                null
            }
        }
    }

    override suspend fun getAllOptionSetsForDataset(datasetId: String): Map<String, com.ash.simpledataentry.domain.model.OptionSet> {
        return withContext(Dispatchers.IO) {
            try {
                // Get all data elements for dataset
                val dataset = d2.dataSetModule().dataSets()
                    .withDataSetElements()
                    .uid(datasetId)
                    .blockingGet() ?: return@withContext emptyMap()

                val dataElementUids = dataset.dataSetElements()?.mapNotNull { it.dataElement()?.uid() } ?: emptyList()

                val optionSets = mutableMapOf<String, com.ash.simpledataentry.domain.model.OptionSet>()

                dataElementUids.forEach { dataElementUid ->
                    val optionSet = getOptionSetForDataElement(dataElementUid)
                    if (optionSet != null) {
                        optionSets[dataElementUid] = optionSet
                    }
                }

                Log.d("DataEntryRepositoryImpl", "Loaded ${optionSets.size} option sets for dataset $datasetId")
                optionSets
            } catch (e: Exception) {
                Log.e("DataEntryRepositoryImpl", "Error fetching option sets for dataset $datasetId", e)
                emptyMap()
            }
        }
    }

    /**
     * Map DHIS2 SDK ValueType to domain ValueType
     */
    private fun mapValueType(sdkValueType: org.hisp.dhis.android.core.common.ValueType?): com.ash.simpledataentry.domain.model.ValueType {
        return when (sdkValueType) {
            org.hisp.dhis.android.core.common.ValueType.TEXT -> com.ash.simpledataentry.domain.model.ValueType.TEXT
            org.hisp.dhis.android.core.common.ValueType.LONG_TEXT -> com.ash.simpledataentry.domain.model.ValueType.LONG_TEXT
            org.hisp.dhis.android.core.common.ValueType.PHONE_NUMBER -> com.ash.simpledataentry.domain.model.ValueType.PHONE_NUMBER
            org.hisp.dhis.android.core.common.ValueType.EMAIL -> com.ash.simpledataentry.domain.model.ValueType.EMAIL
            org.hisp.dhis.android.core.common.ValueType.BOOLEAN -> com.ash.simpledataentry.domain.model.ValueType.BOOLEAN
            org.hisp.dhis.android.core.common.ValueType.TRUE_ONLY -> com.ash.simpledataentry.domain.model.ValueType.TRUE_ONLY
            org.hisp.dhis.android.core.common.ValueType.DATE -> com.ash.simpledataentry.domain.model.ValueType.DATE
            org.hisp.dhis.android.core.common.ValueType.DATETIME -> com.ash.simpledataentry.domain.model.ValueType.DATETIME
            org.hisp.dhis.android.core.common.ValueType.TIME -> com.ash.simpledataentry.domain.model.ValueType.TIME
            org.hisp.dhis.android.core.common.ValueType.NUMBER -> com.ash.simpledataentry.domain.model.ValueType.NUMBER
            org.hisp.dhis.android.core.common.ValueType.UNIT_INTERVAL -> com.ash.simpledataentry.domain.model.ValueType.UNIT_INTERVAL
            org.hisp.dhis.android.core.common.ValueType.PERCENTAGE -> com.ash.simpledataentry.domain.model.ValueType.PERCENTAGE
            org.hisp.dhis.android.core.common.ValueType.INTEGER -> com.ash.simpledataentry.domain.model.ValueType.INTEGER
            org.hisp.dhis.android.core.common.ValueType.INTEGER_POSITIVE -> com.ash.simpledataentry.domain.model.ValueType.INTEGER_POSITIVE
            org.hisp.dhis.android.core.common.ValueType.INTEGER_NEGATIVE -> com.ash.simpledataentry.domain.model.ValueType.INTEGER_NEGATIVE
            org.hisp.dhis.android.core.common.ValueType.INTEGER_ZERO_OR_POSITIVE -> com.ash.simpledataentry.domain.model.ValueType.INTEGER_ZERO_OR_POSITIVE
            org.hisp.dhis.android.core.common.ValueType.COORDINATE -> com.ash.simpledataentry.domain.model.ValueType.COORDINATE
            org.hisp.dhis.android.core.common.ValueType.AGE -> com.ash.simpledataentry.domain.model.ValueType.AGE
            org.hisp.dhis.android.core.common.ValueType.URL -> com.ash.simpledataentry.domain.model.ValueType.URL
            org.hisp.dhis.android.core.common.ValueType.FILE_RESOURCE -> com.ash.simpledataentry.domain.model.ValueType.FILE_RESOURCE
            org.hisp.dhis.android.core.common.ValueType.IMAGE -> com.ash.simpledataentry.domain.model.ValueType.IMAGE
            else -> com.ash.simpledataentry.domain.model.ValueType.TEXT
        }
    }

    override suspend fun getValidationRulesForDataset(datasetId: String): List<org.hisp.dhis.android.core.validation.ValidationRule> {
        return withContext(Dispatchers.IO) {
            try {
                d2.validationModule().validationRules()
                    .byDataSetUids(listOf(datasetId))
                    .blockingGet()
            } catch (e: Exception) {
                Log.w("DataEntryRepository", "Failed to fetch validation rules for dataset $datasetId: ${e.message}")
                emptyList()
            }
        }
    }
}
