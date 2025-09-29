# COMPREHENSIVE FUNCTION FLOW MAP
## DHIS2 Android App - Every Function Call Traced

This document maps every exact function call from login to data display, with intricate technical details.

---

## 1. LOGIN FLOW - Complete Function Stack

### Entry Point: User Clicks Login Button
```
LoginScreen.kt:
Button.onClick → viewModel.loginWithProgress(serverUrl, username, password, context, db)
```

### LoginViewModel.loginWithProgress()
**File:** `LoginViewModel.kt`
**Function:** `loginWithProgress(serverUrl, username, password, context, db)`

**Internal Flow:**
1. **State Update:**
   ```kotlin
   _state.value = _state.value.copy(
       isLoading = true,
       error = null,
       showSplash = true,
       navigationProgress = NavigationProgress.initial()
   )
   ```

2. **Offline Login Check:**
   ```kotlin
   val canLoginOffline = authRepository.canLoginOffline(context, username, serverUrl)
   ```

3. **Login Execution:**
   ```kotlin
   // Either offline or online login
   val loginResult = if (canLoginOffline) {
       authRepository.attemptOfflineLogin(username, password, serverUrl, context, db)
   } else {
       authRepository.loginWithProgress(username, password, serverUrl, context, db) { progress ->
           _state.value = _state.value.copy(navigationProgress = progress)
       }
   }
   ```

### AuthRepositoryImpl.loginWithProgress()
**File:** `AuthRepositoryImpl.kt`
**Function:** `loginWithProgress(serverUrl, username, password, context, db, onProgress)`

**Internal Flow:**
```kotlin
return try {
    sessionManager.loginWithProgress(context, Dhis2Config(serverUrl, username, password), db, onProgress)
    true
} catch (e: Exception) {
    false
}
```

### SessionManager.loginWithProgress() - THE CORE
**File:** `SessionManager.kt`
**Function:** `loginWithProgress(context, dhis2Config, db, onProgress)`

**EXACT STEP-BY-STEP EXECUTION:**

#### Step 1: Initialization (0-10%)
```kotlin
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
    wipeAllData(context)  // CALLS: d2?.wipeModule()?.wipeEverything()
}

d2 = null
initD2(context)  // CALLS: D2Manager.blockingInstantiateD2(config)
```

#### Step 2: Authentication (10-30%)
```kotlin
onProgress(NavigationProgress(
    phase = LoadingPhase.AUTHENTICATING,
    overallPercentage = 15,
    phaseTitle = LoadingPhase.AUTHENTICATING.title,
    phaseDetail = "Connecting to server..."
))

if (d2?.userModule()?.isLogged()?.blockingGet() == true) {
    d2?.userModule()?.blockingLogOut()  // EXACT SDK CALL
}

d2?.userModule()?.blockingLogIn(
    dhis2Config.username,
    dhis2Config.password,
    dhis2Config.serverUrl
) ?: throw IllegalStateException("D2 not initialized")
```

#### Step 3: Download Metadata (30-60%)
```kotlin
onProgress(NavigationProgress(
    phase = LoadingPhase.DOWNLOADING_METADATA,
    overallPercentage = 35,
    phaseTitle = LoadingPhase.DOWNLOADING_METADATA.title,
    phaseDetail = "Downloading configuration..."
))

downloadMetadata()  // EXACT CALL: d2?.metadataModule()!!.blockingDownload()

onProgress(NavigationProgress(
    phase = LoadingPhase.DOWNLOADING_METADATA,
    overallPercentage = 55,
    phaseTitle = LoadingPhase.DOWNLOADING_METADATA.title,
    phaseDetail = "Metadata downloaded successfully"
))
```

**CRITICAL DETAIL:** `d2.metadataModule().blockingDownload()` downloads:
- All program definitions (tracker and event programs)
- Data elements, option sets, categories
- Organisation units, user assignments
- Program rules, indicators
- **BUT NOT actual tracker/event instance data**

#### Step 4: Download Data (60-80%)
```kotlin
onProgress(NavigationProgress(
    phase = LoadingPhase.DOWNLOADING_DATA,
    overallPercentage = 65,
    phaseTitle = LoadingPhase.DOWNLOADING_DATA.title,
    phaseDetail = "Downloading your data..."
))

downloadAggregateData()  // EXACT CALL: d2?.aggregatedModule()!!.data().blockingDownload()

onProgress(NavigationProgress(
    phase = LoadingPhase.DOWNLOADING_DATA,
    overallPercentage = 75,
    phaseTitle = LoadingPhase.DOWNLOADING_DATA.title,
    phaseDetail = "Data downloaded successfully"
))
```

