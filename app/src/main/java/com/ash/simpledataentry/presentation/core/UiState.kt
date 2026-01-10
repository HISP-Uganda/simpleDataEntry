package com.ash.simpledataentry.presentation.core

import com.ash.simpledataentry.data.sync.DetailedSyncProgress
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Unified UI state pattern for all ViewModels
 * Type-safe sealed class prevents invalid state combinations
 */
sealed class UiState<out T> {
    /**
     * Loading state with optional operation details and progress tracking
     */
    data class Loading<T>(
        val operation: LoadingOperation = LoadingOperation.Initial,
        val progress: LoadingProgress? = null
    ) : UiState<T>()

    /**
     * Error state with classified error type and optional stale data
     */
    data class Error<T>(
        val error: UiError,
        val previousData: T? = null  // Show stale data with error banner
    ) : UiState<T>()

    /**
     * Success state with data and optional background operation indicator
     */
    data class Success<T>(
        val data: T,
        val backgroundOperation: BackgroundOperation? = null  // e.g., syncing while browsing
    ) : UiState<T>()
}

/**
 * Types of loading operations - determines UI presentation
 */
sealed class LoadingOperation {
    /**
     * Initial loading state (simple spinner)
     */
    object Initial : LoadingOperation()

    /**
     * Navigation/screen load with detailed phases (form loading, list loading)
     */
    data class Navigation(val progress: NavigationProgress) : LoadingOperation()

    /**
     * Save operation with progress tracking
     */
    data class Saving(
        val itemsProcessed: Int = 0,
        val totalItems: Int = 0
    ) : LoadingOperation()

    /**
     * Sync operation with detailed DHIS2 sync progress
     */
    data class Syncing(val progress: DetailedSyncProgress) : LoadingOperation()

    /**
     * Dataset completion workflow
     */
    data class Completing(val progress: CompletionProgress) : LoadingOperation()

    /**
     * Bulk operation (completing multiple instances, deleting, etc.)
     */
    data class BulkOperation(
        val itemsProcessed: Int,
        val totalItems: Int,
        val operationName: String
    ) : LoadingOperation()
}

/**
 * Background operations that don't block UI but show indicator
 */
sealed class BackgroundOperation {
    object Syncing : BackgroundOperation()
    object Exporting : BackgroundOperation()
    object Deleting : BackgroundOperation()
}

/**
 * Classified error types for smart error handling and retry logic
 */
sealed class UiError {
    /**
     * Network connectivity errors (auto-retry eligible)
     */
    data class Network(
        val message: String,
        val canRetry: Boolean = true
    ) : UiError()

    /**
     * Server-side errors (may be retryable depending on status code)
     */
    data class Server(
        val message: String,
        val statusCode: Int? = null
    ) : UiError()

    /**
     * Validation errors (user must fix data)
     */
    data class Validation(
        val message: String,
        val fields: List<String> = emptyList()
    ) : UiError()

    /**
     * Authentication errors (requires re-login)
     */
    data class Authentication(
        val message: String
    ) : UiError()

    /**
     * Local storage or app errors
     */
    data class Local(
        val message: String,
        val cause: String? = null
    ) : UiError()
}

/**
 * Progress tracking for long-running operations
 * Automatically determines if cancel button should show based on duration
 */
data class LoadingProgress(
    val percentage: Int = 0,
    val message: String = "",
    val startTime: Long = System.currentTimeMillis(),
    val onCancel: (() -> Unit)? = null
) {
    /**
     * Operations running >2 seconds should show detailed progress
     */
    val isLongRunning: Boolean
        get() = (System.currentTimeMillis() - startTime) > 2000

    /**
     * Show cancel button after 5 seconds if cancellation is supported
     */
    val showCancelButton: Boolean
        get() = (System.currentTimeMillis() - startTime) > 5000 && onCancel != null
}

/**
 * Navigation loading progress tracking
 * Used during screen transitions and data loading
 */
data class NavigationProgress(
    val phase: LoadingPhase = LoadingPhase.LOADING_DATA,
    val message: String = "",
    val percentage: Int = 0,
    val loadingType: StepLoadingType? = null,
    // Additional fields for backward compatibility
    val title: String = message, // Alias for message
    val overallPercentage: Int = percentage,
    val phaseTitle: String = message,
    val phaseDetail: String = ""
) {
    companion object {
        fun initial() = NavigationProgress(
            phase = LoadingPhase.INITIALIZING,
            message = "Initializing...",
            percentage = 0
        )

        fun error(errorMessage: String) = NavigationProgress(
            phase = LoadingPhase.LOADING_DATA,
            message = errorMessage,
            percentage = 0
        )
    }
}

