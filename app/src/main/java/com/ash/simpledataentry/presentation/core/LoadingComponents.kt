package com.ash.simpledataentry.presentation.core

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ash.simpledataentry.data.sync.DetailedSyncProgress
import com.ash.simpledataentry.data.sync.SyncError
import com.ash.simpledataentry.data.sync.SyncPhase

/**
 * Navigation loading progress tracking models
 */
data class NavigationProgress(
    val phase: LoadingPhase,
    val overallPercentage: Int,
    val phaseTitle: String,
    val phaseDetail: String,
    val isError: Boolean = false,
    val errorMessage: String? = null
) {
    companion object {
        fun initial() = NavigationProgress(
            phase = LoadingPhase.INITIALIZING,
            overallPercentage = 0,
            phaseTitle = LoadingPhase.INITIALIZING.title,
            phaseDetail = LoadingPhase.INITIALIZING.defaultDetail
        )

        fun error(message: String, phase: LoadingPhase = LoadingPhase.INITIALIZING) = NavigationProgress(
            phase = phase,
            overallPercentage = 0,
            phaseTitle = "Error",
            phaseDetail = message,
            isError = true,
            errorMessage = message
        )
    }
}

enum class LoadingPhase(val title: String, val defaultDetail: String, val basePercentage: Int) {
    INITIALIZING("Initializing", "Preparing...", 0),
    AUTHENTICATING("Authenticating", "Verifying credentials...", 10),
    DOWNLOADING_METADATA("Downloading Metadata", "Fetching configuration data...", 30),

    // Granular metadata download phases for resilient loading
    DOWNLOADING_SYSTEM_METADATA("System Metadata", "Downloading constants and settings...", 30),
    DOWNLOADING_ORGUNIT_HIERARCHY("Organization Units", "Downloading org unit hierarchy...", 35),
    DOWNLOADING_CATEGORIES("Categories", "Downloading categories and combinations...", 45),
    DOWNLOADING_DATA_ELEMENTS("Data Elements", "Downloading data elements and indicators...", 55),
    DOWNLOADING_DATASETS("Datasets", "Downloading aggregate datasets...", 60),
    DOWNLOADING_PROGRAMS("Programs", "Downloading tracker and event programs...", 65),
    DOWNLOADING_PROGRAM_STAGES("Program Stages", "Downloading program stages...", 70),
    DOWNLOADING_OPTION_SETS("Option Sets", "Downloading option sets and legends...", 75),
    DOWNLOADING_TRACKED_ENTITIES("Tracked Entities", "Downloading tracked entity types...", 78),

    DOWNLOADING_DATA("Downloading Data", "Retrieving your information...", 80),
    PREPARING_DATABASE("Preparing Data", "Setting up local storage...", 85),
    FINALIZING("Finalizing", "Completing setup...", 95),
    LOADING_DATA("Loading Data", "Fetching information...", 20),
    PROCESSING_DATA("Processing Data", "Preparing display...", 70),
    COMPLETING("Completing", "Almost ready...", 95)
}

/**
 * Enhanced completion workflow models
 */
data class CompletionProgress(
    val phase: CompletionPhase,
    val overallPercentage: Int,
    val phaseTitle: String,
    val phaseDetail: String,
    val isValidating: Boolean = false,
    val validationRuleCount: Int = 0,
    val processedRules: Int = 0,
    val isError: Boolean = false,
    val errorMessage: String? = null
) {
    companion object {
        fun initial() = CompletionProgress(
            phase = CompletionPhase.PREPARING,
            overallPercentage = 0,
            phaseTitle = CompletionPhase.PREPARING.title,
            phaseDetail = CompletionPhase.PREPARING.defaultDetail
        )

        fun error(message: String, phase: CompletionPhase = CompletionPhase.PREPARING) = CompletionProgress(
            phase = phase,
            overallPercentage = 0,
            phaseTitle = "Error",
            phaseDetail = message,
            isError = true,
            errorMessage = message
        )
    }
}

enum class CompletionPhase(val title: String, val defaultDetail: String, val basePercentage: Int) {
    PREPARING("Preparing", "Setting up validation...", 10),
    VALIDATING("Validating", "Running validation rules...", 30),
    PROCESSING_RESULTS("Processing Results", "Analyzing validation results...", 70),
    COMPLETING("Completing", "Marking dataset as complete...", 90),
    COMPLETED("Completed", "Dataset completed successfully!", 100)
}

enum class CompletionAction {
    VALIDATE_AND_COMPLETE,
    COMPLETE_WITHOUT_VALIDATION,
    RERUN_VALIDATION,
    MARK_INCOMPLETE
}

/**
 * UI Blocking Manager - DHIS2 pattern for preventing interaction during sync
 */
