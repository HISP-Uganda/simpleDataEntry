package com.example.simplede.data

import android.content.Context
import android.util.Log
import com.example.simplede.domain.model.Dhis2Config
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.D2Configuration
import org.hisp.dhis.android.core.D2Manager

object SessionManager {
    private var d2: D2? = null
    private var currentSession: Dhis2Config? = null

    fun initD2(context: Context) {
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

            } catch (e: Exception) {
                Log.e("SessionManager", "D2 initialization failed", e)
                throw e
            }
        }
    }

    fun login(config: Dhis2Config): Result<Unit> {


        //d2?.aggregatedModule()?.data()?.blockingDownload()



        return try {
            if (isSessionActive()) {
                Log.d("SessionManager", "Session already active")
                return Result.success(Unit)
            }

            d2?.userModule()?.blockingLogIn(
                config.username,
                config.password,
                config.serverUrl
            ) ?: throw IllegalStateException("D2 not initialized")

            currentSession = config
            Log.i("SessionManager", "Login successful for ${config.username}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SessionManager", "Login failed", e)
            Result.failure(e)
        }
    }

    fun isSessionActive(): Boolean {
        return d2?.userModule()?.isLogged()?.blockingGet() ?: false
    }

    fun logout() {
        try {
            d2?.userModule()?.blockingLogOut()
            currentSession = null
        } catch (e: Exception) {
            Log.e("SessionManager", "Logout error: ${e.message}", e)
        }
    }

    fun getSession(): Dhis2Config? = currentSession

    fun getD2(): D2? = d2
}
