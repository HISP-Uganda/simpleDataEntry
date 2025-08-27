package com.ash.simpledataentry.testutil

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.mockito.kotlin.mock

/**
 * Test utilities and extensions for consistent testing
 */

/**
 * JUnit rule that sets the main dispatcher to TestDispatcher for testing coroutines
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

/**
 * Creates a mock with relaxed stubbing (returns default values)
 */
inline fun <reified T : Any> relaxedMock(): T = mock<T>()

/**
 * Extension for testing LiveData/StateFlow values
 */
val instantTaskExecutorRule = InstantTaskExecutorRule()

/**
 * Common test constants
 */
object TestConstants {
    const val TEST_TIMEOUT_MS = 5000L
    const val TEST_DATASET_UID = "testDataset123"
    const val TEST_ORG_UNIT_UID = "testOrgUnit456"
    const val TEST_PERIOD = "202401"
    const val TEST_ATTRIBUTE_COMBO = "testAttributeCombo"
    const val TEST_USERNAME = "testuser"
    const val TEST_SERVER_URL = "https://test.dhis2.org"
    const val TEST_ACCOUNT_UID = "testAccount789"
}