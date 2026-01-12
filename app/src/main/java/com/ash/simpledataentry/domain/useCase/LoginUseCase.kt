package com.ash.simpledataentry.domain.useCase

import com.ash.simpledataentry.domain.repository.AuthRepository
import javax.inject.Inject
import android.content.Context

class LoginUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(
        username: String,
        password: String,
        serverUrl: String,
        context: Context
    ): Boolean {
        return repository.login(serverUrl, username, password, context)
    }
}

