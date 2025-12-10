package com.ash.simpledataentry.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for tracker programs (WITH_REGISTRATION)
 * Cached to enable reactive Flow-based updates
 */
@Entity(tableName = "tracker_programs")
data class TrackerProgramEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val trackedEntityType: String?,
    val categoryCombo: String?,
    val styleIcon: String? = null,
    val styleColor: String? = null,
    val enrollmentDateLabel: String? = null,
    val incidentDateLabel: String? = null,
    val displayIncidentDate: Boolean = false,
    val onlyEnrollOnce: Boolean = false,
    val selectEnrollmentDatesInFuture: Boolean = false,
    val selectIncidentDatesInFuture: Boolean = false,
    val featureType: String = "NONE",
    val minAttributesRequiredToSearch: Int = 1,
    val maxTeiCountToReturn: Int = 50
)
