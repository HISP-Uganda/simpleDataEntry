package com.ash.simpledataentry.domain.model

/**
 * Represents an inferred category structure from data element names
 * Used for event/tracker programs which don't have DHIS2 category combinations
 *
 * Example: "ANC Visit - First Trimester - Blood Pressure"
 * Could infer: Category1 = "ANC Visit", Category2 = "First Trimester", Field = "Blood Pressure"
 */
data class ImpliedCategory(
    val name: String,              // Category name (e.g., "ANC Visit", "Age Group")
    val options: List<String>,     // Category options (e.g., ["First Trimester", "Second Trimester"])
    val level: Int,                // Nesting level (0 = top level, 1 = second level, etc.)
    val separator: String          // Detected separator (e.g., "-", "_", "|")
)

/**
 * Represents a complete inferred category combination for a section
 */
data class ImpliedCategoryCombination(
    val categories: List<ImpliedCategory>,  // Ordered list of inferred categories (outer to inner)
    val confidence: Double,                  // Confidence score (0.0 to 1.0)
    val pattern: CategoryPattern,            // Detected naming pattern
    val totalDataElements: Int,              // Total data elements analyzed
    val structuredDataElements: Int          // Data elements that fit the pattern
)

/**
 * Detected naming pattern types
 */
enum class CategoryPattern {
    HIERARCHICAL,      // "Category1 - Category2 - Field" (most common)
    PREFIX_GROUPED,    // "Prefix: Field1", "Prefix: Field2"
    UNDERSCORE_DELIM,  // "Category1_Category2_Field"
    PIPE_DELIM,        // "Category1 | Category2 | Field"
    FLAT              // No detectable structure
}

/**
 * Maps a data element to its implied category options
 */
data class ImpliedCategoryMapping(
    val dataElementId: String,
    val dataElementName: String,
    val categoryOptionsByLevel: Map<Int, String>,  // Level -> Option name
    val fieldName: String                           // The actual field name after categories
)
