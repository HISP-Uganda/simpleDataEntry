package com.ash.simpledataentry.domain.model

import java.util.Date

/**
 * Unified program instance that can represent dataset instances, tracker enrollments, or events
 * This enables a single interface for displaying different program instance types in the UI
 */
sealed class ProgramInstance {
    abstract val id: String
    abstract val programId: String
    abstract val programName: String
    abstract val organisationUnit: OrganisationUnit
    abstract val lastUpdated: Date
    abstract val state: ProgramInstanceState
    abstract val programType: ProgramType
    abstract val syncStatus: SyncStatus

    /**
     * Dataset instance (existing aggregate data)
     */
    data class DatasetInstance(
        override val id: String,
        override val programId: String,
        override val programName: String,
        override val organisationUnit: OrganisationUnit,
        override val lastUpdated: Date,
        override val state: ProgramInstanceState,
        override val syncStatus: SyncStatus,
        val period: Period,
        val attributeOptionCombo: String,
        val originalDatasetInstance: com.ash.simpledataentry.domain.model.DatasetInstance
    ) : ProgramInstance() {
        override val programType: ProgramType = ProgramType.DATASET
    }

    /**
     * Tracker enrollment instance
     */
    data class TrackerEnrollment(
        override val id: String,
        override val programId: String,
        override val programName: String,
        override val organisationUnit: OrganisationUnit,
        override val lastUpdated: Date,
        override val state: ProgramInstanceState,
        override val syncStatus: SyncStatus,
        val trackedEntityInstance: String,
        val enrollmentDate: Date,
        val incidentDate: Date? = null,
        val followUp: Boolean = false,
        val completedDate: Date? = null,
        val attributes: List<TrackedEntityAttributeValue> = emptyList(),
        val events: List<Event> = emptyList()
    ) : ProgramInstance() {
        override val programType: ProgramType = ProgramType.TRACKER
    }

    /**
     * Event instance (without registration)
     */
    data class EventInstance(
        override val id: String,
        override val programId: String,
        override val programName: String,
        override val organisationUnit: OrganisationUnit,
        override val lastUpdated: Date,
        override val state: ProgramInstanceState,
        override val syncStatus: SyncStatus,
        val programStage: String,
        val eventDate: Date? = null,
        val dueDate: Date? = null,
        val completedDate: Date? = null,
        val coordinates: Coordinates? = null,
        val dataValues: List<TrackedEntityDataValue> = emptyList()
    ) : ProgramInstance() {
        override val programType: ProgramType = ProgramType.EVENT
    }
}

/**
 * Unified program instance state
 */
enum class ProgramInstanceState {
    ACTIVE,      // Active enrollment/event or open dataset
    COMPLETED,   // Completed enrollment/event or complete dataset
    CANCELLED,   // Cancelled enrollment
    OVERDUE,     // Overdue event
    SCHEDULED,   // Scheduled event
    SKIPPED,     // Skipped event
    APPROVED,    // Approved dataset
    LOCKED       // Locked dataset
}

/**
 * Extension functions for easy access to underlying objects
 */
fun ProgramInstance.asDatasetInstance(): com.ash.simpledataentry.domain.model.DatasetInstance? =
    (this as? ProgramInstance.DatasetInstance)?.originalDatasetInstance

fun ProgramInstance.asTrackerEnrollment(): Enrollment? = when (this) {
    is ProgramInstance.TrackerEnrollment -> Enrollment(
        id = this.id,
        trackedEntityInstance = this.trackedEntityInstance,
        program = this.programId,
        organisationUnit = this.organisationUnit.id,
        enrollmentDate = this.enrollmentDate,
        incidentDate = this.incidentDate,
        status = when (this.state) {
            ProgramInstanceState.ACTIVE -> EnrollmentStatus.ACTIVE
            ProgramInstanceState.COMPLETED -> EnrollmentStatus.COMPLETED
            ProgramInstanceState.CANCELLED -> EnrollmentStatus.CANCELLED
            else -> EnrollmentStatus.ACTIVE
        },
        followUp = this.followUp,
        completedDate = this.completedDate,
        lastUpdated = this.lastUpdated,
        events = this.events
    )
    else -> null
}

