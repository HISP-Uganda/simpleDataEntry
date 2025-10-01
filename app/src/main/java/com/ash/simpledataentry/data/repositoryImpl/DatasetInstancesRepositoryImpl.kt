package com.ash.simpledataentry.data.repositoryImpl

import android.util.Log
import com.ash.simpledataentry.domain.model.DatasetInstance
import com.ash.simpledataentry.domain.model.DatasetInstanceState
import com.ash.simpledataentry.domain.model.Period
import com.ash.simpledataentry.domain.repository.DatasetInstancesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flowOn
import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.data.local.AppDatabase
import com.ash.simpledataentry.data.local.DraftInstanceSummary
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.organisationunit.OrganisationUnit
import javax.inject.Inject

private const val TAG = "DatasetInstancesRepo"

class DatasetInstancesRepositoryImpl @Inject constructor(
    private val sessionManager: SessionManager,
    private val database: AppDatabase
) : DatasetInstancesRepository {

    private val d2 get() = sessionManager.getD2()!!

    override suspend fun getDatasetInstances(datasetId: String): List<DatasetInstance> {
        Log.d(TAG, "Fetching dataset instances for dataset: $datasetId")
        return withContext(Dispatchers.IO) {
            try {
                // Get user's data capture org units
                Log.d(TAG, "Fetching user org units")
                val userOrgUnits = d2.organisationUnitModule().organisationUnits()
                    .byOrganisationUnitScope(OrganisationUnit.Scope.SCOPE_DATA_CAPTURE)
                    .blockingGet()

                Log.d(TAG, "Found ${userOrgUnits.size} org units")

                val userOrgUnitUid = userOrgUnits.firstOrNull()?.uid()
                if (userOrgUnitUid == null) {
                    Log.e(TAG, "No organization unit found for user")
                    return@withContext emptyList()
                }

                Log.d(TAG, "Using org unit: $userOrgUnitUid")

                // Get dataset instances

                val instance = d2.dataSetModule()
                    .dataSetInstances()
                    .byDataSetUid().eq(datasetId)
                    .byOrganisationUnitUid().eq(userOrgUnitUid)
                    .blockingCount()




                val instances = d2.dataSetModule()
                    .dataSetInstances()
                    .byDataSetUid().eq(datasetId)
                    .byOrganisationUnitUid().eq(userOrgUnitUid)
                    .blockingGet()

                Log.d(TAG, "Found $instance instances for dataset")

                Log.d(TAG, "Found ${instances.size} instances for dataset")


                val sdkInstances = instances.map { instance ->
                    Log.d(TAG, "Processing SDK instance: ${instance.id()} | datasetId: ${instance.dataSetUid()} | period: ${instance.period()} | orgUnit: ${instance.organisationUnitUid()} | attributeOptionComboUid: ${instance.attributeOptionComboUid()} | attributeOptionComboDisplayName: ${instance.attributeOptionComboDisplayName()}")
                    DatasetInstance(
                        id = instance.id().toString(),
                        datasetId = instance.dataSetUid(),
                        period = Period(id = instance.period()),
                        organisationUnit = com.ash.simpledataentry.domain.model.OrganisationUnit(
                            id = instance.organisationUnitUid(),
                            name = instance.organisationUnitDisplayName() ?: ""
                        ),
                        attributeOptionCombo = instance.attributeOptionComboUid() ?: "",
                        state = when {
                            instance.completed() == true -> DatasetInstanceState.COMPLETE
                            else -> DatasetInstanceState.OPEN
                        },
                        lastUpdated = instance.lastUpdated()?.toString()
                    )
                }
                
                // Get draft instances that don't have corresponding SDK instances
                val draftInstances = database.dataValueDraftDao().getDistinctDraftInstances(datasetId)
                Log.d(TAG, "Found ${draftInstances.size} draft instances")
                
                val sdkInstanceKeys = sdkInstances.map { "${it.period.id}-${it.organisationUnit.id}-${it.attributeOptionCombo}" }.toSet()
                
                val draftOnlyInstances = draftInstances.filter { draft ->
                    val key = "${draft.period}-${draft.orgUnit}-${draft.attributeOptionCombo}"
                    !sdkInstanceKeys.contains(key)
                }.map { draft ->
                    Log.d(TAG, "Processing draft-only instance: datasetId: ${draft.datasetId} | period: ${draft.period} | orgUnit: ${draft.orgUnit} | attributeOptionCombo: ${draft.attributeOptionCombo}")
                    
                    // Get org unit name from DHIS2 SDK
                    val orgUnitName = try {
                        d2.organisationUnitModule().organisationUnits()
                            .uid(draft.orgUnit)
                            .blockingGet()?.displayName() ?: draft.orgUnit
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not get org unit name for ${draft.orgUnit}", e)
                        draft.orgUnit
                    }
                    
                    DatasetInstance(
                        id = "draft-${draft.datasetId}-${draft.period}-${draft.orgUnit}-${draft.attributeOptionCombo}",
                        datasetId = draft.datasetId,
                        period = Period(id = draft.period),
                        organisationUnit = com.ash.simpledataentry.domain.model.OrganisationUnit(
                            id = draft.orgUnit,
                            name = orgUnitName
                        ),
                        attributeOptionCombo = draft.attributeOptionCombo,
                        state = DatasetInstanceState.OPEN, // Draft instances are always open
                        lastUpdated = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", java.util.Locale.getDefault())
                            .format(java.util.Date(draft.lastModified))
                    )
                }
                
                val allInstances = (sdkInstances + draftOnlyInstances).sortedByDescending { it.lastUpdated }
                Log.d(TAG, "Returning ${allInstances.size} total instances (${sdkInstances.size} SDK + ${draftOnlyInstances.size} draft-only)")
                allInstances
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching dataset instances", e)
                throw e
            }
        }
    }

    override suspend fun syncDatasetInstances() {
        Log.d(TAG, "Starting complete data sync (datasets, tracker, events)")
        withContext(Dispatchers.IO) {
            try {
                // Upload local changes to dataset instances
               // d2.dataSetModule().dataSetInstances().blockingUpload()

                // Download latest dataset instances from server
                Log.d(TAG, "Syncing dataset instances...")
                d2.dataSetModule().dataSetInstances().blockingGet()

                // Download tracker data (enrollments and their events)
                Log.d(TAG, "Syncing tracker data...")

                // Get tracker programs
                val trackerPrograms = d2.programModule().programs()
                    .byProgramType().eq(org.hisp.dhis.android.core.program.ProgramType.WITH_REGISTRATION)
                    .blockingGet()

                Log.d(TAG, "Found ${trackerPrograms.size} tracker programs to sync")

                // Use EXACT official Android Capture app patterns
                Log.d(TAG, "Using EXACT official Android Capture app patterns")

                if (trackerPrograms.isNotEmpty()) {
                    // Download for each tracker program using official pattern
                    trackerPrograms.forEach { program ->
                        val programUid = program.uid()
                        Log.d(TAG, "Syncing tracker data for program: $programUid")

                        d2.trackedEntityModule().trackedEntityInstanceDownloader()
                            .byProgramUid(programUid)
                            .download()
                    }
                } else {
                    Log.d(TAG, "No tracker programs found, downloading all tracker data")
                    // Fallback: download all tracker data without program filter
                    d2.trackedEntityModule().trackedEntityInstanceDownloader()
                        .download()
                }

                // Download standalone events using official pattern
                Log.d(TAG, "Syncing event data...")
                d2.eventModule().eventDownloader()
                    .download()

                Log.d(TAG, "Complete sync completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error during sync", e)
                throw e
            }
        }
    }

    override suspend fun completeDatasetInstance(datasetId: String, period: String, orgUnit: String, attributeOptionCombo: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Marking dataset as complete: $datasetId, $period, $orgUnit, $attributeOptionCombo")
                d2.dataSetModule().dataSetCompleteRegistrations().value(period,orgUnit,datasetId,attributeOptionCombo).blockingSet()
                d2.dataSetModule().dataSetCompleteRegistrations().blockingUpload()
                Log.d(TAG, "Dataset marked as complete successfully.")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to complete dataset instance", e)
                Result.failure(e)
            }
        }
    }
    
    override suspend fun getDatasetInstanceCount(datasetId: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Getting instance count for dataset: $datasetId")
                
                // Get user's data capture org units
                val userOrgUnits = d2.organisationUnitModule().organisationUnits()
                    .byOrganisationUnitScope(OrganisationUnit.Scope.SCOPE_DATA_CAPTURE)
                    .blockingGet()

                val userOrgUnitUid = userOrgUnits.firstOrNull()?.uid()
                if (userOrgUnitUid == null) {
                    Log.e(TAG, "No organization unit found for user")
                    return@withContext 0
                }

                // Count dataset instances
                val count = d2.dataSetModule()
                    .dataSetInstances()
                    .byDataSetUid().eq(datasetId)
                    .byOrganisationUnitUid().eq(userOrgUnitUid)
                    .blockingCount()

                Log.d(TAG, "Found $count instances for dataset $datasetId")
                count
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get instance count for dataset $datasetId", e)
                0
            }
        }
    }

    override suspend fun markDatasetInstanceIncomplete(datasetId: String, period: String, orgUnit: String, attributeOptionCombo: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Marking dataset as incomplete: $datasetId, $period, $orgUnit, $attributeOptionCombo")
                d2.dataSetModule().dataSetCompleteRegistrations()
                    .value(period, orgUnit, datasetId, attributeOptionCombo).blockingDeleteIfExist()
                d2.dataSetModule().dataSetCompleteRegistrations().blockingUpload()
                Log.d(TAG, "Dataset marked as incomplete successfully.")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark dataset as incomplete", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Dedicated tracker data synchronization method
     * Handles tracker-specific requirements separately from aggregate datasets
     */
    private suspend fun syncTrackerData(programId: String) = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== TRACKER DATA SYNC START for program: $programId ===")

        try {
            // Validate authentication state
            val isLoggedIn = try {
                d2.userModule().isLogged().blockingGet()
            } catch (e: Exception) {
                false
            }

            if (!isLoggedIn) {
                Log.e(TAG, "User not authenticated - cannot sync tracker data in offline mode")
                throw Exception("Cannot sync tracker data in offline mode. Please login online first.")
            }

            val user = d2.userModule().user().blockingGet()
            if (user?.uid() == null) {
                Log.e(TAG, "Invalid authentication state - user UID is null")
                throw Exception("Invalid authentication state")
            }

            Log.d(TAG, "User authenticated: ${user.uid()}")

            // STEP 1: Ensure tracker-specific metadata is complete
            Log.d(TAG, "STEP 1: Ensuring tracker metadata is complete...")
            try {
                // Download complete metadata with special focus on tracker dependencies
                d2.metadataModule().blockingDownload()
                Log.d(TAG, "Metadata sync completed")
            } catch (e: Exception) {
                Log.e(TAG, "Metadata sync failed: ${e.message}", e)
                throw e
            }

            // STEP 2: Check and resolve foreign key violations BEFORE download
            Log.d(TAG, "STEP 2: Pre-download foreign key violation check...")
            sessionManager.checkForeignKeyViolations()

            // STEP 3: Get user's org unit scope for tracker data
            val userOrgUnits = d2.organisationUnitModule().organisationUnits()
                .byOrganisationUnitScope(OrganisationUnit.Scope.SCOPE_DATA_CAPTURE)
                .blockingGet()

            Log.d(TAG, "User has data capture access to ${userOrgUnits.size} org units")
            userOrgUnits.forEach { orgUnit ->
                Log.d(TAG, "  - ${orgUnit.uid()}: ${orgUnit.displayName()}")
            }

            if (userOrgUnits.isEmpty()) {
                Log.e(TAG, "No org units with data capture scope found")
                throw Exception("No organization units found for tracker data access")
            }

            // STEP 4: Perform tracker-specific download
            Log.d(TAG, "STEP 4: Downloading tracker data for program $programId...")

            // Use TrackedEntityInstanceDownloader with specific program filter
            val downloader = d2.trackedEntityModule().trackedEntityInstanceDownloader()
                .byProgramUid(programId)

            // Execute download
            val downloadResult = downloader.download()
            Log.d(TAG, "Tracker download completed with result: $downloadResult")

            // STEP 5: Post-download validation and foreign key violation resolution
            Log.d(TAG, "STEP 5: Post-download validation...")

            // Check for any foreign key violations that may have occurred during download
            sessionManager.checkForeignKeyViolations()

            // STEP 6: Verify what was actually stored
            Log.d(TAG, "STEP 6: Verifying stored tracker data...")

            val storedTEIs = d2.trackedEntityModule().trackedEntityInstances()
                .byProgramUids(listOf(programId))
                .blockingGet()
            Log.d(TAG, "Found ${storedTEIs.size} TrackedEntityInstances in local storage")

            val storedEnrollments = d2.enrollmentModule().enrollments()
                .byProgram().eq(programId)
                .blockingGet()
            Log.d(TAG, "Found ${storedEnrollments.size} Enrollments in local storage")

            val storedEvents = d2.eventModule().events()
                .byProgramUid().eq(programId)
                .blockingGet()
            Log.d(TAG, "Found ${storedEvents.size} Events in local storage")

            // STEP 7: Additional diagnostics if no data was stored despite successful API call
            if (storedTEIs.isEmpty() && storedEnrollments.isEmpty()) {
                Log.w(TAG, "DIAGNOSTIC: No tracker data stored despite successful download")

                // Check if program exists and is accessible
                val program = d2.programModule().programs().uid(programId).blockingGet()
                Log.d(TAG, "DIAGNOSTIC: Program details - UID: ${program?.uid()}, Name: ${program?.displayName()}, Type: ${program?.programType()}")

                // Check if there are any TEIs at all for any program in accessible org units
                val anyTEIs = d2.trackedEntityModule().trackedEntityInstances()
                    .blockingCount()
                Log.d(TAG, "DIAGNOSTIC: Total TEIs in database: $anyTEIs")

                // Check if the issue is org unit scope mismatch
                val allOrgUnitsWithTEIs = d2.trackedEntityModule().trackedEntityInstances()
                    .blockingGet()
                    .map { it.organisationUnit() }
                    .distinct()
                Log.d(TAG, "DIAGNOSTIC: Org units that have TEI data: ${allOrgUnitsWithTEIs.joinToString(", ")}")

                val userAccessibleOrgUnitUIDs = userOrgUnits.map { it.uid() }
                Log.d(TAG, "DIAGNOSTIC: User accessible org units: ${userAccessibleOrgUnitUIDs.joinToString(", ")}")

                val overlap = allOrgUnitsWithTEIs.intersect(userAccessibleOrgUnitUIDs.toSet())
                Log.d(TAG, "DIAGNOSTIC: Org unit overlap (data exists + user access): ${overlap.joinToString(", ")}")
            }

            Log.d(TAG, "=== TRACKER DATA SYNC COMPLETED ===")

        } catch (e: Exception) {
            Log.e(TAG, "=== TRACKER DATA SYNC FAILED ===")
            Log.e(TAG, "Error: ${e.message}", e)
            throw e
        }
    }

    // New unified program instance methods
    override suspend fun getProgramInstances(
        programId: String,
        programType: com.ash.simpledataentry.domain.model.ProgramType
    ): kotlinx.coroutines.flow.Flow<List<com.ash.simpledataentry.domain.model.ProgramInstance>> =
        kotlinx.coroutines.flow.flow {
            when (programType) {
                com.ash.simpledataentry.domain.model.ProgramType.DATASET -> {
                    val datasetInstances = getDatasetInstances(programId)
                    val programInstances = datasetInstances.map { datasetInstance ->
                        com.ash.simpledataentry.domain.model.ProgramInstance.DatasetInstance(
                            id = datasetInstance.id,
                            programId = datasetInstance.datasetId,
                            programName = "Dataset", // TODO: Get actual dataset name
                            organisationUnit = datasetInstance.organisationUnit,
                            lastUpdated = try {
                                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", java.util.Locale.getDefault())
                                    .parse(datasetInstance.lastUpdated ?: java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date()))
                            } catch (e: Exception) {
                                java.util.Date()
                            },
                            state = when (datasetInstance.state) {
                                DatasetInstanceState.OPEN -> com.ash.simpledataentry.domain.model.ProgramInstanceState.ACTIVE
                                DatasetInstanceState.COMPLETE -> com.ash.simpledataentry.domain.model.ProgramInstanceState.COMPLETED
                                DatasetInstanceState.APPROVED -> com.ash.simpledataentry.domain.model.ProgramInstanceState.APPROVED
                                DatasetInstanceState.LOCKED -> com.ash.simpledataentry.domain.model.ProgramInstanceState.LOCKED
                            },
                            syncStatus = com.ash.simpledataentry.domain.model.SyncStatus.SYNCED, // TODO: Determine actual sync status
                            period = datasetInstance.period,
                            attributeOptionCombo = datasetInstance.attributeOptionCombo,
                            originalDatasetInstance = datasetInstance
                        )
                    }
                    emit(programInstances)
                }
                com.ash.simpledataentry.domain.model.ProgramType.TRACKER -> {
                    getTrackerEnrollments(programId).collect { enrollments ->
                        emit(enrollments.map { it as com.ash.simpledataentry.domain.model.ProgramInstance })
                    }
                }
                com.ash.simpledataentry.domain.model.ProgramType.EVENT -> {
                    getEventInstances(programId).collect { events ->
                        emit(events.map { it as com.ash.simpledataentry.domain.model.ProgramInstance })
                    }
                }
                else -> emit(emptyList())
            }
        }

    override suspend fun getProgramInstanceCount(
        programId: String,
        programType: com.ash.simpledataentry.domain.model.ProgramType
    ): Int {
        return when (programType) {
            com.ash.simpledataentry.domain.model.ProgramType.DATASET -> getDatasetInstanceCount(programId)
            com.ash.simpledataentry.domain.model.ProgramType.TRACKER -> {
                withContext(Dispatchers.IO) {
                    try {
                        d2.trackedEntityModule().trackedEntityInstances()
                            .byProgramUids(listOf(programId))
                            .blockingCount()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to get tracker enrollment count for program $programId", e)
                        0
                    }
                }
            }
            com.ash.simpledataentry.domain.model.ProgramType.EVENT -> {
                withContext(Dispatchers.IO) {
                    try {
                        d2.eventModule().events()
                            .byProgramUid().eq(programId)
                            .blockingCount()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to get event count for program $programId", e)
                        0
                    }
                }
            }
            else -> 0
        }
    }

    override suspend fun syncProgramInstances(
        programId: String,
        programType: com.ash.simpledataentry.domain.model.ProgramType
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                when (programType) {
                    com.ash.simpledataentry.domain.model.ProgramType.DATASET -> {
                        syncDatasetInstances()
                    }
                    com.ash.simpledataentry.domain.model.ProgramType.TRACKER -> {
                        syncTrackerData(programId)
                    }
                    com.ash.simpledataentry.domain.model.ProgramType.EVENT -> {
                        // Sync events - download from server
                        Log.d(TAG, "=== EVENT DATA SYNC START for program: $programId ===")

                        // First ensure metadata is up to date
                        d2.metadataModule().blockingDownload()
                        Log.d(TAG, "EVENT SYNC: Metadata sync completed")

                        // Then download actual event data for this specific program
                        Log.d(TAG, "EVENT SYNC: Starting event download...")
                        d2.eventModule().eventDownloader()
                            .byProgramUid(programId)
                            .blockingDownload()

                        // Verify what was actually stored
                        val storedEvents = d2.eventModule().events()
                            .byProgramUid().eq(programId)
                            .blockingGet()
                        Log.d(TAG, "EVENT SYNC: Found ${storedEvents.size} events in local storage for program $programId")

                        storedEvents.forEach { event ->
                            Log.d(TAG, "EVENT SYNC: - Event ${event.uid()}: status=${event.status()}, orgUnit=${event.organisationUnit()}, date=${event.eventDate()}")
                        }

                        Log.d(TAG, "=== EVENT DATA SYNC COMPLETED for program $programId ===")
                    }
                    else -> { /* Do nothing */ }
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync program instances for $programId", e)
                Result.failure(e)
            }
        }
    }

    // Tracker-specific methods
    override suspend fun getTrackerEnrollments(programId: String): kotlinx.coroutines.flow.Flow<List<com.ash.simpledataentry.domain.model.ProgramInstance.TrackerEnrollment>> =
        kotlinx.coroutines.flow.flow {
            try {
                Log.d(TAG, "Fetching tracker enrollments for program: $programId")

                // Get user's data capture org units AND their descendants (children)
                val userOrgUnits = d2.organisationUnitModule().organisationUnits()
                    .byOrganisationUnitScope(OrganisationUnit.Scope.SCOPE_DATA_CAPTURE)
                    .blockingGet()

                Log.d(TAG, "Found ${userOrgUnits.size} user org units for tracker enrollments")

                if (userOrgUnits.isEmpty()) {
                    Log.e(TAG, "No organization units found for user")
                    emit(emptyList())
                    return@flow
                }

                // Get all descendant org units (children) of user's org units
                val allAccessibleOrgUnits = mutableSetOf<String>()
                userOrgUnits.forEach { userOrgUnit ->
                    // Add the user's org unit itself
                    allAccessibleOrgUnits.add(userOrgUnit.uid())

                    // Add all descendants using the path-based approach
                    val descendants = d2.organisationUnitModule().organisationUnits()
                        .byPath().like("${userOrgUnit.path()}/%")
                        .blockingGet()

                    descendants.forEach { descendant ->
                        allAccessibleOrgUnits.add(descendant.uid())
                    }
                }

                Log.d(TAG, "Total accessible org units (including children): ${allAccessibleOrgUnits.size}")

                // Get enrollments using the expanded org unit list
                val enrollments = d2.enrollmentModule().enrollments()
                    .byProgram().eq(programId)
                    .byOrganisationUnit().`in`(allAccessibleOrgUnits.toList())
                    .byStatus().`in`(listOf(
                        org.hisp.dhis.android.core.enrollment.EnrollmentStatus.ACTIVE,
                        org.hisp.dhis.android.core.enrollment.EnrollmentStatus.COMPLETED
                    ))
                    .byDeleted().eq(false)
                    .blockingGet()

                Log.d(TAG, "Found ${enrollments.size} enrollments for program $programId (filtered by ${allAccessibleOrgUnits.size} org units including children, active/completed status, not deleted)")

                // Additional debugging: Check what enrollments exist without any filters
                val allEnrollmentsInProgram = d2.enrollmentModule().enrollments()
                    .byProgram().eq(programId)
                    .blockingGet()

                Log.d(TAG, "DEBUG: Found ${allEnrollmentsInProgram.size} total enrollments for program $programId (no filters)")

                if (allEnrollmentsInProgram.isNotEmpty()) {
                    allEnrollmentsInProgram.forEach { enrollment ->
                        Log.d(TAG, "DEBUG: Enrollment ${enrollment.uid()} - orgUnit: ${enrollment.organisationUnit()}, status: ${enrollment.status()}, deleted: ${enrollment.deleted()}")
                    }
                } else {
                    Log.w(TAG, "DEBUG: No enrollments found at all for program $programId - this means no tracker data exists for this program on the DHIS2 server within user's org unit scope")
                }

                // Additional debugging: Check what org units actually have data for this program
                val allEnrollmentsAnyProgram = d2.enrollmentModule().enrollments()
                    .blockingGet()

                Log.d(TAG, "DEBUG: Found ${allEnrollmentsAnyProgram.size} total enrollments across ALL programs")

                val orgUnitsWithData = allEnrollmentsAnyProgram.map { it.organisationUnit() }.distinct()
                Log.d(TAG, "DEBUG: Organization units that have enrollment data: ${orgUnitsWithData.joinToString(", ")}")
                Log.d(TAG, "DEBUG: User's accessible org units: ${allAccessibleOrgUnits.joinToString(", ")}")

                val programInstances = enrollments.map { enrollment ->
                    val orgUnit = d2.organisationUnitModule().organisationUnits()
                        .uid(enrollment.organisationUnit())
                        .blockingGet()

                    val program = d2.programModule().programs()
                        .uid(programId)
                        .blockingGet()

                    com.ash.simpledataentry.domain.model.ProgramInstance.TrackerEnrollment(
                        id = enrollment.uid(),
                        programId = programId,
                        programName = program?.displayName() ?: program?.name() ?: "Unknown Program",
                        organisationUnit = com.ash.simpledataentry.domain.model.OrganisationUnit(
                            id = orgUnit?.uid() ?: "",
                            name = orgUnit?.displayName() ?: "Unknown"
                        ),
                        lastUpdated = enrollment.lastUpdated() ?: java.util.Date(),
                        state = when (enrollment.status()) {
                            org.hisp.dhis.android.core.enrollment.EnrollmentStatus.ACTIVE ->
                                com.ash.simpledataentry.domain.model.ProgramInstanceState.ACTIVE
                            org.hisp.dhis.android.core.enrollment.EnrollmentStatus.COMPLETED ->
                                com.ash.simpledataentry.domain.model.ProgramInstanceState.COMPLETED
                            org.hisp.dhis.android.core.enrollment.EnrollmentStatus.CANCELLED ->
                                com.ash.simpledataentry.domain.model.ProgramInstanceState.CANCELLED
                            else -> com.ash.simpledataentry.domain.model.ProgramInstanceState.ACTIVE
                        },
                        syncStatus = when (enrollment.aggregatedSyncState()) {
                            org.hisp.dhis.android.core.common.State.SYNCED -> com.ash.simpledataentry.domain.model.SyncStatus.SYNCED
                            org.hisp.dhis.android.core.common.State.TO_UPDATE -> com.ash.simpledataentry.domain.model.SyncStatus.NOT_SYNCED
                            org.hisp.dhis.android.core.common.State.TO_POST -> com.ash.simpledataentry.domain.model.SyncStatus.NOT_SYNCED
                            else -> com.ash.simpledataentry.domain.model.SyncStatus.SYNCED
                        },
                        trackedEntityInstance = enrollment.trackedEntityInstance() ?: "",
                        enrollmentDate = enrollment.enrollmentDate() ?: java.util.Date(),
                        incidentDate = enrollment.incidentDate(),
                        followUp = enrollment.followUp() ?: false,
                        completedDate = enrollment.completedDate()
                    )
                }
                emit(programInstances)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get tracker enrollments for program $programId", e)
                emit(emptyList())
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun createTrackerEnrollment(
        programId: String,
        trackedEntityId: String,
        orgUnitId: String
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val enrollmentUid = d2.enrollmentModule().enrollments()
                    .blockingAdd(
                        org.hisp.dhis.android.core.enrollment.EnrollmentCreateProjection.builder()
                            .program(programId)
                            .organisationUnit(orgUnitId)
                            .trackedEntityInstance(trackedEntityId)
                            .build()
                    )
                Result.success(enrollmentUid)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create tracker enrollment", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun completeEnrollment(enrollmentId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                d2.enrollmentModule().enrollments().uid(enrollmentId)
                    .setStatus(org.hisp.dhis.android.core.enrollment.EnrollmentStatus.COMPLETED)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to complete enrollment", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun cancelEnrollment(enrollmentId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                d2.enrollmentModule().enrollments().uid(enrollmentId)
                    .setStatus(org.hisp.dhis.android.core.enrollment.EnrollmentStatus.CANCELLED)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel enrollment", e)
                Result.failure(e)
            }
        }
    }

    // Event-specific methods
    override suspend fun getEventInstances(programId: String): kotlinx.coroutines.flow.Flow<List<com.ash.simpledataentry.domain.model.ProgramInstance.EventInstance>> =
        kotlinx.coroutines.flow.flow {
            try {
                Log.d(TAG, "Fetching event instances for program: $programId")

                // Get user's data capture org units AND their descendants (children)
                val userOrgUnits = d2.organisationUnitModule().organisationUnits()
                    .byOrganisationUnitScope(OrganisationUnit.Scope.SCOPE_DATA_CAPTURE)
                    .blockingGet()

                Log.d(TAG, "Found ${userOrgUnits.size} user org units for event instances")

                if (userOrgUnits.isEmpty()) {
                    Log.e(TAG, "No organization units found for user")
                    emit(emptyList())
                    return@flow
                }

                // Get all descendant org units (children) of user's org units
                val allAccessibleOrgUnits = mutableSetOf<String>()
                userOrgUnits.forEach { userOrgUnit ->
                    // Add the user's org unit itself
                    allAccessibleOrgUnits.add(userOrgUnit.uid())

                    // Add all descendants using the path-based approach
                    val descendants = d2.organisationUnitModule().organisationUnits()
                        .byPath().like("${userOrgUnit.path()}/%")
                        .blockingGet()

                    descendants.forEach { descendant ->
                        allAccessibleOrgUnits.add(descendant.uid())
                    }
                }

                Log.d(TAG, "Total accessible org units for events (including children): ${allAccessibleOrgUnits.size}")

                // Get events using the expanded org unit list
                val events = d2.eventModule().events()
                    .byProgramUid().eq(programId)
                    .byOrganisationUnitUid().`in`(allAccessibleOrgUnits.toList())
                    .byStatus().`in`(listOf(
                        org.hisp.dhis.android.core.event.EventStatus.ACTIVE,
                        org.hisp.dhis.android.core.event.EventStatus.COMPLETED,
                        org.hisp.dhis.android.core.event.EventStatus.SCHEDULE,
                        org.hisp.dhis.android.core.event.EventStatus.OVERDUE
                    ))
                    .byDeleted().eq(false)
                    .blockingGet()

                Log.d(TAG, "Found ${events.size} events for program $programId (filtered by ${allAccessibleOrgUnits.size} org units including children, valid statuses, not deleted)")

                val programInstances = events.map { event ->
                    val orgUnit = d2.organisationUnitModule().organisationUnits()
                        .uid(event.organisationUnit())
                        .blockingGet()

                    val program = d2.programModule().programs()
                        .uid(programId)
                        .blockingGet()

                    com.ash.simpledataentry.domain.model.ProgramInstance.EventInstance(
                        id = event.uid(),
                        programId = programId,
                        programName = program?.displayName() ?: program?.name() ?: "Unknown Program",
                        organisationUnit = com.ash.simpledataentry.domain.model.OrganisationUnit(
                            id = orgUnit?.uid() ?: "",
                            name = orgUnit?.displayName() ?: "Unknown"
                        ),
                        lastUpdated = event.lastUpdated() ?: java.util.Date(),
                        state = when (event.status()) {
                            org.hisp.dhis.android.core.event.EventStatus.ACTIVE ->
                                com.ash.simpledataentry.domain.model.ProgramInstanceState.ACTIVE
                            org.hisp.dhis.android.core.event.EventStatus.COMPLETED ->
                                com.ash.simpledataentry.domain.model.ProgramInstanceState.COMPLETED
                            org.hisp.dhis.android.core.event.EventStatus.OVERDUE ->
                                com.ash.simpledataentry.domain.model.ProgramInstanceState.OVERDUE
                            org.hisp.dhis.android.core.event.EventStatus.SCHEDULE ->
                                com.ash.simpledataentry.domain.model.ProgramInstanceState.SCHEDULED
                            org.hisp.dhis.android.core.event.EventStatus.SKIPPED ->
                                com.ash.simpledataentry.domain.model.ProgramInstanceState.SKIPPED
                            else -> com.ash.simpledataentry.domain.model.ProgramInstanceState.ACTIVE
                        },
                        syncStatus = when (event.aggregatedSyncState()) {
                            org.hisp.dhis.android.core.common.State.SYNCED -> com.ash.simpledataentry.domain.model.SyncStatus.SYNCED
                            org.hisp.dhis.android.core.common.State.TO_UPDATE -> com.ash.simpledataentry.domain.model.SyncStatus.NOT_SYNCED
                            org.hisp.dhis.android.core.common.State.TO_POST -> com.ash.simpledataentry.domain.model.SyncStatus.NOT_SYNCED
                            else -> com.ash.simpledataentry.domain.model.SyncStatus.SYNCED
                        },
                        programStage = event.programStage() ?: "",
                        eventDate = event.eventDate(),
                        dueDate = event.dueDate(),
                        completedDate = event.completedDate(),
                        coordinates = event.geometry()?.let { geometry ->
                            // Convert DHIS2 geometry to coordinates if needed
                            null // TODO: Implement geometry conversion
                        }
                    )
                }
                emit(programInstances)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get event instances for program $programId", e)
                emit(emptyList())
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun createEventInstance(
        programId: String,
        programStageId: String,
        orgUnitId: String
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val eventUid = d2.eventModule().events()
                    .blockingAdd(
                        org.hisp.dhis.android.core.event.EventCreateProjection.builder()
                            .program(programId)
                            .programStage(programStageId)
                            .organisationUnit(orgUnitId)
                            .build()
                    )
                Result.success(eventUid)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create event instance", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun completeEvent(eventId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                d2.eventModule().events().uid(eventId)
                    .setStatus(org.hisp.dhis.android.core.event.EventStatus.COMPLETED)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to complete event", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun skipEvent(eventId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                d2.eventModule().events().uid(eventId)
                    .setStatus(org.hisp.dhis.android.core.event.EventStatus.SKIPPED)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to skip event", e)
                Result.failure(e)
            }
        }
    }
}