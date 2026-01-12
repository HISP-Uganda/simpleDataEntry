package com.ash.simpledataentry.data.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.ash.simpledataentry.presentation.settings.SyncFrequency
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages background synchronization scheduling using WorkManager
 * 
 * This manager:
 * - Schedules periodic sync based on user preferences
 * - Handles constraints (network connectivity, battery optimization)
 * - Manages work cancellation and rescheduling
 */
@Singleton
class BackgroundSyncManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "BackgroundSyncManager"
        private const val SYNC_WORK_NAME = "background_sync_work"
        private const val METADATA_SYNC_WORK_NAME = "metadata_sync_work"
    }
    
    private val workManager = WorkManager.getInstance(context)
    
    /**
     * Configure background sync schedule based on user preference
     */
    fun configureSyncSchedule(frequency: SyncFrequency) {
        Log.d(TAG, "Configuring background sync: ${frequency.displayName}")
        Log.d(TAG, "Interval: ${frequency.intervalMinutes} minutes")
        
        // Cancel any existing work first
        cancelBackgroundSync()
        
        when (frequency) {
            SyncFrequency.NEVER, SyncFrequency.MANUAL -> {
                Log.d(TAG, "Background sync disabled per user preference")
                // Work already cancelled above
            }
            else -> {
                schedulePeriodicSync(frequency)
            }
        }
    }
    
    /**
     * Schedule periodic sync work
     */
    private fun schedulePeriodicSync(frequency: SyncFrequency) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
            
        val periodicWorkRequest = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
            frequency.intervalMinutes.toLong(), 
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag(SYNC_WORK_NAME)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
            
        workManager.enqueueUniquePeriodicWork(
            SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            periodicWorkRequest
        )
        
        Log.d(TAG, "Scheduled periodic sync every ${frequency.intervalMinutes} minutes")
    }
    
    /**
     * Cancel background sync
     */
    fun cancelBackgroundSync() {
        workManager.cancelUniqueWork(SYNC_WORK_NAME)
        Log.d(TAG, "Background sync cancelled")
    }
    
    /**
     * Trigger an immediate sync (one-time work)
     */
    fun triggerImmediateSync(): String {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()

        val workName = "immediate_sync_${System.currentTimeMillis()}"
        val oneTimeWorkRequest = OneTimeWorkRequestBuilder<BackgroundSyncWorker>()
            .setConstraints(constraints)
            .addTag("immediate_sync")
            .build()

        workManager.enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            oneTimeWorkRequest
        )
        Log.d(TAG, "Triggered immediate sync with work name: $workName")
        return workName
    }
    
    /**
     * Get sync status information
     */
    fun getSyncStatusInfo(): String {
        return "Background sync is active and managed by WorkManager"
    }
    
    /**
     * Get work info for monitoring periodic sync status
     */
    fun getSyncWorkInfo() = workManager.getWorkInfosForUniqueWorkLiveData(SYNC_WORK_NAME)

    /**
     * Get work info for monitoring immediate sync status
     */
    fun getImmediateSyncWorkInfo(workName: String) = workManager.getWorkInfosForUniqueWorkLiveData(workName)

    /**
     * Schedule periodic metadata sync (typically weekly)
     */
    fun scheduleMetadataSync(frequency: SyncFrequency = SyncFrequency.WEEKLY) {
        Log.d(TAG, "Scheduling metadata sync: ${frequency.displayName}")

        // Cancel any existing metadata sync work first
        cancelMetadataSync()

        when (frequency) {
            SyncFrequency.NEVER, SyncFrequency.MANUAL -> {
                Log.d(TAG, "Metadata sync disabled per user preference")
                // Work already cancelled above
            }
            else -> {
                schedulePeriodicMetadataSync(frequency)
            }
        }
    }

    /**
     * Schedule periodic metadata sync work
     */
    private fun schedulePeriodicMetadataSync(frequency: SyncFrequency) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<MetadataSyncWorker>(
            frequency.intervalMinutes.toLong(),
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag(METADATA_SYNC_WORK_NAME)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            METADATA_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            periodicWorkRequest
        )

        Log.d(TAG, "Scheduled periodic metadata sync every ${frequency.intervalMinutes} minutes")
    }

    /**
     * Trigger an immediate metadata sync (one-time work)
     */
    fun triggerImmediateMetadataSync(): String {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()

        val workName = "immediate_metadata_sync_${System.currentTimeMillis()}"
        val oneTimeWorkRequest = OneTimeWorkRequestBuilder<MetadataSyncWorker>()
            .setConstraints(constraints)
            .addTag("immediate_metadata_sync")
            .build()

        workManager.enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            oneTimeWorkRequest
        )
        Log.d(TAG, "Triggered immediate metadata sync with work name: $workName")
        return workName
    }

    /**
     * Cancel metadata sync
     */
    fun cancelMetadataSync() {
        workManager.cancelUniqueWork(METADATA_SYNC_WORK_NAME)
        Log.d(TAG, "Metadata sync cancelled")
    }

    /**
     * Get work info for monitoring metadata sync status
     */
    fun getMetadataSyncWorkInfo() = workManager.getWorkInfosForUniqueWorkLiveData(METADATA_SYNC_WORK_NAME)
}