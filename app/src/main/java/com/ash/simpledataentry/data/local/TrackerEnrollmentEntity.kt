package com.ash.simpledataentry.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for tracker enrollments (instances of WITH_REGISTRATION programs)
 * Cached to enable reactive Flow-based updates and eliminate blocking SDK calls
 */
@Entity(tableName = "tracker_enrollments")
data class TrackerEnrollmentEntity(
    @PrimaryKey val id: String,
    val programId: String,
    val trackedEntityInstanceId: String,
    val organisationUnitId: String,
    val organisationUnitName: String,
    val enrollmentDate: String?,
    val incidentDate: String?,
    val status: String,  // ACTIVE, COMPLETED, CANCELLED
    val followUp: Boolean = false,
    val deleted: Boolean = false,
    val lastUpdated: String?
)
