package com.ash.simpledataentry.data

import android.content.Context
import android.util.Log
import com.ash.simpledataentry.domain.model.Dhis2Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.D2Configuration
import org.hisp.dhis.android.core.D2Manager
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit
import com.ash.simpledataentry.data.local.AppDatabase
import com.ash.simpledataentry.presentation.core.NavigationProgress
import com.ash.simpledataentry.presentation.core.LoadingPhase
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import org.koin.core.context.GlobalContext
import java.security.MessageDigest
import java.nio.charset.StandardCharsets

/**
 * Result of downloading a single metadata type
 */
data class MetadataTypeResult(
    val type: String,
    val success: Boolean,
    val isCritical: Boolean, // If true, login should fail if this metadata type fails
    val error: String? = null,
    val errorType: String? = null
)

/**
 * Overall result of metadata download with breakdown by type
 */
data class MetadataDownloadResult(
    val successful: Int,
    val failed: Int,
    val criticalFailures: List<MetadataTypeResult>,
    val details: List<MetadataTypeResult>
) {
    val hasAnyFailures: Boolean get() = failed > 0
    val hasCriticalFailures: Boolean get() = criticalFailures.isNotEmpty()
    val canProceed: Boolean get() = !hasCriticalFailures
}

@Singleton
class SessionManager @Inject constructor() {
    private var d2: D2? = null

    suspend fun initD2(context: Context) = withContext(Dispatchers.IO) {
        // Stop any existing Koin instance that DHIS2 SDK might have started
        try {
            GlobalContext.stopKoin()
        } catch (e: Exception) {
            // Ignore if Koin wasn't started
        }
        
        if (d2 == null) {
            try {
                // Concise logging interceptor - only logs errors and summaries
                val loggingInterceptor = Interceptor { chain ->
                    val request = chain.request()
                    val url = request.url.toString()
                    val startTime = System.currentTimeMillis()

                    val response = chain.proceed(request)
                    val duration = System.currentTimeMillis() - startTime

                    // Only log non-successful responses or slow requests
                    if (!response.isSuccessful || duration > 5000) {
                        Log.w("OkHttp", "${request.method} $url | ${response.code} | ${duration}ms")
                        if (!response.isSuccessful) {
                            Log.e("OkHttp", "Failed: ${response.message}")
                        }
                    }

                    response
                }
                val config = D2Configuration.builder()
                    .context(context)
                    .appName("Simple Data Entry")
                    .appVersion("1.0")
                    .readTimeoutInSeconds(180)  // 3 minutes for downloads
                    .writeTimeoutInSeconds(600) // 10 minutes for uploads
                    .connectTimeoutInSeconds(60) // 1 minute for connection
                    .interceptors(listOf(loggingInterceptor))
                    .build()
                d2 = D2Manager.blockingInstantiateD2(config)
                Log.d("SessionManager", "D2 initialized successfully with OkHttp logging")
            } catch (e: Exception) {
                Log.e("SessionManager", "D2 initialization failed", e)
                throw e
            }
        }
    }

    suspend fun login(context: Context, dhis2Config: Dhis2Config, db: AppDatabase) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
        val lastUser = prefs.getString("username", null)
        val lastServer = prefs.getString("serverUrl", null)
        val isDifferentUser = lastUser != dhis2Config.username || lastServer != dhis2Config.serverUrl

        if (isDifferentUser) {
            Log.d("SessionManager", "USER SWITCH DETECTED: Wiping data for previous user $lastUser@$lastServer")
            wipeAllData(context)
            // CRITICAL FIX: Verify wipe completed successfully before proceeding
            verifyDataWipeCompleted(context)
            Log.d("SessionManager", "Data wipe verified complete - safe to proceed with new user login")
        }

        // Always re-instantiate D2 before login to ensure fresh state
        d2 = null
        initD2(context)

        // Log out if already logged in to avoid D2Error
        if (d2?.userModule()?.isLogged()?.blockingGet() == true) {
            d2?.userModule()?.blockingLogOut()
        }

