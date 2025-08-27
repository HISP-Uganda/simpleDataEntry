package com.ash.simpledataentry.domain.model

import java.util.Date

data class FilterState(
    val periodType: PeriodFilterType = PeriodFilterType.ALL,
    val relativePeriod: RelativePeriod? = null,
    val customFromDate: Date? = null,
    val customToDate: Date? = null,
    val syncStatus: SyncStatus = SyncStatus.ALL,
    val completionStatus: CompletionStatus = CompletionStatus.ALL,
    val searchQuery: String = ""
)

data class DatasetInstanceFilterState(
    val periodType: PeriodFilterType = PeriodFilterType.ALL,
    val relativePeriod: RelativePeriod? = null,
    val customFromDate: Date? = null,
    val customToDate: Date? = null,
    val syncStatus: SyncStatus = SyncStatus.ALL,
    val completionStatus: CompletionStatus = CompletionStatus.ALL,
    val attributeOptionCombo: String? = null,
    val searchQuery: String = ""
) {
    fun hasActiveFilters(): Boolean {
        return periodType != PeriodFilterType.ALL ||
                syncStatus != SyncStatus.ALL ||
                completionStatus != CompletionStatus.ALL ||
                attributeOptionCombo != null ||
                searchQuery.isNotBlank()
    }
}

enum class PeriodFilterType {
    ALL,
    RELATIVE,
    CUSTOM_RANGE
}

enum class RelativePeriod(val displayName: String, val months: Int) {
    // Daily periods
    TODAY("Today", 0),
    YESTERDAY("Yesterday", 0),
    LAST_3_DAYS("Last 3 Days", 0),
    LAST_7_DAYS("Last 7 Days", 0),
    LAST_14_DAYS("Last 14 Days", 0),
    
    // Weekly periods
    THIS_WEEK("This Week", 0),
    LAST_WEEK("Last Week", 0),
    LAST_4_WEEKS("Last 4 Weeks", 0),
    LAST_12_WEEKS("Last 12 Weeks", 0),
    
    // Monthly periods
    THIS_MONTH("This Month", 0),
    LAST_MONTH("Last Month", -1),
    LAST_3_MONTHS("Last 3 Months", -3),
    LAST_6_MONTHS("Last 6 Months", -6),
    LAST_12_MONTHS("Last 12 Months", -12),
    
    // Bi-monthly periods
    THIS_BIMONTH("This Bi-month", 0),
    LAST_BIMONTH("Last Bi-month", -2),
    LAST_6_BIMONTHS("Last 6 Bi-months", -12),
    
    // Quarterly periods
    THIS_QUARTER("This Quarter", 0),
    LAST_QUARTER("Last Quarter", -3),
    LAST_4_QUARTERS("Last 4 Quarters", -12),
    
    // Six-monthly periods
    THIS_SIX_MONTH("This Six-month", 0),
    LAST_SIX_MONTH("Last Six-month", -6),
    LAST_2_SIXMONTHS("Last 2 Six-months", -12),
    
    // Yearly periods
    THIS_YEAR("This Year", 0),
    LAST_YEAR("Last Year", -12),
    LAST_5_YEARS("Last 5 Years", -60),
    
    // Financial years
    THIS_FINANCIAL_YEAR("This Financial Year", 0),
    LAST_FINANCIAL_YEAR("Last Financial Year", -12)
}

enum class SyncStatus(val displayName: String) {
    ALL("All"),
    SYNCED("Synced"),
    NOT_SYNCED("Not Synced")
}

enum class CompletionStatus(val displayName: String) {
    ALL("All"),
    COMPLETE("Complete"),
    INCOMPLETE("Incomplete")
}
