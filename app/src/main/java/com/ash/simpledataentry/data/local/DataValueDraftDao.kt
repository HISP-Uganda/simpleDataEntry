package com.ash.simpledataentry.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete

@Dao
interface DataValueDraftDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDraft(draft: DataValueDraftEntity)

    @Query("SELECT * FROM data_value_drafts WHERE datasetId = :datasetId AND period = :period AND orgUnit = :orgUnit AND attributeOptionCombo = :attributeOptionCombo")
    suspend fun getDraftsForInstance(datasetId: String, period: String, orgUnit: String, attributeOptionCombo: String): List<DataValueDraftEntity>

    @Query("DELETE FROM data_value_drafts WHERE datasetId = :datasetId AND period = :period AND orgUnit = :orgUnit AND attributeOptionCombo = :attributeOptionCombo")
    suspend fun deleteDraftsForInstance(datasetId: String, period: String, orgUnit: String, attributeOptionCombo: String)

    @Delete
    suspend fun deleteDraft(draft: DataValueDraftEntity)

    @Query("SELECT * FROM data_value_drafts")
    suspend fun getAllDrafts(): List<DataValueDraftEntity>
} 