/**
 * DHIS2 SDK Safe Wrapper Templates
 * Auto-generated code templates for safe DHIS2 SDK integration
 *
 * These templates follow the established patterns in simpleDataEntry project
 */

package com.ash.simpledataentry.templates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.hisp.dhis.android.core.D2
import android.util.Log

// ============================================================================
// TEMPLATE 1: Repository Suspend Function (Single Item)
// ============================================================================
/**
 * Template for fetching a single item from DHIS2 SDK
 *
 * Usage: Replace {{PLACEHOLDERS}} with actual values
 * Example: fetchDataElement, fetchOrganisationUnit, fetchProgram
 */
suspend fun fetch{{ENTITY_NAME}}(
    d2: D2,
    uid: String
): {{ENTITY_TYPE}}? = withContext(Dispatchers.IO) {
    try {
        d2.{{MODULE_NAME}}().{{ENTITY_COLLECTION}}()
            .uid(uid)
            .blockingGet()
    } catch (e: Exception) {
        Log.e("{{TAG}}", "Failed to fetch {{ENTITY_NAME}}: $uid", e)
        null  // or throw, depending on error handling strategy
    }
}

// ============================================================================
// TEMPLATE 2: Repository Suspend Function (List of Items)
// ============================================================================
/**
 * Template for fetching multiple items from DHIS2 SDK
 *
 * Example: fetchDataElements, fetchOrganisationUnits, fetchPrograms
 */
suspend fun fetch{{ENTITY_NAME}}List(
    d2: D2,
    {{FILTER_PARAMS}}
): List<{{ENTITY_TYPE}}> = withContext(Dispatchers.IO) {
    try {
        d2.{{MODULE_NAME}}().{{ENTITY_COLLECTION}}()
            .{{FILTER_CHAIN}}
            .blockingGet()
    } catch (e: Exception) {
        Log.e("{{TAG}}", "Failed to fetch {{ENTITY_NAME}} list", e)
        emptyList()
    }
}

// ============================================================================
// TEMPLATE 3: Batch Fetch with Caching
// ============================================================================
/**
 * Template for batch fetching items to avoid repeated SDK calls
 * Perfect for loops or repeated access patterns
 *
 * Example: Fetch all data element names at once, then use cache
 */
suspend fun fetch{{ENTITY_NAME}}Batch(
    d2: D2,
    uids: List<String>
): Map<String, {{ENTITY_TYPE}}> = withContext(Dispatchers.IO) {
    uids.associateWith { uid ->
        try {
            d2.{{MODULE_NAME}}().{{ENTITY_COLLECTION}}()
                .uid(uid)
                .blockingGet()
        } catch (e: Exception) {
            Log.w("{{TAG}}", "Failed to load {{ENTITY_NAME}}: $uid", e)
            null
        }
    }.filterValues { it != null } as Map<String, {{ENTITY_TYPE}}>
}

// ============================================================================
// TEMPLATE 4: Flow with flowOn (Reactive Streaming)
// ============================================================================
/**
 * Template for reactive data streaming from DHIS2 SDK
 * Use when you need continuous updates or cancellable operations
 *
 * Example: Observe program changes, watch sync progress
 */
fun observe{{ENTITY_NAME}}(
    d2: D2,
    uid: String
): Flow<{{ENTITY_TYPE}}?> = flow {
    val data = d2.{{MODULE_NAME}}().{{ENTITY_COLLECTION}}()
        .uid(uid)
        .blockingGet()
    emit(data)
}.flowOn(Dispatchers.IO)

// ============================================================================
// TEMPLATE 5: Sync Operation with Result Type
// ============================================================================
/**
 * Template for DHIS2 sync operations
 * Returns Result<T> for explicit error handling
 *
 * Example: syncMetadata, uploadDataValues, downloadProgram
 */
suspend fun sync{{OPERATION_NAME}}(
    d2: D2,
    {{PARAMS}}
): Result<Unit> = withContext(Dispatchers.IO) {
    try {
        d2.{{MODULE_NAME}}().{{OPERATION_METHOD}}()
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e("{{TAG}}", "Sync failed: {{OPERATION_NAME}}", e)
        Result.failure(e)
    }
}

// ============================================================================
// TEMPLATE 6: Repository with Domain Model Transformation
// ============================================================================
/**
 * Template for repository method that transforms SDK model to domain model
 * Follows clean architecture pattern
 *
 * Example: Get program as domain Program, not SDK org.hisp.dhis... Program
 */
suspend fun get{{ENTITY_NAME}}(
    d2: D2,
    uid: String
): {{DOMAIN_MODEL}}? = withContext(Dispatchers.IO) {
    try {
        val sdkModel = d2.{{MODULE_NAME}}().{{ENTITY_COLLECTION}}()
            .uid(uid)
            .blockingGet()

        sdkModel?.let { toDomainModel(it) }
    } catch (e: Exception) {
        Log.e("{{TAG}}", "Failed to fetch {{ENTITY_NAME}}: $uid", e)
        null
    }
}

