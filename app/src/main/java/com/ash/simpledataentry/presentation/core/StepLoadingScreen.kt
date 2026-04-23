package com.ash.simpledataentry.presentation.core

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DynamicForm
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ash.simpledataentry.ui.theme.DHIS2Blue
import com.ash.simpledataentry.ui.theme.DHIS2BlueDark
import com.ash.simpledataentry.ui.theme.DatasetAccent
import androidx.core.view.WindowInsetsControllerCompat
import android.app.Activity

enum class StepLoadingType {
    LOGIN,
    ENTRY,
    SYNC,
    VALIDATION
}

private data class StepLoadingStep(val label: String)

@Composable
fun StepLoadingScreen(
    type: StepLoadingType,
    currentStep: Int,
    progressPercent: Int,
    currentLabel: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val isDarkTheme = isSystemInDarkTheme()
    val statusBarColor = if (isDarkTheme) Color.Black else Color.White
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val colorInt = statusBarColor.toArgb()
        window.statusBarColor = colorInt
        window.navigationBarColor = colorInt
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDarkTheme
        insetsController.isAppearanceLightNavigationBars = !isDarkTheme
    }
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
        StepLoadingType.VALIDATION -> listOf(
            StepLoadingStep("Preparing validation..."),
            StepLoadingStep("Running validation rules..."),
            StepLoadingStep("Reviewing results..."),
            StepLoadingStep("Finalizing...")
        )
    }

    val safeStep = currentStep.coerceIn(0, steps.lastIndex)
    val isComplete = safeStep >= steps.lastIndex && progressPercent >= 100
    val sanitizedLabel = currentLabel
        ?.takeIf { it.isNotBlank() }
        ?.takeUnless { it.trim().matches(Regex("^Progress:\\s*\\d+%$")) }
    val displayLabel = sanitizedLabel ?: steps[safeStep].label
    val pulseTransition = rememberInfiniteTransition(label = "step_pulse")
    val iconScale by pulseTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_scale"
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
                .widthIn(max = 760.dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .shadow(12.dp, CircleShape)
                    .background(Color.White.copy(alpha = 0.95f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val icon = when {
                    isComplete -> Icons.Default.CheckCircle
                    type == StepLoadingType.SYNC -> Icons.Default.CloudDownload
                    type == StepLoadingType.ENTRY -> Icons.Default.DynamicForm
                    type == StepLoadingType.VALIDATION -> Icons.Default.CheckCircle
                    else -> Icons.Default.Storage
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isComplete) DHIS2Blue else DatasetAccent,
                    modifier = Modifier
                        .size(50.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                        .background(Color.Transparent, CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = when (type) {
                    StepLoadingType.LOGIN -> "Setting Up Your Workspace"
                    StepLoadingType.ENTRY -> "Preparing Form"
                    StepLoadingType.SYNC -> "Syncing Data"
                    StepLoadingType.VALIDATION -> "Validating Data"
                },
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = displayLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF0A1B2D).copy(alpha = 0.92f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${safeStep + 1} / ${steps.size}",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "${progressPercent.coerceIn(0, 100)}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )

                    LinearProgressIndicator(
                        progress = { progressPercent.coerceIn(0, 100) / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = DHIS2Blue,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = actionLabel)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = "Please do not close the app",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}
