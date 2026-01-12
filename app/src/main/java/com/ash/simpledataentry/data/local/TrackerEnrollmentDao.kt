package com.ash.simpledataentry.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for tracker enrollments
 * Provides reactive Flow-based access to cached enrollment data
 */
@Dao
interface TrackerEnrollmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(enrollments: List<TrackerEnrollmentEntity>)

    @Query("SELECT * FROM tracker_enrollments WHERE programId = :programId AND deleted = 0")
    fun getByProgram(programId: String): Flow<List<TrackerEnrollmentEntity>>

    @Query("DELETE FROM tracker_enrollments WHERE programId = :programId")
    suspend fun deleteByProgram(programId: String)

    @Query("DELETE FROM tracker_enrollments")
    suspend fun clearAll()

    @Query("SELECT * FROM tracker_enrollments WHERE id = :enrollmentId")
    suspend fun getById(enrollmentId: String): TrackerEnrollmentEntity?

    @Query("SELECT COUNT(*) FROM tracker_enrollments WHERE programId = :programId AND deleted = 0")
    suspend fun getCountByProgram(programId: String): Int
}
