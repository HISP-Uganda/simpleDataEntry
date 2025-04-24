package com.example.simplede.domain.model


data class DataValue(
    val dataElement: String,
    val categoryOptionCombo: String,
    val value: String?,
    val comment: String?,
    val storedBy: String?,
    //val timestamp: Date,
    //val syncState: SyncState = SyncState.SYNCED,
    val validationState: ValidationState = ValidationState.VALID
)

enum class ValidationState {
    VALID,
    ERROR,
    WARNING
}

data class ValidationResult(
    val isValid: Boolean,
    val state: ValidationState,
    val message: String?
)

data class ValueHistory(
    val value: String,
    //val timestamp: Date,
    val storedBy: String,
    val comment: String?
)

enum class DataEntryType {
    TEXT,
    NUMBER,
    DATE,
    YES_NO,
    MULTIPLE_CHOICE,
    COORDINATES
}


