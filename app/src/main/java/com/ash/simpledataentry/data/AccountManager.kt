package com.ash.simpledataentry.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages multiple saved DHIS2 accounts with isolated databases.
 * Each account gets its own D2 SDK database and Room database.
 */
@Singleton
class AccountManager @Inject constructor() {

    init {
        Log.d(TAG, "AccountManager instance created: ${this.hashCode()}")
    }

    companion object {
        private const val PREFS_NAME = "account_manager_prefs"
        private const val KEY_ACTIVE_ACCOUNT_ID = "active_account_id"
        private const val KEY_ACCOUNT_LIST = "account_list"
        private const val TAG = "AccountManager"
    }

    /**
     * Represents a saved DHIS2 account
     */
    data class AccountInfo(
        val accountId: String,        // Stable hash: MD5(username@serverUrl)
        val username: String,
        val serverUrl: String,
        val displayName: String,      // For UI: "user123 (play.dhis2.org)"
        val d2DatabaseName: String,   // "dhis2_{accountId}.db"
        val roomDatabaseName: String, // "room_{accountId}.db"
        val lastUsed: Long            // Timestamp
    )

    /**
     * Generate stable account identifier from username + serverUrl
     * Uses MD5 hash to create short, filesystem-safe string
     */
    fun generateAccountId(username: String, serverUrl: String): String {
        val input = "${username.lowercase()}@${serverUrl.lowercase()}"
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(16) // 16 chars
    }

    /**
     * Create AccountInfo for a given username + serverUrl
     */
    fun createAccountInfo(username: String, serverUrl: String): AccountInfo {
        val accountId = generateAccountId(username, serverUrl)
        val normalizedServer = serverUrl.replace(Regex("^https?://"), "")
            .replace("/", "_")
            .take(30)

        return AccountInfo(
            accountId = accountId,
            username = username,
            serverUrl = serverUrl,
            displayName = "$username ($normalizedServer)",
            d2DatabaseName = "dhis2_$accountId",
            roomDatabaseName = "room_$accountId",
            lastUsed = System.currentTimeMillis()
        )
    }

    /**
     * Get or create account info for login
     */
    fun getOrCreateAccount(context: Context, username: String, serverUrl: String): AccountInfo {
        val prefs = getPrefs(context)
        val existingAccounts = getAllAccounts(context)
        val accountId = generateAccountId(username, serverUrl)

        // Find existing account
        val existing = existingAccounts.find { it.accountId == accountId }
        if (existing != null) {
            // Update lastUsed timestamp
            val updated = existing.copy(lastUsed = System.currentTimeMillis())
            saveAccount(context, updated)
            return updated
        }

        // Create new account
        val newAccount = createAccountInfo(username, serverUrl)
        saveAccount(context, newAccount)
        Log.i(TAG, "Created new account: ${newAccount.displayName} (ID: ${newAccount.accountId})")
        return newAccount
    }

    /**
     * Save or update an account in the list
     */
    private fun saveAccount(context: Context, account: AccountInfo) {
        val prefs = getPrefs(context)
        val accounts = getAllAccounts(context).toMutableList()

        // Remove old version if exists
        accounts.removeAll { it.accountId == account.accountId }

        // Add updated version
        accounts.add(account)

        // Save back to SharedPreferences
        saveAccountList(prefs, accounts)
    }

    /**
     * Get all saved accounts, sorted by last used (most recent first)
     */
    fun getAllAccounts(context: Context): List<AccountInfo> {
        val prefs = getPrefs(context)
        val json = prefs.getString(KEY_ACCOUNT_LIST, null)
        Log.d(TAG, "getAllAccounts() called from: ${Thread.currentThread().stackTrace[3]}")
        Log.d(TAG, "SharedPrefs JSON: ${json ?: "NULL"}")

        if (json == null) {
            Log.d(TAG, "No accounts found - JSON is null")
            return emptyList()
        }

        return try {
            // Parse JSON manually (avoiding Gson dependency)
            val accounts = parseAccountListJson(json)
            Log.d(TAG, "Parsed ${accounts.size} accounts: ${accounts.map { it.accountId }}")
            accounts
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse account list", e)
            emptyList()
        }
    }

    /**
     * Get the currently active account ID
     */
    fun getActiveAccountId(context: Context): String? {
        return getPrefs(context).getString(KEY_ACTIVE_ACCOUNT_ID, null)
    }