fun ProgramInstance.asEvent(): Event? = when (this) {
    is ProgramInstance.EventInstance -> Event(
        id = this.id,
        program = this.programId,
        programStage = this.programStage,
        organisationUnit = this.organisationUnit.id,
        eventDate = this.eventDate,
        dueDate = this.dueDate,
        completedDate = this.completedDate,
        coordinates = this.coordinates,
        status = when (this.state) {
            ProgramInstanceState.ACTIVE -> EventStatus.ACTIVE
            ProgramInstanceState.COMPLETED -> EventStatus.COMPLETED
            ProgramInstanceState.OVERDUE -> EventStatus.OVERDUE
            ProgramInstanceState.SCHEDULED -> EventStatus.SCHEDULE
            ProgramInstanceState.SKIPPED -> EventStatus.SKIPPED
            else -> EventStatus.ACTIVE
        },
        lastUpdated = this.lastUpdated,
        dataValues = this.dataValues
    )
    else -> null
}

/**
 * Get display title for the instance
 */
fun ProgramInstance.getDisplayTitle(): String = when (this) {
    is ProgramInstance.DatasetInstance -> "${programName} - ${period.id}"
    is ProgramInstance.TrackerEnrollment -> {
        val mainAttribute = attributes.firstOrNull()?.value ?: trackedEntityInstance
        "${programName} - $mainAttribute"
    }
    is ProgramInstance.EventInstance -> {
        val eventDate = eventDate?.let { android.text.format.DateFormat.getDateFormat(null).format(it) } ?: "No date"
        "${programName} - $eventDate"
    }
}

/**
 * Get display subtitle for the instance
 */
fun ProgramInstance.getDisplaySubtitle(): String = when (this) {
    is ProgramInstance.DatasetInstance -> organisationUnit.name
    is ProgramInstance.TrackerEnrollment -> {
        val enrollmentDate = android.text.format.DateFormat.getDateFormat(null).format(enrollmentDate)
        "Enrolled: $enrollmentDate • ${organisationUnit.name}"
    }
    is ProgramInstance.EventInstance -> {
        val statusText = when (state) {
            ProgramInstanceState.COMPLETED -> "Completed"
            ProgramInstanceState.OVERDUE -> "Overdue"
            ProgramInstanceState.SCHEDULED -> "Scheduled"
            else -> "Active"
        }
        "$statusText • ${organisationUnit.name}"
    }
}

/**
 * Check if this instance supports data entry
 */
fun ProgramInstance.supportsDataEntry(): Boolean = when (this) {
    is ProgramInstance.DatasetInstance -> state == ProgramInstanceState.ACTIVE
    is ProgramInstance.TrackerEnrollment -> state == ProgramInstanceState.ACTIVE
    is ProgramInstance.EventInstance -> state in listOf(
        ProgramInstanceState.ACTIVE,
        ProgramInstanceState.SCHEDULED,
        ProgramInstanceState.OVERDUE
    )
}

/**
 * Get status color for UI display
 */
fun ProgramInstance.getStatusColor(): androidx.compose.ui.graphics.Color {
    return when (state) {
        ProgramInstanceState.COMPLETED -> androidx.compose.ui.graphics.Color(0xFF4CAF50) // Green
        ProgramInstanceState.ACTIVE -> androidx.compose.ui.graphics.Color(0xFF2196F3) // Blue
        ProgramInstanceState.OVERDUE -> androidx.compose.ui.graphics.Color(0xFFF44336) // Red
        ProgramInstanceState.CANCELLED -> androidx.compose.ui.graphics.Color(0xFF9E9E9E) // Gray
        ProgramInstanceState.SCHEDULED -> androidx.compose.ui.graphics.Color(0xFFFF9800) // Orange
        ProgramInstanceState.SKIPPED -> androidx.compose.ui.graphics.Color(0xFF9E9E9E) // Gray
        ProgramInstanceState.APPROVED -> androidx.compose.ui.graphics.Color(0xFF4CAF50) // Green
        ProgramInstanceState.LOCKED -> androidx.compose.ui.graphics.Color(0xFF607D8B) // Blue Gray
    }
}