package com.ash.simpledataentry.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for tracker programs (WITH_REGISTRATION)
 * Provides reactive Flow-based access to cached program metadata
 */
@Dao
interface TrackerProgramDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(programs: List<TrackerProgramEntity>)

    @Query("SELECT * FROM tracker_programs")
    fun getAll(): Flow<List<TrackerProgramEntity>>

    @Query("DELETE FROM tracker_programs")
    suspend fun clearAll()

    @Query("SELECT * FROM tracker_programs WHERE id = :programId")
    suspend fun getById(programId: String): TrackerProgramEntity?
}
