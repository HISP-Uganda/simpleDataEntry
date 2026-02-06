package com.ash.simpledataentry.presentation

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.navigation.AppNavigation
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.ash.simpledataentry.ui.theme.SimpleDataEntryTheme
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionManager: SessionManager
    private val _isRestoringSession = MutableStateFlow(false)
    private val isRestoringSession: StateFlow<Boolean> = _isRestoringSession.asStateFlow()

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, true)
        // Initialize D2 on app start
        lifecycleScope.launch {
            try {
                sessionManager.initD2(this@MainActivity)
                Log.d("MainActivity", "D2 initialized in onCreate")
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to initialize D2 in onCreate", e)
            }
        }

        setContent {
            val isRestoring by isRestoringSession.collectAsState()
            SimpleDataEntryTheme {
                val isLightTheme = !isSystemInDarkTheme()
                val barColor = if (isLightTheme) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.primary
                }
                val useDarkIcons = isLightTheme && barColor.luminance() > 0.5f
                SideEffect {
                    window.statusBarColor = barColor.toArgb()
                    window.navigationBarColor = barColor.toArgb()
                    val insetsController = WindowInsetsControllerCompat(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = useDarkIcons
                    insetsController.isAppearanceLightNavigationBars = useDarkIcons
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
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume - checking D2 session")

        // Restore D2 session if needed when app comes back from background
        lifecycleScope.launch {
            _isRestoringSession.value = true
            try {
                sessionManager.restoreSessionIfNeeded(this@MainActivity)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to restore session on resume", e)
            } finally {
                _isRestoringSession.value = false
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
}
