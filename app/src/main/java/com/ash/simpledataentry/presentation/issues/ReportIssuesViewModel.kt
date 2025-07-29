package com.ash.simpledataentry.presentation.issues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IssueReportState(
    val issueTitle: String = "",
    val issueDescription: String = "",
    val issueType: String = "Bug Report",
    val userEmail: String = "",
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ReportIssuesViewModel @Inject constructor() : ViewModel() {
    
    private val _state = MutableStateFlow(IssueReportState())
    val state: StateFlow<IssueReportState> = _state.asStateFlow()
    
    fun updateIssueTitle(title: String) {
        _state.value = _state.value.copy(issueTitle = title)
    }
    
    fun updateIssueDescription(description: String) {
        _state.value = _state.value.copy(issueDescription = description)
    }
    
    fun updateIssueType(type: String) {
        _state.value = _state.value.copy(issueType = type)
    }
    
    fun updateUserEmail(email: String) {
        _state.value = _state.value.copy(userEmail = email)
    }
    
    fun validateForm(): Boolean {
        val currentState = _state.value
        return currentState.issueTitle.isNotBlank() && 
               currentState.issueDescription.isNotBlank()
    }
    
    fun submitIssue() {
        if (!validateForm()) {
            _state.value = _state.value.copy(
                errorMessage = "Please fill in all required fields"
            )
            return
        }
        
        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, errorMessage = null)
            
            try {
                // In a real implementation, this would send the issue to a backend service
                // For now, we'll just simulate success since we're using email intent
                kotlinx.coroutines.delay(1000) // Simulate network delay
                
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    submitSuccess = true
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    errorMessage = "Failed to submit issue: ${e.message}"
                )
            }
        }
    }
    
    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }
    
    fun resetForm() {
        _state.value = IssueReportState()
    }
    
    fun getEmailSubject(): String {
        val currentState = _state.value
        return "[${currentState.issueType}] ${currentState.issueTitle}"
    }
    
    fun getEmailBody(): String {
        val currentState = _state.value
        return buildString {
            appendLine("Issue Type: ${currentState.issueType}")
            appendLine("Title: ${currentState.issueTitle}")
            appendLine()
            appendLine("Description:")
            appendLine(currentState.issueDescription)
            appendLine()
            appendLine("Contact Email: ${currentState.userEmail.ifEmpty { "Not provided" }}")
            appendLine()
            appendLine("---")
            appendLine("App: DHIS2 Data Entry")
            appendLine("Version: 1.0.0")
            appendLine("Device: Android")
            appendLine("Timestamp: ${System.currentTimeMillis()}")
        }
    }
}
