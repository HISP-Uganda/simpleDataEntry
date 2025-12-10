package com.ash.simpledataentry.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.work.Data
import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.data.DatabaseProvider
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
    private val networkStateManager: NetworkStateManager,
    private val databaseProvider: DatabaseProvider,
    private val syncQueueManager: SyncQueueManager
) : CoroutineWorker(context, workerParams) {
    
    companion object {
        const val TAG = "BackgroundSyncWorker"
        const val WORK_NAME = "background_sync_work"
    }
    
    private suspend fun updateProgress(step: String, progress: Int) {
        setProgress(workDataOf(
            "step" to step,
            "progress" to progress
        ))
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Start foreground service to show sync notification (Android 8+ requirement)
            SyncForegroundService.startService(applicationContext)

            updateProgress("Initializing sync...", 0)
            Log.d(TAG, "Starting background sync...")

            // Check if we have an active session
            if (!sessionManager.isSessionActive()) {
                Log.d(TAG, "No active session - skipping sync")
                SyncForegroundService.stopService(applicationContext)
                return@withContext Result.success()
            }

            // Check network connectivity
            if (!networkStateManager.isOnline()) {
                Log.d(TAG, "No network connectivity - skipping sync")
                SyncForegroundService.stopService(applicationContext)
                return@withContext Result.retry()
            }

            var syncSuccess = true
            
            // Sync datasets metadata
            try {
                updateProgress("Syncing datasets metadata...", 20)
                Log.d(TAG, "Syncing datasets metadata...")
                val datasetsResult = datasetsRepository.syncDatasets()
                if (datasetsResult.isFailure) {
                    Log.w(TAG, "Failed to sync datasets: ${datasetsResult.exceptionOrNull()}")
                    syncSuccess = false
                } else {
                    updateProgress("Datasets metadata sync completed", 40)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing datasets", e)
                syncSuccess = false
            }

            // Sync dataset instances
            try {
                updateProgress("Syncing dataset instances...", 50)
                Log.d(TAG, "Syncing dataset instances...")
                datasetInstancesRepository.syncDatasetInstances()
                updateProgress("Dataset instances sync completed", 70)
                Log.d(TAG, "Dataset instances sync completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing dataset instances", e)
                syncSuccess = false
            }

            // Upload pending data values (if any)
            try {
                updateProgress("Uploading pending data values...", 80)
                Log.d(TAG, "Uploading pending data values...")
                // Use injected SyncQueueManager singleton to handle all pending uploads
                syncQueueManager.startSync()
                updateProgress("Data upload completed", 95)
                Log.d(TAG, "Pending data upload completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading pending data", e)
                syncSuccess = false
            }
            
            val result = if (syncSuccess) {
                updateProgress("Sync completed successfully", 100)
                Log.d(TAG, "Background sync completed successfully")
                Result.success()
            } else {
                updateProgress("Sync failed - will retry", 0)
                Log.w(TAG, "Background sync completed with some failures - will retry")
                Result.retry()
            }

            // Stop foreground service (service will stop itself when sync completes, but ensure cleanup)
            SyncForegroundService.stopService(applicationContext)

            return@withContext result

        } catch (e: Exception) {
            Log.e(TAG, "Background sync failed with exception", e)
            // Stop foreground service on error
            SyncForegroundService.stopService(applicationContext)
            return@withContext Result.retry()
        }
    }
}