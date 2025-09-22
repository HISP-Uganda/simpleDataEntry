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

enum class SyncPhase(val title: String, val defaultDetail: String) {
    INITIALIZING("Initializing Sync", "Preparing data for upload..."),
    VALIDATING_CONNECTION("Validating Connection", "Checking server connection..."),
    UPLOADING_DATA("Uploading Data", "Sending data to server..."),
    DOWNLOADING_UPDATES("Downloading Updates", "Getting latest data..."),
    FINALIZING("Finalizing", "Completing sync process...")
}

sealed class SyncError {
    data class NetworkError(val message: String, val canAutoRetry: Boolean = true) : SyncError()
    data class ValidationError(val message: String) : SyncError()
    data class ServerError(val message: String, val statusCode: Int? = null) : SyncError()
    data class TimeoutError(val message: String, val canAutoRetry: Boolean = true) : SyncError()
    data class AuthenticationError(val message: String) : SyncError()
    data class UnknownError(val message: String) : SyncError()
}

data class DetailedSyncProgress(
    val phase: SyncPhase,
    val overallPercentage: Int,
    val phaseTitle: String,
    val phaseDetail: String,
    val canNavigateBack: Boolean = false,
    val error: SyncError? = null,
    val isAutoRetrying: Boolean = false,
    val autoRetryCountdown: Int? = null
) {
    companion object {
        fun initial() = DetailedSyncProgress(
            phase = SyncPhase.INITIALIZING,
            overallPercentage = 0,
            phaseTitle = SyncPhase.INITIALIZING.title,
            phaseDetail = SyncPhase.INITIALIZING.defaultDetail
        )

        fun error(error: SyncError) = DetailedSyncProgress(
            phase = SyncPhase.INITIALIZING,
            overallPercentage = 0,
            phaseTitle = "Sync Failed",
            phaseDetail = when (error) {
                is SyncError.NetworkError -> error.message
                is SyncError.ValidationError -> error.message
                is SyncError.ServerError -> error.message
                is SyncError.TimeoutError -> error.message
                is SyncError.AuthenticationError -> error.message
                is SyncError.UnknownError -> error.message
            },
            canNavigateBack = true,
            error = error
        )
    }
}

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

    private val _detailedProgress = MutableStateFlow<DetailedSyncProgress?>(null)
    val detailedProgress: StateFlow<DetailedSyncProgress?> = _detailedProgress.asStateFlow()

    private var syncJob: Job? = null
    private var networkMonitorJob: Job? = null
    private var autoRetryJob: Job? = null

    // Aligned timeout configuration with DHIS2 SDK recommendations
    private val uploadTimeoutMs = 600000L // 10 minutes - matches DHIS2 SDK expectations
    private val downloadTimeoutMs = 300000L // 5 minutes - sufficient for metadata downloads
    private val connectionTimeoutMs = 30000L // 30 seconds - reasonable for connection establishment
    private val connectionValidationTimeoutMs = 15000L // 15 seconds for connection validation
    private val overallSyncTimeoutMs = 900000L // 15 minutes max for entire sync process

    // Moderate retry configuration aligned with longer timeouts
    private val maxRetryAttempts = 2 // Reduce attempts since individual operations have longer timeouts
    private val baseRetryDelayMs = 5000L // Start with 5 seconds between retries
    private val maxRetryDelayMs = 20000L // Up to 20 seconds max delay
    private val maxConsecutiveFailures = 3 // Allow more failures since timeouts are longer

    // Chunk configuration optimized for efficiency with longer timeouts
    private val baseChunkSize = 25 // Moderate chunk size for better efficiency
    private val maxChunkSize = 50 // Larger maximum chunks since timeouts are longer
    private val minChunkSize = 5 // Minimum 5 values per chunk for efficiency
    
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
            try {
                // Add overall sync timeout to prevent hanging during screen lock
                withTimeout(overallSyncTimeoutMs) { // 15 minutes maximum for entire sync operation
                    performSync()
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(tag, "Sync operation timed out after ${overallSyncTimeoutMs/1000} seconds")
                setErrorState(SyncError.TimeoutError("Sync timed out - may be caused by screen lock or network issues"))
            } catch (e: Exception) {
                Log.e(tag, "Sync operation failed", e)
                setErrorState(classifyError(e))
            }
        }

        return try {
            syncJob?.join()
            if (_syncState.value.error == null) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(_syncState.value.error))
            }
        } catch (e: Exception) {
            // Ensure state is reset even if join() fails
            _syncState.value = _syncState.value.copy(
                isRunning = false,
                error = e.message ?: "Sync failed"
            )
            Result.failure(e)
        }
    }

    suspend fun startSyncForInstance(
        datasetId: String,
        period: String,
        orgUnit: String,
        attributeOptionCombo: String,
        forceSync: Boolean = false
    ): Result<Unit> {
        if (_syncState.value.isRunning && !forceSync) {
            return Result.failure(Exception("Sync already in progress"))
        }

        syncJob?.cancel()
        syncJob = syncScope.launch {
            try {
                // Reasonable timeout for instance sync aligned with upload timeout
                withTimeout(uploadTimeoutMs) { // Use same timeout as regular uploads
                    performSyncForInstance(datasetId, period, orgUnit, attributeOptionCombo)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(tag, "Instance sync operation timed out after ${uploadTimeoutMs/1000} seconds")
                _syncState.value = _syncState.value.copy(
                    isRunning = false,
                    error = "Sync timed out - may be caused by screen lock or network issues",
                    failedAttempts = _syncState.value.failedAttempts + 1
                )
                clearDetailedProgress() // CRITICAL: Clear overlay on timeout
            } catch (e: Exception) {
                Log.e(tag, "Instance sync operation failed", e)
                val errorMessage = when (e) {
                    is kotlinx.coroutines.TimeoutCancellationException -> "Sync timed out"
                    else -> classifyError(e).let { error ->
                        when (error) {
                            is SyncError.NetworkError -> error.message
                            is SyncError.TimeoutError -> error.message
                            is SyncError.AuthenticationError -> error.message
                            is SyncError.ServerError -> error.message
                            is SyncError.ValidationError -> error.message
                            is SyncError.UnknownError -> error.message
                        }
                    }
                }
                _syncState.value = _syncState.value.copy(
                    isRunning = false,
                    error = errorMessage,
                    failedAttempts = _syncState.value.failedAttempts + 1
                )
                clearDetailedProgress() // CRITICAL: Clear overlay on any error
            }
        }

        return try {
            syncJob?.join()
            if (_syncState.value.error == null) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(_syncState.value.error))
            }
        } catch (e: Exception) {
            // Ensure state is reset even if join() fails
            _syncState.value = _syncState.value.copy(
                isRunning = false,
                error = e.message ?: "Instance sync failed"
            )
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
            // Phase 1: Initialize sync (0-10%)
            updateProgress(SyncPhase.INITIALIZING, 0)

            // Phase 2: Validate connection (5-10%)
            if (!validateConnection()) {
                return // Error state already set in validateConnection()
            }

            val d2 = sessionManager.getD2()!! // Already validated in validateConnection()

            // Get all draft data values to sync
            val drafts = database.dataValueDraftDao().getAllDrafts()
            Log.d(tag, "Starting sync of ${drafts.size} draft values")

            updateProgress(SyncPhase.INITIALIZING, 15, "Found ${drafts.size} data values to sync")

            if (drafts.isEmpty()) {
                updateProgress(SyncPhase.FINALIZING, 100, "No data to sync")
                _syncState.value = _syncState.value.copy(
                    isRunning = false,
                    queueSize = 0,
                    lastSuccessfulSync = System.currentTimeMillis(),
                    failedAttempts = 0
                )
                clearDetailedProgress()
                return
            }

            // Phase 3: Prepare data for upload (15-30%)
            updateProgress(SyncPhase.INITIALIZING, 20, "Preparing ${drafts.size} values for upload...")
            val allSuccessfulDrafts = mutableListOf<DataValueDraftEntity>()

            for ((index, draft) in drafts.withIndex()) {
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

                    allSuccessfulDrafts.add(draft)

                    // Update progress for data preparation
                    val prepProgress = 20 + ((index + 1) * 10 / drafts.size)
                    updateProgress(SyncPhase.INITIALIZING, prepProgress, "Prepared ${index + 1} of ${drafts.size} values")

                } catch (e: Exception) {
                    Log.w(tag, "Failed to set draft value for ${draft.dataElement}: ${e.message}")
                }
            }

            if (allSuccessfulDrafts.isEmpty()) {
                setErrorState(SyncError.ValidationError("No valid data values to upload"))
                return
            }

            Log.d(tag, "Successfully set ${allSuccessfulDrafts.size} values, starting upload")

            // Phase 4: Upload data with enhanced retry logic (30-80%)
            var attempt = 0
            var uploadSuccessful = false

            while (attempt < maxRetryAttempts && !uploadSuccessful) {
                try {
                    val uploadDetail = if (attempt == 0) {
                        "Uploading ${allSuccessfulDrafts.size} data values..."
                    } else {
                        "Upload attempt ${attempt + 1} of $maxRetryAttempts"
                    }

                    updateProgress(SyncPhase.UPLOADING_DATA, 40, uploadDetail)

                    Log.d(tag, "Upload attempt ${attempt + 1} of $maxRetryAttempts for ${allSuccessfulDrafts.size} values")

                                    // Enhanced upload with shorter timeout and network validation
                    val uploadResult = withTimeout(uploadTimeoutMs) {
                        // Validate network before upload
                        if (!networkStateManager.isOnline()) {
                            throw Exception("Network connection lost before upload")
                        }
                        d2.dataValueModule().dataValues().blockingUpload()
                    }

                    Log.d(tag, "Upload result: $uploadResult")
                    uploadSuccessful = true

                    updateProgress(SyncPhase.UPLOADING_DATA, 80, "Upload completed successfully")

                    // Remove successfully synced drafts
                    for (draft in allSuccessfulDrafts) {
                        database.dataValueDraftDao().deleteDraft(draft)
                    }

                    Log.d(tag, "Upload completed successfully for ${allSuccessfulDrafts.size} values")

                } catch (e: Exception) {
                    attempt++
                    val error = classifyError(e)
                    Log.w(tag, "Upload attempt $attempt failed: ${error}", e)

                    if (attempt >= maxRetryAttempts) {
                        setErrorState(error)
                        return
                    }

                    // Check if we should auto-retry
                    val shouldAutoRetry = when (error) {
                        is SyncError.NetworkError -> error.canAutoRetry
                        is SyncError.TimeoutError -> error.canAutoRetry
                        else -> false
                    }

                    if (shouldAutoRetry) {
                        // Exponential backoff with network validation
                        val retryDelay = calculateRetryDelay(attempt)
                        Log.w(tag, "Auto-retrying upload in ${retryDelay}ms (attempt ${attempt + 1} of $maxRetryAttempts)")

                        // Show countdown to user
                        showRetryCountdown(retryDelay, attempt)

                        // Check network before retry
                        if (!networkStateManager.isOnline()) {
                            setErrorState(SyncError.NetworkError("Network connection lost during retry"))
                            return
                        }
                    } else {
                        setErrorState(error)
                        return
                    }
                }
            }

            // Phase 5: Download updates (80-95%)
            if (uploadSuccessful) {
                try {
                    updateProgress(SyncPhase.DOWNLOADING_UPDATES, 85, "Downloading latest data...")

                    withTimeout(downloadTimeoutMs) {
                        d2.dataValueModule().dataValues().get()
                    }

                    updateProgress(SyncPhase.DOWNLOADING_UPDATES, 95, "Download completed")
                } catch (e: Exception) {
                    Log.w(tag, "Failed to download latest data after upload: ${e.message}")
                    // Don't fail the entire sync just because download failed
                    updateProgress(SyncPhase.DOWNLOADING_UPDATES, 95, "Download failed but upload succeeded")
                }
            }

            // Phase 6: Finalize (95-100%)
            updateProgress(SyncPhase.FINALIZING, 98, "Finalizing sync...")

            _syncState.value = _syncState.value.copy(
                isRunning = false,
                queueSize = database.dataValueDraftDao().getAllDrafts().size,
                lastSuccessfulSync = if (uploadSuccessful) System.currentTimeMillis() else _syncState.value.lastSuccessfulSync,
                failedAttempts = if (uploadSuccessful) 0 else _syncState.value.failedAttempts + 1,
                error = null
            )

            updateProgress(SyncPhase.FINALIZING, 100, "Sync completed successfully")

            Log.d(tag, "Sync completed - ${allSuccessfulDrafts.size} values uploaded successfully")

            // Clear progress after short delay for user to see completion
            delay(1500)
            clearDetailedProgress()

        } catch (e: Exception) {
            val error = classifyError(e)
            Log.e(tag, "Sync failed: $error", e)

            _syncState.value = _syncState.value.copy(
                isRunning = false,
                failedAttempts = _syncState.value.failedAttempts + 1,
                error = when (error) {
                    is SyncError.NetworkError -> error.message
                    is SyncError.TimeoutError -> error.message
                    is SyncError.AuthenticationError -> error.message
                    is SyncError.ServerError -> error.message
                    is SyncError.ValidationError -> error.message
                    is SyncError.UnknownError -> error.message
                }
            )

            // Don't clear progress on error - let user see error state and navigate back
            if (_detailedProgress.value?.error == null) {
                setErrorState(error)
            }
        }
    }

    private suspend fun performSyncForInstance(
        datasetId: String,
        period: String,
        orgUnit: String,
        attributeOptionCombo: String
    ) {
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

            // Get drafts for specific dataset instance only
            val drafts = database.dataValueDraftDao().getDraftsForInstance(
                datasetId, period, orgUnit, attributeOptionCombo
            )
            Log.d(tag, "Starting sync for dataset instance: $datasetId, period: $period, orgUnit: $orgUnit")
            Log.d(tag, "Found ${drafts.size} draft values for this instance")

            if (drafts.isEmpty()) {
                _syncState.value = _syncState.value.copy(
                    isRunning = false,
                    queueSize = database.dataValueDraftDao().getAllDrafts().size,
                    lastSuccessfulSync = System.currentTimeMillis(),
                    failedAttempts = 0
                )
                return
            }

            // Step 1: Set ALL drafts for this instance in DHIS2 local database
            Log.d(tag, "Setting ${drafts.size} draft values for instance in DHIS2 local database")
            val allSuccessfulDrafts = mutableListOf<DataValueDraftEntity>()

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

                    allSuccessfulDrafts.add(draft)
                } catch (e: Exception) {
                    Log.w(tag, "Failed to set draft value for ${draft.dataElement}: ${e.message}")
                }
            }

            if (allSuccessfulDrafts.isEmpty()) {
                Log.w(tag, "No valid drafts to upload for instance, skipping upload")
                return
            }

            Log.d(tag, "Successfully set ${allSuccessfulDrafts.size} values for instance, starting upload")

            // Step 2: Upload ALL set values ONCE with retry logic
            var lastException: Exception? = null
            var attempt = 0
            var uploadSuccessful = false

            while (attempt < maxRetryAttempts && !uploadSuccessful) {
                try {
                    Log.d(tag, "Upload attempt ${attempt + 1} of $maxRetryAttempts for ${allSuccessfulDrafts.size} values from instance")

                    // Use withTimeout to handle long-running upload operations
                    val uploadResult = withTimeout(uploadTimeoutMs) {
                        d2.dataValueModule().dataValues().blockingUpload()
                    }
                    Log.d(tag, "Upload result for instance: $uploadResult")

                    // If upload successful, mark as uploaded
                    uploadSuccessful = true

                    // Remove successfully synced drafts for this instance
                    for (draft in allSuccessfulDrafts) {
                        database.dataValueDraftDao().deleteDraft(draft)
                    }

                    Log.d(tag, "Upload completed successfully for ${allSuccessfulDrafts.size} values from instance")

                } catch (e: Exception) {
                    lastException = e
                    attempt++

                    // Extract meaningful error message
                    val errorMessage = extractErrorMessage(e)
                    Log.w(tag, "Upload attempt $attempt failed for instance: $errorMessage", e)

                    // Check if we should retry based on error type
                    val shouldRetry = shouldRetryForError(e) && attempt < maxRetryAttempts

                    if (shouldRetry) {
                        // Calculate progressive backoff delay - longer for network issues
                        val baseDelay = if (isNetworkTimeoutError(e)) baseRetryDelayMs * 2 else baseRetryDelayMs
                        val delay = minOf(
                            baseDelay * (1L shl (attempt - 1)),
                            maxRetryDelayMs
                        )

                        Log.w(tag, "Retrying upload for instance in ${delay}ms (attempt ${attempt + 1} of $maxRetryAttempts)")
                        delay(delay)

                        // Check if network is still available before retry
                        if (!networkStateManager.isOnline()) {
                            throw Exception("Network connection lost during retry")
                        }
                    } else {
                        // Don't retry for certain error types
                        break
                    }
                }
            }

            // If upload failed after all retries, throw the last exception
            if (!uploadSuccessful && lastException != null) {
                val errorMessage = extractErrorMessage(lastException)
                Log.e(tag, "Failed to upload instance after $maxRetryAttempts attempts: $errorMessage")
                throw lastException
            }

            // Download latest data to sync (with timeout) - only if some uploads succeeded
            if (allSuccessfulDrafts.isNotEmpty()) {
                try {
                    withTimeout(downloadTimeoutMs) {
                        d2.dataValueModule().dataValues().get()
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Failed to download latest data after upload: ${e.message}")
                    // Don't fail the entire sync just because download failed
                }
            }

            _syncState.value = _syncState.value.copy(
                isRunning = false,
                queueSize = database.dataValueDraftDao().getAllDrafts().size,
                lastSuccessfulSync = if (allSuccessfulDrafts.isNotEmpty()) System.currentTimeMillis() else _syncState.value.lastSuccessfulSync,
                failedAttempts = if (allSuccessfulDrafts.isNotEmpty()) 0 else _syncState.value.failedAttempts + 1,
                error = if (allSuccessfulDrafts.isEmpty()) "Upload failed for dataset instance" else null
            )

            Log.d(tag, "Sync completed for instance - ${allSuccessfulDrafts.size} values uploaded successfully")

            // CRITICAL: Always clear detailed progress on successful completion
            clearDetailedProgress()

            if (allSuccessfulDrafts.isEmpty()) {
                throw Exception("Failed to upload any data values for dataset instance")
            }

        } catch (e: Exception) {
            val userFriendlyError = extractErrorMessage(e)
            Log.e(tag, "Sync failed for instance: $userFriendlyError", e)
            _syncState.value = _syncState.value.copy(
                isRunning = false,
                failedAttempts = _syncState.value.failedAttempts + 1,
                error = userFriendlyError
            )
            // CRITICAL: Always clear detailed progress on instance sync failure
            clearDetailedProgress()
        }
    }

    suspend fun cancelSync() {
        syncJob?.cancel()
        autoRetryJob?.cancel()
        _syncState.value = _syncState.value.copy(
            isRunning = false,
            error = "Sync cancelled by user"
        )
        clearDetailedProgress()
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
        autoRetryJob?.cancel()
    }

    /**
     * Clear detailed progress state
     */
    fun clearDetailedProgress() {
        _detailedProgress.value = null
    }

    /**
     * Update detailed progress with phase information
     */
    private suspend fun updateProgress(
        phase: SyncPhase,
        percentage: Int,
        detail: String? = null,
        canNavigateBack: Boolean = false
    ) {
        _detailedProgress.value = DetailedSyncProgress(
            phase = phase,
            overallPercentage = percentage,
            phaseTitle = phase.title,
            phaseDetail = detail ?: phase.defaultDetail,
            canNavigateBack = canNavigateBack
        )
        delay(100) // Small delay for UI feedback
    }

    /**
     * Set error state with navigation capability and reset running state
     */
    private fun setErrorState(error: SyncError) {
        _detailedProgress.value = DetailedSyncProgress.error(error)
        // Critical fix: Reset isRunning to false when setting error state
        _syncState.value = _syncState.value.copy(
            isRunning = false,
            error = when (error) {
                is SyncError.NetworkError -> error.message
                is SyncError.TimeoutError -> error.message
                is SyncError.AuthenticationError -> error.message
                is SyncError.ValidationError -> error.message
                is SyncError.ServerError -> error.message
                is SyncError.UnknownError -> error.message
            },
            failedAttempts = _syncState.value.failedAttempts + 1
        )
    }

    /**
     * Enhanced connection validation using DHIS2 patterns
     */
    private suspend fun validateConnection(): Boolean {
        return try {
            updateProgress(SyncPhase.VALIDATING_CONNECTION, 5, "Checking DHIS2 session...")

            val d2 = sessionManager.getD2()
            if (d2 == null) {
                Log.e(tag, "DHIS2 session is null - authentication required")
                setErrorState(SyncError.AuthenticationError("DHIS2 session not available. Please log in again."))
                return false
            }

            updateProgress(SyncPhase.VALIDATING_CONNECTION, 7, "Checking network connectivity...")

            if (!networkStateManager.isOnline()) {
                Log.w(tag, "Network not available for sync")
                setErrorState(SyncError.NetworkError("No internet connection available"))
                return false
            }

            updateProgress(SyncPhase.VALIDATING_CONNECTION, 8, "Testing server connection...")

            // Test connection using DHIS2 pattern - lightweight system info call
            withTimeout(connectionValidationTimeoutMs) {
                try {
                    val systemInfo = d2.systemInfoModule().systemInfo().blockingGet()
                    Log.d(tag, "Connection validated - server version: ${systemInfo?.version()}")
                } catch (e: Exception) {
                    Log.w(tag, "System info call failed, trying alternative validation")
                    // Fallback: try to access user info
                    d2.userModule().user().blockingGet()
                }
            }

            updateProgress(SyncPhase.VALIDATING_CONNECTION, 10, "Connection validated successfully")
            Log.d(tag, "Connection validation successful")
            true

        } catch (e: Exception) {
            Log.e(tag, "Connection validation failed", e)

            val error = when {
                e is TimeoutCancellationException -> {
                    Log.w(tag, "Connection validation timed out")
                    SyncError.TimeoutError("Server connection timed out - check your internet connection")
                }
                e.message?.contains("401") == true || e.message?.contains("Unauthorized") == true -> {
                    Log.e(tag, "Authentication failed during validation")
                    SyncError.AuthenticationError("Authentication failed. Please log in again.")
                }
                e.message?.contains("SocketTimeoutException") == true -> {
                    Log.w(tag, "Socket timeout during validation")
                    SyncError.NetworkError("Connection timed out - check your internet connection")
                }
                e.message?.contains("Software caused connection abort") == true -> {
                    Log.w(tag, "Connection abort during validation")
                    SyncError.NetworkError("Network connection was interrupted")
                }
                e.message?.contains("ConnectException") == true -> {
                    Log.w(tag, "Cannot connect to server")
                    SyncError.NetworkError("Cannot connect to DHIS2 server")
                }
                e.message?.contains("UnknownHostException") == true -> {
                    Log.w(tag, "Cannot resolve server hostname")
                    SyncError.NetworkError("Cannot reach DHIS2 server - check internet connection")
                }
                else -> {
                    Log.e(tag, "Unknown validation error: ${e.message}")
                    SyncError.NetworkError("Connection validation failed: ${e.message}")
                }
            }

            setErrorState(error)
            false
        }
    }

    /**
     * Enhanced error classification using DHIS2 patterns for better retry logic
     */
    private fun classifyError(exception: Exception): SyncError {
        Log.d(tag, "Classifying error: ${exception.javaClass.simpleName} - ${exception.message}")

        return when {
            // Handle D2Error specifically (DHIS2 SDK errors)
            exception.message?.contains("D2Error") == true -> {
                Log.w(tag, "D2Error detected - likely network/server issue")
                SyncError.NetworkError("DHIS2 server connection issue - this may be caused by screen lock or network interruption", canAutoRetry = true)
            }

            // Timeout errors - can auto-retry (common during screen lock)
            exception is kotlinx.coroutines.TimeoutCancellationException -> {
                Log.w(tag, "Coroutine timeout - likely caused by screen lock or slow network")
                SyncError.TimeoutError("Sync timed out - may be caused by screen lock or slow network", canAutoRetry = true)
            }
            exception.message?.contains("SocketTimeoutException") == true -> {
                Log.w(tag, "Socket timeout detected")
                SyncError.TimeoutError("Network timeout - check your internet connection", canAutoRetry = true)
            }
            exception.message?.contains("timeout") == true -> {
                Log.w(tag, "General timeout detected")
                SyncError.TimeoutError("Operation timed out", canAutoRetry = true)
            }

            // Socket errors - specific handling for connection abort (main issue from logs)
            exception.message?.contains("Software caused connection abort") == true ||
            exception.message?.contains("SocketException") == true -> {
                Log.w(tag, "Socket connection abort - likely screen lock during sync")
                SyncError.NetworkError("Network connection was interrupted (often caused by screen lock)", canAutoRetry = true)
            }

            // Network connection errors - can auto-retry
            exception.message?.contains("ConnectException") == true -> {
                Log.w(tag, "Connection exception - cannot connect to server")
                SyncError.NetworkError("Cannot connect to server", canAutoRetry = true)
            }
            exception.message?.contains("UnknownHostException") == true -> {
                Log.w(tag, "Unknown host - DNS or network issue")
                SyncError.NetworkError("Cannot reach server - check internet connection", canAutoRetry = true)
            }

            // Authentication errors - require user action
            exception.message?.contains("401") == true || exception.message?.contains("Unauthorized") == true -> {
                Log.e(tag, "Authentication failed")
                SyncError.AuthenticationError("Authentication failed. Please log in again.")
            }

            // Server errors - some can retry
            exception.message?.contains("500") == true -> {
                Log.w(tag, "Server error 500")
                SyncError.ServerError("Server error - please try again", statusCode = 500)
            }
            exception.message?.contains("503") == true -> {
                Log.w(tag, "Server unavailable 503")
                SyncError.ServerError("Server temporarily unavailable", statusCode = 503)
            }
            exception.message?.contains("400") == true -> {
                Log.e(tag, "Bad request 400")
                SyncError.ValidationError("Invalid data submitted")
            }
            exception.message?.contains("404") == true -> {
                Log.e(tag, "Not found 404")
                SyncError.ValidationError("Resource not found")
            }

            // Default handling - be more conservative about retrying
            else -> {
                Log.e(tag, "Unknown error type: ${exception.javaClass.simpleName}")
                SyncError.UnknownError(exception.message ?: "Sync failed. Please try again.")
            }
        }
    }

    /**
     * Calculate exponential backoff delay for retries
     */
    private fun calculateRetryDelay(attempt: Int): Long {
        return minOf(
            baseRetryDelayMs * (1L shl (attempt - 1)), // Exponential: 2s, 4s, 8s
            maxRetryDelayMs
        )
    }

    /**
     * Show retry countdown to user with progress updates
     */
    private suspend fun showRetryCountdown(delayMs: Long, attempt: Int) {
        val seconds = (delayMs / 1000).toInt()
        for (remaining in seconds downTo 1) {
            _detailedProgress.value = _detailedProgress.value?.copy(
                isAutoRetrying = true,
                autoRetryCountdown = remaining,
                phaseDetail = "Network error - retrying in $remaining seconds... (attempt ${attempt + 1} of $maxRetryAttempts)"
            )
            delay(1000)
        }
    }

    /**
     * Auto-retry logic for network errors
     */
    private suspend fun handleAutoRetry(error: SyncError, attempt: Int): Boolean {
        val canAutoRetry = when (error) {
            is SyncError.NetworkError -> error.canAutoRetry && attempt < maxRetryAttempts
            is SyncError.TimeoutError -> error.canAutoRetry && attempt < maxRetryAttempts
            else -> false
        }

        if (!canAutoRetry) return false

        val delay = minOf(
            baseRetryDelayMs * (1L shl (attempt - 1)),
            maxRetryDelayMs
        )

        // Show auto-retry countdown
        repeat((delay / 1000).toInt()) { second ->
            val remaining = (delay / 1000).toInt() - second
            _detailedProgress.value = _detailedProgress.value?.copy(
                isAutoRetrying = true,
                autoRetryCountdown = remaining,
                phaseDetail = "Auto-retrying in $remaining seconds... (attempt ${attempt + 1} of $maxRetryAttempts)"
            )
            delay(1000)
        }

        // Check network before retry
        if (!networkStateManager.isOnline()) {
            setErrorState(SyncError.NetworkError("Network connection lost during retry"))
            return false
        }

        return true
    }

    /**
     * Determine optimal chunk size based on failure history and network conditions
     */
    private fun getAdaptiveChunkSize(): Int {
        val currentFailures = _syncState.value.failedAttempts
        return when {
            // If we've had multiple failures, use smaller chunks
            currentFailures >= 3 -> minChunkSize
            currentFailures >= 1 -> baseChunkSize / 2
            // If sync has been successful recently, use larger chunks
            else -> baseChunkSize
        }
    }

    /**
     * Extract a user-friendly error message from various exception types
     */
    private fun extractErrorMessage(exception: Exception): String {
        return when {
            // Handle D2Error from DHIS2 SDK
            exception.message?.contains("D2Error") == true -> {
                "Server connection issue. Please check your internet connection and try again."
            }
            // Handle timeout exceptions
            exception is kotlinx.coroutines.TimeoutCancellationException -> {
                "Upload timed out. Please check your internet connection and try again."
            }
            exception.message?.contains("SocketTimeoutException") == true -> {
                "Connection timed out. Please check your internet connection and try again."
            }
            exception.message?.contains("UnknownHostException") == true -> {
                "Cannot reach server. Please check your internet connection."
            }
            exception.message?.contains("ConnectException") == true -> {
                "Cannot connect to server. Please check your internet connection."
            }
            // Handle authentication issues
            exception.message?.contains("401") == true || exception.message?.contains("Unauthorized") == true -> {
                "Authentication failed. Please log in again."
            }
            // Handle server errors
            exception.message?.contains("500") == true -> {
                "Server error. Please try again later."
            }
            exception.message?.contains("503") == true -> {
                "Server temporarily unavailable. Please try again later."
            }
            // Default message
            else -> {
                exception.message ?: "Sync failed. Please try again."
            }
        }
    }

    /**
     * Determine if an error should trigger a retry attempt
     */
    private fun shouldRetryForError(exception: Exception): Boolean {
        return when {
            // Retry for network/timeout issues
            exception is kotlinx.coroutines.TimeoutCancellationException -> true
            exception.message?.contains("SocketTimeoutException") == true -> true
            exception.message?.contains("ConnectException") == true -> true
            exception.message?.contains("D2Error") == true -> true
            exception.message?.contains("500") == true -> true
            exception.message?.contains("503") == true -> true
            // Don't retry for authentication issues
            exception.message?.contains("401") == true -> false
            exception.message?.contains("Unauthorized") == true -> false
            // Don't retry for client errors
            exception.message?.contains("400") == true -> false
            exception.message?.contains("404") == true -> false
            // Default to retry for unknown errors
            else -> true
        }
    }

    /**
     * Check if error is specifically network timeout related for longer backoff
     */
    private fun isNetworkTimeoutError(exception: Exception): Boolean {
        return when {
            exception is kotlinx.coroutines.TimeoutCancellationException -> true
            exception.message?.contains("SocketTimeoutException") == true -> true
            exception.message?.contains("ConnectTimeoutException") == true -> true
            exception.message?.contains("timeout") == true -> true
            else -> false
        }
    }
}