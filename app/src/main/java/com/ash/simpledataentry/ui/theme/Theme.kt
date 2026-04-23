package com.ash.simpledataentry.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkColorScheme = darkColorScheme(
    primary = Primary80,
    onPrimary = NeutralOnSurface,
    primaryContainer = PrimaryContainer80,
    onPrimaryContainer = Primary80,
    secondary = Secondary80,
    onSecondary = NeutralOnSurface,
    background = Color(0xFF101214),
    onBackground = Color(0xFFE6E8EC),
    surface = Color(0xFF101214),
    onSurface = Color(0xFFE6E8EC),
    surfaceVariant = Color(0xFF1E2226),
    onSurfaceVariant = Color(0xFFD3D7DD),
    outline = Color(0xFF3F4650)
)

private val LightColorScheme = lightColorScheme(
    primary = Primary40,
    onPrimary = Color.White,
    primaryContainer = PrimaryContainer40,
    onPrimaryContainer = NeutralOnSurface,
    secondary = Secondary40,
    onSecondary = Color.White,
    background = NeutralBackground,
    onBackground = NeutralOnSurface,
    surface = NeutralSurface,
    onSurface = NeutralOnSurface,
    surfaceVariant = NeutralSurfaceVariant,
    onSurfaceVariant = NeutralMuted,
    outline = NeutralOutline
)

@Composable
fun SimpleDataEntryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val formColors = if (darkTheme) {
        FormColors(
            gridHeaderBackground = colorScheme.surfaceVariant,
            gridHeaderText = colorScheme.onSurfaceVariant,
            gridRowHeaderBackground = colorScheme.surfaceVariant.copy(alpha = 0.8f),
            gridRowHeaderText = colorScheme.onSurfaceVariant,
            gridCellBackground = colorScheme.surface,
            gridCellAltBackground = colorScheme.surfaceVariant.copy(alpha = 0.5f),
            gridCellText = colorScheme.onSurface,
            gridCellPlaceholder = colorScheme.onSurfaceVariant,
            gridBorder = colorScheme.outline.copy(alpha = 0.5f),
            gridBorderFocused = colorScheme.primary,
            gridBorderError = colorScheme.error
        )
    } else {
        FormColors(
            gridHeaderBackground = colorScheme.surfaceVariant,
            gridHeaderText = colorScheme.onSurfaceVariant,
            gridRowHeaderBackground = colorScheme.surfaceVariant.copy(alpha = 0.6f),
            gridRowHeaderText = colorScheme.onSurfaceVariant,
            gridCellBackground = colorScheme.surface,
            gridCellAltBackground = colorScheme.surfaceVariant.copy(alpha = 0.35f),
            gridCellText = colorScheme.onSurface,
            gridCellPlaceholder = colorScheme.onSurfaceVariant,
            gridBorder = colorScheme.outline.copy(alpha = 0.6f),
            gridBorderFocused = colorScheme.primary,
            gridBorderError = colorScheme.error
        )
    }

    val formDimensions = FormDimensions(
        rowHorizontalPadding = 16.dp,
        rowVerticalPadding = 6.dp,
        sectionCornerRadius = 14.dp,
        fieldCornerRadius = 10.dp,
        gridCellHeight = 52.dp
    )

    val formTypography = FormTypography(
        gridHeader = Typography.labelMedium.copy(
            fontSize = 12.sp,
            letterSpacing = 0.3.sp
        ),
        gridRowHeader = Typography.labelLarge.copy(
            fontSize = 13.sp
        ),
        gridCell = Typography.bodyLarge.copy(
            fontSize = 16.sp
        ),
        sectionTitle = Typography.titleMedium.copy(
            fontSize = 16.sp
        )
    )

    CompositionLocalProvider(
        LocalFormColors provides formColors,
        LocalFormDimensions provides formDimensions,
        LocalFormTypography provides formTypography
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}
