package com.ash.simpledataentry.presentation.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ash.simpledataentry.domain.model.SavedAccount
import com.ash.simpledataentry.presentation.core.BaseScreen
import com.ash.simpledataentry.presentation.core.LoadingOperation
import com.ash.simpledataentry.presentation.core.NavigationProgress
import com.ash.simpledataentry.presentation.core.StepLoadingScreen
import com.ash.simpledataentry.presentation.core.StepLoadingType
import com.ash.simpledataentry.presentation.core.UiState

@Composable
fun ManageAccountsLoginScreen(
    navController: NavController,
    accountViewModel: AccountSelectionViewModel = hiltViewModel(),
    loginViewModel: LoginViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val accountState by accountViewModel.uiState.collectAsState()
    val loginState by loginViewModel.uiState.collectAsState()

    var selectedAccount by remember { mutableStateOf<SavedAccount?>(null) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val accounts = when (val state = accountState) {
        is UiState.Success -> state.data.accounts
        is UiState.Error -> state.previousData?.accounts ?: emptyList()
        is UiState.Loading -> emptyList()
    }
    val isLoginLoading = loginState is UiState.Loading
    val loadingProgress = (loginState as? UiState.Loading)?.operation?.let { op ->
        (op as? LoadingOperation.Navigation)?.progress
    }
    val loginErrorMessage = (loginState as? UiState.Error)?.error?.let { error ->
        when (error) {
            is com.ash.simpledataentry.presentation.core.UiError.Authentication -> error.message
            is com.ash.simpledataentry.presentation.core.UiError.Local -> error.message
            is com.ash.simpledataentry.presentation.core.UiError.Network -> error.message
            is com.ash.simpledataentry.presentation.core.UiError.Server -> error.message
            is com.ash.simpledataentry.presentation.core.UiError.Validation -> error.message
        }
    }

    LaunchedEffect(Unit) {
        accountViewModel.loadAccounts()
    }

    val isLoggedIn = (loginState as? UiState.Success)?.data?.isLoggedIn == true
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            navController.navigate("datasets") {
                popUpTo("login") { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    BaseScreen(
        title = "Manage Accounts",
        subtitle = "Select an account to sign in",
        navController = navController
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items = accounts, key = { it.id }) { account ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isLoginLoading) {
                            selectedAccount = account
                            password = ""
                            passwordVisible = false
                            loginViewModel.clearError()
                        },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = null,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = account.displayName, fontWeight = FontWeight.SemiBold)
                            Text(text = account.username)
                            Text(text = account.serverUrl)
                        }
                    }
                }
            }
        }
    }

    selectedAccount?.takeIf { !isLoginLoading }?.let { account ->
        AlertDialog(
            onDismissRequest = { selectedAccount = null },
            title = { Text("Enter password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Sign in as ${account.username}")
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            if (loginErrorMessage != null) loginViewModel.clearError()
                        },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        label = { Text("Password") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!isLoginLoading && !loginErrorMessage.isNullOrBlank()) {
                        Text(
                            text = loginErrorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = password.isNotBlank() && !isLoginLoading,
                    onClick = {
                        loginViewModel.loginWithBackgroundBootstrap(
                            serverUrl = account.serverUrl,
                            username = account.username,
                            password = password,
                            context = context
                        )
                    }
                ) {
                    Text("Sign in")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedAccount = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (isLoginLoading) {
        val progress = loadingProgress ?: NavigationProgress.initial()
        val stepIndex = when (progress.phase) {
            com.ash.simpledataentry.presentation.core.LoadingPhase.INITIALIZING -> 0
            com.ash.simpledataentry.presentation.core.LoadingPhase.AUTHENTICATING -> 1
            com.ash.simpledataentry.presentation.core.LoadingPhase.DOWNLOADING_METADATA -> 2
            com.ash.simpledataentry.presentation.core.LoadingPhase.LOADING_DATA -> 3
            com.ash.simpledataentry.presentation.core.LoadingPhase.PROCESSING,
            com.ash.simpledataentry.presentation.core.LoadingPhase.PROCESSING_DATA -> 4
            com.ash.simpledataentry.presentation.core.LoadingPhase.COMPLETING,
            com.ash.simpledataentry.presentation.core.LoadingPhase.FINALIZING -> 5
        }
        val percent = progress.overallPercentage.takeIf { it in 1..100 }
            ?: progress.percentage.takeIf { it in 1..100 }
            ?: 0
        val label = when {
            progress.phaseDetail.isNotBlank() -> progress.phaseDetail
            progress.phaseTitle.isNotBlank() -> progress.phaseTitle
            else -> "Signing in..."
        }

        StepLoadingScreen(
            type = StepLoadingType.LOGIN,
            currentStep = stepIndex,
            progressPercent = percent,
            currentLabel = label,
            actionLabel = "Cancel",
            onAction = { loginViewModel.abortLogin(context, "Login cancelled. Please try again.") },
            modifier = Modifier.fillMaxSize()
        )
    }
}
