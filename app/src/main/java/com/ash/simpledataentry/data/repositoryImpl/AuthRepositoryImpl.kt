package com.ash.simpledataentry.data.repositoryImpl

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.domain.model.Dhis2Config
import com.ash.simpledataentry.domain.repository.AuthRepository
import com.ash.simpledataentry.domain.repository.SystemRepository
import com.ash.simpledataentry.data.local.AppDatabase
import javax.inject.Inject
import javax.inject.Singleton
import com.ash.simpledataentry.presentation.core.NavigationProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val sessionManager: SessionManager,
    private val metadataCacheService: com.ash.simpledataentry.data.cache.MetadataCacheService
) : AuthRepository {

    // Scope for background tasks that survive beyond the calling coroutine
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun login(serverUrl: String, username: String, password: String, context: Context, db: AppDatabase): Boolean {
        return try {
            sessionManager.login(context, Dhis2Config(serverUrl, username, password), db)
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
        db: AppDatabase,
        onProgress: (NavigationProgress) -> Unit
    ): Boolean {
        return try {
            // Blocking metadata download with UI lock (completes before returning)
            sessionManager.loginWithProgress(context, Dhis2Config(serverUrl, username, password), db, onProgress)

            // CRITICAL: Clear metadata caches after successful login (handles user-switch scenarios)
            metadataCacheService.clearAllCaches()

            // CRITICAL: Start async background data sync AFTER metadata completes
            // UI is unlocked, user can navigate immediately
            Log.d("AuthRepositoryImpl", "Metadata sync complete - starting background data sync")
            backgroundScope.launch {
                var syncSuccess = false
                var syncMessage: String? = null

                sessionManager.startBackgroundDataSync(context, db) { success, message ->
                    syncSuccess = success
                    syncMessage = message
                }

                // Show non-intrusive toast notification when background sync completes
                withContext(Dispatchers.Main) {
                    val toastMessage = if (syncSuccess) {
                        "✓ Data sync complete"
                    } else {
                        "⚠ Data sync incomplete: ${syncMessage ?: "Unknown error"}"
                    }
                    Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show()
                    Log.d("AuthRepositoryImpl", "Background sync completed: $toastMessage")
                }
            }

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
        context: Context,
        db: AppDatabase
    ): Boolean {
        return try {
            sessionManager.attemptOfflineLogin(context, Dhis2Config(serverUrl, username, password), db)
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