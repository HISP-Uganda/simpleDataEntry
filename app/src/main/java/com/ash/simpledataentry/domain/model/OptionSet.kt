package com.ash.simpledataentry.domain.model

/**
 * Represents a DHIS2 option set - a predefined list of options for a data element
 * Used for rendering dropdowns, radio buttons, or other selection controls
 */
data class OptionSet(
    val id: String,
    val name: String,
    val displayName: String? = null,
    val options: List<Option>,
    val valueType: ValueType = ValueType.TEXT
)

/**
 * Individual option within an option set
 */
data class Option(
    val code: String,              // The value stored in DHIS2
    val name: String,               // Display name for the option
    val displayName: String? = null, // Alternative display name
    val icon: String? = null,       // Optional icon identifier for visual rendering
    val color: String? = null,      // Optional color hex code for visual rendering
    val sortOrder: Int = 0          // Order in which option should appear
)

/**
 * Determines how an option set should be rendered in the UI
 */
enum class RenderType {
    /**
     * Automatically determine based on option count and metadata
     * - ≤2 options: YES_NO_BUTTONS
     * - ≤4 options: RADIO_BUTTONS
     * - Has icons: ICON_PALETTE
     * - Otherwise: DROPDOWN
     */
    DEFAULT,

    /**
     * Always render as dropdown/select menu
     * Best for: >4 options, searchable lists
     */
    DROPDOWN,

    /**
     * Render as horizontal/vertical radio button group
     * Best for: 2-4 mutually exclusive options
     */
    RADIO_BUTTONS,

    /**
     * Render as checkbox group (for multi-select)
     * Best for: Multiple selections allowed
     */
    CHECKBOX,

    /**
     * Special rendering for YES/NO boolean options
     * Visual toggle or button pair
     */
    YES_NO_BUTTONS,

    /**
     * Grid of icon-based options
     * Best for: Options with icon metadata (e.g., emojis, symbols)
     */
    ICON_PALETTE
}

/**
 * Compute the optimal render type for an option set based on its characteristics
 */
fun OptionSet.computeRenderType(): RenderType {
    return when {
        // Boolean/Yes-No options
        options.size == 2 && options.all {
            it.code.uppercase() in listOf("YES", "NO", "TRUE", "FALSE", "1", "0")
        } -> RenderType.YES_NO_BUTTONS

        // Small option sets work well as radio buttons
        options.size <= 4 -> RenderType.RADIO_BUTTONS

        // Option sets with icons should use visual palette
        options.any { it.icon != null } -> RenderType.ICON_PALETTE

        // Default to dropdown for larger sets
        else -> RenderType.DROPDOWN
    }
}

/**
 * Get sorted options respecting sortOrder field
 */
fun OptionSet.getSortedOptions(): List<Option> {
    return options.sortedBy { it.sortOrder }
}

/**
 * Find option by code
 */
fun OptionSet.findOptionByCode(code: String): Option? {
    return options.firstOrNull { it.code == code }
}

/**
 * Get display name for an option code
 */
fun OptionSet.getDisplayNameForCode(code: String): String? {
    return findOptionByCode(code)?.let { it.displayName ?: it.name }
}
