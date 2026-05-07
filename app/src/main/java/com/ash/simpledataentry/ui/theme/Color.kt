package com.ash.simpledataentry.ui.theme

import androidx.compose.ui.graphics.Color

// DHIS2-like blue palette.
val BrandGreen = Color(0xFF2E7D32)
val BrandGreenLight = Color(0xFFC8E6C9)

val ClinicalBlue = Color(0xFF1976D2)
val ClinicalBlueLight = Color(0xFF64B5F6)
val ClinicalBlueDark = Color(0xFF0D47A1)
val ClinicalBlueContainer = Color(0xFFD7E8FF)

// Legacy DHIS2 color aliases (keep existing usages compiling)
val DHIS2Blue = ClinicalBlue
val DHIS2BlueLight = ClinicalBlueLight
val DHIS2BlueDark = ClinicalBlueDark
val DHIS2BlueDeep = ClinicalBlueDark

// Light gray neutrals with better contrast.
val NeutralBackground = Color(0xFFF3F7FC)
val NeutralSurface = Color(0xFFFFFFFF)
val NeutralSurfaceVariant = Color(0xFFE3ECF6)
val NeutralOutline = Color(0xFFB6C6DB)
val NeutralMuted = Color(0xFF4A617D)
val NeutralOnSurface = Color(0xFF1F2A37)

// Light theme colors
val Primary40 = ClinicalBlue
val PrimaryContainer40 = ClinicalBlueContainer
val Secondary40 = BrandGreen

// Dark theme colors
val Primary80 = ClinicalBlueLight
val PrimaryContainer80 = Color(0xFF103B78)
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

// Event accent (teal)
val EventAccent = Color(0xFF0F9D8A)
val EventAccentLight = Color(0xFFDDF5F1)

// Tracker accent (slate-blue)
val TrackerAccent = Color(0xFF3B82C4)
val TrackerAccentLight = Color(0xFFE1EFFB)

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