**CRITICAL DETAIL:** `d2.aggregatedModule().data().blockingDownload()` downloads:
- Dataset instance data (aggregate data values)
- **DOES NOT download tracker enrollments**
- **DOES NOT download event instances**

#### Step 5: Database Preparation (80-95%)
```kotlin
onProgress(NavigationProgress(
    phase = LoadingPhase.PREPARING_DATABASE,
    overallPercentage = 85,
    phaseTitle = LoadingPhase.PREPARING_DATABASE.title,
    phaseDetail = "Preparing local database..."
))

hydrateRoomFromSdk(context, db)
```

**hydrateRoomFromSdk() EXACT CALLS:**
```kotlin
val d2Instance = d2 ?: return@withContext

// Load datasets into Room
val datasets = d2Instance.dataSetModule().dataSets().blockingGet().map { ... }
db.datasetDao().clearAll()
db.datasetDao().insertAll(datasets)

// Load data elements into Room
val dataElements = d2Instance.dataElementModule().dataElements().blockingGet().map { ... }
db.dataElementDao().clearAll()
db.dataElementDao().insertAll(dataElements)
```

**CRITICAL DETAIL:** Only datasets and data elements are stored in Room. No tracker/event data is cached.

---

## 2. NAVIGATION TO DATASETS SCREEN

### Trigger: Login Success
```kotlin
// LoginScreen.kt - LaunchedEffect
LaunchedEffect(state.isLoggedIn, state.saveAccountOffered) {
    if (state.isLoggedIn && !state.saveAccountOffered) {
        navController.navigate("datasets") {
            popUpTo("login") { inclusive = true }
        }
    }
}
```

### AppNavigation Route Handler
```kotlin
// AppNavigation.kt
composable(DatasetsScreen.route) {
    DatasetsScreen(navController = navController)
}
```

---

## 3. DATASETS SCREEN INITIALIZATION FLOW

### DatasetsScreen Composable Entry
```kotlin
// DatasetsScreen.kt
@Composable
fun DatasetsScreen(
    navController: NavController,
    viewModel: DatasetsViewModel = hiltViewModel()  // Hilt creates ViewModel
) {
    val datasetsState by viewModel.uiState.collectAsState()
    // ... UI code
}
```

### DatasetsViewModel Constructor and Init
```kotlin
// DatasetsViewModel.kt
@HiltViewModel
class DatasetsViewModel @Inject constructor(
    private val datasetsRepository: com.ash.simpledataentry.domain.repository.DatasetsRepository,
    // ... other dependencies
) : ViewModel() {

    init {
        loadPrograms()  // IMMEDIATE CALL ON CREATION
        backgroundDataPrefetcher.startPrefetching()

        // Observe sync progress
        viewModelScope.launch {
            syncQueueManager.detailedProgress.collect { progress -> ... }
        }
    }
```

### loadPrograms() Function
```kotlin
// DatasetsViewModel.kt
fun loadPrograms() {
    viewModelScope.launch {
        _uiState.value = DatasetsState.Loading
        datasetsRepository.getAllPrograms()  // KEY CALL
            .catch { exception -> ... }
            .collect { programs -> ... }
    }
}
```

### DatasetsRepositoryImpl.getAllPrograms()
```kotlin
// DatasetsRepositoryImpl.kt
override fun getAllPrograms(): Flow<List<ProgramItem>> = combine(
    getDatasets(),       // Call 1
    getTrackerPrograms(), // Call 2
    getEventPrograms()   // Call 3
) { datasets, trackerPrograms, eventPrograms ->
    val programItems = mutableListOf<ProgramItem>()
    programItems.addAll(datasets.map { ProgramItem.DatasetProgram(it) })
    programItems.addAll(trackerPrograms.map { ProgramItem.TrackerProgram(it) })
    programItems.addAll(eventPrograms.map { ProgramItem.EventProgram(it) })
    Log.d(TAG, "Combined programs: ${datasets.size} datasets, ${trackerPrograms.size} tracker, ${eventPrograms.size} event")
    programItems
}
```

