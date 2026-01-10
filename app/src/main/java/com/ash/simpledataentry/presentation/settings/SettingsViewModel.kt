package com.ash.simpledataentry.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.simpledataentry.data.repositoryImpl.SavedAccountRepository
import com.ash.simpledataentry.data.security.AccountEncryption
import com.ash.simpledataentry.domain.model.SavedAccount
import com.ash.simpledataentry.domain.repository.SettingsRepository
import com.ash.simpledataentry.data.sync.BackgroundSyncManager
import com.ash.simpledataentry.data.DatabaseProvider
import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.presentation.core.UiState
import com.ash.simpledataentry.presentation.core.UiError
import com.ash.simpledataentry.presentation.core.LoadingOperation
import com.ash.simpledataentry.presentation.core.BackgroundOperation
import com.ash.simpledataentry.presentation.core.LoadingProgress
import com.ash.simpledataentry.presentation.core.emitSuccess
import com.ash.simpledataentry.presentation.core.emitError
import com.ash.simpledataentry.presentation.core.emitLoading
import com.ash.simpledataentry.presentation.core.dataOr
import com.ash.simpledataentry.presentation.core.clearError
import com.ash.simpledataentry.util.toUiError
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

/**
 * Settings data model - contains only data, no UI state flags
 */
data class SettingsData(
    val accounts: List<SavedAccount> = emptyList(),
    val isEncryptionAvailable: Boolean = false,
    val syncFrequency: SyncFrequency = SyncFrequency.DAILY,
    val isMetadataSyncing: Boolean = false,
    val metadataSyncMessage: String? = null,
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
    private val databaseProvider: DatabaseProvider,
    private val sessionManager: SessionManager
) : ViewModel() {

    // New UiState pattern
    private val _uiState = MutableStateFlow<UiState<SettingsData>>(
        UiState.Success(SettingsData())
    )
    val uiState: StateFlow<UiState<SettingsData>> = _uiState.asStateFlow()

    init {
        checkEncryptionAvailability()
        loadSyncFrequency()
    }
    
    fun loadAccounts() {
        viewModelScope.launch {
            _uiState.emitLoading(LoadingOperation.Initial)

            try {
                val accounts = savedAccountRepository.getAllSavedAccounts()
                val currentData = getCurrentData()
                val newData = currentData.copy(accounts = accounts)

                // Update new state
                _uiState.emitSuccess(newData)
            } catch (e: Exception) {
                val uiError = e.toUiError()

                // Update new state
                _uiState.emitError(uiError)
            }
        }
    }

    /**
     * Helper to get current data from UiState
     */
    private fun getCurrentData(): SettingsData {
        return _uiState.value.dataOr { SettingsData() }
    }
    
    fun deleteAccount(accountId: String) {
        viewModelScope.launch {
            try {
                val result = savedAccountRepository.deleteAccount(accountId)
                if (result.isSuccess) {
                    // Reload accounts after deletion
                    loadAccounts()
                } else {
                    val error = result.exceptionOrNull()?.toUiError()
                        ?: UiError.Local("Failed to delete account")
                    _uiState.emitError(error)
                }
            } catch (e: Exception) {
                val uiError = e.toUiError()
                val currentData = getCurrentData()
                _uiState.emitSuccess(currentData.copy(updateCheckInProgress = false))
                _uiState.emitError(uiError)
            }
        }
    }

    fun syncMetadataNow() {
        viewModelScope.launch {
            try {
                val currentData = getCurrentData()
                _uiState.emitSuccess(
                    currentData.copy(isMetadataSyncing = true, metadataSyncMessage = null)
                )

                backgroundSyncManager.triggerImmediateMetadataSync()

                _uiState.emitSuccess(
                    currentData.copy(
                        isMetadataSyncing = false,
                        metadataSyncMessage = "Metadata sync started"
                    )
                )
            } catch (e: Exception) {
                val uiError = e.toUiError()
                _uiState.emitError(uiError)
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
                    val error = result.exceptionOrNull()?.toUiError()
                        ?: UiError.Local("Failed to delete all accounts")
                    _uiState.emitError(error)
                }
            } catch (e: Exception) {
                val uiError = e.toUiError()
                _uiState.emitError(uiError)
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
                    val error = result.exceptionOrNull()?.toUiError()
                        ?: UiError.Local("Failed to update account")
                    _uiState.emitError(error)
                }
            } catch (e: Exception) {
                val uiError = e.toUiError()
                _uiState.emitError(uiError)
            }
        }
    }
    
    private fun checkEncryptionAvailability() {
        viewModelScope.launch {
            try {
                val isAvailable = accountEncryption.isEncryptionAvailable()
                val currentData = getCurrentData()
                val newData = currentData.copy(isEncryptionAvailable = isAvailable)

                _uiState.emitSuccess(newData)
            } catch (e: Exception) {
                val uiError = e.toUiError()
                _uiState.emitError(uiError)
            }
        }
    }

    fun clearError() {
        // Convert error state back to success with previous data
        _uiState.clearError { SettingsData() }
    }
    
    // === NEW SETTINGS FEATURES ===
    
    fun setSyncFrequency(frequency: SyncFrequency) {
        viewModelScope.launch {
            try {
                // Persist the setting
                settingsRepository.setSyncFrequency(frequency)

                // Update UI state
                val currentData = getCurrentData()
                val newData = currentData.copy(syncFrequency = frequency)
                _uiState.emitSuccess(newData)

                // Configure background sync with WorkManager
                backgroundSyncManager.configureSyncSchedule(frequency)

            } catch (e: Exception) {
                val uiError = e.toUiError()
                _uiState.emitError(uiError)
            }
        }
    }

    private fun loadSyncFrequency() {
        viewModelScope.launch {
            try {
                val savedFrequency = settingsRepository.getSyncFrequency()
                val currentData = getCurrentData()
                val newData = currentData.copy(syncFrequency = savedFrequency)

                _uiState.emitSuccess(newData)
            } catch (e: Exception) {
                // Use default frequency if loading fails - not an error state
                val currentData = getCurrentData()
                val newData = currentData.copy(syncFrequency = SyncFrequency.DAILY)

                _uiState.emitSuccess(newData)
            }
        }
    }
    
    fun exportData() {
        viewModelScope.launch {
            try {
                // Show background operation indicator
                val currentData = getCurrentData()
                _uiState.emitSuccess(
                    currentData.copy(isExporting = true, exportProgress = 0f),
                    BackgroundOperation.Exporting
                )

                // Simulate export progress
                for (progress in 0..100 step 10) {
                    _uiState.emitSuccess(
                        getCurrentData().copy(exportProgress = progress / 100f, isExporting = true),
                        BackgroundOperation.Exporting
                    )
                    kotlinx.coroutines.delay(200) // Simulate work
                }

                // TODO: Implement actual data export to ZIP file
                // This should:
                // 1. Query all data from Room database
                // 2. Create offline-compatible data files
                // 3. Package into ZIP format
                // 4. Save to external storage or share via intent

                // Clear background operation
                _uiState.emitSuccess(currentData.copy(isExporting = false, exportProgress = 0f))
            } catch (e: Exception) {
                val uiError = e.toUiError()
                _uiState.emitError(uiError)
            }
        }
    }
    
    fun deleteAllData() {
        viewModelScope.launch {
            try {
                // Show background operation indicator
                val currentData = getCurrentData()
                _uiState.emitSuccess(currentData.copy(isDeleting = true), BackgroundOperation.Deleting)

                // 1. Clear all Room database tables
                val database = databaseProvider.getCurrentDatabase()
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

                // Reset to clean state
                val cleanData = SettingsData(
                    accounts = emptyList(),
                    syncFrequency = SyncFrequency.DAILY
                )
                _uiState.emitSuccess(cleanData)

            } catch (e: Exception) {
                val uiError = e.toUiError()
                _uiState.emitError(uiError)
            }
        }
    }
    
    fun checkForUpdates() {
        viewModelScope.launch {
            try {
                // Show loading state
                val currentData = getCurrentData()
                _uiState.emitSuccess(
                    currentData.copy(updateCheckInProgress = true),
                    BackgroundOperation.Syncing
                )

                val currentVersion = "1.0" // Current app version from build.gradle
                val latestVersion = fetchLatestVersionFromGitHub()

                val updateAvailable = if (latestVersion != null) {
                    compareVersions(currentVersion, latestVersion) < 0
                } else false

                // Update data with version check results
                val newData = currentData.copy(
                    updateAvailable = updateAvailable,
                    updateCheckInProgress = false,
                    latestVersion = if (updateAvailable) latestVersion else null
                )

                _uiState.emitSuccess(newData)
            } catch (e: Exception) {
                val uiError = e.toUiError()
                _uiState.emitError(uiError)
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
