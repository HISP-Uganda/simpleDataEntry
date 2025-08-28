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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import com.ash.simpledataentry.R
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // DHIS2-style pulsing loading animation
                Dhis2PulsingLoader()
                
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Loading your data...",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    } else {
        // Local state for input fields, preserved across configuration changes
        var serverUrl by rememberSaveable { mutableStateOf("") }
        var username by rememberSaveable { mutableStateOf("") }
        var password by rememberSaveable { mutableStateOf("") }
        var showUrlDropdown by remember { mutableStateOf(false) }
        var showAccountDropdown by remember { mutableStateOf(false) }
        var passwordVisible by remember { mutableStateOf(false) }

        val context = LocalContext.current

        Box(modifier = Modifier.fillMaxSize().imePadding()) {
            val serverUrlBringIntoViewRequester = remember { BringIntoViewRequester() }
            val usernameBringIntoViewRequester = remember { BringIntoViewRequester() }
            val passwordBringIntoViewRequester = remember { BringIntoViewRequester() }
            var serverUrlFocused by remember { mutableStateOf(false) }
            var usernameFocused by remember { mutableStateOf(false) }
            var passwordFocused by remember { mutableStateOf(false) }

            LaunchedEffect(serverUrlFocused) {
                if (serverUrlFocused) {
                    serverUrlBringIntoViewRequester.bringIntoView()
                }
            }
            LaunchedEffect(usernameFocused) {
                if (usernameFocused) {
                    usernameBringIntoViewRequester.bringIntoView()
                }
            }
            LaunchedEffect(passwordFocused) {
                if (passwordFocused) {
                    passwordBringIntoViewRequester.bringIntoView()
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // DHIS2 Official Logo Icon
                Icon(
                    painter = painterResource(id = R.drawable.dhis2_logo),
                    contentDescription = "DHIS2 Logo",
                    modifier = Modifier
                        .size(80.dp)
                        .padding(bottom = 32.dp),
                    tint = MaterialTheme.colorScheme.primary
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
                            viewModel.updateUrlSuggestions(it)
                        },
                        label = { Text("Server URL") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .bringIntoViewRequester(serverUrlBringIntoViewRequester)
                            .onFocusChanged { focusState ->
                                serverUrlFocused = focusState.isFocused
                                if (focusState.isFocused) {
                                    viewModel.updateUrlSuggestions(serverUrl)
                                } else {
                                    viewModel.clearUrlSuggestions()
                                }
                            },
                        enabled = !state.isLoading,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        trailingIcon = {
                            if (state.cachedUrls.isNotEmpty()) {
                                IconButton(
                                    onClick = { showUrlDropdown = !showUrlDropdown }
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
                    
                    // Dropdown for URL suggestions
                    if (state.urlSuggestions.isNotEmpty() && serverUrl.isNotBlank()) {
                        DropdownMenu(
                            expanded = true,
                            onDismissRequest = { viewModel.clearUrlSuggestions() }
                        ) {
                            state.urlSuggestions.forEach { suggestion ->
                                DropdownMenuItem(
                                    text = { Text(suggestion.url) },
                                    onClick = {
                                        serverUrl = suggestion.url
                                        viewModel.clearUrlSuggestions()
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .bringIntoViewRequester(usernameBringIntoViewRequester)
                        .onFocusChanged { focusState ->
                            usernameFocused = focusState.isFocused
                        },
                    enabled = !state.isLoading
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .bringIntoViewRequester(passwordBringIntoViewRequester)
                        .onFocusChanged { focusState ->
                            passwordFocused = focusState.isFocused
                        },
                    enabled = !state.isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
                Box(
                    modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .imePadding()
                    .padding(16.dp))
        {
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
                        viewModel.login(serverUrl, username, password, context, db)
                    },
                    enabled = !state.isLoading &&
                            serverUrl.isNotBlank() &&
                            username.isNotBlank() &&
                            password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Login")
                }
            }

            // Show error message as a Snackbar
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
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
