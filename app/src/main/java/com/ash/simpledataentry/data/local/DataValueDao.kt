package com.ash.simpledataentry.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DataValueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(values: List<DataValueEntity>)

    @Query("SELECT * FROM data_values WHERE datasetId = :datasetId AND period = :period AND orgUnit = :orgUnit AND attributeOptionCombo = :attributeOptionCombo")
    suspend fun getValuesForInstance(datasetId: String, period: String, orgUnit: String, attributeOptionCombo: String): List<DataValueEntity>

    @Query("DELETE FROM data_values WHERE datasetId = :datasetId AND period = :period AND orgUnit = :orgUnit AND attributeOptionCombo = :attributeOptionCombo")
    suspend fun deleteValuesForInstance(datasetId: String, period: String, orgUnit: String, attributeOptionCombo: String)
} 