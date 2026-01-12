package com.ash.simpledataentry.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for event programs (WITHOUT_REGISTRATION)
 * Provides reactive Flow-based access to cached program metadata
 */
@Dao
interface EventProgramDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(programs: List<EventProgramEntity>)

    @Query("SELECT * FROM event_programs")
    fun getAll(): Flow<List<EventProgramEntity>>

    @Query("DELETE FROM event_programs")
    suspend fun clearAll()

    @Query("SELECT * FROM event_programs WHERE id = :programId")
    suspend fun getById(programId: String): EventProgramEntity?
}
