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
 * Tracked entity attribute value
 */
data class TrackedEntityAttributeValue(
    val trackedEntityAttribute: String,
    val trackedEntityInstance: String,
    val value: String?,
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
    val enrollment: String? = null, // Null for event programs
    val program: String,
    val programStage: String,
    val organisationUnit: String,
    val eventDate: Date? = null,
    val dueDate: Date? = null,
    val completedDate: Date? = null,
    val coordinates: Coordinates? = null,
    val featureType: FeatureType = FeatureType.NONE,
    val status: EventStatus = EventStatus.ACTIVE,
    val assignedUser: String? = null,
    val created: Date = Date(),
    val lastUpdated: Date = Date(),
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
    return events.maxByOrNull { it.lastUpdated }
}

fun Event.getDataValue(dataElementId: String): String? {
    return dataValues.find { it.dataElement == dataElementId }?.value
}

fun Event.isOverdue(): Boolean {
    return status == EventStatus.OVERDUE ||
           (dueDate != null && dueDate.before(Date()) && status != EventStatus.COMPLETED)
}

fun Event.isCompleted(): Boolean {
    return status == EventStatus.COMPLETED || completedDate != null
}