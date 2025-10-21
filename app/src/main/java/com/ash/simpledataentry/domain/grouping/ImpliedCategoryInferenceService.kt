package com.ash.simpledataentry.domain.grouping

import android.util.Log
import com.ash.simpledataentry.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for inferring category structure from data element names
 * in event/tracker programs which don't have explicit DHIS2 category combinations
 *
 * This is a RESTORATION of previously removed functionality based on git commits:
 * - 2b78f6e: "UI/UX polish, cleaning up the data entry form accordions..."
 * - c11092e: "UI/UX polish the accordion logic looks good now"
 */
@Singleton
class ImpliedCategoryInferenceService @Inject constructor() {

    companion object {
        private const val TAG = "ImpliedCategoryInference"
        private const val MIN_CONFIDENCE = 0.6  // Minimum confidence to accept pattern
        private const val MIN_STRUCTURED_RATIO = 0.7  // At least 70% of elements must fit pattern
        private val SEPARATORS = listOf(" - ", " | ", "_", " / ", ": ")
    }

    /**
     * Infer category structure from a list of data elements
     * Returns null if no clear pattern is detected
     */
    fun inferCategoryStructure(
        dataElements: List<DataValue>,
        sectionName: String
    ): ImpliedCategoryCombination? {

        if (dataElements.isEmpty()) {
            Log.d(TAG, "Section '$sectionName': No data elements to analyze")
            return null
        }

        Log.d(TAG, "=== ANALYZING SECTION '$sectionName' ===")
        Log.d(TAG, "Total data elements: ${dataElements.size}")

        // Try each separator pattern
        for (separator in SEPARATORS) {
            val result = tryInferWithSeparator(dataElements, separator, sectionName)
            if (result != null && result.confidence >= MIN_CONFIDENCE) {
                Log.d(TAG, "✓ Pattern detected with separator '$separator': confidence=${result.confidence}")
                return result
            }
        }

        Log.d(TAG, "✗ No clear category pattern detected for section '$sectionName'")
        return null
    }

    /**
     * Try to infer structure using a specific separator
     */
    private fun tryInferWithSeparator(
        dataElements: List<DataValue>,
        separator: String,
        sectionName: String
    ): ImpliedCategoryCombination? {

        // Parse all names using this separator
        val parsedNames = dataElements.mapNotNull { element ->
            val parts = element.dataElementName.split(separator)
            if (parts.size >= 2) {
                element to parts
            } else null
        }

        // Need at least 70% of elements to fit the pattern
        val structuredRatio = parsedNames.size.toDouble() / dataElements.size
        if (structuredRatio < MIN_STRUCTURED_RATIO) {
            Log.d(TAG, "  Separator '$separator': only ${(structuredRatio * 100).toInt()}% fit (need ${(MIN_STRUCTURED_RATIO * 100).toInt()}%)")
            return null
        }

        // Find the maximum depth (number of parts)
        val maxDepth = parsedNames.maxOfOrNull { it.second.size } ?: return null

        // Check if depth is consistent
        val depthCounts = parsedNames.groupingBy { it.second.size }.eachCount()
        val mostCommonDepth = depthCounts.maxByOrNull { it.value }?.key ?: return null

        // At least 80% should have the same depth
        val depthConsistency = (depthCounts[mostCommonDepth] ?: 0).toDouble() / parsedNames.size
        if (depthConsistency < 0.8) {
            Log.d(TAG, "  Separator '$separator': inconsistent depth (${(depthConsistency * 100).toInt()}% consistency)")
            return null
        }

        // Build category structure
        val categories = mutableListOf<ImpliedCategory>()

        // For each level (except the last which is the field name)
        for (level in 0 until mostCommonDepth - 1) {
            val optionsAtLevel = parsedNames
                .filter { it.second.size == mostCommonDepth }
                .map { it.second[level].trim() }
                .distinct()
                .sorted()

            // Skip levels with too many unique values (probably not a category)
            if (optionsAtLevel.size > 20) {
                Log.d(TAG, "  Separator '$separator': level $level has ${optionsAtLevel.size} options (too many)")
                continue
            }

            // Skip levels where every element has a unique value (not a category)
            val elementsAtDepth = parsedNames.count { it.second.size == mostCommonDepth }
            if (optionsAtLevel.size == elementsAtDepth) {
                Log.d(TAG, "  Separator '$separator': level $level has all unique values (not a category)")
                continue
            }

            categories.add(
                ImpliedCategory(
                    name = "Category ${level + 1}",  // Generic name, could be improved with NLP
                    options = optionsAtLevel,
                    level = level,
                    separator = separator
                )
            )
        }

        if (categories.isEmpty()) {
            Log.d(TAG, "  Separator '$separator': no valid categories found")
            return null
        }

        // Calculate confidence based on:
        // 1. Ratio of structured elements
        // 2. Depth consistency
        // 3. Number of categories found
        val confidence = (structuredRatio * 0.5) +
                        (depthConsistency * 0.3) +
                        (minOf(categories.size / 3.0, 1.0) * 0.2)

        val pattern = when (separator) {
            " - " -> CategoryPattern.HIERARCHICAL
            " | " -> CategoryPattern.PIPE_DELIM
            "_" -> CategoryPattern.UNDERSCORE_DELIM
            " / " -> CategoryPattern.HIERARCHICAL
            ": " -> CategoryPattern.PREFIX_GROUPED
            else -> CategoryPattern.FLAT
        }

        Log.d(TAG, "  Separator '$separator': ${categories.size} categories, confidence=$confidence")
        categories.forEachIndexed { index, cat ->
            Log.d(TAG, "    Level $index: ${cat.options.size} options - ${cat.options.take(3).joinToString(", ")}${if (cat.options.size > 3) "..." else ""}")
        }

        return ImpliedCategoryCombination(
            categories = categories,
            confidence = confidence,
            pattern = pattern,
            totalDataElements = dataElements.size,
            structuredDataElements = parsedNames.size
        )
    }

    /**
     * Create mappings for data elements to their implied category options
     */
    fun createMappings(
        dataElements: List<DataValue>,
        combination: ImpliedCategoryCombination
    ): List<ImpliedCategoryMapping> {

        val separator = combination.categories.firstOrNull()?.separator ?: return emptyList()

        return dataElements.mapNotNull { element ->
            val parts = element.dataElementName.split(separator)

            if (parts.size < 2) {
                null
            } else {
                val categoryOptions = mutableMapOf<Int, String>()

                // Map each level to its option
                combination.categories.forEach { category ->
                    if (category.level < parts.size - 1) {
                        categoryOptions[category.level] = parts[category.level].trim()
                    }
                }

                // Last part is the field name
                val fieldName = parts.lastOrNull()?.trim() ?: element.dataElementName

                ImpliedCategoryMapping(
                    dataElementId = element.dataElement,
                    dataElementName = element.dataElementName,
                    categoryOptionsByLevel = categoryOptions,
                    fieldName = fieldName
                )
            }
        }
    }

    /**
     * Group data elements by their implied category options for nested rendering
     */
    fun groupByImpliedCategories(
        mappings: List<ImpliedCategoryMapping>,
        combination: ImpliedCategoryCombination
    ): Map<List<String>, List<ImpliedCategoryMapping>> {

        // Group by all category levels
        return mappings.groupBy { mapping ->
            combination.categories.map { category ->
                mapping.categoryOptionsByLevel[category.level] ?: ""
            }
        }
    }
}
