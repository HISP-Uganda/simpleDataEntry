package com.ash.simpledataentry.data.sync

import kotlinx.coroutines.sync.Mutex

/**
 * Serializes DHIS2 SDK data-value / aggregate operations that mutate the SDK local DB.
 *
 * The SDK uses Room/SQLite internally and can throw "cannot start a transaction within a transaction"
 * when overlapping writes happen from refresh and upload flows.
 */
object D2SdkOperationLocks {
    val dataValueAndAggregateMutex = Mutex()
}
