package com.ash.simpledataentry.presentation.tracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ash.simpledataentry.domain.model.ProgramStage

/**
 * Dialog for selecting a program stage when creating a new event
 * Shows all available program stages with their metadata and event counts
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramStageSelectionDialog(
    programStages: List<ProgramStage>,
    existingEventCounts: Map<String, Int>,  // Map of programStageId to event count
    onStageSelected: (ProgramStage) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Program Stage",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(programStages.sortedBy { it.sortOrder }) { stage ->
                    ProgramStageCard(
                        stage = stage,
                        eventCount = existingEventCounts[stage.id] ?: 0,
                        onSelect = { onStageSelected(stage) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Card displaying a single program stage option
 */
@Composable
private fun ProgramStageCard(
    stage: ProgramStage,
    eventCount: Int,
    onSelect: () -> Unit
) {
    val isDisabled = !stage.repeatable && eventCount > 0
    val backgroundColor = if (isDisabled) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        onClick = {
            if (!isDisabled) {
                onSelect()
            }
        },
        enabled = !isDisabled,
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Event,
                contentDescription = null,
                tint = if (isDisabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stage.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDisabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                if (stage.description != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stage.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Repeatable badge
                    if (stage.repeatable) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "Repeatable",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "One-time",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Event count badge
                    if (eventCount > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "$eventCount event${if (eventCount > 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Show message if disabled
                if (isDisabled) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Event already exists for this stage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Checkmark icon if event exists
            if (eventCount > 0 && !isDisabled) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Has events",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
