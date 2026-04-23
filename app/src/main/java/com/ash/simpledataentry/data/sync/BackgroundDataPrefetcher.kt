package com.ash.simpledataentry.data.sync

import android.util.Log
import com.ash.simpledataentry.data.cache.MetadataCacheService
import com.ash.simpledataentry.data.local.DatasetDao
import com.ash.simpledataentry.data.SessionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for background data prefetching to improve app performance.
 * Pre-loads commonly accessed metadata and data values in the background.
 */
@Singleton
class BackgroundDataPrefetcher @Inject constructor(
    private val sessionManager: SessionManager,
    private val metadataCacheService: MetadataCacheService,
    private val datasetDao: DatasetDao
) {
    
    private val d2 get() = sessionManager.getD2()
    private var prefetchJob: Job? = null
    private var lastPrefetchedDatasetIds: List<String> = emptyList()
    private var lastPrefetchTimeMs: Long = 0L
    
    /**
     * Start background prefetching after successful login
     */
    fun startPrefetching(topDatasetCount: Int = 3) {
        prefetchJob?.cancel()
        prefetchJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                Log.d("BackgroundDataPrefetcher", "Starting background metadata prefetching...")

                val datasets = datasetDao.getAll().first()
                val datasetIds = datasets.map { it.id }.take(topDatasetCount)

                if (datasetIds.isEmpty()) {
                    Log.d("BackgroundDataPrefetcher", "No datasets available for prefetching")
                    return@launch
                }

                val now = System.currentTimeMillis()
                val withinCooldown = now - lastPrefetchTimeMs < 15 * 60 * 1000
                if (withinCooldown && datasetIds == lastPrefetchedDatasetIds) {
                    Log.d("BackgroundDataPrefetcher", "Skipping prefetch (recently completed)")
                    return@launch
                }

                Log.d("BackgroundDataPrefetcher", "Pre-warming caches for ${datasetIds.size} datasets")
                metadataCacheService.preWarmCaches(datasetIds)

                lastPrefetchedDatasetIds = datasetIds
                lastPrefetchTimeMs = now

                Log.d("BackgroundDataPrefetcher", "Background prefetching completed successfully")
            } catch (e: Exception) {
                Log.w("BackgroundDataPrefetcher", "Background prefetching failed", e)
            }
        }
    }

    /**
     * Preload metadata (and a small slice of data values) for a selected dataset.
     * Used to speed up form preparation after user selection.
     */
    fun prefetchForDataset(datasetId: String) {
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val now = System.currentTimeMillis()
                if (lastPrefetchedDatasetIds == listOf(datasetId) && now - lastPrefetchTimeMs < 5 * 60 * 1000) {
                    Log.d("BackgroundDataPrefetcher", "Skipping dataset prefetch (recently completed)")
                    return@launch
                }
                Log.d("BackgroundDataPrefetcher", "Prefetching data for dataset $datasetId")
                metadataCacheService.preWarmCaches(listOf(datasetId))
                prefetchRecentDataValues(listOf(datasetId))
                lastPrefetchedDatasetIds = listOf(datasetId)
                lastPrefetchTimeMs = now
            } catch (e: Exception) {
                Log.w("BackgroundDataPrefetcher", "Dataset prefetch failed for $datasetId", e)
            }
        }
    }
    
    /**
     * Stop background prefetching
     */
    fun stopPrefetching() {
        prefetchJob?.cancel()
        prefetchJob = null
        Log.d("BackgroundDataPrefetcher", "Background prefetching stopped")
    }
    
    /**
     * Pre-fetch recent data values for commonly used datasets
     */
    private suspend fun prefetchRecentDataValues(datasetIds: List<String>) {
        val d2Instance = d2 ?: return
        
        Log.d("BackgroundDataPrefetcher", "Pre-fetching recent data values for ${datasetIds.size} datasets")
        
        try {
            // Get recent periods (last 3 months worth of data)
            val recentPeriods = d2Instance.periodModule().periodHelper()
                .getPeriodsForDataSet(datasetIds.firstOrNull() ?: return)
                .blockingGet()
                .take(3) // Last 3 periods
            
            // Get user's org units
            val userOrgUnits = d2Instance.organisationUnitModule().organisationUnits()
                .byOrganisationUnitScope(org.hisp.dhis.android.core.organisationunit.OrganisationUnit.Scope.SCOPE_DATA_CAPTURE)
                .blockingGet()
                .take(2) // Limit to first 2 org units
            
            val prefetchJobs = datasetIds.flatMap { datasetId ->
                recentPeriods.flatMap { period ->
                    userOrgUnits.map { orgUnit -> Triple(datasetId, period, orgUnit) }
                }
            }

            prefetchJobs.chunked(10).forEach { batch ->
                // Run sequentially under the shared SDK lock to avoid nested SQLite transactions
                // while foreground sync/upload is active.
                for ((datasetId, period, orgUnit) in batch) {
                    try {
                        D2SdkOperationLocks.dataValueAndAggregateMutex.withLock {
                            d2Instance.dataValueModule().dataValues()
                                .byDataSetUid(datasetId)
                                .byPeriod().eq(period.periodId())
                                .byOrganisationUnitUid().eq(orgUnit.uid())
                                .blockingGet()
                        }
                    } catch (e: Exception) {
                        Log.w("BackgroundDataPrefetcher", "Failed to prefetch data for dataset $datasetId, period ${period.periodId()}", e)
                    }
                }
                delay(50)
            }
            
            Log.d("BackgroundDataPrefetcher", "Data values prefetching completed")
            
        } catch (e: Exception) {
            Log.w("BackgroundDataPrefetcher", "Data values prefetching failed", e)
        }
    }
    
    /**
     * Clear all cached data (useful when switching accounts)
     */
    fun clearAllCaches() {
        stopPrefetching()
        metadataCacheService.clearAllCaches()
        Log.d("BackgroundDataPrefetcher", "All caches cleared")
    }
}
