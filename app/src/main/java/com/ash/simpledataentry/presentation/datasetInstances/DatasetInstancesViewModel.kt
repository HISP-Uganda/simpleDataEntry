package com.ash.simpledataentry.presentation.datasetInstances

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.ash.simpledataentry.data.local.DataValueDraftDao
import com.ash.simpledataentry.data.sync.SyncQueueManager
import com.ash.simpledataentry.data.sync.DetailedSyncProgress
import com.ash.simpledataentry.presentation.core.NavigationProgress
import com.ash.simpledataentry.presentation.core.LoadingPhase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DatasetInstancesState(
    val instances: List<ProgramInstance> = emptyList(),
    val filteredInstances: List<ProgramInstance> = emptyList(),
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val attributeOptionCombos: List<Pair<String, String>> = emptyList(),
    val dataset: com.ash.simpledataentry.domain.model.Dataset? = null,
    val program: com.ash.simpledataentry.domain.model.Program? = null,
    val programType: ProgramType = ProgramType.DATASET,
    val localInstanceCount: Int = 0,
    val instancesWithDrafts: Set<String> = emptySet(), // Set of instance keys that have draft values
    val detailedSyncProgress: DetailedSyncProgress? = null, // Enhanced sync progress
    val navigationProgress: NavigationProgress? = null // Enhanced loading progress
)

