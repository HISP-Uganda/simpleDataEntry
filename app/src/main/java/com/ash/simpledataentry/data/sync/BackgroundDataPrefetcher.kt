package com.ash.simpledataentry.data.sync

import android.util.Log
import com.ash.simpledataentry.data.cache.MetadataCacheService
import com.ash.simpledataentry.data.local.DatasetDao
import com.ash.simpledataentry.data.SessionManager
import kotlinx.coroutines.*
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
    
    /**
     * Start background prefetching after successful login
     */
    fun startPrefetching() {
        prefetchJob?.cancel()
        prefetchJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                Log.d("BackgroundDataPrefetcher", "Starting background data prefetching...")
                
                // 1. Pre-warm metadata caches for all available datasets
                val datasets = datasetDao.getAll()
                val datasetIds = datasets.map { it.id }
                
                Log.d("BackgroundDataPrefetcher", "Pre-warming caches for ${datasetIds.size} datasets")
                metadataCacheService.preWarmCaches(datasetIds)
                
                // 2. Pre-fetch recent data values for commonly used datasets (optional)
                prefetchRecentDataValues(datasetIds.take(5)) // Limit to top 5 datasets to avoid excessive API calls
                
                Log.d("BackgroundDataPrefetcher", "Background prefetching completed successfully")
                
            } catch (e: Exception) {
                Log.w("BackgroundDataPrefetcher", "Background prefetching failed", e)
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
            
            // Pre-fetch data values for recent periods and datasets
            datasetIds.forEach { datasetId ->
                recentPeriods.forEach { period ->
                    userOrgUnits.forEach { orgUnit ->
                        try {
                            // This will trigger the SDK to cache the data values locally
                            d2Instance.dataValueModule().dataValues()
                                .byDataSetUid(datasetId)
                                .byPeriod().eq(period.periodId())
                                .byOrganisationUnitUid().eq(orgUnit.uid())
                                .blockingGet()
                            
                            // Small delay to avoid overwhelming the server
                            delay(100)
                            
                        } catch (e: Exception) {
                            Log.w("BackgroundDataPrefetcher", "Failed to prefetch data for dataset $datasetId, period ${period.periodId()}", e)
                        }
                    }
                }
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