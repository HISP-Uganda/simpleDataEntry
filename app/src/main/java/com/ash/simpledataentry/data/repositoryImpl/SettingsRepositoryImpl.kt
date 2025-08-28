package com.ash.simpledataentry.data.repositoryImpl

import android.content.Context
import android.content.SharedPreferences
import com.ash.simpledataentry.domain.repository.SettingsRepository
import com.ash.simpledataentry.presentation.settings.SyncFrequency
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {
    
    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_SYNC_FREQUENCY = "sync_frequency"
        private const val KEY_INITIAL_SETUP_COMPLETE = "initial_setup_complete"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // StateFlow to observe settings changes
    private val _syncFrequency = MutableStateFlow(getSyncFrequencyFromPrefs())
    
    override suspend fun setSyncFrequency(frequency: SyncFrequency) {
        prefs.edit()
            .putString(KEY_SYNC_FREQUENCY, frequency.name)
            .apply()
        
        // Update the flow
        _syncFrequency.value = frequency
    }
    
    override suspend fun getSyncFrequency(): SyncFrequency {
        return getSyncFrequencyFromPrefs()
    }
    
    override fun observeSyncFrequency(): Flow<SyncFrequency> {
        return _syncFrequency.asStateFlow()
    }
    
    override suspend fun setInitialSetupComplete(complete: Boolean) {
        prefs.edit()
            .putBoolean(KEY_INITIAL_SETUP_COMPLETE, complete)
            .apply()
    }
    
    override suspend fun isInitialSetupComplete(): Boolean {
        return prefs.getBoolean(KEY_INITIAL_SETUP_COMPLETE, false)
    }
    
    override suspend fun clearAllSettings() {
        prefs.edit().clear().apply()
        
        // Reset StateFlow to default
        _syncFrequency.value = SyncFrequency.DAILY
    }
    
    private fun getSyncFrequencyFromPrefs(): SyncFrequency {
        val saved = prefs.getString(KEY_SYNC_FREQUENCY, SyncFrequency.DAILY.name)
        return try {
            SyncFrequency.valueOf(saved ?: SyncFrequency.DAILY.name)
        } catch (e: IllegalArgumentException) {
            // Fallback to default if invalid value stored
            SyncFrequency.DAILY
        }
    }
}