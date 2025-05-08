package com.ash.simpledataentry.domain.model

import com.ash.simpledataentry.domain.model.ValidationState

data class DataValue(
    val dataElement: String,
    val dataElementName: String,
    val sectionName: String,
    val categoryOptionCombo: String,
    val categoryOptionComboName: String,
    val value: String?,
    val comment: String?,
    val storedBy: String?,
    val validationState: ValidationState = ValidationState.VALID,
    val dataEntryType: DataEntryType = DataEntryType.TEXT,
    val isRequired: Boolean = false,
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val lastModified: Long = System.currentTimeMillis(),
    val valueHistory: List<ValueHistory> = emptyList(),
    val validationRules: List<ValidationRule> = emptyList()
)

data class ValidationRule(
    val rule: String,
    val message: String,
    val severity: ValidationState
) 