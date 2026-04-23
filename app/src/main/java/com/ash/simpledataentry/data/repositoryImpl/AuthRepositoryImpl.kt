package com.ash.simpledataentry.data.repositoryImpl

import android.content.Context
import android.util.Log
import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.domain.model.Dhis2Config
import com.ash.simpledataentry.domain.repository.AuthRepository
import com.ash.simpledataentry.domain.repository.SystemRepository
import javax.inject.Inject
import javax.inject.Singleton
import com.ash.simpledataentry.presentation.core.NavigationProgress

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val sessionManager: SessionManager,
    private val metadataCacheService: com.ash.simpledataentry.data.cache.MetadataCacheService,
    private val backgroundSyncManager: com.ash.simpledataentry.data.sync.BackgroundSyncManager
) : AuthRepository {


    override suspend fun login(serverUrl: String, username: String, password: String, context: Context): Boolean {
        return try {
            sessionManager.login(context, Dhis2Config(serverUrl, username, password))
            // CRITICAL: Clear metadata caches after successful login (handles user-switch scenarios)
            metadataCacheService.clearAllCaches()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun loginWithProgress(
        serverUrl: String,
        username: String,
        password: String,
        context: Context,
        onProgress: (NavigationProgress) -> Unit
    ): Boolean {
        // Blocking metadata download with UI lock (completes before returning)
        sessionManager.loginWithProgress(context, Dhis2Config(serverUrl, username, password), backgroundSyncManager, onProgress)

        // CRITICAL: Clear metadata caches after successful login (handles user-switch scenarios)
        metadataCacheService.clearAllCaches()

        // Background data sync is now manual (Settings -> "Download full data")
        Log.d("AuthRepositoryImpl", "Metadata sync complete - skipping auto full data sync")

        return true
    }

    suspend fun loginAuthOnly(
        serverUrl: String,
        username: String,
        password: String,
        context: Context
    ): Boolean {
        return try {
            sessionManager.authenticateOnly(context, Dhis2Config(serverUrl, username, password))
            metadataCacheService.clearAllCaches()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Attempt offline login with saved account data
     */
    suspend fun attemptOfflineLogin(
        serverUrl: String,
        username: String,
        password: String,
        context: Context
    ): Boolean {
        return try {
            sessionManager.attemptOfflineLogin(context, Dhis2Config(serverUrl, username, password))
            // CRITICAL: Clear metadata caches after successful offline login (handles user-switch scenarios)
            metadataCacheService.clearAllCaches()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if offline login is possible for given credentials
     */
    fun canLoginOffline(context: Context, username: String, serverUrl: String): Boolean {
        return sessionManager.canLoginOffline(context, username, serverUrl)
    }

    //override fun isLoggedIn(): Boolean = sessionManager.isSessionActive()

    override suspend fun logout() = sessionManager.logout()

}

@Singleton
class SystemRepositoryImpl @Inject constructor(
    private val sessionManager: SessionManager

) : SystemRepository {

    override suspend fun initializeD2(context: Context) {
        Log.d("SystemRepository", "Initializing D2")
        try {
            sessionManager.initD2(context)
        } catch (e: Exception) {
            Log.e("SystemRepository", "D2 initialization failed", e)
            throw e
        }
    }
}
