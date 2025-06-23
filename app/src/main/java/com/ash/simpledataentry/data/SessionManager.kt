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
import org.koin.core.context.stopKoin
import com.ash.simpledataentry.data.local.AppDatabase
import okhttp3.OkHttpClient
import okhttp3.Interceptor

@Singleton
class SessionManager @Inject constructor() {
    private var d2: D2? = null

    suspend fun initD2(context: Context) = withContext(Dispatchers.IO) {
        try {
            stopKoin()
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
                    .readTimeoutInSeconds(30)
                    .writeTimeoutInSeconds(30)
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

    suspend fun wipeAllData(context: Context) = withContext(Dispatchers.IO) {
        try {
            d2?.wipeModule()?.wipeEverything()
            val prefs = context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            Log.i("SessionManager", "All local data wiped successfully")
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
        // Hydrate datasets
        val datasets = d2Instance.dataSetModule().dataSets().blockingGet().map {
            com.ash.simpledataentry.data.local.DatasetEntity(
                id = it.uid(),
                name = it.displayName() ?: it.name() ?: "Unnamed Dataset",
                description = it.description() ?: "",
                periodType = it.periodType()?.name ?: "Monthly"
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
    }

}