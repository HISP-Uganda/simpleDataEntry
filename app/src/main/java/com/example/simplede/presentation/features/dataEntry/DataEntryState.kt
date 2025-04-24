package com.example.simplede.presentation.features.dataEntry


import com.example.simplede.domain.model.DataValue
import com.example.simplede.domain.model.ValidationState

data class DataEntryState(
    val instanceId: String = "",
    val datasetId: String = "",
    val datasetName: String = "",
    val period: String = "",
    val attributeOptionCombo: String = "",
    val isEditMode: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val dataValues: List<DataValue> = emptyList(),
    val currentDataValue: DataValue? = null,
    val currentStep: Int = 0,
    val isCompleted: Boolean = false
)