package com.ash.simpledataentry.data.repositoryImpl

import android.util.Log
import com.ash.simpledataentry.domain.model.DatasetInstance
import com.ash.simpledataentry.domain.model.DatasetInstanceState
import com.ash.simpledataentry.domain.model.Period
import com.ash.simpledataentry.domain.repository.DatasetInstancesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        Log.d(TAG, "Starting dataset instances sync")
        withContext(Dispatchers.IO) {
            try {
                // Upload local changes to dataset instances
               // d2.dataSetModule().dataSetInstances().blockingUpload()
                // Download latest dataset instances from server
                d2.dataSetModule().dataSetInstances().blockingGet()
                Log.d(TAG, "Sync completed successfully")
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
                        // Sync tracker enrollments and events
                        // Use the synchronization calls for tracker data
                        d2.trackedEntityModule().trackedEntityInstances().blockingGet()
                        d2.eventModule().events().blockingGet()
                    }
                    com.ash.simpledataentry.domain.model.ProgramType.EVENT -> {
                        // Sync events
                        d2.eventModule().events().blockingGet()
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
            withContext(Dispatchers.IO) {
                try {
                    val enrollments = d2.enrollmentModule().enrollments()
                        .byProgram().eq(programId)
                        .blockingGet()

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
            }
        }

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
            withContext(Dispatchers.IO) {
                try {
                    val events = d2.eventModule().events()
                        .byProgramUid().eq(programId)
                        .blockingGet()

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
            }
        }

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