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

    @Query("SELECT * FROM data_elements WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<DataElementEntity>

    @Query("SELECT COUNT(*) FROM data_elements")
    suspend fun count(): Int

    @Query("DELETE FROM data_elements")
    suspend fun clearAll()
} 
