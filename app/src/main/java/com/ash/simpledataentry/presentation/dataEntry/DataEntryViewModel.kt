package com.ash.simpledataentry.presentation.dataEntry

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.simpledataentry.data.local.DataValueDraftDao
import com.ash.simpledataentry.data.local.DataValueDraftEntity
import com.ash.simpledataentry.domain.model.*
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
    val isExpandedSections: Map<String, Boolean> = emptyMap()
)

@HiltViewModel
class DataEntryViewModel @Inject constructor(
    private val application: Application,
    private val repository: DataEntryRepository,
    private val useCases: DataEntryUseCases,
    private val draftDao: DataValueDraftDao
) : ViewModel() {
    private val _state = MutableStateFlow(DataEntryState())
    val state: StateFlow<DataEntryState> = _state.asStateFlow()

    // Track unsaved edits: key = Pair<dataElement, categoryOptionCombo>, value = DataValue
    private val dirtyDataValues = mutableMapOf<Pair<String, String>, DataValue>()

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
                        isEditMode = isEditMode
                    )
                }

                val drafts = withContext(Dispatchers.IO) {
                    draftDao.getDraftsForInstance(datasetId, period, orgUnitId, attributeOptionCombo)
                }
                val draftMap = drafts.associateBy { it.dataElement to it.categoryOptionCombo }

                val attributeOptionComboDeferred = async {
                    repository.getAttributeOptionCombos(datasetId)
                }

                val dataValuesFlow = repository.getDataValues(datasetId, period, orgUnitId, attributeOptionCombo)
                dataValuesFlow.collect { values ->
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
                    draftMap.forEach { (key, draft) ->
                        mergedValues.find { it.dataElement == key.first && it.categoryOptionCombo == key.second }?.let { merged ->
                            dirtyDataValues[key] = merged
                        }
                    }

                    _state.update { currentState ->
                        currentState.copy(
                            dataValues = mergedValues,
                            currentDataValue = mergedValues.firstOrNull(),
                            currentStep = 0,
                            isLoading = false,
                            expandedSection = null,
                            categoryComboStructures = categoryComboStructures,
                            optionUidsToComboUid = optionUidsToComboUid,
                            attributeOptionComboName = attributeOptionComboName,
                            attributeOptionCombos = attributeOptionCombos
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("DataEntryViewModel", "Failed to load data values", e)
                _state.update { currentState ->
                    currentState.copy(
                        error = "Failed to load data values: ${e.message}",
                        isLoading = false
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
                    Log.e("DataEntryViewModel", "Failed to stage ${'$'}{failed.size} drafts: ${'$'}{failed.map { it.exceptionOrNull()?.message }}")
                    _state.update { it.copy(isLoading = false, error = "Failed to stage some values for upload.") }
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
                        Log.e("DataEntryViewModel", "Upload failed or returned empty result: $uploadResult")
                        _state.update { it.copy(isLoading = false, error = "Upload failed or returned empty result.") }
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.e("DataEntryViewModel", "Upload failed: ${'$'}{e.message}", e)
                    _state.update { it.copy(isLoading = false, error = "Upload failed: ${'$'}{e.message}") }
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

                _state.update { it.copy(isLoading = false, error = null) }
            } catch (e: Exception) {
                Log.e("DataEntryViewModel", "Sync failed: ${'$'}{e.message}", e)
                _state.update { currentState ->
                    currentState.copy(error = "Sync failed: ${'$'}{e.message}", isLoading = false)
                }
            }
        }
    }
    
    fun updateComment(comment: String) {
        _state.value.currentDataValue?.let { currentValue ->
            _state.update { currentState ->
                currentState.copy(
                    currentDataValue = currentValue.copy(
                        comment = comment
                    )
                )
            }
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

    fun toggleCategoryGroup(sectionName: String, categoryGroup: String) {
        _state.update { currentState ->
            val key = "$sectionName:$categoryGroup"
            val newExpanded = if (currentState.expandedCategoryGroup == key) null else key
            currentState.copy(expandedCategoryGroup = newExpanded)
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
        savePressed = false
    }

    fun wasSavePressed(): Boolean = savePressed

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

    fun toggleGridRow(sectionName: String, rowKey: String) {
        _state.update { currentState ->
            val currentSet = currentState.expandedGridRows[sectionName] ?: emptySet()
            val newSet = if (currentSet.contains(rowKey)) currentSet - rowKey else currentSet + rowKey
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
}