object UIBlockingManager {
    /**
     * Block all user interactions during sync operations
     * Following DHIS2 Android Capture app pattern
     */
    fun blockInteractions(activity: Activity) {
        try {
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            )
        } catch (e: Exception) {
            // Fallback - log but don't crash
            android.util.Log.w("UIBlockingManager", "Failed to block interactions", e)
        }
    }

    /**
     * Restore user interactions after sync completion
     */
    fun unblockInteractions(activity: Activity) {
        try {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        } catch (e: Exception) {
            // Fallback - log but don't crash
            android.util.Log.w("UIBlockingManager", "Failed to unblock interactions", e)
        }
    }
}

// LOADING ANIMATION CONFIGURATION - Edit this object to change animations globally
object LoadingConfig {
    // Choose your loading animation type here
    val primaryAnimation: LoadingAnimationType = LoadingAnimationType.DHIS2_PULSING_DOTS
    val overlayAnimation: LoadingAnimationType = LoadingAnimationType.COMPACT_SPINNER

    // Animation parameters
    val pulsingDotSize = 12.dp
    val pulsingDotSpacing = 12.dp
    val overlayBlurRadius = 8.dp
    val overlayAlpha = 0.7f
}

enum class LoadingAnimationType {
    DHIS2_PULSING_DOTS,
    COMPACT_SPINNER,
    MATERIAL_SPINNER,
    BOUNCING_DOTS
}

/**
 * Centralized loading animation selector
 * Change animation type in LoadingConfig to swap globally
 */
@Composable
fun LoadingAnimation(
    type: LoadingAnimationType = LoadingConfig.primaryAnimation,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    size: androidx.compose.ui.unit.Dp = 20.dp
) {
    when (type) {
        LoadingAnimationType.DHIS2_PULSING_DOTS -> Dhis2PulsingDots(color = color)
        LoadingAnimationType.COMPACT_SPINNER -> CompactSpinner(color = color, size = size)
        LoadingAnimationType.MATERIAL_SPINNER -> MaterialSpinner(color = color, size = size)
        LoadingAnimationType.BOUNCING_DOTS -> BouncingDots(color = color)
    }
}

/**
 * DHIS2-style pulsing loader with three animated dots
 */
@Composable
private fun Dhis2PulsingDots(
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(LoadingConfig.pulsingDotSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition$index")

            val scale by infiniteTransition.animateFloat(
                initialValue = 0.8f,
                targetValue = 1.3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 800,
                        delayMillis = index * 200,
                        easing = EaseInOut
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scaleAnimation$index"
            )

            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 800,
                        delayMillis = index * 200,
                        easing = EaseInOut
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alphaAnimation$index"
            )

            Box(
                modifier = Modifier
                    .size(LoadingConfig.pulsingDotSize)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        alpha = alpha
                    )
                    .background(
                        color = color,
                        shape = CircleShape
                    )
            )
        }
    }
}

/**
 * Compact circular spinner
 */
@Composable
private fun CompactSpinner(
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    size: androidx.compose.ui.unit.Dp = 20.dp
) {
    CircularProgressIndicator(
        modifier = Modifier.size(size),
        strokeWidth = 2.dp,
        color = color
    )
}

/**
 * Material Design spinner
 */
@Composable
private fun MaterialSpinner(
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    size: androidx.compose.ui.unit.Dp = 24.dp
) {
    CircularProgressIndicator(
        modifier = Modifier.size(size),
        strokeWidth = 3.dp,
        color = color
    )
}

/**
 * Bouncing dots animation
 */
@Composable
private fun BouncingDots(
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "bounceTransition$index")

            val offsetY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -10f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 600,
                        delayMillis = index * 100,
                        easing = EaseInOut
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bounceAnimation$index"
            )

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer { translationY = offsetY }
                    .background(
                        color = color,
                        shape = CircleShape
                    )
            )
        }
    }
}

/**
 * Full-screen loading overlay - completely obscures content
 * Use for navigation between major screens
 */
