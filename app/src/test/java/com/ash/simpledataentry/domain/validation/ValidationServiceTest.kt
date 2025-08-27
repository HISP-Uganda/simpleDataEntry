package com.ash.simpledataentry.domain.validation

import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.domain.model.*
import com.ash.simpledataentry.testutil.MainDispatcherRule
import com.ash.simpledataentry.testutil.TestConstants.TEST_ATTRIBUTE_COMBO
import com.ash.simpledataentry.testutil.TestConstants.TEST_DATASET_UID
import com.ash.simpledataentry.testutil.TestConstants.TEST_ORG_UNIT_UID
import com.ash.simpledataentry.testutil.TestConstants.TEST_PERIOD
import com.ash.simpledataentry.testutil.TestDataBuilders
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.datavalue.DataValueCollectionRepository
import org.hisp.dhis.android.core.datavalue.DataValueModule
import org.hisp.dhis.android.core.datavalue.DataValueObjectRepository
import org.hisp.dhis.android.core.validation.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

class ValidationServiceTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Mock
    private lateinit var sessionManager: SessionManager

    @Mock
    private lateinit var d2: D2

    @Mock
    private lateinit var validationModule: ValidationModule

    @Mock
    private lateinit var validationRuleCollectionRepository: ValidationRuleCollectionRepository

    @Mock
    private lateinit var dataValueModule: DataValueModule

    @Mock
    private lateinit var dataValueCollectionRepository: DataValueCollectionRepository

    @Mock
    private lateinit var dataValueObjectRepository: DataValueObjectRepository

    private lateinit var validationService: ValidationService

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        validationService = ValidationService(sessionManager)
        
        whenever(sessionManager.getD2()).thenReturn(d2)
        whenever(d2.validationModule()).thenReturn(validationModule)
        whenever(validationModule.validationRules()).thenReturn(validationRuleCollectionRepository)
        whenever(d2.dataValueModule()).thenReturn(dataValueModule)
        whenever(dataValueModule.dataValues()).thenReturn(dataValueCollectionRepository)
    }

    @Test
    fun `validateDatasetInstance should return error when D2 is not available`() = runTest {
        // Arrange
        whenever(sessionManager.getD2()).thenReturn(null)
        val dataValues = listOf(TestDataBuilders.createTestDataValue())

        // Act
        val result = validationService.validateDatasetInstance(
            TEST_DATASET_UID, TEST_PERIOD, TEST_ORG_UNIT_UID, TEST_ATTRIBUTE_COMBO, dataValues
        )

        // Assert
        assertThat(result.canComplete).isFalse()
        assertThat(result.errorCount).isEqualTo(1)
        assertThat(result.validationResult).isInstanceOf(ValidationResult.Error::class.java)
    }

    @Test
    fun `validateDatasetInstance should return success when no validation rules exist`() = runTest {
        // Arrange
        whenever(validationRuleCollectionRepository.byDataSetUids(listOf(TEST_DATASET_UID)))
            .thenReturn(validationRuleCollectionRepository)
        whenever(validationRuleCollectionRepository.blockingGet()).thenReturn(emptyList())
        val dataValues = listOf(TestDataBuilders.createTestDataValue())

        // Act
        val result = validationService.validateDatasetInstance(
            TEST_DATASET_UID, TEST_PERIOD, TEST_ORG_UNIT_UID, TEST_ATTRIBUTE_COMBO, dataValues
        )

        // Assert
        assertThat(result.canComplete).isTrue()
        assertThat(result.totalRulesChecked).isEqualTo(0)
        assertThat(result.errorCount).isEqualTo(0)
        assertThat(result.validationResult).isInstanceOf(ValidationResult.Success::class.java)
    }

    @Test
    fun `validateDatasetInstance should process validation rules correctly`() = runTest {
        // Arrange
        val mockValidationRule = mock<ValidationRule> {
            on { uid() } doReturn "rule123"
            on { name() } doReturn "Test Rule"
            on { importance() } doReturn ValidationRuleImportance.MEDIUM
            on { leftSide() } doReturn mock {
                on { expression() } doReturn "#{dataElement1.categoryCombo1}"
            }
            on { rightSide() } doReturn mock {
                on { expression() } doReturn "100"
            }
        }

        val mockDataValue = mock<org.hisp.dhis.android.core.datavalue.DataValue> {
            on { dataElement() } doReturn "dataElement1"
            on { categoryOptionCombo() } doReturn "categoryCombo1"
            on { value() } doReturn "50"
        }

        whenever(validationRuleCollectionRepository.byDataSetUids(listOf(TEST_DATASET_UID)))
            .thenReturn(validationRuleCollectionRepository)
        whenever(validationRuleCollectionRepository.blockingGet())
            .thenReturn(listOf(mockValidationRule))

        whenever(dataValueCollectionRepository.byPeriod()).thenReturn(dataValueCollectionRepository)
        whenever(dataValueCollectionRepository.eq(TEST_PERIOD)).thenReturn(dataValueCollectionRepository)
        whenever(dataValueCollectionRepository.byOrganisationUnitUid()).thenReturn(dataValueCollectionRepository)
        whenever(dataValueCollectionRepository.eq(TEST_ORG_UNIT_UID)).thenReturn(dataValueCollectionRepository)
        whenever(dataValueCollectionRepository.byAttributeOptionComboUid()).thenReturn(dataValueCollectionRepository)
        whenever(dataValueCollectionRepository.eq(TEST_ATTRIBUTE_COMBO)).thenReturn(dataValueCollectionRepository)
        whenever(dataValueCollectionRepository.blockingGet()).thenReturn(listOf(mockDataValue))

        // Setup data value saving
        whenever(dataValueCollectionRepository.value(any(), any(), any(), any(), any()))
            .thenReturn(dataValueObjectRepository)

        val dataValues = listOf(
            TestDataBuilders.createTestDataValue(
                dataElement = "dataElement1",
                categoryOptionCombo = "categoryCombo1",
                value = "50"
            )
        )

        // Act
        val result = validationService.validateDatasetInstance(
            TEST_DATASET_UID, TEST_PERIOD, TEST_ORG_UNIT_UID, TEST_ATTRIBUTE_COMBO, dataValues
        )

        // Assert
        assertThat(result.totalRulesChecked).isEqualTo(1)
        assertThat(result.canComplete).isTrue() // Should pass with sufficient data
        verify(dataValueObjectRepository).blockingSet("50")
    }

    @Test
    fun `validateDatasetInstance should handle high importance rules with insufficient data`() = runTest {
        // Arrange
        val mockValidationRule = mock<ValidationRule> {
            on { uid() } doReturn "rule123"
            on { name() } doReturn "High Priority Rule"
            on { importance() } doReturn ValidationRuleImportance.HIGH
            on { leftSide() } doReturn mock {
                on { expression() } doReturn "#{dataElement1.categoryCombo1}"
            }
            on { rightSide() } doReturn mock {
                on { expression() } doReturn "#{dataElement2.categoryCombo1}"
            }
        }

        whenever(validationRuleCollectionRepository.byDataSetUids(listOf(TEST_DATASET_UID)))
            .thenReturn(validationRuleCollectionRepository)
        whenever(validationRuleCollectionRepository.blockingGet())
            .thenReturn(listOf(mockValidationRule))

        whenever(dataValueCollectionRepository.byPeriod()).thenReturn(dataValueCollectionRepository)
        whenever(dataValueCollectionRepository.eq(TEST_PERIOD)).thenReturn(dataValueCollectionRepository)
        whenever(dataValueCollectionRepository.byOrganisationUnitUid()).thenReturn(dataValueCollectionRepository)
        whenever(dataValueCollectionRepository.eq(TEST_ORG_UNIT_UID)).thenReturn(dataValueCollectionRepository)
        whenever(dataValueCollectionRepository.byAttributeOptionComboUid()).thenReturn(dataValueCollectionRepository)
        whenever(dataValueCollectionRepository.eq(TEST_ATTRIBUTE_COMBO)).thenReturn(dataValueCollectionRepository)
        whenever(dataValueCollectionRepository.blockingGet()).thenReturn(emptyList())

        whenever(dataValueCollectionRepository.value(any(), any(), any(), any(), any()))
            .thenReturn(dataValueObjectRepository)

        val dataValues = listOf(TestDataBuilders.createTestDataValue())

        // Act
        val result = validationService.validateDatasetInstance(
            TEST_DATASET_UID, TEST_PERIOD, TEST_ORG_UNIT_UID, TEST_ATTRIBUTE_COMBO, dataValues
        )

        // Assert
        assertThat(result.totalRulesChecked).isEqualTo(1)
        assertThat(result.warningCount).isGreaterThan(0)
        assertThat(result.canComplete).isTrue() // Warnings don't prevent completion
    }

    @Test
    fun `validateDatasetInstance should handle validation rule processing errors gracefully`() = runTest {
        // Arrange
        whenever(validationRuleCollectionRepository.byDataSetUids(listOf(TEST_DATASET_UID)))
            .thenReturn(validationRuleCollectionRepository)
        whenever(validationRuleCollectionRepository.blockingGet())
            .thenThrow(RuntimeException("Validation rules fetch failed"))

        val dataValues = listOf(TestDataBuilders.createTestDataValue())

        // Act
        val result = validationService.validateDatasetInstance(
            TEST_DATASET_UID, TEST_PERIOD, TEST_ORG_UNIT_UID, TEST_ATTRIBUTE_COMBO, dataValues
        )

        // Assert
        assertThat(result.errorCount).isEqualTo(1)
        assertThat(result.canComplete).isFalse()
        assertThat(result.validationResult).isInstanceOf(ValidationResult.Error::class.java)
    }
}