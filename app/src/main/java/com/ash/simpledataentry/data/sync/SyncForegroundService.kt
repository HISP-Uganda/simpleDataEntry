package com.ash.simpledataentry.data.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ash.simpledataentry.presentation.MainActivity
import com.ash.simpledataentry.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that shows a notification during sync operations.
 * Required for Android 8+ to keep sync running in the background with user visibility.
 */
@AndroidEntryPoint
class SyncForegroundService : Service() {

    @Inject
    lateinit var syncQueueManager: SyncQueueManager

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null
    private var notificationManager: NotificationManager? = null

    companion object {
        const val ACTION_START_SYNC = "com.ash.simpledataentry.ACTION_START_SYNC"
        const val ACTION_CANCEL_SYNC = "com.ash.simpledataentry.ACTION_CANCEL_SYNC"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID_DATA_SYNC = "data_sync_channel"
        private const val CHANNEL_ID_METADATA_SYNC = "metadata_sync_channel"

        private const val CHANNEL_NAME_DATA = "Data Sync"
        private const val CHANNEL_NAME_METADATA = "Metadata Sync"

        fun startService(context: Context) {
            val intent = Intent(context, SyncForegroundService::class.java).apply {
                action = ACTION_START_SYNC
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, SyncForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SYNC -> {
                startForegroundWithNotification()
                observeSyncProgress()
            }
            ACTION_CANCEL_SYNC -> {
                serviceScope.launch {
                    syncQueueManager.cancelSync()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        progressJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundWithNotification() {
        val notification = createSyncNotification(
            progress = 0,
            phaseTitle = "Initializing sync...",
            phaseDetail = null
        )
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun observeSyncProgress() {
        progressJob?.cancel()
        progressJob = serviceScope.launch {
            syncQueueManager.detailedProgress.collect { progress ->
                when {
                    progress == null -> {
                        // Sync complete or cancelled
                        stopSelf()
                    }
                    progress.error != null -> {
                        // Show error notification
                        showErrorNotification(progress)
                        // Don't stop service immediately - let user see error
                    }
                    else -> {
                        // Update progress notification
                        updateNotification(progress)
                    }
                }
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Data sync channel (high importance for active sync)
            val dataChannel = NotificationChannel(
                CHANNEL_ID_DATA_SYNC,
                CHANNEL_NAME_DATA,
                NotificationManager.IMPORTANCE_LOW // Low to avoid sound/vibration
            ).apply {
                description = "Shows progress when syncing data with server"
                setShowBadge(false)
            }

            // Metadata sync channel (low importance for background refresh)
            val metadataChannel = NotificationChannel(
                CHANNEL_ID_METADATA_SYNC,
                CHANNEL_NAME_METADATA,
                NotificationManager.IMPORTANCE_MIN // Minimal for background tasks
            ).apply {
                description = "Shows progress when refreshing metadata"
                setShowBadge(false)
            }

            notificationManager?.createNotificationChannel(dataChannel)
            notificationManager?.createNotificationChannel(metadataChannel)
        }
    }

    private fun createSyncNotification(
        progress: Int,
        phaseTitle: String,
        phaseDetail: String?,
        currentItem: Int = 0,
        totalItems: Int = 0,
        itemDescription: String? = null
    ): Notification {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, SyncForegroundService::class.java).apply {
            action = ACTION_CANCEL_SYNC
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            0,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build detail text with item counter if available
        val detailText = buildString {
            if (totalItems > 0 && currentItem > 0) {
                append("$currentItem/$totalItems")
                if (itemDescription != null) {
                    append(" $itemDescription")
                }
                if (phaseDetail != null) {
                    append(" • $phaseDetail")
                }
            } else if (phaseDetail != null) {
                append(phaseDetail)
            }
        }

        return NotificationCompat.Builder(this, CHANNEL_ID_DATA_SYNC)
            .setContentTitle(phaseTitle)
            .setContentText(detailText.ifEmpty { "Syncing data..." })
            .setSmallIcon(android.R.drawable.stat_notify_sync) // Android built-in sync icon
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_delete, // Android built-in cancel icon
                "Cancel",
                cancelPendingIntent
            )
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(progress: DetailedSyncProgress) {
        val notification = createSyncNotification(
            progress = progress.overallPercentage,
            phaseTitle = progress.phaseTitle,
            phaseDetail = progress.phaseDetail,
            currentItem = progress.currentItem,
            totalItems = progress.totalItems,
            itemDescription = progress.itemDescription
        )
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun showErrorNotification(progress: DetailedSyncProgress) {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val errorMessage = progress.error?.let { error ->
            when (error) {
                is SyncError.NetworkError -> error.message
                is SyncError.ValidationError -> error.message
                is SyncError.ServerError -> error.message
                is SyncError.TimeoutError -> error.message
                is SyncError.AuthenticationError -> error.message
                is SyncError.UnknownError -> error.message
            }
        } ?: "Sync failed"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_DATA_SYNC)
            .setContentTitle("Sync failed")
            .setContentText(errorMessage)
            .setSmallIcon(android.R.drawable.stat_notify_error) // Android built-in error icon
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager?.notify(NOTIFICATION_ID, notification)

        // Stop service after showing error
        stopSelf()
    }
}
