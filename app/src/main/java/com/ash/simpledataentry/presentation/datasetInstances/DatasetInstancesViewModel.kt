package com.ash.simpledataentry.presentation.datasetInstances

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.simpledataentry.data.sync.SyncStatusController
import com.ash.simpledataentry.domain.model.CompletionStatus
import com.ash.simpledataentry.domain.model.DatasetInstance
import com.ash.simpledataentry.domain.model.DatasetInstanceFilterState
import com.ash.simpledataentry.domain.model.InstanceSortBy
import com.ash.simpledataentry.domain.model.PeriodFilterType
import com.ash.simpledataentry.domain.model.DatasetInstanceState
import com.ash.simpledataentry.domain.model.SortOrder
import com.ash.simpledataentry.domain.model.SyncStatus
import com.ash.simpledataentry.domain.model.ProgramInstance
import com.ash.simpledataentry.domain.model.ProgramType
import com.ash.simpledataentry.domain.model.asDatasetInstance
import com.ash.simpledataentry.domain.useCase.GetDatasetInstancesUseCase
import com.ash.simpledataentry.domain.useCase.SyncDatasetInstancesUseCase
import com.ash.simpledataentry.domain.repository.DataEntryRepository
import com.ash.simpledataentry.domain.repository.DatasetInstancesRepository
import com.ash.simpledataentry.util.NetworkUtils
import com.ash.simpledataentry.util.PeriodHelper
import com.ash.simpledataentry.data.DatabaseProvider
import com.ash.simpledataentry.data.sync.SyncQueueManager
import com.ash.simpledataentry.presentation.core.LoadingOperation
import com.ash.simpledataentry.presentation.core.LoadingPhase
import com.ash.simpledataentry.presentation.core.LoadingProgress
import com.ash.simpledataentry.presentation.core.NavigationProgress
import com.ash.simpledataentry.presentation.core.UiError
import com.ash.simpledataentry.presentation.core.UiState
import com.ash.simpledataentry.util.toUiError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DatasetInstancesData(
    val instances: List<ProgramInstance> = emptyList(),
    val filteredInstances: List<ProgramInstance> = emptyList(),
    val successMessage: String? = null,
    val attributeOptionCombos: List<Pair<String, String>> = emptyList(),
    val dataset: com.ash.simpledataentry.domain.model.Dataset? = null,
    val program: com.ash.simpledataentry.domain.model.Program? = null,
    val programType: ProgramType = ProgramType.DATASET,
    val localInstanceCount: Int = 0,
    val instancesWithDrafts: Set<String> = emptySet()
)

