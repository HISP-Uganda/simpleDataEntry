package com.example.simplede.data.repositoryImpl

import android.content.Context
import com.example.simplede.data.SessionManager
import com.example.simplede.domain.model.Dhis2Config
import com.example.simplede.domain.repository.AuthRepository

class AuthRepositoryImpl(private val context: Context) : AuthRepository {
    override suspend fun login(config: Dhis2Config): Result<Unit> {
        return try {
            SessionManager.initD2(context)
            SessionManager.login(config)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSessionInfo(): Dhis2Config? {
        return SessionManager.getSession()
    }

    override suspend fun verifyCredentials(): Boolean {
        return SessionManager.isSessionActive()
    }
}