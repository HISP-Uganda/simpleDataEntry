package com.ash.simpledataentry.presentation.core

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * PHASE 4: Top bar progress indicator for sync/download operations
 * Shows linear progress bar beneath app bar during metadata downloads and data sync
 */
@Composable
fun TopBarProgress(
    isVisible: Boolean,
    progress: Float? = null, // null = indeterminate, 0.0-1.0 = determinate
    modifier: Modifier = Modifier
) {
    if (isVisible) {
        Box(modifier = modifier.fillMaxWidth()) {
            if (progress != null && progress in 0.0..1.0) {
                // Determinate progress (shows actual percentage)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            } else {
                // Indeterminate progress (animated)
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}
