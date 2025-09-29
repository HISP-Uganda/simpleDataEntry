package com.ash.simpledataentry.domain.model

import java.util.Date

/**
 * Tracked entity domain model representing an individual being tracked
 */
data class TrackedEntity(
    val id: String,
    val trackedEntityType: String,
    val organisationUnit: String,
    val coordinates: Coordinates? = null,
    val featureType: FeatureType = FeatureType.NONE,
    val created: Date = Date(),
    val lastUpdated: Date = Date(),
    val deleted: Boolean = false,
    val attributes: List<TrackedEntityAttributeValue> = emptyList(),
    val enrollments: List<Enrollment> = emptyList(),
    val relationships: List<Relationship> = emptyList()
)

/**
 * Tracked entity attribute definition
 */
data class TrackedEntityAttribute(
    val id: String,
    val displayName: String,
    val description: String? = null,
    val valueType: String = "TEXT",
    val mandatory: Boolean = false
)

/**
 * Tracked entity attribute value
 */
data class TrackedEntityAttributeValue(
    val id: String = "",
    val displayName: String = "",
    val trackedEntityAttribute: String = "",
    val trackedEntityInstance: String = "",
    val value: String? = null,
    val created: Date = Date(),
    val lastUpdated: Date = Date()
)

/**
 * Enrollment in a tracker program
 */
data class Enrollment(
    val id: String,
    val trackedEntityInstance: String,
    val program: String,
    val organisationUnit: String,
    val enrollmentDate: Date,
    val incidentDate: Date? = null,
    val coordinates: Coordinates? = null,
    val featureType: FeatureType = FeatureType.NONE,
    val status: EnrollmentStatus = EnrollmentStatus.ACTIVE,
    val followUp: Boolean = false,
    val completedDate: Date? = null,
    val created: Date = Date(),
    val lastUpdated: Date = Date(),
    val deleted: Boolean = false,
    val events: List<Event> = emptyList(),
    val notes: List<Note> = emptyList()
)

/**
 * Event in a program stage
 */
data class Event(
    val id: String,
    val programId: String = "",
    val programStageId: String = "",
    val programStageName: String? = null,
    val enrollmentId: String? = null, // Null for event programs
    val program: String = "",
    val programStage: String = "",
    val organisationUnitId: String = "",
    val organisationUnit: String = "",
    val eventDate: Date? = null,
    val dueDate: Date? = null,
    val completedDate: Date? = null,
    val coordinates: Coordinates? = null,
    val featureType: FeatureType = FeatureType.NONE,
    val status: String = "ACTIVE",
    val assignedUser: String? = null,
    val created: Date = Date(),
    val lastUpdated: Date? = null,
    val deleted: Boolean = false,
    val dataValues: List<TrackedEntityDataValue> = emptyList(),
    val notes: List<Note> = emptyList()
)

/**
 * Data value for tracked entity events
 */
data class TrackedEntityDataValue(
    val event: String,
    val dataElement: String,
    val value: String?,
    val providedElsewhere: Boolean = false,
    val created: Date = Date(),
    val lastUpdated: Date = Date()
)

/**
 * Relationship between tracked entities
 */
data class Relationship(
    val id: String,
    val relationshipType: String,
    val from: RelationshipItem,
    val to: RelationshipItem,
    val created: Date = Date(),
    val lastUpdated: Date = Date()
)

/**
 * Relationship item (can be tracked entity or enrollment)
 */
sealed class RelationshipItem {
    data class TrackedEntityItem(val trackedEntity: String) : RelationshipItem()
    data class EnrollmentItem(val enrollment: String) : RelationshipItem()
}

/**
 * Note/comment for enrollments and events
 */
data class Note(
    val id: String,
    val value: String,
    val noteType: NoteType = NoteType.ENROLLMENT,
    val created: Date = Date(),
    val lastUpdated: Date = Date(),
    val storedBy: String? = null
)

/**
 * Coordinates for geospatial data
 */
data class Coordinates(
    val latitude: Double,
    val longitude: Double
)

/**
 * Enrollment status enumeration
 */
enum class EnrollmentStatus {
    ACTIVE,
    COMPLETED,
    CANCELLED
}

/**
 * Event status enumeration
 */
enum class EventStatus {
    ACTIVE,
    COMPLETED,
    VISITED,
    SCHEDULE,
    OVERDUE,
    SKIPPED
}

/**
 * Note type enumeration
 */
enum class NoteType {
    ENROLLMENT,
    EVENT
}

/**
 * Extension functions for common operations
 */
fun TrackedEntity.getAttributeValue(attributeId: String): String? {
    return attributes.find { it.trackedEntityAttribute == attributeId }?.value
}

fun TrackedEntity.getActiveEnrollments(): List<Enrollment> {
    return enrollments.filter { it.status == EnrollmentStatus.ACTIVE }
}

fun Enrollment.getEvents(programStageId: String? = null): List<Event> {
    return if (programStageId != null) {
        events.filter { it.programStage == programStageId }
    } else {
        events
    }
}

fun Enrollment.getLatestEvent(): Event? {
    return events.maxByOrNull { it.lastUpdated ?: Date(0) }
}

fun Event.getDataValue(dataElementId: String): String? {
    return dataValues.find { it.dataElement == dataElementId }?.value
}

fun Event.isOverdue(): Boolean {
    return status == "OVERDUE" ||
           (dueDate != null && dueDate.before(Date()) && status != "COMPLETED")
}

fun Event.isCompleted(): Boolean {
    return status == "COMPLETED" || completedDate != null
}