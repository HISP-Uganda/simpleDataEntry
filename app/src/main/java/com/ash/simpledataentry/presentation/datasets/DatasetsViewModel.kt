package com.ash.simpledataentry.presentation.datasets

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.simpledataentry.domain.model.*
import com.ash.simpledataentry.domain.useCase.FilterDatasetsUseCase
import com.ash.simpledataentry.data.sync.BackgroundSyncManager
import com.ash.simpledataentry.data.sync.DetailedSyncProgress
import com.ash.simpledataentry.data.sync.SyncQueueManager
import com.ash.simpledataentry.domain.useCase.GetDatasetsUseCase
import com.ash.simpledataentry.domain.useCase.LogoutUseCase
import com.ash.simpledataentry.domain.useCase.SyncDatasetsUseCase
import com.ash.simpledataentry.util.PeriodHelper
import com.ash.simpledataentry.data.sync.BackgroundDataPrefetcher
import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.data.repositoryImpl.SavedAccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import androidx.work.WorkInfo
import javax.inject.Inject

sealed class DatasetsState {
    data object Loading : DatasetsState()
    data class Error(val message: String) : DatasetsState()
    data class Success(
        val programs: List<ProgramItem>,
        val filteredPrograms: List<ProgramItem> = programs,
        val currentFilter: FilterState = FilterState(),
        val currentProgramType: ProgramType = ProgramType.ALL,
        val isSyncing: Boolean = false,
        val isLoadingRemote: Boolean = false, // Background loading from remote
        val isDownloadingData: Boolean = false, // Separate flag for download-only sync
        val syncMessage: String? = null,
        val syncProgress: Int = 0,
        val syncStep: String? = null,
        val detailedSyncProgress: DetailedSyncProgress? = null // Enhanced sync progress
    ) : DatasetsState()
}
@HiltViewModel
class DatasetsViewModel @Inject constructor(
    private val datasetsRepository: com.ash.simpledataentry.domain.repository.DatasetsRepository,
    private val getDatasetsUseCase: GetDatasetsUseCase,
    private val syncDatasetsUseCase: SyncDatasetsUseCase,
    private val filterDatasetsUseCase: FilterDatasetsUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val backgroundSyncManager: BackgroundSyncManager,
    private val backgroundDataPrefetcher: BackgroundDataPrefetcher,
    private val sessionManager: SessionManager,
    private val savedAccountRepository: SavedAccountRepository,
    private val syncQueueManager: SyncQueueManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<DatasetsState>(DatasetsState.Loading)
    val uiState: StateFlow<DatasetsState> = _uiState.asStateFlow()

    init {
        loadPrograms()
        // Start background prefetching after programs are loaded
        backgroundDataPrefetcher.startPrefetching()

        // Observe sync progress from SyncQueueManager
        viewModelScope.launch {
            syncQueueManager.detailedProgress.collect { progress ->
                val currentState = _uiState.value
                if (currentState is DatasetsState.Success) {
                    _uiState.value = currentState.copy(
                        detailedSyncProgress = progress,
                        isSyncing = progress != null
                    )
                }
            }
        }
    }

    fun refreshPrograms() {
        loadPrograms()
    }

    fun loadPrograms() {
        viewModelScope.launch {
            _uiState.value = DatasetsState.Loading
            var isFirstEmission = true
            datasetsRepository.getAllPrograms()
                .catch { exception ->
                    _uiState.value = DatasetsState.Error(
                        message = exception.message ?: "Failed to load programs"
                    )
                }
                .collect { programs ->
                    val currentState = _uiState.value
                    val wasSyncing = (currentState as? DatasetsState.Success)?.isSyncing == true
                    // CRITICAL FIX: Preserve syncMessage AND download state from previous state
                    val preservedSyncMessage = (currentState as? DatasetsState.Success)?.syncMessage
                    val isDownloading = (currentState as? DatasetsState.Success)?.isDownloadingData == true

                    if (isFirstEmission) {
                        // First emission - could be cached data or fresh data if no cache
                        _uiState.value = DatasetsState.Success(
                            programs = programs,
                            filteredPrograms = programs,
                            isSyncing = false,
                            isLoadingRemote = true, // Show spinner - remote fetch is happening
                            isDownloadingData = isDownloading, // PRESERVE download state during Flow emissions
                            syncMessage = preservedSyncMessage ?: if (wasSyncing) "Sync completed successfully!" else null,
                            syncProgress = 0,
                            syncStep = null
                        )
                        isFirstEmission = false
                    } else {
                        // Second emission - fresh data from remote with updated counts
                        _uiState.value = DatasetsState.Success(
                            programs = programs,
                            filteredPrograms = programs,
                            isSyncing = false,
                            isLoadingRemote = false, // Hide spinner - remote fetch complete
                            isDownloadingData = false, // NOW safe to clear download flag - fresh data loaded
                            syncMessage = preservedSyncMessage, // KEEP message so toast can display
                            syncProgress = 0,
                            syncStep = null
                        )
                    }
                }
        }
    }

    fun syncDatasets(uploadFirst: Boolean = false) {
        val currentState = _uiState.value
        if (currentState is DatasetsState.Success && !currentState.isSyncing) {
            Log.d("DatasetsViewModel", "Starting enhanced sync for datasets, uploadFirst: $uploadFirst")

            viewModelScope.launch {
                try {
                    // Use the enhanced SyncQueueManager which provides detailed progress tracking
                    val syncResult = syncQueueManager.startSync(forceSync = uploadFirst)
                    syncResult.fold(
                        onSuccess = {
                            Log.d("DatasetsViewModel", "Enhanced sync completed successfully")
                            val message = if (uploadFirst) {
                                "Data synchronized successfully"
                            } else {
                                "Datasets synced successfully"
                            }
                            // Set success message BEFORE reloading programs
                            val state = _uiState.value
                            if (state is DatasetsState.Success) {
                                _uiState.value = state.copy(
                                    syncMessage = message,
                                    detailedSyncProgress = null // Clear progress when done
                                )
                            }
                            // Reload all data after sync - this will preserve syncMessage and show updated counts
                            loadPrograms()
                        },
                        onFailure = { error ->
                            Log.e("DatasetsViewModel", "Enhanced sync failed", error)
                            val errorMessage = error.message ?: "Failed to sync datasets"
                            val state = _uiState.value
                            if (state is DatasetsState.Success) {
                                _uiState.value = state.copy(
                                    isLoadingRemote = false,
                                    syncMessage = errorMessage,
                                    detailedSyncProgress = null // Clear progress on failure
                                )
                            }
                        }
                    )
                } catch (e: Exception) {
                    Log.e("DatasetsViewModel", "Enhanced sync failed", e)
                    val state = _uiState.value
                    if (state is DatasetsState.Success) {
                        _uiState.value = state.copy(
                            isSyncing = false,
                            isLoadingRemote = false,
                            syncMessage = e.message ?: "Failed to sync datasets",
                            detailedSyncProgress = null
                        )
                    }
                }
            }
        } else {
            Log.w("DatasetsViewModel", "DATASETS SYNC: Cannot sync - already syncing or state is not Success")
        }
    }

    fun downloadOnlySync() {
        val currentState = _uiState.value
        if (currentState is DatasetsState.Success && !currentState.isSyncing) {
            Log.d("DatasetsViewModel", "Starting download-only sync for datasets")

            // CRITICAL FIX: Set download flag to show spinner during entire download + reload cycle
            _uiState.value = currentState.copy(
                isDownloadingData = true, // This flag persists through loadPrograms() emissions
                syncMessage = null
            )

            viewModelScope.launch {
                try {
                    val syncResult = syncQueueManager.startDownloadOnlySync()
                    syncResult.fold(
                        onSuccess = {
                            Log.d("DatasetsViewModel", "Download-only sync completed successfully")
                            // Small delay to ensure SDK database writes are committed
                            kotlinx.coroutines.delay(100)
                            // Set success message BEFORE reloading programs
                            val state = _uiState.value
                            if (state is DatasetsState.Success) {
                                _uiState.value = state.copy(
                                    syncMessage = "Latest data downloaded successfully",
                                    detailedSyncProgress = null
                                )
                            }
                            // Reload all data after download - this will preserve syncMessage and show updated counts
                            loadPrograms()
                        },
                        onFailure = { error ->
                            Log.e("DatasetsViewModel", "Download-only sync failed", error)
                            val errorMessage = error.message ?: "Failed to download latest data"
                            val state = _uiState.value
                            if (state is DatasetsState.Success) {
                                _uiState.value = state.copy(
                                    isDownloadingData = false, // Clear download flag on failure
                                    syncMessage = errorMessage,
                                    detailedSyncProgress = null
                                )
                            }
                        }
                    )
                } catch (e: Exception) {
                    Log.e("DatasetsViewModel", "Download-only sync failed", e)
                    val state = _uiState.value
                    if (state is DatasetsState.Success) {
                        _uiState.value = state.copy(
                            isSyncing = false,
                            isDownloadingData = false, // Clear download flag on exception
                            syncMessage = e.message ?: "Failed to download datasets",
                            detailedSyncProgress = null
                        )
                    }
                }
            }
        } else {
            Log.w("DatasetsViewModel", "DATASETS DOWNLOAD: Cannot download - already syncing or state is not Success")
        }
    }

    fun applyFilter(filterState: FilterState) {
        val currentState = _uiState.value
        if (currentState is DatasetsState.Success) {
            val filteredPrograms = filterPrograms(currentState.programs, filterState, currentState.currentProgramType)
            _uiState.value = currentState.copy(
                filteredPrograms = filteredPrograms,
                currentFilter = filterState
            )
        }
    }

    fun filterByProgramType(programType: ProgramType) {
        val currentState = _uiState.value
        if (currentState is DatasetsState.Success) {
            val filteredPrograms = filterPrograms(currentState.programs, currentState.currentFilter, programType)
            _uiState.value = currentState.copy(
                filteredPrograms = filteredPrograms,
                currentProgramType = programType
            )
        }
    }

    private fun filterPrograms(programs: List<ProgramItem>, filterState: FilterState, programType: ProgramType): List<ProgramItem> {
        // First filter by program type
        val typeFiltered = if (programType == ProgramType.ALL) {
            programs
        } else {
            programs.filter { it.programType == programType }
        }

        // Apply search filter
        val searchFiltered = if (filterState.searchQuery.isBlank()) {
            typeFiltered
        } else {
            typeFiltered.filter { program ->
                program.name.contains(filterState.searchQuery, ignoreCase = true) ||
                program.description?.contains(filterState.searchQuery, ignoreCase = true) == true
            }
        }

        // Period filter - for datasets only, simplified implementation
        val periodFiltered = if (filterState.periodType == PeriodFilterType.ALL) {
            searchFiltered
        } else {
            // For now, we assume all programs can be filtered by any period
            // In a real implementation, you would check period compatibility
            searchFiltered
        }

        // Sync status filter - simplified implementation
        val syncFiltered = when (filterState.syncStatus) {
            SyncStatus.ALL -> periodFiltered
            // In a real scenario, you would check the sync status of the program instances
            else -> periodFiltered
        }

        // Apply sorting
        return sortPrograms(syncFiltered, filterState.sortBy, filterState.sortOrder)
    }

    private fun sortPrograms(programs: List<ProgramItem>, sortBy: SortBy, sortOrder: SortOrder): List<ProgramItem> {
        val sorted = when (sortBy) {
            SortBy.NAME -> programs.sortedBy { it.name.lowercase() }
            SortBy.CREATED_DATE -> programs.sortedBy { it.id } // Use ID as fallback since no created date field
            SortBy.ENTRY_COUNT -> programs.sortedBy { it.instanceCount }
        }

        return if (sortOrder == SortOrder.DESCENDING) sorted.reversed() else sorted
    }

    fun clearFilters() {
        val currentState = _uiState.value
        if (currentState is DatasetsState.Success) {
            _uiState.value = currentState.copy(
                filteredPrograms = currentState.programs,
                currentFilter = FilterState(),
                currentProgramType = ProgramType.ALL
            )
        }
    }

    fun clearSyncMessage() {
        val currentState = _uiState.value
        if (currentState is DatasetsState.Success) {
            _uiState.value = currentState.copy(syncMessage = null)
        }
    }

    fun dismissSyncOverlay() {
        val currentState = _uiState.value
        if (currentState is DatasetsState.Success) {
            // Clear error state in SyncQueueManager to prevent persistent dialogs
            syncQueueManager.clearErrorState()
            _uiState.value = currentState.copy(
                detailedSyncProgress = null,
                isSyncing = false
            )
        }
    }


    fun logout() {
        viewModelScope.launch {
            try {
                // Stop background prefetching and clear caches
                backgroundDataPrefetcher.clearAllCaches()
                logoutUseCase()

            } catch (e: Exception) {
                _uiState.value = DatasetsState.Error(
                    message = "Failed to logout: ${e.message}"
                )
            }
        }
    }

    fun deleteAccount(context: android.content.Context) {
        viewModelScope.launch {
            try {
                // Stop background prefetching and clear caches
                backgroundDataPrefetcher.clearAllCaches()
                
                // Delete all saved accounts
                savedAccountRepository.deleteAllAccounts()
                
                // Wipe all DHIS2 data and local data
                sessionManager.wipeAllData(context)
                
                // Logout from current session
                logoutUseCase()
                
            } catch (e: Exception) {
                _uiState.value = DatasetsState.Error(
                    message = "Failed to delete account: ${e.message}"
                )
            }
        }
    }

}
