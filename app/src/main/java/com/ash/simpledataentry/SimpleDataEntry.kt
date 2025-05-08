package com.ash.simpledataentry

import android.app.Application
import android.util.Log
import com.ash.simpledataentry.domain.repository.SystemRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltAndroidApp
class SimpleDataEntry : Application() {


    @Inject lateinit var systemRepository: SystemRepository
    // Application level setup can go here if needed
    override fun onCreate() {
        super.onCreate()
        initializeApp()
    }

    private fun initializeApp() {

        CoroutineScope(Dispatchers.IO).launch {
            try {
                systemRepository.initializeD2(this@SimpleDataEntry)
                Log.d("Application", "D2 initialization completed")
            } catch (e: Exception) {
                Log.e("Application", "D2 initialization failed", e)
            }
        }

    }
}
