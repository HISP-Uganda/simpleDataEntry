package com.ash.simpledataentry.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OrganisationUnitDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(orgUnits: List<OrganisationUnitEntity>)

    @Query("SELECT * FROM organisation_units")
    suspend fun getAll(): List<OrganisationUnitEntity>

    @Query("DELETE FROM organisation_units")
    suspend fun clearAll()
} 