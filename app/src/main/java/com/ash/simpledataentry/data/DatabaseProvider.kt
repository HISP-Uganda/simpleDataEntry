package com.ash.simpledataentry.data

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.ash.simpledataentry.data.local.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides synchronous access to the current account's Room database.
 *
 * This class bridges the gap between:
 * - DatabaseManager's suspend functions (used during login)
 * - Hilt's synchronous @Provides methods (used for dependency injection)
 *
 * DatabaseManager caches the current database in memory after login.
 * DatabaseProvider simply exposes that cached instance synchronously.
 *
 * PRE-LOGIN FALLBACK:
 * Before login, a fallback "shared" database is used to allow Hilt to initialize
 * dependencies at app startup. This fallback database is empty and NOT used for
 * actual data storage - once user logs in, the account-specific database takes over.
 */
@Singleton
class DatabaseProvider @Inject constructor(
    private val databaseManager: DatabaseManager,
    @ApplicationContext private val context: Context
) {
    private val TAG = "DatabaseProvider"

    // Lazy fallback database for pre-login state
    // This allows Hilt to initialize DAOs at app startup before user logs in
    private val fallbackDatabase: AppDatabase by lazy {
        Log.d(TAG, "Creating fallback database for pre-login state")
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "fallback_pre_login_db"
        )
        .addMigrations(
            com.ash.simpledataentry.di.AppModule.MIGRATION_1_2,
            com.ash.simpledataentry.di.AppModule.MIGRATION_3_4,
            com.ash.simpledataentry.di.AppModule.MIGRATION_4_5,
            com.ash.simpledataentry.di.AppModule.MIGRATION_6_7,
            com.ash.simpledataentry.di.AppModule.MIGRATION_7_8
        )
        .build()
    }

    /**
     * Get the current account's database.
     *
     * Returns the account-specific database if logged in, otherwise returns
     * a fallback database for pre-login initialization.
     *
     * @return The active account's Room database, or fallback if not logged in
     */
    fun getCurrentDatabase(): AppDatabase {
        val database = databaseManager.getCurrentDatabaseOrNull()

        if (database == null) {
            Log.w(TAG, "No account database - using fallback pre-login database")
            return fallbackDatabase
        }

        return database
    }

    /**
     * Check if we're using the account-specific database (user is logged in)
     * vs the fallback database (pre-login state).
     */
    fun isAccountDatabaseActive(): Boolean {
        return databaseManager.getCurrentDatabaseOrNull() != null
    }

    /**
     * Get the shared database for cross-account data.
     *
     * This database is used for data that should be accessible regardless of
     * which account is logged in, such as:
     * - Saved accounts list (for account selection screen)
     * - App settings that apply across accounts
     *
     * This uses the fallback database which persists across account switches.
     */
    fun getSharedDatabase(): AppDatabase {
        return fallbackDatabase
    }
}
