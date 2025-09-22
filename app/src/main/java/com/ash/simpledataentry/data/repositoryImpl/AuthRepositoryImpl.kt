package com.ash.simpledataentry.data.repositoryImpl

import android.content.Context
import android.util.Log
import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.domain.model.Dhis2Config
import com.ash.simpledataentry.domain.repository.AuthRepository
import com.ash.simpledataentry.domain.repository.SystemRepository
import com.ash.simpledataentry.data.local.AppDatabase
import javax.inject.Inject
import javax.inject.Singleton
import com.ash.simpledataentry.presentation.core.NavigationProgress

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val sessionManager: SessionManager,

) : AuthRepository {

    override suspend fun login(serverUrl: String, username: String, password: String, context: Context, db: AppDatabase): Boolean {
        return try {
            sessionManager.login(context, Dhis2Config(serverUrl, username, password), db)
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
            sessionManager.loginWithProgress(context, Dhis2Config(serverUrl, username, password), db, onProgress)
            true
        } catch (e: Exception) {
            false
        }
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