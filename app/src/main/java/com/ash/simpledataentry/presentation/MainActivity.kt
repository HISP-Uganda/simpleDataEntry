package com.ash.simpledataentry.presentation

import android.annotation.SuppressLint
import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.navigation.AppNavigation
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.ash.simpledataentry.ui.theme.SimpleDataEntryTheme
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionManager: SessionManager
    private val _isRestoringSession = MutableStateFlow(false)
    private val isRestoringSession: StateFlow<Boolean> = _isRestoringSession.asStateFlow()
    private var restoreJob: Job? = null
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d("MainActivity", "POST_NOTIFICATIONS granted=$granted")
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, true)
        requestNotificationPermissionIfNeeded()
        // Initialize D2 on app start
        restoreJob?.cancel()
        restoreJob = lifecycleScope.launch {
            _isRestoringSession.value = true
            try {
                sessionManager.initD2(this@MainActivity)
                val restored = withTimeoutOrNull(8_000) {
                    sessionManager.restoreSessionIfNeeded(this@MainActivity)
                    true
                } ?: false
                if (!restored) {
                    Log.w("MainActivity", "Session restoration timed out on app start; continuing")
                }
                Log.d("MainActivity", "D2 initialized in onCreate")
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to initialize D2 in onCreate", e)
            } finally {
                _isRestoringSession.value = false
            }
        }

        setContent {
            val isRestoring by isRestoringSession.collectAsState()
            SimpleDataEntryTheme {
                if (isRestoring) {
                    val isDarkTheme = isSystemInDarkTheme()
                    val statusBarColor = if (isDarkTheme) Color.Black else Color.White
                    val navigationBarColor = if (isDarkTheme) Color.Black else Color.White
                    SideEffect {
                        window.statusBarColor = statusBarColor.toArgb()
                        window.navigationBarColor = navigationBarColor.toArgb()
                        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
                        insetsController.isAppearanceLightStatusBars = !isDarkTheme
                        insetsController.isAppearanceLightNavigationBars = !isDarkTheme
                    }
                }
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AppNavigation(navController = navController)
                        if (isRestoring) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.35f))
                            ) {
                                Surface(
                                    modifier = Modifier.align(Alignment.Center),
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text("Restoring session...")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume - checking D2 session")

        // Restore session silently in background when app returns from recents.
        // Avoid showing a blocking full-screen overlay on every resume.
        restoreJob?.cancel()
        restoreJob = lifecycleScope.launch {
            try {
                withTimeoutOrNull(8_000) {
                    sessionManager.restoreSessionIfNeeded(this@MainActivity)
                } ?: Log.w("MainActivity", "Session restoration timed out on resume")
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to restore session on resume", e)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d("MainActivity", "onPause - preserving session state")
    }

    override fun onStop() {
        super.onStop()
        Log.d("MainActivity", "onStop - app going to background")
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) return
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
