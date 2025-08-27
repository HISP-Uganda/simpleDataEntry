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
}