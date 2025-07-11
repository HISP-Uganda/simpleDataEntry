package com.ash.simpledataentry.data.repositoryImpl

import android.util.Log
import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.domain.model.Category
import com.ash.simpledataentry.domain.model.CategoryCombo
import com.ash.simpledataentry.domain.model.CategoryOption
import com.ash.simpledataentry.domain.model.DataElement
import com.ash.simpledataentry.domain.model.Dataset
import com.ash.simpledataentry.domain.model.DatasetSection
import com.ash.simpledataentry.domain.model.FormType
import com.ash.simpledataentry.domain.model.ValueType
import com.ash.simpledataentry.domain.repository.DatasetsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.dataset.DataSet
import org.hisp.dhis.android.core.period.PeriodType
import java.util.Date
import com.ash.simpledataentry.data.local.DatasetDao
import com.ash.simpledataentry.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DatasetsRepositoryImpl(
    private val sessionManager: SessionManager,
    private val datasetDao: DatasetDao,
    private val context: android.content.Context
) : DatasetsRepository {

    private val TAG = "DatasetsRepositoryImpl"
    private val d2 get() = sessionManager.getD2()!!
    /**
     * Fetches all datasets from Room first, then DHIS2 SDK if needed.
     * Emits the result as a Flow.
     */
    override fun getDatasets(): Flow<List<Dataset>> = flow {
        val local = withContext(Dispatchers.IO) { datasetDao.getAll() }
        if (local.isNotEmpty()) {
            emit(local.map { it.toDomainModel() })
            return@flow
        }
        // If Room is empty, try to fetch from SDK if online
        if (NetworkUtils.isNetworkAvailable(context)) {
            try {
                Log.d(TAG, "Fetching datasets from SDK...")
                val d2Instance = d2
                if (d2Instance == null) {
                    Log.e(TAG, "D2 instance is null")
                    throw Exception("D2 not initialized")
                }
                val datasets = d2Instance.dataSetModule()
                    .dataSets()
                    .blockingGet()
                    .map { dataSet ->
                        Log.d(TAG, "Processing dataset: ${dataSet.uid()}")
                        dataSet.toDomainModel()
                    }
                // Update Room
                withContext(Dispatchers.IO) {
                    datasetDao.clearAll()
                    datasetDao.insertAll(datasets.map { it.toEntity() })
                }
                emit(datasets)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching datasets", e)
                emit(emptyList())
            }
        } else {
            // Offline and Room is empty
            emit(emptyList())
        }
    }

    override suspend fun syncDatasets(): Result<Unit> {
        return try {
            Log.d(TAG, "Starting dataset sync...")
            d2.metadataModule().blockingDownload()
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
            val datasets = d2.dataSetModule()
                .dataSets()
                .blockingGet()
                .map { it.toDomainModel() }

            Log.d(TAG, "Found ${datasets.size} datasets after filtering")
            Result.success(datasets)
        } catch (e: Exception) {
            Log.e(TAG, "Error filtering datasets", e)
            Result.failure(e)
        }
    }

}

/**
 * Extension function converting the DHIS2 DataSet object into our domain Dataset model.
 */
fun DataSet.toDomainModel(): Dataset {
    return Dataset(
        id = uid(),
        name = displayName() ?: name() ?: "Unnamed Dataset",
        description = description() ?: "",
        periodType = when (periodType()?.name) {
            "Daily" -> PeriodType.Daily
            "Weekly" -> PeriodType.Weekly
            "Monthly" -> PeriodType.Monthly
            "Yearly" -> PeriodType.Yearly
            else -> PeriodType.Monthly // Default fallback, adjust as needed
        }

    )
}

// Extension functions for mapping
fun Dataset.toEntity(): com.ash.simpledataentry.data.local.DatasetEntity =
    com.ash.simpledataentry.data.local.DatasetEntity(
        id = id,
        name = name,
        description = description.toString(),
        periodType = periodType?.name ?: "Monthly"
    )

fun com.ash.simpledataentry.data.local.DatasetEntity.toDomainModel(): Dataset =
    Dataset(
        id = id,
        name = name,
        description = description,
        periodType = when (periodType) {
            "Daily" -> PeriodType.Daily
            "Weekly" -> PeriodType.Weekly
            "Monthly" -> PeriodType.Monthly
            "Yearly" -> PeriodType.Yearly
            else -> PeriodType.Monthly
        }
    )