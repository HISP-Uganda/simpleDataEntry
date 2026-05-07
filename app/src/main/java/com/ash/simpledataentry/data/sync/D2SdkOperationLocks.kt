package com.ash.simpledataentry.data.sync

import android.util.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicLong

/**
 * Serializes DHIS2 SDK data-value / aggregate operations that mutate the SDK local DB.
 *
 * The SDK uses Room/SQLite internally and can throw "cannot start a transaction within a transaction"
 * when overlapping writes happen from refresh and upload flows.
 */
object D2SdkOperationLocks {
    private const val TAG = "D2SdkOpQueue"
    val dataValueAndAggregateMutex = Mutex()
    private val opCounter = AtomicLong(0L)

    suspend fun <T> withSdkOp(
        opName: String,
        block: suspend () -> T
    ): T {
        val opId = opCounter.incrementAndGet()
        val threadName = Thread.currentThread().name
        val queuedAt = System.currentTimeMillis()
        val job = currentCoroutineContext().toString()
        val caller = Throwable().stackTrace
            .firstOrNull { !it.className.contains("D2SdkOperationLocks") }
            ?.let { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
            ?: "unknown"
        Log.d(TAG, "[$opId] QUEUED op=$opName caller=$caller thread=$threadName ctx=$job")
        return dataValueAndAggregateMutex.withLock {
            val startedAt = System.currentTimeMillis()
            val waitMs = startedAt - queuedAt
            Log.d(TAG, "[$opId] ENTER op=$opName caller=$caller waitMs=$waitMs thread=$threadName")
            try {
                val result = block()
                val doneAt = System.currentTimeMillis()
                Log.d(TAG, "[$opId] EXIT op=$opName caller=$caller holdMs=${doneAt - startedAt} totalMs=${doneAt - queuedAt}")
                result
            } catch (t: Throwable) {
                val failedAt = System.currentTimeMillis()
                Log.e(
                    TAG,
                    "[$opId] FAIL op=$opName caller=$caller holdMs=${failedAt - startedAt} totalMs=${failedAt - queuedAt} msg=${t.message}",
                    t
                )
                throw t
            }
        }
    }
}
