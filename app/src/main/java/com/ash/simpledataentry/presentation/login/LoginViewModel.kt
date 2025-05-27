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

data class LoginState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val error: String? = null,
    val showSplash: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun login(serverUrl: String, username: String, password: String, context: Context) {
        viewModelScope.launch {
            try {
                // Show splash screen immediately
                _state.value = _state.value.copy(
                    isLoading = true,
                    error = null,
                    showSplash = true
                )

                val loginResult = loginUseCase(serverUrl, username, password, context)
                if (loginResult) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        showSplash = true
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Login failed: Invalid credentials or server error",
                        showSplash = false
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Login failed: ${e.message ?: "Unknown error"}",
                    showSplash = false
                )
            }
        }
    }

    fun hideSplash() {
        _state.value = _state.value.copy(showSplash = false)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}