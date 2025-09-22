package com.ash.simpledataentry.presentation.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.simpledataentry.domain.useCase.LoginUseCase
import com.ash.simpledataentry.data.local.AppDatabase
import com.ash.simpledataentry.data.local.CachedUrlEntity
import com.ash.simpledataentry.data.repositoryImpl.LoginUrlCacheRepository
import com.ash.simpledataentry.data.repositoryImpl.SavedAccountRepository
import com.ash.simpledataentry.data.repositoryImpl.AuthRepositoryImpl
import com.ash.simpledataentry.presentation.core.NavigationProgress
import com.ash.simpledataentry.domain.model.SavedAccount
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
    val showSplash: Boolean = false,
    val cachedUrls: List<CachedUrlEntity> = emptyList(),
    val urlSuggestions: List<CachedUrlEntity> = emptyList(),
    val savedAccounts: List<SavedAccount> = emptyList(),
    val showAccountSelection: Boolean = false,
    val saveAccountOffered: Boolean = false,
    val pendingCredentials: Triple<String, String, String>? = null, // serverUrl, username, password
    val navigationProgress: NavigationProgress? = null // Enhanced loading progress
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val urlCacheRepository: LoginUrlCacheRepository,
    private val savedAccountRepository: SavedAccountRepository,
    private val authRepository: AuthRepositoryImpl
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    init {
        loadCachedUrls()
        loadSavedAccounts()
    }

    fun login(serverUrl: String, username: String, password: String, context: Context, db: AppDatabase) {
        viewModelScope.launch {
            try {
                // Show splash screen immediately
                _state.value = _state.value.copy(
                    isLoading = true,
                    error = null,
                    showSplash = true
                )

                val loginResult = loginUseCase(username, password, serverUrl, context, db)
                if (loginResult) {
                    // Cache the successful URL
                    urlCacheRepository.addOrUpdateUrl(serverUrl)
                    
                    // Check if we should offer to save the account
                    val existingAccount = savedAccountRepository.getAccountByCredentials(serverUrl, username)
                    android.util.Log.d("LoginDebug", "Existing account found: ${existingAccount != null}, saveAccountOffered will be: ${existingAccount == null}")
                    
                    if (existingAccount != null) {
                        // Update last used timestamp for existing account
                        savedAccountRepository.updateLastUsed(existingAccount.id)
                        savedAccountRepository.switchToAccount(existingAccount.id)
                    }
                    
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        showSplash = true,
                        saveAccountOffered = existingAccount == null,
                        pendingCredentials = if (existingAccount == null) Triple(serverUrl, username, password) else null
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
                    showSplash = false,
                    navigationProgress = NavigationProgress.error(e.message ?: "Login failed")
                )
            }
        }
    }

    fun loginWithProgress(serverUrl: String, username: String, password: String, context: Context, db: AppDatabase) {
        viewModelScope.launch {
            try {
                // Show splash screen immediately
                _state.value = _state.value.copy(
                    isLoading = true,
                    error = null,
                    showSplash = true,
                    navigationProgress = NavigationProgress.initial()
                )

                // Check if offline login is possible first
                val canLoginOffline = authRepository.canLoginOffline(context, username, serverUrl)
                android.util.Log.d("LoginViewModel", "Can login offline: $canLoginOffline")

                val loginResult = if (canLoginOffline) {
                    // Try offline login first
                    android.util.Log.d("LoginViewModel", "Attempting offline login for $username")
                    _state.value = _state.value.copy(
                        navigationProgress = NavigationProgress.initial().copy(
                            phaseTitle = "Offline Login",
                            phaseDetail = "Accessing saved session..."
                        )
                    )

                    authRepository.attemptOfflineLogin(
                        username = username,
                        password = password,
                        serverUrl = serverUrl,
                        context = context,
                        db = db
                    )
                } else {
                    // Fall back to online login with progress tracking
                    android.util.Log.d("LoginViewModel", "Offline login not possible, attempting online login")
                    authRepository.loginWithProgress(
                        username = username,
                        password = password,
                        serverUrl = serverUrl,
                        context = context,
                        db = db
                    ) { progress ->
                        _state.value = _state.value.copy(navigationProgress = progress)
                    }
                }

                if (loginResult) {
                    // Cache the successful URL
                    urlCacheRepository.addOrUpdateUrl(serverUrl)

                    // Check if we should offer to save the account
                    val existingAccount = savedAccountRepository.getAccountByCredentials(serverUrl, username)
                    android.util.Log.d("LoginDebug", "Enhanced login - Existing account found: ${existingAccount != null}")

                    if (existingAccount != null) {
                        // Update last used timestamp for existing account
                        savedAccountRepository.updateLastUsed(existingAccount.id)
                        savedAccountRepository.switchToAccount(existingAccount.id)
                    }

                    _state.value = _state.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        showSplash = true,
                        saveAccountOffered = existingAccount == null,
                        pendingCredentials = if (existingAccount == null) Triple(serverUrl, username, password) else null,
                        navigationProgress = null // Clear progress when done
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = if (canLoginOffline) {
                            "Offline login failed - try connecting to the internet"
                        } else {
                            "Login failed: Invalid credentials or server error"
                        },
                        showSplash = false,
                        navigationProgress = null
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Login failed: ${e.message ?: "Unknown error"}",
                    showSplash = false,
                    navigationProgress = NavigationProgress.error(e.message ?: "Login failed")
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

    private fun loadCachedUrls() {
        viewModelScope.launch {
            try {
                val cachedUrls = urlCacheRepository.getCachedUrls()
                _state.value = _state.value.copy(cachedUrls = cachedUrls)
            } catch (e: Exception) {
                // Silently handle error - cached URLs are not critical for login
            }
        }
    }

    fun updateUrlSuggestions(query: String) {
        viewModelScope.launch {
            try {
                val suggestions = urlCacheRepository.getSuggestedUrls(query)
                _state.value = _state.value.copy(urlSuggestions = suggestions)
            } catch (e: Exception) {
                // Silently handle error - suggestions are not critical
                _state.value = _state.value.copy(urlSuggestions = emptyList())
            }
        }
    }

    fun clearUrlSuggestions() {
        _state.value = _state.value.copy(urlSuggestions = emptyList())
    }

    fun removeUrl(url: String) {
        viewModelScope.launch {
            try {
                urlCacheRepository.removeUrl(url)
                loadCachedUrls() // Refresh the cached URLs list
            } catch (e: Exception) {
                // Silently handle error
            }
        }
    }

    private fun loadSavedAccounts() {
        viewModelScope.launch {
            try {
                val accounts = savedAccountRepository.getAllSavedAccounts()
                _state.value = _state.value.copy(
                    savedAccounts = accounts,
                    showAccountSelection = accounts.isNotEmpty()
                )
            } catch (e: Exception) {
                // Silently handle error - saved accounts are not critical for login
            }
        }
    }

    fun saveAccount(displayName: String, serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            try {
                val result = savedAccountRepository.saveAccount(displayName, serverUrl, username, password)
                if (result.isSuccess) {
                    loadSavedAccounts() // Refresh the accounts list
                    _state.value = _state.value.copy(
                        saveAccountOffered = false,
                        pendingCredentials = null
                    )
                } else {
                    _state.value = _state.value.copy(
                        error = "Failed to save account: ${result.exceptionOrNull()?.message}",
                        saveAccountOffered = false,
                        pendingCredentials = null
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Failed to save account: ${e.message}",
                    saveAccountOffered = false,
                    pendingCredentials = null
                )
            }
        }
    }

    fun loginWithSavedAccount(account: SavedAccount, context: Context, db: AppDatabase) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(
                    isLoading = true,
                    error = null,
                    showSplash = true
                )

                val decryptedPassword = savedAccountRepository.getDecryptedPassword(account.id)
                if (decryptedPassword == null) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Failed to decrypt account password",
                        showSplash = false
                    )
                    return@launch
                }

                // Check if offline login is possible first
                val canLoginOffline = authRepository.canLoginOffline(context, account.username, account.serverUrl)
                android.util.Log.d("LoginViewModel", "Can login offline: $canLoginOffline")

                val loginResult = if (canLoginOffline) {
                    // Try offline login first
                    android.util.Log.d("LoginViewModel", "Attempting offline login for ${account.username}")
                    _state.value = _state.value.copy(
                        navigationProgress = NavigationProgress.initial().copy(
                            phaseTitle = "Offline Login",
                            phaseDetail = "Accessing saved session..."
                        )
                    )

                    authRepository.attemptOfflineLogin(
                        username = account.username,
                        password = decryptedPassword,
                        serverUrl = account.serverUrl,
                        context = context,
                        db = db
                    )
                } else {
                    // Fall back to online login with progress tracking
                    android.util.Log.d("LoginViewModel", "Offline login not possible, attempting online login")
                    _state.value = _state.value.copy(navigationProgress = NavigationProgress.initial())

                    authRepository.loginWithProgress(
                        username = account.username,
                        password = decryptedPassword,
                        serverUrl = account.serverUrl,
                        context = context,
                        db = db
                    ) { progress ->
                        _state.value = _state.value.copy(navigationProgress = progress)
                    }
                }

                if (loginResult) {
                    // Update last used and switch to this account
                    savedAccountRepository.switchToAccount(account.id)

                    _state.value = _state.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        showSplash = true,
                        navigationProgress = null // Clear progress when done
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = if (canLoginOffline) {
                            "Offline login failed - try connecting to the internet"
                        } else {
                            "Login failed: Invalid credentials or server error"
                        },
                        showSplash = false,
                        navigationProgress = null
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Login failed: ${e.message ?: "Unknown error"}",
                    showSplash = false,
                    navigationProgress = NavigationProgress.error(e.message ?: "Login failed")
                )
            }
        }
    }

    fun dismissSaveAccountOffer() {
        _state.value = _state.value.copy(
            saveAccountOffered = false,
            pendingCredentials = null
        )
    }

    fun toggleAccountSelection() {
        val currentShow = _state.value.showAccountSelection
        _state.value = _state.value.copy(showAccountSelection = !currentShow)
    }
    
    fun selectAccount(account: SavedAccount): Triple<String, String, String> {
        // Return serverUrl, username, displayName for pre-filling form
        return Triple(account.serverUrl, account.username, account.displayName)
    }
    
    fun canSaveNewAccount(): Boolean {
        return state.value.savedAccounts.size < SavedAccountRepository.MAX_SAVED_ACCOUNTS
    }
}
