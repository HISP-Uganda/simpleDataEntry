package com.ash.simpledataentry.presentation.dataEntry

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.ash.simpledataentry.data.local.DataValueDraftDao
import com.ash.simpledataentry.data.repositoryImpl.ValidationRepository
import com.ash.simpledataentry.domain.model.*
import com.ash.simpledataentry.domain.repository.DataEntryRepository
import com.ash.simpledataentry.domain.useCase.DataEntryUseCases
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
import org.mockito.kotlin.*

class DataEntryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var application: Application

    @Mock
    private lateinit var repository: DataEntryRepository

    @Mock
    private lateinit var useCases: DataEntryUseCases

    @Mock
    private lateinit var draftDao: DataValueDraftDao

    @Mock
    private lateinit var validationRepository: ValidationRepository

    private lateinit var viewModel: DataEntryViewModel

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        viewModel = DataEntryViewModel(application, repository, useCases, draftDao, validationRepository)
    }

    @Test
    fun `initial state should have default values`() = runTest {
        viewModel.state.test {
            val initialState = awaitItem()
            
            assertThat(initialState.datasetId).isEmpty()
            assertThat(initialState.isLoading).isFalse()
            assertThat(initialState.showLoadingScreen).isTrue()
            assertThat(initialState.isContentReady).isFalse()
            assertThat(initialState.error).isNull()
            assertThat(initialState.dataValues).isEmpty()
            assertThat(initialState.validationState).isEqualTo(ValidationState.VALID)
        }
    }

    @Test
    fun `loadDataValues should update state with dataset information`() = runTest {
        // Arrange
        val testDataValues = listOf(
            TestDataBuilders.createTestDataValue(dataElement = "element1", value = "100"),
            TestDataBuilders.createTestDataValue(dataElement = "element2", value = "200")
        )
        
        whenever(useCases.getDataValues.invoke(any(), any(), any(), any()))
            .thenReturn(flowOf(testDataValues))

        // Act & Assert
        viewModel.state.test {
            val initialState = awaitItem()
            
            viewModel.loadDataValues(
                TEST_DATASET_UID,
                "Test Dataset",
                TEST_PERIOD,
                TEST_ORG_UNIT_UID,
                TEST_ATTRIBUTE_COMBO,
                "Test Combo"
            )

            // Skip loading states and get final state
            skipItems(1) // Loading state
            val finalState = awaitItem()

            assertThat(finalState.datasetId).isEqualTo(TEST_DATASET_UID)
            assertThat(finalState.datasetName).isEqualTo("Test Dataset")
            assertThat(finalState.period).isEqualTo(TEST_PERIOD)
            assertThat(finalState.orgUnit).isEqualTo(TEST_ORG_UNIT_UID)
            assertThat(finalState.attributeOptionCombo).isEqualTo(TEST_ATTRIBUTE_COMBO)
            assertThat(finalState.attributeOptionComboName).isEqualTo("Test Combo")
            assertThat(finalState.isLoading).isFalse()
            assertThat(finalState.dataValues).hasSize(2)
        }
    }

    @Test
    fun `loadDataValues should handle error gracefully`() = runTest {
        // Arrange
        whenever(useCases.getDataValues.invoke(any(), any(), any(), any()))
            .thenThrow(RuntimeException("Network error"))

        // Act & Assert
        viewModel.state.test {
            val initialState = awaitItem()
            
            viewModel.loadDataValues(
                TEST_DATASET_UID,
                "Test Dataset",
                TEST_PERIOD,
                TEST_ORG_UNIT_UID,
                TEST_ATTRIBUTE_COMBO,
                "Test Combo"
            )

            skipItems(1) // Loading state
            val errorState = awaitItem()

            assertThat(errorState.isLoading).isFalse()
            assertThat(errorState.error).isNotNull()
            assertThat(errorState.error).contains("Network error")
        }
    }

    @Test
    fun `updateCurrentValue should create dirty data value`() = runTest {
        // Arrange
        val testDataValue = TestDataBuilders.createTestDataValue(
            dataElement = "element1",
            categoryOptionCombo = "combo1",
            value = "original"
        )
        
        whenever(useCases.getDataValues.invoke(any(), any(), any(), any()))
            .thenReturn(flowOf(listOf(testDataValue)))

        // Load initial data
        viewModel.loadDataValues(
            TEST_DATASET_UID,
            "Test Dataset",
            TEST_PERIOD,
            TEST_ORG_UNIT_UID,
            TEST_ATTRIBUTE_COMBO,
            "Test Combo"
        )

        // Act & Assert
        viewModel.state.test {
            skipItems(2) // Skip initial and loading states
            val loadedState = awaitItem()

            viewModel.updateCurrentValue("updated_value", "element1", "combo1")
            
            val updatedState = awaitItem()
            
            // Check that the data value was updated in the state
            val updatedDataValue = updatedState.dataValues.find { 
                it.dataElement == "element1" && it.categoryOptionCombo == "combo1" 
            }
            assertThat(updatedDataValue?.value).isEqualTo("updated_value")
        }
    }

    @Test
    fun `saveDataEntry should handle successful save`() = runTest {
        // Arrange
        val testDataValue = TestDataBuilders.createTestDataValue()
        whenever(useCases.saveDataValue.invoke(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Result.success(testDataValue))

        whenever(useCases.getDataValues.invoke(any(), any(), any(), any()))
            .thenReturn(flowOf(listOf(testDataValue)))

        // Load initial data
        viewModel.loadDataValues(
            TEST_DATASET_UID,
            "Test Dataset", 
            TEST_PERIOD,
            TEST_ORG_UNIT_UID,
            TEST_ATTRIBUTE_COMBO,
            "Test Combo"
        )

        // Make a change to create dirty data
        viewModel.updateCurrentValue("new_value", testDataValue.dataElement, testDataValue.categoryOptionCombo)

        // Act & Assert
        viewModel.state.test {
            skipItems(3) // Skip initial, loading, and update states
            
            viewModel.saveDataEntry()
            
            val savingState = awaitItem()
            assertThat(savingState.saveInProgress).isTrue()
            
            val savedState = awaitItem()
            assertThat(savedState.saveInProgress).isFalse()
            assertThat(savedState.saveResult?.isSuccess).isTrue()
        }
    }

    @Test
    fun `saveDataEntry should handle save failure`() = runTest {
        // Arrange
        val exception = RuntimeException("Save failed")
        whenever(useCases.saveDataValue.invoke(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Result.failure(exception))

        whenever(useCases.getDataValues.invoke(any(), any(), any(), any()))
            .thenReturn(flowOf(listOf(TestDataBuilders.createTestDataValue())))

        viewModel.loadDataValues(
            TEST_DATASET_UID,
            "Test Dataset",
            TEST_PERIOD,
            TEST_ORG_UNIT_UID,
            TEST_ATTRIBUTE_COMBO,
            "Test Combo"
        )

        viewModel.updateCurrentValue("new_value", "element1", "combo1")

        // Act & Assert
        viewModel.state.test {
            skipItems(3) // Skip initial, loading, and update states
            
            viewModel.saveDataEntry()
            
            val savingState = awaitItem()
            assertThat(savingState.saveInProgress).isTrue()
            
            val savedState = awaitItem()
            assertThat(savedState.saveInProgress).isFalse()
            assertThat(savedState.saveResult?.isFailure).isTrue()
        }
    }

    @Test
    fun `validateDatasetInstance should update validation state`() = runTest {
        // Arrange
        val validationSummary = TestDataBuilders.createTestValidationSummary(
            canComplete = true,
            errorCount = 0
        )
        
        whenever(validationRepository.validateDatasetInstance(any(), any(), any(), any(), any()))
            .thenReturn(validationSummary)

        whenever(useCases.getDataValues.invoke(any(), any(), any(), any()))
            .thenReturn(flowOf(listOf(TestDataBuilders.createTestDataValue())))

        viewModel.loadDataValues(
            TEST_DATASET_UID,
            "Test Dataset",
            TEST_PERIOD,
            TEST_ORG_UNIT_UID,
            TEST_ATTRIBUTE_COMBO,
            "Test Combo"
        )

        // Act & Assert
        viewModel.state.test {
            skipItems(2) // Skip initial and loading states
            val loadedState = awaitItem()

            viewModel.validateDatasetInstance()
            
            val validatingState = awaitItem()
            assertThat(validatingState.validationInProgress).isTrue()
            
            val validatedState = awaitItem()
            assertThat(validatedState.validationInProgress).isFalse()
            assertThat(validatedState.validationResult).isNotNull()
            assertThat(validatedState.validationResult?.canComplete).isTrue()
        }
    }

    @Test
    fun `toggleSectionExpansion should update expanded section state`() = runTest {
        // Arrange
        whenever(useCases.getDataValues.invoke(any(), any(), any(), any()))
            .thenReturn(flowOf(emptyList()))

        viewModel.loadDataValues(
            TEST_DATASET_UID,
            "Test Dataset",
            TEST_PERIOD,
            TEST_ORG_UNIT_UID,
            TEST_ATTRIBUTE_COMBO,
            "Test Combo"
        )

        // Act & Assert
        viewModel.state.test {
            skipItems(2) // Skip initial and loading states
            val loadedState = awaitItem()
            assertThat(loadedState.expandedSection).isNull()

            viewModel.toggleSectionExpansion("section1")
            
            val expandedState = awaitItem()
            assertThat(expandedState.expandedSection).isEqualTo("section1")

            viewModel.toggleSectionExpansion("section1")
            
            val collapsedState = awaitItem()
            assertThat(collapsedState.expandedSection).isNull()
        }
    }

    @Test
    fun `clearError should reset error state`() = runTest {
        // Arrange
        whenever(useCases.getDataValues.invoke(any(), any(), any(), any()))
            .thenThrow(RuntimeException("Test error"))

        viewModel.loadDataValues(
            TEST_DATASET_UID,
            "Test Dataset",
            TEST_PERIOD,
            TEST_ORG_UNIT_UID,
            TEST_ATTRIBUTE_COMBO,
            "Test Combo"
        )

        // Act & Assert
        viewModel.state.test {
            skipItems(1) // Skip initial state
            val loadingState = awaitItem()
            val errorState = awaitItem()
            assertThat(errorState.error).isNotNull()

            viewModel.clearError()
            
            val clearedState = awaitItem()
            assertThat(clearedState.error).isNull()
        }
    }
}