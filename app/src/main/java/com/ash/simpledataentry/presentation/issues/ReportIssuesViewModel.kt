package com.ash.simpledataentry.presentation.issues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.simpledataentry.presentation.core.UiState
import com.ash.simpledataentry.presentation.core.LoadingOperation
import com.ash.simpledataentry.util.toUiError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Pure data model (no UI state like isSubmitting)
data class ReportIssuesData(
    val issueTitle: String = "",
    val issueDescription: String = "",
    val issueType: String = "Bug Report",
    val userEmail: String = "",
    val submitSuccess: Boolean = false
)

@HiltViewModel
class ReportIssuesViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<ReportIssuesData>>(
        UiState.Success(ReportIssuesData())
    )
    val uiState: StateFlow<UiState<ReportIssuesData>> = _uiState.asStateFlow()

    private fun getCurrentData(): ReportIssuesData {
        return when (val current = _uiState.value) {
            is UiState.Success -> current.data
            is UiState.Error -> current.previousData ?: ReportIssuesData()
            is UiState.Loading -> ReportIssuesData()
        }
    }

    fun updateIssueTitle(title: String) {
        val currentData = getCurrentData()
        _uiState.value = UiState.Success(currentData.copy(issueTitle = title))
    }

    fun updateIssueDescription(description: String) {
        val currentData = getCurrentData()
        _uiState.value = UiState.Success(currentData.copy(issueDescription = description))
    }

    fun updateIssueType(type: String) {
        val currentData = getCurrentData()
        _uiState.value = UiState.Success(currentData.copy(issueType = type))
    }

    fun updateUserEmail(email: String) {
        val currentData = getCurrentData()
        _uiState.value = UiState.Success(currentData.copy(userEmail = email))
    }

    fun validateForm(): Boolean {
        val currentData = getCurrentData()
        return currentData.issueTitle.isNotBlank() &&
               currentData.issueDescription.isNotBlank()
    }

    fun submitIssue() {
        if (!validateForm()) {
            val validationError = Exception("Please fill in all required fields")
            _uiState.value = UiState.Error(validationError.toUiError(), getCurrentData())
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = UiState.Loading(LoadingOperation.Initial)

                // In a real implementation, this would send the issue to a backend service
                // For now, we'll just simulate success since we're using email intent
                kotlinx.coroutines.delay(1000) // Simulate network delay

                val successData = getCurrentData().copy(submitSuccess = true)
                _uiState.value = UiState.Success(successData)
            } catch (e: Exception) {
                val uiError = e.toUiError()
                _uiState.value = UiState.Error(uiError, getCurrentData())
            }
        }
    }

    fun clearError() {
        // If in error state, return to success with current data
        if (_uiState.value is UiState.Error) {
            _uiState.value = UiState.Success(getCurrentData())
        }
    }

    fun resetForm() {
        _uiState.value = UiState.Success(ReportIssuesData())
    }

    fun getEmailSubject(): String {
        val currentData = getCurrentData()
        return "[${currentData.issueType}] ${currentData.issueTitle}"
    }

    fun getEmailBody(): String {
        val currentData = getCurrentData()
        return buildString {
            appendLine("Issue Type: ${currentData.issueType}")
            appendLine("Title: ${currentData.issueTitle}")
            appendLine()
            appendLine("Description:")
            appendLine(currentData.issueDescription)
            appendLine()
            appendLine("Contact Email: ${currentData.userEmail.ifEmpty { "Not provided" }}")
            appendLine()
            appendLine("---")
            appendLine("App: DHIS2 Data Entry")
            appendLine("Version: 1.0.0")
            appendLine("Device: Android")
            appendLine("Timestamp: ${System.currentTimeMillis()}")
        }
    }
}
