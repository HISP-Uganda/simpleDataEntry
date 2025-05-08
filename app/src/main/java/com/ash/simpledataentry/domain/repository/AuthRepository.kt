package com.ash.simpledataentry.domain.repository

import android.content.Context

interface AuthRepository {
    suspend fun login(serverUrl: String, username: String, password: String, context: Context): Boolean
    suspend fun logout()
    //fun isLoggedIn(): Boolean
}

interface SystemRepository {
    suspend fun initializeD2(context: Context)
}