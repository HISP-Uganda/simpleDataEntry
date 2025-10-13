package com.ash.simpledataentry.domain.grouping

import android.util.Log
import com.ash.simpledataentry.domain.model.*
import org.hisp.dhis.android.core.validation.ValidationRule
import org.hisp.dhis.android.core.common.RelativeOrganisationUnit

/**
 * Analyzes data elements and determines the best grouping strategy
 * using a multi-strategy approach with confidence levels
 */
class DataElementGroupingAnalyzer {

    companion object {
        private const val TAG = "GroupingAnalyzer"
        private const val DEFAULT_CATEGORY_COMBO = "HllvX50cXC0"
    }

    /**
     * Main entry point: Determines the best grouping strategy for a list of data elements
     */
    fun analyzeGrouping(
        dataElements: List<DataValue>,
        categoryComboStructures: Map<String, List<Pair<String, List<Pair<String, String>>>>>,
        optionSets: Map<String, OptionSet>,
        validationRules: List<ValidationRule> = emptyList()
    ): List<GroupingStrategy> {

        Log.d(TAG, "=== Starting grouping analysis for ${dataElements.size} data elements ===")

        val strategies = mutableListOf<GroupingStrategy>()

        // Strategy 0.5: HIGHEST CONFIDENCE - Validation rule analysis
        if (validationRules.isNotEmpty()) {
            Log.d(TAG, "=== STRATEGY 0.5: Validation rule-based grouping (${validationRules.size} rules) ===")
            val validationGroups = extractGroupsFromValidationRules(dataElements, validationRules)
            if (validationGroups.isNotEmpty()) {
                Log.d(TAG, "Found ${validationGroups.size} validation-rule-based groups (HIGHEST confidence)")
                strategies.addAll(validationGroups)

                // Remove already grouped elements
                val groupedElements = validationGroups.flatMap { it.members }.toSet()
                val remaining = dataElements.filter { it !in groupedElements }

                if (remaining.isEmpty()) return strategies

                // Continue analysis with remaining elements
                return strategies + analyzeGrouping(remaining, categoryComboStructures, optionSets, emptyList())
            }
        }

        // Strategy 1: HIGH CONFIDENCE - Category Combo based grouping
        val categoryGroups = groupByCategoryCombos(dataElements, categoryComboStructures)
        if (categoryGroups.isNotEmpty()) {
            Log.d(TAG, "Found ${categoryGroups.size} category combo groups (HIGH confidence)")
            strategies.addAll(categoryGroups)

            // Remove already grouped elements
            val groupedElements = categoryGroups.flatMap { it.members }.toSet()
            val remaining = dataElements.filter { it !in groupedElements }

            if (remaining.isEmpty()) return strategies

            // Continue analysis with remaining elements
            return strategies + analyzeRemainingElements(remaining, optionSets)
        }

        // Strategy 2-4: Analyze all elements if no category combos found
        return analyzeRemainingElements(dataElements, optionSets)
    }

    /**
     * STRATEGY 1: HIGH CONFIDENCE - Group by DHIS2 Category Combos
     */
    private fun groupByCategoryCombos(
        dataElements: List<DataValue>,
        categoryComboStructures: Map<String, List<Pair<String, List<Pair<String, String>>>>>
    ): List<GroupingStrategy> {

        // Group by data element - each data element with non-default category combo
        // should have multiple category option combos
        val byDataElement = dataElements
            .filter { it.categoryOptionCombo != DEFAULT_CATEGORY_COMBO }
            .groupBy { it.dataElement }

        return byDataElement.mapNotNull { (dataElementId, combos) ->
            val firstCombo = combos.firstOrNull() ?: return@mapNotNull null
            val structure = categoryComboStructures[firstCombo.categoryOptionCombo]

            if (structure != null && structure.isNotEmpty()) {
                Log.d(TAG, "Category combo group: ${firstCombo.dataElementName} with ${combos.size} combos")

                GroupingStrategy(
                    confidence = ConfidenceLevel.HIGH,
                    groupType = GroupType.DIMENSIONAL_GRID,
                    groupTitle = firstCombo.dataElementName,
                    members = combos,
                    metadata = GroupMetadata(
                        categoryComboUid = firstCombo.categoryOptionCombo,
                        categoryComboStructure = structure,
                        detectionMethod = "DHIS2 Category Combo"
                    )
                )
            } else null
        }
    }

