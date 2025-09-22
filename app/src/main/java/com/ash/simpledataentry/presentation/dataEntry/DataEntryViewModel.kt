package com.ash.simpledataentry.presentation.dataEntry

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.simpledataentry.data.local.DataValueDraftDao
import com.ash.simpledataentry.data.local.DataValueDraftEntity
import com.ash.simpledataentry.data.repositoryImpl.ValidationRepository
import com.ash.simpledataentry.domain.model.*
import com.ash.simpledataentry.data.sync.DetailedSyncProgress
import com.ash.simpledataentry.data.sync.SyncQueueManager
import com.ash.simpledataentry.domain.repository.DataEntryRepository
import com.ash.simpledataentry.domain.useCase.DataEntryUseCases
import com.ash.simpledataentry.util.NetworkUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DataEntryState(
    val datasetId: String = "",
    val datasetName: String = "",
    val period: String = "",
    val orgUnit: String = "",
    val attributeOptionCombo: String = "",
    val attributeOptionComboName: String = "",
    val isEditMode: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val dataValues: List<DataValue> = emptyList(),
    val currentDataValue: DataValue? = null,
    val currentStep: Int = 0,
    val isCompleted: Boolean = false,
    val validationState: ValidationState = ValidationState.VALID,
    val validationMessage: String? = null,
    val expandedSection: String? = null,
    val expandedCategoryGroup: String? = null,
    val categoryComboStructures: Map<String, List<Pair<String, List<Pair<String, String>>>>> = emptyMap(),
    val optionUidsToComboUid: Map<String, Map<Set<String>, String>> = emptyMap(),
    val isNavigating: Boolean = false,
    val saveInProgress: Boolean = false,
    val saveResult: Result<Unit>? = null,
    val attributeOptionCombos: List<Pair<String, String>> = emptyList(),
    val expandedGridRows: Map<String, Set<String>> = emptyMap(),
    val isExpandedSections: Map<String, Boolean> = emptyMap(),
    val currentSectionIndex: Int = -1,
    val totalSections: Int = 0,
    val dataElementGroupedSections: Map<String, Map<String, List<DataValue>>> = emptyMap(),
    val localDraftCount: Int = 0,
    val isSyncing: Boolean = false,
    val detailedSyncProgress: DetailedSyncProgress? = null, // Enhanced sync progress
    val successMessage: String? = null,
    val isValidating: Boolean = false,
    val validationSummary: ValidationSummary? = null,
    val navigationProgress: com.ash.simpledataentry.presentation.core.NavigationProgress? = null, // Enhanced loading progress
    val completionProgress: com.ash.simpledataentry.presentation.core.CompletionProgress? = null, // Enhanced completion progress
    val showCompletionDialog: Boolean = false,
    val completionAction: com.ash.simpledataentry.presentation.core.CompletionAction? = null
)

