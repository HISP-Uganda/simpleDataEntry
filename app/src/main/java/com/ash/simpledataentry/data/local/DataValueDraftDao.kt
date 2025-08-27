package com.ash.simpledataentry.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DataValueDraftDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDraft(draft: DataValueDraftEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(drafts: List<DataValueDraftEntity>)

    @Query("SELECT * FROM data_value_drafts WHERE datasetId = :datasetId AND period = :period AND orgUnit = :orgUnit AND attributeOptionCombo = :attributeOptionCombo")
    suspend fun getDraftsForInstance(datasetId: String, period: String, orgUnit: String, attributeOptionCombo: String): List<DataValueDraftEntity>

    @Query("DELETE FROM data_value_drafts WHERE datasetId = :datasetId AND period = :period AND orgUnit = :orgUnit AND attributeOptionCombo = :attributeOptionCombo")
    suspend fun deleteDraftsForInstance(datasetId: String, period: String, orgUnit: String, attributeOptionCombo: String)

    @Delete
    suspend fun deleteDraft(draft: DataValueDraftEntity)

    @Query("DELETE FROM data_value_drafts WHERE datasetId = :datasetId AND period = :period AND orgUnit = :orgUnit AND attributeOptionCombo = :attributeOptionCombo AND dataElement = :dataElement AND categoryOptionCombo = :categoryOptionCombo")
    suspend fun deleteDraft(datasetId: String, period: String, orgUnit: String, attributeOptionCombo: String, dataElement: String, categoryOptionCombo: String)

    @Query("SELECT * FROM data_value_drafts")
    suspend fun getAllDrafts(): List<DataValueDraftEntity>
    
    @Query("""
        SELECT DISTINCT datasetId, period, orgUnit, attributeOptionCombo, 
               MAX(lastModified) as lastModified
        FROM data_value_drafts 
        WHERE datasetId = :datasetId
        GROUP BY datasetId, period, orgUnit, attributeOptionCombo
    """)
    suspend fun getDistinctDraftInstances(datasetId: String): List<DraftInstanceSummary>
    
    @Query("SELECT COUNT(*) FROM data_value_drafts WHERE datasetId = :datasetId AND period = :period AND orgUnit = :orgUnit AND attributeOptionCombo = :attributeOptionCombo")
    suspend fun getDraftCountForInstance(datasetId: String, period: String, orgUnit: String, attributeOptionCombo: String): Int
}