package com.ash.simpledataentry.data.repositoryImpl

import com.ash.simpledataentry.domain.model.DataValue
import com.ash.simpledataentry.domain.model.ValidationSummary
import com.ash.simpledataentry.domain.validation.ValidationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ValidationRepository @Inject constructor(
    private val validationService: ValidationService
) {
    
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
        
        val cacheKey = generateCacheKey(datasetId, period, organisationUnit, attributeOptionCombo, dataValues)
        
        // Return cached result if available and not forcing refresh
        if (!forceRefresh && _validationCache.containsKey(cacheKey)) {
            val cachedResult = _validationCache[cacheKey]!!
            _lastValidationResult.value = cachedResult
            return@withContext cachedResult
        }
        
        // Execute validation
        val result = validationService.validateDatasetInstance(
            datasetId = datasetId,
            period = period,
            organisationUnit = organisationUnit,
            attributeOptionCombo = attributeOptionCombo,
            dataValues = dataValues
        )
        
        // Cache the result
        _validationCache[cacheKey] = result
        _lastValidationResult.value = result
        
        // Clean up old cache entries to prevent memory issues
        if (_validationCache.size > MAX_CACHE_SIZE) {
            cleanupCache()
        }
        
        result
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
    }
}