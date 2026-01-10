package com.ash.simpledataentry.domain.grouping

import android.util.Log
import com.ash.simpledataentry.domain.model.*
import java.util.Locale
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
        private const val MIN_PAREN_STRUCTURED_RATIO = 0.5  // Allow mixed sections for parenthetical suffixes
        private const val MIN_PAREN_GENDER_RATIO = 0.25  // Allow sparse gender suffixes
        private val SEPARATORS = listOf(" - ", " | ", "_", " / ", ": ")
        private const val PAREN_SUFFIX = "__PAREN_SUFFIX__"
        private val PAREN_SUFFIX_REGEX = Regex("^(.*)\\(([^)]+)\\)\\s*$")
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

        var bestResult: ImpliedCategoryCombination? = null
        var bestScore = Double.NEGATIVE_INFINITY

        // Try each separator pattern, keep the strongest category signal.
        for (separator in SEPARATORS) {
            val result = tryInferWithSeparator(dataElements, separator, sectionName)
            if (result != null) {
                val optionCount = result.categories.sumOf { it.options.size }
                val score = result.confidence + (result.categories.size * 0.1) + (optionCount * 0.001)
                if (score > bestScore) {
                    bestScore = score
                    bestResult = result
                }
            }
        }

        val parentheticalResult = tryInferWithParentheticalSuffix(dataElements, sectionName)
        if (parentheticalResult != null) {
            val score = parentheticalResult.confidence + (parentheticalResult.categories.size * 0.1)
            if (score > bestScore) {
                bestScore = score
                bestResult = parentheticalResult
            }
        }

        if (bestResult != null && bestResult.confidence >= MIN_CONFIDENCE) {
            Log.d(
                TAG,
                "✓ Pattern detected: ${bestResult.categories.size} categories, confidence=${bestResult.confidence}"
            )
            return bestResult
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

        val (reinferredCategories, depthConsistency) = buildCategoriesWithFallback(parsedNames, separator)

        if (reinferredCategories.isEmpty()) {
            Log.d(TAG, "  Separator '$separator': no valid categories found")
            return null
        }
        if (reinferredCategories.none { it.options.size > 1 }) {
            Log.d(TAG, "  Separator '$separator': no multi-option categories found")
            return null
        }

        // Calculate confidence based on:
        // 1. Ratio of structured elements
        // 2. Depth consistency
        // 3. Number of categories found
        val confidence = (structuredRatio * 0.5) +
                        (depthConsistency * 0.3) +
                        (minOf(reinferredCategories.size / 3.0, 1.0) * 0.2)

        val pattern = when (separator) {
            " - " -> CategoryPattern.HIERARCHICAL
            " | " -> CategoryPattern.PIPE_DELIM
            "_" -> CategoryPattern.UNDERSCORE_DELIM
            " / " -> CategoryPattern.HIERARCHICAL
            ": " -> CategoryPattern.PREFIX_GROUPED
            else -> CategoryPattern.FLAT
        }

        Log.d(TAG, "  Separator '$separator': ${reinferredCategories.size} categories, confidence=$confidence")
        reinferredCategories.forEachIndexed { index, cat ->
            Log.d(TAG, "    Level $index: ${cat.options.size} options - ${cat.options.take(3).joinToString(", ")}${if (cat.options.size > 3) "..." else ""}")
        }

        return ImpliedCategoryCombination(
            categories = reinferredCategories,
            confidence = confidence,
            pattern = pattern,
            totalDataElements = dataElements.size,
            structuredDataElements = parsedNames.size
        )
    }

    private fun tryInferWithParentheticalSuffix(
        dataElements: List<DataValue>,
        sectionName: String
    ): ImpliedCategoryCombination? {
        val parsed = dataElements.mapNotNull { element ->
            val match = PAREN_SUFFIX_REGEX.find(element.dataElementName) ?: return@mapNotNull null
            val baseName = match.groupValues[1].trim()
            val option = match.groupValues[2].trim()
            if (baseName.isBlank() || option.isBlank()) return@mapNotNull null
            element to (baseName to option)
        }

        val structuredRatio = parsed.size.toDouble() / dataElements.size
        val rawBaseNames = parsed.map { it.second.first }
        val suffixOptions = parsed.map { it.second.second }
        val distinctSuffixOptions = linkedSetOf<String>().apply {
            suffixOptions.forEach { add(it) }
        }.toList()

        val genderOptions = distinctSuffixOptions.filter { isGenderOption(it) }
        val requiredRatio = if (genderOptions.size >= 2) MIN_PAREN_GENDER_RATIO else MIN_PAREN_STRUCTURED_RATIO
        if (structuredRatio < requiredRatio) {
            Log.d(
                TAG,
                "  Parenthetical: only ${(structuredRatio * 100).toInt()}% fit (need ${(requiredRatio * 100).toInt()}%)"
            )
            return null
        }

        if (distinctSuffixOptions.size <= 1) {
            Log.d(TAG, "  Parenthetical: only one suffix option detected")
            return null
        }

        val normalizedBaseNames = normalizeParentheticalBaseNames(rawBaseNames)
        val baseNamesWithSuffix = parsed.map { (_, pair) ->
            val baseName = pair.first
            val option = pair.second
            if (genderOptions.size >= 2 && !isGenderOption(option)) {
                "$baseName (${option.trim()})"
            } else {
                baseName
            }
        }
        val normalizedWithSuffix = normalizeParentheticalBaseNames(baseNamesWithSuffix)
        val distinctBaseNames = linkedSetOf<String>().apply {
            normalizedWithSuffix.forEach { add(it) }
        }.toList()

        val confidence = (structuredRatio * 0.6) + (minOf(distinctSuffixOptions.size / 4.0, 1.0) * 0.4)
        Log.d(
            TAG,
            "  Parenthetical: ${distinctBaseNames.size} base names, ${distinctSuffixOptions.size} suffix options, confidence=$confidence"
        )

        val categories = mutableListOf(
            ImpliedCategory(
                name = "Indicator",
                options = distinctBaseNames,
                level = 0,
                separator = PAREN_SUFFIX
            )
        )
        if (genderOptions.size >= 2) {
            categories.add(
                ImpliedCategory(
                    name = "Gender",
                    options = genderOptions,
                    level = 1,
                    separator = PAREN_SUFFIX
                )
            )
        } else {
            categories.add(
                ImpliedCategory(
                    name = inferCategoryName(distinctSuffixOptions, 1),
                    options = distinctSuffixOptions,
                    level = 1,
                    separator = PAREN_SUFFIX
                )
            )
        }

        return ImpliedCategoryCombination(
            categories = categories,
            confidence = confidence,
            pattern = CategoryPattern.PARENTHETICAL,
            totalDataElements = dataElements.size,
            structuredDataElements = parsed.size
        )
    }

    private fun normalizeParentheticalBaseNames(baseNames: List<String>): List<String> {
        if (baseNames.isEmpty()) return baseNames
        val commonPrefix = baseNames.first().substringBefore(" - ", missingDelimiterValue = "")
        val hasCommonPrefix = commonPrefix.isNotBlank() && baseNames.all {
            it.startsWith("$commonPrefix - ")
        }
        return if (hasCommonPrefix) {
            baseNames.map { it.removePrefix("$commonPrefix - ").trim() }
        } else {
            baseNames.map { it.trim() }
        }
    }

    private fun buildCategoriesWithFallback(
        parsedNames: List<Pair<DataValue, List<String>>>,
        separator: String
    ): Pair<List<ImpliedCategory>, Double> {
        val parts = parsedNames.map { it.second }
        val (mostCommonDepth, depthConsistency) = computeDepthStats(parts)
        val baseCategories = buildCategories(parts, separator, baseLevel = 0, mostCommonDepth = mostCommonDepth)
        if (baseCategories.isNotEmpty()) {
            return baseCategories to depthConsistency
        }

        val commonPrefix = parts.firstOrNull()?.firstOrNull()
        val hasCommonPrefix = commonPrefix != null && parts.all {
            it.isNotEmpty() && it.first().trim().equals(commonPrefix.trim(), ignoreCase = true)
        }
        if (!hasCommonPrefix) {
            return emptyList<ImpliedCategory>() to depthConsistency
        }

        val trimmedParts = parts.mapNotNull { tokens ->
            if (tokens.size <= 1) null else tokens.drop(1)
        }
        if (trimmedParts.isEmpty()) {
            return emptyList<ImpliedCategory>() to depthConsistency
        }
        val (trimmedDepth, trimmedConsistency) = computeDepthStats(trimmedParts)
        val trimmedCategories = buildCategories(
            trimmedParts,
            separator = separator,
            baseLevel = 1,
            mostCommonDepth = trimmedDepth
        )
        return trimmedCategories to trimmedConsistency
    }

    private fun computeDepthStats(parts: List<List<String>>): Pair<Int, Double> {
        val depthCounts = parts.groupingBy { it.size }.eachCount()
        val mostCommonDepth = depthCounts.maxByOrNull { it.value }?.key ?: return 0 to 0.0
        val depthConsistency = (depthCounts[mostCommonDepth] ?: 0).toDouble() / parts.size
        return mostCommonDepth to depthConsistency
    }

    private fun buildCategories(
        parts: List<List<String>>,
        separator: String,
        baseLevel: Int,
        mostCommonDepth: Int
    ): List<ImpliedCategory> {
        if (mostCommonDepth <= 0) {
            return emptyList()
        }
        val categories = mutableListOf<ImpliedCategory>()
        val elementsAtDepth = parts.count { it.size == mostCommonDepth }
        for (level in 0 until mostCommonDepth) {
            val optionsAtLevel = parts
                .filter { it.size == mostCommonDepth }
                .map { it[level].trim() }
                .distinct()
                .sorted()

            if (optionsAtLevel.size <= 1 && parts.size > 1) {
                Log.d(TAG, "  Separator '$separator': level ${baseLevel + level} has single option (skipping)")
                continue
            }
            if (optionsAtLevel.size > 20) {
                Log.d(
                    TAG,
                    "  Separator '$separator': level ${baseLevel + level} has ${optionsAtLevel.size} options (too many)"
                )
                continue
            }
            if (optionsAtLevel.size == elementsAtDepth) {
                Log.d(
                    TAG,
                    "  Separator '$separator': level ${baseLevel + level} has all unique values (not a category)"
                )
                continue
            }

            categories.add(
                ImpliedCategory(
                    name = inferCategoryName(optionsAtLevel, baseLevel + level),
                    options = optionsAtLevel,
                    level = baseLevel + level,
                    separator = separator
                )
            )
        }
        return categories
    }

    /**
     * Create mappings for data elements to their implied category options
     */
    fun createMappings(
        dataElements: List<DataValue>,
        combination: ImpliedCategoryCombination
    ): List<ImpliedCategoryMapping> {

        val separator = combination.categories.firstOrNull()?.separator ?: return emptyList()
        if (separator == PAREN_SUFFIX) {
            val baseOptions = combination.categories.firstOrNull()?.options ?: emptyList()
            val genderOptions = combination.categories
                .firstOrNull { it.name.equals("Gender", ignoreCase = true) }
                ?.options
                ?: emptyList()
            return dataElements.mapNotNull { element ->
                val match = PAREN_SUFFIX_REGEX.find(element.dataElementName) ?: return@mapNotNull null
                val baseNameRaw = match.groupValues[1].trim()
                val option = match.groupValues[2].trim()
                if (baseNameRaw.isBlank() || option.isBlank()) return@mapNotNull null

                val normalizedBase = if (genderOptions.isNotEmpty() && !isGenderOption(option)) {
                    resolveParentheticalBaseName("$baseNameRaw (${option.trim()})", baseOptions)
                } else {
                    resolveParentheticalBaseName(baseNameRaw, baseOptions)
                }
                val level1Option = if (genderOptions.isNotEmpty() && isGenderOption(option)) option else null

                ImpliedCategoryMapping(
                    dataElementId = element.dataElement,
                    dataElementName = element.dataElementName,
                    categoryOptionsByLevel = buildMap {
                        put(0, normalizedBase)
                        if (level1Option != null) {
                            put(1, level1Option)
                        }
                    },
                    fieldName = normalizedBase
                )
            }
        }

        return dataElements.mapNotNull { element ->
            val parts = element.dataElementName.split(separator)

            if (parts.size < 2) {
                null
            } else {
                val categoryOptions = mutableMapOf<Int, String>()

                // Map each level to its option
                combination.categories.forEach { category ->
                    if (category.level < parts.size) {
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

    private fun resolveParentheticalBaseName(baseNameRaw: String, options: List<String>): String {
        val trimmed = baseNameRaw.trim()
        val exactMatch = options.firstOrNull { it.equals(trimmed, ignoreCase = true) }
        if (exactMatch != null) {
            return exactMatch
        }
        val suffixMatch = options.firstOrNull { option ->
            trimmed.endsWith(" - $option", ignoreCase = true)
        }
        if (suffixMatch != null) {
            return suffixMatch
        }
        return trimmed
    }

    private fun isGenderOption(option: String): Boolean {
        val normalized = option.trim().lowercase(Locale.ENGLISH)
        return normalized in setOf("male", "female", "man", "woman", "boy", "girl", "m", "f")
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

    private fun inferCategoryName(options: List<String>, level: Int): String {
        val normalized = options.map { it.trim().lowercase(Locale.ENGLISH) }
        val normalizedSet = normalized.toSet()
        val genderKeys = setOf("male", "female", "man", "woman", "boy", "girl", "m", "f")
        val yesNoKeys = setOf("yes", "no", "true", "false")
        val monthKeys = setOf(
            "january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december",
            "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "sept", "oct", "nov", "dec"
        )
        val ageRangePattern = Regex(
            "^(under\\s+\\d+|\\d+\\s*\\+|\\d+\\s*-\\s*\\d+|\\d+\\s*to\\s*\\d+)$"
        )
        val gradePattern = Regex("^(p\\d+|grade\\s*\\d+|g\\d+)$")

        if (normalizedSet.containsAll(setOf("male", "female")) ||
            normalizedSet.containsAll(setOf("m", "f")) ||
            normalizedSet.containsAll(setOf("boy", "girl")) ||
            normalizedSet.containsAll(setOf("man", "woman"))
        ) {
            return "Gender"
        }

        if (normalizedSet.intersect(yesNoKeys).size >= 2) {
            return "Response"
        }

        if (normalized.all { it.contains("trimester") }) {
            return "Trimester"
        }

        if (normalized.all { it.contains("quarter") } || normalized.all { it.startsWith("q") }) {
            return "Quarter"
        }

        if (normalized.all { it in monthKeys }) {
            return "Month"
        }

        if (normalized.all { ageRangePattern.matches(it) }) {
            return "Age group"
        }

        if (normalized.all { gradePattern.matches(it) || it.contains("grade") }) {
            return "Grade"
        }

        val commonSuffix = findCommonSuffixWord(options)
        if (!commonSuffix.isNullOrBlank()) {
            return commonSuffix.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.ENGLISH) else char.toString()
            }
        }

        return "Category ${level + 1}"
    }

    private fun findCommonSuffixWord(options: List<String>): String? {
        if (options.isEmpty()) return null
        val suffixes = options.mapNotNull { option ->
            option.trim().split(Regex("\\s+")).lastOrNull()?.lowercase(Locale.ENGLISH)
        }
        return if (suffixes.distinct().size == 1) suffixes.first() else null
    }
}
