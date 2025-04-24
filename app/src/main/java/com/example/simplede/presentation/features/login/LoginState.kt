package com.example.simplede.presentation.features.login

import com.example.simplede.domain.model.Dhis2Config


sealed class LoginState {
    object Initial : LoginState()
    object Loading : LoginState()
    data class Error(val message: String) : LoginState()
    data class Success(val config: Dhis2Config) : LoginState()
}