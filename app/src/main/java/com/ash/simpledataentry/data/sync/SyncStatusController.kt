package com.ash.simpledataentry.data.sync

import android.content.Context
import androidx.lifecycle.asFlow
import com.ash.simpledataentry.data.SessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Centralised controller that exposes application-wide sync status so UI elements
 * (e.g. TopBarProgress) can react consistently.
 */
@Singleton
class SyncStatusController @Inject constructor(
    private val syncQueueManager: SyncQueueManager,
    private val backgroundSyncManager: BackgroundSyncManager,
    private val sessionManager: SessionManager,
    @ApplicationContext private val appContext: Context
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _appSyncState = MutableStateFlow(AppSyncState())
    val appSyncState: StateFlow<AppSyncState> = _appSyncState.asStateFlow()

    private val _metadataBootstrapState = MutableStateFlow(MetadataBootstrapState())
    val metadataBootstrapState: StateFlow<MetadataBootstrapState> = _metadataBootstrapState.asStateFlow()

    val showTopBarProgress: StateFlow<Boolean> = appSyncState
        .map { state -> state.isRunning }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    val topBarProgressValue: StateFlow<Float?> = appSyncState
        .map { state ->
            state.progress?.overallPercentage?.let { percentage ->
                percentage / 100f
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        scope.launch {
            combine(
                syncQueueManager.syncState,
                syncQueueManager.detailedProgress
            ) { state, progress ->
                AppSyncState(
                    isRunning = state.isRunning || progress != null,
                    progress = progress,
                    queueSize = state.queueSize,
                    lastSync = state.lastSuccessfulSync,
                    error = state.error
                )
            }.collect { combinedState ->
                _appSyncState.value = combinedState
            }
        }

        scope.launch {
            sessionManager.currentAccountId
                .collect { accountId ->
                    if (accountId == null) {
                        _metadataBootstrapState.value = MetadataBootstrapState()
                    } else if (_metadataBootstrapState.value.activeAccountId != accountId) {
                        _metadataBootstrapState.value = MetadataBootstrapState(activeAccountId = accountId)
                    }
                }
        }

        scope.launch {
            combine(
                sessionManager.currentAccountId,
                backgroundSyncManager.getImmediateMetadataSyncWorkInfo().asFlow()
            ) { accountId, infos ->
                accountId to infos.firstOrNull()
            }.collect { (accountId, workInfo) ->
                if (accountId == null) {
                    _metadataBootstrapState.value = MetadataBootstrapState()
                    return@collect
                }

                val readinessLevel = runCatching {
                    sessionManager.getMetadataReadinessLevel(appContext)
                }.getOrDefault(MetadataReadinessLevel.None)
                val isReady = readinessLevel == MetadataReadinessLevel.FormReady

                val previous = _metadataBootstrapState.value
                val nextState = when (workInfo?.state) {
                    androidx.work.WorkInfo.State.ENQUEUED -> MetadataBootstrapState(
                        phase = MetadataBootstrapPhase.Enqueued,
                        activeAccountId = accountId,
                        progress = workInfo.progress.getInt("progress", 0),
                        stage = workInfo.progress.getString("stage"),
                        readinessLevel = readinessLevel,
                        message = workInfo.progress.getString("step") ?: "Metadata sync queued",
                        isReady = isReady,
                        canRetry = false,
                        lastCompletedAt = previous.lastCompletedAt
                    )
                    androidx.work.WorkInfo.State.RUNNING -> MetadataBootstrapState(
                        phase = MetadataBootstrapPhase.Running,
                        activeAccountId = accountId,
                        progress = workInfo.progress.getInt("progress", 0),
                        stage = workInfo.progress.getString("stage"),
                        readinessLevel = readinessLevel,
                        message = workInfo.progress.getString("step") ?: "Syncing metadata",
                        isReady = isReady,
                        canRetry = false,
                        lastCompletedAt = previous.lastCompletedAt
                    )
                    androidx.work.WorkInfo.State.SUCCEEDED -> MetadataBootstrapState(
                        phase = MetadataBootstrapPhase.Succeeded,
                        activeAccountId = accountId,
                        progress = 100,
                        stage = workInfo.outputData.getString("stage"),
                        readinessLevel = readinessLevel,
                        message = workInfo.outputData.getString("message") ?: "Metadata synced successfully",
                        isReady = isReady,
                        canRetry = false,
                        lastCompletedAt = workInfo.outputData.getLong("completedAt", System.currentTimeMillis())
                    )
                    androidx.work.WorkInfo.State.FAILED -> MetadataBootstrapState(
                        phase = MetadataBootstrapPhase.Failed,
                        activeAccountId = accountId,
                        stage = workInfo.outputData.getString("stage"),
                        readinessLevel = readinessLevel,
                        message = workInfo.outputData.getString("error") ?: "Metadata sync failed",
                        diagnosticMessage = workInfo.outputData.getString("diagnostic"),
                        isReady = isReady,
                        canRetry = workInfo.outputData.getBoolean("retryable", true),
                        lastCompletedAt = previous.lastCompletedAt
                    )
                    androidx.work.WorkInfo.State.CANCELLED -> MetadataBootstrapState(
                        phase = MetadataBootstrapPhase.Cancelled,
                        activeAccountId = accountId,
                        stage = workInfo.outputData.getString("stage"),
                        readinessLevel = readinessLevel,
                        message = "Metadata sync cancelled",
                        isReady = isReady,
                        canRetry = !isReady,
                        lastCompletedAt = previous.lastCompletedAt
                    )
                    else -> {
                        if (isReady) {
                            MetadataBootstrapState(
                                phase = MetadataBootstrapPhase.Succeeded,
                                activeAccountId = accountId,
                                progress = 100,
                                stage = previous.stage,
                                readinessLevel = readinessLevel,
                                message = previous.message ?: "Metadata ready",
                                isReady = true,
                                canRetry = false,
                                lastCompletedAt = previous.lastCompletedAt
                            )
                        } else if (readinessLevel == MetadataReadinessLevel.ListReady) {
                            MetadataBootstrapState(
                                phase = MetadataBootstrapPhase.Idle,
                                activeAccountId = accountId,
                                stage = previous.stage,
                                readinessLevel = readinessLevel,
                                message = "Programs are ready. Forms are still preparing.",
                                isReady = false,
                                canRetry = false,
                                lastCompletedAt = previous.lastCompletedAt
                            )
                        } else {
                            MetadataBootstrapState(
                                phase = MetadataBootstrapPhase.Idle,
                                activeAccountId = accountId,
                                stage = previous.stage,
                                readinessLevel = readinessLevel,
                                message = "Metadata sync required",
                                isReady = false,
                                canRetry = true,
                                lastCompletedAt = previous.lastCompletedAt
                            )
                        }
                    }
                }

                _metadataBootstrapState.value = nextState
            }
        }
    }
}

data class AppSyncState(
    val isRunning: Boolean = false,
    val progress: DetailedSyncProgress? = null,
    val queueSize: Int = 0,
    val lastSync: Long? = null,
    val error: String? = null
)