    /**
     * Analyze remaining elements using MEDIUM and LOW confidence strategies
     */
    private fun analyzeRemainingElements(
        dataElements: List<DataValue>,
        optionSets: Map<String, OptionSet>
    ): List<GroupingStrategy> {

        val strategies = mutableListOf<GroupingStrategy>()
        val remaining = dataElements.toMutableList()

        // Strategy 2: MEDIUM CONFIDENCE - Dimensional pattern recognition
        val dimensionalGroups = extractDimensionalPatterns(remaining)
        if (dimensionalGroups.isNotEmpty()) {
            Log.d(TAG, "Found ${dimensionalGroups.size} dimensional pattern groups (MEDIUM confidence)")
            strategies.addAll(dimensionalGroups)

            val grouped = dimensionalGroups.flatMap { it.members }.toSet()
            remaining.removeAll(grouped)
        }

        // Strategy 3: MEDIUM CONFIDENCE - Option set semantic analysis
        val optionSetGroups = analyzeOptionSetGrouping(remaining, optionSets)
        if (optionSetGroups.isNotEmpty()) {
            Log.d(TAG, "Found ${optionSetGroups.size} option set groups (MEDIUM confidence)")
            strategies.addAll(optionSetGroups)

            val grouped = optionSetGroups.flatMap { it.members }.toSet()
            remaining.removeAll(grouped)
        }

        // Strategy 3.5: MEDIUM CONFIDENCE - Boolean mutually exclusive detection (without option sets)
        val booleanGroups = detectMutuallyExclusiveBooleans(remaining)
        if (booleanGroups.isNotEmpty()) {
            Log.d(TAG, "Found ${booleanGroups.size} mutually exclusive boolean groups (MEDIUM confidence)")
            strategies.addAll(booleanGroups)

            val grouped = booleanGroups.flatMap { it.members }.toSet()
            remaining.removeAll(grouped)
        }

        // Strategy 4: LOW CONFIDENCE - Semantic clustering
        // LOWERED THRESHOLD: Accept 2+ elements (was 3) to reduce ungrouped elements
        if (remaining.size >= 2) {
            val semanticClusters = clusterBySemanticSimilarity(remaining)
            if (semanticClusters.isNotEmpty()) {
                Log.d(TAG, "Found ${semanticClusters.size} semantic clusters (LOW confidence)")
                strategies.addAll(semanticClusters)

                val grouped = semanticClusters.flatMap { it.members }.toSet()
                remaining.removeAll(grouped)
            }
        }

        // Fallback: FLAT_LIST for any remaining ungrouped elements
        if (remaining.isNotEmpty()) {
            Log.d(TAG, "${remaining.size} elements remain ungrouped - using FLAT_LIST")
            strategies.add(
                GroupingStrategy(
                    confidence = ConfidenceLevel.LOW,
                    groupType = GroupType.FLAT_LIST,
                    groupTitle = "",
                    members = remaining,
                    metadata = GroupMetadata(detectionMethod = "Fallback - no pattern detected")
                )
            )
        }

        return strategies
    }

    /**
     * STRATEGY 2: MEDIUM CONFIDENCE - Extract dimensional patterns from names
     * Pattern: "PREFIX (dim1value dim2value dim3value)"
     */
    private fun extractDimensionalPatterns(dataElements: List<DataValue>): List<GroupingStrategy> {
        val regex = """^(.*?)\s*\(([^)]+)\)\s*$""".toRegex()

        val parsed = dataElements.mapNotNull { dv ->
            regex.find(dv.dataElementName)?.let { match ->
                val base = match.groupValues[1].trim()
                val dimensions = match.groupValues[2].split(Regex("\\s+"))
                dv to (base to dimensions)
            }
        }

        if (parsed.isEmpty()) return emptyList()

        // Group by common base name
        val byBase = parsed.groupBy { it.second.first }

        return byBase.mapNotNull { (baseName, items) ->
            // LOWERED THRESHOLD: Accept 2+ instances (was 3) for better coverage
            if (items.size < 2) return@mapNotNull null

            val dimensionValues = items.map { it.second.second }
            val maxDimensions = dimensionValues.maxOfOrNull { it.size } ?: return@mapNotNull null

            // LOWERED THRESHOLD: Accept single dimension (was 2) to catch more patterns
            if (maxDimensions < 1) return@mapNotNull null

            // Extract dimensions
            val dimensions = (0 until maxDimensions).map { dimIndex ->
                val valuesAtPosition = dimensionValues
                    .filter { it.size > dimIndex }
                    .map { it[dimIndex] }
                    .toSet()

                Dimension(
                    name = inferDimensionName(valuesAtPosition),
                    values = valuesAtPosition,
                    order = dimIndex
                )
            }

            val pattern = DimensionalPattern(baseName, dimensions)
            val members = items.map { it.first }

            // Build inferred category combo from dimensional pattern
            val inferredCatCombo = buildInferredCategoryCombo(baseName, dimensions, members)

            Log.d(TAG, "Dimensional pattern: '$baseName' with ${dimensions.size} dimensions: ${dimensions.map { "${it.name}(${it.values.size})" }}")
            if (inferredCatCombo != null) {
                Log.d(TAG, "  Inferred CategoryCombo: ${inferredCatCombo.name}")
                inferredCatCombo.categories.forEach { cat ->
                    Log.d(TAG, "    - ${cat.name}: ${cat.categoryOptions.joinToString(", ")}")
                }
                if (inferredCatCombo.isConditional) {
                    Log.d(TAG, "    Conditional rules: ${inferredCatCombo.conditionalRules.joinToString("; ")}")
                }
                Log.d(TAG, "    Completeness: ${inferredCatCombo.actualCombinations}/${inferredCatCombo.totalExpectedCombinations} (${(inferredCatCombo.completenessRatio * 100).toInt()}%)")
            }

            GroupingStrategy(
                confidence = ConfidenceLevel.MEDIUM,
                groupType = GroupType.DIMENSIONAL_GRID,
                groupTitle = baseName,
                members = members,
                metadata = GroupMetadata(
                    dimensionalPattern = pattern,
                    inferredCategoryCombo = inferredCatCombo,
                    detectionMethod = "Dimensional Pattern Recognition"
                )
            )
        }
    }

