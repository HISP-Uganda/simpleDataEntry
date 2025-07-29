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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
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


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) {
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
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 4.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
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
}
