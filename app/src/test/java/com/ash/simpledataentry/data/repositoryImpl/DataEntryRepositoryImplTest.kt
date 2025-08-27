package com.ash.simpledataentry.data.repositoryImpl

import app.cash.turbine.test
import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.data.local.DataValueDao
import com.ash.simpledataentry.data.local.DataValueEntity
import com.ash.simpledataentry.domain.model.DataValue
import com.ash.simpledataentry.domain.model.DataValueValidationResult
import com.ash.simpledataentry.testutil.MainDispatcherRule
import com.ash.simpledataentry.testutil.TestConstants.TEST_ATTRIBUTE_COMBO
import com.ash.simpledataentry.testutil.TestConstants.TEST_DATASET_UID
import com.ash.simpledataentry.testutil.TestConstants.TEST_ORG_UNIT_UID
import com.ash.simpledataentry.testutil.TestConstants.TEST_PERIOD
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.dataelement.DataElement
import org.hisp.dhis.android.core.dataelement.DataElementCollectionRepository
import org.hisp.dhis.android.core.dataelement.DataElementModule
import org.hisp.dhis.android.core.datavalue.DataValueCollectionRepository
import org.hisp.dhis.android.core.datavalue.DataValueModule
import org.hisp.dhis.android.core.datavalue.DataValueObjectRepository
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

class DataEntryRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Mock
    private lateinit var sessionManager: SessionManager

    @Mock
    private lateinit var dataValueDao: DataValueDao

    @Mock
    private lateinit var d2: D2

    @Mock
    private lateinit var dataValueModule: DataValueModule

    @Mock
    private lateinit var dataValueCollectionRepository: DataValueCollectionRepository

    @Mock
    private lateinit var dataValueObjectRepository: DataValueObjectRepository

    @Mock
    private lateinit var dataElementModule: DataElementModule

    @Mock
    private lateinit var dataElementCollectionRepository: DataElementCollectionRepository

    @Mock
    private lateinit var dataElement: DataElement

    private lateinit var repository: DataEntryRepositoryImpl

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        whenever(sessionManager.getD2()).thenReturn(d2)
        whenever(d2.dataValueModule()).thenReturn(dataValueModule)
        whenever(dataValueModule.dataValues()).thenReturn(dataValueCollectionRepository)
        whenever(d2.dataElementModule()).thenReturn(dataElementModule)
        whenever(dataElementModule.dataElements()).thenReturn(dataElementCollectionRepository)
        
        repository = DataEntryRepositoryImpl(sessionManager, dataValueDao)
    }

    @Test
    fun `getDataValues should return flow of data values from local database`() = runTest {
        // Arrange
        val localDataValues = listOf(
            DataValueEntity(
                dataElement = "element1",
                period = TEST_PERIOD,
                organisationUnit = TEST_ORG_UNIT_UID,
                categoryOptionCombo = "categoryCombo1",
                attributeOptionCombo = TEST_ATTRIBUTE_COMBO,
                value = "100",
                storedBy = "testuser",
                created = "2024-01-01T00:00:00.000",
                lastUpdated = "2024-01-01T00:00:00.000"
            )
        )
        
        whenever(dataValueDao.getDataValues(TEST_PERIOD, TEST_ORG_UNIT_UID, TEST_ATTRIBUTE_COMBO))
            .thenReturn(flowOf(localDataValues))

        // Act
        val result = repository.getDataValues(TEST_DATASET_UID, TEST_PERIOD, TEST_ORG_UNIT_UID, TEST_ATTRIBUTE_COMBO)

        // Assert
        result.test {
            val dataValues = awaitItem()
            assertThat(dataValues).hasSize(1)
            assertThat(dataValues[0].dataElement).isEqualTo("element1")
            assertThat(dataValues[0].value).isEqualTo("100")
            awaitComplete()
        }
    }

    @Test
    fun `saveDataValue should save to DHIS2 and local database when online`() = runTest {
        // Arrange
        whenever(dataValueCollectionRepository.value(any(), any(), any(), any(), any()))
            .thenReturn(dataValueObjectRepository)
        whenever(dataValueObjectRepository.blockingSet(any())).thenReturn(Unit)

        // Act
        val result = repository.saveDataValue(
            TEST_DATASET_UID, TEST_PERIOD, TEST_ORG_UNIT_UID, TEST_ATTRIBUTE_COMBO,
            "element1", "categoryCombo1", "150", "Test comment"
        )

        // Assert
        assertThat(result.isSuccess).isTrue()
        verify(dataValueObjectRepository).blockingSet("150")
        verify(dataValueDao).insertDataValue(any())
    }

    @Test
    fun `saveDataValue should handle DHIS2 save failure gracefully`() = runTest {
        // Arrange
        whenever(dataValueCollectionRepository.value(any(), any(), any(), any(), any()))
            .thenReturn(dataValueObjectRepository)
        whenever(dataValueObjectRepository.blockingSet(any()))
            .thenThrow(RuntimeException("Network error"))

        // Act
        val result = repository.saveDataValue(
            TEST_DATASET_UID, TEST_PERIOD, TEST_ORG_UNIT_UID, TEST_ATTRIBUTE_COMBO,
            "element1", "categoryCombo1", "150", "Test comment"
        )

        // Assert
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Network error")
        verify(dataValueDao, never()).insertDataValue(any())
    }

    @Test
    fun `saveDataValue should handle null or empty values`() = runTest {
        // Arrange
        whenever(dataValueCollectionRepository.value(any(), any(), any(), any(), any()))
            .thenReturn(dataValueObjectRepository)

        // Act
        val result = repository.saveDataValue(
            TEST_DATASET_UID, TEST_PERIOD, TEST_ORG_UNIT_UID, TEST_ATTRIBUTE_COMBO,
            "element1", "categoryCombo1", null, null
        )

        // Assert
        assertThat(result.isSuccess).isTrue()
        verify(dataValueObjectRepository).blockingSet(null)
    }

    @Test
    fun `validateValue should return valid for numeric data element with valid number`() = runTest {
        // Arrange
        whenever(dataElementCollectionRepository.uid("element1")).thenReturn(dataElementCollectionRepository)
        whenever(dataElementCollectionRepository.blockingGet()).thenReturn(dataElement)
        whenever(dataElement.valueType()).thenReturn(org.hisp.dhis.android.core.common.ValueType.NUMBER)

        // Act
        val result = repository.validateValue(TEST_DATASET_UID, "element1", "123.45")

        // Assert
        assertThat(result).isInstanceOf(DataValueValidationResult.Valid::class.java)
    }

    @Test
    fun `validateValue should return invalid for numeric data element with non-numeric value`() = runTest {
        // Arrange
        whenever(dataElementCollectionRepository.uid("element1")).thenReturn(dataElementCollectionRepository)
        whenever(dataElementCollectionRepository.blockingGet()).thenReturn(dataElement)
        whenever(dataElement.valueType()).thenReturn(org.hisp.dhis.android.core.common.ValueType.NUMBER)

        // Act
        val result = repository.validateValue(TEST_DATASET_UID, "element1", "not_a_number")

        // Assert
        assertThat(result).isInstanceOf(DataValueValidationResult.Invalid::class.java)
        val invalidResult = result as DataValueValidationResult.Invalid
        assertThat(invalidResult.message).contains("must be a valid number")
    }

    @Test
    fun `validateValue should return valid for text data element`() = runTest {
        // Arrange
        whenever(dataElementCollectionRepository.uid("element1")).thenReturn(dataElementCollectionRepository)
        whenever(dataElementCollectionRepository.blockingGet()).thenReturn(dataElement)
        whenever(dataElement.valueType()).thenReturn(org.hisp.dhis.android.core.common.ValueType.TEXT)

        // Act
        val result = repository.validateValue(TEST_DATASET_UID, "element1", "Any text value")

        // Assert
        assertThat(result).isInstanceOf(DataValueValidationResult.Valid::class.java)
    }

    @Test
    fun `validateValue should return valid for integer data element with valid integer`() = runTest {
        // Arrange
        whenever(dataElementCollectionRepository.uid("element1")).thenReturn(dataElementCollectionRepository)
        whenever(dataElementCollectionRepository.blockingGet()).thenReturn(dataElement)
        whenever(dataElement.valueType()).thenReturn(org.hisp.dhis.android.core.common.ValueType.INTEGER)

        // Act
        val result = repository.validateValue(TEST_DATASET_UID, "element1", "42")

        // Assert
        assertThat(result).isInstanceOf(DataValueValidationResult.Valid::class.java)
    }

    @Test
    fun `validateValue should return invalid for integer data element with decimal value`() = runTest {
        // Arrange
        whenever(dataElementCollectionRepository.uid("element1")).thenReturn(dataElementCollectionRepository)
        whenever(dataElementCollectionRepository.blockingGet()).thenReturn(dataElement)
        whenever(dataElement.valueType()).thenReturn(org.hisp.dhis.android.core.common.ValueType.INTEGER)

        // Act
        val result = repository.validateValue(TEST_DATASET_UID, "element1", "42.5")

        // Assert
        assertThat(result).isInstanceOf(DataValueValidationResult.Invalid::class.java)
        val invalidResult = result as DataValueValidationResult.Invalid
        assertThat(invalidResult.message).contains("must be a valid integer")
    }

    @Test
    fun `validateValue should handle data element not found`() = runTest {
        // Arrange
        whenever(dataElementCollectionRepository.uid("element1")).thenReturn(dataElementCollectionRepository)
        whenever(dataElementCollectionRepository.blockingGet()).thenReturn(null)

        // Act
        val result = repository.validateValue(TEST_DATASET_UID, "element1", "123")

        // Assert
        assertThat(result).isInstanceOf(DataValueValidationResult.Invalid::class.java)
        val invalidResult = result as DataValueValidationResult.Invalid
        assertThat(invalidResult.message).contains("Data element not found")
    }

    @Test
    fun `validateValue should handle D2 not available`() = runTest {
        // Arrange
        whenever(sessionManager.getD2()).thenReturn(null)

        // Act
        val result = repository.validateValue(TEST_DATASET_UID, "element1", "123")

        // Assert
        assertThat(result).isInstanceOf(DataValueValidationResult.Invalid::class.java)
        val invalidResult = result as DataValueValidationResult.Invalid
        assertThat(invalidResult.message).contains("DHIS2 session not available")
    }

    @Test
    fun `validateValue should handle validation errors gracefully`() = runTest {
        // Arrange
        whenever(dataElementCollectionRepository.uid("element1")).thenReturn(dataElementCollectionRepository)
        whenever(dataElementCollectionRepository.blockingGet()).thenThrow(RuntimeException("Database error"))

        // Act
        val result = repository.validateValue(TEST_DATASET_UID, "element1", "123")

        // Assert
        assertThat(result).isInstanceOf(DataValueValidationResult.Invalid::class.java)
        val invalidResult = result as DataValueValidationResult.Invalid
        assertThat(invalidResult.message).contains("Validation error")
    }
}