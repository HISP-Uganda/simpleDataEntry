package com.ash.simpledataentry.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ash.simpledataentry.testutil.TestConstants.TEST_ATTRIBUTE_COMBO
import com.ash.simpledataentry.testutil.TestConstants.TEST_DATASET_UID
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
class DataValueDraftDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var draftDao: DataValueDraftDao

    @Before
    fun createDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        draftDao = database.dataValueDraftDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    @Test
    fun insertAndGetDraft() = runTest {
        // Arrange
        val draft = DataValueDraftEntity(
            datasetUid = TEST_DATASET_UID,
            period = TEST_PERIOD,
            organisationUnitUid = TEST_ORG_UNIT_UID,
            attributeOptionComboUid = TEST_ATTRIBUTE_COMBO,
            dataElementUid = "element1",
            categoryOptionComboUid = "categoryCombo1",
            value = "draft_value_100",
            comment = "Test comment",
            lastModified = System.currentTimeMillis()
        )

        // Act
        draftDao.insertOrUpdateDraft(draft)

        // Assert
        val drafts = draftDao.getDrafts(
            TEST_DATASET_UID, TEST_PERIOD, TEST_ORG_UNIT_UID, TEST_ATTRIBUTE_COMBO
        ).first()
        
        assertThat(drafts).hasSize(1)
        assertThat(drafts[0]).isEqualTo(draft)
    }

    @Test
    fun updateExistingDraft() = runTest {
        // Arrange
        val originalDraft = DataValueDraftEntity(
            datasetUid = TEST_DATASET_UID,
            period = TEST_PERIOD,
            organisationUnitUid = TEST_ORG_UNIT_UID,
            attributeOptionComboUid = TEST_ATTRIBUTE_COMBO,
            dataElementUid = "element1",
            categoryOptionComboUid = "categoryCombo1",
            value = "original_value",
            comment = null,
            lastModified = 1000L
        )

        // Act
        draftDao.insertOrUpdateDraft(originalDraft)
        
        val updatedDraft = originalDraft.copy(
            value = "updated_value",
            comment = "Updated comment",
            lastModified = 2000L
        )
        draftDao.insertOrUpdateDraft(updatedDraft)

        // Assert
        val drafts = draftDao.getDrafts(
            TEST_DATASET_UID, TEST_PERIOD, TEST_ORG_UNIT_UID, TEST_ATTRIBUTE_COMBO
        ).first()
        
        assertThat(drafts).hasSize(1)
        assertThat(drafts[0].value).isEqualTo("updated_value")
        assertThat(drafts[0].comment).isEqualTo("Updated comment")
        assertThat(drafts[0].lastModified).isEqualTo(2000L)
    }

    @Test
    fun getDraftsFiltersByInstance() = runTest {
        // Arrange
        val draft1 = DataValueDraftEntity(
            datasetUid = TEST_DATASET_UID,
            period = TEST_PERIOD,
            organisationUnitUid = TEST_ORG_UNIT_UID,
            attributeOptionComboUid = TEST_ATTRIBUTE_COMBO,
            dataElementUid = "element1",
            categoryOptionComboUid = "categoryCombo1",
            value = "value1",
            comment = null,
            lastModified = 1000L
        )
        
        val draft2 = DataValueDraftEntity(
            datasetUid = "different_dataset",
            period = TEST_PERIOD,
            organisationUnitUid = TEST_ORG_UNIT_UID,
            attributeOptionComboUid = TEST_ATTRIBUTE_COMBO,
            dataElementUid = "element2",
            categoryOptionComboUid = "categoryCombo2",
            value = "value2",
            comment = null,
            lastModified = 1000L
        )

        // Act
        draftDao.insertOrUpdateDraft(draft1)
        draftDao.insertOrUpdateDraft(draft2)

        // Assert - should only return drafts for the specified dataset instance
        val drafts = draftDao.getDrafts(
            TEST_DATASET_UID, TEST_PERIOD, TEST_ORG_UNIT_UID, TEST_ATTRIBUTE_COMBO
        ).first()
        
        assertThat(drafts).hasSize(1)
        assertThat(drafts[0]).isEqualTo(draft1)
    }

    @Test
    fun deleteDraft() = runTest {
        // Arrange
        val draft = DataValueDraftEntity(
            datasetUid = TEST_DATASET_UID,
            period = TEST_PERIOD,
            organisationUnitUid = TEST_ORG_UNIT_UID,
            attributeOptionComboUid = TEST_ATTRIBUTE_COMBO,
            dataElementUid = "element1",
            categoryOptionComboUid = "categoryCombo1",
            value = "value_to_delete",
            comment = null,
            lastModified = 1000L
        )

        // Act
        draftDao.insertOrUpdateDraft(draft)
        draftDao.deleteDraft(
            TEST_DATASET_UID, TEST_PERIOD, TEST_ORG_UNIT_UID, TEST_ATTRIBUTE_COMBO,
            "element1", "categoryCombo1"
        )

        // Assert
        val drafts = draftDao.getDrafts(
            TEST_DATASET_UID, TEST_PERIOD, TEST_ORG_UNIT_UID, TEST_ATTRIBUTE_COMBO
        ).first()
        
        assertThat(drafts).isEmpty()
    }

    @Test
    fun deleteAllDraftsForInstance() = runTest {
        // Arrange
        val drafts = listOf(
            DataValueDraftEntity(
                datasetUid = TEST_DATASET_UID,
                period = TEST_PERIOD,
                organisationUnitUid = TEST_ORG_UNIT_UID,
                attributeOptionComboUid = TEST_ATTRIBUTE_COMBO,
                dataElementUid = "element1",
                categoryOptionComboUid = "categoryCombo1",
                value = "value1",
                comment = null,
                lastModified = 1000L
            ),
            DataValueDraftEntity(
                datasetUid = TEST_DATASET_UID,
                period = TEST_PERIOD,
                organisationUnitUid = TEST_ORG_UNIT_UID,
                attributeOptionComboUid = TEST_ATTRIBUTE_COMBO,
                dataElementUid = "element2",
                categoryOptionComboUid = "categoryCombo2",
                value = "value2",
                comment = null,
                lastModified = 1000L
            )
        )

        // Act
        drafts.forEach { draftDao.insertOrUpdateDraft(it) }
        draftDao.deleteAllDraftsForInstance(
            TEST_DATASET_UID, TEST_PERIOD, TEST_ORG_UNIT_UID, TEST_ATTRIBUTE_COMBO
        )

        // Assert
        val remainingDrafts = draftDao.getDrafts(
            TEST_DATASET_UID, TEST_PERIOD, TEST_ORG_UNIT_UID, TEST_ATTRIBUTE_COMBO
        ).first()
        
        assertThat(remainingDrafts).isEmpty()
    }

    @Test
    fun getDraftInstanceSummaries() = runTest {
        // Arrange
        val drafts = listOf(
            DataValueDraftEntity(
                datasetUid = TEST_DATASET_UID,
                period = TEST_PERIOD,
                organisationUnitUid = TEST_ORG_UNIT_UID,
                attributeOptionComboUid = TEST_ATTRIBUTE_COMBO,
                dataElementUid = "element1",
                categoryOptionComboUid = "categoryCombo1",
                value = "value1",
                comment = null,
                lastModified = 1000L
            ),
            DataValueDraftEntity(
                datasetUid = TEST_DATASET_UID,
                period = "202402",
                organisationUnitUid = TEST_ORG_UNIT_UID,
                attributeOptionComboUid = TEST_ATTRIBUTE_COMBO,
                dataElementUid = "element2",
                categoryOptionComboUid = "categoryCombo2",
                value = "value2",
                comment = null,
                lastModified = 2000L
            )
        )

        // Act
        drafts.forEach { draftDao.insertOrUpdateDraft(it) }

        // Assert
        val summaries = draftDao.getDraftInstanceSummaries().first()
        
        assertThat(summaries).hasSize(2)
        
        val summary1 = summaries.find { it.period == TEST_PERIOD }
        assertThat(summary1).isNotNull()
        assertThat(summary1!!.datasetUid).isEqualTo(TEST_DATASET_UID)
        assertThat(summary1.draftCount).isEqualTo(1)
        assertThat(summary1.lastModified).isEqualTo(1000L)
        
        val summary2 = summaries.find { it.period == "202402" }
        assertThat(summary2).isNotNull()
        assertThat(summary2!!.draftCount).isEqualTo(1)
        assertThat(summary2.lastModified).isEqualTo(2000L)
    }

    @Test
    fun clearAllDrafts() = runTest {
        // Arrange
        val drafts = listOf(
            DataValueDraftEntity(
                datasetUid = TEST_DATASET_UID,
                period = TEST_PERIOD,
                organisationUnitUid = TEST_ORG_UNIT_UID,
                attributeOptionComboUid = TEST_ATTRIBUTE_COMBO,
                dataElementUid = "element1",
                categoryOptionComboUid = "categoryCombo1",
                value = "value1",
                comment = null,
                lastModified = 1000L
            ),
            DataValueDraftEntity(
                datasetUid = "dataset2",
                period = "202402",
                organisationUnitUid = "orgunit2",
                attributeOptionComboUid = "combo2",
                dataElementUid = "element2",
                categoryOptionComboUid = "categoryCombo2",
                value = "value2",
                comment = null,
                lastModified = 2000L
            )
        )

        // Act
        drafts.forEach { draftDao.insertOrUpdateDraft(it) }
        draftDao.clearAllDrafts()

        // Assert
        val allSummaries = draftDao.getDraftInstanceSummaries().first()
        assertThat(allSummaries).isEmpty()
    }
}