    /**
     * Infer dimension name from values
     */
    private fun inferDimensionName(values: Set<String>): String {
        return when {
            // Grade patterns: P1, P2, ..., P7 or P.1, P.2, ..., P.7 or mixed
            values.all { it.matches(Regex("P\\.?\\d+.*")) } -> "Grade Level"
            values.all { it.matches(Regex("\\d+")) } -> "Numeric Category"
            values.all { it in setOf("Male", "Female", "male", "female") } -> "Gender"
            // Check for mixed boarding/disability dimension (Day/Boarding + Disabled)
            (values.any { it in setOf("Day", "Boarding", "day", "boarding") } &&
             values.any { it.contains("Disabled", ignoreCase = true) }) -> "Boarding/Disability Status"
            values.all { it in setOf("Day", "Boarding", "day", "boarding") } -> "Boarding Status"
            values.any { it.contains("Disabled", ignoreCase = true) } -> "Disability Status"
            values.any { it.contains("Urban", ignoreCase = true) || it.contains("Rural", ignoreCase = true) } -> "Location"
            values.size <= 5 -> "Category"
            else -> "Dimension"
        }
    }

    /**
     * Build inferred category combo from dimensional pattern
     * Detects hierarchical/conditional structures like "P1 Day/Boarding" vs "P.1 Disabled"
     */
    private fun buildInferredCategoryCombo(
        baseName: String,
        dimensions: List<Dimension>,
        members: List<DataValue>
    ): InferredCategoryCombo? {
        if (dimensions.isEmpty()) return null

        // Detect conditional/hierarchical patterns
        // Example: Grade has P1-P7 (regular) and P.1-P.7 (disabled)
        // This means: Grade × (Day/Boarding OR Disabled) × Gender

        val gradeIndex = dimensions.indexOfFirst { it.name == "Grade Level" }
        val disabilityIndex = dimensions.indexOfFirst { it.name == "Disability Status" }
        val boardingIndex = dimensions.indexOfFirst { it.name == "Boarding Status" }
        val boardingDisabilityIndex = dimensions.indexOfFirst { it.name == "Boarding/Disability Status" }
        val genderIndex = dimensions.indexOfFirst { it.name == "Gender" }

        // Check if this is the conditional grade/boarding/disability pattern
        // Can be either separate dimensions OR a combined boarding/disability dimension
        val isConditional = gradeIndex >= 0 &&
                           ((disabilityIndex >= 0 && boardingIndex >= 0) || boardingDisabilityIndex >= 0)

        val categories = if (isConditional) {
            // Build conditional category structure
            val effectiveBoardingIndex = if (boardingDisabilityIndex >= 0) boardingDisabilityIndex else boardingIndex
            buildConditionalCategories(dimensions, gradeIndex, effectiveBoardingIndex, disabilityIndex, genderIndex, members)
        } else {
            // Simple cross-product structure
            buildSimpleCategories(dimensions)
        }

        val totalExpected = categories.fold(1) { acc, cat -> acc * cat.optionCount }
        val actualCount = members.size
        val completeness = actualCount.toFloat() / totalExpected.toFloat()

        val conditionalRules = if (isConditional) {
            listOf("Regular students: Grade (P1-P7) × Boarding Status (Day/Boarding) × Gender",
                   "Students with disabilities: Grade (P.1-P.7) × Gender (no boarding status)")
        } else {
            emptyList()
        }

        val categoryNames = categories.joinToString(" × ") { it.name }

        return InferredCategoryCombo(
            name = categoryNames,
            categories = categories,
            totalExpectedCombinations = totalExpected,
            actualCombinations = actualCount,
            completenessRatio = completeness,
            isConditional = isConditional,
            conditionalRules = conditionalRules,
            appliedToDataElements = members.map { it.dataElement }
        )
    }

    /**
     * Build simple category structure (cross-product)
     */
    private fun buildSimpleCategories(dimensions: List<Dimension>): List<InferredCategory> {
        return dimensions.map { dim ->
            InferredCategory(
                name = dim.name,
                categoryOptions = dim.values.sorted(),
                optionCount = dim.values.size,
                detectionMethod = "Suffix extraction from parenthetical notation"
            )
        }
    }

