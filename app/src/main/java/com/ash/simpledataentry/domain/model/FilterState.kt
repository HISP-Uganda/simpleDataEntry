package com.ash.simpledataentry.domain.model

import org.hisp.dhis.android.core.common.RelativePeriod
import java.util.Date

data class FilterState(
    val periodType: PeriodFilterType = PeriodFilterType.ALL,
    val relativePeriod: RelativePeriod? = null,
    val customFromDate: Date? = null,
    val customToDate: Date? = null,
    val syncStatus: SyncStatus = SyncStatus.ALL,
    val completionStatus: CompletionStatus = CompletionStatus.ALL,
    val searchQuery: String = "",
    val sortBy: SortBy = SortBy.NAME,
    val sortOrder: SortOrder = SortOrder.ASCENDING,
    val datasetPeriodType: DatasetPeriodType = DatasetPeriodType.ALL,
    val organizationUnit: OrganizationUnitFilter = OrganizationUnitFilter.ALL
)

data class DatasetInstanceFilterState(
    val periodType: PeriodFilterType = PeriodFilterType.ALL,
    val relativePeriod: RelativePeriod? = null,
    val customFromDate: Date? = null,
    val customToDate: Date? = null,
    val syncStatus: SyncStatus = SyncStatus.ALL,
    val completionStatus: CompletionStatus = CompletionStatus.ALL,
    val attributeOptionCombo: String? = null,
    val searchQuery: String = "",
    val sortBy: InstanceSortBy = InstanceSortBy.PERIOD,
    val sortOrder: SortOrder = SortOrder.DESCENDING
) {
    fun hasActiveFilters(): Boolean {
        return periodType != PeriodFilterType.ALL ||
                syncStatus != SyncStatus.ALL ||
                completionStatus != CompletionStatus.ALL ||
                attributeOptionCombo != null ||
                searchQuery.isNotBlank() ||
                sortBy != InstanceSortBy.PERIOD ||
                sortOrder != SortOrder.DESCENDING
    }
}

enum class PeriodFilterType {
    ALL,
    RELATIVE,
    CUSTOM_RANGE
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

enum class SortBy(val displayName: String) {
    NAME("Name"),
    CREATED_DATE("Created Date"),
    ENTRY_COUNT("Entry Count")
}

enum class SortOrder(val displayName: String) {
    ASCENDING("Ascending"),
    DESCENDING("Descending")
}

enum class InstanceSortBy(val displayName: String) {
    ORGANISATION_UNIT("Organisation Unit"),
    PERIOD("Period"),
    LAST_UPDATED("Last Updated"),
    COMPLETION_STATUS("Completion Status")
}

enum class DatasetPeriodType(val displayName: String) {
    ALL("All Period Types"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    QUARTERLY("Quarterly"),
    YEARLY("Yearly")
}

enum class OrganizationUnitFilter(val displayName: String) {
    ALL("All Organization Units"),
    ASSIGNED("My Assigned Units"),
    FAVORITES("Favorite Units"),
    RECENT("Recently Used")
}
