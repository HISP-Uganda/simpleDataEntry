package com.ash.simpledataentry.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.simpledataentry.data.repositoryImpl.SavedAccountRepository
import com.ash.simpledataentry.domain.model.SavedAccount
import com.ash.simpledataentry.presentation.core.UiState
import com.ash.simpledataentry.presentation.core.UiError
import com.ash.simpledataentry.presentation.core.LoadingOperation
import com.ash.simpledataentry.util.toUiError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Account selection data model
 */
data class AccountSelectionData(
    val accounts: List<SavedAccount> = emptyList()
)

/**
 * DEPRECATED: Old state model for backward compatibility
 * Use UiState<AccountSelectionData> instead
 */
@Deprecated("Use UiState<AccountSelectionData> instead")
data class AccountSelectionState(
    val accounts: List<SavedAccount> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AccountSelectionViewModel @Inject constructor(
    private val savedAccountRepository: SavedAccountRepository
) : ViewModel() {

    // New UiState pattern
    private val _uiState = MutableStateFlow<UiState<AccountSelectionData>>(
        UiState.Success(AccountSelectionData())
    )
    val uiState: StateFlow<UiState<AccountSelectionData>> = _uiState.asStateFlow()

    // Deprecated state for backward compatibility
    @Deprecated("Use uiState instead")
    private val _state = MutableStateFlow(AccountSelectionState())
    @Deprecated("Use uiState instead")
    val state: StateFlow<AccountSelectionState> = _state.asStateFlow()

    fun loadAccounts() {
        viewModelScope.launch {
            // Update both states for backward compatibility
            _state.value = _state.value.copy(isLoading = true, error = null)
            _uiState.value = UiState.Loading(LoadingOperation.Initial)

            try {
                val accounts = savedAccountRepository.getAllSavedAccounts()
                val newData = AccountSelectionData(accounts = accounts)

                // Update new state
                _uiState.value = UiState.Success(newData)

                // Update old state for backward compatibility
                _state.value = _state.value.copy(
                    accounts = accounts,
                    isLoading = false
                )
            } catch (e: Exception) {
                val uiError = e.toUiError()

                // Update new state
                _uiState.value = UiState.Error(uiError, getCurrentData())

                // Update old state for backward compatibility
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = when (uiError) {
                        is UiError.Network -> uiError.message
                        is UiError.Server -> uiError.message
                        is UiError.Local -> uiError.message
                        else -> "Failed to load accounts"
                    }
                )
            }
        }
    }

    fun selectAccount(account: SavedAccount) {
        viewModelScope.launch {
            try {
                savedAccountRepository.switchToAccount(account.id)
            } catch (e: Exception) {
                val uiError = e.toUiError()
                _uiState.value = UiState.Error(uiError, getCurrentData())
                _state.value = _state.value.copy(
                    error = "Failed to switch account: ${e.message}"
                )
            }
        }
    }

    fun deleteAccount(accountId: String) {
        viewModelScope.launch {
            try {
                savedAccountRepository.deleteAccount(accountId)
                // Reload accounts after deletion
                loadAccounts()
            } catch (e: Exception) {
                val uiError = e.toUiError()
                _uiState.value = UiState.Error(uiError, getCurrentData())
                _state.value = _state.value.copy(
                    error = "Failed to delete account: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        // Convert error state back to success with previous data
        val current = _uiState.value
        if (current is UiState.Error) {
            _uiState.value = UiState.Success(current.previousData ?: AccountSelectionData())
        }
        _state.value = _state.value.copy(error = null)
    }

    /**
     * Helper to get current data from UiState
     */
    private fun getCurrentData(): AccountSelectionData {
        return when (val current = _uiState.value) {
            is UiState.Success -> current.data
            is UiState.Error -> current.previousData ?: AccountSelectionData()
            is UiState.Loading -> AccountSelectionData()
        }
    }
}