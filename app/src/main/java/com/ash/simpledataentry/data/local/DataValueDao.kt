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
    
    @Query("DELETE FROM data_values")
    suspend fun deleteAllDataValues()

    @Query("SELECT * FROM data_values WHERE datasetId = :datasetId")
    suspend fun getValuesForDataset(datasetId: String): List<DataValueEntity>

    @Query("""
        SELECT COUNT(*) FROM (
            SELECT DISTINCT period, orgUnit, attributeOptionCombo
            FROM data_values
            WHERE datasetId = :datasetId
        )
    """)
    suspend fun countDistinctInstances(datasetId: String): Int

    @Query("""
        SELECT COUNT(*) FROM (
            SELECT DISTINCT period, orgUnit, attributeOptionCombo
            FROM data_values
            WHERE datasetId = :datasetId AND orgUnit IN (:orgUnitIds)
        )
    """)
    suspend fun countDistinctInstancesForOrgUnits(datasetId: String, orgUnitIds: List<String>): Int

    @Query("""
        SELECT COUNT(*) FROM (
            SELECT DISTINCT period, orgUnit, attributeOptionCombo
            FROM data_values
            WHERE datasetId = :datasetId
            UNION
            SELECT DISTINCT period, orgUnit, attributeOptionCombo
            FROM data_value_drafts
            WHERE datasetId = :datasetId
        )
    """)
    suspend fun countDistinctInstancesIncludingDrafts(datasetId: String): Int

    @Query("""
        SELECT COUNT(*) FROM (
            SELECT DISTINCT period, orgUnit, attributeOptionCombo
            FROM data_values
            WHERE datasetId = :datasetId AND orgUnit IN (:orgUnitIds)
            UNION
            SELECT DISTINCT period, orgUnit, attributeOptionCombo
            FROM data_value_drafts
            WHERE datasetId = :datasetId AND orgUnit IN (:orgUnitIds)
        )
    """)
    suspend fun countDistinctInstancesIncludingDraftsForOrgUnits(datasetId: String, orgUnitIds: List<String>): Int
}
