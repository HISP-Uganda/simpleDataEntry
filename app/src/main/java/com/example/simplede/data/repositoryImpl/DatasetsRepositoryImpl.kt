package com.example.simplede.data.repositoryImpl

import android.util.Log
import com.example.simplede.data.SessionManager
import com.example.simplede.domain.model.*
import com.example.simplede.domain.repository.DatasetRepository
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.dataset.DataSet

class DatasetsRepositoryImpl : DatasetRepository {
    private val TAG = "DatasetsRepositoryImpl"
    private val d2: D2? get() = SessionManager.getD2()

    override suspend fun getDatasets(): Result<List<Dataset>> {
        return try {
            Log.d(TAG, "Fetching datasets...")
            val d2Instance = d2
            if (d2Instance == null) {
                Log.e(TAG, "D2 instance is null")
                return Result.failure(Exception("D2 not initialized"))
            }

            val datasets = d2Instance.dataSetModule()
                .dataSets()
                .blockingGet()
                .map { dataSet ->
                    Log.d(TAG, "Processing dataset: ${dataSet.uid()}")
                    dataSet.toDomainModel()
                }

            Log.d(TAG, "Fetched ${datasets.size} datasets")
            Result.success(datasets)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching datasets", e)
            Result.failure(e)
        }
    }

    override suspend fun syncDatasets(): Result<Unit> {
        return try {
            Log.d(TAG, "Starting dataset sync...")
            d2?.metadataModule()?.blockingDownload()
            Log.d(TAG, "Dataset sync completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Dataset sync failed", e)
            Result.failure(e)
        }
    }

    override suspend fun filterDatasets(period: String?, syncStatus: Boolean?): Result<List<Dataset>> {
        return try {
            Log.d(TAG, "Filtering datasets - period: $period, syncStatus: $syncStatus")
            val datasets = d2?.dataSetModule()
                ?.dataSets()
                ?.blockingGet()
                ?.map { it.toDomainModel() }
                ?: emptyList()
            
            Log.d(TAG, "Found ${datasets.size} datasets after filtering")
            Result.success(datasets)
        } catch (e: Exception) {
            Log.e(TAG, "Error filtering datasets", e)
            Result.failure(e)
        }
    }

    private fun DataSet.toDomainModel(): Dataset {
        val formType = when {
            style()?.toString() == "SECTION" -> FormType.SECTION

            else -> FormType.DEFAULT
        }

        val name = displayName() ?: name() ?: ""
        Log.d(TAG, "Converting dataset: $name with form type: $formType")

        return Dataset(
            id = uid(),
            name = name,
            description = description() ?: "No description",
            //categoryCombo = CategoryCombo(
                //id = categoryCombo()?.uid() ?: "",
                //name = categoryCombo()?.displayName() ?: "",
                //categories = emptyList()
            //),
            formType = formType,
            sections = emptyList(),
            dataElements = emptyList()
        )
    }
}