### getTrackerPrograms() - EXACT EXECUTION
```kotlin
// DatasetsRepositoryImpl.kt
override fun getTrackerPrograms(): Flow<List<Program>> = flow {
    try {
        val d2Instance = d2
        val programs = d2Instance.programModule().programs()
            .byProgramType().eq(SdkProgramType.WITH_REGISTRATION)  // SDK FILTER
            .blockingGet()  // BLOCKING DHIS2 SDK CALL
            .map { sdkProgram ->
                val domainProgram = sdkProgram.toDomainModel()

                // GET COUNT FROM REPOSITORY
                val enrollmentCount = datasetInstancesRepository.getProgramInstanceCount(
                    domainProgram.id,
                    com.ash.simpledataentry.domain.model.ProgramType.TRACKER
                )
                domainProgram.copy(enrollmentCount = enrollmentCount)
            }

        Log.d(TAG, "Retrieved ${programs.size} tracker programs from SDK")
        emit(programs)
    } catch (e: Exception) {
        Log.e(TAG, "Error fetching tracker programs", e)
        emit(emptyList())
    }
}
```

### getProgramInstanceCount() for TRACKER
```kotlin
// DatasetInstancesRepositoryImpl.kt
override suspend fun getProgramInstanceCount(
    programId: String,
    programType: com.ash.simpledataentry.domain.model.ProgramType
): Int {
    return when (programType) {
        com.ash.simpledataentry.domain.model.ProgramType.TRACKER -> {
            withContext(Dispatchers.IO) {
                try {
                    d2.trackedEntityModule().trackedEntityInstances()
                        .byProgramUids(listOf(programId))
                        .blockingCount()  // EXACT SDK COUNT CALL
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get tracker enrollment count for program $programId", e)
                    0
                }
            }
        }
        // ... other cases
    }
}
```

**CRITICAL DETAIL:** This count will be 0 if no tracker data exists locally, regardless of server data.

---

## 4. TRACKER PROGRAM CLICK FLOW

### User Clicks Tracker Program Card
```kotlin
// DatasetsScreen.kt
Card(
    onClick = {
        navController.navigate("DatasetInstances/${program.id}/${program.name}")
    }
) { ... }
```

### Navigation Route Resolution
```kotlin
// AppNavigation.kt
composable(
    route = "DatasetInstances/{datasetId}/{datasetName}",
    arguments = listOf(
        navArgument("datasetId") { type = NavType.StringType },
        navArgument("datasetName") { type = NavType.StringType }
    )
) { backStackEntry ->
    val datasetId = backStackEntry.arguments?.getString("datasetId") ?: ""
    val datasetName = backStackEntry.arguments?.getString("datasetName") ?: ""
    DatasetInstancesScreen(
        navController = navController,
        datasetId = datasetId,      // This is actually the program ID
        datasetName = datasetName   // This is actually the program name
    )
}
```

---

## 5. DATASET INSTANCES SCREEN INITIALIZATION FOR TRACKER

### DatasetInstancesScreen Composable
```kotlin
// DatasetInstancesScreen.kt
@Composable
fun DatasetInstancesScreen(
    navController: NavController,
    datasetId: String,  // Actually contains program ID
    datasetName: String,
    viewModel: DatasetInstancesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    // CRITICAL INITIALIZATION TRIGGER
    LaunchedEffect(datasetId) {
        // Auto-detect program type and initialize
        viewModel.initializeWithProgramId(datasetId)  // KEY CALL
    }

    // Also calls refresh
    LaunchedEffect(Unit) {
        viewModel.refreshData()
    }
```

