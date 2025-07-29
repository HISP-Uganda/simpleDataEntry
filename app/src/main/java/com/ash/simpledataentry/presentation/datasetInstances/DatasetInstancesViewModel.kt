package com.ash.simpledataentry.presentation.datasetInstances

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.simpledataentry.domain.model.CompletionStatus
import com.ash.simpledataentry.domain.model.DatasetInstance
import com.ash.simpledataentry.domain.model.DatasetInstanceFilterState
import com.ash.simpledataentry.domain.model.PeriodFilterType
import com.ash.simpledataentry.domain.model.DatasetInstanceState
import com.ash.simpledataentry.domain.model.SyncStatus
import com.ash.simpledataentry.domain.useCase.GetDatasetInstancesUseCase
import com.ash.simpledataentry.domain.useCase.SyncDatasetInstancesUseCase
import com.ash.simpledataentry.domain.repository.DataEntryRepository
import com.ash.simpledataentry.domain.repository.DatasetInstancesRepository
import com.ash.simpledataentry.util.NetworkUtils
import com.ash.simpledataentry.util.PeriodHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DatasetInstancesState(
    val instances: List<DatasetInstance> = emptyList(),
    val filteredInstances: List<DatasetInstance> = emptyList(),
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val attributeOptionCombos: List<Pair<String, String>> = emptyList()
)

