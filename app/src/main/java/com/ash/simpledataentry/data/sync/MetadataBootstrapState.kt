package com.ash.simpledataentry.data.sync

data class MetadataBootstrapState(
    val phase: MetadataBootstrapPhase = MetadataBootstrapPhase.Idle,
    val activeAccountId: String? = null,
    val progress: Int? = null,
    val stage: String? = null,
    val readinessLevel: MetadataReadinessLevel = MetadataReadinessLevel.None,
    val message: String? = null,
    val diagnosticMessage: String? = null,
    val isReady: Boolean = false,
    val canRetry: Boolean = false,
    val lastCompletedAt: Long? = null
) {
    val isRunning: Boolean
        get() = phase == MetadataBootstrapPhase.Enqueued || phase == MetadataBootstrapPhase.Running

    val isListReady: Boolean
        get() = readinessLevel == MetadataReadinessLevel.ListReady || readinessLevel == MetadataReadinessLevel.FormReady

    val isFormReady: Boolean
        get() = isReady || readinessLevel == MetadataReadinessLevel.FormReady
}

enum class MetadataBootstrapPhase {
    Idle,
    Enqueued,
    Running,
    Succeeded,
    Failed,
    Cancelled
}

enum class MetadataReadinessLevel {
    None,
    ListReady,
    FormReady
}
