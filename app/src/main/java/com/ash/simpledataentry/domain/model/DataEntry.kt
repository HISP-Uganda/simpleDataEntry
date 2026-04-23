package com.ash.simpledataentry.domain.model

enum class ValidationState {
    VALID,
    ERROR,
    WARNING
}

data class DataValueValidationResult(
    val isValid: Boolean,
    val state: ValidationState,
    val message: String?
)

data class ValueHistory(
    val value: String,
    val timestamp: Long = System.currentTimeMillis(),
    val storedBy: String,
    val comment: String?
)

enum class DataEntryType {
    TEXT,
    NUMBER,
    DATE,
    YES_NO,
    YES_ONLY,
    MULTIPLE_CHOICE,
    COORDINATES,
    PERCENTAGE,
    INTEGER,
    POSITIVE_INTEGER,
    NEGATIVE_INTEGER,
    POSITIVE_NUMBER,
    NEGATIVE_NUMBER,
    PHONE_NUMBER
}

sealed class DataEntryValidation {
    data class Required(val message: String = "This field is required") : DataEntryValidation()
    data class MinValue(val value: Double, val message: String) : DataEntryValidation()
    data class MaxValue(val value: Double, val message: String) : DataEntryValidation()
    data class Pattern(val regex: String, val message: String) : DataEntryValidation()
    data class Custom(val validator: (String?) -> DataValueValidationResult) : DataEntryValidation()
}

fun DataEntryType.getDefaultValidation(): Array<DataEntryValidation> {
    return when (this) {
        DataEntryType.NUMBER -> arrayOf(
            DataEntryValidation.Pattern("^-?\\d*\\.?\\d*$", "Please enter a valid number")
        )
        DataEntryType.INTEGER -> arrayOf(
            DataEntryValidation.Pattern("^-?\\d+$", "Please enter a valid integer")
        )
        DataEntryType.POSITIVE_INTEGER -> arrayOf(
            DataEntryValidation.Pattern("^\\d+$", "Please enter a positive integer")
        )
        DataEntryType.NEGATIVE_INTEGER -> arrayOf(
            DataEntryValidation.Pattern("^-\\d+$", "Please enter a negative integer")
        )
        DataEntryType.POSITIVE_NUMBER -> arrayOf(
            DataEntryValidation.Pattern("^\\d*\\.?\\d*$", "Please enter a positive number")
        )
        DataEntryType.NEGATIVE_NUMBER -> arrayOf(
            DataEntryValidation.Pattern("^-\\d*\\.?\\d*$", "Please enter a negative number")
        )
        DataEntryType.PERCENTAGE -> arrayOf(
            DataEntryValidation.Pattern("^\\d*\\.?\\d*$", "Please enter a valid percentage"),
            DataEntryValidation.MaxValue(100.0, "Percentage cannot exceed 100%")
        )
        DataEntryType.DATE -> arrayOf(
            DataEntryValidation.Pattern("^\\d{4}-\\d{2}-\\d{2}$", "Use date format YYYY-MM-DD")
        )
        DataEntryType.PHONE_NUMBER -> arrayOf(
            DataEntryValidation.Pattern("^\\+?[0-9]{6,15}$", "Please enter a valid phone number")
        )
        else -> emptyArray()
    }
}
