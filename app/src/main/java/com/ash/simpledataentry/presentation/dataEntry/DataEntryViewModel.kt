package com.ash.simpledataentry.presentation.dataEntry

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.simpledataentry.domain.model.*
import com.ash.simpledataentry.domain.repository.DataEntryRepository
import com.ash.simpledataentry.domain.useCase.DataEntryUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    val expandedSections: Set<String> = emptySet(),
    val expandedCategoryGroups: Set<String> = emptySet()
)

@HiltViewModel
class DataEntryViewModel @Inject constructor(
    private val repository: DataEntryRepository,
    private val useCases: DataEntryUseCases
) : ViewModel() {
    private val _state = MutableStateFlow(DataEntryState())
    val state: StateFlow<DataEntryState> = _state.asStateFlow()

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
                // Get attribute option combo name
                val attributeOptionCombos = repository.getAttributeOptionCombos(datasetId)
                val attributeOptionComboName = attributeOptionCombos.find { it.first == attributeOptionCombo }?.second ?: attributeOptionCombo

                _state.update { currentState ->
                    currentState.copy(
                        isLoading = true,
                        error = null,
                        datasetId = datasetId,
                        datasetName = datasetName,
                        period = period,
                        orgUnit = orgUnitId,
                        attributeOptionCombo = attributeOptionCombo,
                        attributeOptionComboName = attributeOptionComboName,
                        isEditMode = isEditMode
                    )
                }

                repository.getDataValues(datasetId, period, orgUnitId, attributeOptionCombo)
                    .collect { values ->
                        _state.update { currentState ->
                            currentState.copy(
                                dataValues = values,
                                currentDataValue = values.firstOrNull(),
                                currentStep = 0,
                                isLoading = false,
                                expandedSections = emptySet()
                            )
                        }
                    }
            } catch (e: Exception) {
                _state.update { currentState ->
                    currentState.copy(
                        error = "Failed to load data values: ${e.message}",
                        isLoading = false
                    )
                }
            }
        }
    }

    fun updateCurrentValue(value: String) {
        _state.value.currentDataValue?.let { dataValue ->
            _state.update { currentState ->
                currentState.copy(
                    currentDataValue = dataValue.copy(value = value),
                    dataValues = currentState.dataValues.map {
                        if (it.dataElement == dataValue.dataElement && 
                            it.categoryOptionCombo == dataValue.categoryOptionCombo) {
                            it.copy(value = value)
                        } else {
                            it
                        }
                    }
                )
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

    fun saveCurrentValue() {
        viewModelScope.launch {
            _state.value.currentDataValue?.let { dataValue ->
                try {
                    _state.update { it.copy(isLoading = true) }
                    
                    val result = useCases.saveDataValue(
                        datasetId = _state.value.datasetId,
                        period = _state.value.period,
                        orgUnit = _state.value.orgUnit,
                        attributeOptionCombo = _state.value.attributeOptionCombo,
                        dataElement = dataValue.dataElement,
                        categoryOptionCombo = dataValue.categoryOptionCombo,
                        value = dataValue.value,
                        comment = dataValue.comment
                    )

                    result.fold(
                        onSuccess = { savedValue ->
                            _state.update { currentState ->
                                currentState.copy(
                                    dataValues = currentState.dataValues.map {
                                        if (it.dataElement == savedValue.dataElement && 
                                            it.categoryOptionCombo == savedValue.categoryOptionCombo) {
                                            savedValue
                                        } else {
                                            it
                                        }
                                    },
                                    currentDataValue = savedValue,
                                    isLoading = false
                                )
                            }
                        },
                        onFailure = { error ->
                            _state.update { currentState ->
                                currentState.copy(
                                    error = "Failed to save value: ${error.message}",
                                    isLoading = false
                                )
                            }
                        }
                    )
                } catch (e: Exception) {
                    _state.update { currentState ->
                        currentState.copy(
                            error = "Failed to save value: ${e.message}",
                            isLoading = false
                        )
                    }
                }
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
            val newExpandedSections = currentState.expandedSections.toMutableSet()
            if (sectionName in newExpandedSections) {
                newExpandedSections.remove(sectionName)
            } else {
                newExpandedSections.add(sectionName)
            }
            currentState.copy(expandedSections = newExpandedSections)
        }
    }

    fun toggleCategoryGroup(sectionName: String, categoryGroup: String) {
        _state.update { currentState ->
            val key = "$sectionName:$categoryGroup"
            val newExpandedGroups = currentState.expandedCategoryGroups.toMutableSet()
            if (key in newExpandedGroups) {
                newExpandedGroups.remove(key)
            } else {
                newExpandedGroups.add(key)
            }
            currentState.copy(expandedCategoryGroups = newExpandedGroups)
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
}