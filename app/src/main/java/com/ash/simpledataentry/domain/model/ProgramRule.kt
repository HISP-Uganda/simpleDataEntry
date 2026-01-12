package com.ash.simpledataentry.domain.model

/**
 * Represents a DHIS2 program rule that controls form behavior dynamically
 * Rules are evaluated when field values change to show/hide fields, calculate values, etc.
 */
data class ProgramRule(
    val id: String,
    val name: String,
    val condition: String,              // D2 expression (e.g., "#{dataElement} > 5")
    val actions: List<ProgramRuleAction>,
    val priority: Int = 0,              // Higher priority rules execute first
    val programId: String? = null,      // For tracker programs
    val programStageId: String? = null  // For specific program stages
)

/**
 * Types of actions a program rule can perform
 */
enum class ProgramRuleActionType {
    SHOW_FIELD,         // Make a field visible
    HIDE_FIELD,         // Hide a field from view
    ASSIGN_VALUE,       // Set a calculated value
    SHOW_WARNING,       // Display a warning message
    SHOW_ERROR,         // Display an error message
    MAKE_MANDATORY,     // Make a field required
    MAKE_OPTIONAL,      // Make a field optional
    DISPLAY_TEXT,       // Show informational text
    DISPLAY_KEY_VALUE_PAIR, // Show key-value pair
    HIDE_SECTION,       // Hide entire section
    SHOW_SECTION,       // Show entire section
    SET_MANDATORY_FIELD, // Alternative to MAKE_MANDATORY
    ERROR_ON_COMPLETE   // Show error preventing completion
}

/**
 * Individual action within a program rule
 */
data class ProgramRuleAction(
    val type: ProgramRuleActionType,
    val dataElementId: String?,        // Target data element (can be null for display actions)
    val value: String? = null,          // Calculated value or expression
    val message: String? = null,        // Warning/error message text
    val content: String? = null,        // Content for display actions
    val attributeType: String? = null,  // For attribute-based actions
    val optionGroupId: String? = null,  // For option group filters
    val sectionId: String? = null       // For section-level actions
)

/**
 * Result of evaluating program rules - effects to apply to the form
 */
data class ProgramRuleEffect(
    val hiddenFields: Set<String> = emptySet(),
    val disabledFields: Set<String> = emptySet(),
    val mandatoryFields: Set<String> = emptySet(),
    val fieldWarnings: Map<String, String> = emptyMap(),
    val fieldErrors: Map<String, String> = emptyMap(),
    val calculatedValues: Map<String, String> = emptyMap(),
    val displayKeyValuePairs: Map<String, String> = emptyMap(),
    val displayTexts: List<String> = emptyList(),
    val hiddenSections: Set<String> = emptySet()
)
