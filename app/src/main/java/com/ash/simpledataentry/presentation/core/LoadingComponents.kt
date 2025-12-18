package com.ash.simpledataentry.presentation.core

import android.app.Activity
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ash.simpledataentry.data.sync.DetailedSyncProgress
import com.ash.simpledataentry.data.sync.SyncPhase

/**
 * Adaptive loading overlay - automatically shows simple or detailed progress
 * based on operation duration (<2s = simple, >2s = detailed)
 */
@Composable
fun AdaptiveLoadingOverlay(
    uiState: UiState<*>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    when (uiState) {
        is UiState.Loading -> {
            val progress = uiState.progress

            // Show detailed progress if long-running or if progress tracking exists
            if (progress?.isLongRunning == true || uiState.operation !is LoadingOperation.Initial) {
                DetailedProgressOverlay(
                    operation = uiState.operation,
                    progress = progress,
                    modifier = modifier,
                    content = content
                )
            } else {
                // Simple spinner for quick operations
                SimpleProgressOverlay(
                    message = progress?.message,
                    modifier = modifier,
                    content = content
                )
            }
        }
        is UiState.Error -> {
            // Show content with error banner
            Box(modifier = modifier.fillMaxSize()) {
                content()
                ErrorBanner(
                    error = uiState.error,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
        is UiState.Success -> {
            // Show content with optional background operation indicator
            Box(modifier = modifier.fillMaxSize()) {
                content()
                uiState.backgroundOperation?.let { operation ->
                    BackgroundOperationIndicator(
                        operation = operation,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }
}

/**
 * Simple loading overlay - spinner with optional message
 * Used for operations <2s or initial states
 */
@Composable
internal fun SimpleProgressOverlay(
    message: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Blurred content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(4.dp)
                .alpha(0.6f)
        ) {
            content()
        }

        // Centered spinner
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()

                    if (message != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * Detailed progress overlay - shows phase, progress bar, item counts, action buttons
 * Used for operations >2s with detailed progress tracking
 */
@Composable
internal fun DetailedProgressOverlay(
    operation: LoadingOperation,
    progress: LoadingProgress?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    // Block UI during sync operations
    LaunchedEffect(operation) {
        if (context is Activity && operation is LoadingOperation.Syncing) {
            UIBlockingManager.blockInteractions(context)
        }
    }

    DisposableEffect(context) {
        onDispose {
            if (context is Activity) {
                UIBlockingManager.unblockInteractions(context)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Blurred content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(4.dp)
                .alpha(0.6f)
        ) {
            content()
        }

        // Progress card
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            ProgressCard(
                operation = operation,
                progress = progress
            )
        }
    }
}

/**
 * Progress card - main container for detailed progress display
 * Extracted from DetailedSyncOverlay
 */
@Composable
private fun ProgressCard(
    operation: LoadingOperation,
    progress: LoadingProgress?
) {
    Card(
        modifier = Modifier.padding(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Phase icon
            PhaseIcon(operation = operation)

            Spacer(modifier = Modifier.height(16.dp))

            // Title and message
            when (operation) {
                is LoadingOperation.Syncing -> {
                    val syncProgress = operation.progress
                    Text(
                        text = syncProgress.phaseTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = syncProgress.phaseDetail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )

                    // Progress bar
                    if (syncProgress.overallPercentage > 0) {
                        Spacer(modifier = Modifier.height(16.dp))
                        ProgressBar(
                            percentage = syncProgress.overallPercentage,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Item counter
                    ItemCounter(
                        processedItems = syncProgress.processedItems,
                        totalItems = syncProgress.totalItems,
                        phase = syncProgress.phase
                    )
                }

                is LoadingOperation.Saving -> {
                    Text(
                        text = "Saving...",
                        style = MaterialTheme.typography.headlineSmall
                    )

                    if (operation.totalItems > 0) {
                        Spacer(modifier = Modifier.height(16.dp))
                        ProgressBar(
                            percentage = (operation.itemsProcessed * 100 / operation.totalItems),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${operation.itemsProcessed} / ${operation.totalItems}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                is LoadingOperation.BulkOperation -> {
                    Text(
                        text = operation.operationName,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    ProgressBar(
                        percentage = (operation.itemsProcessed * 100 / operation.totalItems),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${operation.itemsProcessed} / ${operation.totalItems}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                else -> {
                    if (progress?.message != null) {
                        Text(
                            text = progress.message,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Action buttons
            ActionButtons(
                progress = progress,
                operation = operation
            )
        }
    }
}

/**
 * Phase icon with animation
 * Extracted from DetailedSyncOverlay
 */
@Composable
private fun PhaseIcon(operation: LoadingOperation) {
    val infiniteTransition = rememberInfiniteTransition(label = "phase_icon_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val icon = when (operation) {
        is LoadingOperation.Syncing -> when (operation.progress.phase) {
            SyncPhase.PREPARING -> Icons.Default.CloudSync
            SyncPhase.UPLOADING_DATA -> Icons.Default.CloudUpload
            SyncPhase.DOWNLOADING_METADATA -> Icons.Default.CloudDownload
            SyncPhase.DOWNLOADING_DATA -> Icons.Default.CloudDownload
            SyncPhase.FINALIZING -> Icons.Default.CheckCircle
            else -> Icons.Default.CloudSync // Fallback for all other sync phases
        }
        is LoadingOperation.Saving -> Icons.Default.Save
        is LoadingOperation.BulkOperation -> Icons.Default.DynamicForm
        is LoadingOperation.Completing -> Icons.Default.CheckCircle
        is LoadingOperation.Navigation -> Icons.Default.Navigation
        else -> Icons.Default.HourglassEmpty
    }

    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(48.dp),
        tint = MaterialTheme.colorScheme.primary
    )
}

/**
 * Progress bar with percentage
 * Extracted from DetailedSyncOverlay
 */
@Composable
private fun ProgressBar(
    percentage: Int,
    modifier: Modifier = Modifier
) {
    LinearProgressIndicator(
        progress = { percentage / 100f },
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "$percentage%",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    )
}

/**
 * Item counter for sync operations
 * Extracted from DetailedSyncOverlay
 */
@Composable
private fun ItemCounter(
    processedItems: Int,
    totalItems: Int,
    phase: SyncPhase
) {
    if (totalItems > 0) {
        Spacer(modifier = Modifier.height(12.dp))

        val itemLabel = when (phase) {
            SyncPhase.DOWNLOADING_METADATA -> "metadata items"
            SyncPhase.UPLOADING_DATA -> "records uploaded"
            SyncPhase.DOWNLOADING_DATA -> "records downloaded"
            else -> "items"
        }

        Text(
            text = "$processedItems / $totalItems $itemLabel",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

/**
 * Action buttons (cancel, retry)
 * Extracted from DetailedSyncOverlay
 */
@Composable
private fun ActionButtons(
    progress: LoadingProgress?,
    operation: LoadingOperation
) {
    // Show cancel button for long-running cancellable operations
    if (progress?.showCancelButton == true) {
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(
            onClick = { progress.onCancel?.invoke() }
        ) {
            Text("Cancel")
        }
    }
}

/**
 * Error banner for error states with stale data
 */
@Composable
fun ErrorBanner(
    error: UiError,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.onErrorContainer
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (error) {
                        is UiError.Network -> "Network Error"
                        is UiError.Server -> "Server Error"
                        is UiError.Validation -> "Validation Error"
                        is UiError.Authentication -> "Authentication Error"
                        is UiError.Local -> "Error"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )

                Text(
                    text = when (error) {
                        is UiError.Network -> error.message
                        is UiError.Server -> error.message
                        is UiError.Validation -> error.message
                        is UiError.Authentication -> error.message
                        is UiError.Local -> error.message
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }

            // Show retry indicator for retryable errors
            if (error is UiError.Network && error.canRetry) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Can retry",
                    tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Background operation indicator - small banner for non-blocking operations
 */
@Composable
private fun BackgroundOperationIndicator(
    operation: BackgroundOperation,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = when (operation) {
                    is BackgroundOperation.Syncing -> "Syncing in background..."
                    is BackgroundOperation.Exporting -> "Exporting data..."
                    is BackgroundOperation.Deleting -> "Deleting..."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * KEPT UNCHANGED: CompletionProgressOverlay
 * Shows validation and completion progress phases with appropriate UI blocking
 */
@Composable
fun CompletionProgressOverlay(
    progress: CompletionProgress?,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    // Handle UI blocking/unblocking during completion
    LaunchedEffect(progress) {
        if (context is Activity) {
            if (progress != null && !progress.isError) {
                // Block interactions during active completion/validation
                UIBlockingManager.blockInteractions(context)
            } else {
                // Unblock when completion is done or there's an error
                UIBlockingManager.unblockInteractions(context)
            }
        }
    }

    // Cleanup on disposal
    DisposableEffect(context) {
        onDispose {
            if (context is Activity) {
                UIBlockingManager.unblockInteractions(context)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Main content
        Box(
            modifier = if (progress != null) {
                Modifier
                    .fillMaxSize()
                    .blur(4.dp)
                    .alpha(0.6f)
            } else {
                Modifier.fillMaxSize()
            }
        ) {
            content()
        }

        // Completion progress overlay
        if (progress != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (progress.isError) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Phase icon
                        val phaseIcon = when (progress.phase) {
                            CompletionPhase.PREPARING -> Icons.Default.Sync
                            CompletionPhase.VALIDATING -> Icons.Default.Verified
                            CompletionPhase.PROCESSING_RESULTS -> Icons.Default.Analytics
                            CompletionPhase.COMPLETING -> Icons.Default.CheckCircle
                            CompletionPhase.COMPLETED -> Icons.Default.CheckCircle
                        }

                        // Show error icon if there's an error
                        if (progress.isError) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Error",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        } else {
                            // Normal phase icon with animation
                            Icon(
                                imageVector = phaseIcon,
                                contentDescription = progress.phaseTitle,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Phase title
                        Text(
                            text = progress.phaseTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (progress.isError) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Phase detail
                        Text(
                            text = progress.phaseDetail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (progress.isError) {
                                MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            },
                            textAlign = TextAlign.Center
                        )

                        // Progress bar (only if not in error state)
                        if (!progress.isError) {
                            Spacer(modifier = Modifier.height(16.dp))
                            LinearProgressIndicator(
                                progress = { progress.overallPercentage / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${progress.overallPercentage}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }

                        // Validation progress details (if validating)
                        if (progress.isValidating && progress.validationRuleCount > 0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Rules: ${progress.processedRules}/${progress.validationRuleCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }

                        // Error message
                        if (progress.isError && progress.errorMessage != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = progress.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center
                            )
                        }

                        // Action buttons
                        if (progress.isError && onCancel != null) {
                            Spacer(modifier = Modifier.height(24.dp))
                            OutlinedButton(onClick = onCancel) {
                                Text("Dismiss")
                            }
                        } else if (onCancel != null && progress.phase != CompletionPhase.COMPLETED) {
                            Spacer(modifier = Modifier.height(24.dp))
                            OutlinedButton(onClick = onCancel) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * UI Blocking Manager - DHIS2 pattern for blocking interactions during critical operations
 */
object UIBlockingManager {
    fun blockInteractions(activity: Activity) {
        activity.window?.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        )
    }

    fun unblockInteractions(activity: Activity) {
        activity.window?.clearFlags(
            android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        )
    }
}

/**
 * Loading configuration constants
 */
private object LoadingConfig {
    val overlayBlurRadius = 4.dp
    const val overlayAlpha = 0.6f
}
