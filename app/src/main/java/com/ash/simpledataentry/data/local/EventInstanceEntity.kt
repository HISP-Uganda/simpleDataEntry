package com.ash.simpledataentry.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for event instances (standalone events from WITHOUT_REGISTRATION programs)
 * Cached to enable reactive Flow-based updates and eliminate blocking SDK calls
 */
@Entity(tableName = "event_instances")
data class EventInstanceEntity(
    @PrimaryKey val id: String,
    val programId: String,
    val programStageId: String,
    val organisationUnitId: String,
    val organisationUnitName: String,
    val eventDate: String?,
    val status: String,  // ACTIVE, COMPLETED, SCHEDULE, OVERDUE, SKIPPED
    val deleted: Boolean = false,
    val lastUpdated: String?,
    val enrollmentId: String? = null  // Null for standalone events
)
