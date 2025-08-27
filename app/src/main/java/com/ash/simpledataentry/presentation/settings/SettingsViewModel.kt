package com.ash.simpledataentry.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.simpledataentry.data.repositoryImpl.SavedAccountRepository
import com.ash.simpledataentry.data.security.AccountEncryption
import com.ash.simpledataentry.domain.model.SavedAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val accounts: List<SavedAccount> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isEncryptionAvailable: Boolean = false,
    val syncFrequency: SyncFrequency = SyncFrequency.DAILY,
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val isDeleting: Boolean = false,
    val updateCheckInProgress: Boolean = false,
    val updateAvailable: Boolean = false,
    val latestVersion: String? = null
)

enum class SyncFrequency(val displayName: String, val intervalMinutes: Int) {
    NEVER("Never", -1),
    MANUAL("Manual Only", 0),
    EVERY_15_MINUTES("Every 15 minutes", 15),
    HOURLY("Hourly", 60),
    DAILY("Daily", 1440),
    WEEKLY("Weekly", 10080)
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val savedAccountRepository: SavedAccountRepository,
    private val accountEncryption: AccountEncryption
) : ViewModel() {
    
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()
    
    init {
        checkEncryptionAvailability()
    }
    
    fun loadAccounts() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            
            try {
                val accounts = savedAccountRepository.getAllSavedAccounts()
                _state.value = _state.value.copy(
                    accounts = accounts,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to load accounts: ${e.message}"
                )
            }
        }
    }
    
    fun deleteAccount(accountId: String) {
        viewModelScope.launch {
            try {
                val result = savedAccountRepository.deleteAccount(accountId)
                if (result.isSuccess) {
                    // Reload accounts after deletion
                    loadAccounts()
                } else {
                    _state.value = _state.value.copy(
                        error = "Failed to delete account: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Failed to delete account: ${e.message}"
                )
            }
        }
    }
    
    fun deleteAllAccounts() {
        viewModelScope.launch {
            try {
                val result = savedAccountRepository.deleteAllAccounts()
                if (result.isSuccess) {
                    // Reload accounts after deletion (should be empty)
                    loadAccounts()
                } else {
                    _state.value = _state.value.copy(
                        error = "Failed to delete all accounts: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Failed to delete all accounts: ${e.message}"
                )
            }
        }
    }
    
    fun updateAccountDisplayName(accountId: String, newDisplayName: String) {
        viewModelScope.launch {
            try {
                val result = savedAccountRepository.updateAccountDisplayName(accountId, newDisplayName)
                if (result.isSuccess) {
                    // Reload accounts to reflect changes
                    loadAccounts()
                } else {
                    _state.value = _state.value.copy(
                        error = "Failed to update account: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Failed to update account: ${e.message}"
                )
            }
        }
    }
    
    private fun checkEncryptionAvailability() {
        viewModelScope.launch {
            try {
                val isAvailable = accountEncryption.isEncryptionAvailable()
                _state.value = _state.value.copy(isEncryptionAvailable = isAvailable)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isEncryptionAvailable = false,
                    error = "Failed to check encryption availability: ${e.message}"
                )
            }
        }
    }
    
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
    
    // === NEW SETTINGS FEATURES ===
    
    fun setSyncFrequency(frequency: SyncFrequency) {
        _state.value = _state.value.copy(syncFrequency = frequency)
        // TODO: Persist this setting and configure background sync
    }
    
    fun exportData() {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(
                    isExporting = true,
                    exportProgress = 0f,
                    error = null
                )
                
                // Simulate export progress
                for (progress in 0..100 step 10) {
                    _state.value = _state.value.copy(exportProgress = progress / 100f)
                    kotlinx.coroutines.delay(200) // Simulate work
                }
                
                // TODO: Implement actual data export to ZIP file
                // This should:
                // 1. Query all data from Room database
                // 2. Create offline-compatible data files
                // 3. Package into ZIP format
                // 4. Save to external storage or share via intent
                
                _state.value = _state.value.copy(
                    isExporting = false,
                    exportProgress = 0f
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isExporting = false,
                    exportProgress = 0f,
                    error = "Failed to export data: ${e.message}"
                )
            }
        }
    }
    
    fun deleteAllData() {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(
                    isDeleting = true,
                    error = null
                )
                
                // TODO: Implement secure data deletion
                // This should:
                // 1. Clear all Room database tables
                // 2. Clear SharedPreferences
                // 3. Clear cached files
                // 4. Clear saved accounts (with confirmation)
                // 5. Reset app to initial state
                
                kotlinx.coroutines.delay(2000) // Simulate deletion time
                
                _state.value = _state.value.copy(isDeleting = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isDeleting = false,
                    error = "Failed to delete data: ${e.message}"
                )
            }
        }
    }
    
    fun checkForUpdates() {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(
                    updateCheckInProgress = true,
                    error = null
                )
                
                // Simulate update check
                kotlinx.coroutines.delay(1500)
                
                // TODO: Implement actual update checking
                // This should:
                // 1. Query app version endpoint or GitHub releases
                // 2. Compare with current app version
                // 3. Show update availability and download link
                
                val currentVersion = "1.0.0" // Get from BuildConfig
                val latestVersion = "1.1.0" // From remote check
                val updateAvailable = latestVersion != currentVersion
                
                _state.value = _state.value.copy(
                    updateCheckInProgress = false,
                    updateAvailable = updateAvailable,
                    latestVersion = if (updateAvailable) latestVersion else null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    updateCheckInProgress = false,
                    error = "Failed to check for updates: ${e.message}"
                )
            }
        }
    }
}