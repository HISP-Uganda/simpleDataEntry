package com.ash.simpledataentry.data

import android.content.Context
import androidx.room.Room
import com.ash.simpledataentry.data.local.AppDatabase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages account-specific Room databases.
 * Each account gets its own isolated database file.
 */
@Singleton
class DatabaseManager @Inject constructor(
    private val accountManager: AccountManager
) {
    private var currentDatabase: AppDatabase? = null
    private var currentAccountId: String? = null
    private val mutex = Mutex()

    /**
     * Get or create database for specified account.
     * If account changes, closes old database and opens new one.
     */
    suspend fun getDatabaseForAccount(context: Context, account: AccountManager.AccountInfo): AppDatabase = mutex.withLock {
        // If same account and database exists, return it
        if (currentAccountId == account.accountId && currentDatabase != null) {
            return@withLock currentDatabase!!
        }

        // Close old database if exists
        currentDatabase?.close()

        // Use the account object directly - no lookup needed!

        // Create new database with account-specific name
        val newDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            account.roomDatabaseName  // e.g., "room_a1b2c3d4e5f6g7h8"
        )
        .addMigrations(
            com.ash.simpledataentry.di.AppModule.MIGRATION_1_2,
            com.ash.simpledataentry.di.AppModule.MIGRATION_3_4,
            com.ash.simpledataentry.di.AppModule.MIGRATION_4_5,
            com.ash.simpledataentry.di.AppModule.MIGRATION_6_7,
            com.ash.simpledataentry.di.AppModule.MIGRATION_7_8,
            com.ash.simpledataentry.di.AppModule.MIGRATION_8_9
        )
        .build()

        currentDatabase = newDatabase
        currentAccountId = account.accountId

        android.util.Log.d("DatabaseManager", "Switched to database: ${account.roomDatabaseName} for account: ${account.displayName}")

        return@withLock newDatabase
    }

    /**
     * Get current database if initialized, null otherwise.
     * Non-suspending, safe for use in Hilt providers and synchronous contexts.
     *
     * @return Current database or null if no account is logged in
     */
    fun getCurrentDatabaseOrNull(): AppDatabase? {
        return currentDatabase
    }

    /**
     * Close current database (e.g., on logout)
     */
    suspend fun closeCurrentDatabase() = mutex.withLock {
        currentDatabase?.close()
        currentDatabase = null
        currentAccountId = null
        android.util.Log.d("DatabaseManager", "Closed current database")
    }
}