/**
 * Loading phases for navigation operations
 */
enum class LoadingPhase(val title: String, val error: String = "") {
    LOADING_DATA("Loading Data", "Failed to load data"),
    PROCESSING("Processing", "Processing failed"),
    FINALIZING("Finalizing", "Finalization failed"),
    INITIALIZING("Initializing", "Initialization failed"),
    PROCESSING_DATA("Processing Data", "Data processing failed"),
    COMPLETING("Completing", "Completion failed"),
    AUTHENTICATING("Authenticating", "Authentication failed"),
    DOWNLOADING_METADATA("Downloading Metadata", "Metadata download failed")
}

/**
 * Helpers for working with UiState flows
 */
fun <T> UiState<T>.dataOr(default: () -> T): T = when (this) {
    is UiState.Success -> data
    is UiState.Error -> previousData ?: default()
    is UiState.Loading -> default()
}

fun <T> UiState<T>.dataOrNull(): T? = when (this) {
    is UiState.Success -> data
    is UiState.Error -> previousData
    is UiState.Loading -> null
}

fun <T> MutableStateFlow<UiState<T>>.emitLoading(operation: LoadingOperation = LoadingOperation.Initial) {
    value = UiState.Loading(operation)
}

fun <T> MutableStateFlow<UiState<T>>.emitSuccess(data: T, backgroundOperation: BackgroundOperation? = null) {
    value = UiState.Success(data, backgroundOperation)
}

fun <T> MutableStateFlow<UiState<T>>.emitError(
    error: UiError,
    previousData: T? = value.dataOrNull()
) {
    value = UiState.Error(error, previousData)
}

fun <T> MutableStateFlow<UiState<T>>.clearError(default: () -> T) {
    val previousData = (value as? UiState.Error)?.previousData
    value = UiState.Success(previousData ?: default())
}

fun <T> MutableStateFlow<UiState<T>>.updateSuccess(
    default: () -> T,
    backgroundOperation: BackgroundOperation? = null,
    update: (T) -> T
) {
    val currentData = value.dataOr(default)
    val currentBackground = (value as? UiState.Success)?.backgroundOperation
    value = UiState.Success(update(currentData), backgroundOperation ?: currentBackground)
}

/**
 * DEPRECATED: Loading animation types
 * Kept for backward compatibility with old loading components
 * New components use Material Design CircularProgressIndicator
 */
@Deprecated("No longer used - new components use Material Design indicators")
enum class LoadingAnimationType {
    DHIS2_PULSING_DOTS,
    BOUNCING_DOTS,
    CIRCULAR_PROGRESS
}

/**
 * Completion actions for dataset/tracker data entry
 * Used in data entry workflow completion dialogs
 */
enum class CompletionAction {
    VALIDATE_AND_COMPLETE,
    COMPLETE_WITHOUT_VALIDATION,
    RERUN_VALIDATION,
    MARK_INCOMPLETE
}

/**
 * Completion phases for dataset/tracker completion workflow
 */
enum class CompletionPhase(val title: String, val error: String = "") {
    PREPARING("Preparing", "Preparation failed"),
    VALIDATING("Validating", "Validation failed"),
    PROCESSING_RESULTS("Processing Results", "Processing failed"),
    COMPLETING("Completing", "Completion failed"),
    COMPLETED("Completed", "")
}

/**
 * Progress tracking for completion workflow
 */
data class CompletionProgress(
    val phase: CompletionPhase,
    val overallPercentage: Int,
    val phaseTitle: String,
    val phaseDetail: String,
    val isError: Boolean = false,
    val errorMessage: String? = null,
    val isValidating: Boolean = phase == CompletionPhase.VALIDATING,
    val validationRuleCount: Int = 0,
    val processedRules: Int = 0
) {
    companion object {
        fun error(message: String): CompletionProgress {
            return CompletionProgress(
                phase = CompletionPhase.VALIDATING,
                overallPercentage = 0,
                phaseTitle = "Error",
                phaseDetail = message,
                isError = true,
                errorMessage = message
            )
        }
    }
}
