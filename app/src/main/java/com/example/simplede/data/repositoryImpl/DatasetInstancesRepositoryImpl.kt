package com.example.simplede.data.repositoryImpl

import android.util.Log
import com.example.simplede.domain.model.*
import com.example.simplede.domain.repository.DatasetInstancesRepository
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.organisationunit.OrganisationUnit
import org.hisp.dhis.android.core.dataset.DataSetInstance

private const val TAG = "DatasetInstancesRepo"

class DatasetInstancesRepositoryImpl(private val d2: D2) : DatasetInstancesRepository {

    override suspend fun getDatasetMetadata(datasetId: String): DatasetMetadata {
        Log.d(TAG, "Fetching metadata for dataset: $datasetId")
        return try {
            val dataset = d2.dataSetModule().dataSets()
                .uid(datasetId)
                .blockingGet()
                ?: throw Exception("Dataset not found")

            Log.d(TAG, "Found dataset: ${dataset.displayName()}")
            
            DatasetMetadata(
                datasetId = dataset.uid(),
                name = dataset.displayName() ?: dataset.name() ?: "",
                description = dataset.description(),
                periodType = dataset.periodType()?.name ?: "",
                canCreateNew = true,
                lastSync = dataset.lastUpdated()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching dataset metadata", e)
            throw e
        }
    }

    override suspend fun getDatasetInstances(datasetId: String): List<DatasetInstance> {
        Log.d(TAG, "Fetching dataset instances for dataset: $datasetId")
        return try {
            // Get user's data capture org units
            Log.d(TAG, "Fetching user org units")
            val userOrgUnits = d2.organisationUnitModule().organisationUnits()
                .byOrganisationUnitScope(OrganisationUnit.Scope.SCOPE_DATA_CAPTURE)
                .blockingGet()
            
            Log.d(TAG, "Found ${userOrgUnits.size} org units")
            
            val userOrgUnitUid = userOrgUnits.firstOrNull()?.uid()
            if (userOrgUnitUid == null) {
                Log.e(TAG, "No organization unit found for user")
                return emptyList()
            }
            
            Log.d(TAG, "Using org unit: $userOrgUnitUid")


            // Get dataset instances
            val instances = d2.dataSetModule()
                .dataSetInstances()
                .byDataSetUid().eq(datasetId)
                .byOrganisationUnitUid().eq(userOrgUnitUid)
                .blockingGet()

            Log.d(TAG, "Found ${instances.size} instances for dataset")
            
            instances.map { instance ->
                Log.d(TAG, "Processing instance: ${instance.id()}")
                DatasetInstance(
                    id = instance.id().toString(),
                    datasetId = instance.dataSetUid(),
                    period = Period(id = instance.period()),
                    organisationUnit = OrganisationUnit(
                        id = instance.organisationUnitUid(),
                        name = instance.organisationUnitDisplayName() ?: ""
                    ),
                    attributeOptionCombo = instance.attributeOptionComboDisplayName() ?: "",
                    state = when {
                        instance.completed() == true -> DatasetInstanceState.COMPLETE
                        else -> DatasetInstanceState.OPEN
                    }
                )
            }.also {
                Log.d(TAG, "Returning ${it.size} processed instances")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching dataset instances", e)
            emptyList()
        }
    }

    override suspend fun syncDatasetInstances() {
        Log.d(TAG, "Starting dataset instances sync")
        try {
            d2.dataSetModule().dataSetInstances().blockingGet()
            Log.d(TAG, "Sync completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during sync", e)
        }
    }
}