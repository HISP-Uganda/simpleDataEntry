package com.ash.simpledataentry.presentation.datasets.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ash.simpledataentry.domain.model.DatasetStyle

/**
 * Program Type for determining appropriate fallback icons
 */
enum class ProgramType {
    DATASET,
    TRACKER_PROGRAM,
    EVENT_PROGRAM
}

/**
 * DatasetIcon - Renders DHIS2 dataset icons with intelligent fallback support
 *
 * This composable displays dataset icons from DHIS2 instance styling with metadata-driven approach.
 * Falls back to appropriate Material icons based on program type when DHIS2 icon is not available.
 */
@Composable
fun DatasetIcon(
    style: DatasetStyle?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    programType: ProgramType = ProgramType.DATASET,
    customFallback: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    val iconName = style?.icon
    val iconColor = style?.color?.let { parseHexColor(it) } ?: tint

    // Determine the appropriate fallback icon based on program type
    val fallbackIcon = customFallback ?: getDefaultIconForProgramType(programType)

    // Debug logging
    android.util.Log.d("DatasetIcon", "Rendering icon - style: $style, iconName: $iconName, color: ${style?.color}, programType: $programType")

    when {
        iconName != null && isDHIS2IconAvailable(iconName) -> {
            // TODO: Implement DHIS2 icon rendering when icon library is available
            // For now, use fallback icon but with custom color if available
            android.util.Log.d("DatasetIcon", "Using DHIS2 icon path (fallback for now): $iconName")
            Icon(
                imageVector = fallbackIcon,
                contentDescription = getContentDescription(programType),
                modifier = modifier.size(size),
                tint = iconColor
            )
        }
        else -> {
            // Fallback to program-type-specific icon
            android.util.Log.d("DatasetIcon", "Using fallback icon for $programType with color: $iconColor")
            Icon(
                imageVector = fallbackIcon,
                contentDescription = getContentDescription(programType),
                modifier = modifier.size(size),
                tint = iconColor
            )
        }
    }
}

/**
 * Returns the appropriate fallback icon based on program type
 */
private fun getDefaultIconForProgramType(programType: ProgramType): ImageVector {
    return when (programType) {
        ProgramType.DATASET -> Icons.Default.BarChart  // Data collection/aggregation
        ProgramType.TRACKER_PROGRAM -> Icons.Default.Timeline  // Person/entity tracking over time
        ProgramType.EVENT_PROGRAM -> Icons.Default.EventNote  // Single events/services
    }
}

/**
 * Returns appropriate content description based on program type
 */
private fun getContentDescription(programType: ProgramType): String {
    return when (programType) {
        ProgramType.DATASET -> "Dataset Icon"
        ProgramType.TRACKER_PROGRAM -> "Tracker Program Icon"
        ProgramType.EVENT_PROGRAM -> "Event Program Icon"
    }
}

/**
 * Checks if a DHIS2 icon is available in the app
 * TODO: Implement actual DHIS2 icon lookup when icon library is integrated
 */
private fun isDHIS2IconAvailable(iconName: String): Boolean {
    // For now, return false to always use fallback
    // Future implementation will check against DHIS2 icon library
    // Common DHIS2 icon names to potentially support:
    // "positive_health_medium", "negative_health_medium", "child_health", "maternal_health", etc.
    return false
}

/**
 * Parses hex color string to Color object
 */
private fun parseHexColor(hexColor: String): Color? {
    return try {
        val cleanHex = hexColor.removePrefix("#")
        if (cleanHex.length == 6) {
            Color(android.graphics.Color.parseColor("#$cleanHex"))
        } else null
    } catch (e: Exception) {
        null
    }
}