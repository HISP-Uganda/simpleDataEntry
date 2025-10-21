package com.ash.simpledataentry.domain.model

/**
 * Represents different confidence levels for grouping strategies
 */
enum class ConfidenceLevel {
    HIGH,      // Use DHIS2 metadata definitively (e.g., category combos)
    MEDIUM,    // Pattern-based inference (e.g., dimensional patterns, option set analysis)
    LOW        // Fallback heuristics (e.g., semantic clustering)
}

/**
 * Represents different types of data element groupings
 */
enum class GroupType {
    RADIO_GROUP,           // Mutually exclusive selection (radio buttons)
    CHECKBOX_GROUP,        // Multiple selection allowed (checkboxes)
    DIMENSIONAL_GRID,      // Multi-dimensional data (e.g., age × gender)
    SEMANTIC_CLUSTER,      // Related but independent fields grouped by semantic similarity
    FLAT_LIST             // No grouping - render as flat list
}

/**
 * Metadata about a grouping strategy
 */
data class GroupMetadata(
    val categoryComboUid: String? = null,
    val categoryComboStructure: List<Pair<String, List<Pair<String, String>>>>? = null,
    val dimensionalPattern: DimensionalPattern? = null,
    val inferredCategoryCombo: InferredCategoryCombo? = null,
    val mutualExclusivityScore: Float? = null,
    val semanticSimilarityScore: Float? = null,
    val detectionMethod: String = "",
    val numericConfidenceScore: Float? = null  // IMPROVEMENT 5: Numeric confidence (0.0-1.0) for granular ranking
) {
    companion object {
        fun empty() = GroupMetadata()
    }
}

/**
 * Represents a dimensional pattern extracted from data element names
 */
data class DimensionalPattern(
    val baseName: String,           // e.g., "Number of Pupil Fed"
    val dimensions: List<Dimension>  // e.g., [Grade, Status, Gender]
)

/**
 * Represents a single dimension in a dimensional pattern
 */
data class Dimension(
    val name: String,        // e.g., "Grade Level"
    val values: Set<String>, // e.g., ["P1", "P2", "P3", ...]
    val order: Int           // Natural ordering
)

/**
 * Represents an inferred category combination extracted from dimensional patterns
 * This allows us to present DHIS2-like category structure even when not explicitly defined
 */
data class InferredCategoryCombo(
    val name: String,                           // e.g., "Grade × Status × Gender"
    val categories: List<InferredCategory>,     // The detected dimensions as categories
    val totalExpectedCombinations: Int,         // Expected number of data elements (product of category option counts)
    val actualCombinations: Int,                // Actual number of data elements found
    val completenessRatio: Float,               // actualCombinations / totalExpectedCombinations
    val isConditional: Boolean = false,         // True if some dimension values depend on others (e.g., Disabled replaces Day/Boarding)
    val conditionalRules: List<String> = emptyList(), // Human-readable rules like "If Disabled, no Boarding Status"
    val appliedToDataElements: List<String>     // UIDs of data elements using this inferred catcombo
)

/**
 * Represents a single inferred category (dimension) within an inferred category combo
 */
data class InferredCategory(
    val name: String,                           // e.g., "Student Status"
    val categoryOptions: List<String>,          // e.g., ["Day", "Boarding", "Disabled"]
    val optionCount: Int,                       // Number of options
    val detectionMethod: String                 // How we identified this dimension
)

/**
 * Main grouping strategy that determines how data elements should be grouped and rendered
 */
data class GroupingStrategy(
    val confidence: ConfidenceLevel,
    val groupType: GroupType,
    val groupTitle: String,
    val members: List<DataValue>,
    val metadata: GroupMetadata = GroupMetadata.empty()
) {
    /**
     * Returns true if this grouping should be rendered with visual grouping
     */
    fun shouldRenderAsGroup(): Boolean {
        return when (groupType) {
            GroupType.FLAT_LIST -> false
            else -> members.size >= 2
        }
    }

    /**
     * Returns true if this is a high-confidence grouping based on DHIS2 metadata
     */
    fun isDefinitive(): Boolean {
        return confidence == ConfidenceLevel.HIGH
    }

    /**
     * Returns a user-friendly description of how this grouping was detected
     */
    fun getDetectionDescription(): String {
        return when {
            isDefinitive() -> "Grouped by DHIS2 category combination"
            metadata.dimensionalPattern != null -> "Detected ${metadata.dimensionalPattern.dimensions.size}-dimensional pattern"
            metadata.mutualExclusivityScore != null -> {
                if (groupType == GroupType.RADIO_GROUP) {
                    "Detected mutually exclusive options (${(metadata.mutualExclusivityScore * 100).toInt()}% confidence)"
                } else {
                    "Detected related options (${(metadata.mutualExclusivityScore * 100).toInt()}% confidence)"
                }
            }
            metadata.semanticSimilarityScore != null -> "Grouped by semantic similarity"
            else -> "Default grouping"
        }
    }
}
