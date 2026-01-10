package com.ash.simpledataentry.ui.theme

import androidx.compose.ui.graphics.Color

// Primary brand and neutral tones inspired by the design-system bundle.
val BrandGreen = Color(0xFF16A34A)
val BrandGreenDark = Color(0xFF15803D)
val BrandGreenLight = Color(0xFFD1FAE5)

// DHIS2 blue remains available for secondary accents and legacy usage.
val DHIS2Blue = Color(0xFF0073E7)
val DHIS2BlueLight = Color(0xFF4A90E2)
val DHIS2BlueDark = Color(0xFF004BA0)
val DHIS2BlueDeep = Color(0xFF1976D2)

// Neutral surfaces (matching design-system theme.css intent).
val NeutralBackground = Color(0xFFFFFFFF)
val NeutralSurface = Color(0xFFFFFFFF)
val NeutralSurfaceVariant = Color(0xFFECECF0)
val NeutralOutline = Color(0x1A000000) // 10% black
val NeutralMuted = Color(0xFF717182)
val NeutralOnSurface = Color(0xFF0F0F12)

// Light theme colors
val Primary40 = DHIS2Blue
val PrimaryContainer40 = Color(0xFFDBEAFE)  // Light blue
val Secondary40 = BrandGreen  // Green available as secondary accent

// Dark theme colors
val Primary80 = DHIS2BlueLight
val PrimaryContainer80 = Color(0xFF1E3A5F)  // Dark blue
val Secondary80 = BrandGreenLight  // Green as secondary accent

// Legacy colors for compatibility
val Purple80 = Primary80
val PurpleGrey80 = Secondary80
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Primary40
val PurpleGrey40 = Secondary40
val Pink40 = Color(0xFF7D5260)

// ============================================
// Semantic Color Tokens - Program Types
// ============================================

// Dataset accent (blue)
val DatasetAccent = Color(0xFF2563EB)        // blue-600
val DatasetAccentLight = Color(0xFFDBEAFE)   // blue-100

// Event accent (orange)
val EventAccent = Color(0xFFEA580C)          // orange-600
val EventAccentLight = Color(0xFFFFEDD5)     // orange-100

// Tracker accent (purple)
val TrackerAccent = Color(0xFF7C3AED)        // purple-600
val TrackerAccentLight = Color(0xFFEDE9FE)   // purple-100

// ============================================
// Semantic Color Tokens - Status Colors
// ============================================

// Draft status (orange/yellow)
val StatusDraft = Color(0xFFFFA726)          // orange
val StatusDraftLight = Color(0xFFFFF3E0)     // orange-50

// Completed status (blue)
val StatusCompleted = Color(0xFF2563EB)      // blue-600
val StatusCompletedLight = Color(0xFFDBEAFE) // blue-100

// Synced status (green)
val StatusSynced = Color(0xFF16A34A)         // green-600
val StatusSyncedLight = Color(0xFFD1FAE5)    // green-100

// Error/Cancelled status (red)
val StatusError = Color(0xFFEF5350)          // red
val StatusErrorLight = Color(0xFFFFEBEE)     // red-50