    /**
     * Build conditional category structure for grade/boarding/disability pattern
     */
    private fun buildConditionalCategories(
        dimensions: List<Dimension>,
        gradeIndex: Int,
        boardingIndex: Int,
        disabilityIndex: Int,
        genderIndex: Int,
        members: List<DataValue>
    ): List<InferredCategory> {
        val categories = mutableListOf<InferredCategory>()

        // Determine if we have regular grades (P1-P7) and disabled grades (P.1-P.7)
        val allGrades = dimensions[gradeIndex].values
        val regularGrades = allGrades.filter { it.matches(Regex("P\\d+")) }.sorted()
        val disabledGrades = allGrades.filter { it.matches(Regex("P\\.\\d+.*")) }.sorted()

        // Extract boarding statuses - filter out "Disabled" if it's a combined dimension
        val boardingValues = dimensions[boardingIndex].values
        val boardingStatuses = boardingValues.filter {
            !it.contains("Disabled", ignoreCase = true)
        }.sorted()
        val hasDisability = disabledGrades.isNotEmpty()

        // Category 1: Combined Student Type & Grade
        val studentTypeOptions = mutableListOf<String>()

        // Add regular student options (Grade × Boarding)
        regularGrades.forEach { grade ->
            boardingStatuses.forEach { boarding ->
                studentTypeOptions.add("$grade $boarding")
            }
        }

        // Add disabled student options (Grade only, no boarding)
        disabledGrades.forEach { grade ->
            studentTypeOptions.add("$grade Disabled")
        }

        categories.add(
            InferredCategory(
                name = "Student Type & Grade",
                categoryOptions = studentTypeOptions.sorted(),
                optionCount = studentTypeOptions.size,
                detectionMethod = "Hierarchical pattern detection (conditional boarding/disability status)"
            )
        )

        // Category 2: Gender (if present)
        if (genderIndex >= 0) {
            categories.add(
                InferredCategory(
                    name = dimensions[genderIndex].name,
                    categoryOptions = dimensions[genderIndex].values.sorted(),
                    optionCount = dimensions[genderIndex].values.size,
                    detectionMethod = "Suffix extraction"
                )
            )
        }

        return categories
    }

    /**
     * STRATEGY 3: MEDIUM CONFIDENCE - Option set semantic analysis
     */
    private fun analyzeOptionSetGrouping(
        dataElements: List<DataValue>,
        optionSets: Map<String, OptionSet>
    ): List<GroupingStrategy> {

        // Filter to elements with option sets
        val withOptionSets = dataElements.mapNotNull { dv ->
            optionSets[dv.dataElement]?.let { dv to it }
        }

        if (withOptionSets.isEmpty()) return emptyList()

        val strategies = mutableListOf<GroupingStrategy>()

        // Group by option set ID - only fields sharing the SAME option set can be grouped
        val byOptionSet = withOptionSets.groupBy { it.second.id }

        byOptionSet.forEach { (optionSetId, fieldsWithSet) ->
            val fields = fieldsWithSet.map { it.first }
            val optionSet = fieldsWithSet.first().second

            if (fields.size < 2) return@forEach // Need at least 2 fields

            // Check if it's a YES/NO option set
            if (isYesNoOptionSet(optionSet)) {
                val exclusivityScore = computeMutualExclusivityScore(fields)

                val groupType = when {
                    exclusivityScore > 0.8 -> GroupType.RADIO_GROUP
                    exclusivityScore > 0.5 -> GroupType.CHECKBOX_GROUP
                    else -> GroupType.SEMANTIC_CLUSTER
                }

                val groupTitle = extractCommonConcept(fields.map { it.dataElementName })

                Log.d(TAG, "Option set group: '$groupTitle' (${fields.size} fields, exclusivity: ${(exclusivityScore * 100).toInt()}%, type: $groupType)")

                strategies.add(
                    GroupingStrategy(
                        confidence = ConfidenceLevel.MEDIUM,
                        groupType = groupType,
                        groupTitle = groupTitle,
                        members = fields,
                        metadata = GroupMetadata(
                            mutualExclusivityScore = exclusivityScore,
                            detectionMethod = "Option Set Analysis (YES/NO)"
                        )
                    )
                )
            } else {
                // Non-YES/NO option sets - treat as semantic cluster if similar names
                val commonConcept = extractCommonConcept(fields.map { it.dataElementName })
                if (commonConcept.length >= 5) {
                    Log.d(TAG, "Option set cluster: '$commonConcept' (${fields.size} fields with shared option set)")

                    strategies.add(
                        GroupingStrategy(
                            confidence = ConfidenceLevel.MEDIUM,
                            groupType = GroupType.SEMANTIC_CLUSTER,
                            groupTitle = commonConcept,
                            members = fields,
                            metadata = GroupMetadata(
                                detectionMethod = "Option Set Clustering"
                            )
                        )
                    )
                }
            }
        }

        return strategies
    }

