package com.ash.simpledataentry.domain.repository

import com.ash.simpledataentry.presentation.settings.SyncFrequency
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for app settings management
 */
interface SettingsRepository {
    
    /**
     * Set the sync frequency for background sync
     */
    suspend fun setSyncFrequency(frequency: SyncFrequency)
    
    /**
     * Get the current sync frequency setting
     */
    suspend fun getSyncFrequency(): SyncFrequency
    
    /**
     * Observe sync frequency changes as a flow
     */
    fun observeSyncFrequency(): Flow<SyncFrequency>
    
    /**
     * Set whether the user has completed the initial setup
     */
    suspend fun setInitialSetupComplete(complete: Boolean)
    
    /**
     * Check if initial setup is complete
     */
    suspend fun isInitialSetupComplete(): Boolean
    
    /**
     * Clear all settings (for data deletion)
     */
    suspend fun clearAllSettings()
}