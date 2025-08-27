package com.ash.simpledataentry.domain.useCase

import com.ash.simpledataentry.domain.model.DataValue
import com.ash.simpledataentry.domain.model.DataValueValidationResult
import com.ash.simpledataentry.domain.model.ValidationResult
import com.ash.simpledataentry.domain.repository.DataEntryRepository
import com.ash.simpledataentry.testutil.MainDispatcherRule
import com.ash.simpledataentry.testutil.TestConstants.TEST_ATTRIBUTE_COMBO
import com.ash.simpledataentry.testutil.TestConstants.TEST_DATASET_UID
import com.ash.simpledataentry.testutil.TestConstants.TEST_ORG_UNIT_UID
import com.ash.simpledataentry.testutil.TestConstants.TEST_PERIOD
import com.ash.simpledataentry.testutil.TestDataBuilders
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class DataEntryUseCasesTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Mock
    private lateinit var repository: DataEntryRepository

    private lateinit var getDataValuesUseCase: GetDataValuesUseCase
    private lateinit var saveDataValueUseCase: SaveDataValueUseCase
    private lateinit var validateValueUseCase: ValidateValueUseCase

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        getDataValuesUseCase = GetDataValuesUseCase(repository)
        saveDataValueUseCase = SaveDataValueUseCase(repository)
        validateValueUseCase = ValidateValueUseCase(repository)
    }

    @Test
    fun `getDataValues should return flow of data values from repository`() = runTest {
        // Arrange
        val expectedDataValues = listOf(
            TestDataBuilders.createTestDataValue(dataElement = "element1", value = "100"),
            TestDataBuilders.createTestDataValue(dataElement = "element2", value = "200")
        )
        whenever(
            repository.getDataValues(TEST_DATASET_UID, TEST_PERIOD, TEST_ORG_UNIT_UID, TEST_ATTRIBUTE_COMBO)
        ).thenReturn(flowOf(expectedDataValues))

        // Act
        val result = getDataValuesUseCase(TEST_DATASET_UID, TEST_PERIOD, TEST_ORG_UNIT_UID, TEST_ATTRIBUTE_COMBO)

        // Assert
        result.collect { dataValues ->
            assertThat(dataValues).hasSize(2)
            assertThat(dataValues).containsExactlyElementsIn(expectedDataValues)
        }
    }

    @Test
    fun `saveDataValue should return success result when repository saves successfully`() = runTest {
        // Arrange
        val expectedDataValue = TestDataBuilders.createTestDataValue(value = "150")
        whenever(
            repository.saveDataValue(
                TEST_DATASET_UID, TEST_PERIOD, TEST_ORG_UNIT_UID, TEST_ATTRIBUTE_COMBO,
                "element1", "categoryCombo1", "150", "Test comment"
            )
        ).thenReturn(Result.success(expectedDataValue))

        // Act
        val result = saveDataValueUseCase(
            TEST_DATASET_UID, TEST_PERIOD, TEST_ORG_UNIT_UID, TEST_ATTRIBUTE_COMBO,
            "element1", "categoryCombo1", "150", "Test comment"
        )

        // Assert
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(expectedDataValue)
    }

    @Test
    fun `saveDataValue should return failure result when repository fails`() = runTest {
        // Arrange
        val exception = RuntimeException("Save failed")
        whenever(
            repository.saveDataValue(
                TEST_DATASET_UID, TEST_PERIOD, TEST_ORG_UNIT_UID, TEST_ATTRIBUTE_COMBO,
                "element1", "categoryCombo1", "invalid", null
            )
        ).thenReturn(Result.failure(exception))

        // Act
        val result = saveDataValueUseCase(
            TEST_DATASET_UID, TEST_PERIOD, TEST_ORG_UNIT_UID, TEST_ATTRIBUTE_COMBO,
            "element1", "categoryCombo1", "invalid", null
        )

        // Assert
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isEqualTo(exception)
    }

    @Test
    fun `validateValue should return validation result from repository`() = runTest {
        // Arrange
        val expectedResult = DataValueValidationResult.Valid("Value is valid")
        whenever(
            repository.validateValue(TEST_DATASET_UID, "element1", "100")
        ).thenReturn(expectedResult)

        // Act
        val result = validateValueUseCase(TEST_DATASET_UID, "element1", "100")

        // Assert
        assertThat(result).isEqualTo(expectedResult)
    }

    @Test
    fun `validateValue should return invalid result for invalid input`() = runTest {
        // Arrange
        val expectedResult = DataValueValidationResult.Invalid("Value must be a number")
        whenever(
            repository.validateValue(TEST_DATASET_UID, "element1", "invalid")
        ).thenReturn(expectedResult)

        // Act
        val result = validateValueUseCase(TEST_DATASET_UID, "element1", "invalid")

        // Assert
        assertThat(result).isEqualTo(expectedResult)
    }
}