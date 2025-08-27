package com.ash.simpledataentry.testutil

import com.ash.simpledataentry.domain.model.*
import org.hisp.dhis.android.core.validation.ValidationRule
import org.hisp.dhis.android.core.validation.ValidationRuleImportance
import java.time.LocalDate

/**
 * Test data builders for consistent test data creation
 */
object TestDataBuilders {

    fun createTestDataset(
        uid: String = "testDataset123",
        displayName: String = "Test Dataset",
        organisationUnits: List<String> = listOf("orgUnit1", "orgUnit2"),
        periods: List<String> = listOf("202401", "202402")
    ) = Dataset(
        uid = uid,
        displayName = displayName,
        organisationUnits = organisationUnits,
        periods = periods
    )

    fun createTestDatasetInstance(
        datasetUid: String = "testDataset123",
        period: String = "202401",
        organisationUnitUid: String = "orgUnit1",
        attributeOptionCombo: String = "defaultCombo",
        lastUpdated: LocalDate = LocalDate.now(),
        isComplete: Boolean = false,
        hasDataValues: Boolean = true
    ) = DatasetInstance(
        datasetUid = datasetUid,
        period = period,
        organisationUnitUid = organisationUnitUid,
        attributeOptionCombo = attributeOptionCombo,
        lastUpdated = lastUpdated,
        isComplete = isComplete,
        hasDataValues = hasDataValues
    )

    fun createTestDataValue(
        dataElement: String = "dataElement1",
        categoryOptionCombo: String = "categoryCombo1",
        value: String? = "100",
        storedBy: String? = "testUser"
    ) = DataValue(
        dataElement = dataElement,
        categoryOptionCombo = categoryOptionCombo,
        value = value,
        storedBy = storedBy
    )

    fun createTestDataEntry(
        datasetUid: String = "testDataset123",
        period: String = "202401",
        organisationUnitUid: String = "orgUnit1",
        attributeOptionCombo: String = "defaultCombo",
        dataValues: List<DataValue> = listOf(createTestDataValue()),
        lastUpdated: LocalDate = LocalDate.now(),
        isComplete: Boolean = false
    ) = DataEntry(
        datasetUid = datasetUid,
        period = period,
        organisationUnitUid = organisationUnitUid,
        attributeOptionCombo = attributeOptionCombo,
        dataValues = dataValues,
        lastUpdated = lastUpdated,
        isComplete = isComplete
    )

    fun createTestSavedAccount(
        uid: String = "account123",
        displayName: String = "Test Account",
        serverUrl: String = "https://test.dhis2.org",
        username: String = "testuser"
    ) = SavedAccount(
        uid = uid,
        displayName = displayName,
        serverUrl = serverUrl,
        username = username
    )

    fun createTestValidationIssue(
        ruleId: String = "rule123",
        ruleName: String = "Test Validation Rule",
        description: String = "Test validation failed",
        severity: ValidationSeverity = ValidationSeverity.ERROR,
        affectedDataElements: List<String> = emptyList()
    ) = ValidationIssue(
        ruleId = ruleId,
        ruleName = ruleName,
        description = description,
        severity = severity,
        affectedDataElements = affectedDataElements
    )

    fun createTestValidationResult(
        type: ValidationResultType = ValidationResultType.SUCCESS,
        message: String = "Validation passed",
        issues: List<ValidationIssue> = emptyList()
    ) = when (type) {
        ValidationResultType.SUCCESS -> ValidationResult.Success(message)
        ValidationResultType.ERROR -> ValidationResult.Error(issues.ifEmpty { listOf(createTestValidationIssue()) })
        ValidationResultType.WARNING -> ValidationResult.Warning(issues.ifEmpty { listOf(createTestValidationIssue(severity = ValidationSeverity.WARNING)) })
        ValidationResultType.MIXED -> ValidationResult.Mixed(
            errors = issues.filter { it.severity == ValidationSeverity.ERROR }.ifEmpty { listOf(createTestValidationIssue()) },
            warnings = issues.filter { it.severity == ValidationSeverity.WARNING }.ifEmpty { listOf(createTestValidationIssue(severity = ValidationSeverity.WARNING)) }
        )
    }

    fun createTestValidationSummary(
        totalRulesChecked: Int = 5,
        passedRules: Int = 3,
        errorCount: Int = 1,
        warningCount: Int = 1,
        canComplete: Boolean = false,
        executionTimeMs: Long = 150L,
        validationResult: ValidationResult = createTestValidationResult()
    ) = ValidationSummary(
        totalRulesChecked = totalRulesChecked,
        passedRules = passedRules,
        errorCount = errorCount,
        warningCount = warningCount,
        canComplete = canComplete,
        executionTimeMs = executionTimeMs,
        validationResult = validationResult
    )

    fun createTestDhis2Config(
        serverUrl: String = "https://test.dhis2.org",
        username: String = "testuser",
        password: String = "testpass"
    ) = Dhis2Config(
        serverUrl = serverUrl,
        username = username,
        password = password
    )

    enum class ValidationResultType {
        SUCCESS, ERROR, WARNING, MIXED
    }
}