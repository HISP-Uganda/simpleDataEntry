package com.ash.simpledataentry.data.repositoryImpl

import com.ash.simpledataentry.domain.model.DataValue
import com.ash.simpledataentry.domain.model.ValidationIssue
import com.ash.simpledataentry.domain.model.ValidationResult
import com.ash.simpledataentry.domain.model.ValidationSeverity
import com.ash.simpledataentry.domain.model.ValidationSummary
import com.ash.simpledataentry.domain.validation.ValidationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ValidationRepository @Inject constructor(
    private val validationService: ValidationService
) {
    private val validationMutex = Mutex()
    
    // Cache validation results to avoid unnecessary re-execution
    private val _validationCache = mutableMapOf<String, ValidationSummary>()
    private val _lastValidationResult = MutableStateFlow<ValidationSummary?>(null)
    
    val lastValidationResult: Flow<ValidationSummary?> = _lastValidationResult.asStateFlow()
    
    suspend fun validateDatasetInstance(
        datasetId: String,
        period: String,
        organisationUnit: String,
        attributeOptionCombo: String,
        dataValues: List<DataValue>,
        forceRefresh: Boolean = false
    ): ValidationSummary = withContext(Dispatchers.IO) {
        validationMutex.withLock {
            val cacheKey = generateCacheKey(datasetId, period, organisationUnit, attributeOptionCombo, dataValues)

            // Return cached result if available and not forcing refresh
            if (!forceRefresh && _validationCache.containsKey(cacheKey)) {
                val cachedResult = _validationCache[cacheKey]!!
                _lastValidationResult.value = cachedResult
                return@withLock cachedResult
            }

            // Execute validation with guarded retries for transient SQLite nested-transaction conflicts.
            var result = validationService.validateDatasetInstance(
                datasetId = datasetId,
                period = period,
                organisationUnit = organisationUnit,
                attributeOptionCombo = attributeOptionCombo,
                dataValues = dataValues
            )
            var attempts = 1
            while (attempts < MAX_VALIDATION_ATTEMPTS && isNestedTransactionFailure(result)) {
                delay(VALIDATION_RETRY_DELAYS_MS[(attempts - 1).coerceAtMost(VALIDATION_RETRY_DELAYS_MS.lastIndex)])
                attempts++
                result = validationService.validateDatasetInstance(
                    datasetId = datasetId,
                    period = period,
                    organisationUnit = organisationUnit,
                    attributeOptionCombo = attributeOptionCombo,
                    dataValues = dataValues
                )
            }

            if (isNestedTransactionFailure(result)) {
                result = ValidationSummary(
                    totalRulesChecked = result.totalRulesChecked,
                    passedRules = result.passedRules,
                    errorCount = 0,
                    warningCount = 1,
                    canComplete = true,
                    executionTimeMs = result.executionTimeMs,
                    validationResult = ValidationResult.Warning(
                        listOf(
                            ValidationIssue(
                                ruleId = "validation_busy_retry",
                                ruleName = "Validation Busy",
                                description = "Validation is temporarily busy due to database locking. Please sync and retry if needed.",
                                severity = ValidationSeverity.WARNING
                            )
                        )
                    )
                )
            }

            // Cache the result
            _validationCache[cacheKey] = result
            _lastValidationResult.value = result

            // Clean up old cache entries to prevent memory issues
            if (_validationCache.size > MAX_CACHE_SIZE) {
                cleanupCache()
            }

            result
        }
    }
    
    suspend fun getLastValidationResult(): ValidationSummary? {
        return _lastValidationResult.value
    }
    
    suspend fun clearValidationCache() = withContext(Dispatchers.IO) {
        _validationCache.clear()
        _lastValidationResult.value = null
    }
    
    suspend fun clearValidationCacheForDataset(datasetId: String) = withContext(Dispatchers.IO) {
        val keysToRemove = _validationCache.keys.filter { it.startsWith("$datasetId|") }
        keysToRemove.forEach { _validationCache.remove(it) }
        
        // Clear last result if it was for this dataset
        _lastValidationResult.value?.let { lastResult ->
            // You might want to add datasetId to ValidationSummary for this check
            // For now, we'll clear it to be safe
            _lastValidationResult.value = null
        }
    }
    
    suspend fun isValidationRequired(
        datasetId: String,
        period: String,
        organisationUnit: String,
        attributeOptionCombo: String,
        dataValues: List<DataValue>
    ): Boolean = withContext(Dispatchers.IO) {
        val cacheKey = generateCacheKey(datasetId, period, organisationUnit, attributeOptionCombo, dataValues)
        !_validationCache.containsKey(cacheKey)
    }
    
    private fun generateCacheKey(
        datasetId: String,
        period: String,
        organisationUnit: String,
        attributeOptionCombo: String,
        dataValues: List<DataValue>
    ): String {
        // Create a hash of the data values to detect changes
        val dataHash = dataValues
            .sortedBy { "${it.dataElement}-${it.categoryOptionCombo}" }
            .joinToString("|") { "${it.dataElement}-${it.categoryOptionCombo}:${it.value}" }
            .hashCode()
            
        return "$datasetId|$period|$organisationUnit|$attributeOptionCombo|$dataHash"
    }
    
    private fun cleanupCache() {
        // Keep only the most recent entries (simple LRU-like cleanup)
        if (_validationCache.size > MAX_CACHE_SIZE) {
            val entriesToRemove = _validationCache.size - (MAX_CACHE_SIZE / 2)
            val keysToRemove = _validationCache.keys.take(entriesToRemove)
            keysToRemove.forEach { _validationCache.remove(it) }
        }
    }
    
    companion object {
        private const val MAX_CACHE_SIZE = 50
        private const val MAX_VALIDATION_ATTEMPTS = 1
        private val VALIDATION_RETRY_DELAYS_MS = longArrayOf(0L)
    }

    private fun isNestedTransactionFailure(summary: ValidationSummary): Boolean {
        val errors = when (val result = summary.validationResult) {
            is ValidationResult.Error -> result.errors
            is ValidationResult.Mixed -> result.errors
            else -> emptyList()
        }
        return errors.any { issue ->
            issue.ruleId == "validation_transaction_error" ||
                issue.description.contains("cannot start a transaction within a transaction", ignoreCase = true) ||
                issue.description.contains("SQLITE_ERROR", ignoreCase = true)
        }
    }
}
