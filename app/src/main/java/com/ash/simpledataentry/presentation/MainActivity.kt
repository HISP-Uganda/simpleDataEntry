package com.ash.simpledataentry.presentation

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.navigation.AppNavigation
import com.ash.simpledataentry.ui.theme.SimpleDataEntryTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.hisp.dhis.mobile.ui.designsystem.theme.DHIS2Theme
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionManager: SessionManager

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
            DHIS2Theme {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavigation(navController = navController)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume - checking D2 session")

        // Restore D2 session if needed when app comes back from background
        lifecycleScope.launch {
            try {
                sessionManager.restoreSessionIfNeeded(this@MainActivity)
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
}

