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
                val config = D2Configuration.builder()
                    .context(context)
                    .appName("Simple Data Entry")
                    .appVersion("1.0")
                    .readTimeoutInSeconds(30)
                    .writeTimeoutInSeconds(30)
                    .build()
                d2 = D2Manager.blockingInstantiateD2(config)
                Log.d("SessionManager", "D2 initialized successfully")
            } catch (e: Exception) {
                Log.e("SessionManager", "D2 initialization failed", e)
                throw e
            }
        }
    }

    suspend fun login(context: Context, dhis2Config: Dhis2Config) = withContext(Dispatchers.IO) {
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



}