    /**
     * STRATEGY 3.5: MEDIUM CONFIDENCE - Detect mutually exclusive booleans
     * Multi-pass algorithm: delimiter -> word sequence -> single word
     * With exclusivity scoring for RADIO_GROUP vs CHECKBOX_GROUP classification
     */
    private fun detectMutuallyExclusiveBooleans(dataElements: List<DataValue>): List<GroupingStrategy> {
        val candidates = dataElements.filter { dv ->
            dv.dataEntryType == DataEntryType.YES_NO
        }

        if (candidates.size < 2) return emptyList()

        val strategies = mutableListOf<GroupingStrategy>()
        val grouped = mutableSetOf<DataValue>()

        // PASS 1: DELIMITER-BASED EXTRACTION
        Log.d(TAG, "=== PASS 1: Delimiter-based extraction ===")
        val pass1Groups = extractByDelimiter(candidates)
        pass1Groups.forEach { (subject, fields) ->
            if (fields.size >= 2) {
                val group = createGroupStrategy(subject, fields, "Pass 1: Delimiter")
                if (group != null) {
                    strategies.add(group)
                    grouped.addAll(fields)
                    Log.d(TAG, "PASS 1 ✓: '$subject' -> ${fields.size} fields")
                }
            }
        }

        // PASS 2: COMMON WORD-SEQUENCE EXTRACTION
        val remainingAfterPass1 = candidates.filter { it !in grouped }
        if (remainingAfterPass1.size >= 2) {
            Log.d(TAG, "=== PASS 2: Word-sequence (${remainingAfterPass1.size} remaining) ===")
            val pass2Groups = extractByCommonWordSequence(remainingAfterPass1)
            pass2Groups.forEach { (subject, fields) ->
                if (fields.size >= 2) {
                    val group = createGroupStrategy(subject, fields, "Pass 2: Word-seq")
                    if (group != null) {
                        strategies.add(group)
                        grouped.addAll(fields)
                        Log.d(TAG, "PASS 2 ✓: '$subject' -> ${fields.size} fields")
                    }
                }
            }
        }

        // PASS 3: SINGLE-WORD SUBJECT EXTRACTION
        val remainingAfterPass2 = candidates.filter { it !in grouped }
        if (remainingAfterPass2.size >= 2) {
            Log.d(TAG, "=== PASS 3: Single-word (${remainingAfterPass2.size} remaining) ===")
            val pass3Groups = extractBySingleWordSubject(remainingAfterPass2)
            pass3Groups.forEach { (subject, fields) ->
                if (fields.size >= 2) {
                    val group = createGroupStrategy(subject, fields, "Pass 3: Single-word")
                    if (group != null) {
                        strategies.add(group)
                        grouped.addAll(fields)
                        Log.d(TAG, "PASS 3 ✓: '$subject' -> ${fields.size} fields")
                    }
                }
            }
        }

        return strategies
    }

    /**
     * PASS 1: Extract by delimiters " - " or ": "
     */
    private fun extractByDelimiter(fields: List<DataValue>): Map<String, List<DataValue>> {
        val bySubject = mutableMapOf<String, MutableList<DataValue>>()
        val delimiters = listOf(" - ", ": ")

        fields.forEach { field ->
            for (delimiter in delimiters) {
                if (delimiter in field.dataElementName) {
                    val lastIndex = field.dataElementName.lastIndexOf(delimiter)
                    if (lastIndex > 0) {
                        val subject = field.dataElementName.substring(0, lastIndex).trim()
                        if (subject.length >= 3) {
                            bySubject.getOrPut(subject) { mutableListOf() }.add(field)
                            break
                        }
                    }
                }
            }
        }

        return bySubject
    }

    /**
     * PASS 2: Extract by common starting word sequences (multi-word)
     */
    private fun extractByCommonWordSequence(fields: List<DataValue>): Map<String, List<DataValue>> {
        val bySubject = mutableMapOf<String, MutableList<DataValue>>()

        fields.forEach { field ->
            val words = field.dataElementName.split(Regex("\\s+"))
            if (words.size >= 2) {
                // Try progressively shorter prefixes
                for (prefixLen in (words.size - 1) downTo 2) {
                    val prefix = words.take(prefixLen).joinToString(" ")
                    bySubject.getOrPut(prefix) { mutableListOf() }.add(field)
                }
            }
        }

        // Only keep groups with 2+ members
        return bySubject.filter { it.value.size >= 2 }
    }

    /**
     * PASS 3: Extract by first word only (critical for "Ownership Public", "Location Rural")
     */
    private fun extractBySingleWordSubject(fields: List<DataValue>): Map<String, List<DataValue>> {
        val bySubject = mutableMapOf<String, MutableList<DataValue>>()

        fields.forEach { field ->
            val words = field.dataElementName.split(Regex("\\s+"))
            if (words.size >= 2) {
                val firstWord = words[0]
                if (firstWord.length >= 3) {
                    bySubject.getOrPut(firstWord) { mutableListOf() }.add(field)
                }
            }
        }

        return bySubject.filter { it.value.size >= 2 }
    }

    /**
     * Create group with exclusivity scoring and type classification
     */
    private fun createGroupStrategy(
        subject: String,
        fields: List<DataValue>,
        detectionMethod: String
    ): GroupingStrategy? {
        val options = fields.map { field ->
            field.dataElementName.removePrefix(subject).trim()
                .removePrefix("-").removePrefix(":").trim()
        }

        // SANITY CHECK
        val longestOptionWordCount = options.maxOfOrNull { it.split(Regex("\\s+")).size } ?: 0
        val avgOptionLength = options.sumOf { it.length } / options.size.toFloat()

        if (longestOptionWordCount > 5 || avgOptionLength > 30) {
            Log.d(TAG, "REJECTED: '$subject' - options too long")
            return null
        }

        // EXCLUSIVITY SCORE
        val trueCount = fields.count {
            it.value.equals("true", ignoreCase = true) || it.value == "1"
        }

        val exclusivityScore = when {
            trueCount <= 1 -> 100f
            trueCount == 2 -> 50f
            else -> 100f / (trueCount.toFloat() * 2)
        }

        // CLASSIFY TYPE
        val groupType = if (exclusivityScore > 75f) {
            Log.d(TAG, "→ RADIO_GROUP: exclusivity=${exclusivityScore.toInt()}%, ${trueCount}/${fields.size} selected")
            GroupType.RADIO_GROUP
        } else {
            Log.d(TAG, "→ CHECKBOX_GROUP: exclusivity=${exclusivityScore.toInt()}%, ${trueCount}/${fields.size} selected")
            GroupType.CHECKBOX_GROUP
        }

        return GroupingStrategy(
            confidence = ConfidenceLevel.MEDIUM,
            groupType = groupType,
            groupTitle = subject.trim(),
            members = fields,
            metadata = GroupMetadata(
                mutualExclusivityScore = exclusivityScore / 100f,
                detectionMethod = detectionMethod
            )
        )
    }