/**
 * Extension function for SDK → Domain transformation
 */
private fun toDomainModel(sdkModel: {{SDK_TYPE}}): {{DOMAIN_MODEL}} {
    return {{DOMAIN_MODEL}}(
        id = sdkModel.uid(),
        name = sdkModel.displayName() ?: sdkModel.name() ?: "Unknown",
        // ... map other fields
    )
}

// ============================================================================
// TEMPLATE 7: User Org Unit Scoped Query
// ============================================================================
/**
 * Template for queries filtered by user's organization unit scope
 * Critical for data security - only show user's authorized data
 *
 * Example: Get datasets for user's org units only
 */
suspend fun fetch{{ENTITY_NAME}}ForUser(
    d2: D2
): List<{{ENTITY_TYPE}}> = withContext(Dispatchers.IO) {
    try {
        // Step 1: Get user's data capture org units
        val userOrgUnits = d2.organisationUnitModule()
            .organisationUnits()
            .byOrganisationUnitScope(
                OrganisationUnit.Scope.SCOPE_DATA_CAPTURE
            )
            .blockingGet()

        val userOrgUnitUid = userOrgUnits.firstOrNull()?.uid()
            ?: return@withContext emptyList()

        // Step 2: Query data filtered by org unit
        d2.{{MODULE_NAME}}().{{ENTITY_COLLECTION}}()
            .byOrganisationUnitUid().eq(userOrgUnitUid)
            .blockingGet()
    } catch (e: Exception) {
        Log.e("{{TAG}}", "Failed to fetch {{ENTITY_NAME}} for user", e)
        emptyList()
    }
}

// ============================================================================
// TEMPLATE 8: Category Combo Query with Error Recovery
// ============================================================================
/**
 * Template for querying category combos (common in data entry)
 * Includes fallback for missing category options
 *
 * Example: Get category option combos for dataset
 */
suspend fun getCategoryCombos(
    d2: D2,
    categoryComboUid: String
): List<CategoryOptionCombo> = withContext(Dispatchers.IO) {
    try {
        val categoryCombo = d2.categoryModule().categoryCombos()
            .withCategories()
            .uid(categoryComboUid)
            .blockingGet()

        categoryCombo?.let {
            d2.categoryModule().categoryOptionCombos()
                .byCategoryComboUid().eq(categoryComboUid)
                .blockingGet()
        } ?: emptyList()
    } catch (e: Exception) {
        Log.e("CategoryCombo", "Failed to load category combos", e)
        emptyList()
    }
}

// ============================================================================
// TEMPLATE 9: Pagination Support for Large Datasets
// ============================================================================
/**
 * Template for paginated queries when dealing with large datasets
 * Prevents memory issues and improves performance
 *
 * Example: Load 50 enrollments at a time
 */
suspend fun fetch{{ENTITY_NAME}}Page(
    d2: D2,
    page: Int,
    pageSize: Int = 50
): List<{{ENTITY_TYPE}}> = withContext(Dispatchers.IO) {
    try {
        d2.{{MODULE_NAME}}().{{ENTITY_COLLECTION}}()
            .{{FILTER_CHAIN}}
            .limit(pageSize)
            .offset(page * pageSize)
            .blockingGet()
    } catch (e: Exception) {
        Log.e("{{TAG}}", "Failed to fetch page $page of {{ENTITY_NAME}}", e)
        emptyList()
    }
}

// ============================================================================
// TEMPLATE 10: ViewModel Integration Pattern
// ============================================================================
/**
 * Template showing how to call repository methods from ViewModel
 * Demonstrates proper coroutine scope usage
 */
@HiltViewModel
class {{ENTITY_NAME}}ViewModel @Inject constructor(
    private val repository: {{ENTITY_NAME}}Repository
) : ViewModel() {

    private val _state = MutableStateFlow({{ENTITY_NAME}}State())
    val state: StateFlow<{{ENTITY_NAME}}State> = _state.asStateFlow()

    fun load{{ENTITY_NAME}}(uid: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            // Repository handles IO dispatching - no need for withContext here
            val result = repository.fetch{{ENTITY_NAME}}(uid)

            _state.value = _state.value.copy(
                data = result,
                isLoading = false,
                error = if (result == null) "Failed to load" else null
            )
        }
    }
}

// ============================================================================
// COMMON DHIS2 SDK MODULES REFERENCE
// ============================================================================
/**
 * Quick reference for DHIS2 SDK module names:
 *
 * d2.dataSetModule()              → Aggregate datasets
 * d2.programModule()              → Tracker & Event programs
 * d2.dataElementModule()          → Data elements
 * d2.organisationUnitModule()     → Organization units
 * d2.categoryModule()             → Categories & combinations
 * d2.dataValueModule()            → Data values (aggregate)
 * d2.trackedEntityModule()        → Tracked entities
 * d2.enrollmentModule()           → Program enrollments
 * d2.eventModule()                → Program events
 * d2.metadataModule()             → Metadata sync
 * d2.userModule()                 → User & authentication
 * d2.systemInfoModule()           → System information
 */
