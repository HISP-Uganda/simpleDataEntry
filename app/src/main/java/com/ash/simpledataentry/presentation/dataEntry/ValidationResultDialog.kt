package com.ash.simpledataentry.presentation.dataEntry

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ash.simpledataentry.domain.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValidationResultDialog(
    validationSummary: ValidationSummary,
    onDismiss: () -> Unit,
    onTryAgain: (() -> Unit)? = null,
    onComplete: (() -> Unit)? = null,
    onCompleteAnyway: (() -> Unit)? = null,
    showCompletionOption: Boolean = false
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header Section (matching CompletionActionDialog style)
                ValidationHeaderSection(validationSummary)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Summary Statistics
                    ValidationSummaryCard(validationSummary)

                    // Tab selection for issues
                    if (validationSummary.hasIssues) {
                    val tabs = buildList {
                        if (validationSummary.hasErrors) add("Errors (${validationSummary.errorCount})")
                        if (validationSummary.hasWarnings) add("Warnings (${validationSummary.warningCount})")
                    }
                    
                    if (tabs.size > 1) {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            tabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    text = { 
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Issues List
                        ValidationIssuesList(
                            validationSummary = validationSummary,
                            selectedTab = selectedTab
                        )
                    }
                }

                // Action Buttons (matching CompletionActionDialog style)
                ValidationDialogButtons(
                    validationSummary = validationSummary,
                    showCompletionOption = showCompletionOption,
                    onComplete = onComplete,
                    onCompleteAnyway = onCompleteAnyway,
                    onTryAgain = onTryAgain,
                    onDismiss = onDismiss
                )
            }
        }
    }
}

@Composable
private fun ValidationDialogButtons(
    validationSummary: ValidationSummary,
    showCompletionOption: Boolean,
    onComplete: (() -> Unit)?,
    onCompleteAnyway: (() -> Unit)?,
    onTryAgain: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
    ) {
        // Try Again button (leftmost)
        if (onTryAgain != null) {
            TextButton(onClick = onTryAgain) {
                Text("Try Again")
            }
        }

        // Cancel/Close button
        TextButton(onClick = onDismiss) {
            Text(if (showCompletionOption) "Cancel" else "Close")
        }

        // Completion buttons
        if (showCompletionOption) {
            if (validationSummary.canComplete) {
                // If validation passed or has only warnings, show Complete button
                Button(
                    onClick = { onComplete?.invoke() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Complete")
                }
            } else {
                // If validation failed with errors, show Complete Anyway button
                Button(
                    onClick = { onCompleteAnyway?.invoke() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Complete Anyway")
                }
            }
        }
    }
}

@Composable
private fun ValidationHeaderSection(validationSummary: ValidationSummary) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val (icon, color, title) = when {
            !validationSummary.hasIssues -> Triple(
                Icons.Default.CheckCircle,
                MaterialTheme.colorScheme.primary,
                "Validation Passed"
            )
            validationSummary.hasErrors -> Triple(
                Icons.Default.Error,
                MaterialTheme.colorScheme.error,
                "Validation Failed"
            )
            else -> Triple(
                Icons.Default.Warning,
                MaterialTheme.colorScheme.secondary,
                "Validation Warning"
            )
        }

        val subtitle = when {
            !validationSummary.hasIssues -> "All validation rules passed successfully"
            validationSummary.hasErrors -> "Found ${validationSummary.errorCount} error(s) that need attention"
            else -> "Found ${validationSummary.warningCount} warning(s) to review"
        }

        // Status Icon (matching CompletionActionDialog size)
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
        }

        // Title and Status
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@Composable
private fun ValidationSummaryCard(validationSummary: ValidationSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Validation Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryStatItem(
                    value = validationSummary.totalRulesChecked.toString(),
                    label = "Total Rules",
                    color = MaterialTheme.colorScheme.primary
                )
                
                SummaryStatItem(
                    value = validationSummary.passedRules.toString(),
                    label = "Passed",
                    color = MaterialTheme.colorScheme.tertiary
                )
                
                if (validationSummary.hasErrors) {
                    SummaryStatItem(
                        value = validationSummary.errorCount.toString(),
                        label = "Errors",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                if (validationSummary.hasWarnings) {
                    SummaryStatItem(
                        value = validationSummary.warningCount.toString(),
                        label = "Warnings",
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            
            // Completion status
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (validationSummary.canComplete) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (validationSummary.canComplete) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.Error
                        },
                        contentDescription = null,
                        tint = if (validationSummary.canComplete) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    
                    Text(
                        text = if (validationSummary.canComplete) {
                            "Dataset can be completed"
                        } else {
                            "Cannot complete dataset - fix errors first"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (validationSummary.canComplete) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryStatItem(
    value: String,
    label: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ValidationIssuesList(
    validationSummary: ValidationSummary,
    selectedTab: Int
) {
    val issues = when (validationSummary.validationResult) {
        is ValidationResult.Error -> {
            if (selectedTab == 0) validationSummary.validationResult.errors else emptyList()
        }
        is ValidationResult.Warning -> {
            if (selectedTab == 0) validationSummary.validationResult.warnings else emptyList()
        }
        is ValidationResult.Mixed -> {
            when (selectedTab) {
                0 -> validationSummary.validationResult.errors
                1 -> validationSummary.validationResult.warnings
                else -> emptyList()
            }
        }
        is ValidationResult.Success -> emptyList()
    }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(issues) { issue ->
            ValidationIssueItem(issue)
        }
    }
}

@Composable
private fun ValidationIssueItem(issue: ValidationIssue) {
    val (icon, color) = when (issue.severity) {
        ValidationSeverity.ERROR -> Icons.Default.Error to MaterialTheme.colorScheme.error
        ValidationSeverity.WARNING -> Icons.Default.Warning to MaterialTheme.colorScheme.secondary
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.05f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = color
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = issue.ruleName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}