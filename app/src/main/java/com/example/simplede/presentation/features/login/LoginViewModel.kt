package com.example.simplede.presentation.features.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.simplede.data.repositoryImpl.AuthRepositoryImpl
import com.example.simplede.domain.model.Dhis2Config
import com.example.simplede.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LoginViewModel(AuthRepositoryImpl(context)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Initial)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    init {
        checkExistingSession()
    }

    private fun checkExistingSession() {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                val sessionInfo = authRepository.getSessionInfo()
                if (sessionInfo != null) {
                    verifyStoredCredentials()
                } else {
                    _loginState.value = LoginState.Initial
                }
            } catch (e: Exception) {
                _loginState.value = LoginState.Error("Failed to check session: ${e.message}")
            }
        }
    }

    private fun verifyStoredCredentials() {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                val isValid = authRepository.verifyCredentials()
                if (isValid) {
                    val config = authRepository.getSessionInfo()
                    if (config != null) {
                        _loginState.value = LoginState.Success(config)
                    } else {
                        _loginState.value = LoginState.Initial
                    }
                } else {
                    _loginState.value = LoginState.Initial
                }
            } catch (e: Exception) {
                _loginState.value = LoginState.Error("Failed to verify credentials: ${e.message}")
            }
        }
    }

    fun login(serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                val config = Dhis2Config(serverUrl, username, password)
                val loginResult = authRepository.login(config)
                if (loginResult.isSuccess) {
                    _loginState.value = LoginState.Success(config)
                } else {
                    _loginState.value = LoginState.Error(loginResult.exceptionOrNull()?.message ?: "Login failed")
                }
            } catch (e: Exception) {
                _loginState.value = LoginState.Error("Login failed: ${e.message}")
            }
        }
    }
}

