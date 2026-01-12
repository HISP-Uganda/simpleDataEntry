package com.ash.simpledataentry.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DatasetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(datasets: List<DatasetEntity>)

    @Query("SELECT * FROM datasets")
    fun getAll(): Flow<List<DatasetEntity>>

    @Query("DELETE FROM datasets")
    suspend fun clearAll()
} 