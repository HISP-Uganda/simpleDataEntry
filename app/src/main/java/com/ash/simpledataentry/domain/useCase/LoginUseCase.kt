package com.ash.simpledataentry.domain.useCase

import com.ash.simpledataentry.domain.repository.AuthRepository
import javax.inject.Inject
import android.content.Context
import com.ash.simpledataentry.data.local.AppDatabase

class LoginUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(
        username: String,
        password: String,
        serverUrl: String,
        context: Context,
        db: AppDatabase
    ): Boolean {
        return repository.login(serverUrl, username, password, context, db)
    }
}

