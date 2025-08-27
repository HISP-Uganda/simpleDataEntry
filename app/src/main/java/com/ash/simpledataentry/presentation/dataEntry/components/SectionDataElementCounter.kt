package com.ash.simpledataentry.presentation.dataEntry.components

import com.ash.simpledataentry.domain.model.DataValue

/**
 * Smart counting utility for data elements in sections.
 * Focuses on actual data elements with values rather than total fields/category combinations.
 */
object SectionDataElementCounter {
    
    /**
     * Count unique data elements that have non-empty values in a section
     */
    fun countFilledDataElements(
        sectionDataValues: List<DataValue>,
        dataValues: List<DataValue> = sectionDataValues
    ): Int {
        val filledDataElements = sectionDataValues
            .filter { dataValue ->
                !dataValue.value.isNullOrBlank()
            }
            .map { it.dataElement }
            .distinct()
        
        return filledDataElements.size
    }
    
    /**
     * Count total unique data elements in a section (regardless of values)
     */
    fun countTotalDataElements(sectionDataValues: List<DataValue>): Int {
        return sectionDataValues
            .map { it.dataElement }
            .distinct()
            .size
    }
    
    /**
     * Calculate section completion percentage based on data elements (not fields)
     */
    fun calculateSectionCompletion(
        sectionDataValues: List<DataValue>,
        dataValues: List<DataValue> = sectionDataValues
    ): Float {
        val totalElements = countTotalDataElements(sectionDataValues)
        if (totalElements == 0) return 0f
        
        val filledElements = countFilledDataElements(sectionDataValues, dataValues)
        return (filledElements.toFloat() / totalElements.toFloat()) * 100f
    }
    
    /**
     * Check if a section has any filled data elements
     */
    fun hasSectionData(
        sectionDataValues: List<DataValue>,
        dataValues: List<DataValue> = sectionDataValues
    ): Boolean {
        return countFilledDataElements(sectionDataValues, dataValues) > 0
    }
    
    /**
     * Get completion summary for display purposes
     */
    fun getCompletionSummary(
        sectionDataValues: List<DataValue>,
        dataValues: List<DataValue> = sectionDataValues
    ): SectionCompletionSummary {
        val totalElements = countTotalDataElements(sectionDataValues)
        val filledElements = countFilledDataElements(sectionDataValues, dataValues)
        val completionPercentage = calculateSectionCompletion(sectionDataValues, dataValues)
        
        return SectionCompletionSummary(
            totalDataElements = totalElements,
            filledDataElements = filledElements,
            completionPercentage = completionPercentage,
            isComplete = filledElements == totalElements && totalElements > 0
        )
    }
}

/**
 * Data class to encapsulate section completion information
 */
data class SectionCompletionSummary(
    val totalDataElements: Int,
    val filledDataElements: Int,
    val completionPercentage: Float,
    val isComplete: Boolean
) {
    fun getDisplayText(): String {
        return if (filledDataElements > 0) {
            "$filledDataElements of $totalDataElements data elements completed"
        } else {
            "$totalDataElements data elements"
        }
    }
}