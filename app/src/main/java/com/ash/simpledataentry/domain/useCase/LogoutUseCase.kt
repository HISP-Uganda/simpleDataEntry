package com.ash.simpledataentry.domain.useCase

import com.ash.simpledataentry.domain.repository.AuthRepository
import javax.inject.Inject

class LogoutUseCase @Inject constructor(private val authRepository: AuthRepository) {
    suspend operator fun invoke() {
        return authRepository.logout()
    }
}