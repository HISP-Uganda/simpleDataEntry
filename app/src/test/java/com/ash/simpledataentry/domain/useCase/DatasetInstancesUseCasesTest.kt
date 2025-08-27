package com.ash.simpledataentry.domain.useCase

import com.ash.simpledataentry.domain.model.DatasetInstance
import com.ash.simpledataentry.domain.model.FilterState
import com.ash.simpledataentry.domain.repository.DatasetInstancesRepository
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

class DatasetInstancesUseCasesTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Mock
    private lateinit var repository: DatasetInstancesRepository

    private lateinit var getDatasetInstancesUseCase: GetDatasetInstancesUseCase
    private lateinit var syncDatasetInstancesUseCase: SyncDatasetInstancesUseCase
    private lateinit var completeDatasetInstanceUseCase: CompleteDatasetInstanceUseCase
    private lateinit var markDatasetInstanceIncompleteUseCase: MarkDatasetInstanceIncompleteUseCase

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        getDatasetInstancesUseCase = GetDatasetInstancesUseCase(repository)
        syncDatasetInstancesUseCase = SyncDatasetInstancesUseCase(repository)
        completeDatasetInstanceUseCase = CompleteDatasetInstanceUseCase(repository)
        markDatasetInstanceIncompleteUseCase = MarkDatasetInstanceIncompleteUseCase(repository)
    }

    @Test
    fun `getDatasetInstances should return filtered instances from repository`() = runTest {
        // Arrange
        val filterState = FilterState(
            selectedDatasets = listOf(TEST_DATASET_UID),
            selectedPeriods = listOf(TEST_PERIOD),
            selectedOrgUnits = listOf(TEST_ORG_UNIT_UID),
            showCompleted = true,
            showIncomplete = true
        )
        val expectedInstances = listOf(
            TestDataBuilders.createTestDatasetInstance(
                datasetUid = TEST_DATASET_UID,
                period = TEST_PERIOD,
                organisationUnitUid = TEST_ORG_UNIT_UID,
                isComplete = false
            ),
            TestDataBuilders.createTestDatasetInstance(
                datasetUid = TEST_DATASET_UID,
                period = TEST_PERIOD,
                organisationUnitUid = TEST_ORG_UNIT_UID,
                isComplete = true
            )
        )
        whenever(repository.getDatasetInstances(filterState)).thenReturn(flowOf(expectedInstances))

        // Act
        val result = getDatasetInstancesUseCase(filterState)

        // Assert
        result.collect { instances ->
            assertThat(instances).hasSize(2)
            assertThat(instances).containsExactlyElementsIn(expectedInstances)
        }
    }

    @Test
    fun `syncDatasetInstances should return success when sync completes`() = runTest {
        // Arrange
        whenever(repository.syncDatasetInstances()).thenReturn(Result.success(Unit))

        // Act
        val result = syncDatasetInstancesUseCase()

        // Assert
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun `syncDatasetInstances should return failure when sync fails`() = runTest {
        // Arrange
        val exception = RuntimeException("Network error")
        whenever(repository.syncDatasetInstances()).thenReturn(Result.failure(exception))

        // Act
        val result = syncDatasetInstancesUseCase()

        // Assert
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isEqualTo(exception)
    }

    @Test
    fun `completeDatasetInstance should return success when completion succeeds`() = runTest {
        // Arrange
        whenever(
            repository.completeDatasetInstance(
                TEST_DATASET_UID,
                TEST_PERIOD,
                TEST_ORG_UNIT_UID,
                TEST_ATTRIBUTE_COMBO
            )
        ).thenReturn(Result.success(Unit))

        // Act
        val result = completeDatasetInstanceUseCase(
            TEST_DATASET_UID,
            TEST_PERIOD,
            TEST_ORG_UNIT_UID,
            TEST_ATTRIBUTE_COMBO
        )

        // Assert
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun `completeDatasetInstance should return failure when completion fails`() = runTest {
        // Arrange
        val exception = RuntimeException("Validation failed")
        whenever(
            repository.completeDatasetInstance(
                TEST_DATASET_UID,
                TEST_PERIOD,
                TEST_ORG_UNIT_UID,
                TEST_ATTRIBUTE_COMBO
            )
        ).thenReturn(Result.failure(exception))

        // Act
        val result = completeDatasetInstanceUseCase(
            TEST_DATASET_UID,
            TEST_PERIOD,
            TEST_ORG_UNIT_UID,
            TEST_ATTRIBUTE_COMBO
        )

        // Assert
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isEqualTo(exception)
    }

    @Test
    fun `markDatasetInstanceIncomplete should return success when operation succeeds`() = runTest {
        // Arrange
        whenever(
            repository.markDatasetInstanceIncomplete(
                TEST_DATASET_UID,
                TEST_PERIOD,
                TEST_ORG_UNIT_UID,
                TEST_ATTRIBUTE_COMBO
            )
        ).thenReturn(Result.success(Unit))

        // Act
        val result = markDatasetInstanceIncompleteUseCase(
            TEST_DATASET_UID,
            TEST_PERIOD,
            TEST_ORG_UNIT_UID,
            TEST_ATTRIBUTE_COMBO
        )

        // Assert
        assertThat(result.isSuccess).isTrue()
    }
}