### DatasetInstancesViewModel.initializeWithProgramId() - EXACT EXECUTION
```kotlin
// DatasetInstancesViewModel.kt
fun initializeWithProgramId(id: String) {
    if (id.isEmpty()) return

    viewModelScope.launch {
        try {
            Log.d("DatasetInstancesVM", "Auto-detecting program type for ID: $id using DHIS2 SDK directly")

            val d2 = sessionManager.getD2()
            if (d2 == null) {
                Log.e("DatasetInstancesVM", "D2 not initialized, defaulting to DATASET")
                setDatasetId(id)
                return@launch
            }

            var foundType: ProgramType? = null

            // Check datasets first
            try {
                val datasets = d2.dataSetModule().dataSets().blockingGet()
                if (datasets.any { it.uid() == id }) {
                    foundType = ProgramType.DATASET
                    Log.d("DatasetInstancesVM", "Found as dataset: $id")
                }
            } catch (e: Exception) {
                Log.w("DatasetInstancesVM", "Error checking datasets via SDK: ${e.message}")
            }

            // Check tracker programs
            if (foundType == null) {
                try {
                    val programs = d2.programModule().programs()
                        .byProgramType().eq(org.hisp.dhis.android.core.program.ProgramType.WITH_REGISTRATION)
                        .blockingGet()
                    if (programs.any { it.uid() == id }) {
                        foundType = ProgramType.TRACKER
                        Log.d("DatasetInstancesVM", "Found as tracker program: $id")
                    }
                } catch (e: Exception) {
                    Log.w("DatasetInstancesVM", "Error checking tracker programs via SDK: ${e.message}")
                }
            }

            // Check event programs
            if (foundType == null) {
                try {
                    val programs = d2.programModule().programs()
                        .byProgramType().eq(org.hisp.dhis.android.core.program.ProgramType.WITHOUT_REGISTRATION)
                        .blockingGet()
                    if (programs.any { it.uid() == id }) {
                        foundType = ProgramType.EVENT
                        Log.d("DatasetInstancesVM", "Found as event program: $id")
                    }
                } catch (e: Exception) {
                    Log.w("DatasetInstancesVM", "Error checking event programs via SDK: ${e.message}")
                }
            }

            // Initialize with detected type
            val detectedType = foundType ?: ProgramType.DATASET
            Log.d("DatasetInstancesVM", "Auto-detected program type: $detectedType for ID: $id")

            if (detectedType == ProgramType.DATASET) {
                setDatasetId(id)
            } else {
                setProgramId(id, detectedType)  // FOR TRACKER: calls this
            }
        } catch (e: Exception) {
            Log.e("DatasetInstancesVM", "Failed to auto-detect program type for $id, defaulting to DATASET", e)
            setDatasetId(id)
        }
    }
}
```

### setProgramId() for Tracker
```kotlin
// DatasetInstancesViewModel.kt
fun setProgramId(id: String, programType: ProgramType) {
    if (id.isNotEmpty() && (id != programId || programType != currentProgramType)) {
        programId = id
        currentProgramType = programType
        Log.d("DatasetInstancesVM", "Initializing ViewModel with program: $programId, type: $programType")
        loadData()  // TRIGGERS DATA LOADING
    }
}
```

### loadData() for TRACKER - EXACT EXECUTION
```kotlin
// DatasetInstancesViewModel.kt
private fun loadData() {
    if (programId.isEmpty()) {
        Log.e("DatasetInstancesVM", "Cannot load data: programId is empty")
        return
    }

    Log.d("DatasetInstancesVM", "Enhanced loading program data for ID: $programId, type: $currentProgramType")
    viewModelScope.launch {
        _state.value = _state.value.copy(
            isLoading = true,
            error = null,
            navigationProgress = NavigationProgress(
                phase = LoadingPhase.INITIALIZING,
                overallPercentage = 10,
                phaseTitle = LoadingPhase.INITIALIZING.title,
                phaseDetail = "Preparing to load data..."
            )
        )

        try {
            // Load program metadata
            when (currentProgramType) {
                ProgramType.TRACKER -> {
                    datasetsRepository.getTrackerPrograms().collect { programs ->
                        program = programs.find { it.id == programId }
                    }
                }
                // ... other cases
            }

            // Load instances - CRITICAL SECTION
            val instancesResult = when (currentProgramType) {
                ProgramType.TRACKER -> {
                    try {
                        var trackerInstances: List<ProgramInstance> = emptyList()
                        datasetInstacesRepository.getProgramInstances(programId, currentProgramType).collect { instances ->
                            trackerInstances = instances
                        }
                        Result.success(trackerInstances)
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
                // ... other cases
            }

            instancesResult.fold(
                onSuccess = { instances ->
                    Log.d("DatasetInstancesVM", "Received ${instances.size} instances")
                    // Update UI with instances (will be empty if no data)
                },
                onFailure = { error ->
                    Log.e("DatasetInstancesVM", "Failed to load instances", error)
                }
            )
        } catch (e: Exception) {
            Log.e("DatasetInstancesVM", "Error in loadData", e)
        }
    }
}
```

