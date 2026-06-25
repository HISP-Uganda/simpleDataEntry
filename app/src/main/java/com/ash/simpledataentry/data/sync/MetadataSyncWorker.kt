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
import androidx.work.Data
import androidx.work.workDataOf
import com.ash.simpledataentry.data.SessionManager
import androidx.core.app.NotificationCompat
import com.ash.simpledataentry.presentation.MainActivity
import android.app.PendingIntent
import android.content.Intent
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
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
    private val mobileMetadataBootstrapper: MobileMetadataBootstrapper
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "MetadataSyncWorker"
        const val WORK_NAME = "metadata_sync_work"
        private const val NOTIFICATION_ID = 1201
        private const val CHANNEL_ID = "metadata_sync_channel"
    }

    private suspend fun updateProgress(step: String, progress: Int, stage: String? = null) {
        setProgress(workDataOf(
            "step" to step,
            "progress" to progress,
            "stage" to stage
        ))
        setForeground(createForegroundInfo(step, progress))
    }

    private fun successData(message: String, stage: String): Data = workDataOf(
        "message" to message,
        "stage" to stage,
        "completedAt" to System.currentTimeMillis()
    )

    private fun failureData(
        message: String,
        stage: String,
        diagnostic: String?,
        retryable: Boolean
    ): Data = workDataOf(
        "error" to message,
        "stage" to stage,
        "diagnostic" to diagnostic,
        "retryable" to retryable
    )

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
            updateProgress("Initializing metadata sync...", 0, BootstrapStage.VERIFY_SESSION.name)
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

            try {
                val metadataResult = mobileMetadataBootstrapper.bootstrap { progress ->
                    updateProgress(
                        step = progress.message,
                        progress = progress.progress,
                        stage = progress.stage.name
                    )
                }

                if (!metadataResult.success) {
                    Log.e(
                        TAG,
                        "Metadata bootstrap failed at ${metadataResult.stage.name}: ${metadataResult.diagnosticMessage}"
                    )
                    updateProgress(
                        step = metadataResult.message,
                        progress = metadataResult.stage.progress,
                        stage = metadataResult.stage.name
                    )
                    return@withContext Result.failure(
                        failureData(
                            message = metadataResult.message,
                            stage = metadataResult.stage.name,
                            diagnostic = metadataResult.diagnosticMessage,
                            retryable = metadataResult.retryable
                        )
                    )
                }

                updateProgress(metadataResult.message, 100, metadataResult.stage.name)
                Log.d(TAG, "Metadata sync completed: ${metadataResult.message}")
                return@withContext Result.success(
                    successData(
                        message = metadataResult.message,
                        stage = metadataResult.stage.name
                    )
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error syncing metadata", e)
                updateProgress("Metadata sync failed. Retry metadata sync.", 0, BootstrapStage.VERIFY_SESSION.name)
                return@withContext Result.failure(
                    failureData(
                        message = "Metadata sync failed. Retry metadata sync.",
                        stage = BootstrapStage.VERIFY_SESSION.name,
                        diagnostic = e.message,
                        retryable = true
                    )
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Metadata sync failed with exception", e)
            return@withContext Result.failure(
                failureData(
                    message = "Metadata sync failed. Retry metadata sync.",
                    stage = BootstrapStage.VERIFY_SESSION.name,
                    diagnostic = e.message,
                    retryable = true
                )
            )
        }
    }
}
