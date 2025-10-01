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
    val valueHistory: List<ValueHistory> = emptyList(),

    // NEW: Option set support for dropdowns/radio buttons
    val optionSet: OptionSet? = null,

    // NEW: Explicit render type (overrides auto-computed from option set)
    val renderType: RenderType? = null
)

data class ValidationRule(
    val rule: String,
    val message: String,
    val severity: ValidationState
) 