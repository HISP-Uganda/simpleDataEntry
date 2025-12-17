package com.ash.simpledataentry.data.cache

import android.util.Log
import com.ash.simpledataentry.data.DatabaseProvider
import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.data.local.*
import com.ash.simpledataentry.domain.model.GroupingStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.hisp.dhis.android.core.D2
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for caching and optimizing metadata access across feature stacks.
 * Leverages data already loaded during login to reduce API calls in EditEntryScreen.
 *
 * CRITICAL: Uses DatabaseProvider for dynamic DAO access to ensure we always use
 * the correct account-specific database, not stale DAOs from app startup.
 */
@Singleton
class MetadataCacheService @Inject constructor(
    private val sessionManager: SessionManager,
    private val databaseProvider: DatabaseProvider
) {
    // Dynamic DAO accessors - always get from current database
    private val dataElementDao: DataElementDao get() = databaseProvider.getCurrentDatabase().dataElementDao()
    private val categoryComboDao: CategoryComboDao get() = databaseProvider.getCurrentDatabase().categoryComboDao()
    private val categoryOptionComboDao: CategoryOptionComboDao get() = databaseProvider.getCurrentDatabase().categoryOptionComboDao()
    private val organisationUnitDao: OrganisationUnitDao get() = databaseProvider.getCurrentDatabase().organisationUnitDao()
    private val dataValueDao: DataValueDao get() = databaseProvider.getCurrentDatabase().dataValueDao()

    private val d2 get() = sessionManager.getD2()!!

    // Cache for sections to avoid repeated API calls
    private val sectionsCache = mutableMapOf<String, List<SectionInfo>>()

    // Cache for category combo structures to avoid repeated API calls
    private val categoryComboStructureCache = mutableMapOf<String, List<Pair<String, List<Pair<String, String>>>>>()

    // Cache for category option combos
    private val categoryOptionCombosCache = mutableMapOf<String, List<Pair<String, List<String>>>>()

    // Cache for grouping analysis results (validation rule-based grouping)
    // Key: datasetId, Value: Map of section name to grouping strategies
    // This cache persists across ViewModel lifecycle to avoid expensive re-computation
    private val groupingStrategyCache = mutableMapOf<String, Map<String, List<GroupingStrategy>>>()

    // Cache for implied category inference results (for event/tracker programs)
    // Key: programId:sectionName, Value: ImpliedCategoryCombination
    // This cache persists across ViewModel lifecycle to avoid expensive re-computation
    private val impliedCategoryCache = mutableMapOf<String, com.ash.simpledataentry.domain.model.ImpliedCategoryCombination>()

    data class SectionInfo(
        val name: String,
        val dataElementUids: List<String>
    )
    
    /**
     * Get optimized data for EditEntryScreen with pre-fetched and pre-mapped data values
     * This implements the requested architecture: metadata first, then data values mapped to UI structure
     */
    suspend fun getOptimizedDataForEntry(
        datasetId: String,
        period: String,
        orgUnit: String,
        attributeOptionCombo: String
    ): OptimizedEntryData = withContext(Dispatchers.IO) {

        Log.d("MetadataCacheService", "Getting optimized data for dataset: $datasetId")

        // 1. Get sections (cache or API) - this is the UI structure parsing
        val sections = getSectionsForDataset(datasetId)
        val allDataElementUids = sections.flatMap { it.dataElementUids }.distinct()

        // DEBUG: Log what UIDs we're looking for
        Log.d("MetadataCacheService", "Looking for ${allDataElementUids.size} data element UIDs from ${sections.size} sections")
        if (allDataElementUids.isNotEmpty()) {
            Log.d("MetadataCacheService", "First 5 UIDs requested: ${allDataElementUids.take(5)}")
        }

        // 2. Get data elements from Room (already hydrated during login)
        val dataElements = dataElementDao.getByIds(allDataElementUids).associateBy { it.id }

        // DEBUG: Log what we found
        Log.d("MetadataCacheService", "Found ${dataElements.size} data elements in Room")
        if (dataElements.isEmpty() && allDataElementUids.isNotEmpty()) {
            // Check total data elements in Room
            val totalInRoom = dataElementDao.getAll().size
            Log.w("MetadataCacheService", "WARNING: Room has $totalInRoom total data elements but none match the requested UIDs!")
        }

        // 3. Get category combos from Room (already hydrated during login)
        val categoryCombos = categoryComboDao.getAll().associateBy { it.id }

        // 4. Get category option combos from Room (already hydrated during login)
        val categoryOptionCombos = categoryOptionComboDao.getAll().associateBy { it.id }

        // 5. Get org units from Room (already hydrated during login)
        val orgUnits = organisationUnitDao.getAll().associateBy { it.id }

        // 6. NEW STEP: Pre-fetch and map data values using the parsed UI structure
        val sdkDataValues = preFetchAndMapDataValues(datasetId, period, orgUnit, attributeOptionCombo)

        Log.d("MetadataCacheService", "Optimized data complete: ${sections.size} sections, ${dataElements.size} data elements, ${sdkDataValues.size} data values")

        OptimizedEntryData(
            sections = sections,
            dataElements = dataElements,
            categoryCombos = categoryCombos,
            categoryOptionCombos = categoryOptionCombos,
            orgUnits = orgUnits,
            sdkDataValues = sdkDataValues
        )
    }
    
    /**
     * Get optimized data with fresh SDK data values for sync operations
     * Use this when you need the latest server data, not for regular data entry
     */
    suspend fun getOptimizedDataWithFreshValues(
        datasetId: String,
        period: String,
        orgUnit: String,
        attributeOptionCombo: String
    ): OptimizedEntryData = withContext(Dispatchers.IO) {
        
        Log.d("MetadataCacheService", "Getting optimized data with fresh values for dataset: $datasetId")
        
        // Get cached metadata (fast)
        val baseData = getOptimizedDataForEntry(datasetId, period, orgUnit, attributeOptionCombo)
        
        // Get fresh data values from SDK (slower, only when needed)
        val sdkDataValues = d2.dataValueModule().dataValues()
            .byDataSetUid(datasetId)
            .byPeriod().eq(period)
            .byOrganisationUnitUid().eq(orgUnit)
            .byAttributeOptionComboUid().eq(attributeOptionCombo)
            .blockingGet()
            .associateBy { (it.dataElement() ?: "") to (it.categoryOptionCombo() ?: "") }
        
        Log.d("MetadataCacheService", "Fresh data loaded: ${sdkDataValues.size} data values from server")
            
        baseData.copy(sdkDataValues = sdkDataValues)
    }
    
    /**
     * Get sections for a dataset with caching
     */
    private suspend fun getSectionsForDataset(datasetId: String): List<SectionInfo> {
        return sectionsCache.getOrPut(datasetId) {
            val sections = d2.dataSetModule().sections()
                .withDataElements()
                .byDataSetUid().eq(datasetId)
                .blockingGet()
                
            sections.map { section ->
                SectionInfo(
                    name = section.displayName() ?: "Unassigned",
                    dataElementUids = section.dataElements()?.map { it.uid() } ?: emptyList()
                )
            }
        }
    }
    
    /**
     * Get category combo structure with caching
     * Categories and options are returned in the order provided by the SDK
     * (SDK maintains metadata-defined order internally)
     */
    suspend fun getCategoryComboStructure(categoryComboUid: String): List<Pair<String, List<Pair<String, String>>>> {
        return categoryComboStructureCache.getOrPut(categoryComboUid) {
            try {
                // Get the category option combo to find its category combo
                val categoryOptionCombo = d2.categoryModule().categoryOptionCombos()
                    .uid(categoryComboUid)
                    .blockingGet() ?: return@getOrPut emptyList()

                val actualCategoryComboUid = categoryOptionCombo.categoryCombo()

                // Get the category combo with its categories
                val categoryCombo = d2.categoryModule().categoryCombos()
                    .withCategories()
                    .uid(actualCategoryComboUid!!.uid())
                    .blockingGet() ?: return@getOrPut emptyList()

                val categories = categoryCombo.categories() ?: emptyList()
                if (categories.isEmpty()) return@getOrPut emptyList()

                // Process categories in the order returned by SDK (maintains metadata order)
                // Use mapIndexed to preserve original order while processing
                categories.mapIndexedNotNull { catIndex, catRef ->
                    val category = d2.categoryModule().categories()
                        .withCategoryOptions()
                        .uid(catRef.uid())
                        .blockingGet() ?: return@mapIndexedNotNull null

                    val options = category.categoryOptions() ?: emptyList()

                    // Options from SDK should already be in sorted order
                    val optionPairs = options.map { optRef ->
                        optRef.uid() to (optRef.displayName() ?: optRef.uid())
                    }

                    Log.d("MetadataCacheService", "Category $catIndex: ${category.displayName()} with ${optionPairs.size} options")

                    (category.displayName() ?: category.uid()) to optionPairs
                }
            } catch (e: Exception) {
                Log.e("MetadataCacheService", "Error getting category combo structure", e)
                emptyList()
            }
        }
    }
    
    /**
     * Get category option combos with caching
     */
    suspend fun getCategoryOptionCombos(categoryComboUid: String): List<Pair<String, List<String>>> {
        return categoryOptionCombosCache.getOrPut(categoryComboUid) {
            try {
                // Get the category option combo to find its category combo
                val categoryOptionCombo = d2.categoryModule().categoryOptionCombos()
                    .uid(categoryComboUid)
                    .blockingGet() ?: return@getOrPut emptyList()

                val actualCategoryComboUid = categoryOptionCombo.categoryCombo()
                
                val combos = d2.categoryModule().categoryOptionCombos()
                    .byCategoryComboUid().eq(actualCategoryComboUid!!.uid())
                    .withCategoryOptions()
                    .blockingGet()
                
                combos.map { coc ->
                    val optionUids = coc.categoryOptions()?.map { it.uid() } ?: emptyList()
                    coc.uid() to optionUids
                }
            } catch (e: Exception) {
                Log.e("MetadataCacheService", "Error getting category option combos", e)
                emptyList()
            }
        }
    }
    
    /**
     * Get cached grouping strategies for a dataset
     * Returns null if not cached - caller should compute and store
     */
    fun getGroupingStrategies(datasetId: String): Map<String, List<GroupingStrategy>>? {
        return groupingStrategyCache[datasetId]
    }

    /**
     * Store grouping strategies in cache
     * This persists across ViewModel lifecycle for performance
     */
    fun setGroupingStrategies(datasetId: String, strategies: Map<String, List<GroupingStrategy>>) {
        groupingStrategyCache[datasetId] = strategies
        Log.d("MetadataCacheService", "Cached grouping strategies for dataset $datasetId: ${strategies.size} sections")
    }

    /**
     * Get cached implied category combination for a program section
     * Returns null if not cached - caller should compute and store
     */
    fun getImpliedCategories(programId: String, sectionName: String): com.ash.simpledataentry.domain.model.ImpliedCategoryCombination? {
        val key = "$programId:$sectionName"
        return impliedCategoryCache[key]
    }

    /**
     * Store implied category combination in cache
     * This persists across ViewModel lifecycle for performance
     */
    fun setImpliedCategories(
        programId: String,
        sectionName: String,
        combination: com.ash.simpledataentry.domain.model.ImpliedCategoryCombination
    ) {
        val key = "$programId:$sectionName"
        impliedCategoryCache[key] = combination
        Log.d("MetadataCacheService", "Cached implied categories for $key: ${combination.categories.size} levels, confidence=${combination.confidence}")
    }

    /**
     * Clear all caches (useful when switching accounts or after sync)
     */
    fun clearAllCaches() {
        sectionsCache.clear()
        categoryComboStructureCache.clear()
        categoryOptionCombosCache.clear()
        groupingStrategyCache.clear()
        impliedCategoryCache.clear()
        Log.d("MetadataCacheService", "All metadata caches cleared")
    }
    
    /**
     * Pre-fetch and map data values using the parsed UI structure
     * This bridges the gap between metadata parsing and data retrieval
     */
    private suspend fun preFetchAndMapDataValues(
        datasetId: String,
        period: String,
        orgUnit: String,
        attributeOptionCombo: String
    ): Map<Pair<String, String>, org.hisp.dhis.android.core.datavalue.DataValue> {

        Log.d("MetadataCacheService", "Pre-fetching data values for dataset: $datasetId")

        // First check if we have cached data in Room
        val cachedDataValues = dataValueDao.getValuesForInstance(datasetId, period, orgUnit, attributeOptionCombo)

        if (cachedDataValues.isNotEmpty()) {
            Log.d("MetadataCacheService", "Using cached data values from Room: ${cachedDataValues.size} entries")

            // For cached data, we'll return empty map and let the DataEntryRepositoryImpl
            // handle retrieving data from Room cache directly - this avoids complex SDK mocking
            return emptyMap()
        }

        // If no cached data, fetch fresh data values from SDK
        val rawSdkDataValues = d2.dataValueModule().dataValues()
            .byDataSetUid(datasetId)
            .byPeriod().eq(period)
            .byOrganisationUnitUid().eq(orgUnit)
            .byAttributeOptionComboUid().eq(attributeOptionCombo)
            .blockingGet()

        Log.d("MetadataCacheService", "Raw SDK data values retrieved: ${rawSdkDataValues.size}")

        // Map using the actual keys from SDK data - this fixes the key mismatch issue
        val mappedDataValues = rawSdkDataValues.associateBy {
            (it.dataElement() ?: "") to (it.categoryOptionCombo() ?: "")
        }

        // Store in Room for quick subsequent loading
        storeDataValuesInRoom(datasetId, period, orgUnit, attributeOptionCombo, rawSdkDataValues)

        Log.d("MetadataCacheService", "Data values mapped and cached: ${mappedDataValues.size} entries")

        return mappedDataValues
    }

    /**
     * Store SDK data values in Room database for quick subsequent access
     */
    private suspend fun storeDataValuesInRoom(
        datasetId: String,
        period: String,
        orgUnit: String,
        attributeOptionCombo: String,
        sdkDataValues: List<org.hisp.dhis.android.core.datavalue.DataValue>
    ) {
        // Clear existing data for this instance
        dataValueDao.deleteValuesForInstance(datasetId, period, orgUnit, attributeOptionCombo)

        // Convert SDK data values to Room entities
        val entities = sdkDataValues.map { sdkValue ->
            DataValueEntity(
                datasetId = datasetId,
                period = period,
                orgUnit = orgUnit,
                attributeOptionCombo = attributeOptionCombo,
                dataElement = sdkValue.dataElement() ?: "",
                categoryOptionCombo = sdkValue.categoryOptionCombo() ?: "",
                value = sdkValue.value(),
                comment = sdkValue.comment()
            )
        }

        // Store in Room
        dataValueDao.insertAll(entities)
        Log.d("MetadataCacheService", "Stored ${entities.size} data values in Room database")
    }


    /**
     * Pre-warm caches with commonly used data
     */
    suspend fun preWarmCaches(datasetIds: List<String>) = withContext(Dispatchers.IO) {
        Log.d("MetadataCacheService", "Pre-warming caches for ${datasetIds.size} datasets")

        datasetIds.forEach { datasetId ->
            try {
                getSectionsForDataset(datasetId)
            } catch (e: Exception) {
                Log.w("MetadataCacheService", "Failed to pre-warm cache for dataset $datasetId", e)
            }
        }

        Log.d("MetadataCacheService", "Cache pre-warming completed")
    }
}

/**
 * Data class containing all optimized metadata for EditEntryScreen
 */
data class OptimizedEntryData(
    val sections: List<MetadataCacheService.SectionInfo>,
    val dataElements: Map<String, DataElementEntity>,
    val categoryCombos: Map<String, CategoryComboEntity>,
    val categoryOptionCombos: Map<String, CategoryOptionComboEntity>,
    val orgUnits: Map<String, OrganisationUnitEntity>,
    val sdkDataValues: Map<Pair<String, String>, org.hisp.dhis.android.core.datavalue.DataValue>
)