@HiltViewModel
class DataEntryViewModel @Inject constructor(
    private val application: Application,
    private val repository: DataEntryRepository,
    private val useCases: DataEntryUseCases,
    private val draftDao: DataValueDraftDao,
    private val validationRepository: ValidationRepository,
    private val syncQueueManager: SyncQueueManager
) : ViewModel() {
    private val _state = MutableStateFlow(DataEntryState())
    val state: StateFlow<DataEntryState> = _state.asStateFlow()

    // Track unsaved edits: key = Pair<dataElement, categoryOptionCombo>, value = DataValue
    private val dirtyDataValues = mutableMapOf<Pair<String, String>, DataValue>()

    init {
        // Observe sync progress from SyncQueueManager
        viewModelScope.launch {
            syncQueueManager.detailedProgress.collect { progress ->
                _state.update { currentState ->
                    currentState.copy(
                        detailedSyncProgress = progress,
                        isSyncing = progress != null
                    )
                }
            }
        }
    }

    // --- BEGIN: Per-field TextFieldValue state ---
    private val _fieldStates = mutableStateMapOf<String, androidx.compose.ui.text.input.TextFieldValue>()
    val fieldStates: Map<String, androidx.compose.ui.text.input.TextFieldValue> get() = _fieldStates
    private fun fieldKey(dataElement: String, categoryOptionCombo: String): String = "$dataElement|$categoryOptionCombo"
    fun initializeFieldState(dataValue: DataValue) {
        val key = fieldKey(dataValue.dataElement, dataValue.categoryOptionCombo)
        if (!_fieldStates.containsKey(key)) {
            _fieldStates[key] = androidx.compose.ui.text.input.TextFieldValue(dataValue.value ?: "")
        }
    }
    fun onFieldValueChange(newValue: androidx.compose.ui.text.input.TextFieldValue, dataValue: DataValue) {
        val key = fieldKey(dataValue.dataElement, dataValue.categoryOptionCombo)
        _fieldStates[key] = newValue
        updateCurrentValue(newValue.text, dataValue.dataElement, dataValue.categoryOptionCombo)
    }
    // --- END: Per-field TextFieldValue state ---

    private var savePressed = false

    fun loadDataValues(
        datasetId: String,
        datasetName: String,
        period: String,
        orgUnitId: String,
        attributeOptionCombo: String,
        isEditMode: Boolean
    ) {
        viewModelScope.launch {
            try {
                _state.update { currentState ->
                    currentState.copy(
                        isLoading = true,
                        error = null,
                        datasetId = datasetId,
                        datasetName = datasetName,
                        period = period,
                        orgUnit = orgUnitId,
                        attributeOptionCombo = attributeOptionCombo,
                        isEditMode = isEditMode,
                        navigationProgress = com.ash.simpledataentry.presentation.core.NavigationProgress(
                            phase = com.ash.simpledataentry.presentation.core.LoadingPhase.INITIALIZING,
                            overallPercentage = 10,
                            phaseTitle = com.ash.simpledataentry.presentation.core.LoadingPhase.INITIALIZING.title,
                            phaseDetail = "Preparing form..."
                        )
                    )
                }

                // Step 1: Load Drafts (10-30%)
                _state.update { it.copy(
                    navigationProgress = com.ash.simpledataentry.presentation.core.NavigationProgress(
                        phase = com.ash.simpledataentry.presentation.core.LoadingPhase.LOADING_DATA,
                        overallPercentage = 25,
                        phaseTitle = com.ash.simpledataentry.presentation.core.LoadingPhase.LOADING_DATA.title,
                        phaseDetail = "Loading draft data..."
                    )
                )}

                val drafts = withContext(Dispatchers.IO) {
                    draftDao.getDraftsForInstance(datasetId, period, orgUnitId, attributeOptionCombo)
                }
                val draftMap = drafts.associateBy { it.dataElement to it.categoryOptionCombo }

                val attributeOptionComboDeferred = async {
                    repository.getAttributeOptionCombos(datasetId)
                }

                // Step 2: Load Data Values (30-50%)
                _state.update { it.copy(
                    navigationProgress = com.ash.simpledataentry.presentation.core.NavigationProgress(
                        phase = com.ash.simpledataentry.presentation.core.LoadingPhase.LOADING_DATA,
                        overallPercentage = 40,
                        phaseTitle = com.ash.simpledataentry.presentation.core.LoadingPhase.LOADING_DATA.title,
                        phaseDetail = "Loading form data..."
                    )
                )}

                val dataValuesFlow = repository.getDataValues(datasetId, period, orgUnitId, attributeOptionCombo)
                dataValuesFlow.collect { values ->
                    // Step 3: Process Categories (50-70%)
                    _state.update { it.copy(
                        navigationProgress = com.ash.simpledataentry.presentation.core.NavigationProgress(
                            phase = com.ash.simpledataentry.presentation.core.LoadingPhase.PROCESSING_DATA,
                            overallPercentage = 60,
                            phaseTitle = com.ash.simpledataentry.presentation.core.LoadingPhase.PROCESSING_DATA.title,
                            phaseDetail = "Processing categories..."
                        )
                    )}

                    val uniqueCategoryCombos = values
                        .mapNotNull { it.categoryOptionCombo }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .toSet()

                    val categoryComboStructures = mutableMapOf<String, List<Pair<String, List<Pair<String, String>>>>>()
                    val optionUidsToComboUid = mutableMapOf<String, Map<Set<String>, String>>()

                    uniqueCategoryCombos.map { comboUid ->
                        async {
                            if (!categoryComboStructures.containsKey(comboUid)) {
                                val structure = repository.getCategoryComboStructure(comboUid)
                                categoryComboStructures[comboUid] = structure

                                val combos = repository.getCategoryOptionCombos(comboUid)
                                val map = combos.associate { coc ->
                                    val optionUids = coc.second.toSet()
                                    optionUids to coc.first
                                }
                                optionUidsToComboUid[comboUid] = map
                            }
                        }
                    }.awaitAll()

                    val attributeOptionCombos = attributeOptionComboDeferred.await()
                    val attributeOptionComboName = attributeOptionCombos.find { it.first == attributeOptionCombo }?.second ?: attributeOptionCombo

                    val mergedValues = values.map { fetched ->
                        val key = fetched.dataElement to fetched.categoryOptionCombo
                        draftMap[key]?.let { draft ->
                            fetched.copy(
                                value = draft.value,
                                comment = draft.comment,
                                lastModified = draft.lastModified
                            )
                        } ?: fetched
                    }

                    dirtyDataValues.clear()
                    savePressed = false // Reset save state when loading new data
                    draftMap.forEach { (key, draft) ->
                        mergedValues.find { it.dataElement == key.first && it.categoryOptionCombo == key.second }?.let { merged ->
                            dirtyDataValues[key] = merged
                        }
                    }

                    // Step 4: Finalizing (70-100%)
                    _state.update { it.copy(
                        navigationProgress = com.ash.simpledataentry.presentation.core.NavigationProgress(
                            phase = com.ash.simpledataentry.presentation.core.LoadingPhase.COMPLETING,
                            overallPercentage = 90,
                            phaseTitle = com.ash.simpledataentry.presentation.core.LoadingPhase.COMPLETING.title,
                            phaseDetail = "Setting up form..."
                        )
                    )}

                    _state.update { currentState ->

                        val groupedBySection = mergedValues.groupBy { it.sectionName } // Group once
                        val dataElementGroupedSections = groupedBySection.mapValues { (_, sectionValues) ->
                            sectionValues.groupBy { it.dataElement }
                        }
                        val totalSections = groupedBySection.size

                        // Determine the initial currentSectionIndex
                        val initialOrPreservedIndex = if (totalSections > 0) {
                            // If you want to ALWAYS open the first section on load, uncomment next line:
                            // 0
                            // If you want to respect a previously opened section or default to closed:
                            if (currentState.currentSectionIndex >= 0 && currentState.currentSectionIndex < totalSections) {
                                currentState.currentSectionIndex // Preserve if valid
                            } else if (currentState.currentSectionIndex == -1 && currentState.dataValues.isEmpty()) { // First ever load and state is still default
                                0 // Open first section on very first load
                            }
                            else {
                                currentState.currentSectionIndex // Keep as -1 or whatever it was if sections changed
                            }
                        } else {
                            -1 // No sections, so no section can be open
                        }.let {
                            // Final check to ensure index is valid or -1
                            if (it >= totalSections && totalSections > 0) totalSections -1
                            else if (it < -1) -1
                            else it
                        }

                        currentState.copy(
                            dataValues = mergedValues,
                            totalSections = totalSections,
                            currentSectionIndex = initialOrPreservedIndex,
                            currentDataValue = mergedValues.firstOrNull(),
                            currentStep = 0,
                            isLoading = false,
                            expandedSection = null,
                            categoryComboStructures = categoryComboStructures,
                            optionUidsToComboUid = optionUidsToComboUid,
                            attributeOptionComboName = attributeOptionComboName,
                            attributeOptionCombos = attributeOptionCombos,
                            dataElementGroupedSections = dataElementGroupedSections,
                            navigationProgress = null // Clear progress when done
                        )
                    }
                }
                
                // Load draft count after data is loaded
                loadDraftCount()
                
            } catch (e: Exception) {
                Log.e("DataEntryViewModel", "Failed to load data values", e)
                _state.update { currentState ->
                    currentState.copy(
                        error = "Failed to load data values: ${e.message}",
                        isLoading = false,
                        navigationProgress = com.ash.simpledataentry.presentation.core.NavigationProgress.error(e.message ?: "Failed to load data values")
                    )
                }
            }
        }
    }

    fun updateCurrentValue(value: String, dataElementUid: String, categoryOptionComboUid: String) {
        val key = dataElementUid to categoryOptionComboUid
        val dataValueToUpdate = _state.value.dataValues.find {
            it.dataElement == dataElementUid && it.categoryOptionCombo == categoryOptionComboUid
        }
        if (dataValueToUpdate != null) {
            val updatedValueObject = dataValueToUpdate.copy(value = value)
            dirtyDataValues[key] = updatedValueObject
            
            // Reset savePressed when new changes are made after a save
            if (savePressed) {
                savePressed = false
            }
            _state.update { currentState ->
                currentState.copy(
                    dataValues = currentState.dataValues.map {
                        if (it.dataElement == dataElementUid && it.categoryOptionCombo == categoryOptionComboUid) updatedValueObject else it
                    },
                    currentDataValue = if (currentState.currentDataValue?.dataElement == dataElementUid && currentState.currentDataValue?.categoryOptionCombo == categoryOptionComboUid) updatedValueObject else currentState.currentDataValue
                )
            }
            viewModelScope.launch(Dispatchers.IO) {
                if (value.isNotBlank()) {
                    draftDao.upsertDraft(
                        DataValueDraftEntity(
                            datasetId = _state.value.datasetId,
                            period = _state.value.period,
                            orgUnit = _state.value.orgUnit,
                            attributeOptionCombo = _state.value.attributeOptionCombo,
                            dataElement = dataElementUid,
                            categoryOptionCombo = categoryOptionComboUid,
                            value = value,
                            comment = updatedValueObject.comment,
                            lastModified = System.currentTimeMillis()
                        )
                    )
                } else {
                    draftDao.deleteDraft(
                        datasetId = _state.value.datasetId,
                        period = _state.value.period,
                        orgUnit = _state.value.orgUnit,
                        attributeOptionCombo = _state.value.attributeOptionCombo,
                        dataElement = dataElementUid,
                        categoryOptionCombo = categoryOptionComboUid
                    )
                }
                
                // Update draft count after draft operation
                loadDraftCount()
            }
        }
    }

    fun saveAllDataValues(context: android.content.Context? = null) {
        viewModelScope.launch {
            _state.update { it.copy(saveInProgress = true, saveResult = null) }
            savePressed = true
            val stateSnapshot = _state.value

            try {
                val draftsToSave = dirtyDataValues.values.map { dataValue ->
                    DataValueDraftEntity(
                        datasetId = stateSnapshot.datasetId,
                        period = stateSnapshot.period,
                        orgUnit = stateSnapshot.orgUnit,
                        attributeOptionCombo = stateSnapshot.attributeOptionCombo,
                        dataElement = dataValue.dataElement,
                        categoryOptionCombo = dataValue.categoryOptionCombo,
                        value = dataValue.value,
                        comment = dataValue.comment,
                        lastModified = System.currentTimeMillis()
                    )
                }

                withContext(Dispatchers.IO) {
                    draftDao.upsertAll(draftsToSave)
                }

                dirtyDataValues.clear()

                _state.update { it.copy(
                    saveInProgress = false,
                    saveResult = Result.success(Unit)
                ) }

            } catch (e: Exception) {
                _state.update { it.copy(
                    saveInProgress = false,
                    saveResult = Result.failure(e)
                ) }
            }
        }
    }

    fun syncCurrentEntryForm() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val stateSnapshot = _state.value
            val isOnline = NetworkUtils.isNetworkAvailable(application)
            if (!isOnline) {
                _state.update { it.copy(isLoading = false, error = "Cannot sync while offline.") }
                return@launch
            }

            try {
                // 1. Load all drafts for the current instance
                val drafts = withContext(Dispatchers.IO) {
                    draftDao.getDraftsForInstance(
                        stateSnapshot.datasetId,
                        stateSnapshot.period,
                        stateSnapshot.orgUnit,
                        stateSnapshot.attributeOptionCombo
                    )
                }
                Log.d("DataEntryViewModel", "Loaded ${'$'}{drafts.size} drafts for sync")

                // 2. For each draft, stage value in SDK
                val results = drafts.map { draft ->
                    async(Dispatchers.IO) {
                        useCases.saveDataValue(
                            datasetId = draft.datasetId,
                            period = draft.period,
                            orgUnit = draft.orgUnit,
                            attributeOptionCombo = draft.attributeOptionCombo,
                            dataElement = draft.dataElement,
                            categoryOptionCombo = draft.categoryOptionCombo,
                            value = draft.value,
                            comment = draft.comment
                        )
                    }
                }.awaitAll()
                val failed = results.filter { it.isFailure }
                if (failed.isNotEmpty()) {
                    Log.e(
                        "DataEntryViewModel",
                        "Failed to stage ${'$'}{failed.size} drafts: ${'$'}{failed.map { it.exceptionOrNull()?.message }}"
                    )
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to stage some values for upload."
                        )
                    }
                    return@launch
                }
                Log.d("DataEntryViewModel", "All drafts staged in SDK")

                // 3. Trigger upload
                try {
                    val uploadResult = withContext(Dispatchers.IO) {
                        repository.syncCurrentEntryForm()
                    }
                    Log.d("DataEntryViewModel", "Data values uploaded successfully: $uploadResult")
                    // Only delete drafts if uploadResult is not null/empty
                    if (uploadResult != null && uploadResult.toString().isNotBlank()) {
                        withContext(Dispatchers.IO) {
                            draftDao.deleteDraftsForInstance(
                                stateSnapshot.datasetId,
                                stateSnapshot.period,
                                stateSnapshot.orgUnit,
                                stateSnapshot.attributeOptionCombo
                            )
                        }
                        Log.d("DataEntryViewModel", "Drafts deleted after successful upload")
                    } else {
                        Log.e(
                            "DataEntryViewModel",
                            "Upload failed or returned empty result: $uploadResult"
                        )
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = "Upload failed or returned empty result."
                            )
                        }
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.e("DataEntryViewModel", "Upload failed: ${'$'}{e.message}", e)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = "Upload failed: ${'$'}{e.message}"
                        )
                    }
                    return@launch
                }

                // 4. Reload data values to refresh UI
                loadDataValues(
                    datasetId = _state.value.datasetId,
                    datasetName = _state.value.datasetName,
                    period = _state.value.period,
                    orgUnitId = _state.value.orgUnit,
                    attributeOptionCombo = _state.value.attributeOptionCombo,
                    isEditMode = _state.value.isEditMode
                )
            }
            catch (e: Exception) {
                null
            }
        }


    }

    private fun loadDraftCount() {
        viewModelScope.launch {
            try {
                val stateSnapshot = _state.value
                val draftCount = withContext(Dispatchers.IO) {
                    draftDao.getDraftCountForInstance(
                        stateSnapshot.datasetId,
                        stateSnapshot.period,
                        stateSnapshot.orgUnit,
                        stateSnapshot.attributeOptionCombo
                    )
                }
                _state.update { it.copy(localDraftCount = draftCount) }
            } catch (e: Exception) {
                Log.e("DataEntryViewModel", "Failed to load draft count", e)
            }
        }
    }

    fun syncDataEntry(uploadFirst: Boolean = false) {
        val stateSnapshot = _state.value
        if (stateSnapshot.datasetId.isEmpty()) {
            Log.e("DataEntryViewModel", "Cannot sync: datasetId is empty")
            return
        }

        Log.d("DataEntryViewModel", "Starting enhanced sync for data entry: datasetId=${stateSnapshot.datasetId}, uploadFirst: $uploadFirst")
        viewModelScope.launch {
            try {
                // Use the enhanced SyncQueueManager which provides detailed progress tracking
                val syncResult = syncQueueManager.startSync(forceSync = uploadFirst)
                syncResult.fold(
                    onSuccess = {
                        Log.d("DataEntryViewModel", "Enhanced sync completed successfully")
                        // Reload all data after sync
                        loadDataValues(
                            datasetId = stateSnapshot.datasetId,
                            datasetName = stateSnapshot.datasetName,
                            period = stateSnapshot.period,
                            orgUnitId = stateSnapshot.orgUnit,
                            attributeOptionCombo = stateSnapshot.attributeOptionCombo,
                            isEditMode = stateSnapshot.isEditMode
                        )
                        val message = if (uploadFirst) {
                            "Data synchronized successfully with enhanced progress tracking"
                        } else {
                            "Data entry synced successfully"
                        }
                        _state.update {
                            it.copy(
                                successMessage = message,
                                detailedSyncProgress = null // Clear progress when done
                            )
                        }
                    },
                    onFailure = { error ->
                        Log.e("DataEntryViewModel", "Enhanced sync failed", error)
                        val errorMessage = error.message ?: "Failed to sync data entry"
                        _state.update {
                            it.copy(
                                error = errorMessage,
                                detailedSyncProgress = null // Clear progress on failure
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("DataEntryViewModel", "Enhanced sync failed", e)
                _state.update {
                    it.copy(
                        isSyncing = false,
                        error = e.message ?: "Failed to sync data entry",
                        detailedSyncProgress = null
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(error = null, successMessage = null) }
    }

    fun dismissSyncOverlay() {
        _state.update {
            it.copy(
                detailedSyncProgress = null,
                isSyncing = false
            )
        }
    }


    fun toggleSection(sectionName: String) {
        _state.update { currentState ->
            val current = currentState.isExpandedSections[sectionName] ?: false
            currentState.copy(
                isExpandedSections = currentState.isExpandedSections.toMutableMap().apply {
                    this[sectionName] = !current
                }
            )
        }
    }

    fun setCurrentSectionIndex(index: Int) {
        _state.update { currentState ->
            if (index < 0 || index >= currentState.totalSections) { // Safety check
                return@update currentState
            }
            val newIndex = if (currentState.currentSectionIndex == index) {
                -1 // If clicking the currently open section, close it
            } else {
                index // Otherwise, open the clicked section
            }
            currentState.copy(currentSectionIndex = newIndex)
        }
    }


    fun goToNextSection() {
        _state.update { currentState ->
            if (currentState.totalSections == 0) return@update currentState

            val newIndex = if (currentState.currentSectionIndex == -1) {
                0 // If nothing is open, "Next" opens the first section
            } else {
                (currentState.currentSectionIndex + 1).coerceAtMost(currentState.totalSections - 1)
            }
            currentState.copy(currentSectionIndex = newIndex)
        }
    }

    fun goToPreviousSection() {
        _state.update { currentState ->
            if (currentState.totalSections == 0) return@update currentState

            val newIndex = if (currentState.currentSectionIndex == -1) {
                currentState.totalSections - 1 // If nothing is open, "Previous" opens the last section
            } else {
                (currentState.currentSectionIndex - 1).coerceAtLeast(0)
            }
            currentState.copy(currentSectionIndex = newIndex)
        }
    }


    fun toggleCategoryGroup(sectionName: String, categoryGroup: String) {
        _state.update { currentState ->
            val key = "$sectionName:$categoryGroup"
            val newExpanded = if (currentState.expandedCategoryGroup == key) null else key
            currentState.copy(expandedCategoryGroup = newExpanded)
        }
    }

    fun moveToNextStep(): Boolean {
        val currentStep = _state.value.currentStep
        val totalSteps = _state.value.dataValues.size
        return if (currentStep < totalSteps - 1) {
            _state.update { currentState ->
                currentState.copy(
                    currentStep = currentStep + 1,
                    currentDataValue = _state.value.dataValues[currentStep + 1]
                )
            }
            false
        } else {
            _state.update { currentState ->
                currentState.copy(
                    isEditMode = true,
                    isCompleted = true
                )
            }
            true
        }
    }

    fun moveToPreviousStep(): Boolean {
        val currentStep = _state.value.currentStep
        return if (currentStep > 0) {
            _state.update { currentState ->
                currentState.copy(
                    currentStep = currentStep - 1,
                    currentDataValue = _state.value.dataValues[currentStep - 1]
                )
            }
            true
        } else {
            false
        }
    }

    suspend fun getAvailablePeriods(datasetId: String): List<Period> {
        return repository.getAvailablePeriods(datasetId)
    }

    suspend fun getUserOrgUnit(datasetId: String): OrganisationUnit? {
        return try {
            repository.getUserOrgUnit(datasetId)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getUserOrgUnits(datasetId: String): List<OrganisationUnit> {
        return try {
            repository.getUserOrgUnits(datasetId)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getDefaultAttributeOptionCombo(): String {
        return try {
            repository.getDefaultAttributeOptionCombo()
        } catch (e: Exception) {
            "default"
        }
    }

    suspend fun getAttributeOptionCombos(datasetId: String): List<Pair<String, String>> {
        return repository.getAttributeOptionCombos(datasetId)
    }

    fun setNavigating(isNavigating: Boolean) {
        _state.update { it.copy(isNavigating = isNavigating) }
    }

    fun resetSaveFeedback() {
        _state.update { it.copy(saveResult = null, saveInProgress = false) }
        // Don't reset savePressed here - only reset when new changes are made
    }

    fun wasSavePressed(): Boolean = savePressed
    
    fun hasUnsavedChanges(): Boolean = dirtyDataValues.isNotEmpty()

    fun clearDraftsForCurrentInstance() {
        val stateSnapshot = _state.value
        viewModelScope.launch(Dispatchers.IO) {
            draftDao.deleteDraftsForInstance(
                stateSnapshot.datasetId,
                stateSnapshot.period,
                stateSnapshot.orgUnit,
                stateSnapshot.attributeOptionCombo
            )
            dirtyDataValues.clear()
            savePressed = false // Reset save state when discarding changes
            withContext(Dispatchers.Main) {
                loadDataValues(
                    datasetId = stateSnapshot.datasetId,
                    datasetName = stateSnapshot.datasetName,
                    period = stateSnapshot.period,
                    orgUnitId = stateSnapshot.orgUnit,
                    attributeOptionCombo = stateSnapshot.attributeOptionCombo,
                    isEditMode = true
                )
            }
        }
    }

    /**
     * Clear only the current session's unsaved changes without affecting previously saved drafts
     */
    fun clearCurrentSessionChanges() {
        // Simply reset the dirty tracking and field states to their loaded values
        val currentState = _state.value

        // Reset field states to their loaded values from the database
        dirtyDataValues.clear()

        // Reset field states to original loaded values
        _fieldStates.clear()
        currentState.dataValues.forEach { dataValue ->
            val key = "${dataValue.dataElement}|${dataValue.categoryOptionCombo}"
            _fieldStates[key] = androidx.compose.ui.text.input.TextFieldValue(dataValue.value ?: "")
        }

        savePressed = false

        Log.d("DataEntryViewModel", "Cleared current session changes, preserved existing drafts")
    }

    fun toggleGridRow(sectionName: String, rowKey: String) {
        _state.update { currentState ->
            val currentSet = currentState.expandedGridRows[sectionName] ?: emptySet()
            val newSet =
                if (currentSet.contains(rowKey)) currentSet - rowKey else currentSet + rowKey
            currentState.copy(
                expandedGridRows = currentState.expandedGridRows.toMutableMap().apply {
                    put(sectionName, newSet)
                }
            )
        }
    }

    fun isGridRowExpanded(sectionName: String, rowKey: String): Boolean {
        return _state.value.expandedGridRows[sectionName]?.contains(rowKey) == true
    }

    fun startValidationForCompletion() {
        val stateSnapshot = _state.value
        viewModelScope.launch {
            Log.d("DataEntryViewModel", "=== COMPLETION FLOW: Starting validation for completion ===")
            Log.d("DataEntryViewModel", "Current isCompleted state: ${stateSnapshot.isCompleted}")
            Log.d("DataEntryViewModel", "Dataset: ${stateSnapshot.datasetId}, Period: ${stateSnapshot.period}, OrgUnit: ${stateSnapshot.orgUnit}")
            _state.update { it.copy(isValidating = true, error = null, validationSummary = null) }
            
            try {
                val validationResult = validationRepository.validateDatasetInstance(
                    datasetId = stateSnapshot.datasetId,
                    period = stateSnapshot.period,
                    organisationUnit = stateSnapshot.orgUnit,
                    attributeOptionCombo = stateSnapshot.attributeOptionCombo,
                    dataValues = stateSnapshot.dataValues,
                    forceRefresh = true // Always refresh validation for completion
                )
                
                Log.d("DataEntryViewModel", "Validation completed: ${validationResult.errorCount} errors, ${validationResult.warningCount} warnings")
                
                _state.update { 
                    it.copy(
                        isValidating = false, 
                        validationSummary = validationResult
                    ) 
                }
                
            } catch (e: Exception) {
                Log.e("DataEntryViewModel", "Error during validation: ${e.message}", e)
                _state.update {
                    it.copy(
                        isValidating = false,
                        error = "Error during validation: ${e.message}"
                    )
                }
            }
        }
    }

    fun completeDatasetAfterValidation(onResult: (Boolean, String?) -> Unit) {
        val stateSnapshot = _state.value
        viewModelScope.launch {
            Log.d("DataEntryViewModel", "=== COMPLETION FLOW: Proceeding with dataset completion after validation ===")
            Log.d("DataEntryViewModel", "Dataset: ${stateSnapshot.datasetId}, Period: ${stateSnapshot.period}, OrgUnit: ${stateSnapshot.orgUnit}")
            Log.d("DataEntryViewModel", "AttributeOptionCombo: ${stateSnapshot.attributeOptionCombo}")
            _state.update { it.copy(isLoading = true, error = null) }
            
            try {
                val result = useCases.completeDatasetInstance(
                    stateSnapshot.datasetId,
                    stateSnapshot.period,
                    stateSnapshot.orgUnit,
                    stateSnapshot.attributeOptionCombo
                )
                
                if (result.isSuccess) {
                    val validationSummary = stateSnapshot.validationSummary
                    val successMessage = if (validationSummary?.warningCount ?: 0 > 0) {
                        "Dataset marked as complete successfully. Note: ${validationSummary?.warningCount} validation warning(s) were found."
                    } else {
                        "Dataset marked as complete successfully. All validation rules passed."
                    }
                    
                    Log.d("DataEntryViewModel", successMessage)
                    _state.update { it.copy(isCompleted = true, isLoading = false, validationSummary = null) }
                    onResult(true, successMessage)
                } else {
                    Log.e("DataEntryViewModel", "Failed to mark dataset as complete: ${result.exceptionOrNull()?.message}")
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = result.exceptionOrNull()?.message
                        )
                    }
                    onResult(false, result.exceptionOrNull()?.message)
                }
                
            } catch (e: Exception) {
                Log.e("DataEntryViewModel", "Error during completion: ${e.message}", e)
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Error during completion: ${e.message}"
                    )
                }
                onResult(false, "Error during completion: ${e.message}")
            }
        }
    }

    fun markDatasetIncomplete(onResult: (Boolean, String?) -> Unit) {
        val stateSnapshot = _state.value
        viewModelScope.launch {
            Log.d("DataEntryViewModel", "=== COMPLETION FLOW: Marking dataset as incomplete ===")
            Log.d("DataEntryViewModel", "Dataset: ${stateSnapshot.datasetId}, Period: ${stateSnapshot.period}, OrgUnit: ${stateSnapshot.orgUnit}")
            Log.d("DataEntryViewModel", "AttributeOptionCombo: ${stateSnapshot.attributeOptionCombo}")
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                val result = useCases.markDatasetInstanceIncomplete(
                    stateSnapshot.datasetId,
                    stateSnapshot.period,
                    stateSnapshot.orgUnit,
                    stateSnapshot.attributeOptionCombo
                )

                if (result.isSuccess) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isCompleted = false,
                            error = null
                        )
                    }
                    onResult(true, "Dataset marked as incomplete")
                } else {
                    Log.e("DataEntryViewModel", "Failed to mark dataset incomplete: ${result.exceptionOrNull()}")
                    _state.update { it.copy(isLoading = false, error = result.exceptionOrNull()?.message) }
                    onResult(false, result.exceptionOrNull()?.message)
                }
            } catch (e: Exception) {
                Log.e("DataEntryViewModel", "Exception marking dataset incomplete: ${e.message}", e)
                _state.update { it.copy(isLoading = false, error = e.message) }
                onResult(false, e.message)
            }
        }
    }

    fun clearValidationResult() {
        _state.update { it.copy(validationSummary = null) }
    }

    // === Enhanced Completion Workflow ===

    fun showCompletionDialog() {
        _state.update { it.copy(showCompletionDialog = true) }
    }

    fun dismissCompletionDialog() {
        _state.update {
            it.copy(
                showCompletionDialog = false,
                completionAction = null,
                completionProgress = null
            )
        }
    }

    fun startCompletionWorkflow(action: com.ash.simpledataentry.presentation.core.CompletionAction) {
        _state.update {
            it.copy(
                completionAction = action,
                showCompletionDialog = false
            )
        }

        when (action) {
            com.ash.simpledataentry.presentation.core.CompletionAction.VALIDATE_AND_COMPLETE -> {
                startValidationWithCompletionProgress()
            }
            com.ash.simpledataentry.presentation.core.CompletionAction.COMPLETE_WITHOUT_VALIDATION -> {
                completeDirectlyWithProgress()
            }
            com.ash.simpledataentry.presentation.core.CompletionAction.RERUN_VALIDATION -> {
                validateWithoutCompletion()
            }
            com.ash.simpledataentry.presentation.core.CompletionAction.MARK_INCOMPLETE -> {
                markIncompleteWithProgress()
            }
        }
    }

    private fun startValidationWithCompletionProgress() {
        val stateSnapshot = _state.value
        viewModelScope.launch {
            try {
                // Step 1: Preparing (0-10%)
                _state.update {
                    it.copy(
                        isValidating = true,
                        error = null,
                        validationSummary = null,
                        completionProgress = com.ash.simpledataentry.presentation.core.CompletionProgress(
                            phase = com.ash.simpledataentry.presentation.core.CompletionPhase.PREPARING,
                            overallPercentage = 5,
                            phaseTitle = com.ash.simpledataentry.presentation.core.CompletionPhase.PREPARING.title,
                            phaseDetail = "Setting up validation...",
                            isValidating = true
                        )
                    )
                }

                // Step 2: Validating (10-70%)
                _state.update {
                    it.copy(
                        completionProgress = com.ash.simpledataentry.presentation.core.CompletionProgress(
                            phase = com.ash.simpledataentry.presentation.core.CompletionPhase.VALIDATING,
                            overallPercentage = 20,
                            phaseTitle = com.ash.simpledataentry.presentation.core.CompletionPhase.VALIDATING.title,
                            phaseDetail = "Running validation rules...",
                            isValidating = true
                        )
                    )
                }

                val validationResult = validationRepository.validateDatasetInstance(
                    datasetId = stateSnapshot.datasetId,
                    period = stateSnapshot.period,
                    organisationUnit = stateSnapshot.orgUnit,
                    attributeOptionCombo = stateSnapshot.attributeOptionCombo,
                    dataValues = stateSnapshot.dataValues,
                    forceRefresh = true
                )

                // Step 3: Processing Results (70-90%)
                _state.update {
                    it.copy(
                        completionProgress = com.ash.simpledataentry.presentation.core.CompletionProgress(
                            phase = com.ash.simpledataentry.presentation.core.CompletionPhase.PROCESSING_RESULTS,
                            overallPercentage = 80,
                            phaseTitle = com.ash.simpledataentry.presentation.core.CompletionPhase.PROCESSING_RESULTS.title,
                            phaseDetail = "Analyzing validation results...",
                            isValidating = true
                        )
                    )
                }

                _state.update {
                    it.copy(
                        isValidating = false,
                        validationSummary = validationResult,
                        completionProgress = null // Clear progress when validation dialog shows
                    )
                }

            } catch (e: Exception) {
                Log.e("DataEntryViewModel", "Enhanced validation failed", e)
                _state.update {
                    it.copy(
                        isValidating = false,
                        error = "Validation failed: ${e.message}",
                        completionProgress = com.ash.simpledataentry.presentation.core.CompletionProgress.error(
                            e.message ?: "Validation failed"
                        )
                    )
                }
            }
        }
    }

    private fun completeDirectlyWithProgress() {
        val stateSnapshot = _state.value
        viewModelScope.launch {
            try {
                // Step 1: Preparing (0-10%)
                _state.update {
                    it.copy(
                        isLoading = true,
                        error = null,
                        completionProgress = com.ash.simpledataentry.presentation.core.CompletionProgress(
                            phase = com.ash.simpledataentry.presentation.core.CompletionPhase.PREPARING,
                            overallPercentage = 10,
                            phaseTitle = com.ash.simpledataentry.presentation.core.CompletionPhase.PREPARING.title,
                            phaseDetail = "Preparing to complete dataset..."
                        )
                    )
                }

                // Step 2: Completing (10-90%)
                _state.update {
                    it.copy(
                        completionProgress = com.ash.simpledataentry.presentation.core.CompletionProgress(
                            phase = com.ash.simpledataentry.presentation.core.CompletionPhase.COMPLETING,
                            overallPercentage = 50,
                            phaseTitle = com.ash.simpledataentry.presentation.core.CompletionPhase.COMPLETING.title,
                            phaseDetail = "Marking dataset as complete..."
                        )
                    )
                }

                val result = useCases.completeDatasetInstance(
                    stateSnapshot.datasetId,
                    stateSnapshot.period,
                    stateSnapshot.orgUnit,
                    stateSnapshot.attributeOptionCombo
                )

                if (result.isSuccess) {
                    // Step 3: Completed (90-100%)
                    _state.update {
                        it.copy(
                            completionProgress = com.ash.simpledataentry.presentation.core.CompletionProgress(
                                phase = com.ash.simpledataentry.presentation.core.CompletionPhase.COMPLETED,
                                overallPercentage = 100,
                                phaseTitle = com.ash.simpledataentry.presentation.core.CompletionPhase.COMPLETED.title,
                                phaseDetail = "Dataset completed successfully!"
                            )
                        )
                    }

                    // Show success briefly, then clear
                    kotlinx.coroutines.delay(1500)
                    _state.update {
                        it.copy(
                            isCompleted = true,
                            isLoading = false,
                            completionProgress = null,
                            successMessage = "Dataset marked as complete successfully."
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = result.exceptionOrNull()?.message ?: "Failed to complete dataset",
                            completionProgress = com.ash.simpledataentry.presentation.core.CompletionProgress.error(
                                result.exceptionOrNull()?.message ?: "Failed to complete dataset"
                            )
                        )
                    }
                }

            } catch (e: Exception) {
                Log.e("DataEntryViewModel", "Direct completion failed", e)
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Completion failed: ${e.message}",
                        completionProgress = com.ash.simpledataentry.presentation.core.CompletionProgress.error(
                            e.message ?: "Completion failed"
                        )
                    )
                }
            }
        }
    }

    private fun validateWithoutCompletion() {
        startValidationForCompletion() // Use existing validation method
    }

    private fun markIncompleteWithProgress() {
        val stateSnapshot = _state.value
        viewModelScope.launch {
            try {
                _state.update {
                    it.copy(
                        isLoading = true,
                        error = null,
                        completionProgress = com.ash.simpledataentry.presentation.core.CompletionProgress(
                            phase = com.ash.simpledataentry.presentation.core.CompletionPhase.PREPARING,
                            overallPercentage = 30,
                            phaseTitle = "Marking Incomplete",
                            phaseDetail = "Updating dataset status..."
                        )
                    )
                }

                val result = useCases.markDatasetInstanceIncomplete(
                    stateSnapshot.datasetId,
                    stateSnapshot.period,
                    stateSnapshot.orgUnit,
                    stateSnapshot.attributeOptionCombo
                )

                if (result.isSuccess) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isCompleted = false,
                            error = null,
                            completionProgress = null,
                            successMessage = "Dataset marked as incomplete successfully."
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = result.exceptionOrNull()?.message ?: "Failed to mark incomplete",
                            completionProgress = com.ash.simpledataentry.presentation.core.CompletionProgress.error(
                                result.exceptionOrNull()?.message ?: "Failed to mark incomplete"
                            )
                        )
                    }
                }

            } catch (e: Exception) {
                Log.e("DataEntryViewModel", "Mark incomplete failed", e)
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to mark incomplete",
                        completionProgress = com.ash.simpledataentry.presentation.core.CompletionProgress.error(
                            e.message ?: "Failed to mark incomplete"
                        )
                    )
                }
            }
        }
    }
}
