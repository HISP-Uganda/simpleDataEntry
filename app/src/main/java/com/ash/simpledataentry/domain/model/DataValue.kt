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
    val validationState: ValidationState,
    val dataEntryType: DataEntryType,
    val lastModified: Long,
    val validationRules: List<ValidationRule> = emptyList(),
    val valueHistory: List<ValueHistory> = emptyList()
)

data class ValidationRule(
    val rule: String,
    val message: String,
    val severity: ValidationState
) 