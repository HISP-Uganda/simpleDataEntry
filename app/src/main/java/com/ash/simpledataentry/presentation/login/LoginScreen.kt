package com.ash.simpledataentry.presentation.login

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.imePadding
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ash.simpledataentry.R
import com.ash.simpledataentry.presentation.core.AdaptiveLoadingOverlay
import com.ash.simpledataentry.presentation.core.LoadingOperation
import com.ash.simpledataentry.presentation.core.LoadingPhase
import com.ash.simpledataentry.presentation.core.LoginFormSkeleton
import com.ash.simpledataentry.presentation.core.NavigationProgress
import com.ash.simpledataentry.presentation.core.StepLoadingScreen
import com.ash.simpledataentry.presentation.core.StepLoadingType
import com.ash.simpledataentry.presentation.core.UiState
import com.ash.simpledataentry.presentation.login.ProfileSelectorCard
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
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

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

    LaunchedEffect(loginData.isLoggedIn, loginData.saveAccountOffered) {
        if (loginData.isLoggedIn && !loginData.saveAccountOffered) {
            navController.navigate("datasets") {
                popUpTo("login") { inclusive = true }
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

    // Show a placeholder only for the very first splash, otherwise overlay the form content
    if (showInitialSplash) {
        LoginFormSkeleton(modifier = Modifier.fillMaxSize())
    } else if (stepLoadingInfo != null) {
        StepLoadingScreen(
            type = stepLoadingInfo.type,
            currentStep = stepLoadingInfo.stepIndex,
            progressPercent = stepLoadingInfo.percent,
            currentLabel = stepLoadingInfo.label,
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
            val context = LocalContext.current
            val versionLabel = runCatching {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                "Version ${info.versionName}"
            }.getOrDefault("Version")

            var selectedProfileId by rememberSaveable {
                mutableStateOf(loginData.savedAccounts.firstOrNull { it.isActive }?.id)
            }

            LaunchedEffect(loginData.savedAccounts) {
                if (loginData.savedAccounts.isNotEmpty()) {
                    if (selectedProfileId == null || loginData.savedAccounts.none { it.id == selectedProfileId }) {
                        selectedProfileId = loginData.savedAccounts.firstOrNull { it.isActive }?.id
                            ?: loginData.savedAccounts.firstOrNull()?.id
                    }
                }
            }

            val selectedProfile = loginData.savedAccounts.firstOrNull { it.id == selectedProfileId }
            val scrollState = rememberScrollState()
            val usernameBringIntoViewRequester = remember { BringIntoViewRequester() }
            val passwordBringIntoViewRequester = remember { BringIntoViewRequester() }
            val loginButtonBringIntoViewRequester = remember { BringIntoViewRequester() }
            val profileBringIntoViewRequester = remember { BringIntoViewRequester() }
            val passwordFocusRequester = remember { FocusRequester() }

            var usernameFocused by remember { mutableStateOf(false) }
            var passwordFocused by remember { mutableStateOf(false) }
            var focusRequestCounter by remember { mutableStateOf(0) }

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

            LaunchedEffect(focusRequestCounter) {
                if (focusRequestCounter > 0) {
                    profileBringIntoViewRequester.bringIntoView()
                    passwordBringIntoViewRequester.bringIntoView()
                    kotlinx.coroutines.delay(120)
                    passwordFocusRequester.requestFocus()
                }
            }

            val gradientBrush = Brush.verticalGradient(
                colors = listOf(DHIS2Blue, DHIS2BlueDark)
            )

            fun resetForm() {
                serverUrl = "https://"
                username = ""
                password = ""
                passwordVisible = false
                selectedProfileId = null
                viewModel.clearUrlSuggestions()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(gradientBrush)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.dhis2_official_logo),
                        contentDescription = "DHIS2 Logo",
                        modifier = Modifier
                            .height(220.dp)
                            .padding(bottom = 12.dp),
                        tint = Color.Unspecified
                    )

                    ProfileSelectorCard(
                        profiles = loginData.savedAccounts,
                        selectedProfile = selectedProfile,
                        onSelectProfile = { account ->
                            val (url, user, _) = viewModel.selectAccount(account)
                            serverUrl = url
                            username = user
                            selectedProfileId = account.id
                            focusRequestCounter += 1
                        },
                        onAddNew = {
                            resetForm()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(profileBringIntoViewRequester)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Welcome back",
                                style = MaterialTheme.typography.titleMedium
                            )

                            var serverUrlFieldSize by remember { mutableStateOf(IntSize.Zero) }
                            val density = LocalDensity.current

                            Box {
                                OutlinedTextField(
                                    value = serverUrl,
                                    onValueChange = {
                                        serverUrl = it
                                        viewModel.clearUrlSuggestions()
                                    },
                                    label = { Text("Server URL") },
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

                            Surface(
                                color = DHIS2BlueLight.copy(alpha = 0.3f),
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
                                        tint = DHIS2Blue
                                    )
                                    Column {
                                        Text(
                                            text = "Internet connection required",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "Sync in real time with DHIS2 servers.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { resetForm() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text("Cancel")
                                }

                                Button(
                                    onClick = {
                                        viewModel.loginWithProgress(serverUrl, username, password, context)
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
                                        text = "Login & Sync",
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }

                            TextButton(
                                onClick = { /* TODO: Implement forgot password functionality */ },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text(
                                    text = "Forgot Password",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
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

                Text(
                    text = versionLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                )
            }
        }
    }

    // Save Account Dialog (moved outside if/else)
    if (loginData.saveAccountOffered && loginData.pendingCredentials != null) {
        android.util.Log.d("LoginDebug", "About to show save account dialog")

        var displayName by remember { mutableStateOf("") }

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
                        label = { Text("Display Name") },
                        placeholder = { Text("e.g., Work Account, Personal") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (displayName.isNotBlank()) {
                            val (serverUrl, username, password) = loginData.pendingCredentials!!
                            android.util.Log.d("LoginDebug", "Saving account: $displayName")
                            viewModel.saveAccount(displayName, serverUrl, username, password)
                        }
                    },
                    enabled = displayName.isNotBlank()
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