@Composable
fun FullScreenLoader(
    message: String = "Loading...",
    isVisible: Boolean = true,
    animationType: LoadingAnimationType = LoadingConfig.primaryAnimation,
    progress: Int? = null,
    progressStep: String? = null,
    showBackgroundWarning: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (isVisible) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                LoadingAnimation(type = animationType, size = 32.dp)
                Spacer(modifier = Modifier.height(24.dp))

                // Display progress step or main message
                Text(
                    text = progressStep ?: message,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                // Display progress bar if progress is available
                progress?.let { progressValue ->
                    Spacer(modifier = Modifier.height(24.dp))
                    LinearProgressIndicator(
                        progress = { progressValue / 100f },
                        modifier = Modifier.width(280.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "$progressValue%",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                // Background warning for login operations
                if (showBackgroundWarning) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Warning",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Please keep the app open during login",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Overlay loading - blurs background content but doesn't completely obscure it
 * Use for header actions like sync, refresh, etc.
 */
@Composable
fun OverlayLoader(
    message: String = "Syncing...",
    isVisible: Boolean = true,
    animationType: LoadingAnimationType = LoadingConfig.overlayAnimation,
    progress: Int? = null,
    progressStep: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Main content - disable interactions when overlay is visible
        Box(
            modifier = if (isVisible) {
                Modifier
                    .fillMaxSize()
                    .blur(LoadingConfig.overlayBlurRadius)
                    .alpha(LoadingConfig.overlayAlpha)
            } else {
                Modifier.fillMaxSize()
            }
        ) {
            content()
        }

        // Invisible click barrier to prevent background interactions
        if (isVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Block all clicks */ }
            )
        }

        // Loading overlay
        if (isVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        LoadingAnimation(type = animationType)
                        Spacer(modifier = Modifier.height(16.dp))

                        // Display progress step or main message
                        Text(
                            text = progressStep ?: message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Display progress bar if progress is available
                        progress?.let { progressValue ->
                            Spacer(modifier = Modifier.height(16.dp))
                            LinearProgressIndicator(
                                progress = { progressValue / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "$progressValue%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Backward compatibility - keep existing function names
 */
@Composable
fun Dhis2PulsingLoader(
    modifier: Modifier = Modifier,
    dotColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    LoadingAnimation(
        type = LoadingAnimationType.DHIS2_PULSING_DOTS,
        color = dotColor
    )
}

/**
 * Compact loading indicator for buttons and inline elements
 */
@Composable
fun CompactLoader(
    size: androidx.compose.ui.unit.Dp = 20.dp,
    strokeWidth: androidx.compose.ui.unit.Dp = 2.dp,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    CircularProgressIndicator(
        modifier = Modifier.size(size),
        strokeWidth = strokeWidth,
        color = color
    )
}

/**
 * Enhanced sync overlay with detailed progress, error handling, and navigation
 * Uses DHIS2 patterns for proper UI blocking during sync operations
 */
@Composable
fun DetailedSyncOverlay(
    progress: DetailedSyncProgress?,
    onNavigateBack: () -> Unit,
    onRetry: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    // Handle UI blocking/unblocking using DHIS2 pattern
    LaunchedEffect(progress) {
        if (context is Activity) {
            if (progress != null && progress.error == null) {
                // Block interactions during active sync
                UIBlockingManager.blockInteractions(context)
            } else {
                // Unblock when sync is done or there's an error
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
                    .blur(LoadingConfig.overlayBlurRadius)
                    .alpha(LoadingConfig.overlayAlpha)
            } else {
                Modifier.fillMaxSize()
            }
        ) {
            content()
        }

        // Detailed sync overlay (UI blocking handled by WindowManager flags)
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
                        containerColor = if (progress.error != null) {
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
                            SyncPhase.INITIALIZING -> Icons.Default.Sync
                            SyncPhase.VALIDATING_CONNECTION -> Icons.Default.Wifi
                            SyncPhase.UPLOADING_DATA -> Icons.Default.CloudUpload
                            SyncPhase.DOWNLOADING_UPDATES -> Icons.Default.CloudDownload
                            SyncPhase.FINALIZING -> Icons.Default.CheckCircle
                        }

                        // Show error icon if there's an error
                        if (progress.error != null) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Error",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        } else if (progress.isAutoRetrying) {
                            // Show retry icon during auto-retry
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Auto Retrying",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
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
                            color = if (progress.error != null) {
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
                            color = if (progress.error != null) {
                                MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            },
                            textAlign = TextAlign.Center
                        )

                        // Progress bar (only if not in error state and not auto-retrying countdown)
                        if (progress.error == null && progress.autoRetryCountdown == null) {
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

                        // Auto-retry countdown
                        if (progress.autoRetryCountdown != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Retrying in ${progress.autoRetryCountdown} seconds...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                        }

                        // Action buttons
                        if (progress.error != null || progress.canNavigateBack) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Always show back button when navigation is allowed
                                if (progress.canNavigateBack) {
                                    OutlinedButton(onClick = onNavigateBack) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowLeft,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Go Back")
                                    }
                                }

                                // Show retry button for manual retry errors
                                if (progress.error != null && onRetry != null) {
                                    val shouldShowRetry = when (progress.error) {
                                        is SyncError.NetworkError -> !progress.error.canAutoRetry
                                        is SyncError.TimeoutError -> !progress.error.canAutoRetry
                                        is SyncError.ServerError -> true
                                        is SyncError.ValidationError -> true
                                        is SyncError.AuthenticationError -> true
                                        is SyncError.UnknownError -> true
                                    }

                                    if (shouldShowRetry) {
                                        Button(onClick = onRetry) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Retry")
                                        }
                                    }
                                }

                                // Show cancel button if provided (for both active sync and errors)
                                if (onCancel != null) {
                                    OutlinedButton(onClick = onCancel) {
                                        Text(if (progress.error != null) "Dismiss" else "Cancel")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Enhanced completion progress overlay with detailed progress, similar to sync overlay
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
                    .blur(LoadingConfig.overlayBlurRadius)
                    .alpha(LoadingConfig.overlayAlpha)
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