package com.ash.simpledataentry.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.simpledataentry.data.repositoryImpl.SavedAccountRepository
import com.ash.simpledataentry.data.security.AccountEncryption
import com.ash.simpledataentry.domain.model.SavedAccount
import com.ash.simpledataentry.domain.repository.SettingsRepository
import com.ash.simpledataentry.data.sync.BackgroundSyncManager
import com.ash.simpledataentry.data.local.AppDatabase
import com.ash.simpledataentry.data.SessionManager
// BuildConfig should be automatically available - remove explicit import
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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
    private val accountEncryption: AccountEncryption,
    private val settingsRepository: SettingsRepository,
    private val backgroundSyncManager: BackgroundSyncManager,
    private val database: AppDatabase,
    private val sessionManager: SessionManager
) : ViewModel() {
    
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()
    
    init {
        checkEncryptionAvailability()
        loadSyncFrequency()
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
        viewModelScope.launch {
            try {
                // Persist the setting
                settingsRepository.setSyncFrequency(frequency)
                
                // Update UI state
                _state.value = _state.value.copy(syncFrequency = frequency)
                
                // Configure background sync with WorkManager
                backgroundSyncManager.configureSyncSchedule(frequency)
                
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Failed to save sync frequency: ${e.message}"
                )
            }
        }
    }
    
    private fun loadSyncFrequency() {
        viewModelScope.launch {
            try {
                val savedFrequency = settingsRepository.getSyncFrequency()
                _state.value = _state.value.copy(syncFrequency = savedFrequency)
            } catch (e: Exception) {
                // Use default frequency if loading fails
                _state.value = _state.value.copy(syncFrequency = SyncFrequency.DAILY)
            }
        }
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
                
                // 1. Clear all Room database tables
                database.dataValueDraftDao().deleteAllDrafts()
                database.dataValueDao().deleteAllDataValues() 
                database.datasetDao().clearAll()
                database.dataElementDao().clearAll()
                database.categoryComboDao().clearAll()
                database.categoryOptionComboDao().clearAll()
                database.organisationUnitDao().clearAll()
                database.cachedUrlDao().clearAll()
                
                // 2. Clear saved accounts
                savedAccountRepository.deleteAllAccounts()
                
                // 3. Clear all settings
                settingsRepository.clearAllSettings()
                
                // 4. Cancel any background sync
                backgroundSyncManager.cancelBackgroundSync()
                
                // 5. Clear DHIS2 session if active
                try {
                    if (sessionManager.isSessionActive()) {
                        sessionManager.logout()
                    }
                } catch (e: Exception) {
                    // Log but don't fail the deletion for session issues
                    android.util.Log.w("SettingsViewModel", "Failed to clear DHIS2 session", e)
                }
                
                _state.value = _state.value.copy(
                    isDeleting = false,
                    // Reset local state after deletion
                    accounts = emptyList(),
                    syncFrequency = SyncFrequency.DAILY // Reset to default
                )
                
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
                
                val currentVersion = "1.0" // Current app version from build.gradle
                val latestVersion = fetchLatestVersionFromGitHub()
                
                val updateAvailable = if (latestVersion != null) {
                    compareVersions(currentVersion, latestVersion) < 0
                } else false
                
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
    
    /**
     * Fetch the latest version from GitHub Releases API
     */
    private suspend fun fetchLatestVersionFromGitHub(): String? = withContext(Dispatchers.IO) {
        try {
            // Note: Replace with actual GitHub repository URL
            val githubApiUrl = "https://api.github.com/repos/username/simpleDataEntry/releases/latest"
            val url = URL(githubApiUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)
                val tagName = jsonObject.getString("tag_name")
                
                // Remove 'v' prefix if present (e.g., "v1.2.0" -> "1.2.0")
                return@withContext if (tagName.startsWith("v")) {
                    tagName.substring(1)
                } else {
                    tagName
                }
            } else {
                android.util.Log.w("SettingsViewModel", "GitHub API returned $responseCode")
                return@withContext null
            }
        } catch (e: Exception) {
            android.util.Log.w("SettingsViewModel", "Failed to fetch latest version", e)
            return@withContext null
        }
    }
    
    /**
     * Compare two semantic versions (e.g., "1.0.0" vs "1.1.0")
     * Returns: -1 if version1 < version2, 0 if equal, 1 if version1 > version2
     */
    private fun compareVersions(version1: String, version2: String): Int {
        val parts1 = version1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = version2.split(".").map { it.toIntOrNull() ?: 0 }
        
        val maxLength = maxOf(parts1.size, parts2.size)
        
        for (i in 0 until maxLength) {
            val v1 = parts1.getOrElse(i) { 0 }
            val v2 = parts2.getOrElse(i) { 0 }
            
            when {
                v1 < v2 -> return -1
                v1 > v2 -> return 1
            }
        }
        
        return 0
    }
}