@HiltViewModel
class DatasetInstancesViewModel @Inject constructor(
    private val getDatasetInstancesUseCase: GetDatasetInstancesUseCase,
    private val syncDatasetInstancesUseCase: SyncDatasetInstancesUseCase,
    private val dataEntryRepository: DataEntryRepository,
    private val datasetInstacesRepository: DatasetInstancesRepository,
    private val datasetsRepository: com.ash.simpledataentry.domain.repository.DatasetsRepository,
    private val draftDao: DataValueDraftDao,
    private val syncQueueManager: SyncQueueManager,
    private val sessionManager: com.ash.simpledataentry.data.SessionManager,
    private val app: Application
) : ViewModel() {
    private var programId: String = ""
    private var currentProgramType: ProgramType = ProgramType.DATASET

    private val _state = MutableStateFlow(DatasetInstancesState())
    val state: StateFlow<DatasetInstancesState> = _state.asStateFlow()

    // --- Bulk completion state ---
    private val _bulkCompletionMode = MutableStateFlow(false)
    val bulkCompletionMode: StateFlow<Boolean> = _bulkCompletionMode.asStateFlow()

    private val _selectedInstances = MutableStateFlow<Set<String>>(emptySet())
    val selectedInstances: StateFlow<Set<String>> = _selectedInstances.asStateFlow()

    // Filter state
    private val _filterState = MutableStateFlow(DatasetInstanceFilterState())
    val filterState: StateFlow<DatasetInstanceFilterState> = _filterState.asStateFlow()

    init {
        // Observe sync progress from SyncQueueManager
        viewModelScope.launch {
            syncQueueManager.detailedProgress.collect { progress ->
                _state.value = _state.value.copy(
                    detailedSyncProgress = progress,
                    isSyncing = progress != null
                )
            }
        }
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

        viewModelScope.launch {
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
                // Step 1: Load Configuration (10-30%)
                _state.value = _state.value.copy(
                    navigationProgress = NavigationProgress(
                        phase = LoadingPhase.LOADING_DATA,
                        overallPercentage = 25,
                        phaseTitle = LoadingPhase.LOADING_DATA.title,
                        phaseDetail = "Loading configuration..."
                    )
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
                _state.value = _state.value.copy(
                    navigationProgress = NavigationProgress(
                        phase = LoadingPhase.LOADING_DATA,
                        overallPercentage = 40,
                        phaseTitle = LoadingPhase.LOADING_DATA.title,
                        phaseDetail = "Fetching program information..."
                    )
                )

                // Get program information based on type
                var dataset: com.ash.simpledataentry.domain.model.Dataset? = null
                var program: com.ash.simpledataentry.domain.model.Program? = null

                when (currentProgramType) {
                    ProgramType.DATASET -> {
                        datasetsRepository.getDatasets().collect { datasets ->
                            dataset = datasets.find { it.id == programId }
                        }
                    }
                    ProgramType.TRACKER -> {
                        datasetsRepository.getTrackerPrograms().collect { programs ->
                            program = programs.find { it.id == programId }
                        }
                    }
                    ProgramType.EVENT -> {
                        datasetsRepository.getEventPrograms().collect { programs ->
                            program = programs.find { it.id == programId }
                        }
                    }
                    ProgramType.ALL -> {
                        // Should not happen in this context
                    }
                }
                
                // Step 3: Load Instances (50-70%)
                _state.value = _state.value.copy(
                    navigationProgress = NavigationProgress(
                        phase = LoadingPhase.LOADING_DATA,
                        overallPercentage = 60,
                        phaseTitle = LoadingPhase.LOADING_DATA.title,
                        phaseDetail = "Loading program instances..."
                    )
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
                            var trackerInstances: List<ProgramInstance> = emptyList()
                            datasetInstacesRepository.getProgramInstances(programId, currentProgramType).collect { instances ->
                                trackerInstances = instances
                            }
                            Result.success(trackerInstances)
                        } catch (e: Exception) {
                            Result.failure(e)
                        }
                    }
                    ProgramType.EVENT -> {
                        try {
                            var eventInstances: List<ProgramInstance> = emptyList()
                            datasetInstacesRepository.getProgramInstances(programId, currentProgramType).collect { instances ->
                                eventInstances = instances
                            }
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
                        _state.value = _state.value.copy(
                            navigationProgress = NavigationProgress(
                                phase = LoadingPhase.PROCESSING_DATA,
                                overallPercentage = 80,
                                phaseTitle = LoadingPhase.PROCESSING_DATA.title,
                                phaseDetail = "Checking draft status..."
                            )
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
                        _state.value = _state.value.copy(
                            navigationProgress = NavigationProgress(
                                phase = LoadingPhase.COMPLETING,
                                overallPercentage = 100,
                                phaseTitle = LoadingPhase.COMPLETING.title,
                                phaseDetail = "Ready!"
                            )
                        )

                        _state.value = _state.value.copy(
                            instances = instances,
                            filteredInstances = applyFilters(orderInstances(instances)),
                            isLoading = false,
                            error = null,
                            attributeOptionCombos = attributeOptionCombos,
                            dataset = dataset,
                            program = program,
                            programType = currentProgramType,
                            localInstanceCount = instancesWithDrafts.size,
                            instancesWithDrafts = instancesWithDrafts,
                            navigationProgress = null // Clear progress when done
                        )
                    },
                    onFailure = { error ->
                        Log.e("DatasetInstancesVM", "Error loading data", error)
                        _state.value = _state.value.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load program data",
                            attributeOptionCombos = attributeOptionCombos,
                            dataset = dataset,
                            program = program,
                            programType = currentProgramType,
                            localInstanceCount = 0,
                            navigationProgress = NavigationProgress.error(error.message ?: "Failed to load program data")
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e("DatasetInstancesVM", "Error loading data", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load program data",
                    navigationProgress = NavigationProgress.error(e.message ?: "Failed to load program data")
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
                    com.ash.simpledataentry.domain.model.ProgramType.EVENT -> {
                        Log.d("DatasetInstancesVM", "Syncing event program data from server")
                        val syncResult = datasetInstacesRepository.syncProgramInstances(programId, currentProgramType)
                        syncResult.fold(
                            onSuccess = {
                                Log.d("DatasetInstancesVM", "Event data sync completed successfully")
                                loadData() // Reload all data after sync
                                _state.value = _state.value.copy(
                                    successMessage = "Event instances synced successfully from server",
                                    detailedSyncProgress = null
                                )
                            },
                            onFailure = { error ->
                                Log.e("DatasetInstancesVM", "Event sync failed", error)
                                _state.value = _state.value.copy(
                                    error = "Failed to sync event data: ${error.message}",
                                    detailedSyncProgress = null
                                )
                            }
                        )
                    }
                    com.ash.simpledataentry.domain.model.ProgramType.DATASET -> {
                        // Use the enhanced SyncQueueManager for dataset synchronization
                        val syncResult = syncQueueManager.startSync(forceSync = uploadFirst)
                        syncResult.fold(
                            onSuccess = {
                                Log.d("DatasetInstancesVM", "Dataset sync completed successfully")
                                loadData() // Reload all data after sync
                                val message = if (uploadFirst) {
                                    "Data synchronized successfully with enhanced progress tracking"
                                } else {
                                    "Dataset instances synced successfully"
                                }
                                _state.value = _state.value.copy(
                                    successMessage = message,
                                    detailedSyncProgress = null
                                )
                            },
                            onFailure = { error ->
                                Log.e("DatasetInstancesVM", "Dataset sync failed", error)
                                _state.value = _state.value.copy(
                                    error = error.message ?: "Failed to sync dataset instances",
                                    detailedSyncProgress = null
                                )
                            }
                        )
                    }
                    else -> {
                        Log.w("DatasetInstancesVM", "Unknown program type for sync: $currentProgramType")
                        _state.value = _state.value.copy(
                            error = "Cannot sync: Unknown program type"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("DatasetInstancesVM", "Sync failed", e)
                _state.value = _state.value.copy(
                    isSyncing = false,
                    error = e.message ?: "Failed to sync data",
                    detailedSyncProgress = null
                )
            }
        }
    }

    fun manualRefresh() {
        loadData()
    }

    fun dismissSyncOverlay() {
        // Clear error state in SyncQueueManager to prevent persistent dialogs
        syncQueueManager.clearErrorState()
        _state.value = _state.value.copy(
            detailedSyncProgress = null,
            isSyncing = false
        )
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
        val allInstances = _state.value.instances.associateBy { it.id }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            var anyError: String? = null
            for ((uid, instance) in allInstances) {
                // Only handle completion for dataset instances for now
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
                        else -> null // No action needed
                    }
                    if (result != null && result.isFailure) {
                        anyError = result.exceptionOrNull()?.message ?: "Unknown error"
                        break
                    }
                }
                // TODO: Handle tracker enrollments and events completion
            }
            _state.value = _state.value.copy(isLoading = false)
            if (anyError != null) {
                onResult(false, anyError)
            } else {
                onResult(true, null)
            }
        }
    }

    fun markDatasetInstanceIncomplete(uid: String, onResult: (Boolean, String?) -> Unit) {
        val allInstances = _state.value.instances.associateBy { it.id }
        val instance = allInstances[uid]
        if (instance is ProgramInstance.DatasetInstance) {
            viewModelScope.launch {
                _state.value = _state.value.copy(isLoading = true, error = null)
                val result = datasetInstacesRepository.markDatasetInstanceIncomplete(
                    datasetId = instance.programId,
                    period = instance.period.id,
                    orgUnit = instance.organisationUnit.id,
                    attributeOptionCombo = instance.attributeOptionCombo
                )
                _state.value = _state.value.copy(isLoading = false)
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
        val allInstances = _state.value.instances.associateBy { it.id }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            var anyError: String? = null
            for (uid in uids) {
                val instance = allInstances[uid]
                if (instance is ProgramInstance.DatasetInstance) {
                    val result = datasetInstacesRepository.markDatasetInstanceIncomplete(
                        datasetId = instance.programId,
                        period = instance.period.id,
                        orgUnit = instance.organisationUnit.id,
                        attributeOptionCombo = instance.attributeOptionCombo
                    )
                    if (result.isFailure) {
                        anyError = result.exceptionOrNull()?.message ?: "Unknown error"
                        break
                    }
                }
                // TODO: Handle tracker enrollments and events
            }
            _state.value = _state.value.copy(isLoading = false)
            if (anyError != null) {
                onResult(false, anyError)
            } else {
                onResult(true, null)
            }
        }
    }

    fun markDatasetInstanceComplete(uid: String, onResult: (Boolean, String?) -> Unit) {
        val allInstances = _state.value.instances.associateBy { it.id }
        val instance = allInstances[uid]
        if (instance is ProgramInstance.DatasetInstance) {
            viewModelScope.launch {
                _state.value = _state.value.copy(isLoading = true, error = null)
                val result = datasetInstacesRepository.completeDatasetInstance(
                    datasetId = instance.programId,
                    period = instance.period.id,
                    orgUnit = instance.organisationUnit.id,
                    attributeOptionCombo = instance.attributeOptionCombo
                )
                _state.value = _state.value.copy(isLoading = false)
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
        val currentInstances = _state.value.instances
        _state.value = _state.value.copy(
            filteredInstances = applyFilters(orderInstances(currentInstances))
        )
    }

    private fun applyFilters(instances: List<ProgramInstance>): List<ProgramInstance> {
        val filter = _filterState.value
        val periodHelper = PeriodHelper()

        val periodIds = when (filter.periodType) {
            PeriodFilterType.RELATIVE -> filter.relativePeriod?.let {
                periodHelper.getPeriodIds(it)
            } ?: emptyList()
            PeriodFilterType.CUSTOM_RANGE -> {
                if (filter.customFromDate != null && filter.customToDate != null) {
                    periodHelper.getPeriodIds(filter.customFromDate, filter.customToDate)
                } else emptyList()
            }
            else -> emptyList()
        }

        return instances.filter { instance ->
            // Period filtering only applies to dataset instances
            val periodMatches = when (instance) {
                is ProgramInstance.DatasetInstance -> {
                    if (filter.periodType == PeriodFilterType.ALL) true else instance.period.id in periodIds
                }
                is ProgramInstance.TrackerEnrollment, is ProgramInstance.EventInstance -> true // No period filtering for tracker/events
            }

            // Check sync status based on draft data values
            val instanceKey = createInstanceKey(instance)
            val hasDraftValues = _state.value.instancesWithDrafts.contains(instanceKey)

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
        val currentInstances = _state.value.instances
        _state.value = _state.value.copy(
            filteredInstances = orderInstances(currentInstances)
        )
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
