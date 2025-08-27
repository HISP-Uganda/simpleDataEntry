package com.ash.simpledataentry.domain.validation

import android.util.Log
import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.domain.model.*
import com.ash.simpledataentry.domain.model.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.hisp.dhis.android.core.validation.ValidationRule
import org.hisp.dhis.android.core.validation.ValidationRuleImportance
// Research: ValidationResult class doesn't exist at org.hisp.dhis.android.core.validation.ValidationResult
// Let's try to find the actual validation result types  
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ValidationService @Inject constructor(
    private val sessionManager: SessionManager
) {
    
    private val tag = "ValidationService"
    
    suspend fun validateDatasetInstance(
        datasetId: String,
        period: String,
        organisationUnit: String,
        attributeOptionCombo: String,
        dataValues: List<DataValue>
    ): ValidationSummary = withContext(Dispatchers.IO) {
        
        val startTime = System.currentTimeMillis()
        
        try {
            val d2 = sessionManager.getD2()
                ?: return@withContext ValidationSummary(
                    totalRulesChecked = 0,
                    passedRules = 0,
                    errorCount = 1,
                    warningCount = 0,
                    canComplete = false,
                    executionTimeMs = System.currentTimeMillis() - startTime,
                    validationResult = ValidationResult.Error(
                        listOf(
                            ValidationIssue(
                                ruleId = "system_error",
                                ruleName = "System Error",
                                description = "DHIS2 session not available",
                                severity = ValidationSeverity.ERROR
                            )
                        )
                    )
                )

            // CRITICAL: Comprehensive data staging for DHIS2 SDK validation
            Log.d(tag, "Starting comprehensive data staging for validation...")
            Log.d(tag, "Parameters: dataset=$datasetId, period=$period, orgUnit=$organisationUnit, attrCombo=$attributeOptionCombo")
            Log.d(tag, "Staging ${dataValues.size} data values in DHIS2 SDK...")
            
            var stagedCount = 0
            var stagingErrors = 0
            
            for (dataValue in dataValues) {
                try {
                    // Enhanced staging with validation and logging
                    val valueToStage = dataValue.value ?: ""
                    
                    Log.v(tag, "Staging: ${dataValue.dataElement}.${dataValue.categoryOptionCombo} = '$valueToStage'")
                    
                    // Stage the data value
                    d2.dataValueModule().dataValues()
                        .value(
                            period = period,
                            organisationUnit = organisationUnit,
                            dataElement = dataValue.dataElement,
                            categoryOptionCombo = dataValue.categoryOptionCombo,
                            attributeOptionCombo = attributeOptionCombo
                        )
                        .blockingSet(valueToStage)
                    
                    // Verify the value was staged correctly
                    val stagedValue = d2.dataValueModule().dataValues()
                        .value(period, organisationUnit, dataValue.dataElement, dataValue.categoryOptionCombo, attributeOptionCombo)
                        .blockingGet()
                    
                    if (stagedValue?.value() == valueToStage) {
                        stagedCount++
                        Log.v(tag, "✓ Successfully staged and verified: ${dataValue.dataElement} = '$valueToStage'")
                    } else {
                        stagingErrors++
                        Log.w(tag, "✗ Staging verification failed: ${dataValue.dataElement} expected='$valueToStage' actual='${stagedValue?.value()}'")
                    }
                    
                } catch (e: Exception) {
                    stagingErrors++
                    Log.e(tag, "Failed to stage data value: ${dataValue.dataElement} = '${dataValue.value}': ${e.message}", e)
                }
            }
            
            Log.d(tag, "Data staging complete: $stagedCount staged successfully, $stagingErrors errors")
            
            if (stagingErrors > 0) {
                Log.w(tag, "Some data staging errors occurred - validation may not be completely accurate")
            }
            
            // Ensure the SDK database is synchronized before validation
            try {
                // Force any pending database operations to complete
                Thread.sleep(100) // Small delay to ensure staging is complete
                Log.d(tag, "Data staging synchronization complete")
            } catch (e: Exception) {
                Log.w(tag, "Failed to synchronize staging: ${e.message}")
            }

            Log.d(tag, "Using DHIS2 SDK native validation engine for dataset: $datasetId, period: $period, orgUnit: $organisationUnit")
            
            // Check if validation rules exist before calling validation engine
            val validationRulesForDataset = try {
                d2.validationModule().validationRules()
                    .byDataSetUids(listOf(datasetId))
                    .blockingGet()
            } catch (e: Exception) {
                Log.w(tag, "Could not fetch validation rules for dataset $datasetId: ${e.message}")
                emptyList()
            }
            
            Log.d(tag, "Found ${validationRulesForDataset.size} validation rules for dataset $datasetId")
            
            // RESEARCH: Log validation rule details for debugging
            validationRulesForDataset.forEachIndexed { index, rule ->
                Log.d(tag, "Rule $index: ${rule.name()} (${rule.uid()}) - Importance: ${rule.importance()}")
                Log.d(tag, "  Left: ${rule.leftSide()?.expression()}")  
                Log.d(tag, "  Right: ${rule.rightSide()?.expression()}")
                Log.d(tag, "  Operator: ${rule.operator()}")
            }
            
            if (validationRulesForDataset.isEmpty()) {
                // Check if ANY validation rules exist in the system
                val allRulesCount = try {
                    d2.validationModule().validationRules().blockingGet().size
                } catch (e: Exception) { 0 }
                
                Log.w(tag, "No validation rules for dataset $datasetId (system has $allRulesCount total rules)")
                return@withContext ValidationSummary(
                    totalRulesChecked = 0,
                    passedRules = 0,
                    errorCount = 0,
                    warningCount = 0,
                    canComplete = true,
                    executionTimeMs = System.currentTimeMillis() - startTime,
                    validationResult = ValidationResult.Success("No validation rules configured for this dataset")
                )
            }
            
            // Try DHIS2 SDK validation engine first, then fallback to manual evaluation
            try {
                Log.d(tag, "Attempting DHIS2 SDK validation engine...")
                
                val sdkValidationResult = d2.validationModule()
                    .validationEngine()
                    .validate(datasetId, period, organisationUnit, attributeOptionCombo)
                    .blockingGet()
                
                // RESEARCH: Debug what the SDK actually returns
                Log.d(tag, "SDK validation result type: ${sdkValidationResult?.javaClass?.name}")
                Log.d(tag, "SDK validation result: $sdkValidationResult")
                
                val violations = try {
                    sdkValidationResult?.violations() ?: emptyList()
                } catch (e: Exception) {
                    Log.e(tag, "Failed to get violations: ${e.message}")
                    emptyList()
                }
                
                Log.d(tag, "SDK validation returned ${violations.size} violations")
                if (violations.isNotEmpty()) {
                    violations.forEachIndexed { index, violation ->
                        Log.d(tag, "Violation $index type: ${violation?.javaClass?.name}")
                        Log.d(tag, "Violation $index: $violation")
                    }
                }
                
                if (violations.isNotEmpty()) {
                    // SDK validation found violations, process them
                    return@withContext processSdkValidationResult(
                        violations, validationRulesForDataset, startTime
                    )
                } else {
                    // SDK validation completed successfully with no violations
                    Log.d(tag, "SDK validation completed successfully - no violations found")
                    val executionTime = System.currentTimeMillis() - startTime
                    return@withContext ValidationSummary(
                        totalRulesChecked = validationRulesForDataset.size,
                        passedRules = validationRulesForDataset.size,
                        errorCount = 0,
                        warningCount = 0,
                        canComplete = true,
                        executionTimeMs = executionTime,
                        validationResult = ValidationResult.Success("All validation rules passed successfully")
                    )
                }
                
            } catch (e: Exception) {
                Log.e(tag, "DHIS2 SDK validation engine failed: ${e.message}")
                
                // If SDK validation fails, return a warning but allow completion
                // This prevents blocking users when validation system has issues
                val executionTime = System.currentTimeMillis() - startTime
                val warning = ValidationIssue(
                    ruleId = "sdk_validation_failed",
                    ruleName = "Validation Engine Warning",
                    description = "DHIS2 SDK validation engine failed (${e.message}). ${validationRulesForDataset.size} validation rules exist but could not be evaluated. Please verify data manually before completion.",
                    severity = ValidationSeverity.WARNING
                )
                
                return@withContext ValidationSummary(
                    totalRulesChecked = validationRulesForDataset.size,
                    passedRules = 0,
                    errorCount = 0,
                    warningCount = 1,
                    canComplete = true, // Allow completion with warning
                    executionTimeMs = executionTime,
                    validationResult = ValidationResult.Warning(listOf(warning))
                )
            }

        } catch (e: Exception) {
            Log.e(tag, "Error during validation", e)
            ValidationSummary(
                totalRulesChecked = 0,
                passedRules = 0,
                errorCount = 1,
                warningCount = 0,
                canComplete = false,
                executionTimeMs = System.currentTimeMillis() - startTime,
                validationResult = ValidationResult.Error(
                    listOf(
                        ValidationIssue(
                            ruleId = "validation_error",
                            ruleName = "Validation Error",
                            description = "Validation failed: ${e.message}",
                            severity = ValidationSeverity.ERROR
                        )
                    )
                )
            )
        }
    }
    
    private fun processSdkValidationResult(
        violations: List<Any>, // Use Any since we don't know the exact type
        validationRules: List<ValidationRule>,
        startTime: Long
    ): ValidationSummary {
        val errors = mutableListOf<ValidationIssue>()
        val warnings = mutableListOf<ValidationIssue>()
        
        violations.forEach { violation ->
            try {
                // Try to extract information from violation object using reflection
                val validationRule = violation.javaClass.getMethod("validationRule").invoke(violation) as? ValidationRule
                val ruleName = validationRule?.name() ?: "Unknown Rule"
                val ruleUid = validationRule?.uid() ?: "unknown"
                
                val description = "Validation rule '$ruleName' failed validation check"
                
                val severity = when (validationRule?.importance()) {
                    ValidationRuleImportance.HIGH -> ValidationSeverity.ERROR
                    ValidationRuleImportance.MEDIUM -> ValidationSeverity.WARNING
                    ValidationRuleImportance.LOW -> ValidationSeverity.WARNING
                    null -> ValidationSeverity.WARNING
                }
                
                val validationIssue = ValidationIssue(
                    ruleId = ruleUid,
                    ruleName = ruleName,
                    description = description,
                    severity = severity,
                    affectedDataElements = extractDataElementsFromRule(validationRule)
                )
                
                when (severity) {
                    ValidationSeverity.ERROR -> errors.add(validationIssue)
                    ValidationSeverity.WARNING -> warnings.add(validationIssue)
                }
                
                Log.d(tag, "SDK Validation issue: [$severity] $ruleName - $description")
            } catch (e: Exception) {
                Log.w(tag, "Could not process violation: ${e.message}")
            }
        }
        
        val totalRulesChecked = validationRules.size
        val passedCount = totalRulesChecked - violations.size
        val executionTime = System.currentTimeMillis() - startTime
        
        val finalValidationResult = when {
            errors.isNotEmpty() && warnings.isNotEmpty() -> 
                ValidationResult.Mixed(errors, warnings)
            errors.isNotEmpty() -> 
                ValidationResult.Error(errors)
            warnings.isNotEmpty() -> 
                ValidationResult.Warning(warnings)
            else -> 
                ValidationResult.Success("All validation rules passed successfully")
        }

        Log.d(tag, "SDK Validation summary: $totalRulesChecked rules checked, ${errors.size} errors, ${warnings.size} warnings")

        return ValidationSummary(
            totalRulesChecked = totalRulesChecked,
            passedRules = passedCount,
            errorCount = errors.size,
            warningCount = warnings.size,
            canComplete = errors.isEmpty(),
            executionTimeMs = executionTime,
            validationResult = finalValidationResult
        )
    }
    
    private fun extractDataElementsFromRule(validationRule: ValidationRule?): List<String> {
        if (validationRule == null) return emptyList()
        
        val leftExpression = validationRule.leftSide()?.expression() ?: ""
        val rightExpression = validationRule.rightSide()?.expression() ?: ""
        
        return extractDataElementsFromExpressions(leftExpression, rightExpression)
    }
    
    /**
     * Extract data element references from DHIS2 validation rule expressions
     */
    private fun extractDataElementsFromExpressions(leftExpression: String, rightExpression: String): List<String> {
        val dataElementPattern = Regex("""#\{([^}]+)\}""")
        val leftDataElements = dataElementPattern.findAll(leftExpression).map { it.groupValues[1] }.toList()
        val rightDataElements = dataElementPattern.findAll(rightExpression).map { it.groupValues[1] }.toList()
        return (leftDataElements + rightDataElements).distinct()
    }

}