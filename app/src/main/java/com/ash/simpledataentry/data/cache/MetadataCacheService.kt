package com.ash.simpledataentry.data.cache

import android.util.Log
import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.hisp.dhis.android.core.D2
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for caching and optimizing metadata access across feature stacks.
 * Leverages data already loaded during login to reduce API calls in EditEntryScreen.
 */
@Singleton
class MetadataCacheService @Inject constructor(
    private val sessionManager: SessionManager,
    private val dataElementDao: DataElementDao,
    private val categoryComboDao: CategoryComboDao,
    private val categoryOptionComboDao: CategoryOptionComboDao,
    private val organisationUnitDao: OrganisationUnitDao
) {
    
    private val d2 get() = sessionManager.getD2()!!

    // Cache for sections to avoid repeated API calls
    private val sectionsCache = mutableMapOf<String, List<SectionInfo>>()
    
    // Cache for category combo structures to avoid repeated API calls
    private val categoryComboStructureCache = mutableMapOf<String, List<Pair<String, List<Pair<String, String>>>>>()
    
    // Cache for category option combos
    private val categoryOptionCombosCache = mutableMapOf<String, List<Pair<String, List<String>>>>()
    
    data class SectionInfo(
        val name: String,
        val dataElementUids: List<String>
    )
    
    /**
     * Get optimized data for EditEntryScreen using cached metadata where possible
     */
    suspend fun getOptimizedDataForEntry(
        datasetId: String,
        period: String,
        orgUnit: String,
        attributeOptionCombo: String
    ): OptimizedEntryData = withContext(Dispatchers.IO) {
        
        Log.d("MetadataCacheService", "Getting optimized data for dataset: $datasetId")
        
        // 1. Get sections (cache or API)
        val sections = getSectionsForDataset(datasetId)
        val allDataElementUids = sections.flatMap { it.dataElementUids }.distinct()
        
        // 2. Get data elements from Room (already hydrated during login)
        val dataElements = dataElementDao.getByIds(allDataElementUids).associateBy { it.id }
        
        // 3. Get category combos from Room (already hydrated during login)
        val categoryCombos = categoryComboDao.getAll().associateBy { it.id }
        
        // 4. Get category option combos from Room (already hydrated during login)
        val categoryOptionCombos = categoryOptionComboDao.getAll().associateBy { it.id }
        
        // 5. Get org units from Room (already hydrated during login)
        val orgUnits = organisationUnitDao.getAll().associateBy { it.id }
        
        // 6. Get data values - prefer cached/local data for performance
        // For performance optimization, we'll let the caller handle data values from drafts
        // This eliminates the SDK call that was causing slow loading
        val sdkDataValues = emptyMap<Pair<String, String>, org.hisp.dhis.android.core.datavalue.DataValue>()
        
        Log.d("MetadataCacheService", "Optimized cache load complete: ${sections.size} sections, ${dataElements.size} data elements")
        
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

                categories.mapNotNull { catRef ->
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
     * Clear all caches (useful when switching accounts or after sync)
     */
    fun clearAllCaches() {
        sectionsCache.clear()
        categoryComboStructureCache.clear()
        categoryOptionCombosCache.clear()
        Log.d("MetadataCacheService", "All metadata caches cleared")
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