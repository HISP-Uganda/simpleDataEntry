package com.ash.simpledataentry.data.repositoryImpl

import android.util.Log
import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.domain.model.*
import com.ash.simpledataentry.domain.repository.DatasetsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.combine
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.dataset.DataSet
import org.hisp.dhis.android.core.period.PeriodType
import org.hisp.dhis.android.core.program.ProgramType as SdkProgramType
import java.util.Date
import com.ash.simpledataentry.data.local.DatasetDao
import com.ash.simpledataentry.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DatasetsRepositoryImpl(
    private val sessionManager: SessionManager,
    private val datasetDao: DatasetDao,
    private val context: android.content.Context,
    private val datasetInstancesRepository: com.ash.simpledataentry.domain.repository.DatasetInstancesRepository
) : DatasetsRepository {

    private val TAG = "DatasetsRepositoryImpl"
    private val d2 get() = sessionManager.getD2()!!
    /**
     * Fetches all datasets from Room first, then DHIS2 SDK if needed.
     * Emits the result as a Flow.
     *
     * CRITICAL FIX: Validates cached data belongs to current user before returning it.
     */
    override fun getDatasets(): Flow<List<Dataset>> = flow {
        // SECURITY FIX: Verify cached data belongs to current logged-in user
        val prefs = context.getSharedPreferences("session_prefs", android.content.Context.MODE_PRIVATE)
        val currentUser = prefs.getString("username", null)
        val currentServer = prefs.getString("serverUrl", null)

        // Get cached user from Room metadata (if exists)
        val cachedUser = prefs.getString("cached_datasets_user", null)
        val cachedServer = prefs.getString("cached_datasets_server", null)

        val local = withContext(Dispatchers.IO) { datasetDao.getAll() }

        // Only use cached data if it belongs to the current user
        val isSameUser = currentUser == cachedUser && currentServer == cachedServer

        // If wrong user data is cached, clear it
        if (local.isNotEmpty() && !isSameUser) {
            Log.w(TAG, "Cached datasets belong to different user ($cachedUser), clearing cache...")
            withContext(Dispatchers.IO) {
                datasetDao.clearAll()
            }
        }

        // Emit cached data first if valid (immediate UI response)
        if (local.isNotEmpty() && isSameUser) {
            Log.d(TAG, "Emitting cached datasets for user: $currentUser")
            val datasetsWithCounts = local.map { entity ->
                val instanceCount = datasetInstancesRepository.getDatasetInstanceCount(entity.id)
                entity.toDomainModel().copy(instanceCount = instanceCount)
            }
            emit(datasetsWithCounts)
        }

        // Always check for fresh data from SDK if online
        if (NetworkUtils.isNetworkAvailable(context)) {
            try {
                Log.d(TAG, "Fetching fresh datasets from SDK for user: $currentUser...")
                val d2Instance = d2
                if (d2Instance == null) {
                    Log.e(TAG, "D2 instance is null")
                    // If we already emitted cached data, that's fine
                    if (local.isEmpty() || !isSameUser) {
                        throw Exception("D2 not initialized")
                    }
                    return@flow
                }
                val datasets = withContext(Dispatchers.IO) {
                    d2Instance.dataSetModule()
                        .dataSets()
                        .blockingGet()
                        .map { dataSet ->
                            Log.d(TAG, "Processing dataset: ${dataSet.uid()}")
                            val dataset = dataSet.toDomainModel()
                            val instanceCount = datasetInstancesRepository.getDatasetInstanceCount(dataset.id)
                            dataset.copy(instanceCount = instanceCount)
                        }
                }
                // Update Room and store current user metadata for validation
                withContext(Dispatchers.IO) {
                    datasetDao.clearAll()
                    datasetDao.insertAll(datasets.map { it.toEntity() })
                    // Store current user in SharedPreferences for cache validation
                    prefs.edit().apply {
                        putString("cached_datasets_user", currentUser)
                        putString("cached_datasets_server", currentServer)
                        apply()
                    }
                    Log.d(TAG, "Cached fresh datasets for user: $currentUser@$currentServer")
                }
                emit(datasets)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching fresh datasets", e)
                // Only emit empty list if we didn't already emit cached data
                if (local.isEmpty() || !isSameUser) {
                    emit(emptyList())
                }
            }
        } else {
            // Offline - only emit empty list if we didn't already emit cached data
            if (local.isEmpty() || !isSameUser) {
                Log.d(TAG, "Offline and no valid cached data for user: $currentUser")
                emit(emptyList())
            }
        }
    }

    override suspend fun syncDatasets(): Result<Unit> {
        return try {
            Log.d(TAG, "Syncing dataset DATA (metadata already downloaded during login)")
            // PHASE 3 FIX: Don't retrigger full metadata download - just sync data
            // Metadata is already downloaded by SessionManager during login
            // This method should only download AGGREGATE DATA, not metadata
            withContext(Dispatchers.IO) {
                d2.aggregatedModule().data().blockingDownload()
            }
            Log.d(TAG, "Dataset data sync completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Dataset data sync failed", e)
            Result.failure(e)
        }
    }

    override suspend fun filterDatasets(period: String?, syncStatus: Boolean?): Result<List<Dataset>> {
        return try {
            Log.d(TAG, "Filtering datasets - period: $period, syncStatus: $syncStatus")
            val datasets = withContext(Dispatchers.IO) {
                d2.dataSetModule()
                    .dataSets()
                    .blockingGet()
                    .map { dataSet ->
                        val dataset = dataSet.toDomainModel()
                        val instanceCount = datasetInstancesRepository.getDatasetInstanceCount(dataset.id)
                        dataset.copy(instanceCount = instanceCount)
                    }
            }

            Log.d(TAG, "Found ${datasets.size} datasets after filtering")
            Result.success(datasets)
        } catch (e: Exception) {
            Log.e(TAG, "Error filtering datasets", e)
            Result.failure(e)
        }
    }

    // New tracker program methods implementation
    override fun getTrackerPrograms(): Flow<List<Program>> = flow {
        try {
            val d2Instance = d2
            val programs = d2Instance.programModule().programs()
                .byProgramType().eq(SdkProgramType.WITH_REGISTRATION)
                .blockingGet()
                .map { sdkProgram ->
                    val domainProgram = sdkProgram.toDomainModel()
                    // Get actual enrollment count from repository
                    val enrollmentCount = datasetInstancesRepository.getProgramInstanceCount(
                        domainProgram.id,
                        com.ash.simpledataentry.domain.model.ProgramType.TRACKER
                    )
                    domainProgram.copy(enrollmentCount = enrollmentCount)
                }

            Log.d(TAG, "Retrieved ${programs.size} tracker programs from SDK")
            emit(programs)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching tracker programs", e)
            emit(emptyList())
        }
    }

    override fun getEventPrograms(): Flow<List<Program>> = flow {
        try {
            val d2Instance = d2
            val programs = d2Instance.programModule().programs()
                .byProgramType().eq(SdkProgramType.WITHOUT_REGISTRATION)
                .blockingGet()
                .map { sdkProgram ->
                    val domainProgram = sdkProgram.toDomainModel()
                    // Get actual event count from repository
                    val eventCount = datasetInstancesRepository.getProgramInstanceCount(
                        domainProgram.id,
                        com.ash.simpledataentry.domain.model.ProgramType.EVENT
                    )
                    domainProgram.copy(enrollmentCount = eventCount)
                }

            Log.d(TAG, "Retrieved ${programs.size} event programs from SDK")
            emit(programs)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching event programs", e)
            emit(emptyList())
        }
    }

    override fun getAllPrograms(): Flow<List<ProgramItem>> = combine(
        getDatasets(),
        getTrackerPrograms(),
        getEventPrograms()
    ) { datasets, trackerPrograms, eventPrograms ->
        val programItems = mutableListOf<ProgramItem>()

        // Add datasets as program items
        programItems.addAll(datasets.map { ProgramItem.DatasetProgram(it) })

        // Add tracker programs
        programItems.addAll(trackerPrograms.map { ProgramItem.TrackerProgram(it) })

        // Add event programs
        programItems.addAll(eventPrograms.map { ProgramItem.EventProgram(it) })

        Log.d(TAG, "Combined programs: ${datasets.size} datasets, ${trackerPrograms.size} tracker, ${eventPrograms.size} event")
        programItems
    }

    override suspend fun syncPrograms(): Result<Unit> {
        return try {
            Log.d(TAG, "Syncing program DATA (metadata already downloaded during login)")
            // PHASE 3 FIX: Don't retrigger full metadata download - just sync tracker/event data
            // Metadata is already downloaded by SessionManager during login
            // This method should only download TRACKER/EVENT DATA, not metadata
            withContext(Dispatchers.IO) {
                d2.trackedEntityModule().trackedEntityInstanceDownloader()
                    .limit(500) // Reasonable limit to prevent timeouts
                    .blockingDownload()
            }
            Log.d(TAG, "Program data sync completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Program data sync failed", e)
            Result.failure(e)
        }
    }

    override suspend fun filterPrograms(
        programType: ProgramType?,
        searchQuery: String?
    ): Result<List<ProgramItem>> {
        return try {
            val allPrograms = mutableListOf<ProgramItem>()

            // Get filtered programs based on type
            when (programType) {
                ProgramType.DATASET -> {
                    val datasets = if (searchQuery.isNullOrBlank()) {
                        getDatasets()
                    } else {
                        flow {
                            getDatasets().collect { datasets ->
                                emit(datasets.filter {
                                    it.name.contains(searchQuery, ignoreCase = true) ||
                                    it.description?.contains(searchQuery, ignoreCase = true) == true
                                })
                            }
                        }
                    }
                    datasets.collect { allPrograms.addAll(it.map { ProgramItem.DatasetProgram(it) }) }
                }
                ProgramType.TRACKER -> {
                    getTrackerPrograms().collect { programs ->
                        val filtered = if (searchQuery.isNullOrBlank()) {
                            programs
                        } else {
                            programs.filter {
                                it.name.contains(searchQuery, ignoreCase = true) ||
                                it.description?.contains(searchQuery, ignoreCase = true) == true
                            }
                        }
                        allPrograms.addAll(filtered.map { ProgramItem.TrackerProgram(it) })
                    }
                }
                ProgramType.EVENT -> {
                    getEventPrograms().collect { programs ->
                        val filtered = if (searchQuery.isNullOrBlank()) {
                            programs
                        } else {
                            programs.filter {
                                it.name.contains(searchQuery, ignoreCase = true) ||
                                it.description?.contains(searchQuery, ignoreCase = true) == true
                            }
                        }
                        allPrograms.addAll(filtered.map { ProgramItem.EventProgram(it) })
                    }
                }
                ProgramType.ALL, null -> {
                    getAllPrograms().collect { programs ->
                        val filtered = if (searchQuery.isNullOrBlank()) {
                            programs
                        } else {
                            programs.filter {
                                it.name.contains(searchQuery, ignoreCase = true) ||
                                it.description?.contains(searchQuery, ignoreCase = true) == true
                            }
                        }
                        allPrograms.addAll(filtered)
                    }
                }
            }

            Result.success(allPrograms)
        } catch (e: Exception) {
            Log.e(TAG, "Error filtering programs", e)
            Result.failure(e)
        }
    }

}