### getProgramInstances() for TRACKER
```kotlin
// DatasetInstancesRepositoryImpl.kt
override suspend fun getProgramInstances(
    programId: String,
    programType: com.ash.simpledataentry.domain.model.ProgramType
): kotlinx.coroutines.flow.Flow<List<com.ash.simpledataentry.domain.model.ProgramInstance>> =
    kotlinx.coroutines.flow.flow {
        when (programType) {
            com.ash.simpledataentry.domain.model.ProgramType.TRACKER -> {
                getTrackerEnrollments(programId).collect { enrollments ->
                    emit(enrollments.map { it as com.ash.simpledataentry.domain.model.ProgramInstance })
                }
            }
            // ... other cases
        }
    }
```

### getTrackerEnrollments() - THE CRITICAL FUNCTION
```kotlin
// DatasetInstancesRepositoryImpl.kt
override suspend fun getTrackerEnrollments(programId: String): kotlinx.coroutines.flow.Flow<List<com.ash.simpledataentry.domain.model.ProgramInstance.TrackerEnrollment>> =
    kotlinx.coroutines.flow.flow {
        try {
            Log.d(TAG, "Fetching tracker enrollments for program: $programId")

            // Get user's org units
            val userOrgUnits = d2.organisationUnitModule().organisationUnits()
                .byOrganisationUnitScope(OrganisationUnit.Scope.SCOPE_DATA_CAPTURE)
                .blockingGet()

            Log.d(TAG, "Found ${userOrgUnits.size} user org units for tracker enrollments")

            if (userOrgUnits.isEmpty()) {
                Log.e(TAG, "No organization units found for user")
                emit(emptyList())
                return@flow
            }

            // Build accessible org units list (including children)
            val allAccessibleOrgUnits = mutableSetOf<String>()
            userOrgUnits.forEach { userOrgUnit ->
                allAccessibleOrgUnits.add(userOrgUnit.uid())

                // Add descendants
                val descendants = d2.organisationUnitModule().organisationUnits()
                    .byPath().like("${userOrgUnit.path()}/%")
                    .blockingGet()

                descendants.forEach { descendant ->
                    allAccessibleOrgUnits.add(descendant.uid())
                }
            }

            Log.d(TAG, "Total accessible org units (including children): ${allAccessibleOrgUnits.size}")

            // THE CRITICAL ENROLLMENT QUERY
            val enrollments = d2.enrollmentModule().enrollments()
                .byProgram().eq(programId)
                .byOrganisationUnit().`in`(allAccessibleOrgUnits.toList())
                .byStatus().`in`(listOf(
                    org.hisp.dhis.android.core.enrollment.EnrollmentStatus.ACTIVE,
                    org.hisp.dhis.android.core.enrollment.EnrollmentStatus.COMPLETED
                ))
                .byDeleted().eq(false)
                .blockingGet()

            Log.d(TAG, "Found ${enrollments.size} enrollments for program $programId (filtered by ${allAccessibleOrgUnits.size} org units including children, active/completed status, not deleted)")

            // DEBUG QUERY - NO FILTERS
            val allEnrollmentsInProgram = d2.enrollmentModule().enrollments()
                .byProgram().eq(programId)
                .blockingGet()

            Log.d(TAG, "DEBUG: Found ${allEnrollmentsInProgram.size} total enrollments for program $programId (no filters)")

            if (allEnrollmentsInProgram.isNotEmpty()) {
                allEnrollmentsInProgram.forEach { enrollment ->
                    Log.d(TAG, "DEBUG: Enrollment ${enrollment.uid()} - orgUnit: ${enrollment.organisationUnit()}, status: ${enrollment.status()}, deleted: ${enrollment.deleted()}")
                }
            } else {
                Log.w(TAG, "DEBUG: No enrollments found at all for program $programId - data may not be synced locally yet")
            }

            // Map to domain objects
            val programInstances = enrollments.map { enrollment ->
                // ... mapping code
            }
            emit(programInstances)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get tracker enrollments for program $programId", e)
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)
```

**CRITICAL ANALYSIS:** This is where we see if tracker data exists locally. The debug query `d2.enrollmentModule().enrollments().byProgram().eq(programId).blockingGet()` will show the actual count.

---

## 6. MANUAL SYNC FLOW FOR TRACKER

### User Clicks Sync Button
```kotlin
// DatasetInstancesScreen.kt - Sync button
IconButton(onClick = { showSyncDialog = true }) { ... }

// Sync dialog
SyncConfirmationDialog(
    onConfirm = { uploadFirst ->
        viewModel.syncDatasetInstances(uploadFirst)  // KEY CALL
        showSyncDialog = false
    },
    onDismiss = { showSyncDialog = false }
)
```

