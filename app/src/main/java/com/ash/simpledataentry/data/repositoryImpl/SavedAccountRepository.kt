package com.ash.simpledataentry.data.repositoryImpl

import com.ash.simpledataentry.data.local.AppDatabase
import com.ash.simpledataentry.data.local.SavedAccountEntity
import com.ash.simpledataentry.data.security.AccountEncryption
import com.ash.simpledataentry.domain.model.SavedAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavedAccountRepository @Inject constructor(
    private val database: AppDatabase,
    private val accountEncryption: AccountEncryption
) {
    
    companion object {
        const val MAX_SAVED_ACCOUNTS = 5
    }
    
    private val savedAccountDao = database.savedAccountDao()
    
    suspend fun getAllSavedAccounts(): List<SavedAccount> = withContext(Dispatchers.IO) {
        savedAccountDao.getAllSavedAccounts().map { it.toDomainModel() }
    }
    
    fun getAllSavedAccountsFlow(): Flow<List<SavedAccount>> {
        return savedAccountDao.getAllSavedAccountsFlow().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    suspend fun getSavedAccount(accountId: String): SavedAccount? = withContext(Dispatchers.IO) {
        savedAccountDao.getSavedAccount(accountId)?.toDomainModel()
    }
    
    suspend fun getActiveAccount(): SavedAccount? = withContext(Dispatchers.IO) {
        savedAccountDao.getActiveAccount()?.toDomainModel()
    }
    
    suspend fun getAccountByCredentials(serverUrl: String, username: String): SavedAccount? = withContext(Dispatchers.IO) {
        savedAccountDao.getAccountByCredentials(serverUrl, username)?.toDomainModel()
    }
    
    suspend fun saveAccount(
        displayName: String,
        serverUrl: String,
        username: String,
        password: String
    ): Result<SavedAccount> = withContext(Dispatchers.IO) {
        try {
            // Check if encryption is available
            if (!accountEncryption.isEncryptionAvailable()) {
                return@withContext Result.failure(
                    SecurityException("Android Keystore encryption is not available")
                )
            }
            
            // Encrypt the password
            val encryptedPassword = accountEncryption.encryptPassword(password)
                ?: return@withContext Result.failure(
                    SecurityException("Failed to encrypt password")
                )
            
            // Check if account already exists and enforce limit
            val existingAccount = savedAccountDao.getAccountByCredentials(serverUrl, username)
            if (existingAccount == null) { // Only check limit for new accounts
                val currentCount = savedAccountDao.getAccountCount()
                if (currentCount >= MAX_SAVED_ACCOUNTS) {
                    return@withContext Result.failure(
                        IllegalStateException("Maximum number of saved accounts ($MAX_SAVED_ACCOUNTS) reached")
                    )
                }
            }
            val accountId = existingAccount?.id ?: UUID.randomUUID().toString()
            
            val account = SavedAccount(
                id = accountId,
                displayName = displayName,
                serverUrl = serverUrl,
                username = username,
                encryptedPassword = encryptedPassword,
                lastUsed = System.currentTimeMillis(),
                isActive = false
            )
            
            savedAccountDao.insertSavedAccount(account.toEntity())
            Result.success(account)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun switchToAccount(accountId: String): Result<SavedAccount> = withContext(Dispatchers.IO) {
        try {
            val account = savedAccountDao.getSavedAccount(accountId)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Account not found")
                )
            
            savedAccountDao.switchActiveAccount(accountId)
            Result.success(account.toDomainModel())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun updateLastUsed(accountId: String) = withContext(Dispatchers.IO) {
        savedAccountDao.updateLastUsed(accountId, System.currentTimeMillis())
    }
    
    suspend fun deleteAccount(accountId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            savedAccountDao.deleteSavedAccountById(accountId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun deleteAllAccounts(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            savedAccountDao.deleteAllSavedAccounts()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getAccountCount(): Int = withContext(Dispatchers.IO) {
        savedAccountDao.getAccountCount()
    }
    
    suspend fun searchAccounts(query: String): List<SavedAccount> = withContext(Dispatchers.IO) {
        savedAccountDao.searchAccounts(query).map { it.toDomainModel() }
    }
    
    suspend fun getDecryptedPassword(accountId: String): String? = withContext(Dispatchers.IO) {
        val account = savedAccountDao.getSavedAccount(accountId) ?: return@withContext null
        accountEncryption.decryptPassword(account.encryptedPassword)
    }
    
    suspend fun updateAccountDisplayName(accountId: String, displayName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val account = savedAccountDao.getSavedAccount(accountId)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Account not found")
                )
            
            val updatedAccount = account.copy(displayName = displayName)
            savedAccountDao.updateSavedAccount(updatedAccount)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Extension functions for mapping between domain and entity models
    private fun SavedAccountEntity.toDomainModel(): SavedAccount {
        return SavedAccount(
            id = id,
            displayName = displayName,
            serverUrl = serverUrl,
            username = username,
            encryptedPassword = encryptedPassword,
            lastUsed = lastUsed,
            isActive = isActive,
            createdAt = createdAt
        )
    }
    
    private fun SavedAccount.toEntity(): SavedAccountEntity {
        return SavedAccountEntity(
            id = id,
            displayName = displayName,
            serverUrl = serverUrl,
            username = username,
            encryptedPassword = encryptedPassword,
            lastUsed = lastUsed,
            isActive = isActive,
            createdAt = createdAt
        )
    }
}