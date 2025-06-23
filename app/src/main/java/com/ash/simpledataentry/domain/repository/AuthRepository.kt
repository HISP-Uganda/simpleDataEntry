package com.ash.simpledataentry.domain.repository

import android.content.Context
import com.ash.simpledataentry.data.local.AppDatabase

interface AuthRepository {
    suspend fun login(serverUrl: String, username: String, password: String, context: Context, db: AppDatabase): Boolean
    suspend fun logout()
    //fun isLoggedIn(): Boolean
}

interface SystemRepository {
    suspend fun initializeD2(context: Context)
}