package com.ash.simpledataentry.ui.theme

import androidx.compose.ui.graphics.Color

// Clean clinical palette: cool neutrals with a strong primary.
val BrandGreen = Color(0xFF16A34A)
val BrandGreenLight = Color(0xFFD1FAE5)

val ClinicalBlue = Color(0xFF0F6CBD)
val ClinicalBlueLight = Color(0xFF5BA3E6)
val ClinicalBlueDark = Color(0xFF0B4F8A)
val ClinicalBlueContainer = Color(0xFFD7E9FF)

// Legacy DHIS2 color aliases (keep existing usages compiling)
val DHIS2Blue = ClinicalBlue
val DHIS2BlueLight = ClinicalBlueLight
val DHIS2BlueDark = ClinicalBlueDark
val DHIS2BlueDeep = ClinicalBlueDark

// Cool neutral surfaces.
val NeutralBackground = Color(0xFFF5F7FA)
val NeutralSurface = Color(0xFFFFFFFF)
val NeutralSurfaceVariant = Color(0xFFE9EEF3)
val NeutralOutline = Color(0xFFCBD5E1)
val NeutralMuted = Color(0xFF64748B)
val NeutralOnSurface = Color(0xFF1B2430)

// Light theme colors
val Primary40 = ClinicalBlue
val PrimaryContainer40 = ClinicalBlueContainer
val Secondary40 = BrandGreen

// Dark theme colors
val Primary80 = ClinicalBlueLight
val PrimaryContainer80 = Color(0xFF19304A)
val Secondary80 = BrandGreenLight

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
val DatasetAccent = ClinicalBlue
val DatasetAccentLight = ClinicalBlueContainer

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
