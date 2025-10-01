package com.ash.simpledataentry.data

import android.content.Context
import android.util.Log
import com.ash.simpledataentry.domain.model.Dhis2Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
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
                // Add OkHttp logging interceptor
                val loggingInterceptor = Interceptor { chain ->
                    val request = chain.request()
                    Log.d("OkHttp", "Request: ${request.method} ${request.url}")
                    val response = chain.proceed(request)
                    Log.d("OkHttp", "Response: ${response.code} ${response.message}")
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
            wipeAllData(context)
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

            downloadMetadata()
            downloadAggregateData()
            hydrateRoomFromSdk(context, db)

            Log.i("SessionManager", "Login successful for ${dhis2Config.username}")
        } catch (e: Exception) {
            Log.e("SessionManager", "Login failed", e)
            throw e
        }
    }

    /**
     * Enhanced login with progress tracking
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
                wipeAllData(context)
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

            // Step 3: Download Metadata (30-60%)
            onProgress(NavigationProgress(
                phase = LoadingPhase.DOWNLOADING_METADATA,
                overallPercentage = 35,
                phaseTitle = LoadingPhase.DOWNLOADING_METADATA.title,
                phaseDetail = "Downloading configuration..."
            ))

            downloadMetadata()

            onProgress(NavigationProgress(
                phase = LoadingPhase.DOWNLOADING_METADATA,
                overallPercentage = 55,
                phaseTitle = LoadingPhase.DOWNLOADING_METADATA.title,
                phaseDetail = "Metadata downloaded successfully"
            ))

            // Step 4: Download Data (60-80%)
            onProgress(NavigationProgress(
                phase = LoadingPhase.DOWNLOADING_DATA,
                overallPercentage = 65,
                phaseTitle = LoadingPhase.DOWNLOADING_DATA.title,
                phaseDetail = "Downloading your data..."
            ))

            downloadAggregateData()

            onProgress(NavigationProgress(
                phase = LoadingPhase.DOWNLOADING_DATA,
                overallPercentage = 70,
                phaseTitle = LoadingPhase.DOWNLOADING_DATA.title,
                phaseDetail = "Aggregate data downloaded successfully"
            ))

            // Download tracker/event data
            onProgress(NavigationProgress(
                phase = LoadingPhase.DOWNLOADING_DATA,
                overallPercentage = 75,
                phaseTitle = LoadingPhase.DOWNLOADING_DATA.title,
                phaseDetail = "Downloading tracker data..."
            ))

            downloadTrackerData()

            onProgress(NavigationProgress(
                phase = LoadingPhase.DOWNLOADING_DATA,
                overallPercentage = 80,
                phaseTitle = LoadingPhase.DOWNLOADING_DATA.title,
                phaseDetail = "Tracker data downloaded successfully"
            ))

            // Step 5: Database Preparation (80-95%)
            onProgress(NavigationProgress(
                phase = LoadingPhase.PREPARING_DATABASE,
                overallPercentage = 85,
                phaseTitle = LoadingPhase.PREPARING_DATABASE.title,
                phaseDetail = "Preparing local database..."
            ))

            hydrateRoomFromSdk(context, db)

            onProgress(NavigationProgress(
                phase = LoadingPhase.PREPARING_DATABASE,
                overallPercentage = 90,
                phaseTitle = LoadingPhase.PREPARING_DATABASE.title,
                phaseDetail = "Database setup complete"
            ))

            // Step 6: Finalization (95-100%)
            onProgress(NavigationProgress(
                phase = LoadingPhase.FINALIZING,
                overallPercentage = 98,
                phaseTitle = LoadingPhase.FINALIZING.title,
                phaseDetail = "Login complete!"
            ))

            Log.i("SessionManager", "Enhanced login successful for ${dhis2Config.username}")

            onProgress(NavigationProgress(
                phase = LoadingPhase.FINALIZING,
                overallPercentage = 100,
                phaseTitle = "Ready",
                phaseDetail = "Welcome to DHIS2 Data Entry!"
            ))

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


    fun downloadMetadata() : Unit{
        val d2Instance = d2 ?: throw IllegalStateException("D2 not initialized")

        Log.d("SessionManager", "Starting metadata download - targeting user accessible data only")

        try {
            // CRITICAL FIX: Clear corrupted data before downloading new metadata
            try {
                Log.d("SessionManager", "Pre-cleaning any corrupted foreign key relationships")
                d2Instance.wipeModule()?.wipeData()
                Log.d("SessionManager", "Data pre-cleaning completed")
            } catch (e: Exception) {
                Log.w("SessionManager", "Data pre-cleaning failed (continuing anyway): ${e.message}")
            }

            // Download metadata with better error handling
            // The SDK should automatically filter based on user permissions
            d2Instance.metadataModule().blockingDownload()

            Log.d("SessionManager", "Metadata download completed successfully")

            // Check for foreign key constraint errors that could corrupt tracker data
            checkDatabaseIntegrity(d2Instance)

            // CRITICAL FIX: If we detect foreign key issues, force a clean re-download
            if (hasForeignKeyIssues(d2Instance)) {
                Log.w("SessionManager", "Foreign key issues detected - forcing clean metadata re-download")
                d2Instance.wipeModule()?.wipeData()
                d2Instance.metadataModule().blockingDownload()
                Log.d("SessionManager", "Clean metadata re-download completed")
            }

        } catch (e: Exception) {
            Log.e("SessionManager", "Metadata download failed", e)
            throw e
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

            Log.d("SessionManager", "Database state: ${programs.size} programs, ${orgUnits.size} org units")

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
                Log.d("SessionManager", "Target program QZkuUuLedjh found: ${targetProgram.displayName()}")

                // Check if program has valid org unit assignments
                // Using alternative approach to check program accessibility
                val accessibleOrgUnits = d2Instance.organisationUnitModule().organisationUnits()
                    .byOrganisationUnitScope(org.hisp.dhis.android.core.organisationunit.OrganisationUnit.Scope.SCOPE_DATA_CAPTURE)
                    .blockingGet()

                Log.d("SessionManager", "Program QZkuUuLedjh accessible through ${accessibleOrgUnits.size} data capture org units")

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

        // Apply same comprehensive metadata sync for aggregate data to prevent FK violations
        Log.d("SessionManager", "AGGREGATE: Ensuring metadata dependencies before aggregate data download...")
        try {
            // Force complete metadata sync first
            d2Instance.metadataModule().blockingDownload()

            // Check CategoryOptionCombo dependencies for DataValues
            val categoryOptionCombos = d2Instance.categoryModule().categoryOptionCombos().blockingGet()
            Log.d("SessionManager", "AGGREGATE: Found ${categoryOptionCombos.size} CategoryOptionCombos available")

        } catch (e: Exception) {
            Log.e("SessionManager", "AGGREGATE: Metadata sync failed", e)
        }

        try {
            d2Instance.aggregatedModule().data().blockingDownload()
            Log.d("SessionManager", "AGGREGATE: Data download completed")

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
            Log.d("SessionManager", "Metadata available: $categoryOptionCombos category option combos")
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
                    Log.d("SessionManager", "DOWNLOAD: Starting download for program: $programUid")

                    val downloader = d2Instance.trackedEntityModule().trackedEntityInstanceDownloader()
                        .byProgramUid(programUid)

                    // Log what the downloader is configured to do
                    Log.d("SessionManager", "DOWNLOAD: Downloader configured for program filter: $programUid")

                    // Execute the download and capture any errors
                    downloader.blockingDownload()

                    Log.d("SessionManager", "DOWNLOAD: Download call completed for program: $programUid")

                    // Immediately check if anything was stored
                    val immediateCheck = d2Instance.trackedEntityModule().trackedEntityInstances()
                        .byProgramUids(listOf(programUid))
                        .blockingGet()
                    Log.d("SessionManager", "DOWNLOAD: Immediately after download, found ${immediateCheck.size} TEIs for program $programUid")

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

        // Download standalone events using official pattern
        try {
            d2Instance.eventModule().eventDownloader()
                .download()
            Log.d("SessionManager", "Successfully downloaded event data")
        } catch (e: Exception) {
            Log.e("SessionManager", "Failed to download event data", e)
        }

        Log.d("SessionManager", "Tracker data download completed")

        // POST-DOWNLOAD ANALYSIS: Comprehensive foreign key violation handling
        handlePostDownloadForeignKeyViolations(d2Instance)

        // POST-DOWNLOAD VERIFICATION: Check if data was actually stored
        verifyTrackerDataStorage(d2Instance)
    }

    private fun verifyTrackerDataStorage(d2Instance: D2) {
        try {
            Log.d("SessionManager", "VERIFICATION: Checking if tracker data was actually stored...")

            // Check total TrackedEntityInstances stored
            val allTEIs = d2Instance.trackedEntityModule().trackedEntityInstances().blockingGet()
            Log.d("SessionManager", "VERIFICATION: Found ${allTEIs.size} total TEIs in local database")

            // Check enrollments
            val allEnrollments = d2Instance.enrollmentModule().enrollments().blockingGet()
            Log.d("SessionManager", "VERIFICATION: Found ${allEnrollments.size} total enrollments in local database")

            // Check events
            val allEvents = d2Instance.eventModule().events().blockingGet()
            Log.d("SessionManager", "VERIFICATION: Found ${allEvents.size} total events in local database")

            // Check specifically for our target program QZkuUuLedjh
            // Check enrollments for all programs instead of hardcoded one
            val allProgramEnrollments = d2Instance.enrollmentModule().enrollments()
                .blockingGet()

            Log.d("SessionManager", "VERIFICATION: Found ${allProgramEnrollments.size} enrollments across all programs")

            // Get TEIs that have enrollments by checking enrollment TEI references
            val enrollmentTEIUids = allProgramEnrollments.mapNotNull { it.trackedEntityInstance() }.distinct()
            Log.d("SessionManager", "VERIFICATION: Found ${enrollmentTEIUids.size} unique TEIs with enrollments")

            // CRITICAL ASSESSMENT
            if (allTEIs.isEmpty() && allEnrollments.isEmpty() && allEvents.isEmpty()) {
                Log.w("SessionManager", "CRITICAL: Despite successful download, NO tracker data was stored!")
                Log.w("SessionManager", "This indicates foreign key constraint violations are blocking data storage")
            } else {
                Log.d("SessionManager", "VERIFICATION: Tracker data storage appears to be working correctly")

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
            Log.d("SessionManager", "FK ANALYSIS: Checking for foreign key violations after data download...")

            // Get all foreign key violations from the SDK
            val foreignKeyViolations = d2Instance.maintenanceModule().foreignKeyViolations().blockingGet()

            if (foreignKeyViolations.isEmpty()) {
                Log.d("SessionManager", "FK ANALYSIS: No foreign key violations detected")
                return
            }

            Log.w("SessionManager", "FK ANALYSIS: Found ${foreignKeyViolations.size} foreign key violations")

            // Group violations by type for systematic handling
            val violationsByTable = foreignKeyViolations.groupBy { "${it.fromTable()} -> ${it.toTable()}" }

            violationsByTable.forEach { (violationType, violations) ->
                Log.w("SessionManager", "FK VIOLATION TYPE: $violationType (${violations.size} instances)")

                // Handle specific violation types with targeted solutions
                when {
                    // CategoryOptionCombo violations (most common)
                    violationType.contains("CategoryOptionCombo") -> {
                        handleCategoryOptionComboViolations(d2Instance, violations)
                    }

                    // DataElement violations
                    violationType.contains("DataElement") -> {
                        handleDataElementViolations(d2Instance, violations)
                    }

                    // OrganisationUnit violations
                    violationType.contains("OrganisationUnit") -> {
                        handleOrganisationUnitViolations(d2Instance, violations)
                    }

                    // Program-related violations
                    violationType.contains("Program") -> {
                        handleProgramViolations(d2Instance, violations)
                    }

                    else -> {
                        // Log unknown violation types for investigation
                        violations.take(3).forEach { violation ->
                            Log.w("SessionManager", "UNKNOWN FK VIOLATION: ${violation.fromTable()} -> ${violation.toTable()}, missing: ${violation.notFoundValue()}")
                        }
                    }
                }
            }

            // Attempt to resolve violations by re-syncing missing metadata
            attemptForeignKeyViolationResolution(d2Instance)

        } catch (e: Exception) {
            Log.e("SessionManager", "FK ANALYSIS: Failed to analyze foreign key violations: ${e.message}", e)
        }
    }

    private fun handleCategoryOptionComboViolations(d2Instance: D2, violations: List<org.hisp.dhis.android.core.maintenance.ForeignKeyViolation>) {
        Log.w("SessionManager", "HANDLING CategoryOptionCombo violations (${violations.size} instances)")

        val missingCOCs = violations.map { it.notFoundValue() }.distinct()
        Log.w("SessionManager", "Missing CategoryOptionCombos: ${missingCOCs.take(10).joinToString(", ")}")

        // Try to fetch missing CategoryOptionCombos from server
        try {
            Log.d("SessionManager", "Attempting to fetch missing CategoryOptionCombos from server...")
            // Force sync categories and category combos to resolve missing COCs
            d2Instance.metadataModule().blockingDownload()
        } catch (e: Exception) {
            Log.e("SessionManager", "Failed to resolve CategoryOptionCombo violations: ${e.message}")
        }
    }

    private fun handleDataElementViolations(d2Instance: D2, violations: List<org.hisp.dhis.android.core.maintenance.ForeignKeyViolation>) {
        Log.w("SessionManager", "HANDLING DataElement violations (${violations.size} instances)")
        val missingDataElements = violations.map { it.notFoundValue() }.distinct()
        Log.w("SessionManager", "Missing DataElements: ${missingDataElements.take(5).joinToString(", ")}")
    }

    private fun handleOrganisationUnitViolations(d2Instance: D2, violations: List<org.hisp.dhis.android.core.maintenance.ForeignKeyViolation>) {
        Log.w("SessionManager", "HANDLING OrganisationUnit violations (${violations.size} instances)")
        val missingOrgUnits = violations.map { it.notFoundValue() }.distinct()
        Log.w("SessionManager", "Missing OrganisationUnits: ${missingOrgUnits.take(5).joinToString(", ")}")
    }

    private fun handleProgramViolations(d2Instance: D2, violations: List<org.hisp.dhis.android.core.maintenance.ForeignKeyViolation>) {
        Log.w("SessionManager", "HANDLING Program violations (${violations.size} instances)")
        val missingPrograms = violations.map { it.notFoundValue() }.distinct()
        Log.w("SessionManager", "Missing Programs: ${missingPrograms.take(5).joinToString(", ")}")
    }

    private fun attemptForeignKeyViolationResolution(d2Instance: D2) {
        try {
            Log.d("SessionManager", "RESOLUTION: Attempting to resolve foreign key violations...")

            // Note: Foreign key violations are read-only in SDK - they cannot be manually cleared
            // The SDK will automatically update them after metadata sync resolves dependencies
            Log.d("SessionManager", "RESOLUTION: Note - FK violations are managed by SDK, will be updated after metadata sync")

            // Force a complete metadata re-sync to resolve missing dependencies
            try {
                Log.d("SessionManager", "RESOLUTION: Re-syncing metadata to resolve missing dependencies...")
                d2Instance.metadataModule().blockingDownload()
                Log.d("SessionManager", "RESOLUTION: Metadata re-sync completed")
            } catch (e: Exception) {
                Log.e("SessionManager", "RESOLUTION: Metadata re-sync failed: ${e.message}")
            }

        } catch (e: Exception) {
            Log.e("SessionManager", "RESOLUTION: Failed to resolve foreign key violations: ${e.message}")
        }
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
        // Hydrate datasets with style information
        val datasets = d2Instance.dataSetModule().dataSets().blockingGet().map {
            val datasetStyle = it.style()
            Log.d("SessionManager", "Dataset ${it.uid()}: style=${datasetStyle?.icon()}, color=${datasetStyle?.color()}")

            com.ash.simpledataentry.data.local.DatasetEntity(
                id = it.uid(),
                name = it.displayName() ?: it.name() ?: "Unnamed Dataset",
                description = it.description() ?: "",
                periodType = it.periodType()?.name ?: "Monthly",
                styleIcon = datasetStyle?.icon(),
                styleColor = datasetStyle?.color()
            )
        }
        db.datasetDao().clearAll()
        db.datasetDao().insertAll(datasets)
        // Hydrate data elements
        val dataElements = d2Instance.dataElementModule().dataElements().blockingGet().map {
            com.ash.simpledataentry.data.local.DataElementEntity(
                id = it.uid(),
                name = it.displayName() ?: it.name() ?: "Unnamed DataElement",
                valueType = it.valueType()?.name ?: "TEXT",
                categoryComboId = it.categoryComboUid(),
                description = it.description()
            )
        }
        db.dataElementDao().clearAll()
        db.dataElementDao().insertAll(dataElements)
        // Hydrate category combos
        val categoryCombos = d2Instance.categoryModule().categoryCombos().blockingGet().map {
            com.ash.simpledataentry.data.local.CategoryComboEntity(
                id = it.uid(),
                name = it.displayName() ?: it.name() ?: "Unnamed CategoryCombo"
            )
        }
        db.categoryComboDao().clearAll()
        db.categoryComboDao().insertAll(categoryCombos)
        // Hydrate category option combos
        val categoryOptionCombos = d2Instance.categoryModule().categoryOptionCombos().blockingGet().map {
            com.ash.simpledataentry.data.local.CategoryOptionComboEntity(
                id = it.uid(),
                name = it.displayName() ?: it.uid(),
                categoryComboId = it.categoryCombo()?.uid() ?: "",
                optionUids = it.categoryOptions()?.joinToString(",") { opt -> opt.uid() } ?: ""
            )
        }
        db.categoryOptionComboDao().clearAll()
        db.categoryOptionComboDao().insertAll(categoryOptionCombos)
        // Hydrate organisation units
        val orgUnits = d2Instance.organisationUnitModule().organisationUnits().blockingGet().map {
            com.ash.simpledataentry.data.local.OrganisationUnitEntity(
                id = it.uid(),
                name = it.displayName() ?: it.name() ?: "Unnamed OrgUnit",
                parentId = it.parent()?.uid()
            )
        }
        db.organisationUnitDao().clearAll()
        db.organisationUnitDao().insertAll(orgUnits)


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

                if (index < 10) { // Log first 10 for debugging
                    Log.d("SessionManager", "Storing DataValue $index: datasetId='$datasetId', period='$period', orgUnit='$orgUnit', attributeOptionCombo='$attributeOptionCombo', dataElement='$dataElementUid', categoryOptionCombo='$categoryOptionCombo', value='$value'")
                }

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