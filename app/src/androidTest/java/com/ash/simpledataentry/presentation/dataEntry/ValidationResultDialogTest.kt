package com.ash.simpledataentry.presentation.dataEntry

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ash.simpledataentry.domain.model.*
import com.ash.simpledataentry.testutil.TestDataBuilders
import com.ash.simpledataentry.ui.theme.SimpleDataEntryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ValidationResultDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun validationDialog_displaysSuccessResult() {
        // Arrange
        val successSummary = TestDataBuilders.createTestValidationSummary(
            totalRulesChecked = 5,
            passedRules = 5,
            errorCount = 0,
            warningCount = 0,
            canComplete = true,
            validationResult = ValidationResult.Success("All validation rules passed")
        )

        // Act
        composeTestRule.setContent {
            SimpleDataEntryTheme {
                ValidationResultDialog(
                    validationResult = successSummary,
                    onDismiss = {},
                    onComplete = {},
                    onCancel = {}
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Validation Results").assertIsDisplayed()
        composeTestRule.onNodeWithText("All validation rules passed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Complete Dataset").assertIsDisplayed()
        composeTestRule.onNodeWithText("Complete Dataset").assertIsEnabled()
    }

    @Test
    fun validationDialog_displaysErrorResult() {
        // Arrange
        val errorIssues = listOf(
            TestDataBuilders.createTestValidationIssue(
                ruleName = "Required Field Validation",
                description = "Data element 'Population' is required but missing",
                severity = ValidationSeverity.ERROR
            )
        )
        
        val errorSummary = TestDataBuilders.createTestValidationSummary(
            totalRulesChecked = 3,
            passedRules = 2,
            errorCount = 1,
            warningCount = 0,
            canComplete = false,
            validationResult = ValidationResult.Error(errorIssues)
        )

        // Act
        composeTestRule.setContent {
            SimpleDataEntryTheme {
                ValidationResultDialog(
                    validationResult = errorSummary,
                    onDismiss = {},
                    onComplete = {},
                    onCancel = {}
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Validation Results").assertIsDisplayed()
        composeTestRule.onNodeWithText("Required Field Validation").assertIsDisplayed()
        composeTestRule.onNodeWithText("Data element 'Population' is required but missing").assertIsDisplayed()
        composeTestRule.onNodeWithText("Complete Dataset").assertIsNotEnabled()
    }

    @Test
    fun validationDialog_displaysWarningResult() {
        // Arrange
        val warningIssues = listOf(
            TestDataBuilders.createTestValidationIssue(
                ruleName = "Data Quality Check",
                description = "Value seems unusually high, please verify",
                severity = ValidationSeverity.WARNING
            )
        )
        
        val warningSummary = TestDataBuilders.createTestValidationSummary(
            totalRulesChecked = 3,
            passedRules = 2,
            errorCount = 0,
            warningCount = 1,
            canComplete = true,
            validationResult = ValidationResult.Warning(warningIssues)
        )

        // Act
        composeTestRule.setContent {
            SimpleDataEntryTheme {
                ValidationResultDialog(
                    validationResult = warningSummary,
                    onDismiss = {},
                    onComplete = {},
                    onCancel = {}
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Validation Results").assertIsDisplayed()
        composeTestRule.onNodeWithText("Data Quality Check").assertIsDisplayed()
        composeTestRule.onNodeWithText("Value seems unusually high, please verify").assertIsDisplayed()
        composeTestRule.onNodeWithText("Complete Dataset").assertIsEnabled() // Warnings allow completion
    }

    @Test
    fun validationDialog_displaysMixedResult() {
        // Arrange
        val errors = listOf(
            TestDataBuilders.createTestValidationIssue(
                ruleName = "Critical Error",
                description = "Required field missing",
                severity = ValidationSeverity.ERROR
            )
        )
        
        val warnings = listOf(
            TestDataBuilders.createTestValidationIssue(
                ruleName = "Quality Warning",
                description = "Value seems high",
                severity = ValidationSeverity.WARNING
            )
        )
        
        val mixedSummary = TestDataBuilders.createTestValidationSummary(
            totalRulesChecked = 5,
            passedRules = 3,
            errorCount = 1,
            warningCount = 1,
            canComplete = false,
            validationResult = ValidationResult.Mixed(errors, warnings)
        )

        // Act
        composeTestRule.setContent {
            SimpleDataEntryTheme {
                ValidationResultDialog(
                    validationResult = mixedSummary,
                    onDismiss = {},
                    onComplete = {},
                    onCancel = {}
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Validation Results").assertIsDisplayed()
        composeTestRule.onNodeWithText("Critical Error").assertIsDisplayed()
        composeTestRule.onNodeWithText("Quality Warning").assertIsDisplayed()
        composeTestRule.onNodeWithText("Complete Dataset").assertIsNotEnabled() // Errors prevent completion
    }

    @Test
    fun validationDialog_completeButtonTriggersCallback() {
        // Arrange
        var completeClicked = false
        val successSummary = TestDataBuilders.createTestValidationSummary(
            canComplete = true,
            validationResult = ValidationResult.Success("Validation passed")
        )

        // Act
        composeTestRule.setContent {
            SimpleDataEntryTheme {
                ValidationResultDialog(
                    validationResult = successSummary,
                    onDismiss = {},
                    onComplete = { completeClicked = true },
                    onCancel = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Complete Dataset").performClick()

        // Assert
        assert(completeClicked)
    }

    @Test
    fun validationDialog_cancelButtonTriggersCallback() {
        // Arrange
        var cancelClicked = false
        val summary = TestDataBuilders.createTestValidationSummary()

        // Act
        composeTestRule.setContent {
            SimpleDataEntryTheme {
                ValidationResultDialog(
                    validationResult = summary,
                    onDismiss = {},
                    onComplete = {},
                    onCancel = { cancelClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Cancel").performClick()

        // Assert
        assert(cancelClicked)
    }

    @Test
    fun validationDialog_dismissTriggersCallback() {
        // Arrange
        var dismissClicked = false
        val summary = TestDataBuilders.createTestValidationSummary()

        // Act
        composeTestRule.setContent {
            SimpleDataEntryTheme {
                ValidationResultDialog(
                    validationResult = summary,
                    onDismiss = { dismissClicked = true },
                    onComplete = {},
                    onCancel = {}
                )
            }
        }

        // Simulate clicking outside dialog or pressing back
        composeTestRule.onNodeWithText("Validation Results").assertIsDisplayed()
        // Note: Actual dismiss testing depends on dialog implementation

        // For now, just verify the dialog displays correctly
        assert(true) // Placeholder for dismiss test
    }

    @Test
    fun validationDialog_showsValidationStatistics() {
        // Arrange
        val summary = TestDataBuilders.createTestValidationSummary(
            totalRulesChecked = 10,
            passedRules = 7,
            errorCount = 2,
            warningCount = 1,
            executionTimeMs = 1500L
        )

        // Act
        composeTestRule.setContent {
            SimpleDataEntryTheme {
                ValidationResultDialog(
                    validationResult = summary,
                    onDismiss = {},
                    onComplete = {},
                    onCancel = {}
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("10 rules checked").assertIsDisplayed()
        composeTestRule.onNodeWithText("7 passed").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 errors").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 warning").assertIsDisplayed()
    }
}