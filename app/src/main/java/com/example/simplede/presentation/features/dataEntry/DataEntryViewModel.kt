package com.example.simplede.presentation.features.dataEntry


import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simplede.data.repositoryImpl.DataEntryRepositoryImpl
import com.example.simplede.domain.model.DataValue
import com.example.simplede.domain.model.ValidationState
import com.example.simplede.domain.useCases.dataEntry.DataEntryUseCases
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class DataEntryViewModel : ViewModel() {
    private val repository = DataEntryRepositoryImpl()
    private val useCases = DataEntryUseCases(repository)

    private val _state = MutableStateFlow(DataEntryState())
    val state: StateFlow<DataEntryState> = _state.asStateFlow()

    fun initializeNewEntry(datasetId: String, datasetName: String) {
        val newInstanceId = UUID.randomUUID().toString()
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                useCases.getDataValues(newInstanceId).collect { dataValues ->
                    _state.update { it.copy(
                        instanceId = newInstanceId,
                        datasetId = datasetId,
                        datasetName = datasetName,
                        isEditMode = false,
                        dataValues = dataValues,
                        currentDataValue = dataValues.firstOrNull(),
                        currentStep = 0,
                        isLoading = false
                    )}
                }
            } catch (e: Exception) {
                _state.update { it.copy(
                    error = e.message ?: "Failed to initialize new entry",
                    isLoading = false
                )}
            }
        }
    }

    fun loadExistingEntry(instanceId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, instanceId = instanceId, isEditMode = true) }
            try {
                useCases.getDataValues(instanceId).collect { dataValues ->
                    _state.update { it.copy(
                        dataValues = dataValues,
                        currentDataValue = dataValues.firstOrNull(),
                        isLoading = false
                    )}
                }
            } catch (e: Exception) {
                _state.update { it.copy(
                    error = e.message ?: "Failed to load data values",
                    isLoading = false
                )}
            }
        }
    }

    fun updateCurrentValue(value: String) {
        _state.value.currentDataValue?.let { currentValue ->
            _state.update { it.copy(
                currentDataValue = currentValue.copy(
                    value = value,
                    validationState = ValidationState.VALID // This should be validated properly
                )
            )}
        }
    }

    fun updateComment(comment: String) {
        _state.value.currentDataValue?.let { currentValue ->
            _state.update { it.copy(
                currentDataValue = currentValue.copy(
                    comment = comment
                )
            )}
        }
    }

    fun saveCurrentValue() {
        viewModelScope.launch {
            _state.value.currentDataValue?.let { dataValue ->
                _state.update { it.copy(isLoading = true) }
                try {
                    val result = useCases.saveDataValue(
                        _state.value.instanceId,
                        dataValue.dataElement,
                        dataValue.categoryOptionCombo,
                        dataValue.value,
                        dataValue.comment
                    )
                    result.onSuccess { savedValue ->
                        val updatedValues = _state.value.dataValues.toMutableList()
                        val existingIndex = updatedValues.indexOfFirst {
                            it.dataElement == savedValue.dataElement &&
                                    it.categoryOptionCombo == savedValue.categoryOptionCombo
                        }
                        if (existingIndex != -1) {
                            // Update with new value while preserving validation state
                            updatedValues[existingIndex] = savedValue.copy(
                                validationState = ValidationState.VALID,
                                storedBy = "current_user" // This should come from auth/session
                            )
                        } else {
                            // Add new value with initial validation state
                            updatedValues.add(savedValue.copy(
                                validationState = ValidationState.VALID,
                                storedBy = "current_user" // This should come from auth/session
                            ))
                        }
                        _state.update { it.copy(
                            dataValues = updatedValues,
                            isLoading = false
                        )}
                    }.onFailure { error ->
                        _state.update { it.copy(
                            error = error.message ?: "Failed to save data value",
                            isLoading = false
                        )}
                    }
                } catch (e: Exception) {
                    _state.update { it.copy(
                        error = e.message ?: "Failed to save data value",
                        isLoading = false
                    )}
                }
            }
        }
    }

    fun validateCurrentValue() {
        viewModelScope.launch {
            _state.value.currentDataValue?.let { dataValue ->
                dataValue.value?.let { value ->
                    val validationResult = useCases.validateValue(
                        _state.value.instanceId,
                        dataValue.dataElement,
                        dataValue.categoryOptionCombo,
                        value
                    )

                    // Update the validation state of the current value
                    val updatedDataValue = dataValue.copy(
                        validationState = validationResult.state
                    )

                    // Update the validation state in the list
                    val updatedValues = _state.value.dataValues.toMutableList()
                    val existingIndex = updatedValues.indexOfFirst {
                        it.dataElement == dataValue.dataElement &&
                                it.categoryOptionCombo == dataValue.categoryOptionCombo
                    }
                    if (existingIndex != -1) {
                        updatedValues[existingIndex] = updatedDataValue
                    }

                    _state.update { it.copy(
                        dataValues = updatedValues,
                        currentDataValue = updatedDataValue,
                        error = if (!validationResult.isValid) validationResult.message else null
                    )}
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun moveToNextStep(): Boolean {
        val currentStep = _state.value.currentStep
        val totalSteps = _state.value.dataValues.size

        return if (currentStep < totalSteps - 1) {
            _state.update { it.copy(
                currentStep = currentStep + 1,
                currentDataValue = _state.value.dataValues[currentStep + 1]
            )}
            false // Not completed
        } else {
            _state.update { it.copy(
                isEditMode = true,
                isCompleted = true,
                // Set default period to current date in YYYYMM format
                period = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM")),
                // Set default attributeOptionCombo
                attributeOptionCombo = "default"
            )}
            true // Form completed
        }
    }

    fun moveToPreviousStep(): Boolean {
        val currentStep = _state.value.currentStep
        return if (currentStep > 0) {
            _state.update { it.copy(
                currentStep = currentStep - 1,
                currentDataValue = _state.value.dataValues[currentStep - 1]
            )}
            true // Successfully moved back
        } else {
            false // Can't move back
        }
    }
}