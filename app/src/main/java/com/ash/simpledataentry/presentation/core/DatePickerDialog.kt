package com.ash.simpledataentry.presentation.core

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.*

/**
 * Material 3 Date Picker Dialog Component
 * Provides consistent date selection across the app
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialog(
    onDateSelected: (Date) -> Unit,
    onDismissRequest: () -> Unit,
    initialDate: Date = Date(),
    title: String = "Select Date",
    minDate: Date? = null,
    maxDate: Date? = null,
    modifier: Modifier = Modifier
) {
    // Convert Date to milliseconds for DatePicker
    val initialDateMillis = initialDate.time
    val minDateMillis = minDate?.time
    val maxDateMillis = maxDate?.time

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDateMillis,
        yearRange = (1900..2100),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return when {
                    minDateMillis != null && utcTimeMillis < minDateMillis -> false
                    maxDateMillis != null && utcTimeMillis > maxDateMillis -> false
                    else -> true
                }
            }
        }
    )

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                DatePicker(
                    state = datePickerState,
                    modifier = Modifier.fillMaxWidth(),
                    showModeToggle = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismissRequest
                    ) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    FilledTonalButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                onDateSelected(Date(millis))
                            }
                        },
                        enabled = datePickerState.selectedDateMillis != null
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

/**
 * Compact Date Picker for smaller dialogs
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactDatePickerDialog(
    onDateSelected: (Date) -> Unit,
    onDismissRequest: () -> Unit,
    initialDate: Date = Date(),
    title: String = "Select Date",
    minDate: Date? = null,
    maxDate: Date? = null
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.time,
        initialDisplayMode = DisplayMode.Input
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = {
            DatePicker(
                state = datePickerState,
                showModeToggle = false
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onDateSelected(Date(millis))
                    }
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Date Range Picker Dialog for period selection
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    onDateRangeSelected: (startDate: Date?, endDate: Date?) -> Unit,
    onDismissRequest: () -> Unit,
    initialStartDate: Date? = null,
    initialEndDate: Date? = null,
    title: String = "Select Date Range",
    modifier: Modifier = Modifier
) {
    val dateRangePickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStartDate?.time,
        initialSelectedEndDateMillis = initialEndDate?.time
    )

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                DateRangePicker(
                    state = dateRangePickerState,
                    modifier = Modifier.fillMaxWidth(),
                    showModeToggle = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismissRequest
                    ) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    FilledTonalButton(
                        onClick = {
                            val startDate = dateRangePickerState.selectedStartDateMillis?.let { Date(it) }
                            val endDate = dateRangePickerState.selectedEndDateMillis?.let { Date(it) }
                            onDateRangeSelected(startDate, endDate)
                        }
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

/**
 * Utility functions for date validation
 */
object DatePickerUtils {

    fun formatDateForDisplay(date: Date, pattern: String = "dd/MM/yyyy"): String {
        return SimpleDateFormat(pattern, Locale.getDefault()).format(date)
    }

    fun isDateInFuture(date: Date, allowToday: Boolean = true): Boolean {
        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        val compareDate = Calendar.getInstance()
        compareDate.time = date
        compareDate.set(Calendar.HOUR_OF_DAY, 0)
        compareDate.set(Calendar.MINUTE, 0)
        compareDate.set(Calendar.SECOND, 0)
        compareDate.set(Calendar.MILLISECOND, 0)

        return if (allowToday) {
            compareDate.after(today)
        } else {
            compareDate.timeInMillis > today.timeInMillis
        }
    }

    fun isDateInPast(date: Date, allowToday: Boolean = true): Boolean {
        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 23)
        today.set(Calendar.MINUTE, 59)
        today.set(Calendar.SECOND, 59)
        today.set(Calendar.MILLISECOND, 999)

        val compareDate = Calendar.getInstance()
        compareDate.time = date

        return if (allowToday) {
            compareDate.before(today) || isSameDay(date, Date())
        } else {
            compareDate.before(today) && !isSameDay(date, Date())
        }
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance()
        val cal2 = Calendar.getInstance()
        cal1.time = date1
        cal2.time = date2

        return cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR) &&
                cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
    }

    /**
     * Validates date according to DHIS2 program rules
     */
    fun validateTrackerDate(
        date: Date,
        dateType: TrackerDateType,
        programRules: List<String> = emptyList()
    ): DateValidationResult {
        val errors = mutableListOf<String>()

        // Basic validations based on date type
        when (dateType) {
            TrackerDateType.ENROLLMENT_DATE -> {
                if (isDateInFuture(date, allowToday = true)) {
                    errors.add("Enrollment date cannot be in the future")
                }
            }
            TrackerDateType.INCIDENT_DATE -> {
                if (isDateInFuture(date, allowToday = true)) {
                    errors.add("Incident date cannot be in the future")
                }
            }
            TrackerDateType.EVENT_DATE -> {
                // Event dates can be in future for scheduling
            }
        }

        return DateValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }
}

enum class TrackerDateType {
    ENROLLMENT_DATE,
    INCIDENT_DATE,
    EVENT_DATE
}

data class DateValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)