@HiltViewModel
class DatasetInstancesViewModel @Inject constructor(
    private val getDatasetInstancesUseCase: GetDatasetInstancesUseCase,
    private val syncDatasetInstancesUseCase: SyncDatasetInstancesUseCase,
    private val dataEntryRepository: DataEntryRepository,
    private val datasetInstacesRepository: DatasetInstancesRepository,
    private val app: Application
) : ViewModel() {
    private var datasetId: String = ""

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

    fun setDatasetId(id: String) {
        if (id.isNotEmpty() && id != datasetId) {
            datasetId = id
            Log.d("DatasetInstancesVM", "Initializing ViewModel with dataset: $datasetId")
            loadData()
        }
    }

    private fun loadData() {
        if (datasetId.isEmpty()) {
            Log.e("DatasetInstancesVM", "Cannot load data: datasetId is empty")
            return
        }

        Log.d("DatasetInstancesVM", "Loading dataset data for ID: $datasetId")
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                Log.d("DatasetInstancesVM", "Fetching dataset instances")
                val attributeOptionCombos = dataEntryRepository.getAttributeOptionCombos(datasetId)
                val instancesResult = getDatasetInstancesUseCase(datasetId)
                instancesResult.fold(
                    onSuccess = { instances ->
                        Log.d("DatasetInstancesVM", "Received "+instances.size+" instances")
                        _state.value = _state.value.copy(
                            instances = instances,
                            filteredInstances = applyFilters(instances),
                            isLoading = false,
                            error = null,
                            attributeOptionCombos = attributeOptionCombos
                        )
                    },
                    onFailure = { error ->
                        Log.e("DatasetInstancesVM", "Error loading data", error)
                        _state.value = _state.value.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load dataset data",
                            attributeOptionCombos = attributeOptionCombos
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e("DatasetInstancesVM", "Error loading data", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load dataset data"
                )
            }
        }
    }

    fun syncDatasetInstances() {
        if (datasetId.isEmpty()) {
            Log.e("DatasetInstancesVM", "Cannot sync: datasetId is empty")
            return
        }

        Log.d("DatasetInstancesVM", "Starting sync for dataset: $datasetId")
        viewModelScope.launch {
            _state.value = _state.value.copy(isSyncing = true, error = null)
            try {
                if (!NetworkUtils.isNetworkAvailable(app.applicationContext)) {
                    _state.value = _state.value.copy(
                        isSyncing = false,
                        error = "No network connection. Sync will be attempted when online."
                    )
                    return@launch
                }
                // 1. Push all local data (drafts/unsynced) to server
                dataEntryRepository.pushAllLocalData()
                // 2. Pull updates from server and harmonize
                val result = syncDatasetInstancesUseCase()
                result.fold(
                    onSuccess = {
                        loadData() // Reload all data after sync
                        _state.value = _state.value.copy(
                            isSyncing = false,
                            successMessage = "Dataset instances synced successfully"
                        )
                        Log.d("DatasetInstancesVM", "Sync completed successfully")
                    },
                    onFailure = { error ->
                        Log.e("DatasetInstancesVM", "Sync failed", error)
                        _state.value = _state.value.copy(
                            isSyncing = false,
                            error = error.message ?: "Failed to sync dataset instances"
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e("DatasetInstancesVM", "Sync failed", e)
                _state.value = _state.value.copy(
                    isSyncing = false,
                    error = e.message ?: "Failed to sync dataset instances"
                )
            }
        }
    }

    fun manualRefresh() {
        loadData()
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
                val shouldBeComplete = selected.contains(uid) || instance.state == com.ash.simpledataentry.domain.model.DatasetInstanceState.COMPLETE
                val isComplete = instance.state == com.ash.simpledataentry.domain.model.DatasetInstanceState.COMPLETE
                val result: Result<Unit>? = when {
                    shouldBeComplete && !isComplete -> datasetInstacesRepository.completeDatasetInstance(
                        datasetId = instance.datasetId,
                        period = instance.period.id,
                        orgUnit = instance.organisationUnit.id,
                        attributeOptionCombo = instance.attributeOptionCombo
                    )
                    !shouldBeComplete && isComplete -> datasetInstacesRepository.markDatasetInstanceIncomplete(
                        datasetId = instance.datasetId,
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
        if (instance != null) {
            viewModelScope.launch {
                _state.value = _state.value.copy(isLoading = true, error = null)
                val result = datasetInstacesRepository.markDatasetInstanceIncomplete(
                    datasetId = instance.datasetId,
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
        }
    }

    fun bulkMarkInstancesIncomplete(uids: Set<String>, onResult: (Boolean, String?) -> Unit) {
        val allInstances = _state.value.instances.associateBy { it.id }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            var anyError: String? = null
            for (uid in uids) {
                val instance = allInstances[uid]
                if (instance != null) {
                    val result = datasetInstacesRepository.markDatasetInstanceIncomplete(
                        datasetId = instance.datasetId,
                        period = instance.period.id,
                        orgUnit = instance.organisationUnit.id,
                        attributeOptionCombo = instance.attributeOptionCombo
                    )
                    if (result.isFailure) {
                        anyError = result.exceptionOrNull()?.message ?: "Unknown error"
                        break
                    }
                }
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
        if (instance != null) {
            viewModelScope.launch {
                _state.value = _state.value.copy(isLoading = true, error = null)
                val result = datasetInstacesRepository.completeDatasetInstance(
                    datasetId = instance.datasetId,
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
        }
    }

    // Filter methods
    fun updateFilterState(newFilterState: DatasetInstanceFilterState) {
        _filterState.value = newFilterState
        val currentInstances = _state.value.instances
        _state.value = _state.value.copy(
            filteredInstances = applyFilters(currentInstances)
        )
    }

    private fun applyFilters(instances: List<DatasetInstance>): List<DatasetInstance> {
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
            val periodMatches = if (filter.periodType == PeriodFilterType.ALL) true else instance.period.id in periodIds
            val syncStatusMatches = when (filter.syncStatus) {
                SyncStatus.ALL -> true
                SyncStatus.SYNCED -> instance.state == DatasetInstanceState.COMPLETE // Assuming COMPLETE is a synced state
                SyncStatus.NOT_SYNCED -> instance.state == DatasetInstanceState.OPEN // Assuming OPEN is a not synced state
                //SyncStatus.PENDING -> false // No pending state in the domain model
            }
            val completionMatches = when (filter.completionStatus) {
                CompletionStatus.ALL -> true
                CompletionStatus.COMPLETE -> instance.state == DatasetInstanceState.COMPLETE
                CompletionStatus.INCOMPLETE -> instance.state == DatasetInstanceState.OPEN
            }
            periodMatches && syncStatusMatches && completionMatches
        }
    }

    fun clearFilters() {
        _filterState.value = DatasetInstanceFilterState()
        val currentInstances = _state.value.instances
        _state.value = _state.value.copy(
            filteredInstances = currentInstances
        )
    }
}