/**
 * Extension function converting the DHIS2 DataSet object into our domain Dataset model.
 */
fun DataSet.toDomainModel(): Dataset {
    val datasetStyle = style()?.let { sdkStyle ->
        Log.d("DatasetsRepo", "Dataset ${uid()}: Found style - icon: ${sdkStyle.icon()}, color: ${sdkStyle.color()}")
        com.ash.simpledataentry.domain.model.DatasetStyle(
            icon = sdkStyle.icon(),
            color = sdkStyle.color()
        )
    } ?: run {
        Log.d("DatasetsRepo", "Dataset ${uid()}: No style found")
        null
    }

    return Dataset(
        id = uid(),
        name = displayName() ?: name() ?: "Unnamed Dataset",
        description = description() ?: "",
        periodType = when (periodType()?.name) {
            "Daily" -> PeriodType.Daily
            "Weekly" -> PeriodType.Weekly
            "Monthly" -> PeriodType.Monthly
            "Yearly" -> PeriodType.Yearly
            else -> PeriodType.Monthly // Default fallback, adjust as needed
        },
        style = datasetStyle
    )
}

// Extension functions for mapping
fun Dataset.toEntity(): com.ash.simpledataentry.data.local.DatasetEntity =
    com.ash.simpledataentry.data.local.DatasetEntity(
        id = id,
        name = name,
        description = description.toString(),
        periodType = periodType?.name ?: "Monthly",
        styleIcon = style?.icon,
        styleColor = style?.color
    )

