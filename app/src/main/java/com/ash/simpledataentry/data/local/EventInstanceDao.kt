package com.ash.simpledataentry.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for event instances (standalone or within enrollments)
 * Provides reactive Flow-based access to cached event data
 */
@Dao
interface EventInstanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<EventInstanceEntity>)

    @Query("SELECT * FROM event_instances WHERE programId = :programId AND deleted = 0")
    fun getByProgram(programId: String): Flow<List<EventInstanceEntity>>

    @Query("SELECT * FROM event_instances WHERE enrollmentId = :enrollmentId AND deleted = 0")
    suspend fun getByEnrollment(enrollmentId: String): List<EventInstanceEntity>

    @Query("DELETE FROM event_instances WHERE programId = :programId")
    suspend fun deleteByProgram(programId: String)

    @Query("DELETE FROM event_instances")
    suspend fun clearAll()

    @Query("SELECT * FROM event_instances WHERE id = :eventId")
    suspend fun getById(eventId: String): EventInstanceEntity?

    @Query("SELECT COUNT(*) FROM event_instances WHERE programId = :programId AND deleted = 0")
    suspend fun getCountByProgram(programId: String): Int
}
