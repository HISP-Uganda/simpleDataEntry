package com.ash.simpledataentry.data.sync

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
    private val syncQueueManager: SyncQueueManager
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _appSyncState = MutableStateFlow(AppSyncState())
    val appSyncState: StateFlow<AppSyncState> = _appSyncState.asStateFlow()

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
    }
}

data class AppSyncState(
    val isRunning: Boolean = false,
    val progress: DetailedSyncProgress? = null,
    val queueSize: Int = 0,
    val lastSync: Long? = null,
    val error: String? = null
)
