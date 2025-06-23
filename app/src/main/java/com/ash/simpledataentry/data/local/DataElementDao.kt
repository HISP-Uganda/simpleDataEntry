package com.ash.simpledataentry.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DataElementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(dataElements: List<DataElementEntity>)

    @Query("SELECT * FROM data_elements")
    suspend fun getAll(): List<DataElementEntity>

    @Query("DELETE FROM data_elements")
    suspend fun clearAll()
} 