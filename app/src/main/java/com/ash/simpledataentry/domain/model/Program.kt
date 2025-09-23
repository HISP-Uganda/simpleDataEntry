package com.ash.simpledataentry.domain.model

import org.hisp.dhis.android.core.period.PeriodType
import java.util.Date

/**
 * DHIS2 Program domain model for tracker and event programs
 */
data class Program(
    val id: String,
    val name: String,
    val description: String?,
    val programType: ProgramType,
    val trackedEntityType: String? = null, // Only for tracker programs
    val categoryCombo: String? = null,
    val access: ProgramAccess = ProgramAccess(),
    val style: ProgramStyle? = null,
    val enrollmentDateLabel: String? = null,
    val incidentDateLabel: String? = null,
    val displayIncidentDate: Boolean = false,
    val onlyEnrollOnce: Boolean = false,
    val selectEnrollmentDatesInFuture: Boolean = false,
    val selectIncidentDatesInFuture: Boolean = false,
    val programStages: List<ProgramStage> = emptyList(),
    val programTrackedEntityAttributes: List<ProgramTrackedEntityAttribute> = emptyList(),
    val organisationUnits: List<String> = emptyList(),
    val featureType: FeatureType = FeatureType.NONE,
    val minAttributesRequiredToSearch: Int = 1,
    val maxTeiCountToReturn: Int = 50,
    val enrollmentCount: Int = 0 // Number of enrollments for this program
)

/**
 * Program stage for multi-stage tracker programs
 */
data class ProgramStage(
    val id: String,
    val name: String,
    val description: String?,
    val programId: String,
    val repeatable: Boolean = false,
    val sortOrder: Int = 0,
    val minDaysFromStart: Int = 0,
    val standardInterval: Int? = null,
    val executionDateLabel: String? = null,
    val dueDateLabel: String? = null,
    val allowGenerateNextVisit: Boolean = false,
    val validCompleteOnly: Boolean = false,
    val reportDateToUse: String? = null,
    val openAfterEnrollment: Boolean = false,
    val generatedByEnrollmentDate: Boolean = false,
    val autoGenerateEvent: Boolean = false,
    val displayGenerateEventBox: Boolean = false,
    val blockEntryForm: Boolean = false,
    val hideDueDate: Boolean = false,
    val enableUserAssignment: Boolean = false,
    val style: ProgramStageStyle? = null,
    val featureType: FeatureType = FeatureType.NONE,
    val programStageDataElements: List<ProgramStageDataElement> = emptyList(),
    val programStageSections: List<ProgramStageSection> = emptyList()
)

/**
 * Program stage data element configuration
 */
data class ProgramStageDataElement(
    val id: String,
    val dataElement: String,
    val programStage: String,
    val compulsory: Boolean = false,
    val allowProvidedElsewhere: Boolean = false,
    val sortOrder: Int = 0,
    val displayInReports: Boolean = true,
    val allowFutureDate: Boolean = false,
    val skipSynchronization: Boolean = false
)

/**
 * Program stage section for organizing data elements
 */
data class ProgramStageSection(
    val id: String,
    val name: String,
    val description: String?,
    val programStage: String,
    val sortOrder: Int = 0,
    val dataElements: List<String> = emptyList()
)

/**
 * Program tracked entity attribute configuration
 */
data class ProgramTrackedEntityAttribute(
    val id: String,
    val trackedEntityAttribute: String,
    val program: String,
    val mandatory: Boolean = false,
    val allowFutureDate: Boolean = false,
    val displayInList: Boolean = false,
    val searchable: Boolean = false,
    val sortOrder: Int = 0,
    val valueType: ValueType,
    val optionSet: String? = null
)

/**
 * Program type enumeration
 */
enum class ProgramType {
    DATASET,           // Aggregate dataset (existing)
    TRACKER,          // Tracker program (with registration)
    EVENT,            // Event program (without registration)
    ALL               // Filter option for showing all types
}

/**
 * Feature type for geospatial data
 */
enum class FeatureType {
    NONE,
    POINT,
    POLYGON,
    MULTI_POLYGON
}

/**
 * Program access configuration
 */
data class ProgramAccess(
    val read: Boolean = true,
    val write: Boolean = true,
    val dataRead: Boolean = true,
    val dataWrite: Boolean = true
)

/**
 * Program visual styling configuration
 */
data class ProgramStyle(
    val icon: String? = null,     // DHIS2 icon name
    val color: String? = null     // Hex color code
)

/**
 * Program stage visual styling configuration
 */
data class ProgramStageStyle(
    val icon: String? = null,     // DHIS2 icon name
    val color: String? = null     // Hex color code
)