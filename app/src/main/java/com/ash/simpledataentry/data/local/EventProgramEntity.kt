package com.ash.simpledataentry.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for event programs (WITHOUT_REGISTRATION)
 * Cached to enable reactive Flow-based updates
 */
@Entity(tableName = "event_programs")
data class EventProgramEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val categoryCombo: String?,
    val styleIcon: String? = null,
    val styleColor: String? = null,
    val featureType: String = "NONE"
)
