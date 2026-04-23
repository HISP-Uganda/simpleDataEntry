package com.ash.simpledataentry.data.cache

import android.content.Context
import android.util.Base64
import android.util.Log
import com.ash.simpledataentry.data.DatabaseProvider
import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.data.local.*
import com.ash.simpledataentry.data.repositoryImpl.SavedAccountRepository
import com.ash.simpledataentry.data.sync.D2SdkOperationLocks
import com.ash.simpledataentry.domain.model.GroupingStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import org.hisp.dhis.android.core.D2
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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
    private val databaseProvider: DatabaseProvider,
    private val savedAccountRepository: SavedAccountRepository,
    private val context: Context
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

    private val prefs by lazy {
        context.getSharedPreferences("metadata_cache", Context.MODE_PRIVATE)
    }

    private fun sectionsCacheKey(datasetId: String) = "sections_$datasetId"

    private fun loadSectionsFromDisk(datasetId: String): List<SectionInfo>? {
        val raw = prefs.getString(sectionsCacheKey(datasetId), null) ?: return null
        return try {
            val json = JSONArray(raw)
            buildList {
                for (i in 0 until json.length()) {
                    val item = json.getJSONObject(i)
                    val name = item.optString("name")
                    val uids = item.optJSONArray("uids")
                    val list = buildList {
                        if (uids != null) {
                            for (j in 0 until uids.length()) {
                                add(uids.getString(j))
                            }
                        }
                    }
                    if (name.isNotBlank() && list.isNotEmpty()) {
                        add(SectionInfo(name, list))
                    }
                }
            }.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w("MetadataCacheService", "Failed to parse cached sections for $datasetId", e)
            null
        }
    }

    private fun persistSectionsToDisk(datasetId: String, sections: List<SectionInfo>) {
        if (sections.isEmpty()) return
        try {
            val json = JSONArray()
            sections.forEach { section ->
                val obj = JSONObject()
                obj.put("name", section.name)
                val uids = JSONArray()
                section.dataElementUids.forEach { uids.put(it) }
                obj.put("uids", uids)
                json.put(obj)
            }
            prefs.edit().putString(sectionsCacheKey(datasetId), json.toString()).apply()
        } catch (e: Exception) {
            Log.w("MetadataCacheService", "Failed to persist sections for $datasetId", e)
        }
    }
    
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
        val totalInRoom = dataElementDao.count()
        val dataElements = dataElementDao.getByIds(allDataElementUids).associateBy { it.id }

        // DEBUG: Log what we found
        Log.d("MetadataCacheService", "Found ${dataElements.size} data elements in Room")
        if (dataElements.isEmpty() && allDataElementUids.isNotEmpty()) {
            // Check total data elements in Room
            val totalInRoom = dataElementDao.getAll().size
            Log.w("MetadataCacheService", "WARNING: Room has $totalInRoom total data elements but none match the requested UIDs!")
        }

        // 3. Get category combos from Room (already hydrated during login)
        val neededComboIds = dataElements.values.mapNotNull { it.categoryComboId }.distinct()
        val categoryCombos = if (neededComboIds.isEmpty()) {
            emptyMap()
        } else {
            categoryComboDao.getByIds(neededComboIds).associateBy { it.id }
        }

        // 4. Get category option combos from Room (already hydrated during login)
        val categoryOptionCombos = if (neededComboIds.isEmpty()) {
            emptyMap()
        } else {
            categoryOptionComboDao.getByCategoryComboIds(neededComboIds).associateBy { it.id }
        }

        // 5. Get org units from Room (already hydrated during login)
        val orgUnits = organisationUnitDao.getAll().associateBy { it.id }

        // 6. NEW STEP: Pre-fetch and map data values using the parsed UI structure
        val sdkDataValues = preFetchAndMapDataValues(datasetId, period, orgUnit, attributeOptionCombo)

        Log.d(
            "MetadataCacheService",
            "Optimized data complete: ${sections.size} sections, ${dataElements.size}/${totalInRoom} data elements, ${sdkDataValues.size} data values"
        )

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
        val refreshedCount = refreshDataValues(datasetId, period, orgUnit, attributeOptionCombo)
        Log.d("MetadataCacheService", "Fresh data refresh finished with $refreshedCount values")
        getOptimizedDataForEntry(datasetId, period, orgUnit, attributeOptionCombo)
    }

    /**
     * Force-refresh data values for a specific dataset instance and cache them in Room.
     * Returns the number of values fetched from the server.
     */
    suspend fun refreshDataValues(
        datasetId: String,
        period: String,
        orgUnit: String,
        attributeOptionCombo: String
    ): Int = withContext(Dispatchers.IO) {
        Log.d(
            "MetadataCacheService",
            "DVTRACE refreshDataValues start dataset=$datasetId period=$period orgUnit=$orgUnit requestedAOC=$attributeOptionCombo"
        )
        val defaultCombo = d2.categoryModule().categoryOptionCombos()
            .byDisplayName().eq("default")
            .one()
            .blockingGet()
            ?.uid()
            .orEmpty()

        val resolvedAttr = if (attributeOptionCombo.isBlank()) defaultCombo else attributeOptionCombo
        Log.d(
            "MetadataCacheService",
            "DVTRACE refreshDataValues resolvedAOC=$resolvedAttr defaultAOC=$defaultCombo"
        )

        fun logSdkInstanceDiagnostics(stage: String) {
            try {
                val dataSet = d2.dataSetModule().dataSets()
                    .uid(datasetId)
                    .blockingGet()
                val periodType = dataSet?.periodType()?.name ?: "unknown"

                val dataSetInstance = d2.dataSetModule()
                    .dataSetInstances()
                    .dataSetInstance(datasetId, period, orgUnit, resolvedAttr)
                    .blockingGet()

                val localPeriods = d2.dataValueModule().dataValues()
                    .byDataSetUid(datasetId)
                    .byOrganisationUnitUid().eq(orgUnit)
                    .blockingGet()
                    .mapNotNull { it.period() }
                    .distinct()
                    .sorted()

                val exactKeyCount = d2.dataValueModule().dataValues()
                    .byDataSetUid(datasetId)
                    .byPeriod().eq(period)
                    .byOrganisationUnitUid().eq(orgUnit)
                    .byAttributeOptionComboUid().eq(resolvedAttr)
                    .blockingCount()

                Log.d(
                    "MetadataCacheService",
                    "DVTRACE sdkDiag[$stage] dataset=$datasetId periodType=$periodType exactInstanceExists=${dataSetInstance != null} exactInstanceCompleted=${dataSetInstance?.completed()} exactKeyCount=$exactKeyCount"
                )
                Log.d(
                    "MetadataCacheService",
                    "DVTRACE sdkDiag[$stage] localPeriodsForDatasetOrgUnit count=${localPeriods.size} values=${localPeriods.take(20)}"
                )
            } catch (e: Exception) {
                Log.w(
                    "MetadataCacheService",
                    "DVTRACE sdkDiag[$stage] failed: ${e.message}"
                )
            }
        }

        logSdkInstanceDiagnostics("beforeFetch")

        fun fetchValues(attr: String) = d2.dataValueModule().dataValues()
            .byDataSetUid(datasetId)
            .byPeriod().eq(period)
            .byOrganisationUnitUid().eq(orgUnit)
            .byAttributeOptionComboUid().eq(attr)
            .blockingGet()

        var rawSdkDataValues = D2SdkOperationLocks.dataValueAndAggregateMutex.withLock {
            try {
                // android-core:1.13.1 does not expose a data-value scoped downloader.
                // Aggregated downloader is the supported server fetch path.
                d2.aggregatedModule().data().blockingDownload()
                fetchValues(resolvedAttr)
            } catch (e: Exception) {
                Log.w(
                    "MetadataCacheService",
                    "Data-value download failed for requested AOC=$resolvedAttr, falling back to local cache",
                    e
                )
                fetchValues(resolvedAttr)
            }
        }
        Log.d(
            "MetadataCacheService",
            "DVTRACE fetch requestedAOC=$resolvedAttr returned=${rawSdkDataValues.size}"
        )
        logSdkInstanceDiagnostics("afterFetch")

        if (rawSdkDataValues.isEmpty() && resolvedAttr != defaultCombo && defaultCombo.isNotBlank()) {
            val fallbackValues = D2SdkOperationLocks.dataValueAndAggregateMutex.withLock {
                fetchValues(defaultCombo)
            }
            Log.d(
                "MetadataCacheService",
                "DVTRACE fetch defaultAOC=$defaultCombo returned=${fallbackValues.size}"
            )
            if (fallbackValues.isNotEmpty()) {
                storeDataValuesInRoom(datasetId, period, orgUnit, defaultCombo, fallbackValues)
                return@withContext fallbackValues.size
            }
        }

        if (rawSdkDataValues.isEmpty()) {
            val directApiFallback = fetchDataValuesFromApiDirect(
                datasetId = datasetId,
                period = period,
                orgUnit = orgUnit,
                attributeOptionCombo = resolvedAttr
            )
            if (directApiFallback.isNotEmpty()) {
                Log.d(
                    "MetadataCacheService",
                    "DVTRACE directApi fallback returned=${directApiFallback.size} for requestedAOC=$resolvedAttr"
                )
                storeDirectApiDataValuesInRoom(
                    datasetId = datasetId,
                    period = period,
                    orgUnit = orgUnit,
                    attributeOptionCombo = resolvedAttr,
                    apiDataValues = directApiFallback
                )
                return@withContext directApiFallback.size
            }

            // Last resort: probe all attribute option combos on the dataset category combo
            // and pick the first one containing values for this instance.
            try {
                val dataSet = d2.dataSetModule().dataSets()
                    .uid(datasetId)
                    .blockingGet()
                val dataSetCategoryComboUid = dataSet?.categoryCombo()?.uid().orEmpty()
                if (dataSetCategoryComboUid.isNotBlank()) {
                    val datasetAocs = d2.categoryModule().categoryOptionCombos()
                        .byCategoryComboUid().eq(dataSetCategoryComboUid)
                        .blockingGet()
                        .map { it.uid() }
                        .distinct()
                    for (candidateAoc in datasetAocs) {
                        if (candidateAoc == resolvedAttr || candidateAoc == defaultCombo) continue
                        val candidateValues = D2SdkOperationLocks.dataValueAndAggregateMutex.withLock {
                            fetchValues(candidateAoc)
                        }
                        Log.d(
                            "MetadataCacheService",
                            "DVTRACE probeAOC candidate=$candidateAoc returned=${candidateValues.size}"
                        )
                        if (candidateValues.isNotEmpty()) {
                            Log.d(
                                "MetadataCacheService",
                                "Resolved instance values via alternate AOC $candidateAoc (requested=$resolvedAttr)"
                            )
                            storeDataValuesInRoom(datasetId, period, orgUnit, candidateAoc, candidateValues)
                            return@withContext candidateValues.size
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MetadataCacheService", "AOC probing failed while refreshing data values", e)
            }
        }

        storeDataValuesInRoom(datasetId, period, orgUnit, resolvedAttr, rawSdkDataValues)
        Log.d(
            "MetadataCacheService",
            "DVTRACE refreshDataValues end dataset=$datasetId period=$period orgUnit=$orgUnit storedAOC=$resolvedAttr count=${rawSdkDataValues.size}"
        )
        rawSdkDataValues.size
    }

    private data class DirectApiDataValue(
        val dataElement: String,
        val categoryOptionCombo: String,
        val attributeOptionCombo: String,
        val value: String?,
        val comment: String?
    )

    private suspend fun fetchDataValuesFromApiDirect(
        datasetId: String,
        period: String,
        orgUnit: String,
        attributeOptionCombo: String
    ): List<DirectApiDataValue> = withContext(Dispatchers.IO) {
        val sessionPrefs = context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
        val sessionUsername = sessionPrefs.getString("username", null)
        val sessionServerUrl = sessionPrefs.getString("serverUrl", null)

        val activeAccount = savedAccountRepository.getActiveAccount()
            ?: if (!sessionUsername.isNullOrBlank() && !sessionServerUrl.isNullOrBlank()) {
                savedAccountRepository.getAccountByCredentials(
                    serverUrl = sessionServerUrl,
                    username = sessionUsername
                )
            } else {
                null
            }

        if (activeAccount == null) {
            Log.w(
                "MetadataCacheService",
                "DVTRACE directApi skipped: no saved account credentials for current session (sessionUser=$sessionUsername, sessionServer=$sessionServerUrl)"
            )
            return@withContext emptyList()
        }
        val password = savedAccountRepository.getDecryptedPassword(activeAccount.id)
        if (password.isNullOrBlank()) {
            Log.w("MetadataCacheService", "DVTRACE directApi skipped: no decrypted password for active account")
            return@withContext emptyList()
        }

        val encodedDataset = URLEncoder.encode(datasetId, "UTF-8")
        val encodedPeriod = URLEncoder.encode(period, "UTF-8")
        val encodedOrgUnit = URLEncoder.encode(orgUnit, "UTF-8")
        val encodedAoc = URLEncoder.encode(attributeOptionCombo, "UTF-8")
        val fields = URLEncoder.encode(
            "dataSet,period,orgUnit,dataValues[dataElement,period,orgUnit,categoryOptionCombo,attributeOptionCombo,value,comment]",
            "UTF-8"
        )

        val serverBase = activeAccount.serverUrl.trimEnd('/')
        val url = URL(
            "$serverBase/api/dataValueSets.json" +
                "?dataSet=$encodedDataset" +
                "&period=$encodedPeriod" +
                "&orgUnit=$encodedOrgUnit" +
                "&attributeOptionCombo=$encodedAoc" +
                "&fields=$fields"
        )

        var connection: HttpURLConnection? = null
        try {
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 30000
                readTimeout = 60000
                setRequestProperty("Accept", "application/json")
                val credentials = "${activeAccount.username}:$password"
                val authHeader = Base64.encodeToString(
                    credentials.toByteArray(StandardCharsets.UTF_8),
                    Base64.NO_WRAP
                )
                setRequestProperty("Authorization", "Basic $authHeader")
            }

            val code = connection.responseCode
            val body = try {
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            } catch (e: Exception) {
                ""
            }

            Log.d(
                "MetadataCacheService",
                "DVTRACE directApi GET dataValueSets code=$code dataset=$datasetId period=$period orgUnit=$orgUnit aoc=$attributeOptionCombo bodyLen=${body.length}"
            )

            if (code !in 200..299 || body.isBlank()) {
                return@withContext emptyList()
            }

            val json = JSONObject(body)
            val dataValues = json.optJSONArray("dataValues") ?: JSONArray()
            val parsed = buildList {
                for (i in 0 until dataValues.length()) {
                    val item = dataValues.optJSONObject(i) ?: continue
                    val itemAoc = item.optString("attributeOptionCombo")
                    if (itemAoc.isNotBlank() && itemAoc != attributeOptionCombo) continue
                    add(
                        DirectApiDataValue(
                            dataElement = item.optString("dataElement"),
                            categoryOptionCombo = item.optString("categoryOptionCombo"),
                            attributeOptionCombo = itemAoc,
                            value = item.optString("value").takeIf { item.has("value") },
                            comment = item.optString("comment").takeIf { item.has("comment") && !item.isNull("comment") }
                        )
                    )
                }
            }

            Log.d(
                "MetadataCacheService",
                "DVTRACE directApi parsed dataValues=${parsed.size} dataset=$datasetId period=$period orgUnit=$orgUnit aoc=$attributeOptionCombo"
            )
            parsed.take(5).forEach { value ->
                Log.d(
                    "MetadataCacheService",
                    "DVTRACE directApiSample de=${value.dataElement} coc=${value.categoryOptionCombo} value=${value.value}"
                )
            }
            parsed
        } catch (e: Exception) {
            Log.w(
                "MetadataCacheService",
                "DVTRACE directApi fallback failed for dataset=$datasetId period=$period orgUnit=$orgUnit aoc=$attributeOptionCombo: ${e.message}",
                e
            )
            emptyList()
        } finally {
            connection?.disconnect()
        }
    }

    private suspend fun storeDirectApiDataValuesInRoom(
        datasetId: String,
        period: String,
        orgUnit: String,
        attributeOptionCombo: String,
        apiDataValues: List<DirectApiDataValue>
    ) {
        Log.d(
            "MetadataCacheService",
            "DVTRACE storeDirectApiDataValuesInRoom start dataset=$datasetId period=$period orgUnit=$orgUnit aoc=$attributeOptionCombo incoming=${apiDataValues.size}"
        )
        dataValueDao.deleteValuesForInstance(datasetId, period, orgUnit, attributeOptionCombo)
        val entities = apiDataValues.map { item ->
            DataValueEntity(
                datasetId = datasetId,
                period = period,
                orgUnit = orgUnit,
                attributeOptionCombo = attributeOptionCombo,
                dataElement = item.dataElement,
                categoryOptionCombo = item.categoryOptionCombo,
                value = item.value,
                comment = item.comment
            )
        }
        dataValueDao.insertAll(entities)
        Log.d(
            "MetadataCacheService",
            "DVTRACE storeDirectApiDataValuesInRoom done dataset=$datasetId period=$period orgUnit=$orgUnit aoc=$attributeOptionCombo rows=${entities.size}"
        )
    }
    
    /**
     * Get sections for a dataset with caching
     */
    private suspend fun getSectionsForDataset(datasetId: String): List<SectionInfo> {
        sectionsCache[datasetId]?.let { return it }
        loadSectionsFromDisk(datasetId)?.let { cached ->
            sectionsCache[datasetId] = cached
            return cached
        }

        return sectionsCache.getOrPut(datasetId) {
            val sections = d2.dataSetModule().sections()
                .withDataElements()
                .byDataSetUid().eq(datasetId)
                .blockingGet()
                .sortedWith(
                    compareBy<org.hisp.dhis.android.core.dataset.Section> { it.sortOrder() ?: Int.MAX_VALUE }
                        .thenBy { it.displayName() ?: "" }
                )

            if (sections.isEmpty()) {
                val dataSet = d2.dataSetModule().dataSets()
                    .withDataSetElements()
                    .uid(datasetId)
                    .blockingGet()

                val dataElementUids = dataSet?.dataSetElements()
                    ?.mapNotNull { it.dataElement()?.uid() }
                    ?.distinct()
                    .orEmpty()

                if (dataElementUids.isEmpty()) {
                    emptyList()
                } else {
                    listOf(
                        SectionInfo(
                            name = "Default Section",
                            dataElementUids = dataElementUids
                        )
                    )
                }
            } else {
                val mapped = sections.map { section ->
                    SectionInfo(
                        name = section.displayName() ?: "Unassigned",
                        dataElementUids = section.dataElements()?.map { it.uid() } ?: emptyList()
                    )
                }
                persistSectionsToDisk(datasetId, mapped)
                mapped
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
                Log.d("MetadataCacheService", "Category combo structure request for comboUid=$categoryComboUid")
                // Get the category option combo to find its category combo
                val categoryOptionCombo = d2.categoryModule().categoryOptionCombos()
                    .uid(categoryComboUid)
                    .blockingGet() ?: return@getOrPut emptyList()

                val actualCategoryComboUid = categoryOptionCombo.categoryCombo()
                Log.d(
                    "MetadataCacheService",
                    "Resolved categoryComboUid=${actualCategoryComboUid?.uid()} from categoryOptionCombo=${categoryOptionCombo.uid()}"
                )

                // Get the category combo with its categories
                val categoryCombo = d2.categoryModule().categoryCombos()
                    .withCategories()
                    .uid(actualCategoryComboUid!!.uid())
                    .blockingGet() ?: return@getOrPut emptyList()

                val categories = categoryCombo.categories() ?: emptyList()
                Log.d(
                    "MetadataCacheService",
                    "CategoryCombo ${categoryCombo.displayName() ?: categoryCombo.uid()} has ${categories.size} categories"
                )
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
                    if (optionPairs.isNotEmpty()) {
                        Log.d(
                            "MetadataCacheService",
                            "Category ${category.displayName()} options: ${optionPairs.joinToString { it.second }}"
                        )
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
     * Get category combo structure using a categoryCombo uid directly (no COC indirection).
     */
    suspend fun getCategoryComboStructureByComboUid(
        categoryComboUid: String
    ): List<Pair<String, List<Pair<String, String>>>> = withContext(Dispatchers.IO) {
        try {
            Log.d("MetadataCacheService", "Loading category combo structure by combo uid: $categoryComboUid")
            val categoryCombo = d2.categoryModule().categoryCombos()
                .withCategories()
                .uid(categoryComboUid)
                .blockingGet() ?: return@withContext emptyList()

            val categories = categoryCombo.categories() ?: emptyList()
            Log.d(
                "MetadataCacheService",
                "Combo ${categoryCombo.displayName() ?: categoryCombo.uid()} has ${categories.size} categories"
            )
            if (categories.isEmpty()) return@withContext emptyList()

            categories.mapNotNull { catRef ->
                val category = d2.categoryModule().categories()
                    .withCategoryOptions()
                    .uid(catRef.uid())
                    .blockingGet() ?: return@mapNotNull null

                val options = category.categoryOptions() ?: emptyList()
                val optionPairs = options.map { optRef ->
                    optRef.uid() to (optRef.displayName() ?: optRef.uid())
                }
                Log.d(
                    "MetadataCacheService",
                    "Category ${category.displayName() ?: category.uid()} has ${optionPairs.size} options"
                )
                (category.displayName() ?: category.uid()) to optionPairs
            }
        } catch (e: Exception) {
            Log.e("MetadataCacheService", "Error getting category combo structure by combo uid", e)
            emptyList()
        }
    }

    /**
     * Fetch category option combos directly from SDK by category combo uid.
     */
    suspend fun fetchCategoryOptionCombosByComboUid(
        categoryComboUid: String
    ): List<com.ash.simpledataentry.data.local.CategoryOptionComboEntity> = withContext(Dispatchers.IO) {
        try {
            val combos = d2.categoryModule().categoryOptionCombos()
                .byCategoryComboUid().eq(categoryComboUid)
                .withCategoryOptions()
                .blockingGet()

            combos.map { coc ->
                com.ash.simpledataentry.data.local.CategoryOptionComboEntity(
                    id = coc.uid(),
                    name = coc.displayName() ?: coc.uid(),
                    categoryComboId = coc.categoryCombo()?.uid() ?: categoryComboUid,
                    optionUids = coc.categoryOptions()?.joinToString(",") { opt -> opt.uid() } ?: ""
                )
            }
        } catch (e: Exception) {
            Log.e("MetadataCacheService", "Error fetching category option combos by combo uid", e)
            emptyList()
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
        prefs.edit().clear().apply()
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

        // Keep a snapshot of Room cache as fallback only.
        val cachedDataValues = dataValueDao.getValuesForInstance(datasetId, period, orgUnit, attributeOptionCombo)

        // Always read from SDK local store first. This reflects latest downloaded server values
        // and avoids stale/partial Room snapshots masking existing web data.
        val rawSdkDataValues = d2.dataValueModule().dataValues()
            .byDataSetUid(datasetId)
            .byPeriod().eq(period)
            .byOrganisationUnitUid().eq(orgUnit)
            .byAttributeOptionComboUid().eq(attributeOptionCombo)
            .blockingGet()

        Log.d("MetadataCacheService", "Raw SDK data values retrieved: ${rawSdkDataValues.size}")

        if (rawSdkDataValues.isEmpty()) {
            Log.d(
                "MetadataCacheService",
                "No SDK values for instance; using Room fallback entries=${cachedDataValues.size}"
            )
            return emptyMap()
        }

        // Map using the actual keys from SDK data - this fixes the key mismatch issue
        val mappedDataValues = rawSdkDataValues.associateBy {
            (it.dataElement() ?: "") to (it.categoryOptionCombo() ?: "")
        }

        // Keep app Room mirror aligned with SDK local storage.
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
        Log.d(
            "MetadataCacheService",
            "DVTRACE storeDataValuesInRoom start dataset=$datasetId period=$period orgUnit=$orgUnit aoc=$attributeOptionCombo incoming=${sdkDataValues.size}"
        )
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
        val nonEmptyCount = entities.count { !it.value.isNullOrBlank() }
        Log.d(
            "MetadataCacheService",
            "DVTRACE storeDataValuesInRoom done dataset=$datasetId period=$period orgUnit=$orgUnit aoc=$attributeOptionCombo rows=${entities.size} nonEmpty=$nonEmptyCount"
        )
        entities.take(5).forEach { entity ->
            Log.d(
                "MetadataCacheService",
                "DVTRACE roomSample de=${entity.dataElement} coc=${entity.categoryOptionCombo} value=${entity.value}"
            )
        }
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
