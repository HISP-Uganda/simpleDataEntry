package com.ash.simpledataentry.presentation.dataEntry

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
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
import com.ash.simpledataentry.presentation.core.CompletionAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletionActionDialog(
    isCurrentlyComplete: Boolean,
    onAction: (CompletionAction) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedAction by remember { mutableStateOf<CompletionAction?>(null) }

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
                // Header Section
                CompletionDialogHeader(isCurrentlyComplete)

                // Action Cards
                if (isCurrentlyComplete) {
                    CompletedDatasetActions(
                        selectedAction = selectedAction,
                        onActionSelected = { selectedAction = it }
                    )
                } else {
                    IncompleteDatasetActions(
                        selectedAction = selectedAction,
                        onActionSelected = { selectedAction = it }
                    )
                }

                // Action Buttons
                CompletionDialogButtons(
                    selectedAction = selectedAction,
                    onConfirm = { selectedAction?.let(onAction) },
                    onDismiss = onDismiss
                )
            }
        }
    }
}

@Composable
private fun CompletionDialogHeader(isCurrentlyComplete: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Icon
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    if (isCurrentlyComplete) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isCurrentlyComplete) Icons.Default.CheckCircle else Icons.Default.Edit,
                contentDescription = null,
                tint = if (isCurrentlyComplete) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(28.dp)
            )
        }

        // Title and Status
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isCurrentlyComplete) "Dataset Complete" else "Complete Dataset",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isCurrentlyComplete) {
                    "This dataset is marked as complete"
                } else {
                    "Choose how to complete this dataset"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IncompleteDatasetActions(
    selectedAction: CompletionAction?,
    onActionSelected: (CompletionAction) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionCard(
            title = "Run Validation & Complete",
            description = "Check data quality before marking complete",
            icon = Icons.Default.Verified,
            isPrimary = true,
            isSelected = selectedAction == CompletionAction.VALIDATE_AND_COMPLETE,
            onClick = { onActionSelected(CompletionAction.VALIDATE_AND_COMPLETE) }
        )

        ActionCard(
            title = "Complete Without Validation",
            description = "Mark as complete immediately",
            icon = Icons.Default.Check,
            isPrimary = false,
            isSelected = selectedAction == CompletionAction.COMPLETE_WITHOUT_VALIDATION,
            onClick = { onActionSelected(CompletionAction.COMPLETE_WITHOUT_VALIDATION) }
        )
    }
}

@Composable
private fun CompletedDatasetActions(
    selectedAction: CompletionAction?,
    onActionSelected: (CompletionAction) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionCard(
            title = "Re-run Validation",
            description = "Check current data against validation rules",
            icon = Icons.Default.Refresh,
            isPrimary = true,
            isSelected = selectedAction == CompletionAction.RERUN_VALIDATION,
            onClick = { onActionSelected(CompletionAction.RERUN_VALIDATION) }
        )

        ActionCard(
            title = "Mark as Incomplete",
            description = "Allow editing by marking as incomplete",
            icon = Icons.Default.Edit,
            isPrimary = false,
            isSelected = selectedAction == CompletionAction.MARK_INCOMPLETE,
            onClick = { onActionSelected(CompletionAction.MARK_INCOMPLETE) }
        )
    }
}

@Composable
private fun ActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    isPrimary: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = when {
        isSelected && isPrimary -> MaterialTheme.colorScheme.primary
        isSelected -> MaterialTheme.colorScheme.secondary
        else -> Color.Transparent
    }

    val backgroundColor = when {
        isSelected && isPrimary -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        isSelected -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    AnimatedVisibility(
        visible = true,
        enter = scaleIn(animationSpec = tween(200)) + fadeIn(),
        exit = scaleOut(animationSpec = tween(200)) + fadeOut()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(12.dp)
                ),
            colors = CardDefaults.cardColors(
                containerColor = backgroundColor
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isSelected) 4.dp else 2.dp
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Action Icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isSelected && isPrimary) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            } else if (isSelected) {
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = when {
                            isSelected && isPrimary -> MaterialTheme.colorScheme.primary
                            isSelected -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Action Text
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Selection Indicator
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Selected",
                        tint = if (isPrimary) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondary
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletionDialogButtons(
    selectedAction: CompletionAction?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
    ) {
        TextButton(onClick = onDismiss) {
            Text("Cancel")
        }

        Button(
            onClick = onConfirm,
            enabled = selectedAction != null,
            colors = ButtonDefaults.buttonColors(
                containerColor = when (selectedAction) {
                    CompletionAction.VALIDATE_AND_COMPLETE, CompletionAction.RERUN_VALIDATION ->
                        MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.secondary
                }
            )
        ) {
            Text(
                text = when (selectedAction) {
                    CompletionAction.VALIDATE_AND_COMPLETE -> "Validate & Complete"
                    CompletionAction.COMPLETE_WITHOUT_VALIDATION -> "Complete Now"
                    CompletionAction.RERUN_VALIDATION -> "Run Validation"
                    CompletionAction.MARK_INCOMPLETE -> "Mark Incomplete"
                    null -> "Select Action"
                }
            )
        }
    }
}