@HiltViewModel
class DatasetInstancesViewModel @Inject constructor(
    private val getDatasetInstancesUseCase: GetDatasetInstancesUseCase,
    private val syncDatasetInstancesUseCase: SyncDatasetInstancesUseCase,
    private val dataEntryRepository: DataEntryRepository,
    private val datasetInstacesRepository: DatasetInstancesRepository,
    private val datasetsRepository: com.ash.simpledataentry.domain.repository.DatasetsRepository,
    private val databaseProvider: DatabaseProvider,
    private val syncQueueManager: SyncQueueManager,
    private val sessionManager: com.ash.simpledataentry.data.SessionManager,
    private val syncStatusController: SyncStatusController,
    private val app: Application
) : ViewModel() {
    private var programId: String = ""
    private var currentProgramType: ProgramType = ProgramType.DATASET

    private val _uiState = MutableStateFlow<UiState<DatasetInstancesData>>(
        UiState.Loading(LoadingOperation.Initial)
    )
    val uiState: StateFlow<UiState<DatasetInstancesData>> = _uiState.asStateFlow()

    val syncController: SyncStatusController = syncStatusController

    // --- Bulk completion state ---
    private val _bulkCompletionMode = MutableStateFlow(false)
    val bulkCompletionMode: StateFlow<Boolean> = _bulkCompletionMode.asStateFlow()

    private val _selectedInstances = MutableStateFlow<Set<String>>(emptySet())
    val selectedInstances: StateFlow<Set<String>> = _selectedInstances.asStateFlow()

    // Filter state
    private val _filterState = MutableStateFlow(DatasetInstanceFilterState())
    val filterState: StateFlow<DatasetInstanceFilterState> = _filterState.asStateFlow()

    private var lastSuccessfulData: DatasetInstancesData? = null
    private var pendingSuccessMessage: String? = null
    private var isSyncOverlayVisible = false
    private var userSyncInProgress = false
    private val draftDao get() = databaseProvider.getCurrentDatabase().dataValueDraftDao()

    private fun currentData(): DatasetInstancesData {
        return when (val state = _uiState.value) {
            is UiState.Success -> state.data
            is UiState.Error -> state.previousData ?: lastSuccessfulData ?: DatasetInstancesData()
            is UiState.Loading -> lastSuccessfulData ?: DatasetInstancesData()
        }
    }

    private fun setSuccessData(data: DatasetInstancesData) {
        lastSuccessfulData = data
        _uiState.value = UiState.Success(data)
    }

    private fun restoreLastSuccess() {
        lastSuccessfulData?.let { setSuccessData(it) }
    }

    init {
        // Account change observer - MUST come first
        viewModelScope.launch {
            sessionManager.currentAccountId.collect { accountId ->
                if (accountId == null) {
                    resetToInitialState()
                } else {
                    val previouslyInitialized = programId.isNotEmpty()
                    if (previouslyInitialized) {
                        resetToInitialState()
                    }
                }
            }
        }

        // Observe sync progress from SyncQueueManager
        viewModelScope.launch {
            syncQueueManager.detailedProgress.collect { progress ->
                if (progress != null && userSyncInProgress) {
                    isSyncOverlayVisible = true
                    _uiState.value = UiState.Loading(
                        operation = LoadingOperation.Syncing(progress),
                        progress = LoadingProgress(
                            percentage = progress.overallPercentage,
                            message = progress.phaseDetail
                        )
                    )
                } else if (progress == null && isSyncOverlayVisible) {
                    isSyncOverlayVisible = false
                    userSyncInProgress = false
                    lastSuccessfulData?.let { setSuccessData(it) } ?: run {
                        _uiState.value = UiState.Loading(LoadingOperation.Initial)
                    }
                }
            }
        }
    }

    private fun resetToInitialState() {
        programId = ""
        currentProgramType = ProgramType.DATASET
        lastSuccessfulData = null
        pendingSuccessMessage = null
        isSyncOverlayVisible = false
        _uiState.value = UiState.Loading(LoadingOperation.Initial)
        _filterState.value = DatasetInstanceFilterState()
        _bulkCompletionMode.value = false
        _selectedInstances.value = emptySet()
    }

    /**
     * Creates a unique key for a program instance based on its identity and type
     */
    private fun createInstanceKey(programInstance: ProgramInstance): String {
        return when (programInstance) {
            is ProgramInstance.DatasetInstance -> {
                "${programInstance.programId}|${programInstance.period.id}|${programInstance.organisationUnit.id}|${programInstance.attributeOptionCombo}"
            }
            is ProgramInstance.TrackerEnrollment -> {
                "${programInstance.programId}|${programInstance.trackedEntityInstance}|${programInstance.organisationUnit.id}"
            }
            is ProgramInstance.EventInstance -> {
                "${programInstance.programId}|${programInstance.programStage}|${programInstance.organisationUnit.id}|${programInstance.eventDate?.time ?: 0}"
            }
        }
    }

    /**
     * Creates a unique key for a dataset instance based on its identity (legacy method for backward compatibility)
     */
    private fun createInstanceKey(datasetId: String, period: String, orgUnit: String, attributeOptionCombo: String): String {
        return "$datasetId|$period|$orgUnit|$attributeOptionCombo"
    }

    fun setDatasetId(id: String) {
        if (id.isNotEmpty() && id != programId) {
            programId = id
            currentProgramType = ProgramType.DATASET
            Log.d("DatasetInstancesVM", "Initializing ViewModel with dataset: $programId")
            loadData()
        }
    }

    fun setProgramId(id: String, programType: ProgramType) {
        if (id.isNotEmpty() && (id != programId || programType != currentProgramType)) {
            programId = id
            currentProgramType = programType
            Log.d("DatasetInstancesVM", "Initializing ViewModel with program: $programId, type: $programType")
            loadData()
        }
    }

    /**
     * CRITICAL FIX: Auto-detect program type and initialize appropriately
     * This solves the tracker/event data display issue
     */
    fun initializeWithProgramId(id: String) {
        if (id.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("DatasetInstancesVM", "Auto-detecting program type for ID: $id using DHIS2 SDK directly")

                // Use DHIS2 SDK directly to avoid Flow issues
                val d2 = sessionManager.getD2()

                if (d2 == null) {
                    Log.e("DatasetInstancesVM", "D2 not initialized, defaulting to DATASET")
                    setDatasetId(id)
                    return@launch
                }

                var foundType: ProgramType? = null

                // Check datasets directly via DHIS2 SDK
                try {
                    val datasets = d2.dataSetModule().dataSets().blockingGet()
                    if (datasets.any { it.uid() == id }) {
                        foundType = ProgramType.DATASET
                        Log.d("DatasetInstancesVM", "Found as dataset: $id")
                    }
                } catch (e: Exception) {
                    Log.w("DatasetInstancesVM", "Error checking datasets via SDK: ${e.message}")
                }

                // Check tracker programs directly via DHIS2 SDK
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

                // Check event programs directly via DHIS2 SDK
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

                // Use the detected type or default to DATASET
                val detectedType = foundType ?: ProgramType.DATASET
                Log.d("DatasetInstancesVM", "Auto-detected program type: $detectedType for ID: $id")

                // Now initialize with the correct type
                if (detectedType == ProgramType.DATASET) {
                    setDatasetId(id)
                } else {
                    setProgramId(id, detectedType)
                }
            } catch (e: Exception) {
                Log.e("DatasetInstancesVM", "Failed to auto-detect program type for $id, defaulting to DATASET", e)
                setDatasetId(id)
            }
        }
    }

    fun refreshData() {
        Log.d("DatasetInstancesVM", "Refreshing dataset instances data")
        loadData()
    }

    private fun loadData() {
        if (programId.isEmpty()) {
            Log.e("DatasetInstancesVM", "Cannot load data: programId is empty")
            return
        }

        Log.d("DatasetInstancesVM", "Enhanced loading program data for ID: $programId, type: $currentProgramType")
        viewModelScope.launch {
            val previousData = lastSuccessfulData

            fun emitNavigationProgress(
                phase: LoadingPhase,
                percentage: Int,
                detail: String
            ) {
                val navigationProgress = NavigationProgress(
                    phase = phase,
                    overallPercentage = percentage,
                    phaseTitle = phase.title,
                    phaseDetail = detail
                )
                _uiState.value = UiState.Loading(
                    operation = LoadingOperation.Navigation(navigationProgress),
                    progress = LoadingProgress(message = detail)
                )
            }

            emitNavigationProgress(
                phase = LoadingPhase.INITIALIZING,
                percentage = 10,
                detail = "Preparing to load data..."
            )
            try {
                // Step 1: Load Configuration (10-30%)
                emitNavigationProgress(
                    phase = LoadingPhase.LOADING_DATA,
                    percentage = 25,
                    detail = "Loading configuration..."
                )

                Log.d("DatasetInstancesVM", "Fetching program instances")
                // Get attribute option combos only for datasets
                val attributeOptionCombos = if (currentProgramType == ProgramType.DATASET) {
                    dataEntryRepository.getAttributeOptionCombos(programId)
                } else {
                    emptyList()
                }
                Log.d("DatasetInstancesVM", "getAttributeOptionCombos returned: $attributeOptionCombos")
                
                // Step 2: Get Program Information (30-50%)
                emitNavigationProgress(
                    phase = LoadingPhase.LOADING_DATA,
                    percentage = 40,
                    detail = "Fetching program information..."
                )

                // Get program information based on type
                var dataset: com.ash.simpledataentry.domain.model.Dataset? = null
                var program: com.ash.simpledataentry.domain.model.Program? = null

                when (currentProgramType) {
                    ProgramType.DATASET -> {
                        dataset = datasetsRepository.getDatasets().first().find { it.id == programId }
                    }
                    ProgramType.TRACKER -> {
                        program = datasetsRepository.getTrackerPrograms().first().find { it.id == programId }
                    }
                    ProgramType.EVENT -> {
                        program = datasetsRepository.getEventPrograms().first().find { it.id == programId }
                    }
                    ProgramType.ALL -> {
                        // Should not happen in this context
                    }
                }
                
                // Step 3: Load Instances (50-70%)
                emitNavigationProgress(
                    phase = LoadingPhase.LOADING_DATA,
                    percentage = 60,
                    detail = "Loading program instances..."
                )

                // Load instances based on program type
                val instancesResult = when (currentProgramType) {
                    ProgramType.DATASET -> {
                        // Convert existing dataset instances to unified ProgramInstance
                        try {
                            val datasetInstances = getDatasetInstancesUseCase(programId)
                            datasetInstances.fold(
                                onSuccess = { instances ->
                                    val programInstances = instances.map { datasetInstance ->
                                        ProgramInstance.DatasetInstance(
                                            id = datasetInstance.id,
                                            programId = datasetInstance.datasetId,
                                            programName = dataset?.name ?: "Unknown Dataset",
                                            organisationUnit = datasetInstance.organisationUnit,
                                            lastUpdated = datasetInstance.lastUpdated?.let {
                                                try {
                                                    java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", java.util.Locale.ENGLISH).parse(it)
                                                } catch (e: Exception) {
                                                    java.util.Date()
                                                }
                                            } ?: java.util.Date(),
                                            state = when (datasetInstance.state) {
                                                DatasetInstanceState.COMPLETE -> com.ash.simpledataentry.domain.model.ProgramInstanceState.COMPLETED
                                                DatasetInstanceState.OPEN -> com.ash.simpledataentry.domain.model.ProgramInstanceState.ACTIVE
                                                else -> com.ash.simpledataentry.domain.model.ProgramInstanceState.ACTIVE
                                            },
                                            syncStatus = SyncStatus.SYNCED, // Dataset instances are always considered synced initially
                                            period = datasetInstance.period,
                                            attributeOptionCombo = datasetInstance.attributeOptionCombo,
                                            originalDatasetInstance = datasetInstance
                                        )
                                    }
                                    Result.success(programInstances)
                                },
                                onFailure = { Result.failure(it) }
                            )
                        } catch (e: Exception) {
                            Result.failure(e)
                        }
                    }
                    ProgramType.TRACKER -> {
                        try {
                            val trackerInstances = datasetInstacesRepository.getProgramInstances(programId, currentProgramType).first()
                            Result.success(trackerInstances)
                        } catch (e: Exception) {
                            Result.failure(e)
                        }
                    }
                    ProgramType.EVENT -> {
                        try {
                            val eventInstances = datasetInstacesRepository.getProgramInstances(programId, currentProgramType).first()
                            Result.success(eventInstances)
                        } catch (e: Exception) {
                            Result.failure(e)
                        }
                    }
                    ProgramType.ALL -> Result.success(emptyList()) // Should not happen
                }
                instancesResult.fold(
                    onSuccess = { instances ->
                        Log.d("DatasetInstancesVM", "Received "+instances.size+" instances")

                        // Step 4: Process Draft Status (70-90%)
                        emitNavigationProgress(
                            phase = LoadingPhase.PROCESSING_DATA,
                            percentage = 80,
                            detail = "Checking draft status..."
                        )

                        // Check for instances with draft values (only applies to dataset instances)
                        val instancesWithDrafts = mutableSetOf<String>()
                        for (instance in instances) {
                            when (instance) {
                                is ProgramInstance.DatasetInstance -> {
                                    val draftCount = draftDao.getDraftCountForInstance(
                                        datasetId = instance.programId,
                                        period = instance.period.id,
                                        orgUnit = instance.organisationUnit.id,
                                        attributeOptionCombo = instance.attributeOptionCombo
                                    )
                                    if (draftCount > 0) {
                                        val instanceKey = createInstanceKey(instance)
                                        instancesWithDrafts.add(instanceKey)
                                    }
                                }
                                is ProgramInstance.TrackerEnrollment, is ProgramInstance.EventInstance -> {
                                    // TODO: Implement draft detection for tracker/event instances
                                    // For now, they are considered synced
                                }
                            }
                        }

                        Log.d("DatasetInstancesVM", "Found ${instancesWithDrafts.size} instances with local draft values")

                        // Step 5: Finalizing (90-100%)
                        emitNavigationProgress(
                            phase = LoadingPhase.COMPLETING,
                            percentage = 100,
                            detail = "Ready!"
                        )

                        val orderedInstances = orderInstances(instances)
                        val filteredInstances = applyFilters(
                            instances = orderedInstances,
                            instancesWithDraftsOverride = instancesWithDrafts
                        )
                        val baseData = DatasetInstancesData(
                            instances = instances,
                            filteredInstances = filteredInstances,
                            successMessage = null,
                            attributeOptionCombos = attributeOptionCombos,
                            dataset = dataset,
                            program = program,
                            programType = currentProgramType,
                            localInstanceCount = instancesWithDrafts.size,
                            instancesWithDrafts = instancesWithDrafts
                        )
                        val message = pendingSuccessMessage
                        pendingSuccessMessage = null
                        val finalData = if (message != null) {
                            baseData.copy(successMessage = message)
                        } else {
                            baseData
                        }
                        setSuccessData(finalData)
                    },
                    onFailure = { throwable ->
                        Log.e("DatasetInstancesVM", "Error loading data", throwable)
                        _uiState.value = UiState.Error(
                            error = throwable.toUiError(),
                            previousData = previousData
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e("DatasetInstancesVM", "Error loading data", e)
                _uiState.value = UiState.Error(
                    error = e.toUiError(),
                    previousData = previousData
                )
            }
        }
    }

    fun syncDatasetInstances(uploadFirst: Boolean = false) {
        if (programId.isEmpty()) {
            Log.e("DatasetInstancesVM", "Cannot sync: programId is empty")
            return
        }

        Log.d("DatasetInstancesVM", "Starting enhanced sync for program: $programId, type: $currentProgramType, uploadFirst: $uploadFirst")
        viewModelScope.launch {
            val previousData = lastSuccessfulData
            try {
                when (currentProgramType) {
                    com.ash.simpledataentry.domain.model.ProgramType.TRACKER -> {
                        Log.d("DatasetInstancesVM", "Syncing tracker program data from server")
                        val syncResult = datasetInstacesRepository.syncProgramInstances(programId, currentProgramType)
                        syncResult.fold(
                            onSuccess = {
                                Log.d("DatasetInstancesVM", "Tracker data sync completed successfully")
                                pendingSuccessMessage = "Tracker enrollments synced successfully from server"
                                loadData() // Reload all data after sync
                            },
                            onFailure = { throwable ->
                                Log.e("DatasetInstancesVM", "Tracker sync failed", throwable)
                                _uiState.value = UiState.Error(
                                    error = throwable.toUiError(),
                                    previousData = previousData
                                )
                            }
                        )
                    }
                    com.ash.simpledataentry.domain.model.ProgramType.EVENT -> {
                        Log.d("DatasetInstancesVM", "Syncing event program data from server")
                        val syncResult = datasetInstacesRepository.syncProgramInstances(programId, currentProgramType)
                        syncResult.fold(
                            onSuccess = {
                                Log.d("DatasetInstancesVM", "Event data sync completed successfully")
                                pendingSuccessMessage = "Event instances synced successfully from server"
                                loadData() // Reload all data after sync
                            },
                            onFailure = { throwable ->
                                Log.e("DatasetInstancesVM", "Event sync failed", throwable)
                                _uiState.value = UiState.Error(
                                    error = throwable.toUiError(),
                                    previousData = previousData
                                )
                            }
                        )
                    }
                    com.ash.simpledataentry.domain.model.ProgramType.DATASET -> {
                        userSyncInProgress = true
                        // Use the enhanced SyncQueueManager for dataset synchronization
                        val syncResult = syncQueueManager.startSync(forceSync = uploadFirst)
                        syncResult.fold(
                            onSuccess = {
                                Log.d("DatasetInstancesVM", "Dataset sync completed successfully")
                                pendingSuccessMessage = if (uploadFirst) {
                                    "Data synchronized successfully with enhanced progress tracking"
                                } else {
                                    "Dataset instances synced successfully"
                                }
                                viewModelScope.launch {
                                    withContext(Dispatchers.IO) {
                                        clearDraftsForInstances(currentData().instancesWithDrafts)
                                    }
                                    loadData() // Reload all data after sync
                                }
                            },
                            onFailure = { throwable ->
                                Log.e("DatasetInstancesVM", "Dataset sync failed", throwable)
                                _uiState.value = UiState.Error(
                                    error = throwable.toUiError(),
                                    previousData = previousData
                                )
                            }
                        )
                    }
                    else -> {
                        Log.w("DatasetInstancesVM", "Unknown program type for sync: $currentProgramType")
                        _uiState.value = UiState.Error(
                            error = UiError.Local("Cannot sync: Unknown program type"),
                            previousData = previousData
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("DatasetInstancesVM", "Sync failed", e)
                _uiState.value = UiState.Error(
                    error = e.toUiError(),
                    previousData = previousData
                )
            } finally {
                if (currentProgramType == com.ash.simpledataentry.domain.model.ProgramType.DATASET) {
                    userSyncInProgress = false
                }
            }
        }
    }

    fun syncDatasetInstance(
        instance: ProgramInstance.DatasetInstance,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                userSyncInProgress = true
                val result = syncQueueManager.startSyncForInstance(
                    datasetId = instance.programId,
                    period = instance.period.id,
                    orgUnit = instance.organisationUnit.id,
                    attributeOptionCombo = instance.attributeOptionCombo
                )
                result.fold(
                    onSuccess = {
                        syncQueueManager.clearErrorState()
                        viewModelScope.launch {
                            withContext(Dispatchers.IO) {
                                draftDao.deleteDraftsForInstance(
                                    datasetId = instance.programId,
                                    period = instance.period.id,
                                    orgUnit = instance.organisationUnit.id,
                                    attributeOptionCombo = instance.attributeOptionCombo
                                )
                            }
                            loadData()
                            onResult(true, "Entry synced successfully.")
                        }
                    },
                    onFailure = { error ->
                        syncQueueManager.clearErrorState()
                        onResult(false, error.message ?: "Failed to sync entry.")
                    }
                )
            } catch (e: Exception) {
                syncQueueManager.clearErrorState()
                onResult(false, e.message ?: "Failed to sync entry.")
            } finally {
                userSyncInProgress = false
            }
        }
    }

    fun manualRefresh() {
        loadData()
    }

    private suspend fun clearDraftsForInstances(instanceKeys: Set<String>) {
        if (instanceKeys.isEmpty()) {
            return
        }
        instanceKeys.forEach { key ->
            val parts = key.split("|")
            if (parts.size >= 4) {
                draftDao.deleteDraftsForInstance(
                    datasetId = parts[0],
                    period = parts[1],
                    orgUnit = parts[2],
                    attributeOptionCombo = parts[3]
                )
            }
        }
    }

    fun dismissSyncOverlay() {
        // Clear error state in SyncQueueManager to prevent persistent dialogs
        syncQueueManager.clearErrorState()
        isSyncOverlayVisible = false
        lastSuccessfulData?.let { setSuccessData(it) }
    }

    fun toggleBulkCompletionMode() {
        _bulkCompletionMode.value = !_bulkCompletionMode.value
        if (!_bulkCompletionMode.value) {
            _selectedInstances.value = emptySet()
        }
    }

    fun toggleInstanceSelection(uid: String) {
        _selectedInstances.value = _selectedInstances.value.toMutableSet().apply {
            if (contains(uid)) remove(uid) else add(uid)
        }
    }

    fun clearBulkSelection() {
        _selectedInstances.value = emptySet()
        _bulkCompletionMode.value = false
    }

    fun bulkCompleteSelectedInstances(onResult: (Boolean, String?) -> Unit) {
        val selected = _selectedInstances.value
        val allInstances = (lastSuccessfulData?.instances ?: emptyList()).associateBy { it.id }
        val totalDatasetInstances = allInstances.values.count { it is ProgramInstance.DatasetInstance }
        if (totalDatasetInstances == 0) {
            onResult(false, "No dataset instances available")
            return
        }

        viewModelScope.launch {
            val progressTracker = LoadingProgress(message = "Updating completion status...")
            _uiState.value = UiState.Loading(
                operation = LoadingOperation.BulkOperation(
                    itemsProcessed = 0,
                    totalItems = totalDatasetInstances,
                    operationName = "Updating completion"
                ),
                progress = progressTracker
            )
            var anyError: String? = null
            var processed = 0
            for ((uid, instance) in allInstances) {
                if (instance is ProgramInstance.DatasetInstance) {
                    val shouldBeComplete = selected.contains(uid) || instance.state == com.ash.simpledataentry.domain.model.ProgramInstanceState.COMPLETED
                    val isComplete = instance.state == com.ash.simpledataentry.domain.model.ProgramInstanceState.COMPLETED
                    val result: Result<Unit>? = when {
                        shouldBeComplete && !isComplete -> datasetInstacesRepository.completeDatasetInstance(
                            datasetId = instance.programId,
                            period = instance.period.id,
                            orgUnit = instance.organisationUnit.id,
                            attributeOptionCombo = instance.attributeOptionCombo
                        )
                        !shouldBeComplete && isComplete -> datasetInstacesRepository.markDatasetInstanceIncomplete(
                            datasetId = instance.programId,
                            period = instance.period.id,
                            orgUnit = instance.organisationUnit.id,
                            attributeOptionCombo = instance.attributeOptionCombo
                        )
                        else -> null
                    }
                    processed++
                    _uiState.value = UiState.Loading(
                        operation = LoadingOperation.BulkOperation(
                            itemsProcessed = processed.coerceAtMost(totalDatasetInstances),
                            totalItems = totalDatasetInstances,
                            operationName = "Updating completion"
                        ),
                        progress = progressTracker
                    )

                    if (result != null && result.isFailure) {
                        anyError = result.exceptionOrNull()?.message ?: "Unknown error"
                        break
                    }
                }
            }

            restoreLastSuccess()

            if (anyError != null) {
                onResult(false, anyError)
            } else {
                onResult(true, null)
            }
        }
    }

    fun markDatasetInstanceIncomplete(uid: String, onResult: (Boolean, String?) -> Unit) {
        val allInstances = (lastSuccessfulData?.instances ?: emptyList()).associateBy { it.id }
        val instance = allInstances[uid]
        if (instance is ProgramInstance.DatasetInstance) {
            viewModelScope.launch {
                _uiState.value = UiState.Loading(
                    operation = LoadingOperation.BulkOperation(
                        itemsProcessed = 0,
                        totalItems = 1,
                        operationName = "Marking instance incomplete"
                    ),
                    progress = LoadingProgress(message = "Updating completion status...")
                )
                val result = datasetInstacesRepository.markDatasetInstanceIncomplete(
                    datasetId = instance.programId,
                    period = instance.period.id,
                    orgUnit = instance.organisationUnit.id,
                    attributeOptionCombo = instance.attributeOptionCombo
                )
                restoreLastSuccess()
                if (result.isFailure) {
                    onResult(false, result.exceptionOrNull()?.message ?: "Unknown error")
                } else {
                    onResult(true, null)
                }
            }
        } else {
            onResult(false, "Operation not supported for this instance type")
        }
    }

    fun bulkMarkInstancesIncomplete(uids: Set<String>, onResult: (Boolean, String?) -> Unit) {
        val allInstances = (lastSuccessfulData?.instances ?: emptyList()).associateBy { it.id }
        val datasetInstances = uids.mapNotNull { uid -> allInstances[uid] as? ProgramInstance.DatasetInstance }
        if (datasetInstances.isEmpty()) {
            onResult(false, "No dataset instances selected")
            return
        }

        viewModelScope.launch {
            val progressTracker = LoadingProgress(message = "Updating completion status...")
            _uiState.value = UiState.Loading(
                operation = LoadingOperation.BulkOperation(
                    itemsProcessed = 0,
                    totalItems = datasetInstances.size,
                    operationName = "Marking incomplete"
                ),
                progress = progressTracker
            )
            var anyError: String? = null
            var processed = 0
            for (instance in datasetInstances) {
                val result = datasetInstacesRepository.markDatasetInstanceIncomplete(
                    datasetId = instance.programId,
                    period = instance.period.id,
                    orgUnit = instance.organisationUnit.id,
                    attributeOptionCombo = instance.attributeOptionCombo
                )
                processed++
                _uiState.value = UiState.Loading(
                    operation = LoadingOperation.BulkOperation(
                        itemsProcessed = processed.coerceAtMost(datasetInstances.size),
                        totalItems = datasetInstances.size,
                        operationName = "Marking incomplete"
                    ),
                    progress = progressTracker
                )
                if (result.isFailure) {
                    anyError = result.exceptionOrNull()?.message ?: "Unknown error"
                    break
                }
            }
            restoreLastSuccess()
            if (anyError != null) {
                onResult(false, anyError)
            } else {
                onResult(true, null)
            }
        }
    }

    fun markDatasetInstanceComplete(uid: String, onResult: (Boolean, String?) -> Unit) {
        val allInstances = (lastSuccessfulData?.instances ?: emptyList()).associateBy { it.id }
        val instance = allInstances[uid]
        if (instance is ProgramInstance.DatasetInstance) {
            viewModelScope.launch {
                _uiState.value = UiState.Loading(
                    operation = LoadingOperation.BulkOperation(
                        itemsProcessed = 0,
                        totalItems = 1,
                        operationName = "Marking instance complete"
                    ),
                    progress = LoadingProgress(message = "Updating completion status...")
                )
                val result = datasetInstacesRepository.completeDatasetInstance(
                    datasetId = instance.programId,
                    period = instance.period.id,
                    orgUnit = instance.organisationUnit.id,
                    attributeOptionCombo = instance.attributeOptionCombo
                )
                restoreLastSuccess()
                if (result.isFailure) {
                    onResult(false, result.exceptionOrNull()?.message ?: "Unknown error")
                } else {
                    onResult(true, null)
                }
            }
        } else {
            onResult(false, "Operation not supported for this instance type")
        }
    }

    // Filter methods
    fun updateFilterState(newFilterState: DatasetInstanceFilterState) {
        _filterState.value = newFilterState
        val existingData = lastSuccessfulData ?: return
        val orderedInstances = orderInstances(existingData.instances)
        val filteredInstances = applyFilters(orderedInstances)
        setSuccessData(existingData.copy(filteredInstances = filteredInstances))
    }

    private fun applyFilters(
        instances: List<ProgramInstance>,
        instancesWithDraftsOverride: Set<String>? = null
    ): List<ProgramInstance> {
        val filter = _filterState.value
        val periodHelper = PeriodHelper()
        val instancesWithDrafts = instancesWithDraftsOverride ?: currentData().instancesWithDrafts

        val periodRange = when (filter.periodType) {
            PeriodFilterType.RELATIVE -> filter.relativePeriod?.let { periodHelper.getDateRange(it) }
            PeriodFilterType.CUSTOM_RANGE -> {
                if (filter.customFromDate != null && filter.customToDate != null) {
                    Pair(filter.customFromDate, filter.customToDate)
                } else null
            }
            else -> null
        }

        return instances.filter { instance ->
            // Period filtering only applies to dataset instances
            val periodMatches = when (instance) {
                is ProgramInstance.DatasetInstance -> {
                    if (periodRange == null) {
                        true
                    } else {
                        periodHelper.isPeriodIdWithinRange(
                            instance.period.id,
                            periodRange.first,
                            periodRange.second
                        )
                    }
                }
                is ProgramInstance.TrackerEnrollment, is ProgramInstance.EventInstance -> true // No period filtering for tracker/events
            }

            // Check sync status based on draft data values
            val instanceKey = createInstanceKey(instance)
            val hasDraftValues = instancesWithDrafts.contains(instanceKey)

            val syncStatusMatches = when (filter.syncStatus) {
                SyncStatus.ALL -> true
                SyncStatus.SYNCED -> !hasDraftValues // No draft values = synced
                SyncStatus.NOT_SYNCED -> hasDraftValues // Has draft values = not synced
            }

            val completionMatches = when (filter.completionStatus) {
                CompletionStatus.ALL -> true
                CompletionStatus.COMPLETE -> when (instance.state) {
                    com.ash.simpledataentry.domain.model.ProgramInstanceState.COMPLETED,
                    com.ash.simpledataentry.domain.model.ProgramInstanceState.APPROVED -> true
                    else -> false
                }
                CompletionStatus.INCOMPLETE -> when (instance.state) {
                    com.ash.simpledataentry.domain.model.ProgramInstanceState.ACTIVE,
                    com.ash.simpledataentry.domain.model.ProgramInstanceState.SCHEDULED,
                    com.ash.simpledataentry.domain.model.ProgramInstanceState.OVERDUE -> true
                    else -> false
                }
            }

            // Attribute option combo filtering only applies to dataset instances
            val attributeOptionComboMatches = when (instance) {
                is ProgramInstance.DatasetInstance -> {
                    if (filter.attributeOptionCombo == null) {
                        true
                    } else {
                        instance.attributeOptionCombo == filter.attributeOptionCombo
                    }
                }
                is ProgramInstance.TrackerEnrollment, is ProgramInstance.EventInstance -> true
            }

            val searchMatches = if (filter.searchQuery.isBlank()) {
                true
            } else {
                val query = filter.searchQuery.lowercase()
                when (instance) {
                    is ProgramInstance.DatasetInstance -> {
                        instance.organisationUnit.name.lowercase().contains(query) ||
                                instance.period.id.lowercase().contains(query)
                    }
                    is ProgramInstance.TrackerEnrollment -> {
                        instance.organisationUnit.name.lowercase().contains(query) ||
                                instance.trackedEntityInstance.lowercase().contains(query)
                    }
                    is ProgramInstance.EventInstance -> {
                        instance.organisationUnit.name.lowercase().contains(query) ||
                                instance.programStage.lowercase().contains(query)
                    }
                }
            }

            periodMatches && syncStatusMatches && completionMatches && attributeOptionComboMatches && searchMatches
        }
    }

    fun clearFilters() {
        _filterState.value = DatasetInstanceFilterState()
        lastSuccessfulData?.let { data ->
            val orderedInstances = orderInstances(data.instances)
            setSuccessData(data.copy(filteredInstances = orderedInstances))
        }
    }

    private fun orderInstances(instances: List<ProgramInstance>): List<ProgramInstance> {
        val filter = _filterState.value

        val sorted = when (filter.sortBy) {
            InstanceSortBy.ORGANISATION_UNIT -> instances.sortedBy { it.organisationUnit.name.lowercase() }
            InstanceSortBy.PERIOD -> instances.sortedWith(
                compareBy<ProgramInstance> { instance ->
                    when (instance) {
                        is ProgramInstance.DatasetInstance -> parseDhis2PeriodToDate(instance.period.id)?.time ?: 0L
                        is ProgramInstance.TrackerEnrollment -> instance.enrollmentDate.time
                        is ProgramInstance.EventInstance -> instance.eventDate?.time ?: 0L
                    }
                }.thenBy { instance ->
                    when (instance) {
                        is ProgramInstance.DatasetInstance -> instance.attributeOptionCombo
                        is ProgramInstance.TrackerEnrollment -> instance.trackedEntityInstance
                        is ProgramInstance.EventInstance -> instance.programStage
                    }
                }
            )
            InstanceSortBy.LAST_UPDATED -> instances.sortedBy { it.lastUpdated.time }
            InstanceSortBy.COMPLETION_STATUS -> instances.sortedBy { it.state.ordinal }
        }

        return if (filter.sortOrder == SortOrder.DESCENDING) sorted.reversed() else sorted
    }

    private fun parseDhis2PeriodToDate(periodId: String): java.util.Date? {
        return try {
            when {
                // Yearly: 2023
                Regex("^\\d{4}$").matches(periodId) -> {
                    java.text.SimpleDateFormat("yyyy", java.util.Locale.ENGLISH).parse(periodId)
                }
                // Monthly: 202306
                Regex("^\\d{6}$").matches(periodId) -> {
                    java.text.SimpleDateFormat("yyyyMM", java.util.Locale.ENGLISH).parse(periodId)
                }
                // Daily: 2023-06-01
                Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(periodId) -> {
                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ENGLISH).parse(periodId)
                }
                // Weekly: 2023W23
                Regex("^\\d{4}W\\d{1,2}$").matches(periodId) -> {
                    val year = periodId.substring(0, 4).toInt()
                    val week = periodId.substring(5).toInt()
                    val cal = java.util.Calendar.getInstance(java.util.Locale.ENGLISH)
                    cal.clear()
                    cal.set(java.util.Calendar.YEAR, year)
                    cal.set(java.util.Calendar.WEEK_OF_YEAR, week)
                    cal.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
                    cal.time
                }
                // Quarterly: 2023Q2
                Regex("^\\d{4}Q[1-4]$").matches(periodId) -> {
                    val year = periodId.substring(0, 4).toInt()
                    val quarter = periodId.substring(5).toInt()
                    val month = (quarter - 1) * 3
                    val cal = java.util.Calendar.getInstance(java.util.Locale.ENGLISH)
                    cal.clear()
                    cal.set(java.util.Calendar.YEAR, year)
                    cal.set(java.util.Calendar.MONTH, month)
                    cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                    cal.time
                }
                // Six-monthly: 2023S1 or 2023S2
                Regex("^\\d{4}S[1-2]$").matches(periodId) -> {
                    val year = periodId.substring(0, 4).toInt()
                    val semester = periodId.substring(5).toInt()
                    val month = if (semester == 1) 0 else 6
                    val cal = java.util.Calendar.getInstance(java.util.Locale.ENGLISH)
                    cal.clear()
                    cal.set(java.util.Calendar.YEAR, year)
                    cal.set(java.util.Calendar.MONTH, month)
                    cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                    cal.time
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
