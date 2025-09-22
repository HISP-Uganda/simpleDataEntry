package com.ash.simpledataentry.data

import android.content.Context
import android.util.Log
import com.ash.simpledataentry.domain.model.Dhis2Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.D2Configuration
import org.hisp.dhis.android.core.D2Manager
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit
import com.ash.simpledataentry.data.local.AppDatabase
import com.ash.simpledataentry.presentation.core.NavigationProgress
import com.ash.simpledataentry.presentation.core.LoadingPhase
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import org.koin.core.context.GlobalContext

@Singleton
class SessionManager @Inject constructor() {
    private var d2: D2? = null

    suspend fun initD2(context: Context) = withContext(Dispatchers.IO) {
        // Stop any existing Koin instance that DHIS2 SDK might have started
        try {
            GlobalContext.stopKoin()
        } catch (e: Exception) {
            // Ignore if Koin wasn't started
        }
        
        if (d2 == null) {
            try {
                // Add OkHttp logging interceptor
                val loggingInterceptor = Interceptor { chain ->
                    val request = chain.request()
                    Log.d("OkHttp", "Request: ${request.method} ${request.url}")
                    val response = chain.proceed(request)
                    Log.d("OkHttp", "Response: ${response.code} ${response.message}")
                    response
                }
                val config = D2Configuration.builder()
                    .context(context)
                    .appName("Simple Data Entry")
                    .appVersion("1.0")
                    .readTimeoutInSeconds(180)  // 3 minutes for downloads
                    .writeTimeoutInSeconds(600) // 10 minutes for uploads
                    .connectTimeoutInSeconds(60) // 1 minute for connection
                    .interceptors(listOf(loggingInterceptor))
                    .build()
                d2 = D2Manager.blockingInstantiateD2(config)
                Log.d("SessionManager", "D2 initialized successfully with OkHttp logging")
            } catch (e: Exception) {
                Log.e("SessionManager", "D2 initialization failed", e)
                throw e
            }
        }
    }

    suspend fun login(context: Context, dhis2Config: Dhis2Config, db: AppDatabase) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
        val lastUser = prefs.getString("username", null)
        val lastServer = prefs.getString("serverUrl", null)
        val isDifferentUser = lastUser != dhis2Config.username || lastServer != dhis2Config.serverUrl

        if (isDifferentUser) {
            wipeAllData(context)
        }

        // Always re-instantiate D2 before login to ensure fresh state
        d2 = null
        initD2(context)

        // Log out if already logged in to avoid D2Error
        if (d2?.userModule()?.isLogged()?.blockingGet() == true) {
            d2?.userModule()?.blockingLogOut()
        }

