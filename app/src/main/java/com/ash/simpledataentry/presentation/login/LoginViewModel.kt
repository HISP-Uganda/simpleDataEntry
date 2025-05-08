package com.ash.simpledataentry.presentation.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.simpledataentry.domain.useCase.LoginUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject



sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    object Success : LoginState()
    data class Error(val message: String) : LoginState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    fun login(serverUrl: String, username: String, password: String, context: Context) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                val loginResult = loginUseCase(serverUrl, username, password, context)
                if (loginResult) {
                    _loginState.value = LoginState.Success
                } else {
                    _loginState.value = LoginState.Error("Login failed: Invalid credentials or server error")
                }
            } catch (e: Exception) {
                _loginState.value = LoginState.Error("Login failed: ${e.message ?: "Unknown error"}")
            }
        }
    }
}