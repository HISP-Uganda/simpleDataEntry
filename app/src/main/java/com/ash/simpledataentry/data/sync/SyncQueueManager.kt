package com.ash.simpledataentry.data.sync

import android.util.Log
import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.data.local.AppDatabase
import com.ash.simpledataentry.data.local.DataValueDraftEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class SyncState(
    val isRunning: Boolean = false,
    val queueSize: Int = 0,
    val lastSyncAttempt: Long? = null,
    val lastSuccessfulSync: Long? = null,
    val failedAttempts: Int = 0,
    val error: String? = null
)

@Singleton
class SyncQueueManager @Inject constructor(
    private val networkStateManager: NetworkStateManager,
    private val sessionManager: SessionManager,
    private val database: AppDatabase
) {
    
    private val tag = "SyncQueueManager"
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    
    private var syncJob: Job? = null
    private var networkMonitorJob: Job? = null
    
    // Retry configuration
    private val maxRetryAttempts = 3
    private val baseRetryDelayMs = 1000L
    private val maxRetryDelayMs = 30000L
    
    init {
        startNetworkMonitoring()
    }
    
    private fun startNetworkMonitoring() {
        networkMonitorJob = syncScope.launch {
            networkStateManager.networkState.collect { networkState ->
                if (networkState.hasInternet && _syncState.value.queueSize > 0 && !_syncState.value.isRunning) {
                    Log.d(tag, "Network available, starting queued sync")
                    startSync()
                }
            }
        }
    }
    
    suspend fun queueForSync() {
        val currentQueueSize = database.dataValueDraftDao().getAllDrafts().size
        _syncState.value = _syncState.value.copy(queueSize = currentQueueSize)
        
        if (networkStateManager.isOnline() && !_syncState.value.isRunning) {
            startSync()
        } else {
            Log.d(tag, "Queued for sync - Network available: ${networkStateManager.isOnline()}, Sync running: ${_syncState.value.isRunning}")
        }
    }
    
    suspend fun startSync(forceSync: Boolean = false): Result<Unit> {
        if (_syncState.value.isRunning && !forceSync) {
            return Result.failure(Exception("Sync already in progress"))
        }
        
        syncJob?.cancel()
        syncJob = syncScope.launch {
            performSync()
        }
        
        return try {
            syncJob?.join()
            if (_syncState.value.error == null) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(_syncState.value.error))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun performSync() {
        _syncState.value = _syncState.value.copy(
            isRunning = true,
            lastSyncAttempt = System.currentTimeMillis(),
            error = null
        )
        
        try {
            val d2 = sessionManager.getD2()
            if (d2 == null) {
                throw Exception("DHIS2 session not available")
            }
            
            if (!networkStateManager.isOnline()) {
                throw Exception("No internet connection available")
            }
            
            // Get all draft data values to sync
            val drafts = database.dataValueDraftDao().getAllDrafts()
            Log.d(tag, "Starting sync of ${drafts.size} draft values")
            
            if (drafts.isEmpty()) {
                _syncState.value = _syncState.value.copy(
                    isRunning = false,
                    queueSize = 0,
                    lastSuccessfulSync = System.currentTimeMillis(),
                    failedAttempts = 0
                )
                return
            }
            
            // Convert drafts to DHIS2 data values
            val successfulDrafts = mutableListOf<DataValueDraftEntity>()
            
            for (draft in drafts) {
                try {
                    // Set the data value in DHIS2 local database
                    d2.dataValueModule().dataValues()
                        .value(
                            period = draft.period,
                            organisationUnit = draft.orgUnit,
                            dataElement = draft.dataElement,
                            categoryOptionCombo = draft.categoryOptionCombo,
                            attributeOptionCombo = draft.attributeOptionCombo
                        )
                        .blockingSet(draft.value)
                    
                    successfulDrafts.add(draft)
                } catch (e: Exception) {
                    Log.w(tag, "Failed to set draft value for ${draft.dataElement}: ${e.message}")
                }
            }
            
            if (successfulDrafts.isEmpty()) {
                throw Exception("Failed to convert any draft values")
            }
            
            // Upload to server with retry logic
            var lastException: Exception? = null
            var attempt = 0
            
            while (attempt < maxRetryAttempts) {
                try {
                    Log.d(tag, "Upload attempt ${attempt + 1} of $maxRetryAttempts")
                    
                    val uploadResult = d2.dataValueModule().dataValues().blockingUpload()
                    Log.d(tag, "Upload result: $uploadResult")
                    
                    // If upload successful, remove successfully synced drafts
                    for (draft in successfulDrafts) {
                        database.dataValueDraftDao().deleteDraft(draft)
                    }
                    
                    // Download latest data to sync
                    d2.dataValueModule().dataValues().get()
                    
                    _syncState.value = _syncState.value.copy(
                        isRunning = false,
                        queueSize = database.dataValueDraftDao().getAllDrafts().size,
                        lastSuccessfulSync = System.currentTimeMillis(),
                        failedAttempts = 0,
                        error = null
                    )
                    
                    Log.d(tag, "Sync completed successfully")
                    return
                    
                } catch (e: Exception) {
                    lastException = e
                    attempt++
                    
                    if (attempt < maxRetryAttempts) {
                        // Calculate exponential backoff delay
                        val delay = minOf(
                            baseRetryDelayMs * (1L shl (attempt - 1)),
                            maxRetryDelayMs
                        )
                        
                        Log.w(tag, "Upload attempt $attempt failed, retrying in ${delay}ms: ${e.message}")
                        delay(delay)
                        
                        // Check if network is still available before retry
                        if (!networkStateManager.isOnline()) {
                            throw Exception("Network connection lost during retry")
                        }
                    }
                }
            }
            
            // All retry attempts failed
            throw lastException ?: Exception("Upload failed after $maxRetryAttempts attempts")
            
        } catch (e: Exception) {
            Log.e(tag, "Sync failed", e)
            _syncState.value = _syncState.value.copy(
                isRunning = false,
                failedAttempts = _syncState.value.failedAttempts + 1,
                error = e.message
            )
        }
    }
    
    suspend fun cancelSync() {
        syncJob?.cancel()
        _syncState.value = _syncState.value.copy(
            isRunning = false,
            error = "Sync cancelled by user"
        )
    }
    
    suspend fun clearQueue() {
        database.dataValueDraftDao().getAllDrafts().forEach { draft ->
            database.dataValueDraftDao().deleteDraft(draft)
        }
        _syncState.value = _syncState.value.copy(queueSize = 0)
    }
    
    fun cleanup() {
        syncJob?.cancel()
        networkMonitorJob?.cancel()
    }
}