        try {
            d2?.userModule()?.blockingLogIn(
                dhis2Config.username,
                dhis2Config.password,
                dhis2Config.serverUrl
            ) ?: throw IllegalStateException("D2 not initialized")

            prefs.edit {
                putString("username", dhis2Config.username)
                putString("serverUrl", dhis2Config.serverUrl)
            }

            downloadMetadata()
            downloadAggregateData()
            hydrateRoomFromSdk(context, db)

            Log.i("SessionManager", "Login successful for ${dhis2Config.username}")
        } catch (e: Exception) {
            Log.e("SessionManager", "Login failed", e)
            throw e
        }
    }

    /**
     * Enhanced login with progress tracking
     */
    suspend fun loginWithProgress(
        context: Context,
        dhis2Config: Dhis2Config,
        db: AppDatabase,
        onProgress: (NavigationProgress) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            // Step 1: Initialize (0-10%)
            onProgress(NavigationProgress(
                phase = LoadingPhase.INITIALIZING,
                overallPercentage = 5,
                phaseTitle = LoadingPhase.INITIALIZING.title,
                phaseDetail = "Setting up connection..."
            ))

            val prefs = context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
            val lastUser = prefs.getString("username", null)
            val lastServer = prefs.getString("serverUrl", null)
            val isDifferentUser = lastUser != dhis2Config.username || lastServer != dhis2Config.serverUrl

            if (isDifferentUser) {
                wipeAllData(context)
            }

            // Always re-instantiate D2 before login to ensure fresh state
            d2 = null
            initD2(context)

            // Step 2: Authentication (10-30%)
            onProgress(NavigationProgress(
                phase = LoadingPhase.AUTHENTICATING,
                overallPercentage = 15,
                phaseTitle = LoadingPhase.AUTHENTICATING.title,
                phaseDetail = "Connecting to server..."
            ))

            // Log out if already logged in to avoid D2Error
            if (d2?.userModule()?.isLogged()?.blockingGet() == true) {
                d2?.userModule()?.blockingLogOut()
            }

            d2?.userModule()?.blockingLogIn(
                dhis2Config.username,
                dhis2Config.password,
                dhis2Config.serverUrl
            ) ?: throw IllegalStateException("D2 not initialized")

            onProgress(NavigationProgress(
                phase = LoadingPhase.AUTHENTICATING,
                overallPercentage = 25,
                phaseTitle = LoadingPhase.AUTHENTICATING.title,
                phaseDetail = "Authentication successful"
            ))

            prefs.edit {
                putString("username", dhis2Config.username)
                putString("serverUrl", dhis2Config.serverUrl)
            }

            // Step 3: Download Metadata (30-60%)
            onProgress(NavigationProgress(
                phase = LoadingPhase.DOWNLOADING_METADATA,
                overallPercentage = 35,
                phaseTitle = LoadingPhase.DOWNLOADING_METADATA.title,
                phaseDetail = "Downloading configuration..."
            ))

            downloadMetadata()

            onProgress(NavigationProgress(
                phase = LoadingPhase.DOWNLOADING_METADATA,
                overallPercentage = 55,
                phaseTitle = LoadingPhase.DOWNLOADING_METADATA.title,
                phaseDetail = "Metadata downloaded successfully"
            ))

            // Step 4: Download Data (60-80%)
            onProgress(NavigationProgress(
                phase = LoadingPhase.DOWNLOADING_DATA,
                overallPercentage = 65,
                phaseTitle = LoadingPhase.DOWNLOADING_DATA.title,
                phaseDetail = "Downloading your data..."
            ))

            downloadAggregateData()

            onProgress(NavigationProgress(
                phase = LoadingPhase.DOWNLOADING_DATA,
                overallPercentage = 75,
                phaseTitle = LoadingPhase.DOWNLOADING_DATA.title,
                phaseDetail = "Data downloaded successfully"
            ))

            // Step 5: Database Preparation (80-95%)
            onProgress(NavigationProgress(
                phase = LoadingPhase.PREPARING_DATABASE,
                overallPercentage = 85,
                phaseTitle = LoadingPhase.PREPARING_DATABASE.title,
                phaseDetail = "Preparing local database..."
            ))

            hydrateRoomFromSdk(context, db)

            onProgress(NavigationProgress(
                phase = LoadingPhase.PREPARING_DATABASE,
                overallPercentage = 90,
                phaseTitle = LoadingPhase.PREPARING_DATABASE.title,
                phaseDetail = "Database setup complete"
            ))

            // Step 6: Finalization (95-100%)
            onProgress(NavigationProgress(
                phase = LoadingPhase.FINALIZING,
                overallPercentage = 98,
                phaseTitle = LoadingPhase.FINALIZING.title,
                phaseDetail = "Login complete!"
            ))

            Log.i("SessionManager", "Enhanced login successful for ${dhis2Config.username}")

            onProgress(NavigationProgress(
                phase = LoadingPhase.FINALIZING,
                overallPercentage = 100,
                phaseTitle = "Ready",
                phaseDetail = "Welcome to DHIS2 Data Entry!"
            ))

        } catch (e: Exception) {
            Log.e("SessionManager", "Enhanced login failed", e)
            onProgress(NavigationProgress.error(e.message ?: "Login failed"))
            throw e
        }
    }

    suspend fun wipeAllData(context: Context) = withContext(Dispatchers.IO) {
        try {
            // Clear SharedPreferences first (always safe)
            val prefs = context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            
            // Only wipe DHIS2 data if we have an authenticated D2 instance
            d2?.let { d2Instance ->
                try {
                    d2Instance.wipeModule()?.wipeEverything()
                    Log.i("SessionManager", "DHIS2 data wiped successfully")
                } catch (e: Exception) {
                    Log.w("SessionManager", "Failed to wipe DHIS2 data (might not be logged in): ${e.message}")
                    // Continue anyway - we'll reinitialize
                }
            }
            
            Log.i("SessionManager", "Local data cleared successfully")
            // Re-instantiate D2 after wipe
            d2 = null
            initD2(context)
        } catch (e: Exception) {
            Log.e("SessionManager", "Failed to wipe all data", e)
        }
    }

    fun isSessionActive(): Boolean {
        return d2?.userModule()?.isLogged()?.blockingGet() ?: false
    }

    fun logout() {
        try {
            d2?.userModule()?.blockingLogOut()
        } catch (e: Exception) {
            Log.e("SessionManager", "Logout error: ${e.message}", e)
        }
    }

    fun getD2(): D2? = d2


    fun downloadMetadata() : Unit{

        return d2?.metadataModule()!!.blockingDownload()

    }


    fun downloadAggregateData() : Unit{

        return d2?.aggregatedModule()!!.data().blockingDownload()

    }

    suspend fun hydrateRoomFromSdk(context: Context, db: AppDatabase) = withContext(Dispatchers.IO) {
        val d2Instance = d2 ?: return@withContext
        // Hydrate datasets with style information
        val datasets = d2Instance.dataSetModule().dataSets().blockingGet().map {
            val datasetStyle = it.style()
            Log.d("SessionManager", "Dataset ${it.uid()}: style=${datasetStyle?.icon()}, color=${datasetStyle?.color()}")

            com.ash.simpledataentry.data.local.DatasetEntity(
                id = it.uid(),
                name = it.displayName() ?: it.name() ?: "Unnamed Dataset",
                description = it.description() ?: "",
                periodType = it.periodType()?.name ?: "Monthly",
                styleIcon = datasetStyle?.icon(),
                styleColor = datasetStyle?.color()
            )
        }
        db.datasetDao().clearAll()
        db.datasetDao().insertAll(datasets)
        // Hydrate data elements
        val dataElements = d2Instance.dataElementModule().dataElements().blockingGet().map {
            com.ash.simpledataentry.data.local.DataElementEntity(
                id = it.uid(),
                name = it.displayName() ?: it.name() ?: "Unnamed DataElement",
                valueType = it.valueType()?.name ?: "TEXT",
                categoryComboId = it.categoryComboUid(),
                description = it.description()
            )
        }
        db.dataElementDao().clearAll()
        db.dataElementDao().insertAll(dataElements)
        // Hydrate category combos
        val categoryCombos = d2Instance.categoryModule().categoryCombos().blockingGet().map {
            com.ash.simpledataentry.data.local.CategoryComboEntity(
                id = it.uid(),
                name = it.displayName() ?: it.name() ?: "Unnamed CategoryCombo"
            )
        }
        db.categoryComboDao().clearAll()
        db.categoryComboDao().insertAll(categoryCombos)
        // Hydrate category option combos
        val categoryOptionCombos = d2Instance.categoryModule().categoryOptionCombos().blockingGet().map {
            com.ash.simpledataentry.data.local.CategoryOptionComboEntity(
                id = it.uid(),
                name = it.displayName() ?: it.uid(),
                categoryComboId = it.categoryCombo()?.uid() ?: "",
                optionUids = it.categoryOptions()?.joinToString(",") { opt -> opt.uid() } ?: ""
            )
        }
        db.categoryOptionComboDao().clearAll()
        db.categoryOptionComboDao().insertAll(categoryOptionCombos)
        // Hydrate organisation units
        val orgUnits = d2Instance.organisationUnitModule().organisationUnits().blockingGet().map {
            com.ash.simpledataentry.data.local.OrganisationUnitEntity(
                id = it.uid(),
                name = it.displayName() ?: it.name() ?: "Unnamed OrgUnit",
                parentId = it.parent()?.uid()
            )
        }
        db.organisationUnitDao().clearAll()
        db.organisationUnitDao().insertAll(orgUnits)


        // Hydrate data values from DHIS2 SDK to Room database
        Log.d("SessionManager", "Loading data values from DHIS2 SDK...")
        try {
            // First, create a mapping of data element UIDs to dataset UIDs
            val dataElementToDatasetMap = mutableMapOf<String, String>()
            d2Instance.dataSetModule().dataSets().blockingGet().forEach { dataset ->
                dataset.dataSetElements()?.forEach { dataSetElement ->
                    dataElementToDatasetMap[dataSetElement.dataElement().uid()] = dataset.uid()
                }
            }
            Log.d("SessionManager", "Created mapping for ${dataElementToDatasetMap.size} data elements to datasets")

            // Use regular data values for now until we understand the aggregated module interface
            val regularDataValues = d2Instance.dataValueModule().dataValues().blockingGet()
            Log.d("SessionManager", "Using regular data values (${regularDataValues.size})")

            val dataValuesToUse = regularDataValues

            val dataValues = dataValuesToUse.mapIndexed { index, dataValue ->
                val dataElementUid = dataValue.dataElement() ?: ""
                val datasetId = dataElementToDatasetMap[dataElementUid] ?: ""
                val period = dataValue.period() ?: ""
                val orgUnit = dataValue.organisationUnit() ?: ""
                val attributeOptionCombo = dataValue.attributeOptionCombo() ?: ""
                val categoryOptionCombo = dataValue.categoryOptionCombo() ?: ""
                val value = dataValue.value()

                if (index < 10) { // Log first 10 for debugging
                    Log.d("SessionManager", "Storing DataValue $index: datasetId='$datasetId', period='$period', orgUnit='$orgUnit', attributeOptionCombo='$attributeOptionCombo', dataElement='$dataElementUid', categoryOptionCombo='$categoryOptionCombo', value='$value'")
                }

                com.ash.simpledataentry.data.local.DataValueEntity(
                    datasetId = datasetId,
                    period = period,
                    orgUnit = orgUnit,
                    attributeOptionCombo = attributeOptionCombo,
                    dataElement = dataElementUid,
                    categoryOptionCombo = categoryOptionCombo,
                    value = value,
                    comment = dataValue.comment(),
                    lastModified = dataValue.lastUpdated()?.time ?: System.currentTimeMillis()
                )
            }
            db.dataValueDao().deleteAllDataValues()
            db.dataValueDao().insertAll(dataValues)
            Log.d("SessionManager", "Loaded ${dataValues.size} data values into Room database")
        } catch (e: Exception) {
            Log.e("SessionManager", "Failed to load data values: ${e.message}", e)
        }
    }

}