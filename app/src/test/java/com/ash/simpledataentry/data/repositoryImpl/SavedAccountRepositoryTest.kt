package com.ash.simpledataentry.data.repositoryImpl

import app.cash.turbine.test
import com.ash.simpledataentry.data.local.SavedAccountDao
import com.ash.simpledataentry.data.local.SavedAccountEntity
import com.ash.simpledataentry.data.security.AccountEncryption
import com.ash.simpledataentry.domain.model.SavedAccount
import com.ash.simpledataentry.testutil.MainDispatcherRule
import com.ash.simpledataentry.testutil.TestConstants.TEST_ACCOUNT_UID
import com.ash.simpledataentry.testutil.TestConstants.TEST_SERVER_URL
import com.ash.simpledataentry.testutil.TestConstants.TEST_USERNAME
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

class SavedAccountRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Mock
    private lateinit var dao: SavedAccountDao

    @Mock
    private lateinit var encryption: AccountEncryption

    private lateinit var repository: SavedAccountRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = SavedAccountRepository(dao, encryption)
    }

    @Test
    fun `getAllAccounts should return decrypted accounts from database`() = runTest {
        // Arrange
        val encryptedEntity = SavedAccountEntity(
            uid = TEST_ACCOUNT_UID,
            displayName = "Test Account",
            serverUrl = TEST_SERVER_URL,
            username = TEST_USERNAME,
            encryptedPassword = "encrypted_password_data",
            lastUsed = System.currentTimeMillis()
        )

        whenever(dao.getAllAccounts()).thenReturn(flowOf(listOf(encryptedEntity)))
        whenever(encryption.decrypt("encrypted_password_data")).thenReturn("decrypted_password")

        // Act
        val result = repository.getAllAccounts()

        // Assert
        result.test {
            val accounts = awaitItem()
            assertThat(accounts).hasSize(1)
            
            val account = accounts[0]
            assertThat(account.uid).isEqualTo(TEST_ACCOUNT_UID)
            assertThat(account.displayName).isEqualTo("Test Account")
            assertThat(account.serverUrl).isEqualTo(TEST_SERVER_URL)
            assertThat(account.username).isEqualTo(TEST_USERNAME)
            
            awaitComplete()
        }
        
        verify(encryption).decrypt("encrypted_password_data")
    }

    @Test
    fun `saveAccount should encrypt password and save to database`() = runTest {
        // Arrange
        val account = SavedAccount(
            uid = TEST_ACCOUNT_UID,
            displayName = "Test Account",
            serverUrl = TEST_SERVER_URL,
            username = TEST_USERNAME
        )
        val password = "plain_password"
        
        whenever(encryption.encrypt(password)).thenReturn("encrypted_password_data")

        // Act
        repository.saveAccount(account, password)

        // Assert
        verify(encryption).encrypt(password)
        verify(dao).insertOrUpdateAccount(argThat { savedEntity ->
            savedEntity.uid == TEST_ACCOUNT_UID &&
            savedEntity.displayName == "Test Account" &&
            savedEntity.serverUrl == TEST_SERVER_URL &&
            savedEntity.username == TEST_USERNAME &&
            savedEntity.encryptedPassword == "encrypted_password_data"
        })
    }

    @Test
    fun `getAccountPassword should decrypt and return password`() = runTest {
        // Arrange
        val encryptedEntity = SavedAccountEntity(
            uid = TEST_ACCOUNT_UID,
            displayName = "Test Account",
            serverUrl = TEST_SERVER_URL,
            username = TEST_USERNAME,
            encryptedPassword = "encrypted_password_data",
            lastUsed = System.currentTimeMillis()
        )

        whenever(dao.getAccount(TEST_ACCOUNT_UID)).thenReturn(encryptedEntity)
        whenever(encryption.decrypt("encrypted_password_data")).thenReturn("decrypted_password")

        // Act
        val result = repository.getAccountPassword(TEST_ACCOUNT_UID)

        // Assert
        assertThat(result).isEqualTo("decrypted_password")
        verify(encryption).decrypt("encrypted_password_data")
    }

    @Test
    fun `getAccountPassword should return null when account not found`() = runTest {
        // Arrange
        whenever(dao.getAccount(TEST_ACCOUNT_UID)).thenReturn(null)

        // Act
        val result = repository.getAccountPassword(TEST_ACCOUNT_UID)

        // Assert
        assertThat(result).isNull()
        verify(encryption, never()).decrypt(any())
    }

    @Test
    fun `updateLastUsed should update timestamp in database`() = runTest {
        // Act
        repository.updateLastUsed(TEST_ACCOUNT_UID)

        // Assert
        verify(dao).updateLastUsed(eq(TEST_ACCOUNT_UID), any())
    }

    @Test
    fun `deleteAccount should remove account from database`() = runTest {
        // Act
        repository.deleteAccount(TEST_ACCOUNT_UID)

        // Assert
        verify(dao).deleteAccount(TEST_ACCOUNT_UID)
    }

    @Test
    fun `getAllAccounts should handle decryption failure gracefully`() = runTest {
        // Arrange
        val encryptedEntity = SavedAccountEntity(
            uid = TEST_ACCOUNT_UID,
            displayName = "Test Account",
            serverUrl = TEST_SERVER_URL,
            username = TEST_USERNAME,
            encryptedPassword = "corrupted_encrypted_data",
            lastUsed = System.currentTimeMillis()
        )

        whenever(dao.getAllAccounts()).thenReturn(flowOf(listOf(encryptedEntity)))
        whenever(encryption.decrypt("corrupted_encrypted_data")).thenThrow(RuntimeException("Decryption failed"))

        // Act
        val result = repository.getAllAccounts()

        // Assert - should handle error and return empty list or skip corrupted entries
        result.test {
            val accounts = awaitItem()
            // Repository should handle the error gracefully - exact behavior depends on implementation
            // but should not crash the app
            awaitComplete()
        }
    }

    @Test
    fun `saveAccount should handle encryption failure`() = runTest {
        // Arrange
        val account = SavedAccount(
            uid = TEST_ACCOUNT_UID,
            displayName = "Test Account",
            serverUrl = TEST_SERVER_URL,
            username = TEST_USERNAME
        )
        val password = "plain_password"
        
        whenever(encryption.encrypt(password)).thenThrow(RuntimeException("Encryption failed"))

        // Act & Assert
        try {
            repository.saveAccount(account, password)
            // Should either throw exception or handle gracefully depending on implementation
        } catch (e: Exception) {
            assertThat(e.message).contains("Encryption failed")
        }

        verify(dao, never()).insertOrUpdateAccount(any())
    }

    @Test
    fun `multiple accounts should be handled correctly`() = runTest {
        // Arrange
        val entities = listOf(
            SavedAccountEntity(
                uid = "account1",
                displayName = "Account 1",
                serverUrl = "https://server1.com",
                username = "user1",
                encryptedPassword = "encrypted_pass1",
                lastUsed = 1000L
            ),
            SavedAccountEntity(
                uid = "account2",
                displayName = "Account 2",
                serverUrl = "https://server2.com",
                username = "user2",
                encryptedPassword = "encrypted_pass2",
                lastUsed = 2000L
            )
        )

        whenever(dao.getAllAccounts()).thenReturn(flowOf(entities))
        whenever(encryption.decrypt("encrypted_pass1")).thenReturn("password1")
        whenever(encryption.decrypt("encrypted_pass2")).thenReturn("password2")

        // Act
        val result = repository.getAllAccounts()

        // Assert
        result.test {
            val accounts = awaitItem()
            assertThat(accounts).hasSize(2)
            
            val account1 = accounts.find { it.uid == "account1" }
            val account2 = accounts.find { it.uid == "account2" }
            
            assertThat(account1).isNotNull()
            assertThat(account1!!.displayName).isEqualTo("Account 1")
            assertThat(account1.serverUrl).isEqualTo("https://server1.com")
            
            assertThat(account2).isNotNull()
            assertThat(account2!!.displayName).isEqualTo("Account 2")
            assertThat(account2.serverUrl).isEqualTo("https://server2.com")
            
            awaitComplete()
        }
        
        verify(encryption).decrypt("encrypted_pass1")
        verify(encryption).decrypt("encrypted_pass2")
    }
}