    /**
     * Compute mutual exclusivity score (0.0 to 1.0)
     * Uses generic structural analysis instead of dataset-specific keywords
     */
    private fun computeMutualExclusivityScore(fields: List<DataValue>): Float {
        var score = 0f

        val names = fields.map { it.dataElementName }

        // Signal 1: Common prefix with distinguishing suffix (0.3 weight)
        val commonPrefix = findLongestCommonPrefix(names)
        if (commonPrefix.length >= 5) {
            val suffixes = names.map { it.removePrefix(commonPrefix).trim() }
            if (suffixes.all { it.isNotBlank() } && suffixes.distinct().size == suffixes.size) {
                score += 0.3f
            }
        }

        // Signal 2: Suffix pattern analysis (0.5 weight) - THE KEY GENERIC SIGNAL
        val suffixes = names.map { it.removePrefix(commonPrefix).trim() }
        if (suffixes.all { it.isNotBlank() }) {
            val suffixScore = analyzeSuffixPattern(suffixes)
            score += suffixScore * 0.5f
        }

        // Signal 3: Field count patterns (0.2 weight)
        when {
            fields.size in 2..15 -> {
                val suffixes = names.map { it.removePrefix(commonPrefix).trim() }
                if (suffixes.distinct().size == suffixes.size) {
                    score += 0.2f
                }
            }
            fields.size > 15 -> score -= 0.2f
        }

        return score.coerceIn(0f, 1f)
    }

    /**
     * Analyze suffix patterns to determine if they represent exclusive categories (1.0)
     * or inclusive attributes (0.0). This is the core generic detection logic.
     */
    private fun analyzeSuffixPattern(suffixes: List<String>): Float {
        var suffixScore = 0f
        val lowerSuffixes = suffixes.map { it.lowercase() }

        // Pattern 1: Negation pairs (e.g., "licensed" vs "not licensed") - Strong exclusivity
        val hasNegationPair = lowerSuffixes.any { it.contains("not ") || it.startsWith("no ") } &&
                              lowerSuffixes.any { !it.contains("not ") && !it.startsWith("no ") }
        if (hasNegationPair) suffixScore += 0.4f

        // Pattern 2: Proper noun / Category names (capitalized, no verbs) - Strong exclusivity
        // Examples: "Catholic", "Islamic", "Government", "Private", "Urban", "Rural"
        val properNounPattern = suffixes.filter { suffix ->
            suffix.isNotEmpty() &&
            suffix[0].isUpperCase() &&
            !suffix.lowercase().contains(" is ") &&
            !suffix.lowercase().contains(" has ") &&
            !suffix.lowercase().contains(" does ")
        }
        if (properNounPattern.size >= suffixes.size * 0.7) suffixScore += 0.3f

        // Pattern 3: Verb/adjective attributes (suggests checkboxes) - Reduces score
        val attributeWords = listOf("available", "functioning", "damaged", "working", "broken", "has", "does", "is")
        val hasAttributePattern = lowerSuffixes.any { suffix ->
            attributeWords.any { suffix.contains(it) }
        }
        if (hasAttributePattern) suffixScore -= 0.3f

        // Pattern 4: Enumerated/Listed items - Strong exclusivity
        // Look for patterns like numbered items or clear alternatives
        val allSingleWords = suffixes.all { it.split(Regex("\\s+")).size <= 2 }
        if (allSingleWords && suffixes.size >= 3) suffixScore += 0.2f

        // Pattern 5: Hierarchical or taxonomic terms - Strong exclusivity
        // Examples: "public/private", "urban/rural", organizational categories
        val taxonomicPairs = listOf(
            setOf("public", "private"),
            setOf("urban", "rural", "peri urban"),
            setOf("licensed", "not licensed", "registered"),
            setOf("day", "boarding", "mixed"),
            setOf("boys only", "girls only", "mixed")
        )
        val matchesTaxonomy = taxonomicPairs.any { taxonomy ->
            lowerSuffixes.any { suffix -> taxonomy.any { suffix.contains(it) } }
        }
        if (matchesTaxonomy) suffixScore += 0.3f

        return suffixScore.coerceIn(0f, 1f)
    }