fun com.ash.simpledataentry.data.local.DatasetEntity.toDomainModel(): Dataset =
    Dataset(
        id = id,
        name = name,
        description = description,
        periodType = when (periodType) {
            "Daily" -> PeriodType.Daily
            "Weekly" -> PeriodType.Weekly
            "Monthly" -> PeriodType.Monthly
            "Yearly" -> PeriodType.Yearly
            else -> PeriodType.Monthly
        },
        style = if (styleIcon != null || styleColor != null) {
            com.ash.simpledataentry.domain.model.DatasetStyle(
                icon = styleIcon,
                color = styleColor
            )
        } else null
    )


// Extension function to convert DHIS2 SDK Program to domain model
private fun org.hisp.dhis.android.core.program.Program.toDomainModel(): Program {
    val programStyle = style()?.let { sdkStyle ->
        ProgramStyle(
            icon = sdkStyle.icon(),
            color = sdkStyle.color()
        )
    }

    return Program(
        id = uid(),
        name = displayName() ?: name() ?: "Unnamed Program",
        description = description(),
        programType = when (programType()) {
            SdkProgramType.WITH_REGISTRATION -> ProgramType.TRACKER
            SdkProgramType.WITHOUT_REGISTRATION -> ProgramType.EVENT
            else -> ProgramType.EVENT
        },
        trackedEntityType = trackedEntityType()?.uid(),
        categoryCombo = categoryCombo()?.uid(),
        style = programStyle,
        enrollmentDateLabel = enrollmentDateLabel(),
        incidentDateLabel = incidentDateLabel(),
        displayIncidentDate = displayIncidentDate() ?: false,
        onlyEnrollOnce = onlyEnrollOnce() ?: false,
        selectEnrollmentDatesInFuture = selectEnrollmentDatesInFuture() ?: false,
        selectIncidentDatesInFuture = selectIncidentDatesInFuture() ?: false,
        featureType = when (featureType()?.name) {
            "POINT" -> FeatureType.POINT
            "POLYGON" -> FeatureType.POLYGON
            "MULTI_POLYGON" -> FeatureType.MULTI_POLYGON
            else -> FeatureType.NONE
        },
        minAttributesRequiredToSearch = minAttributesRequiredToSearch() ?: 1,
        maxTeiCountToReturn = maxTeiCountToReturn() ?: 50,
        // Note: enrollment count would need to be fetched separately
        enrollmentCount = 0
    )
}