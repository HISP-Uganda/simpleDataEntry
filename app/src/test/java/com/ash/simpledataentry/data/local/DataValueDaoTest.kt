package com.ash.simpledataentry.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ash.simpledataentry.testutil.TestConstants.TEST_ATTRIBUTE_COMBO
import com.ash.simpledataentry.testutil.TestConstants.TEST_ORG_UNIT_UID
import com.ash.simpledataentry.testutil.TestConstants.TEST_PERIOD
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class DataValueDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dataValueDao: DataValueDao

    @Before
    fun createDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dataValueDao = database.dataValueDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    @Test
    fun insertAndGetDataValue() = runTest {
        // Arrange
        val dataValue = DataValueEntity(
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

        // Act
        dataValueDao.insertDataValue(dataValue)

        // Assert
        val dataValues = dataValueDao.getDataValues(
            TEST_PERIOD, TEST_ORG_UNIT_UID, TEST_ATTRIBUTE_COMBO
        ).first()
        
        assertThat(dataValues).hasSize(1)
        assertThat(dataValues[0]).isEqualTo(dataValue)
    }

    @Test
    fun insertMultipleDataValues() = runTest {
        // Arrange
        val dataValues = listOf(
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
            ),
            DataValueEntity(
                dataElement = "element2",
                period = TEST_PERIOD,
                organisationUnit = TEST_ORG_UNIT_UID,
                categoryOptionCombo = "categoryCombo2",
                attributeOptionCombo = TEST_ATTRIBUTE_COMBO,
                value = "200",
                storedBy = "testuser",
                created = "2024-01-01T00:00:00.000",
                lastUpdated = "2024-01-01T00:00:00.000"
            )
        )

        // Act
        dataValueDao.insertDataValues(dataValues)

        // Assert
        val retrievedValues = dataValueDao.getDataValues(
            TEST_PERIOD, TEST_ORG_UNIT_UID, TEST_ATTRIBUTE_COMBO
        ).first()
        
        assertThat(retrievedValues).hasSize(2)
        assertThat(retrievedValues).containsExactlyElementsIn(dataValues)
    }

    @Test
    fun updateDataValue() = runTest {
        // Arrange
        val originalDataValue = DataValueEntity(
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

        // Act
        dataValueDao.insertDataValue(originalDataValue)
        
        val updatedDataValue = originalDataValue.copy(
            value = "150",
            lastUpdated = "2024-01-02T00:00:00.000"
        )
        dataValueDao.insertDataValue(updatedDataValue) // Insert acts as upsert

        // Assert
        val dataValues = dataValueDao.getDataValues(
            TEST_PERIOD, TEST_ORG_UNIT_UID, TEST_ATTRIBUTE_COMBO
        ).first()
        
        assertThat(dataValues).hasSize(1)
        assertThat(dataValues[0].value).isEqualTo("150")
        assertThat(dataValues[0].lastUpdated).isEqualTo("2024-01-02T00:00:00.000")
    }

    @Test
    fun deleteDataValue() = runTest {
        // Arrange
        val dataValue = DataValueEntity(
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

        // Act
        dataValueDao.insertDataValue(dataValue)
        dataValueDao.deleteDataValue(dataValue)

        // Assert
        val dataValues = dataValueDao.getDataValues(
            TEST_PERIOD, TEST_ORG_UNIT_UID, TEST_ATTRIBUTE_COMBO
        ).first()
        
        assertThat(dataValues).isEmpty()
    }

    @Test
    fun getDataValuesFiltersByParameters() = runTest {
        // Arrange
        val dataValue1 = DataValueEntity(
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
        
        val dataValue2 = DataValueEntity(
            dataElement = "element2",
            period = "202402", // Different period
            organisationUnit = TEST_ORG_UNIT_UID,
            categoryOptionCombo = "categoryCombo2",
            attributeOptionCombo = TEST_ATTRIBUTE_COMBO,
            value = "200",
            storedBy = "testuser",
            created = "2024-01-01T00:00:00.000",
            lastUpdated = "2024-01-01T00:00:00.000"
        )

        // Act
        dataValueDao.insertDataValues(listOf(dataValue1, dataValue2))

        // Assert - should only return values matching the filter
        val filteredValues = dataValueDao.getDataValues(
            TEST_PERIOD, TEST_ORG_UNIT_UID, TEST_ATTRIBUTE_COMBO
        ).first()
        
        assertThat(filteredValues).hasSize(1)
        assertThat(filteredValues[0]).isEqualTo(dataValue1)
    }

    @Test
    fun clearAllDataValues() = runTest {
        // Arrange
        val dataValues = listOf(
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
            ),
            DataValueEntity(
                dataElement = "element2",
                period = TEST_PERIOD,
                organisationUnit = TEST_ORG_UNIT_UID,
                categoryOptionCombo = "categoryCombo2",
                attributeOptionCombo = TEST_ATTRIBUTE_COMBO,
                value = "200",
                storedBy = "testuser",
                created = "2024-01-01T00:00:00.000",
                lastUpdated = "2024-01-01T00:00:00.000"
            )
        )

        // Act
        dataValueDao.insertDataValues(dataValues)
        dataValueDao.clearAllDataValues()

        // Assert
        val remainingValues = dataValueDao.getDataValues(
            TEST_PERIOD, TEST_ORG_UNIT_UID, TEST_ATTRIBUTE_COMBO
        ).first()
        
        assertThat(remainingValues).isEmpty()
    }
}