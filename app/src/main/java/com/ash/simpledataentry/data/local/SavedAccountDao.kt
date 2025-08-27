package com.ash.simpledataentry.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedAccountDao {
    
    @Query("SELECT * FROM saved_accounts ORDER BY lastUsed DESC")
    suspend fun getAllSavedAccounts(): List<SavedAccountEntity>
    
    @Query("SELECT * FROM saved_accounts ORDER BY lastUsed DESC")
    fun getAllSavedAccountsFlow(): Flow<List<SavedAccountEntity>>
    
    @Query("SELECT * FROM saved_accounts WHERE id = :accountId")
    suspend fun getSavedAccount(accountId: String): SavedAccountEntity?
    
    @Query("SELECT * FROM saved_accounts WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveAccount(): SavedAccountEntity?
    
    @Query("SELECT * FROM saved_accounts WHERE serverUrl = :serverUrl AND username = :username LIMIT 1")
    suspend fun getAccountByCredentials(serverUrl: String, username: String): SavedAccountEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedAccount(account: SavedAccountEntity)
    
    @Update
    suspend fun updateSavedAccount(account: SavedAccountEntity)
    
    @Query("UPDATE saved_accounts SET lastUsed = :timestamp WHERE id = :accountId")
    suspend fun updateLastUsed(accountId: String, timestamp: Long)
    
    @Query("UPDATE saved_accounts SET isActive = 0")
    suspend fun deactivateAllAccounts()
    
    @Query("UPDATE saved_accounts SET isActive = 1 WHERE id = :accountId")
    suspend fun setAccountActive(accountId: String)
    
    @Transaction
    suspend fun switchActiveAccount(accountId: String) {
        deactivateAllAccounts()
        setAccountActive(accountId)
        updateLastUsed(accountId, System.currentTimeMillis())
    }
    
    @Delete
    suspend fun deleteSavedAccount(account: SavedAccountEntity)
    
    @Query("DELETE FROM saved_accounts WHERE id = :accountId")
    suspend fun deleteSavedAccountById(accountId: String)
    
    @Query("DELETE FROM saved_accounts")
    suspend fun deleteAllSavedAccounts()
    
    @Query("SELECT COUNT(*) FROM saved_accounts")
    suspend fun getAccountCount(): Int
    
    @Query("SELECT * FROM saved_accounts WHERE displayName LIKE '%' || :query || '%' OR username LIKE '%' || :query || '%' OR serverUrl LIKE '%' || :query || '%' ORDER BY lastUsed DESC")
    suspend fun searchAccounts(query: String): List<SavedAccountEntity>
}