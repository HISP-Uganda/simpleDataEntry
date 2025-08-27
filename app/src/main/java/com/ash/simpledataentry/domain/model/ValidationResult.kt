package com.ash.simpledataentry.domain.model

sealed class ValidationResult {
    data class Success(
        val message: String = "All validation rules passed"
    ) : ValidationResult()
    
    data class Warning(
        val warnings: List<ValidationIssue>
    ) : ValidationResult()
    
    data class Error(
        val errors: List<ValidationIssue>
    ) : ValidationResult()
    
    data class Mixed(
        val errors: List<ValidationIssue>,
        val warnings: List<ValidationIssue>
    ) : ValidationResult()
}

data class ValidationIssue(
    val ruleId: String,
    val ruleName: String,
    val description: String,
    val severity: ValidationSeverity,
    val affectedDataElements: List<String> = emptyList(),
    val leftSideValue: String? = null,
    val rightSideValue: String? = null,
    val operator: String? = null
)

enum class ValidationSeverity {
    ERROR,
    WARNING
}

data class ValidationSummary(
    val totalRulesChecked: Int,
    val passedRules: Int,
    val errorCount: Int,
    val warningCount: Int,
    val canComplete: Boolean, // Whether the dataset can be completed despite issues
    val executionTimeMs: Long,
    val validationResult: ValidationResult
) {
    val hasIssues: Boolean
        get() = errorCount > 0 || warningCount > 0
        
    val hasErrors: Boolean
        get() = errorCount > 0
        
    val hasWarnings: Boolean
        get() = warningCount > 0
}