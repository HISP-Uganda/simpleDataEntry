package com.ash.simpledataentry.presentation.login

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.width
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ash.simpledataentry.R
import com.ash.simpledataentry.navigation.Screen
import com.ash.simpledataentry.presentation.core.AdaptiveLoadingOverlay
import com.ash.simpledataentry.presentation.core.LoadingOperation
import com.ash.simpledataentry.presentation.core.LoadingPhase
import com.ash.simpledataentry.presentation.core.NavigationProgress
import com.ash.simpledataentry.presentation.core.StepLoadingScreen
import com.ash.simpledataentry.presentation.core.StepLoadingType
import com.ash.simpledataentry.presentation.core.UiState
import com.ash.simpledataentry.ui.theme.DHIS2Blue
import com.ash.simpledataentry.ui.theme.DHIS2BlueDark
import com.ash.simpledataentry.ui.theme.DHIS2BlueLight
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

private data class StepLoadingInfo(
    val type: StepLoadingType,
    val stepIndex: Int,
    val percent: Int,
    val label: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: LoginViewModel = hiltViewModel(),
    isAddAccount: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current
    val isNetworkAvailable = rememberNetworkAvailable()
    val isDarkTheme = isSystemInDarkTheme()
    val statusBarColor = if (isDarkTheme) Color.Black else Color.White
    val useDarkIcons = !isDarkTheme

    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        window.statusBarColor = statusBarColor.toArgb()
        window.navigationBarColor = statusBarColor.toArgb()
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = useDarkIcons
            isAppearanceLightNavigationBars = useDarkIcons
        }
    }

    // Extract data from UiState
    val loginData = when (val state = uiState) {
        is UiState.Success -> state.data
        is UiState.Error -> state.previousData ?: LoginData()
        is UiState.Loading -> LoginData()
    }
    val isLoading = uiState is UiState.Loading
    val errorMessage = (uiState as? UiState.Error)?.error?.let { error ->
        when (error) {
            is com.ash.simpledataentry.presentation.core.UiError.Network -> error.message
            is com.ash.simpledataentry.presentation.core.UiError.Server -> error.message
            is com.ash.simpledataentry.presentation.core.UiError.Validation -> error.message
            is com.ash.simpledataentry.presentation.core.UiError.Authentication -> error.message
            is com.ash.simpledataentry.presentation.core.UiError.Local -> error.message
        }
    }

    // Extract navigation progress from loading state
    val navigationProgress = (uiState as? UiState.Loading)?.operation?.let { op ->
        (op as? LoadingOperation.Navigation)?.progress
    }
    var lastNavigationProgress by remember { mutableStateOf<NavigationProgress?>(null) }
    if (navigationProgress != null) {
        lastNavigationProgress = navigationProgress
    }
    val activeNavigationProgress = navigationProgress ?: lastNavigationProgress
    val isInitialLoading = (uiState as? UiState.Loading)?.operation is LoadingOperation.Initial
    val overlayUiState: UiState<LoginData> = if (loginData.showSplash && uiState !is UiState.Loading) {
        val fallbackProgress = activeNavigationProgress ?: NavigationProgress(
            phaseTitle = "Finishing login",
            phaseDetail = "Preparing your workspace...",
            percentage = 90,
            overallPercentage = 90
        )
        UiState.Loading(operation = LoadingOperation.Navigation(fallbackProgress))
    } else {
        uiState
    }
    val showInitialSplash = loginData.showSplash && isInitialLoading
    val stepLoadingInfo = (overlayUiState as? UiState.Loading)?.operation?.let { operation ->
        if (operation is LoadingOperation.Navigation) {
            val phase = operation.progress.phase
            val stepIndex = when (phase) {
                LoadingPhase.INITIALIZING -> 0
                LoadingPhase.AUTHENTICATING -> 1
                LoadingPhase.DOWNLOADING_METADATA -> 2
                LoadingPhase.LOADING_DATA -> 3
                LoadingPhase.PROCESSING,
                LoadingPhase.PROCESSING_DATA -> 4
                LoadingPhase.COMPLETING,
                LoadingPhase.FINALIZING -> 5
            }
            val fallbackPercent = when (stepIndex) {
                0 -> 10
                1 -> 25
                2 -> 60
                3 -> 80
                4 -> 90
                else -> 100
            }
            val percent = operation.progress.overallPercentage
                .takeIf { it in 1..100 }
                ?: operation.progress.percentage.takeIf { it in 1..100 }
                ?: fallbackPercent
            val label = when {
                operation.progress.phaseDetail.isNotBlank() -> operation.progress.phaseDetail
                operation.progress.phaseTitle.isNotBlank() -> operation.progress.phaseTitle
                operation.progress.message.isNotBlank() -> operation.progress.message
                else -> ""
            }
            StepLoadingInfo(StepLoadingType.LOGIN, stepIndex, percent, label)
        } else {
            null
        }
    }

    var lastProgressPercent by remember { mutableStateOf(0) }
    var lastProgressTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(stepLoadingInfo?.percent) {
        val percent = stepLoadingInfo?.percent ?: return@LaunchedEffect
        if (percent != lastProgressPercent) {
            lastProgressPercent = percent
            lastProgressTimestamp = System.currentTimeMillis()
        }
    }
    val isStalled = stepLoadingInfo != null && (System.currentTimeMillis() - lastProgressTimestamp) > 45000

    LaunchedEffect(loginData.isLoggedIn, loginData.saveAccountOffered, isAddAccount) {
        if (loginData.isLoggedIn && !loginData.saveAccountOffered) {
            if (isAddAccount) {
                navController.navigate(com.ash.simpledataentry.navigation.Screen.EditAccountScreen.route) {
                    popUpTo("login") { inclusive = true }
                    launchSingleTop = true
                }
            } else {
                navController.navigate("datasets") {
                    popUpTo("login") { inclusive = true }
                }
            }
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { error ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(error)
                viewModel.clearError()
            }
        }
    }

    // Show step-based loading from the initial splash onward
    if (showInitialSplash || stepLoadingInfo != null) {
        val currentStepInfo = stepLoadingInfo
        StepLoadingScreen(
            type = StepLoadingType.LOGIN,
            currentStep = currentStepInfo?.stepIndex ?: 0,
            progressPercent = currentStepInfo?.percent ?: 0,
            currentLabel = when {
                isStalled -> "Taking longer than usual. Check your connection or server."
                else -> currentStepInfo?.label ?: "Initializing..."
            },
            actionLabel = if (isStalled) "Retry" else "Cancel",
            onAction = { viewModel.abortLogin(context, "Login cancelled. Please try again.") },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        AdaptiveLoadingOverlay(
            uiState = overlayUiState,
            modifier = Modifier.fillMaxSize()
        ) {
            // Local state for input fields, preserved across configuration changes
            var serverUrl by rememberSaveable { mutableStateOf("https://") }
            var username by rememberSaveable { mutableStateOf("") }
            var password by rememberSaveable { mutableStateOf("") }
            var showUrlDropdown by remember { mutableStateOf(false) }
            var passwordVisible by remember { mutableStateOf(false) }

            var isAddingNew by rememberSaveable { mutableStateOf(loginData.savedAccounts.isEmpty()) }

            LaunchedEffect(loginData.savedAccounts) {
                if (loginData.savedAccounts.isEmpty()) {
                    isAddingNew = true
                } else {
                    isAddingNew = false
                }
            }

            val selectedProfile = loginData.savedAccounts.firstOrNull { it.isActive }
                ?: loginData.savedAccounts.firstOrNull()
            val hasSavedProfiles = loginData.savedAccounts.isNotEmpty()
            val showFullForm = !hasSavedProfiles || isAddingNew
            val useSavedLogin = hasSavedProfiles && !isAddingNew && selectedProfile != null
            val scrollState = rememberScrollState()
            val usernameBringIntoViewRequester = remember { BringIntoViewRequester() }
            val passwordBringIntoViewRequester = remember { BringIntoViewRequester() }
            val loginButtonBringIntoViewRequester = remember { BringIntoViewRequester() }
            val passwordFocusRequester = remember { FocusRequester() }

            var usernameFocused by remember { mutableStateOf(false) }
            var passwordFocused by remember { mutableStateOf(false) }

            // Auto-scroll to bring login button into view when username field gets focus
            LaunchedEffect(usernameFocused) {
                if (usernameFocused) {
                    usernameBringIntoViewRequester.bringIntoView()
                    kotlinx.coroutines.delay(300)
                    loginButtonBringIntoViewRequester.bringIntoView()
                }
            }

            // Auto-scroll to bring login button into view when password field gets focus
            LaunchedEffect(passwordFocused) {
                if (passwordFocused) {
                    passwordBringIntoViewRequester.bringIntoView()
                    kotlinx.coroutines.delay(300)
                    loginButtonBringIntoViewRequester.bringIntoView()
                }
            }

            LaunchedEffect(selectedProfile?.id, isAddingNew) {
                if (!isAddingNew) {
                    selectedProfile?.let { profile ->
                        if (serverUrl.isBlank() || serverUrl == "https://") {
                            serverUrl = profile.serverUrl
                        }
                        if (username.isBlank()) {
                            username = profile.username
                        }
                    }
                }
            }

            val gradientBrush = Brush.verticalGradient(
                colors = listOf(DHIS2Blue, DHIS2BlueDark)
            )
            val mistOverlay = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.08f),
                    Color.Transparent
                )
            )

            fun resetForm() {
                serverUrl = "https://"
                username = ""
                password = ""
                passwordVisible = false
                viewModel.clearUrlSuggestions()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(gradientBrush)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(mistOverlay)
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = Color.White.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = Color.White
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.login_logo),
                                    contentDescription = "App Logo",
                                    modifier = Modifier
                                        .height(72.dp)
                                        .padding(10.dp),
                                    tint = Color.Unspecified
                                )
                            }
                            Text(
                                text = "Simple Data Entry",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                            Text(
                                text = "Fast, reliable DHIS2 data capture",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDarkTheme) {
                                MaterialTheme.colorScheme.surface
                            } else {
                                Color.White
                            }
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (useSavedLogin && selectedProfile != null) {
                                Text(
                                    text = "Saved account found",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Use Manage accounts to enter password and sign in.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = "Sign in",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Use your DHIS2 server credentials to continue.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            var serverUrlFieldSize by remember { mutableStateOf(IntSize.Zero) }
                            val density = LocalDensity.current

                            if (showFullForm) {
                                Box {
                                    OutlinedTextField(
                                        value = serverUrl,
                                        onValueChange = {
                                            serverUrl = it
                                            viewModel.clearUrlSuggestions()
                                        },
                                        label = { Text("Server URL (e.g., play.dhis2.org)") },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onGloballyPositioned { coordinates ->
                                                serverUrlFieldSize = coordinates.size
                                            }
                                            .onFocusChanged { focusState ->
                                                if (!focusState.isFocused) {
                                                    viewModel.clearUrlSuggestions()
                                                }
                                            },
                                        enabled = !isLoading,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Link,
                                                contentDescription = "Server URL",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        trailingIcon = {
                                            if (loginData.cachedUrls.isNotEmpty()) {
                                                IconButton(
                                                    onClick = {
                                                        showUrlDropdown = !showUrlDropdown
                                                        if (showUrlDropdown) {
                                                            viewModel.clearUrlSuggestions()
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowDropDown,
                                                        contentDescription = "Show cached URLs"
                                                    )
                                                }
                                            }
                                        }
                                    )

                                    DropdownMenu(
                                        expanded = showUrlDropdown,
                                        onDismissRequest = { showUrlDropdown = false },
                                        modifier = Modifier.width(
                                            with(density) { serverUrlFieldSize.width.toDp() }
                                        )
                                    ) {
                                        loginData.cachedUrls.take(5).forEach { cachedUrl ->
                                            val isSelected = cachedUrl.url == serverUrl
                                            DropdownMenuItem(
                                                text = {
                                                    Surface(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 4.dp),
                                                        shape = RoundedCornerShape(16.dp),
                                                        color = if (isSelected) {
                                                            DHIS2BlueLight
                                                        } else {
                                                            MaterialTheme.colorScheme.surface
                                                        },
                                                        border = BorderStroke(
                                                            width = if (isSelected) 2.dp else 1.dp,
                                                            color = if (isSelected) DHIS2Blue else MaterialTheme.colorScheme.outline
                                                        ),
                                                        tonalElevation = if (isSelected) 4.dp else 0.dp
                                                    ) {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(36.dp)
                                                                    .background(
                                                                        color = DHIS2Blue.copy(alpha = 0.2f),
                                                                        shape = CircleShape
                                                                    ),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Link,
                                                                    contentDescription = null,
                                                                    tint = DHIS2Blue,
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                            }

                                                            Column(
                                                                modifier = Modifier.weight(1f),
                                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                                            ) {
                                                                Text(
                                                                    text = cachedUrl.url,
                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                    maxLines = 1
                                                                )
                                                                Text(
                                                                    text = "Tap to use this server",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }

                                                            IconButton(
                                                                onClick = {
                                                                    viewModel.removeUrl(cachedUrl.url)
                                                                    showUrlDropdown = false
                                                                }
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Clear,
                                                                    contentDescription = "Remove URL",
                                                                    tint = MaterialTheme.colorScheme.error
                                                                )
                                                            }
                                                        }
                                                    }
                                                },
                                                onClick = {
                                                    serverUrl = cachedUrl.url
                                                    showUrlDropdown = false
                                                    viewModel.clearUrlSuggestions()
                                                },
                                                leadingIcon = null,
                                                trailingIcon = null,
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = username,
                                    onValueChange = { username = it },
                                    label = { Text("Username") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .bringIntoViewRequester(usernameBringIntoViewRequester)
                                        .onFocusChanged { focusState ->
                                            usernameFocused = focusState.isFocused
                                        },
                                    enabled = !isLoading,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = "Username",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                )
                            } else {
                                Text(
                                    text = "Select a saved connection to continue.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (!useSavedLogin && showFullForm) {
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Password") },
                                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(passwordFocusRequester)
                                        .bringIntoViewRequester(passwordBringIntoViewRequester)
                                        .onFocusChanged { focusState ->
                                            passwordFocused = focusState.isFocused
                                        },
                                    enabled = !isLoading,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Password",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { passwordVisible = !passwordVisible }
                                        ) {
                                            Icon(
                                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                            )
                                        }
                                    }
                                )
                            }

                            if (!useSavedLogin && !isNetworkAvailable) {
                                Surface(
                                    color = Color(0xFFFFE8CC),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Cloud,
                                            contentDescription = null,
                                            tint = Color(0xFFB25E09)
                                        )
                                        Column {
                                            Text(
                                                text = "No internet connection",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color(0xFF8A4B08)
                                            )
                                            Text(
                                                text = "Online sign-in needs internet. Connect and try again, or use a saved account if available.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF8A4B08).copy(alpha = 0.9f)
                                            )
                                        }
                                    }
                                }
                            }

                            if (!useSavedLogin) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            resetForm()
                                            if (hasSavedProfiles) {
                                                isAddingNew = false
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Text("Cancel")
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.loginWithBackgroundBootstrap(serverUrl, username, password, context)
                                        },
                                        enabled = !isLoading &&
                                                serverUrl.isNotBlank() &&
                                                username.isNotBlank() &&
                                                password.isNotBlank(),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .bringIntoViewRequester(loginButtonBringIntoViewRequester),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Text(
                                            text = "Login",
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }

                            if (useSavedLogin) {
                                Button(
                                    onClick = { navController.navigate(Screen.ManageAccountsLoginScreen.route) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text(
                                        text = "Manage accounts",
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        isAddingNew = true
                                        password = ""
                                    },
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) {
                                    Text("Use different account")
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 64.dp)
                ) {
                    SnackbarHost(
                        hostState = snackbarHostState,
                        snackbar = { data ->
                            Snackbar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = Color.White
                            ) {
                                Text(
                                    data.visuals.message,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    )
                }

                if (hasSavedProfiles) {
                    // Managed inside the saved-account card to avoid duplicate entry points.
                }
            }
        }
    }

    // Save Account Dialog (moved outside if/else)
    if (loginData.saveAccountOffered && loginData.pendingCredentials != null) {
        android.util.Log.d("LoginDebug", "About to show save account dialog")

        val pending = loginData.pendingCredentials
        var displayName by remember(pending) { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = {
                android.util.Log.d("LoginDebug", "Dialog dismissed")
                viewModel.dismissSaveAccountOffer()
            },
            title = { Text("Save Account") },
            text = {
                Column {
                    Text("Would you like to save these login credentials for quick access?")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display name (optional)") },
                        placeholder = { Text("Default: username") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                val resolvedName = displayName.trim().ifBlank { pending.second }
                Button(
                    onClick = {
                        android.util.Log.d("LoginDebug", "Saving account: $resolvedName")
                        viewModel.savePendingAccount(resolvedName)
                    },
                    enabled = resolvedName.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        android.util.Log.d("LoginDebug", "Skip clicked")
                        viewModel.dismissSaveAccountOffer() 
                    }
                ) {
                    Text("Skip")
                }
            }
        )
    }
}

@Composable
private fun rememberNetworkAvailable(): Boolean {
    val context = LocalContext.current
    val connectivityManager = remember {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    var isConnected by remember {
        mutableStateOf(connectivityManager.isCurrentlyConnected())
    }

    DisposableEffect(connectivityManager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isConnected = true
            }

            override fun onLost(network: Network) {
                isConnected = connectivityManager.isCurrentlyConnected()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                isConnected = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)

        onDispose {
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        }
    }

    return isConnected
}

private fun ConnectivityManager.isCurrentlyConnected(): Boolean {
    val network = activeNetwork ?: return false
    val capabilities = getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

private fun formatRelativeTime(timestamp: Long): String {
    if (timestamp <= 0L) return "never"
    val diffMillis = System.currentTimeMillis() - timestamp
    val minutes = diffMillis / (60 * 1000)
    val hours = diffMillis / (60 * 60 * 1000)
    val days = diffMillis / (24 * 60 * 60 * 1000)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours hr ago"
        else -> "$days d ago"
    }
}

@Composable
fun Dhis2PulsingLoader() {
    // Three pulsing dots animation inspired by DHIS2 Android Capture App
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.8f,
                targetValue = 1.3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 800,
                        delayMillis = index * 200, // Stagger the animation
                        easing = EaseInOut
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scaleAnimation"
            )
            
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 800,
                        delayMillis = index * 200,
                        easing = EaseInOut
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alphaAnimation"
            )
            
            Surface(
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {}
        }
    }
}
