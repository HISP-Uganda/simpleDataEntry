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
                        // CRITICAL FIX: Check if user is properly authenticated before attempting API calls
                        val isLoggedIn = try {
                            d2.userModule().isLogged().blockingGet()
                        } catch (e: Exception) {
                            false
                        }

                        if (!isLoggedIn) {
                            Log.e(TAG, "User not properly authenticated - cannot download tracker data. Offline mode detected.")
                            return@withContext Result.failure(Exception("Cannot download tracker data in offline mode. Please login online first."))
                        }

                        val user = try {
                            d2.userModule().user().blockingGet()
                        } catch (e: Exception) {
                            null
                        }

                        if (user?.uid() == null) {
                            Log.e(TAG, "User UID is null - authentication state invalid")
                            return@withContext Result.failure(Exception("Invalid authentication state - user UID is null"))
                        }

                        Log.d(TAG, "User authenticated (${user.uid()}) - proceeding with tracker download for program $programId")

                        // Sync tracker enrollments and events - download from server
                        Log.d(TAG, "Downloading tracker data from server for program $programId")

                        // First ensure metadata is up to date
                        d2.metadataModule().blockingDownload()

                        // Get ALL user's org units (not just data capture scope)
                        // to ensure we can download from org unit FvewOonC8lS where the data exists
                        val allUserOrgUnits = d2.organisationUnitModule().organisationUnits()
                            .blockingGet()

                        Log.d(TAG, "Found ${allUserOrgUnits.size} total accessible org units for tracker download")

                        if (allUserOrgUnits.isEmpty()) {
                            Log.e(TAG, "No organization units found for user - cannot download tracker data")
                            return@withContext Result.failure(Exception("No organization units found for user"))
                        }

                        val allUserOrgUnitUids = allUserOrgUnits.map { it.uid() }
                        Log.d(TAG, "Using ALL accessible org units for tracker download: ${allUserOrgUnitUids.joinToString(", ")}")

                        // CRITICAL DEBUG: Check if user has access to org unit FvewOonC8lS where the tracker data exists
                        Log.d(TAG, "CRITICAL DEBUG: Does user have access to FvewOonC8lS? ${allUserOrgUnitUids.contains("FvewOonC8lS")}")

                        // Download tracker data for specific program using EXACT official Android Capture app pattern
                        Log.d(TAG, "Using EXACT official Android Capture app pattern - downloading for ALL accessible org units")

                        // REMOVED: Pre-check validation that was blocking downloads
                        // The pre-check was querying local data before downloading from server,
                        // creating a catch-22 where no data exists locally because it was never downloaded

                        // CRITICAL DIAGNOSTIC: Compare direct API vs SDK behavior
                        Log.d(TAG, "PERMISSION INVESTIGATION: Comparing direct API vs SDK responses...")

                        try {
                            // Get the authenticated D2 configuration details
                            val serverUrl = d2.systemInfoModule().systemInfo().blockingGet()?.contextPath()
                            val serverVersion = d2.systemInfoModule().systemInfo().blockingGet()?.version()
                            Log.d(TAG, "DIAGNOSTIC: Server URL: $serverUrl, Version: $serverVersion")

                            // Compare what's available via query vs what actually downloads
                            // Check enrollments via direct query (similar to what XML API showed)
                            val directEnrollmentQuery = d2.enrollmentModule().enrollments()
                                .byProgram().eq(programId)
                                .byOrganisationUnit().eq("FvewOonC8lS")
                                .blockingGet()

                            Log.d(TAG, "DIAGNOSTIC: Direct enrollment query found ${directEnrollmentQuery.size} enrollments in program $programId, org unit FvewOonC8lS")

                            // Check what TEIs are available via query
                            val directTEIQuery = d2.trackedEntityModule().trackedEntityInstances()
                                .byProgramUids(listOf(programId))
                                .byOrganisationUnitUid().eq("FvewOonC8lS")
                                .blockingGet()

                            Log.d(TAG, "DIAGNOSTIC: Direct TEI query found ${directTEIQuery.size} TEIs in program $programId, org unit FvewOonC8lS")

                            // Log some sample data if found
                            directTEIQuery.take(3).forEach { tei ->
                                Log.d(TAG, "DIAGNOSTIC: Found TEI: ${tei.uid()} - orgUnit: ${tei.organisationUnit()}")
                            }

                        } catch (e: Exception) {
                            Log.w(TAG, "DIAGNOSTIC: Direct query failed: ${e.message}")
                            Log.e(TAG, "DIAGNOSTIC: Direct query exception details", e)
                        }

                        // CRITICAL FIX: Use multiple download strategies to ensure we get the tracker data
                        Log.d(TAG, "Attempting tracker download using multiple strategies...")

                        try {
                            // Strategy 1: Download by program UID (original approach)
                            Log.d(TAG, "Strategy 1: Downloading TEIs by program UID")
                            Log.d(TAG, "DIAGNOSTIC: Request details - Program: $programId, User OrgUnits: ${allUserOrgUnits.map { it.uid() }}")

                            // Check server capabilities and permissions before download
                            val serverInfo = d2.systemInfoModule().systemInfo().blockingGet()
                            Log.d(TAG, "DIAGNOSTIC: Server version: ${serverInfo?.version()}, Server date: ${serverInfo?.serverDate()}")

                            // Log user authorities and permissions
                            val userAuth = d2.userModule().authorities().blockingGet()
                            Log.d(TAG, "DIAGNOSTIC: User authorities count: ${userAuth.size}")

                            val downloader = d2.trackedEntityModule().trackedEntityInstanceDownloader()
                                .byProgramUid(programId)

                            // Log the exact query that will be executed
                            Log.d(TAG, "DIAGNOSTIC: Executing Strategy 1 download with program filter: $programId")
                            Log.d(TAG, "DIAGNOSTIC: Strategy 1 targeting org units: ${allUserOrgUnits.map { "${it.uid()}-${it.displayName()}" }}")
                            Log.d(TAG, "DIAGNOSTIC: Strategy 1 specifically looking for org unit FvewOonC8lS in user's access list: ${allUserOrgUnits.any { it.uid() == "FvewOonC8lS" }}")

                            val result = downloader.download()
                            Log.d(TAG, "DIAGNOSTIC: Strategy 1 download result: $result")

                            // CRITICAL: Check immediately after this specific download
                            val strategy1Results = d2.trackedEntityModule().trackedEntityInstances()
                                .byProgramUids(listOf(programId))
                                .blockingGet()
                            Log.d(TAG, "DIAGNOSTIC: Immediately after Strategy 1, found ${strategy1Results.size} TEIs for program $programId")
                            Log.d(TAG, "Strategy 1 completed")
                        } catch (e: Exception) {
                            Log.w(TAG, "Strategy 1 failed: ${e.message}")
                            Log.e(TAG, "DIAGNOSTIC: Strategy 1 exception details", e)
                        }

                        try {
                            // Strategy 2: Download all TEIs without program filter
                            Log.d(TAG, "Strategy 2: Downloading all TEIs (no program filter)")
                            Log.d(TAG, "DIAGNOSTIC: Strategy 2 - downloading ALL TEIs without program restriction")

                            // Log current TEI count before download
                            val beforeCount = d2.trackedEntityModule().trackedEntityInstances().blockingCount()
                            Log.d(TAG, "DIAGNOSTIC: TEIs in database before Strategy 2: $beforeCount")

                            val downloader2 = d2.trackedEntityModule().trackedEntityInstanceDownloader()
                            val result2 = downloader2.download()

                            Log.d(TAG, "DIAGNOSTIC: Strategy 2 download result: $result2")

                            // Log current TEI count after download
                            val afterCount = d2.trackedEntityModule().trackedEntityInstances().blockingCount()
                            Log.d(TAG, "DIAGNOSTIC: TEIs in database after Strategy 2: $afterCount (difference: ${afterCount - beforeCount})")

                            Log.d(TAG, "Strategy 2 completed")
                        } catch (e: Exception) {
                            Log.w(TAG, "Strategy 2 failed: ${e.message}")
                            Log.e(TAG, "DIAGNOSTIC: Strategy 2 exception details", e)
                        }

                        try {
                            // Strategy 3: Force a broader download to capture any missed dependencies
                            Log.d(TAG, "Strategy 3: Downloading all available tracker data")
                            Log.d(TAG, "DIAGNOSTIC: Strategy 3 - unlimited scope (no orgunit/program limits)")

                            // Log current state before unlimited download
                            val beforeCount3 = d2.trackedEntityModule().trackedEntityInstances().blockingCount()
                            val beforeEnrollments = d2.enrollmentModule().enrollments().blockingCount()
                            Log.d(TAG, "DIAGNOSTIC: Before Strategy 3 - TEIs: $beforeCount3, Enrollments: $beforeEnrollments")

                            // Try to get more diagnostic info about what's available on server
                            val programs = d2.programModule().programs().blockingGet()
                            Log.d(TAG, "DIAGNOSTIC: Available programs: ${programs.map { "${it.uid()}-${it.displayName()}" }}")

                            val orgUnits = d2.organisationUnitModule().organisationUnits().blockingGet()
                            Log.d(TAG, "DIAGNOSTIC: User's org units: ${orgUnits.map { "${it.uid()}-${it.displayName()}" }}")

                            val downloader3 = d2.trackedEntityModule().trackedEntityInstanceDownloader()
                                .limitByOrgunit(false)  // Don't limit by org unit
                                .limitByProgram(false)  // Don't limit by program

                            val result3 = downloader3.download()
                            Log.d(TAG, "DIAGNOSTIC: Strategy 3 download result: $result3")

                            // Log final counts after unlimited download
                            val afterCount3 = d2.trackedEntityModule().trackedEntityInstances().blockingCount()
                            val afterEnrollments3 = d2.enrollmentModule().enrollments().blockingCount()
                            Log.d(TAG, "DIAGNOSTIC: After Strategy 3 - TEIs: $afterCount3 (diff: ${afterCount3 - beforeCount3}), Enrollments: $afterEnrollments3 (diff: ${afterEnrollments3 - beforeEnrollments})")

                            Log.d(TAG, "Strategy 3 completed")
                        } catch (e: Exception) {
                            Log.w(TAG, "Strategy 3 failed: ${e.message}")
                            Log.e(TAG, "DIAGNOSTIC: Strategy 3 exception details", e)
                        }

                        Log.d(TAG, "All download strategies completed")

                        // Add small delay to allow database transaction to complete
                        kotlinx.coroutines.delay(1000)

                        Log.d(TAG, "COMPREHENSIVE DIAGNOSTIC: Starting post-download analysis...")

                        // CRITICAL DIAGNOSTIC: Check exact data state after all download attempts
                        val finalTEICount = d2.trackedEntityModule().trackedEntityInstances().blockingCount()
                        val finalEnrollmentCount = d2.enrollmentModule().enrollments().blockingCount()
                        val finalEventCount = d2.eventModule().events().blockingCount()

                        Log.d(TAG, "DIAGNOSTIC SUMMARY: Final database state - TEIs: $finalTEICount, Enrollments: $finalEnrollmentCount, Events: $finalEventCount")

                        // Check specifically for the program and org unit we know has data
                        val programSpecificTEIs = d2.trackedEntityModule().trackedEntityInstances()
                            .byProgramUids(listOf(programId))
                            .blockingGet()

                        val orgUnitSpecificTEIs = d2.trackedEntityModule().trackedEntityInstances()
                            .byOrganisationUnitUid().eq("FvewOonC8lS") // The org unit with 23 enrollments
                            .blockingGet()

                        Log.d(TAG, "DIAGNOSTIC: Program $programId specific TEIs: ${programSpecificTEIs.size}")
                        Log.d(TAG, "DIAGNOSTIC: OrgUnit FvewOonC8lS specific TEIs: ${orgUnitSpecificTEIs.size}")

                        // Check if there's a mismatch between what we requested vs what's stored
                        val programEnrollments = d2.enrollmentModule().enrollments()
                            .byProgram().eq(programId)
                            .blockingGet()

                        val orgUnitEnrollments = d2.enrollmentModule().enrollments()
                            .byOrganisationUnit().eq("FvewOonC8lS")
                            .blockingGet()

                        Log.d(TAG, "DIAGNOSTIC: Program $programId enrollments: ${programEnrollments.size}")
                        Log.d(TAG, "DIAGNOSTIC: OrgUnit FvewOonC8lS enrollments: ${orgUnitEnrollments.size}")

                        // Check if there are any access/permission restrictions being applied
                        try {
                            val userOrgUnits = d2.organisationUnitModule().organisationUnits()
                                .byOrganisationUnitScope(OrganisationUnit.Scope.SCOPE_DATA_CAPTURE)
                                .blockingGet()
                            val hasTargetOrgUnit = userOrgUnits.any { it.uid() == "FvewOonC8lS" }
                            Log.d(TAG, "DIAGNOSTIC: User has data capture access to target org unit FvewOonC8lS: $hasTargetOrgUnit")

                            // Check if user has broader org unit access
                            val allUserOrgUnits = d2.organisationUnitModule().organisationUnits().blockingGet()
                            val hasTargetOrgUnitAny = allUserOrgUnits.any { it.uid() == "FvewOonC8lS" }
                            Log.d(TAG, "DIAGNOSTIC: User has any access to target org unit FvewOonC8lS: $hasTargetOrgUnitAny")

                            val userPrograms = d2.programModule().programs().blockingGet()
                            val hasTargetProgram = userPrograms.any { it.uid() == programId }
                            Log.d(TAG, "DIAGNOSTIC: User has access to target program $programId: $hasTargetProgram")
                        } catch (e: Exception) {
                            Log.w(TAG, "DIAGNOSTIC: Could not check user org unit/program access: ${e.message}")
                        }

                        Log.d(TAG, "Tracker data download completed for program $programId")

                        // Debug: Check what TrackedEntityInstances were downloaded (primary objects)
                        val downloadedTEIs = d2.trackedEntityModule().trackedEntityInstances()
                            .byProgramUids(listOf(programId))
                            .blockingGet()
                        Log.d(TAG, "DEBUG: Immediately after download found ${downloadedTEIs.size} TrackedEntityInstances")

                        // Debug: Check enrollments (child objects of TEIs)
                        val immediateCheck = d2.enrollmentModule().enrollments()
                            .byProgram().eq(programId)
                            .blockingGet()
                        Log.d(TAG, "DEBUG: Immediately after download found ${immediateCheck.size} enrollments")

                        // Debug: Check if any TEIs exist at all for this program
                        val allTEIs = d2.trackedEntityModule().trackedEntityInstances()
                            .blockingGet()
                        Log.d(TAG, "DEBUG: Total TEIs in local database: ${allTEIs.size}")

                        // Debug: Show TEI org units if any exist
                        if (downloadedTEIs.isNotEmpty()) {
                            downloadedTEIs.forEach { tei ->
                                Log.d(TAG, "DEBUG: TEI ${tei.uid()} - orgUnit: ${tei.organisationUnit()}")
                            }
                        } else {
                            Log.w(TAG, "DEBUG: No TEIs downloaded for program $programId")
                        }
                    }
                    com.ash.simpledataentry.domain.model.ProgramType.EVENT -> {
                        // Sync events - download from server
                        Log.d(TAG, "Downloading event data from server for program $programId")

                        // First ensure metadata is up to date
                        d2.metadataModule().blockingDownload()

                        // Then download actual event data for this specific program
                        d2.eventModule().eventDownloader()
                            .byProgramUid(programId)
                            .download()

                        Log.d(TAG, "Event data download completed for program $programId")
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