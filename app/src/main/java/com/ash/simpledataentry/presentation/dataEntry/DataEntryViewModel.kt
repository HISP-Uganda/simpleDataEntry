package com.ash.simpledataentry.presentation.dataEntry

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.simpledataentry.domain.model.*
import com.ash.simpledataentry.domain.repository.DataEntryRepository
import com.ash.simpledataentry.domain.useCase.DataEntryUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    val saveResult: Result<Unit>? = null
)

@HiltViewModel
class DataEntryViewModel @Inject constructor(
    private val repository: DataEntryRepository,
    private val useCases: DataEntryUseCases
) : ViewModel() {
    private val _state = MutableStateFlow(DataEntryState())
    val state: StateFlow<DataEntryState> = _state.asStateFlow()

    // Only call loadDataValues on explicit triggers (initial load or parameter change), not on accordion open/close.
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
                Log.d("DataEntryViewModel", "Loading data values for datasetId=$datasetId, period=$period, orgUnitId=$orgUnitId, attributeOptionCombo=$attributeOptionCombo")
                // Set initial loading state immediately
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

                // Load data in parallel
                val attributeOptionComboDeferred = async {
                    repository.getAttributeOptionCombos(datasetId)
                }

                val dataValuesFlow = repository.getDataValues(datasetId, period, orgUnitId, attributeOptionCombo)
                
                // Collect data values
                dataValuesFlow.collect { values ->
                    Log.d("DataEntryViewModel", "Loaded data values: ${values.size}")
                    // Get unique category combos that are actually used in the data values
                    val uniqueCategoryCombos = values
                        .mapNotNull { it.categoryOptionCombo }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .toSet()

                    // Fetch category combo structures and mappings in parallel
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

                    // Get attribute option combo name
                    val attributeOptionCombos = attributeOptionComboDeferred.await()
                    val attributeOptionComboName = attributeOptionCombos.find { it.first == attributeOptionCombo }?.second ?: attributeOptionCombo

                    _state.update { currentState ->
                        currentState.copy(
                            dataValues = values,
                            currentDataValue = values.firstOrNull(),
                            currentStep = 0,
                            isLoading = false,
                            expandedSection = null,
                            categoryComboStructures = categoryComboStructures,
                            optionUidsToComboUid = optionUidsToComboUid,
                            attributeOptionComboName = attributeOptionComboName
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
        val dataValueToUpdate = _state.value.dataValues.find {
            it.dataElement == dataElementUid && it.categoryOptionCombo == categoryOptionComboUid
        }
        if (dataValueToUpdate != null) {
            val updatedValueObject = dataValueToUpdate.copy(value = value)
            _state.update { currentState ->
                currentState.copy(
                    dataValues = currentState.dataValues.map {
                        if (it.dataElement == dataElementUid && it.categoryOptionCombo == categoryOptionComboUid) updatedValueObject else it
                    },
                    // Only update currentDataValue if this is the current one
                    currentDataValue = if (currentState.currentDataValue?.dataElement == dataElementUid && currentState.currentDataValue?.categoryOptionCombo == categoryOptionComboUid) updatedValueObject else currentState.currentDataValue
                )
            }
            // No backend save here; only in-memory update
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
            val newExpanded = if (currentState.expandedSection == sectionName) null else sectionName
            currentState.copy(expandedSection = newExpanded)
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

    fun saveAllDataValues() {
        viewModelScope.launch {
            _state.update { it.copy(saveInProgress = true, saveResult = null) }
            val stateSnapshot = _state.value
            val failed = mutableListOf<Pair<String, String>>()
            for (dataValue in stateSnapshot.dataValues) {
                val result = useCases.saveDataValue(
                    datasetId = stateSnapshot.datasetId,
                    period = stateSnapshot.period,
                    orgUnit = stateSnapshot.orgUnit,
                    attributeOptionCombo = stateSnapshot.attributeOptionCombo,
                    dataElement = dataValue.dataElement,
                    categoryOptionCombo = dataValue.categoryOptionCombo,
                    value = dataValue.value,
                    comment = dataValue.comment
                )
                if (result.isFailure) {
                    failed.add(dataValue.dataElement to dataValue.categoryOptionCombo)
                }
            }
            _state.update { it.copy(
                saveInProgress = false,
                saveResult = if (failed.isEmpty()) Result.success(Unit) else Result.failure(Exception("Failed to save some fields"))
            ) }
        }
    }
}