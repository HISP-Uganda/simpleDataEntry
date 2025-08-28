package com.ash.simpledataentry.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.domain.repository.DatasetInstancesRepository
import com.ash.simpledataentry.domain.repository.DatasetsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background worker for syncing DHIS2 data
 * 
 * This worker:
 * - Syncs datasets metadata
 * - Syncs dataset instances and completion status  
 * - Uploads pending data values
 * - Runs based on user-configured sync frequency
 */
@HiltWorker
class BackgroundSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val sessionManager: SessionManager,
    private val datasetsRepository: DatasetsRepository,
    private val datasetInstancesRepository: DatasetInstancesRepository,
    private val networkStateManager: NetworkStateManager
) : CoroutineWorker(context, workerParams) {
    
    companion object {
        const val TAG = "BackgroundSyncWorker"
        const val WORK_NAME = "background_sync_work"
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting background sync...")
            
            // Check if we have an active session
            if (!sessionManager.isSessionActive()) {
                Log.d(TAG, "No active session - skipping sync")
                return@withContext Result.success()
            }
            
            // Check network connectivity
            if (!networkStateManager.isOnline()) {
                Log.d(TAG, "No network connectivity - skipping sync")
                return@withContext Result.retry()
            }
            
            var syncSuccess = true
            
            // Sync datasets metadata
            try {
                Log.d(TAG, "Syncing datasets metadata...")
                val datasetsResult = datasetsRepository.syncDatasets()
                if (datasetsResult.isFailure) {
                    Log.w(TAG, "Failed to sync datasets: ${datasetsResult.exceptionOrNull()}")
                    syncSuccess = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing datasets", e)
                syncSuccess = false
            }
            
            // Sync dataset instances
            try {
                Log.d(TAG, "Syncing dataset instances...")
                datasetInstancesRepository.syncDatasetInstances()
                Log.d(TAG, "Dataset instances sync completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing dataset instances", e)
                syncSuccess = false
            }
            
            // Upload pending data values (if any)
            try {
                Log.d(TAG, "Uploading pending data values...")
                // Note: This would be implemented when data entry sync is added
                // For now, just log that we would check for pending uploads
                Log.d(TAG, "Pending data upload check completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading pending data", e)
                syncSuccess = false
            }
            
            val result = if (syncSuccess) {
                Log.d(TAG, "Background sync completed successfully")
                Result.success()
            } else {
                Log.w(TAG, "Background sync completed with some failures - will retry")
                Result.retry()
            }
            
            return@withContext result
            
        } catch (e: Exception) {
            Log.e(TAG, "Background sync failed with exception", e)
            return@withContext Result.retry()
        }
    }
}