    /**
     * Set the active account ID
     */
    fun setActiveAccountId(context: Context, accountId: String) {
        getPrefs(context).edit().putString(KEY_ACTIVE_ACCOUNT_ID, accountId).commit()
        Log.d(TAG, "Active account set to: $accountId")
    }

    fun clearActiveAccountId(context: Context) {
        getPrefs(context).edit().remove(KEY_ACTIVE_ACCOUNT_ID).commit()
        Log.d(TAG, "Active account cleared")
    }

    /**
     * Get active account info
     */
    fun getActiveAccount(context: Context): AccountInfo? {
        val activeId = getActiveAccountId(context) ?: return null
        return getAllAccounts(context).find { it.accountId == activeId }
    }

    /**
     * Remove an account and its databases
     */
    fun removeAccount(context: Context, accountId: String): Boolean {
        val prefs = getPrefs(context)
        val accounts = getAllAccounts(context).toMutableList()

        val removed = accounts.removeAll { it.accountId == accountId }
        if (!removed) return false

        saveAccountList(prefs, accounts)

        // Clear active account if it was the removed one
        if (getActiveAccountId(context) == accountId) {
            prefs.edit().remove(KEY_ACTIVE_ACCOUNT_ID).commit()
        }

        // TODO: Delete database files
        // context.getDatabasePath("dhis2_$accountId").delete()
        // context.getDatabasePath("room_$accountId").delete()

        Log.i(TAG, "Removed account: $accountId")
        return true
    }

    /**
     * Check if user has any saved accounts
     */
    fun hasAccounts(context: Context): Boolean {
        return getAllAccounts(context).isNotEmpty()
    }

    /**
     * Clear all accounts (for testing/debugging)
     */
    fun clearAllAccounts(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_ACCOUNT_LIST)
            .remove(KEY_ACTIVE_ACCOUNT_ID)
            .commit()
        Log.w(TAG, "All accounts cleared")
    }

    // ==================== Private Helper Methods ====================

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Save account list to SharedPreferences as JSON
     */
    private fun saveAccountList(prefs: SharedPreferences, accounts: List<AccountInfo>) {
        val json = serializeAccountList(accounts)
        Log.d(TAG, "Saving ${accounts.size} accounts to SharedPrefs")
        Log.d(TAG, "JSON to save: $json")
        val success = prefs.edit().putString(KEY_ACCOUNT_LIST, json).commit()
        Log.d(TAG, "SharedPrefs commit result: $success")
    }

    /**
     * Serialize account list to JSON (manual, no Gson dependency)
     */
    private fun serializeAccountList(accounts: List<AccountInfo>): String {
        val items = accounts.joinToString(",") { account ->
            """
            {
              "accountId":"${account.accountId}",
              "username":"${account.username}",
              "serverUrl":"${account.serverUrl}",
              "displayName":"${account.displayName}",
              "d2DatabaseName":"${account.d2DatabaseName}",
              "roomDatabaseName":"${account.roomDatabaseName}",
              "lastUsed":${account.lastUsed}
            }
            """.trimIndent()
        }
        return "[$items]"
    }

    /**
     * Parse account list from JSON (manual, no Gson dependency)
     */
    private fun parseAccountListJson(json: String): List<AccountInfo> {
        // Simple regex-based parsing (sufficient for our structured JSON)
        val accountPattern = """
            "accountId"\s*:\s*"([^"]+)".*?
            "username"\s*:\s*"([^"]+)".*?
            "serverUrl"\s*:\s*"([^"]+)".*?
            "displayName"\s*:\s*"([^"]+)".*?
            "d2DatabaseName"\s*:\s*"([^"]+)".*?
            "roomDatabaseName"\s*:\s*"([^"]+)".*?
            "lastUsed"\s*:\s*(\d+)
        """.trimIndent().replace("\n", "").toRegex(RegexOption.DOT_MATCHES_ALL)

        return accountPattern.findAll(json).map { match ->
            AccountInfo(
                accountId = match.groupValues[1],
                username = match.groupValues[2],
                serverUrl = match.groupValues[3],
                displayName = match.groupValues[4],
                d2DatabaseName = match.groupValues[5],
                roomDatabaseName = match.groupValues[6],
                lastUsed = match.groupValues[7].toLong()
            )
        }.toList().sortedByDescending { it.lastUsed }
    }
}
