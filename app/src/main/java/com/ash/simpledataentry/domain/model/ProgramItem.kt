package com.ash.simpledataentry.domain.model

/**
 * Unified program item that can represent datasets, tracker programs, or event programs
 * This enables a single interface for displaying different program types in the UI
 */
sealed class ProgramItem {
    abstract val id: String
    abstract val name: String
    abstract val description: String?
    abstract val programType: ProgramType
    abstract val style: Any? // Can be DatasetStyle or ProgramStyle
    abstract val instanceCount: Int

    /**
     * Dataset program item (existing aggregate data)
     */
    data class DatasetProgram(
        val dataset: Dataset
    ) : ProgramItem() {
        override val id: String = dataset.id
        override val name: String = dataset.name
        override val description: String? = dataset.description
        override val programType: ProgramType = ProgramType.DATASET
        override val style: DatasetStyle? = dataset.style
        override val instanceCount: Int = dataset.instanceCount
    }

    /**
     * Tracker program item (individual-level tracking)
     */
    data class TrackerProgram(
        val program: Program
    ) : ProgramItem() {
        override val id: String = program.id
        override val name: String = program.name
        override val description: String? = program.description
        override val programType: ProgramType = ProgramType.TRACKER
        override val style: ProgramStyle? = program.style
        override val instanceCount: Int = program.enrollmentCount
    }

    /**
     * Event program item (events without registration)
     */
    data class EventProgram(
        val program: Program
    ) : ProgramItem() {
        override val id: String = program.id
        override val name: String = program.name
        override val description: String? = program.description
        override val programType: ProgramType = ProgramType.EVENT
        override val style: ProgramStyle? = program.style
        override val instanceCount: Int = program.enrollmentCount // For event programs, this represents event count
    }
}

/**
 * Extension functions for easy access to underlying objects
 */
fun ProgramItem.asDataset(): Dataset? = (this as? ProgramItem.DatasetProgram)?.dataset
fun ProgramItem.asProgram(): Program? = when (this) {
    is ProgramItem.TrackerProgram -> this.program
    is ProgramItem.EventProgram -> this.program
    is ProgramItem.DatasetProgram -> null
}

/**
 * Get the icon name for display
 */
fun ProgramItem.getIconName(): String? = when (this) {
    is ProgramItem.DatasetProgram -> this.style?.icon
    is ProgramItem.TrackerProgram -> this.style?.icon
    is ProgramItem.EventProgram -> this.style?.icon
}

/**
 * Get the color for display
 */
fun ProgramItem.getColor(): String? = when (this) {
    is ProgramItem.DatasetProgram -> this.style?.color
    is ProgramItem.TrackerProgram -> this.style?.color
    is ProgramItem.EventProgram -> this.style?.color
}

/**
 * Get default icon based on program type
 */
fun ProgramItem.getDefaultIcon(): String = when (programType) {
    ProgramType.DATASET -> "dataset"
    ProgramType.TRACKER -> "person"
    ProgramType.EVENT -> "event"
    ProgramType.ALL -> "all_programs"
}

/**
 * Check if this program item supports certain features
 */
fun ProgramItem.supportsEnrollment(): Boolean = when (this) {
    is ProgramItem.TrackerProgram -> true
    is ProgramItem.EventProgram -> false
    is ProgramItem.DatasetProgram -> false
}

fun ProgramItem.supportsMultipleStages(): Boolean = when (this) {
    is ProgramItem.TrackerProgram -> this.program.programStages.size > 1
    is ProgramItem.EventProgram -> this.program.programStages.size > 1
    is ProgramItem.DatasetProgram -> false
}

fun ProgramItem.requiresRegistration(): Boolean = when (this) {
    is ProgramItem.TrackerProgram -> true
    is ProgramItem.EventProgram -> false
    is ProgramItem.DatasetProgram -> false
}