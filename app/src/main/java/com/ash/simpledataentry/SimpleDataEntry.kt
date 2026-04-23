package com.ash.simpledataentry

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ash.simpledataentry.domain.repository.SystemRepository
import dagger.hilt.android.HiltAndroidApp
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.plugins.RxJavaPlugins
import java.io.IOException
import java.net.SocketException
import javax.inject.Inject

@HiltAndroidApp
class SimpleDataEntry : Application(), Configuration.Provider {

    @Inject lateinit var systemRepository: SystemRepository
    @Inject lateinit var workerFactory: HiltWorkerFactory
    
    override fun onCreate() {
        super.onCreate()
        configureRxGlobalErrorHandler()
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

    private fun configureRxGlobalErrorHandler() {
        RxJavaPlugins.setErrorHandler { throwable ->
            val error = if (throwable is UndeliverableException && throwable.cause != null) {
                throwable.cause!!
            } else {
                throwable
            }

            when (error) {
                is IOException, is SocketException, is InterruptedException -> {
                    Log.w("SimpleDataEntry", "Ignored undeliverable Rx error: ${error.message}")
                }
                else -> {
                    Log.e("SimpleDataEntry", "Unhandled Rx error", error)
                }
            }
        }
    }
}
