package com.ash.simpledataentry.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.simpledataentry.data.repositoryImpl.SavedAccountRepository
import com.ash.simpledataentry.domain.model.SavedAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountSelectionState(
    val accounts: List<SavedAccount> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AccountSelectionViewModel @Inject constructor(
    private val savedAccountRepository: SavedAccountRepository
) : ViewModel() {
    
    private val _state = MutableStateFlow(AccountSelectionState())
    val state: StateFlow<AccountSelectionState> = _state.asStateFlow()
    
    fun loadAccounts() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            
            try {
                val accounts = savedAccountRepository.getAllSavedAccounts()
                _state.value = _state.value.copy(
                    accounts = accounts,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to load accounts: ${e.message}"
                )
            }
        }
    }
    
    fun selectAccount(account: SavedAccount) {
        viewModelScope.launch {
            try {
                savedAccountRepository.switchToAccount(account.id)
            } catch (e: Exception) {
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
                _state.value = _state.value.copy(
                    error = "Failed to delete account: ${e.message}"
                )
            }
        }
    }
    
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}