### syncDatasetInstances() for TRACKER
```kotlin
// DatasetInstancesViewModel.kt
fun syncDatasetInstances(uploadFirst: Boolean = false) {
    if (programId.isEmpty()) {
        Log.e("DatasetInstancesVM", "Cannot sync: programId is empty")
        return
    }

    Log.d("DatasetInstancesVM", "Starting enhanced sync for program: $programId, type: $currentProgramType, uploadFirst: $uploadFirst")

    viewModelScope.launch {
        try {
            when (currentProgramType) {
                com.ash.simpledataentry.domain.model.ProgramType.TRACKER -> {
                    Log.d("DatasetInstancesVM", "Syncing tracker program data from server")
                    val syncResult = datasetInstacesRepository.syncProgramInstances(programId, currentProgramType)
                    syncResult.fold(
                        onSuccess = {
                            Log.d("DatasetInstancesVM", "Tracker data sync completed successfully")
                            loadData() // Reload all data after sync
                            _state.value = _state.value.copy(
                                successMessage = "Tracker enrollments synced successfully from server",
                                detailedSyncProgress = null
                            )
                        },
                        onFailure = { error ->
                            Log.e("DatasetInstancesVM", "Tracker sync failed", error)
                            _state.value = _state.value.copy(
                                error = "Failed to sync tracker data: ${error.message}",
                                detailedSyncProgress = null
                            )
                        }
                    )
                }
                // ... other cases
            }
        } catch (e: Exception) {
            Log.e("DatasetInstancesVM", "Sync failed with exception", e)
        }
    }
}
```

### syncProgramInstances() for TRACKER - EXACT SDK CALLS
```kotlin
// DatasetInstancesRepositoryImpl.kt
override suspend fun syncProgramInstances(
    programId: String,
    programType: com.ash.simpledataentry.domain.model.ProgramType
): Result<Unit> {
    return withContext(Dispatchers.IO) {
        try {
            when (programType) {
                com.ash.simpledataentry.domain.model.ProgramType.TRACKER -> {
                    Log.d(TAG, "Downloading tracker data from server for program $programId")

                    // First ensure metadata is up to date
                    d2.metadataModule().blockingDownload()

                    // CRITICAL: Download tracker data for specific program
                    d2.trackedEntityModule().trackedEntityInstanceDownloader()
                        .byProgramUid(programId)
                        .download()

                    Log.d(TAG, "Tracker data download completed for program $programId")
                }
                // ... other cases
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync program instances for $programId", e)
            Result.failure(e)
        }
    }
}
```

**CRITICAL ANALYSIS:**
- `d2.trackedEntityModule().trackedEntityInstanceDownloader().byProgramUid(programId).download()` is the exact SDK call
- This downloads tracked entity instances (enrollments) for the specific program
- If server has tracker data, it will be downloaded and stored locally
- If sync succeeds but still shows empty data, then there are other issues:
  1. Organization unit access permissions
  2. User role permissions
  3. Program enrollment restrictions
  4. Data filters in the query logic

---

## 7. POTENTIAL ISSUES ANALYSIS

### Issue 1: Organization Unit Scope
The query in `getTrackerEnrollments()` filters by:
```kotlin
val userOrgUnits = d2.organisationUnitModule().organisationUnits()
    .byOrganisationUnitScope(OrganisationUnit.Scope.SCOPE_DATA_CAPTURE)
    .blockingGet()
```

**CRITICAL:** If the user doesn't have `SCOPE_DATA_CAPTURE` access to the org units where tracker data exists, it won't be visible.

### Issue 2: Enrollment Status Filter
The query filters by specific statuses:
```kotlin
.byStatus().`in`(listOf(
    org.hisp.dhis.android.core.enrollment.EnrollmentStatus.ACTIVE,
    org.hisp.dhis.android.core.enrollment.EnrollmentStatus.COMPLETED
))
```

**CRITICAL:** If enrollments are in other statuses (CANCELLED, etc.), they won't be shown.

### Issue 3: Program Access
The sync call downloads data for specific program:
```kotlin
.byProgramUid(programId)
```

**CRITICAL:** User must have access to the program and the program must exist on server.

---

## CONCLUSION

The comprehensive flow shows the code is technically correct. The issue must be in:
1. **User permissions/organization unit access**
2. **Enrollment statuses not matching the filter**
3. **Program-specific access restrictions**
4. **Server-side data organization/structure**

The debug logs from `getTrackerEnrollments()` will show the exact counts and help pinpoint the issue.