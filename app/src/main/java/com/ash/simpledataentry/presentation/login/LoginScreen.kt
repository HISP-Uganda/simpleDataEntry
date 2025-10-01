package com.ash.simpledataentry.presentation.login

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import com.ash.simpledataentry.R
import com.ash.simpledataentry.presentation.core.FullScreenLoader
import com.ash.simpledataentry.presentation.core.LoadingAnimationType
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ash.simpledataentry.navigation.Screen.DatasetsScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.ash.simpledataentry.data.local.AppDatabase
import androidx.room.Room
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.graphicsLayer


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(state.isLoggedIn, state.saveAccountOffered) {
        if (state.isLoggedIn && !state.saveAccountOffered) {
            navController.navigate("datasets") {
                popUpTo("login") { inclusive = true }
            }
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(error)
                viewModel.clearError()
            }
        }
    }

    if (state.showSplash) {
        // Enhanced loading screen with detailed progress
        FullScreenLoader(
            message = state.navigationProgress?.phaseTitle ?: "Loading your data...",
            isVisible = true,
            animationType = LoadingAnimationType.DHIS2_PULSING_DOTS,
            progress = state.navigationProgress?.overallPercentage,
            progressStep = state.navigationProgress?.phaseDetail,
            showBackgroundWarning = true,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        // Local state for input fields, preserved across configuration changes
        var serverUrl by rememberSaveable { mutableStateOf("https://") }
        var username by rememberSaveable { mutableStateOf("") }
        var password by rememberSaveable { mutableStateOf("") }
        var showUrlDropdown by remember { mutableStateOf(false) }
        var showAccountDropdown by remember { mutableStateOf(false) }
        var passwordVisible by remember { mutableStateOf(false) }

        val context = LocalContext.current

        Box(modifier = Modifier.fillMaxSize()) {
            val scrollState = rememberScrollState()
            val usernameBringIntoViewRequester = remember { BringIntoViewRequester() }
            val passwordBringIntoViewRequester = remember { BringIntoViewRequester() }
            val loginButtonBringIntoViewRequester = remember { BringIntoViewRequester() }

            var usernameFocused by remember { mutableStateOf(false) }
            var passwordFocused by remember { mutableStateOf(false) }

            // Auto-scroll to bring login button into view when username field gets focus
            LaunchedEffect(usernameFocused) {
                if (usernameFocused) {
                    // First ensure username field is visible
                    usernameBringIntoViewRequester.bringIntoView()
                    // Then bring login button into view
                    kotlinx.coroutines.delay(300)
                    loginButtonBringIntoViewRequester.bringIntoView()
                }
            }

            // Auto-scroll to bring login button into view when password field gets focus
            LaunchedEffect(passwordFocused) {
                if (passwordFocused) {
                    // First ensure password field is visible
                    passwordBringIntoViewRequester.bringIntoView()
                    // Then bring login button into view
                    kotlinx.coroutines.delay(300)
                    loginButtonBringIntoViewRequester.bringIntoView()
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top spacer for vertical centering when not scrolled
                Spacer(modifier = Modifier.height(32.dp))

                // Large DHIS2 Logo - can now be bigger since we have scroll
                Icon(
                    painter = painterResource(id = R.drawable.dhis2_official_logo),
                    contentDescription = "DHIS2 Logo",
                    modifier = Modifier
                        .height(280.dp)
                        .padding(bottom = 24.dp),
                    tint = Color.Unspecified // Use original colors
                )
                
                // Saved Account Selection (if any accounts exist)
                if (state.savedAccounts.isNotEmpty()) {
                    Box {
                        OutlinedTextField(
                            value = "", 
                            onValueChange = { }, 
                            label = { Text("Select Saved Account") },
                            placeholder = { Text("Choose an account or enter manually") },
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            trailingIcon = {
                                IconButton(
                                    onClick = { showAccountDropdown = !showAccountDropdown }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Show saved accounts"
                                    )
                                }
                            }
                        )
                        
                        DropdownMenu(
                            expanded = showAccountDropdown,
                            onDismissRequest = { showAccountDropdown = false },
                            modifier = Modifier.fillMaxWidth() // Make dropdown match field width
                        ) {
                            state.savedAccounts.forEach { account ->
                                DropdownMenuItem(
                                    text = { 
                                        Column {
                                            Text(
                                                text = account.displayName,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = "${account.username}@${account.serverUrl}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        val (url, user, _) = viewModel.selectAccount(account)
                                        serverUrl = url
                                        username = user
                                        showAccountDropdown = false
                                    }
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                Box {
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = {
                            serverUrl = it
                            // Auto-suggestions disabled for better user experience
                            // Only show cached URLs dropdown when explicitly requested
                            viewModel.clearUrlSuggestions()
                        },
                        label = { Text("Server URL") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (!focusState.isFocused) {
                                    viewModel.clearUrlSuggestions()
                                }
                            },
                        enabled = !state.isLoading,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = "Server URL",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            if (state.cachedUrls.isNotEmpty()) {
                                IconButton(
                                    onClick = { 
                                        showUrlDropdown = !showUrlDropdown
                                        // Don't trigger URL suggestions when clicking dropdown button
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
                    
                    // Dropdown for cached URLs
                    DropdownMenu(
                        expanded = showUrlDropdown,
                        onDismissRequest = { showUrlDropdown = false }
                    ) {
                        state.cachedUrls.take(5).forEach { cachedUrl ->
                            DropdownMenuItem(
                                text = { Text(cachedUrl.url) },
                                onClick = {
                                    serverUrl = cachedUrl.url
                                    showUrlDropdown = false
                                    viewModel.clearUrlSuggestions()
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            viewModel.removeUrl(cachedUrl.url)
                                            showUrlDropdown = false
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Remove URL"
                                        )
                                    }
                                }
                            )
                        }
                    }
                    
                    // Auto-suggestions disabled - only show cached URLs dropdown when explicitly requested
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
                    enabled = !state.isLoading,
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
                        .bringIntoViewRequester(passwordBringIntoViewRequester)
                        .onFocusChanged { focusState ->
                            passwordFocused = focusState.isFocused
                        },
                    enabled = !state.isLoading,
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
                
                // Forgot Password Link
                TextButton(
                    onClick = { /* TODO: Implement forgot password functionality */ }
                ) {
                    Text(
                        text = "Forgot Password",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                // Login Button
                Button(
                    onClick = {
                        val db = Room.databaseBuilder(
                            context,
                            AppDatabase::class.java,
                            "simple_data_entry_db"
                        )
                        // TODO: Replace fallbackToDestructiveMigration() with a real migration before production release!
                        .fallbackToDestructiveMigration()
                        .build()
                        viewModel.loginWithProgress(serverUrl, username, password, context, db)
                    },
                    enabled = !state.isLoading &&
                            serverUrl.isNotBlank() &&
                            username.isNotBlank() &&
                            password.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .bringIntoViewRequester(loginButtonBringIntoViewRequester),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "Log in",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                // Bottom spacer for consistent spacing
                Spacer(modifier = Modifier.height(32.dp))
            }

            // Show error message as a Snackbar
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomCenter
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
            
        }
    }

    // Save Account Dialog (moved outside if/else)
    if (state.saveAccountOffered && state.pendingCredentials != null) {
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
                            val (serverUrl, username, password) = state.pendingCredentials!!
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
