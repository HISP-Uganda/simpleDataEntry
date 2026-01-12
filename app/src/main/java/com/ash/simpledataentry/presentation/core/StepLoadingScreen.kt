package com.ash.simpledataentry.presentation.core

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DynamicForm
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ash.simpledataentry.ui.theme.DHIS2Blue
import com.ash.simpledataentry.ui.theme.DHIS2BlueDark
import com.ash.simpledataentry.ui.theme.DatasetAccent

enum class StepLoadingType {
    LOGIN,
    ENTRY,
    SYNC
}

private data class StepLoadingStep(val label: String)

@Composable
fun StepLoadingScreen(
    type: StepLoadingType,
    currentStep: Int,
    progressPercent: Int,
    currentLabel: String? = null,
    modifier: Modifier = Modifier
) {
    val steps = when (type) {
        StepLoadingType.LOGIN -> listOf(
            StepLoadingStep("Initializing"),
            StepLoadingStep("Authenticating"),
            StepLoadingStep("Downloading metadata"),
            StepLoadingStep("Loading data"),
            StepLoadingStep("Processing data"),
            StepLoadingStep("Finalizing")
        )
        StepLoadingType.ENTRY -> listOf(
            StepLoadingStep("Loading form structure..."),
            StepLoadingStep("Preparing data fields..."),
            StepLoadingStep("Ready!")
        )
        StepLoadingType.SYNC -> listOf(
            StepLoadingStep("Uploading local changes..."),
            StepLoadingStep("Fetching updates..."),
            StepLoadingStep("Syncing complete!")
        )
    }

    val safeStep = currentStep.coerceIn(0, steps.lastIndex)
    val isComplete = safeStep >= steps.lastIndex && progressPercent >= 100
    val pulseTransition = rememberInfiniteTransition(label = "step_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DHIS2Blue, DHIS2BlueDark)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .shadow(12.dp, CircleShape)
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val icon = when {
                    isComplete -> Icons.Default.CheckCircle
                    type == StepLoadingType.SYNC -> Icons.Default.CloudDownload
                    type == StepLoadingType.ENTRY -> Icons.Default.DynamicForm
                    else -> Icons.Default.Storage
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isComplete) DHIS2Blue else DatasetAccent,
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = when (type) {
                    StepLoadingType.LOGIN -> "Setting Up Your Workspace"
                    StepLoadingType.ENTRY -> "Preparing Form"
                    StepLoadingType.SYNC -> "Syncing Data"
                },
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = currentLabel?.takeIf { it.isNotBlank() } ?: steps[safeStep].label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { progressPercent.coerceIn(0, 100) / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = DHIS2Blue,
                        trackColor = Color(0xFFE5E7EB)
                    )

                    steps.forEachIndexed { index, step ->
                        val stepColor = when {
                            index < safeStep -> DHIS2Blue
                            index == safeStep -> DatasetAccent
                            else -> Color(0xFFD1D5DB)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(stepColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (index < safeStep) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                } else if (index == safeStep && !isComplete) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(
                                                Color.White.copy(alpha = pulseAlpha),
                                                CircleShape
                                            )
                                    )
                                }
                            }
                            Text(
                                text = step.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (index <= safeStep) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Please do not close the app",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}
