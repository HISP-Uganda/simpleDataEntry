package com.example.simplede.domain.repository

import com.example.simplede.domain.model.Dhis2Config


interface AuthRepository {
    suspend fun login(config: Dhis2Config): Result<Unit>
    suspend fun getSessionInfo(): Dhis2Config?
    suspend fun verifyCredentials(): Boolean
}