    /**
     * STRATEGY 4: LOW CONFIDENCE - Semantic clustering
     */
    private fun clusterBySemanticSimilarity(dataElements: List<DataValue>): List<GroupingStrategy> {
        val clusters = mutableListOf<List<DataValue>>()
        val remaining = dataElements.toMutableList()

        while (remaining.isNotEmpty()) {
            val seed = remaining.removeAt(0)
            val cluster = mutableListOf(seed)

            val similar = remaining.filter { candidate ->
                val similarity = computeNameSimilarity(seed.dataElementName, candidate.dataElementName)
                similarity > 0.6
            }

            cluster.addAll(similar)
            remaining.removeAll(similar.toSet())

            // LOWERED THRESHOLD: Accept 2+ elements (was 3) to reduce ungrouped elements
            if (cluster.size >= 2) {
                clusters.add(cluster)
            }
        }

        return clusters.map { cluster ->
            val commonConcept = extractCommonConcept(cluster.map { it.dataElementName })
            val similarity = cluster.map { dv ->
                computeNameSimilarity(dv.dataElementName, commonConcept)
            }.average().toFloat()

            Log.d(TAG, "Semantic cluster: '$commonConcept' (${cluster.size} fields, similarity: ${(similarity * 100).toInt()}%)")

            GroupingStrategy(
                confidence = ConfidenceLevel.LOW,
                groupType = GroupType.SEMANTIC_CLUSTER,
                groupTitle = commonConcept,
                members = cluster,
                metadata = GroupMetadata(
                    semanticSimilarityScore = similarity,
                    detectionMethod = "Semantic Similarity Clustering"
                )
            )
        }
    }

    /**
     * Helper: Check if option set is YES/NO type
     */
    private fun isYesNoOptionSet(optionSet: OptionSet): Boolean {
        if (optionSet.options.size != 2) return false

        val values = optionSet.options.map { it.code.lowercase() }.toSet()
        return values == setOf("0", "1") ||
               values == setOf("yes", "no") ||
               values == setOf("true", "false")
    }

    /**
     * Helper: Find longest common prefix
     */
    private fun findLongestCommonPrefix(strings: List<String>): String {
        if (strings.isEmpty()) return ""

        var prefix = strings[0]
        for (str in strings.drop(1)) {
            var i = 0
            while (i < prefix.length && i < str.length && prefix[i] == str[i]) {
                i++
            }
            prefix = prefix.substring(0, i)
        }

        return prefix
    }

    /**
     * Helper: Extract common concept from names (better than just prefix)
     */
    private fun extractCommonConcept(names: List<String>): String {
        if (names.isEmpty()) return ""
        if (names.size == 1) return names[0]

        val commonPrefix = findLongestCommonPrefix(names)

        // Clean up the prefix to get a meaningful concept
        return commonPrefix
            .trim()
            .trimEnd('-', ':', '|', '/', ' ', '_')
            .takeIf { it.length >= 3 } ?: "Related Fields"
    }

    /**
     * Helper: Compute similarity between two strings (0.0 to 1.0)
     * Using simple Jaccard similarity of words
     */
    private fun computeNameSimilarity(name1: String, name2: String): Float {
        val words1 = name1.lowercase().split(Regex("\\W+")).filter { it.isNotBlank() }.toSet()
        val words2 = name2.lowercase().split(Regex("\\W+")).filter { it.isNotBlank() }.toSet()

        if (words1.isEmpty() || words2.isEmpty()) return 0f

        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size

        return intersection.toFloat() / union.toFloat()
    }

