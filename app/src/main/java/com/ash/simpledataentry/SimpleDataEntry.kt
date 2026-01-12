package com.ash.simpledataentry

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ash.simpledataentry.domain.repository.SystemRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltAndroidApp
class SimpleDataEntry : Application(), Configuration.Provider {

    @Inject lateinit var systemRepository: SystemRepository
    @Inject lateinit var workerFactory: HiltWorkerFactory
    
    override fun onCreate() {
        super.onCreate()
        initializeApp()
    }
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun initializeApp() {
        // D2 initialization removed - now handled by MainActivity.onCreate()
        // This eliminates race condition with multiple concurrent D2 inits
    }
}