        try {
            d2?.userModule()?.blockingLogIn(
                dhis2Config.username,
                dhis2Config.password,
                dhis2Config.serverUrl
            ) ?: throw IllegalStateException("D2 not initialized")

            prefs.edit {
                putString("username", dhis2Config.username)
                putString("serverUrl", dhis2Config.serverUrl)
            }

            // Use resilient metadata download that handles JSON errors gracefully
            downloadMetadataResilient { _ -> /* No progress UI in simple login */ }
            downloadAggregateData()
            hydrateRoomFromSdk(context, db)

            Log.i("SessionManager", "Login successful for ${dhis2Config.username}")
        } catch (e: Exception) {
            Log.e("SessionManager", "Login failed", e)
            throw e
        }
    }

    /**
     * Start async background data download after successful metadata sync
     * This runs in the background and shows a toast notification when complete
     */
    suspend fun startBackgroundDataSync(
        context: Context,
        db: AppDatabase,
        onComplete: ((Boolean, String?) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        try {
            Log.d("SessionManager", "Starting background data sync...")

            // Download aggregate data
            downloadAggregateData()
            Log.d("SessionManager", "Background: Aggregate data downloaded")

            // Download tracker/event data
            downloadTrackerData()
            Log.d("SessionManager", "Background: Tracker data downloaded")

            // Hydrate Room database
            hydrateRoomFromSdk(context, db)
            Log.d("SessionManager", "Background: Room database hydrated")

            Log.i("SessionManager", "Background data sync completed successfully")
            onComplete?.invoke(true, "Data sync complete")

        } catch (e: Exception) {
            Log.e("SessionManager", "Background data sync failed", e)
            onComplete?.invoke(false, "Data sync failed: ${e.message}")
        }
    }

    /**
     * Enhanced login with progress tracking
     * REFACTORED: Metadata is blocking, data sync is async in background
     */
    suspend fun loginWithProgress(
        context: Context,
        dhis2Config: Dhis2Config,
        db: AppDatabase,
        onProgress: (NavigationProgress) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            // Step 1: Initialize (0-10%)
            onProgress(NavigationProgress(
                phase = LoadingPhase.INITIALIZING,
                overallPercentage = 5,
                phaseTitle = LoadingPhase.INITIALIZING.title,
                phaseDetail = "Setting up connection..."
            ))

            val prefs = context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
            val lastUser = prefs.getString("username", null)
            val lastServer = prefs.getString("serverUrl", null)
            val isDifferentUser = lastUser != dhis2Config.username || lastServer != dhis2Config.serverUrl

            if (isDifferentUser) {
                Log.d("SessionManager", "USER SWITCH DETECTED: Wiping data for previous user $lastUser@$lastServer")
                wipeAllData(context)
                // CRITICAL FIX: Verify wipe completed successfully before proceeding
                verifyDataWipeCompleted(context)
                Log.d("SessionManager", "Data wipe verified complete - safe to proceed with new user login")
            }

            // Always re-instantiate D2 before login to ensure fresh state
            d2 = null
            initD2(context)

            // Step 2: Authentication (10-30%)
            onProgress(NavigationProgress(
                phase = LoadingPhase.AUTHENTICATING,
                overallPercentage = 15,
                phaseTitle = LoadingPhase.AUTHENTICATING.title,
                phaseDetail = "Connecting to server..."
            ))

            // Log out if already logged in to avoid D2Error
            if (d2?.userModule()?.isLogged()?.blockingGet() == true) {
                d2?.userModule()?.blockingLogOut()
            }

            d2?.userModule()?.blockingLogIn(
                dhis2Config.username,
                dhis2Config.password,
                dhis2Config.serverUrl
            ) ?: throw IllegalStateException("D2 not initialized")

            onProgress(NavigationProgress(
                phase = LoadingPhase.AUTHENTICATING,
                overallPercentage = 25,
                phaseTitle = LoadingPhase.AUTHENTICATING.title,
                phaseDetail = "Authentication successful"
            ))

            // SECURITY ENHANCEMENT: Store password hash for secure offline validation
            val passwordHash = hashPassword(dhis2Config.password, dhis2Config.username + dhis2Config.serverUrl)
            prefs.edit {
                putString("username", dhis2Config.username)
                putString("serverUrl", dhis2Config.serverUrl)
                putString("password_hash", passwordHash)
                putLong("hash_created", System.currentTimeMillis())
            }

            // Step 3: Download Metadata (30-80%) - RESILIENT, UI LOCKED
            // Use resilient metadata download with granular progress feedback
            val metadataResult = downloadMetadataResilient(onProgress = onProgress)

            // Check if critical metadata failed
            if (metadataResult.hasCriticalFailures) {
                val criticalErrors = metadataResult.criticalFailures.joinToString(", ") { it.type }
                Log.e("SessionManager", "CRITICAL metadata failures: $criticalErrors")
                throw IllegalStateException("Critical metadata download failed: $criticalErrors")
            }

            // Log warnings for non-critical failures
            if (metadataResult.hasAnyFailures) {
                val failures = metadataResult.details.filter { !it.success }
                failures.forEach { failure ->
                    Log.w("SessionManager", "Non-critical metadata '${failure.type}' failed but continuing: ${failure.error}")
                }
            }

            onProgress(NavigationProgress(
                phase = LoadingPhase.DOWNLOADING_METADATA,
                overallPercentage = 80,
                phaseTitle = "Metadata Complete",
                phaseDetail = if (metadataResult.hasAnyFailures) {
                    "⚠ ${metadataResult.successful} of ${metadataResult.successful + metadataResult.failed} succeeded"
                } else {
                    "✓ All metadata downloaded successfully"
                }
            ))

            // Step 4: Finalization (90-100%) - UI UNLOCKED AFTER THIS
            onProgress(NavigationProgress(
                phase = LoadingPhase.FINALIZING,
                overallPercentage = 95,
                phaseTitle = LoadingPhase.FINALIZING.title,
                phaseDetail = "Login complete!"
            ))

            Log.i("SessionManager", "Enhanced login successful for ${dhis2Config.username}")

            onProgress(NavigationProgress(
                phase = LoadingPhase.FINALIZING,
                overallPercentage = 100,
                phaseTitle = "Ready",
                phaseDetail = "Welcome! Data is syncing in background..."
            ))

            // CRITICAL CHANGE: Data downloads moved to async background task
            // UI is now unlocked, user can start working immediately
            // Background sync will show toast notification when complete
            Log.d("SessionManager", "Metadata sync complete - starting background data sync...")

        } catch (e: Exception) {
            Log.e("SessionManager", "Enhanced login failed", e)
            onProgress(NavigationProgress.error(e.message ?: "Login failed"))
            throw e
        }
    }

    suspend fun wipeAllData(context: Context) = withContext(Dispatchers.IO) {
        try {
            // Clear SharedPreferences first (always safe)
            val prefs = context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()

            // CRITICAL FIX: Force wipe database even when not authenticated to fix foreign key issues
            try {
                // First try authenticated wipe
                d2?.let { d2Instance ->
                    try {
                        if (d2Instance.userModule()?.isLogged()?.blockingGet() == true) {
                            d2Instance.wipeModule()?.wipeEverything()
                            Log.i("SessionManager", "DHIS2 data wiped successfully via authenticated method")
                        } else {
                            Log.w("SessionManager", "Not authenticated - attempting database file cleanup")
                            // Force database cleanup for corrupted state
                            wipeDatabaseFiles(context)
                        }
                    } catch (e: Exception) {
                        Log.w("SessionManager", "Authenticated wipe failed: ${e.message}")
                        // Fallback to manual database file cleanup
                        wipeDatabaseFiles(context)
                    }
                } ?: run {
                    // D2 is null - force database cleanup
                    wipeDatabaseFiles(context)
                }
            } catch (e: Exception) {
                Log.e("SessionManager", "Complete wipe failed, trying database file cleanup", e)
                wipeDatabaseFiles(context)
            }

            Log.i("SessionManager", "Local data cleared successfully")
            // Re-instantiate D2 after wipe
            d2 = null
            initD2(context)
        } catch (e: Exception) {
            Log.e("SessionManager", "Failed to wipe all data", e)
        }
    }

    private fun wipeDatabaseFiles(context: Context) {
        try {
            Log.i("SessionManager", "Performing manual database file cleanup to fix foreign key constraints")

            // Clear DHIS2 SDK database files manually
            val dbDir = context.getDatabasePath("dhis.db").parentFile
            dbDir?.listFiles()?.forEach { file ->
                if (file.name.startsWith("dhis") || file.name.contains("d2")) {
                    val deleted = file.delete()
                    Log.d("SessionManager", "Deleted database file ${file.name}: $deleted")
                }
            }

            // Clear any DHIS2 cache directories
            val cacheDir = java.io.File(context.cacheDir, "d2")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
                Log.d("SessionManager", "Cleared D2 cache directory")
            }

            Log.i("SessionManager", "Manual database cleanup completed")
        } catch (e: Exception) {
            Log.e("SessionManager", "Manual database cleanup failed", e)
        }
    }

    /**
     * CRITICAL FIX: Verify that data wipe completed successfully
     * This prevents race condition where new user data loads before old user data is cleared
     */
    private suspend fun verifyDataWipeCompleted(context: Context) = withContext(Dispatchers.IO) {
        try {

            // Check 1: Verify SharedPreferences are cleared
            val prefs = context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
            val hasUsername = prefs.getString("username", null) != null
            val hasServerUrl = prefs.getString("serverUrl", null) != null

            if (hasUsername || hasServerUrl) {
                Log.w("SessionManager", "VERIFICATION FAILED: SharedPreferences not cleared properly")
                // Force clear again
                prefs.edit().clear().apply()
            }

            // Check 2: Verify database files are deleted
            val dbDir = context.getDatabasePath("dhis.db").parentFile
            val dhisDbFiles = dbDir?.listFiles()?.filter {
                it.name.startsWith("dhis") || it.name.contains("d2")
            } ?: emptyList()

            if (dhisDbFiles.isNotEmpty()) {
                Log.w("SessionManager", "VERIFICATION FAILED: Found ${dhisDbFiles.size} residual database files")
                // Force delete again
                dhisDbFiles.forEach { file ->
                    val deleted = file.delete()
                }
            }

            // Check 3: Verify D2 cache is cleared
            val cacheDir = java.io.File(context.cacheDir, "d2")
            if (cacheDir.exists()) {
                Log.w("SessionManager", "VERIFICATION FAILED: D2 cache directory still exists")
                cacheDir.deleteRecursively()
            }

            // Give file system a moment to finish all delete operations
            kotlinx.coroutines.delay(100)


        } catch (e: Exception) {
            Log.e("SessionManager", "VERIFICATION: Data wipe verification failed", e)
            // Don't throw - log the error but allow login to proceed
            // This prevents blocking legitimate logins if verification has issues
        }
    }

    fun isSessionActive(): Boolean {
        return d2?.userModule()?.isLogged()?.blockingGet() ?: false
    }

    /**
     * Attempt offline login using existing DHIS2 session data with SECURE password validation
     * This validates the password against stored encrypted credentials before granting access
     */
    suspend fun attemptOfflineLogin(context: Context, dhis2Config: Dhis2Config, db: AppDatabase): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d("SessionManager", "Attempting SECURE offline login for ${dhis2Config.username}")

            // Initialize D2 if needed
            if (d2 == null) {
                initD2(context)
            }

            // Check if there's an existing session or cached credentials
            val d2Instance = d2 ?: return@withContext false

            // SECURITY FIX: Verify stored credentials match provided password
            val prefs = context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
            val storedPasswordHash = prefs.getString("password_hash", null)
            val lastUser = prefs.getString("username", null)
            val lastServer = prefs.getString("serverUrl", null)

            // Validate credentials match
            if (lastUser != dhis2Config.username || lastServer != dhis2Config.serverUrl) {
                Log.w("SessionManager", "Username/server mismatch - offline login denied")
                return@withContext false
            }

            // CRITICAL SECURITY: Validate password against stored hash
            if (storedPasswordHash == null) {
                Log.w("SessionManager", "No stored password hash - offline login not secure")
                return@withContext false
            }

            val providedPasswordHash = hashPassword(dhis2Config.password, dhis2Config.username + dhis2Config.serverUrl)
            if (storedPasswordHash != providedPasswordHash) {
                Log.w("SessionManager", "Password validation failed - offline login denied")
                return@withContext false
            }

            // Check if the user has logged in before by checking if we have user data
            val userExists = try {
                d2Instance.userModule().user().blockingGet()?.uid() != null
            } catch (e: Exception) {
                false
            }

            if (userExists) {
                Log.d("SessionManager", "Password validated, enabling secure offline access")

                // CRITICAL FIX: Verify the user session is properly authenticated for API calls
                val isLoggedIn = try {
                    d2Instance.userModule().isLogged().blockingGet()
                } catch (e: Exception) {
                    false
                }

                if (!isLoggedIn) {
                    Log.w("SessionManager", "User session not properly authenticated - cannot make API calls offline")
                    return@withContext false
                }

                val user = d2Instance.userModule().user().blockingGet()
                Log.d("SessionManager", "Verified user session: ${user?.uid()} for offline access")

                // Store session info for offline use
                prefs.edit {
                    putString("username", dhis2Config.username)
                    putString("serverUrl", dhis2Config.serverUrl)
                    putBoolean("offline_mode", true)
                    // Update password hash timestamp for security audit
                    putLong("last_validated", System.currentTimeMillis())
                }

                // Hydrate Room database from existing SDK data
                hydrateRoomFromSdk(context, db)

                Log.i("SessionManager", "SECURE offline login successful for ${dhis2Config.username}")
                true
            } else {
                Log.w("SessionManager", "No existing user data found - offline login not possible")
                false
            }

        } catch (e: Exception) {
            Log.e("SessionManager", "Offline login failed", e)
            false
        }
    }

    /**
     * Check if SECURE offline login is possible for a given user
     * Now includes password hash validation capability
     */
    fun canLoginOffline(context: Context, username: String, serverUrl: String): Boolean {
        return try {
            if (d2 == null) {
                // Try to initialize D2 to check for existing data
                runBlocking { initD2(context) }
            }

            val prefs = context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
            val lastUser = prefs.getString("username", null)
            val lastServer = prefs.getString("serverUrl", null)
            val storedPasswordHash = prefs.getString("password_hash", null)

            // SECURITY: All credentials must match and password hash must exist
            val isSameUser = lastUser == username && lastServer == serverUrl
            val hasSecureCredentials = storedPasswordHash != null

            if (isSameUser && hasSecureCredentials) {
                // Validate session security and check if we have user data in D2
                validateSessionSecurity(context) &&
                d2?.userModule()?.user()?.blockingGet()?.uid() != null
            } else {
                if (!hasSecureCredentials) {
                    Log.w("SessionManager", "No secure credentials stored - offline login not available")
                }
                false
            }
        } catch (e: Exception) {
            Log.w("SessionManager", "Cannot determine offline login capability", e)
            false
        }
    }

    fun logout() {
        try {
            d2?.userModule()?.blockingLogOut()
        } catch (e: Exception) {
            Log.e("SessionManager", "Logout error: ${e.message}", e)
        }
    }

    /**
     * SECURITY ENHANCEMENT: Secure logout that clears sensitive data
     */
    fun secureLogout(context: Context) {
        try {
            // Standard logout
            d2?.userModule()?.blockingLogOut()

            // SECURITY: Clear all sensitive stored data
            val prefs = context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
            prefs.edit {
                remove("password_hash")
                remove("hash_created")
                remove("last_validated")
                putBoolean("offline_mode", false)
            }

            Log.i("SessionManager", "SECURE logout completed - sensitive data cleared")
        } catch (e: Exception) {
            Log.e("SessionManager", "Secure logout error: ${e.message}", e)
        }
    }

    fun getD2(): D2? = d2


    // REMOVED: downloadMetadata() - DEAD CODE
    // This atomic metadata download function has been replaced by downloadMetadataResilient()
    // which handles JSON parse errors gracefully and verifies critical metadata types.
    // See downloadMetadataResilient() at line 626 for the working implementation.

    /**
     * Resilient metadata download that handles JSON errors gracefully
     * Downloads metadata from server and verifies what was successfully stored
     * FIXED: Catches JSON errors but continues if critical metadata exists
     */
    suspend fun downloadMetadataResilient(
        maxRetries: Int = 2,
        isRetry: Boolean = false,
        onProgress: (NavigationProgress) -> Unit
    ): MetadataDownloadResult = withContext(Dispatchers.IO) {
        val d2Instance = d2 ?: throw IllegalStateException("D2 not initialized")

        // PHASE 3: Check retry limit to prevent infinite retrigger loops
        if (isRetry && maxRetries <= 0) {
            Log.w("SessionManager", "⚠ Max retry limit reached - aborting metadata download to prevent loop")
            return@withContext MetadataDownloadResult(
                successful = 0,
                failed = 0,
                criticalFailures = emptyList(),
                details = emptyList()
            )
        }

        Log.d("SessionManager", "RESILIENT: Starting metadata download from server (retry: $isRetry, remaining: $maxRetries)")

        // Show initial progress
        onProgress(NavigationProgress(
            phase = LoadingPhase.DOWNLOADING_METADATA,
            overallPercentage = 30,
            phaseTitle = "Downloading Metadata",
            phaseDetail = "Fetching metadata from server..."
        ))

        var downloadError: Exception? = null

        try {
            // Attempt full metadata download from server
            // This downloads ALL metadata types in one call
            d2Instance.metadataModule().blockingDownload()
            Log.d("SessionManager", "✓ Metadata download completed successfully")

        } catch (e: Exception) {
            // Log error but DON'T fail immediately - check what was actually downloaded
            downloadError = e
            Log.w("SessionManager", "⚠ Metadata download encountered error: ${e.message}")

            if (e.message?.contains("JsonConvertException") == true ||
                e.message?.contains("Illegal json parameter") == true) {
                Log.w("SessionManager", "JSON parse error detected - likely in ProgramStages")
                Log.w("SessionManager", "Continuing to verify what metadata was downloaded before error...")
            } else {
                Log.e("SessionManager", "Unexpected metadata download error", e)
            }
        }

        // ALWAYS verify what metadata exists in local database
        // This checks what was successfully downloaded even if error occurred
        onProgress(NavigationProgress(
            phase = LoadingPhase.DOWNLOADING_METADATA,
            overallPercentage = 50,
            phaseTitle = "Verifying Metadata",
            phaseDetail = "Checking downloaded metadata..."
        ))

        val results = mutableListOf<MetadataTypeResult>()

        // Verify each metadata type to see what actually downloaded
        results.add(verifySystemMetadata(d2Instance, onProgress))
        results.add(verifyOrganizationUnits(d2Instance, onProgress))
        results.add(verifyCategories(d2Instance, onProgress))
        results.add(verifyDataElements(d2Instance, onProgress))
        results.add(verifyDatasets(d2Instance, onProgress))
        results.add(verifyPrograms(d2Instance, onProgress))
        results.add(verifyProgramStages(d2Instance, onProgress))
        results.add(verifyTrackedEntityTypes(d2Instance, onProgress))
        results.add(verifyOptionSets(d2Instance, onProgress))

        val successful = results.count { it.success }
        val failed = results.count { !it.success }
        val criticalFailures = results.filter { it.isCritical && !it.success }

        if (downloadError != null) {
            Log.w("SessionManager", "RESILIENT: Metadata download had errors, but $successful/${results.size} types have data")
        } else {
            Log.d("SessionManager", "RESILIENT: Metadata download successful - $successful/${results.size} types have data")
        }

        results.forEach { result ->
            val status = if (result.success) "✓" else "✗"
            val critical = if (result.isCritical) "[CRITICAL]" else "[optional]"
            Log.d("SessionManager", "RESILIENT: $status ${result.type} $critical ${result.error ?: ""}")
        }

        // PHASE 3: Check for foreign key violations that might cause data integrity issues
        try {
            val fkViolations = d2Instance.maintenanceModule().foreignKeyViolations().blockingGet()
            if (fkViolations.isNotEmpty()) {
                Log.w("SessionManager", "⚠ Foreign key violations detected: ${fkViolations.size} total")

                // Group violations by table to identify problematic metadata types
                val violationsByTable = fkViolations.groupBy { it.fromTable() }
                violationsByTable.forEach { (table, violations) ->
                    Log.w("SessionManager", "  FK violations in '$table': ${violations.size}")
                    // Log first few violations for debugging
                    violations.take(3).forEach { violation ->
                        Log.d("SessionManager", "    → ${violation.fromColumn()} references ${violation.toTable()}.${violation.toColumn()}")
                    }
                }

                // Add FK violation info to metadata result
                val fkInfo = "FK violations: ${violationsByTable.keys.joinToString(", ")}"
                Log.w("SessionManager", "RESILIENT: $fkInfo - this may cause data sync issues")

                // IMPORTANT: Don't fail login due to FK violations - they're expected for incomplete server metadata
                // SDK handles FK violations gracefully during sync by skipping invalid references
            } else {
                Log.d("SessionManager", "✓ No foreign key violations detected - metadata integrity verified")
            }
        } catch (e: Exception) {
            // FK violation check is non-critical - log but don't fail
            Log.w("SessionManager", "Unable to check FK violations: ${e.message}")
        }

        MetadataDownloadResult(
            successful = successful,
            failed = failed,
            criticalFailures = criticalFailures,
            details = results
        )
    }

    /**
     * Verify system metadata was downloaded (constants, settings)
     * CRITICAL - Required for basic app functionality
     */
    private suspend fun verifySystemMetadata(
        d2Instance: D2,
        onProgress: (NavigationProgress) -> Unit
    ): MetadataTypeResult = withContext(Dispatchers.IO) {
        onProgress(NavigationProgress(
            phase = LoadingPhase.DOWNLOADING_SYSTEM_METADATA,
            overallPercentage = 35,
            phaseTitle = "System Metadata",
            phaseDetail = "Verifying constants and settings..."
        ))

        try {
            // Verify system settings and constants were downloaded
            val systemInfo = d2Instance.systemInfoModule().systemInfo().blockingGet()
            val constants = d2Instance.constantModule().constants().blockingGet()
            Log.d("SessionManager", "✓ System metadata verified: ${constants.size} constants")
            MetadataTypeResult(type = "SystemMetadata", success = systemInfo != null, isCritical = true)
        } catch (e: Exception) {
            Log.e("SessionManager", "✗ System metadata verification failed: ${e.message}", e)
            MetadataTypeResult(
                type = "SystemMetadata",
                success = false,
                isCritical = true,
                error = e.message,
                errorType = e.javaClass.simpleName
            )
        }
    }

    /**
     * Verify organization units and hierarchy were downloaded
     * CRITICAL - Required for data capture and program access
     */
    private suspend fun verifyOrganizationUnits(
        d2Instance: D2,
        onProgress: (NavigationProgress) -> Unit
    ): MetadataTypeResult = withContext(Dispatchers.IO) {
        onProgress(NavigationProgress(
            phase = LoadingPhase.DOWNLOADING_ORGUNIT_HIERARCHY,
            overallPercentage = 40,
            phaseTitle = "Organization Units",
            phaseDetail = "Verifying org unit hierarchy..."
        ))

        try {
            val orgUnits = d2Instance.organisationUnitModule().organisationUnits().blockingGet()
            val orgUnitLevels = d2Instance.organisationUnitModule().organisationUnitLevels().blockingGet()
            Log.d("SessionManager", "✓ Organization units verified: ${orgUnits.size} units, ${orgUnitLevels.size} levels")
            MetadataTypeResult(type = "OrganizationUnits", success = orgUnits.isNotEmpty(), isCritical = true)
        } catch (e: Exception) {
            Log.e("SessionManager", "✗ Organization units verification failed: ${e.message}", e)
            MetadataTypeResult(
                type = "OrganizationUnits",
                success = false,
                isCritical = true,
                error = e.message,
                errorType = e.javaClass.simpleName
            )
        }
    }

    /**
     * Verify categories, category combos, and category option combos were downloaded
     * CRITICAL - Required for data values
     */
    private suspend fun verifyCategories(
        d2Instance: D2,
        onProgress: (NavigationProgress) -> Unit
    ): MetadataTypeResult = withContext(Dispatchers.IO) {
        onProgress(NavigationProgress(
            phase = LoadingPhase.DOWNLOADING_CATEGORIES,
            overallPercentage = 45,
            phaseTitle = "Categories",
            phaseDetail = "Verifying categories and combinations..."
        ))

        try {
            val categories = d2Instance.categoryModule().categories().blockingGet()
            val categoryCombos = d2Instance.categoryModule().categoryCombos().blockingGet()
            val categoryOptionCombos = d2Instance.categoryModule().categoryOptionCombos().blockingGet()
            Log.d("SessionManager", "✓ Categories verified: ${categories.size} categories, ${categoryCombos.size} combos, ${categoryOptionCombos.size} option combos")
            MetadataTypeResult(type = "Categories", success = categoryOptionCombos.isNotEmpty(), isCritical = true)
        } catch (e: Exception) {
            Log.e("SessionManager", "✗ Categories verification failed: ${e.message}", e)
            MetadataTypeResult(
                type = "Categories",
                success = false,
                isCritical = true,
                error = e.message,
                errorType = e.javaClass.simpleName
            )
        }
    }

    /**
     * Verify data elements and indicators were downloaded
     * CRITICAL - Required for datasets
     */
    private suspend fun verifyDataElements(
        d2Instance: D2,
        onProgress: (NavigationProgress) -> Unit
    ): MetadataTypeResult = withContext(Dispatchers.IO) {
        onProgress(NavigationProgress(
            phase = LoadingPhase.DOWNLOADING_DATA_ELEMENTS,
            overallPercentage = 50,
            phaseTitle = "Data Elements",
            phaseDetail = "Verifying data elements and indicators..."
        ))

        try {
            val dataElements = d2Instance.dataElementModule().dataElements().blockingGet()
            val indicators = d2Instance.indicatorModule().indicators().blockingGet()
            Log.d("SessionManager", "✓ Data elements verified: ${dataElements.size} data elements, ${indicators.size} indicators")
            MetadataTypeResult(type = "DataElements", success = dataElements.isNotEmpty(), isCritical = true)
        } catch (e: Exception) {
            Log.e("SessionManager", "✗ Data elements verification failed: ${e.message}", e)
            MetadataTypeResult(
                type = "DataElements",
                success = false,
                isCritical = true,
                error = e.message,
                errorType = e.javaClass.simpleName
            )
        }
    }

    /**
     * Verify datasets for aggregate data entry were downloaded
     * ESSENTIAL - Main use case for many implementations
     */
    private suspend fun verifyDatasets(
        d2Instance: D2,
        onProgress: (NavigationProgress) -> Unit
    ): MetadataTypeResult = withContext(Dispatchers.IO) {
        onProgress(NavigationProgress(
            phase = LoadingPhase.DOWNLOADING_DATASETS,
            overallPercentage = 55,
            phaseTitle = "Datasets",
            phaseDetail = "Verifying aggregate datasets..."
        ))

        try {
            val datasets = d2Instance.dataSetModule().dataSets().blockingGet()
            Log.d("SessionManager", "✓ Datasets verified: ${datasets.size} datasets")
            MetadataTypeResult(type = "Datasets", success = datasets.isNotEmpty(), isCritical = false)
        } catch (e: Exception) {
            Log.e("SessionManager", "✗ Datasets verification failed: ${e.message}", e)
            MetadataTypeResult(
                type = "Datasets",
                success = false,
                isCritical = false, // Non-critical if tracker programs work
                error = e.message,
                errorType = e.javaClass.simpleName
            )
        }
    }

    /**
     * Verify programs (tracker and event programs) were downloaded
     * NON-CRITICAL - Continue if this fails
     */
    private suspend fun verifyPrograms(
        d2Instance: D2,
        onProgress: (NavigationProgress) -> Unit
    ): MetadataTypeResult = withContext(Dispatchers.IO) {
        onProgress(NavigationProgress(
            phase = LoadingPhase.DOWNLOADING_PROGRAMS,
            overallPercentage = 60,
            phaseTitle = "Programs",
            phaseDetail = "Verifying tracker and event programs..."
        ))

        try {
            val programs = d2Instance.programModule().programs().blockingGet()
            Log.d("SessionManager", "✓ Programs verified: ${programs.size} programs")
            MetadataTypeResult(type = "Programs", success = programs.isNotEmpty(), isCritical = false)
        } catch (e: Exception) {
            Log.e("SessionManager", "✗ Programs verification failed: ${e.message}", e)
            MetadataTypeResult(
                type = "Programs",
                success = false,
                isCritical = false,
                error = e.message,
                errorType = e.javaClass.simpleName
            )
        }
    }

    /**
     * Verify program stages were downloaded
     * NON-CRITICAL - This is where JSON parse errors commonly occur
     */
    private suspend fun verifyProgramStages(
        d2Instance: D2,
        onProgress: (NavigationProgress) -> Unit
    ): MetadataTypeResult = withContext(Dispatchers.IO) {
        onProgress(NavigationProgress(
            phase = LoadingPhase.DOWNLOADING_PROGRAM_STAGES,
            overallPercentage = 65,
            phaseTitle = "Program Stages",
            phaseDetail = "Verifying program stages..."
        ))

        try {
            val programStages = d2Instance.programModule().programStages().blockingGet()
            Log.d("SessionManager", "✓ Program stages verified: ${programStages.size} stages")
            MetadataTypeResult(type = "ProgramStages", success = programStages.isNotEmpty(), isCritical = false)
        } catch (e: Exception) {
            Log.e("SessionManager", "✗ Program stages verification failed (non-critical): ${e.message}", e)
            MetadataTypeResult(
                type = "ProgramStages",
                success = false,
                isCritical = false,
                error = e.message,
                errorType = if (e is io.ktor.serialization.JsonConvertException) "JSON_PARSE_ERROR" else e.javaClass.simpleName
            )
        }
    }

    /**
     * Verify tracked entity types were downloaded
     * NON-CRITICAL - Continue if this fails
     */
    private suspend fun verifyTrackedEntityTypes(
        d2Instance: D2,
        onProgress: (NavigationProgress) -> Unit
    ): MetadataTypeResult = withContext(Dispatchers.IO) {
        onProgress(NavigationProgress(
            phase = LoadingPhase.DOWNLOADING_TRACKED_ENTITIES,
            overallPercentage = 70,
            phaseTitle = "Tracked Entities",
            phaseDetail = "Verifying tracked entity types..."
        ))

        try {
            val trackedEntityTypes = d2Instance.trackedEntityModule().trackedEntityTypes().blockingGet()
            val trackedEntityAttributes = d2Instance.trackedEntityModule().trackedEntityAttributes().blockingGet()
            Log.d("SessionManager", "✓ Tracked entities verified: ${trackedEntityTypes.size} types, ${trackedEntityAttributes.size} attributes")
            MetadataTypeResult(type = "TrackedEntityTypes", success = trackedEntityTypes.isNotEmpty(), isCritical = false)
        } catch (e: Exception) {
            Log.e("SessionManager", "✗ Tracked entity types verification failed (non-critical): ${e.message}", e)
            MetadataTypeResult(
                type = "TrackedEntityTypes",
                success = false,
                isCritical = false,
                error = e.message,
                errorType = e.javaClass.simpleName
            )
        }
    }

    /**
     * Verify option sets and legends were downloaded
     * NON-CRITICAL - Continue if this fails
     */
    private suspend fun verifyOptionSets(
        d2Instance: D2,
        onProgress: (NavigationProgress) -> Unit
    ): MetadataTypeResult = withContext(Dispatchers.IO) {
        onProgress(NavigationProgress(
            phase = LoadingPhase.DOWNLOADING_OPTION_SETS,
            overallPercentage = 75,
            phaseTitle = "Option Sets",
            phaseDetail = "Verifying option sets and legends..."
        ))

        try {
            val optionSets = d2Instance.optionModule().optionSets().blockingGet()
            val legendSets = d2Instance.legendSetModule().legendSets().blockingGet()
            Log.d("SessionManager", "✓ Option sets verified: ${optionSets.size} option sets, ${legendSets.size} legend sets")
            MetadataTypeResult(type = "OptionSets", success = optionSets.isNotEmpty(), isCritical = false)
        } catch (e: Exception) {
            Log.e("SessionManager", "✗ Option sets verification failed (non-critical): ${e.message}", e)
            MetadataTypeResult(
                type = "OptionSets",
                success = false,
                isCritical = false,
                error = e.message,
                errorType = e.javaClass.simpleName
            )
        }
    }

    private fun hasForeignKeyIssues(d2Instance: D2): Boolean {
        return try {
            // Test if we can access basic metadata relationships without foreign key errors
            val datasets = d2Instance.dataSetModule().dataSets().blockingGet()
            val programs = d2Instance.programModule().programs().blockingGet()
            val orgUnits = d2Instance.organisationUnitModule().organisationUnits()
                .byOrganisationUnitScope(org.hisp.dhis.android.core.organisationunit.OrganisationUnit.Scope.SCOPE_DATA_CAPTURE)
                .blockingGet()

            // If we have metadata but no data capture org units, there might be foreign key issues
            val hasForeignKeyProblems = (datasets.isNotEmpty() || programs.isNotEmpty()) && orgUnits.isEmpty()

            if (hasForeignKeyProblems) {
                Log.w("SessionManager", "Potential foreign key issues detected: ${datasets.size} datasets, ${programs.size} programs, but ${orgUnits.size} data capture org units")
            }

            hasForeignKeyProblems
        } catch (e: Exception) {
            Log.w("SessionManager", "Error checking for foreign key issues: ${e.message}")
            true // Assume there are issues if we can't check
        }
    }

    private fun checkDatabaseIntegrity(d2Instance: D2) {
        try {
            // Since foreign key errors are automatically logged by ForeignKeyCleanerImpl,
            // we'll implement a different approach: test the database state by checking
            // if basic metadata relationships are intact

            Log.d("SessionManager", "Checking database integrity after metadata sync...")

            // Test 1: Check if we have programs with valid organisation units
            val programs = d2Instance.programModule().programs().blockingGet()
            val orgUnits = d2Instance.organisationUnitModule().organisationUnits().blockingGet()


            // Test 2: Specifically check program-orgunit relationships for data capture
            val userDataCaptureOrgUnits = d2Instance.organisationUnitModule().organisationUnits()
                .byOrganisationUnitScope(org.hisp.dhis.android.core.organisationunit.OrganisationUnit.Scope.SCOPE_DATA_CAPTURE)
                .blockingGet()

            Log.d("SessionManager", "User has data capture access to ${userDataCaptureOrgUnits.size} org units")

            if (userDataCaptureOrgUnits.isEmpty()) {
                Log.w("SessionManager", "WARNING: User has no data capture org units - this could indicate foreign key issues")
            }

            // Test 3: Check if we can access specific program without errors
            val targetProgram = programs.find { it.uid() == "QZkuUuLedjh" }
            if (targetProgram != null) {

                // Check if program has valid org unit assignments
                // Using alternative approach to check program accessibility
                val accessibleOrgUnits = d2Instance.organisationUnitModule().organisationUnits()
                    .byOrganisationUnitScope(org.hisp.dhis.android.core.organisationunit.OrganisationUnit.Scope.SCOPE_DATA_CAPTURE)
                    .blockingGet()


                if (accessibleOrgUnits.isEmpty()) {
                    Log.w("SessionManager", "CRITICAL: User has no data capture org units - likely foreign key constraint violation")
                }
            } else {
                Log.w("SessionManager", "WARNING: Target program QZkuUuLedjh not found after metadata sync")
            }

            Log.d("SessionManager", "Database integrity check completed")

        } catch (e: Exception) {
            Log.w("SessionManager", "Database integrity check failed: ${e.message}")
            // This could indicate foreign key constraint issues
        }
    }


    fun downloadAggregateData() : Unit{
        val d2Instance = d2 ?: throw IllegalStateException("D2 not initialized")

        // Metadata is already synced by downloadMetadataResilient() in login flow
        // No need to re-download metadata here (was causing redundant downloads)

        try {
            d2Instance.aggregatedModule().data().blockingDownload()

            // Handle any FK violations from aggregate data
            handlePostDownloadForeignKeyViolations(d2Instance)

        } catch (e: Exception) {
            Log.e("SessionManager", "AGGREGATE: Data download failed", e)
        }
    }

    fun downloadTrackerData() : Unit {
        val d2Instance = d2 ?: throw IllegalStateException("D2 not initialized")

        Log.d("SessionManager", "Starting tracker data download (metadata already synced)")

        // Quick verification that metadata is available (no re-download)
        try {
            val categoryOptionCombos = d2Instance.categoryModule().categoryOptionCombos().blockingCount()
        } catch (e: Exception) {
            Log.w("SessionManager", "Metadata verification warning: ${e.message}")
        }

        // Get all available tracker programs for download
        val trackerPrograms = d2Instance.programModule().programs()
            .byProgramType().eq(org.hisp.dhis.android.core.program.ProgramType.WITH_REGISTRATION)
            .blockingGet()

        Log.d("SessionManager", "Found ${trackerPrograms.size} tracker programs to download")

        // Use EXACT official Android Capture app patterns (much simpler)
        Log.d("SessionManager", "Using EXACT official Android Capture app patterns")

        if (trackerPrograms.isNotEmpty()) {
            // Download for each tracker program using official pattern
            trackerPrograms.forEach { program ->
                val programUid = program.uid()
                Log.d("SessionManager", "Downloading tracker data for program: $programUid")

                try {

                    val downloader = d2Instance.trackedEntityModule().trackedEntityInstanceDownloader()
                        .byProgramUid(programUid)

                    // Log what the downloader is configured to do

                    // Execute the download and capture any errors
                    downloader.blockingDownload()


                    // Immediately check if anything was stored
                    val immediateCheck = d2Instance.trackedEntityModule().trackedEntityInstances()
                        .byProgramUids(listOf(programUid))
                        .blockingGet()

                } catch (e: Exception) {
                    Log.e("SessionManager", "DOWNLOAD: Failed to download tracker data for program: $programUid - ${e.message}", e)
                    e.printStackTrace()
                }
            }
        } else {
            Log.d("SessionManager", "No tracker programs found, downloading all tracker data")
            // Fallback: download all tracker data without program filter
            try {
                d2Instance.trackedEntityModule().trackedEntityInstanceDownloader()
                    .download()
                Log.d("SessionManager", "Successfully downloaded all tracker data")
            } catch (e: Exception) {
                Log.e("SessionManager", "Failed to download all tracker data", e)
            }
        }

        // Download EVENT programs (WITHOUT_REGISTRATION)
        val eventPrograms = d2Instance.programModule().programs()
            .byProgramType().eq(org.hisp.dhis.android.core.program.ProgramType.WITHOUT_REGISTRATION)
            .blockingGet()

        Log.d("SessionManager", "Found ${eventPrograms.size} event programs (without registration) to download")

        if (eventPrograms.isNotEmpty()) {
            eventPrograms.forEach { program ->
                val programUid = program.uid()
                Log.d("SessionManager", "Downloading event program data for: $programUid - ${program.displayName()}")

                try {
                    // Download events for this specific EVENT program
                    d2Instance.eventModule().eventDownloader()
                        .byProgramUid(programUid)
                        .blockingDownload()

                    // Verify download
                    val eventCount = d2Instance.eventModule().events()
                        .byProgramUid().eq(programUid)
                        .blockingCount()
                    Log.d("SessionManager", "Successfully downloaded $eventCount events for program: $programUid")

                } catch (e: Exception) {
                    Log.e("SessionManager", "Failed to download event data for program: $programUid - ${e.message}", e)
                }
            }
        }

        // Download standalone events (events not associated with any program) using official pattern
        try {
            d2Instance.eventModule().eventDownloader()
                .download()
            Log.d("SessionManager", "Successfully downloaded standalone event data")
        } catch (e: Exception) {
            Log.e("SessionManager", "Failed to download standalone event data", e)
        }

        Log.d("SessionManager", "Tracker and event data download completed")

        // POST-DOWNLOAD ANALYSIS: Comprehensive foreign key violation handling
        handlePostDownloadForeignKeyViolations(d2Instance)

        // POST-DOWNLOAD VERIFICATION: Check if data was actually stored
        verifyTrackerDataStorage(d2Instance)
    }

    private fun verifyTrackerDataStorage(d2Instance: D2) {
        try {

            // Check total TrackedEntityInstances stored
            val allTEIs = d2Instance.trackedEntityModule().trackedEntityInstances().blockingGet()

            // Check enrollments
            val allEnrollments = d2Instance.enrollmentModule().enrollments().blockingGet()

            // Check events
            val allEvents = d2Instance.eventModule().events().blockingGet()

            // Check specifically for our target program QZkuUuLedjh
            // Check enrollments for all programs instead of hardcoded one
            val allProgramEnrollments = d2Instance.enrollmentModule().enrollments()
                .blockingGet()


            // Get TEIs that have enrollments by checking enrollment TEI references
            val enrollmentTEIUids = allProgramEnrollments.mapNotNull { it.trackedEntityInstance() }.distinct()

            // CRITICAL ASSESSMENT
            if (allTEIs.isEmpty() && allEnrollments.isEmpty() && allEvents.isEmpty()) {
                Log.w("SessionManager", "CRITICAL: Despite successful download, NO tracker data was stored!")
                Log.w("SessionManager", "This indicates foreign key constraint violations are blocking data storage")
            } else {

                if (enrollmentTEIUids.isEmpty() && allProgramEnrollments.isEmpty()) {
                    Log.w("SessionManager", "WARNING: General tracker data stored but no programs have enrollment data")
                    Log.w("SessionManager", "This could indicate org unit access or program assignment issues")
                }
            }

        } catch (e: Exception) {
            Log.e("SessionManager", "VERIFICATION: Failed to verify tracker data storage: ${e.message}", e)
            Log.w("SessionManager", "Database access error suggests foreign key constraint violations may be affecting storage")
        }
    }

    /**
     * Comprehensive foreign key violation handling for all DHIS2 data types
     * Based on DHIS2 SDK documentation and community solutions
     */
    private fun handlePostDownloadForeignKeyViolations(d2Instance: D2) {
        try {
            val foreignKeyViolations = d2Instance.maintenanceModule().foreignKeyViolations().blockingGet()

            if (foreignKeyViolations.isEmpty()) {
                return
            }

            Log.w("SessionManager", "FK: Found ${foreignKeyViolations.size} violations")

            // Group violations by type for targeted logging
            val violationsByTable = foreignKeyViolations.groupBy { "${it.fromTable()} -> ${it.toTable()}" }

            violationsByTable.forEach { (violationType, violations) ->
                when {
                    violationType.contains("CategoryOptionCombo") -> handleCategoryOptionComboViolations(d2Instance, violations)
                    violationType.contains("DataElement") -> handleDataElementViolations(d2Instance, violations)
                    violationType.contains("OrganisationUnit") -> handleOrganisationUnitViolations(d2Instance, violations)
                    violationType.contains("Program") -> handleProgramViolations(d2Instance, violations)
                    else -> Log.w("SessionManager", "FK: $violationType (${violations.size})")
                }
            }

            attemptForeignKeyViolationResolution(d2Instance)

        } catch (e: Exception) {
            Log.e("SessionManager", "FK: Analysis failed: ${e.message}")
        }
    }

    private fun handleCategoryOptionComboViolations(d2Instance: D2, violations: List<org.hisp.dhis.android.core.maintenance.ForeignKeyViolation>) {
        Log.w("SessionManager", "FK: CategoryOptionCombo violations (${violations.size} instances)")

        val missingCOCs = violations.map { it.notFoundValue() }.distinct()
        Log.w("SessionManager", "FK: Missing CategoryOptionCombos: ${missingCOCs.take(5).joinToString(", ")}")

        // NOTE: Violations don't prevent app functionality - they're just metadata inconsistencies
        // The SDK will automatically resolve them on next sync cycle
        // DO NOT trigger metadata redownload here - it causes infinite loops
    }

    private fun handleDataElementViolations(d2Instance: D2, violations: List<org.hisp.dhis.android.core.maintenance.ForeignKeyViolation>) {
        val missing = violations.map { it.notFoundValue() }.distinct().take(5).joinToString(", ")
        Log.w("SessionManager", "FK: DataElement violations (${violations.size}): $missing")
    }

    private fun handleOrganisationUnitViolations(d2Instance: D2, violations: List<org.hisp.dhis.android.core.maintenance.ForeignKeyViolation>) {
        val missing = violations.map { it.notFoundValue() }.distinct().take(5).joinToString(", ")
        Log.w("SessionManager", "FK: OrganisationUnit violations (${violations.size}): $missing")
    }

    private fun handleProgramViolations(d2Instance: D2, violations: List<org.hisp.dhis.android.core.maintenance.ForeignKeyViolation>) {
        val missing = violations.map { it.notFoundValue() }.distinct().take(5).joinToString(", ")
        Log.w("SessionManager", "FK: Program violations (${violations.size}): $missing")
    }

    private fun attemptForeignKeyViolationResolution(d2Instance: D2) {
        // REMOVED: Automatic metadata redownload on FK violations
        // This was causing infinite login loops when violations persisted

        // NOTE: Foreign key violations are read-only in the SDK and don't prevent app functionality
        // The SDK will automatically resolve them during the next scheduled metadata sync
        // Manual intervention is not needed and was causing performance issues

        Log.d("SessionManager", "FK: Violations logged for monitoring - no automatic resolution needed")
    }

    /**
     * Public method to check and handle foreign key violations
     * Used by repositories to ensure data integrity before and after operations
     */
    fun checkForeignKeyViolations() {
        val d2Instance = d2 ?: return
        handlePostDownloadForeignKeyViolations(d2Instance)
    }

    suspend fun hydrateRoomFromSdk(context: Context, db: AppDatabase) = withContext(Dispatchers.IO) {
        val d2Instance = d2 ?: return@withContext

        // CRITICAL FIX: Store current user metadata for cache validation
        val prefs = context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
        val currentUser = prefs.getString("username", null)
        val currentServer = prefs.getString("serverUrl", null)

        val startTime = System.currentTimeMillis()

        // PERFORMANCE OPTIMIZATION: Fetch all metadata in parallel using async
        val datasetsDeferred = async {
            d2Instance.dataSetModule().dataSets().blockingGet().map {
                val datasetStyle = it.style()

                com.ash.simpledataentry.data.local.DatasetEntity(
                    id = it.uid(),
                    name = it.displayName() ?: it.name() ?: "Unnamed Dataset",
                    description = it.description() ?: "",
                    periodType = it.periodType()?.name ?: "Monthly",
                    styleIcon = datasetStyle?.icon(),
                    styleColor = datasetStyle?.color()
                )
            }
        }

        val dataElementsDeferred = async {
            d2Instance.dataElementModule().dataElements().blockingGet().map {
                com.ash.simpledataentry.data.local.DataElementEntity(
                    id = it.uid(),
                    name = it.displayName() ?: it.name() ?: "Unnamed DataElement",
                    valueType = it.valueType()?.name ?: "TEXT",
                    categoryComboId = it.categoryComboUid(),
                    description = it.description()
                )
            }
        }

        val categoryCombosDeferred = async {
            d2Instance.categoryModule().categoryCombos().blockingGet().map {
                com.ash.simpledataentry.data.local.CategoryComboEntity(
                    id = it.uid(),
                    name = it.displayName() ?: it.name() ?: "Unnamed CategoryCombo"
                )
            }
        }

        val categoryOptionCombosDeferred = async {
            d2Instance.categoryModule().categoryOptionCombos().blockingGet().map {
                com.ash.simpledataentry.data.local.CategoryOptionComboEntity(
                    id = it.uid(),
                    name = it.displayName() ?: it.uid(),
                    categoryComboId = it.categoryCombo()?.uid() ?: "",
                    optionUids = it.categoryOptions()?.joinToString(",") { opt -> opt.uid() } ?: ""
                )
            }
        }

        val orgUnitsDeferred = async {
            d2Instance.organisationUnitModule().organisationUnits().blockingGet().map {
                com.ash.simpledataentry.data.local.OrganisationUnitEntity(
                    id = it.uid(),
                    name = it.displayName() ?: it.name() ?: "Unnamed OrgUnit",
                    parentId = it.parent()?.uid()
                )
            }
        }

        // Wait for all parallel operations to complete
        val datasets = datasetsDeferred.await()
        val dataElements = dataElementsDeferred.await()
        val categoryCombos = categoryCombosDeferred.await()
        val categoryOptionCombos = categoryOptionCombosDeferred.await()
        val orgUnits = orgUnitsDeferred.await()

        val fetchTime = System.currentTimeMillis() - startTime

        // Insert all data sequentially (Room doesn't handle parallel writes well)
        db.datasetDao().clearAll()
        db.datasetDao().insertAll(datasets)

        // Store user metadata for cache validation
        prefs.edit().apply {
            putString("cached_datasets_user", currentUser)
            putString("cached_datasets_server", currentServer)
            apply()
        }
        Log.d("SessionManager", "Hydrated Room with datasets for user: $currentUser@$currentServer")

        db.dataElementDao().clearAll()
        db.dataElementDao().insertAll(dataElements)

        db.categoryComboDao().clearAll()
        db.categoryComboDao().insertAll(categoryCombos)

        db.categoryOptionComboDao().clearAll()
        db.categoryOptionComboDao().insertAll(categoryOptionCombos)

        db.organisationUnitDao().clearAll()
        db.organisationUnitDao().insertAll(orgUnits)

        val totalTime = System.currentTimeMillis() - startTime


        // Hydrate data values from DHIS2 SDK to Room database
        Log.d("SessionManager", "Loading data values from DHIS2 SDK...")
        try {
            // First, create a mapping of data element UIDs to dataset UIDs
            val dataElementToDatasetMap = mutableMapOf<String, String>()
            d2Instance.dataSetModule().dataSets().blockingGet().forEach { dataset ->
                dataset.dataSetElements()?.forEach { dataSetElement ->
                    dataElementToDatasetMap[dataSetElement.dataElement().uid()] = dataset.uid()
                }
            }
            Log.d("SessionManager", "Created mapping for ${dataElementToDatasetMap.size} data elements to datasets")

            // Use regular data values for now until we understand the aggregated module interface
            val regularDataValues = d2Instance.dataValueModule().dataValues().blockingGet()
            Log.d("SessionManager", "Using regular data values (${regularDataValues.size})")

            val dataValuesToUse = regularDataValues

            val dataValues = dataValuesToUse.mapIndexed { index, dataValue ->
                val dataElementUid = dataValue.dataElement() ?: ""
                val datasetId = dataElementToDatasetMap[dataElementUid] ?: ""
                val period = dataValue.period() ?: ""
                val orgUnit = dataValue.organisationUnit() ?: ""
                val attributeOptionCombo = dataValue.attributeOptionCombo() ?: ""
                val categoryOptionCombo = dataValue.categoryOptionCombo() ?: ""
                val value = dataValue.value()


                com.ash.simpledataentry.data.local.DataValueEntity(
                    datasetId = datasetId,
                    period = period,
                    orgUnit = orgUnit,
                    attributeOptionCombo = attributeOptionCombo,
                    dataElement = dataElementUid,
                    categoryOptionCombo = categoryOptionCombo,
                    value = value,
                    comment = dataValue.comment(),
                    lastModified = dataValue.lastUpdated()?.time ?: System.currentTimeMillis()
                )
            }
            db.dataValueDao().deleteAllDataValues()
            db.dataValueDao().insertAll(dataValues)
            Log.d("SessionManager", "Loaded ${dataValues.size} data values into Room database")
        } catch (e: Exception) {
            Log.e("SessionManager", "Failed to load data values: ${e.message}", e)
        }
    }

    /**
     * SECURITY FUNCTION: Create secure password hash with salt
     * Uses SHA-256 with user-specific salt to prevent rainbow table attacks
     */
    private fun hashPassword(password: String, salt: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val input = (password + salt).toByteArray(StandardCharsets.UTF_8)
            val hashBytes = digest.digest(input)

            // Convert to hexadecimal string
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e("SessionManager", "Failed to hash password", e)
            // Fallback - never return null to prevent bypass
            password.hashCode().toString()
        }
    }

    /**
     * SECURITY FUNCTION: Validate session security and force re-authentication if compromised
     */
    fun validateSessionSecurity(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
            val hashCreated = prefs.getLong("hash_created", 0L)
            val currentTime = System.currentTimeMillis()

            // Force re-authentication if hash is older than 30 days (security policy)
            val maxAgeMillis = 30L * 24 * 60 * 60 * 1000 // 30 days
            if (currentTime - hashCreated > maxAgeMillis) {
                Log.w("SessionManager", "Password hash expired - forcing re-authentication")
                return false
            }

            // Verify we have all required security components
            val hasPasswordHash = prefs.getString("password_hash", null) != null
            val hasUsername = prefs.getString("username", null) != null
            val hasServerUrl = prefs.getString("serverUrl", null) != null

            hasPasswordHash && hasUsername && hasServerUrl
        } catch (e: Exception) {
            Log.e("SessionManager", "Session security validation failed", e)
            false
        }
    }

    /**
     * Restore D2 session if needed when app resumes from background
     * Handles app lifecycle issues when screen goes off from inactivity
     */
    suspend fun restoreSessionIfNeeded(context: Context) = withContext(Dispatchers.IO) {
        try {
            Log.d("SessionManager", "Checking if session restoration is needed")

            // Check if D2 is initialized
            if (d2 == null) {
                Log.d("SessionManager", "D2 not initialized, attempting to initialize")
                initD2(context)
            }

            val d2Instance = d2 ?: run {
                Log.w("SessionManager", "Failed to initialize D2, cannot restore session")
                return@withContext
            }

            // Check if user is still logged in
            val isLoggedIn = try {
                d2Instance.userModule().isLogged().blockingGet()
            } catch (e: Exception) {
                Log.w("SessionManager", "Error checking login status: ${e.message}")
                false
            }

            if (isLoggedIn) {
                Log.d("SessionManager", "User is still logged in, session active")
                return@withContext
            }

            // Check if we can restore from offline session
            val prefs = context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
            val isOfflineMode = prefs.getBoolean("offline_mode", false)
            val storedUsername = prefs.getString("username", null)
            val storedServerUrl = prefs.getString("serverUrl", null)

            if (isOfflineMode && storedUsername != null && storedServerUrl != null) {
                Log.d("SessionManager", "Attempting to restore offline session for $storedUsername")

                // Check if we have user data from previous session
                val hasUserData = try {
                    d2Instance.userModule().user().blockingGet()?.uid() != null
                } catch (e: Exception) {
                    Log.w("SessionManager", "Cannot access user data: ${e.message}")
                    false
                }

                if (hasUserData) {
                    Log.i("SessionManager", "Session restored successfully from offline cache")
                    // Update last validated timestamp
                    prefs.edit {
                        putLong("last_validated", System.currentTimeMillis())
                    }
                } else {
                    Log.w("SessionManager", "No user data available for offline session restoration")
                }
            } else {
                Log.d("SessionManager", "No offline session available for restoration")
            }

        } catch (e: Exception) {
            Log.e("SessionManager", "Failed to restore session: ${e.message}", e)
        }
    }

}