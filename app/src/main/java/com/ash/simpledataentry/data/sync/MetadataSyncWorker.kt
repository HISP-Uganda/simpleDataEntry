package com.ash.simpledataentry.data.sync

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ash.simpledataentry.data.AccountManager
import com.ash.simpledataentry.data.DatabaseManager
import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.data.RoomHydrationMode
import androidx.core.app.NotificationCompat
import com.ash.simpledataentry.presentation.MainActivity
import android.app.PendingIntent
import android.content.Intent
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Background worker for syncing DHIS2 metadata periodically
 *
 * This worker:
 * - Downloads updated metadata (datasets, programs, org units, etc.)
 * - Runs on a weekly schedule (configurable)
 * - Can be triggered manually from Settings
 * - Uses resilient metadata download with granular progress
 */
@HiltWorker
class MetadataSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val sessionManager: SessionManager,
    private val networkStateManager: NetworkStateManager,
    private val accountManager: AccountManager,
    private val databaseManager: DatabaseManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "MetadataSyncWorker"
        const val WORK_NAME = "metadata_sync_work"
        private const val NOTIFICATION_ID = 1201
        private const val CHANNEL_ID = "metadata_sync_channel"
    }

    private suspend fun updateProgress(step: String, progress: Int) {
        setProgress(workDataOf(
            "step" to step,
            "progress" to progress
        ))
        setForeground(createForegroundInfo(step, progress))
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Metadata Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows metadata bootstrap progress"
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(step: String, progress: Int): ForegroundInfo {
        ensureChannel()
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Syncing metadata")
            .setContentText("$step (${progress.coerceIn(0,100)}%)")
            .setProgress(100, progress.coerceIn(0,100), false)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            updateProgress("Initializing metadata sync...", 0)
            Log.d(TAG, "Starting metadata sync...")

            // Check if we have an active session
            if (!sessionManager.isSessionActive()) {
                Log.d(TAG, "No active session - skipping metadata sync")
                return@withContext Result.success()
            }

            // Check network connectivity
            if (!networkStateManager.isOnline()) {
                Log.d(TAG, "No network connectivity - skipping metadata sync")
                return@withContext Result.retry()
            }

            // Download metadata with resilient error handling
            try {
                updateProgress("Syncing metadata...", 20)
                Log.d(TAG, "Downloading metadata...")

                // Use SessionManager's resilient metadata download with progress tracking
                val metadataResult = sessionManager.downloadMetadataResilient { progress ->
                    // Convert NavigationProgress to work progress percentage
                    // Metadata download uses 20-80% of overall progress
                    val workProgress = 20 + ((progress.overallPercentage - 30) * 0.6).toInt()
                    runBlocking {
                        updateProgress(progress.phaseDetail, workProgress.coerceIn(20, 80))
                    }
                }

                // Check if critical metadata failed
                if (metadataResult.hasCriticalFailures) {
                    val criticalErrors = metadataResult.criticalFailures.joinToString(", ") { it.type }
                    Log.e(TAG, "CRITICAL metadata failures: $criticalErrors")
                    updateProgress("Metadata sync failed - will retry", 0)
                    return@withContext Result.retry()
                }

                // Log warnings for non-critical failures
                if (metadataResult.hasAnyFailures) {
                    val failures = metadataResult.details.filter { !it.success }
                    failures.forEach { failure ->
                        Log.w(TAG, "Non-critical metadata '${failure.type}' failed but continuing: ${failure.error}")
                    }
                }

                val successMessage = if (metadataResult.hasAnyFailures) {
                    "⚠ ${metadataResult.successful} of ${metadataResult.successful + metadataResult.failed} metadata types synced"
                } else {
                    "✓ All metadata synced successfully"
                }

                // Refresh active account Room cache after metadata update.
                val activeAccount = accountManager.getActiveAccount(applicationContext)
                if (activeAccount != null) {
                    val db = databaseManager.getDatabaseForAccount(applicationContext, activeAccount)
                    sessionManager.hydrateRoomFromSdk(
                        context = applicationContext,
                        db = db,
                        mode = RoomHydrationMode.MINIMAL
                    )
                }

                updateProgress(successMessage, 100)
                Log.d(TAG, "Metadata sync completed: $successMessage")
                return@withContext Result.success()

            } catch (e: Exception) {
                Log.e(TAG, "Error syncing metadata", e)
                updateProgress("Metadata sync failed - will retry", 0)
                return@withContext Result.retry()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Metadata sync failed with exception", e)
            return@withContext Result.retry()
        }
    }
}
