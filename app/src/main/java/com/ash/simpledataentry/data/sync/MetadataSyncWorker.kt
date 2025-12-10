package com.ash.simpledataentry.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.presentation.core.NavigationProgress
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
    private val networkStateManager: NetworkStateManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "MetadataSyncWorker"
        const val WORK_NAME = "metadata_sync_work"
    }

    private suspend fun updateProgress(step: String, progress: Int) {
        setProgress(workDataOf(
            "step" to step,
            "progress" to progress
        ))
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