    /**
     * STRATEGY 0.5: HIGHEST CONFIDENCE - Extract groups from validation rules
     * Parses validation rule expressions to detect grouping patterns
     */
    private fun extractGroupsFromValidationRules(
        dataElements: List<DataValue>,
        validationRules: List<ValidationRule>
    ): List<GroupingStrategy> {
        val strategies = mutableListOf<GroupingStrategy>()
        val grouped = mutableSetOf<DataValue>()

        validationRules.forEach { rule ->
            val leftExpr = rule.leftSide()?.expression() ?: ""
            val rightExpr = rule.rightSide()?.expression() ?: ""
            val operator = rule.operator()?.name ?: ""
            val ruleName = rule.name() ?: ""

            Log.d(TAG, "Analyzing rule: $ruleName")
            Log.d(TAG, "  Left: $leftExpr | Operator: $operator | Right: $rightExpr")

            // Pattern 1: Mutually Exclusive (A + B + C = 1 or A + B + C == 1)
            if (isMutuallyExclusiveRule(leftExpr, rightExpr, operator)) {
                val fieldsInRule = extractDataElementIds(leftExpr + " " + rightExpr)
                Log.d(TAG, "  → MUTUALLY EXCLUSIVE pattern detected: ${fieldsInRule.size} data elements")

                val members = dataElements.filter {
                    it.dataElement in fieldsInRule &&
                    it !in grouped &&
                    it.dataEntryType == DataEntryType.YES_NO
                }

                if (members.size >= 2) {
                    val groupTitle = extractGroupTitle(ruleName)
                    Log.d(TAG, "  ✓ Created RADIO_GROUP: '$groupTitle' with ${members.size} members")

                    strategies.add(
                        GroupingStrategy(
                            confidence = ConfidenceLevel.HIGH,
                            groupType = GroupType.RADIO_GROUP,
                            groupTitle = groupTitle,
                            members = members,
                            metadata = GroupMetadata(
                                mutualExclusivityScore = 1.0f,
                                detectionMethod = "Validation Rule: $ruleName"
                            )
                        )
                    )
                    grouped.addAll(members)
                }
            }

            // Pattern 2: Summation (A + B + C = Total or A + B = C)
            else if (isSummationRule(leftExpr, rightExpr, operator)) {
                val fieldsInRule = extractDataElementIds(leftExpr + " " + rightExpr)
                Log.d(TAG, "  → SUMMATION pattern detected: ${fieldsInRule.size} data elements")

                val members = dataElements.filter {
                    it.dataElement in fieldsInRule &&
                    it !in grouped
                }

                if (members.size >= 2) {
                    val groupTitle = extractGroupTitle(ruleName)
                    Log.d(TAG, "  ✓ Created SEMANTIC_CLUSTER: '$groupTitle' with ${members.size} members")

                    strategies.add(
                        GroupingStrategy(
                            confidence = ConfidenceLevel.HIGH,
                            groupType = GroupType.SEMANTIC_CLUSTER,
                            groupTitle = groupTitle,
                            members = members,
                            metadata = GroupMetadata(
                                detectionMethod = "Validation Rule (Summation): $ruleName"
                            )
                        )
                    )
                    grouped.addAll(members)
                }
            }

            // Pattern 3: Checkbox Group (A + B + C <= N)
            else if (isCheckboxRule(leftExpr, rightExpr, operator)) {
                val fieldsInRule = extractDataElementIds(leftExpr)
                Log.d(TAG, "  → CHECKBOX pattern detected: ${fieldsInRule.size} data elements")

                val members = dataElements.filter {
                    it.dataElement in fieldsInRule &&
                    it !in grouped &&
                    it.dataEntryType == DataEntryType.YES_NO
                }

                if (members.size >= 2) {
                    val groupTitle = extractGroupTitle(ruleName)
                    Log.d(TAG, "  ✓ Created CHECKBOX_GROUP: '$groupTitle' with ${members.size} members")

                    strategies.add(
                        GroupingStrategy(
                            confidence = ConfidenceLevel.HIGH,
                            groupType = GroupType.CHECKBOX_GROUP,
                            groupTitle = groupTitle,
                            members = members,
                            metadata = GroupMetadata(
                                detectionMethod = "Validation Rule (Checkbox): $ruleName"
                            )
                        )
                    )
                    grouped.addAll(members)
                }
            }
        }

        return strategies
    }

    /**
     * Detect mutually exclusive rule pattern: A + B + C = 1 or A + B + C == 1
     */
    private fun isMutuallyExclusiveRule(leftExpr: String, rightExpr: String, operator: String): Boolean {
        // Check if right side is exactly "1" and operator is equality
        val isEqualityOperator = operator.uppercase() in listOf("EQUAL", "EQUAL_TO", "EQ", "==")
        val rightSideIsOne = rightExpr.trim() == "1" || rightExpr.trim() == "1.0"

        // Check if left side is a sum of data elements
        val hasAddition = leftExpr.contains("+")

        return isEqualityOperator && rightSideIsOne && hasAddition
    }

    /**
     * Detect summation rule pattern: A + B + C = Total or A + B = C
     */
    private fun isSummationRule(leftExpr: String, rightExpr: String, operator: String): Boolean {
        val isEqualityOperator = operator.uppercase() in listOf("EQUAL", "EQUAL_TO", "EQ", "==")
        val hasAdditionInLeft = leftExpr.contains("+")
        val rightSideIsDataElement = rightExpr.contains("#{") || rightExpr.matches(Regex("[A-Za-z0-9]{11}"))

        return isEqualityOperator && hasAdditionInLeft && rightSideIsDataElement
    }

    /**
     * Detect checkbox rule pattern: A + B + C <= N or A + B + C < N
     */
    private fun isCheckboxRule(leftExpr: String, rightExpr: String, operator: String): Boolean {
        val isLessThanOperator = operator.uppercase() in listOf("LESS_THAN", "LESS_THAN_OR_EQUAL_TO", "LT", "LE", "<=", "<")
        val hasAddition = leftExpr.contains("+")
        val rightSideIsNumber = rightExpr.trim().toIntOrNull() != null

        return isLessThanOperator && hasAddition && rightSideIsNumber
    }

    /**
     * Extract data element UIDs from expression
     * Handles DHIS2 expression syntax: #{dataElementUid} or #{dataElementUid.categoryOptionComboUid}
     */
    private fun extractDataElementIds(expression: String): Set<String> {
        val regex = """#\{([A-Za-z0-9]{11})(?:\.[A-Za-z0-9]{11})?\}""".toRegex()
        return regex.findAll(expression)
            .map { it.groupValues[1] }
            .toSet()
    }

    /**
     * Extract meaningful group title from validation rule name
     * Examples:
     * - "School Type must be exactly one" -> "School Type"
     * - "Ownership validation" -> "Ownership"
     * - "Location: Only one selected" -> "Location"
     */
    private fun extractGroupTitle(ruleName: String): String {
        // Remove common validation rule suffixes
        val cleaned = ruleName
            .replace(Regex("\\s+(must be|validation|rule|check).*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex(":\\s+.*$"), "")
            .trim()

        return if (cleaned.length >= 3) cleaned else ruleName
    }
}
