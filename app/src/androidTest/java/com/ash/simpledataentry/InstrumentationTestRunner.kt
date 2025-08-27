package com.ash.simpledataentry

import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Test suite runner for instrumentation tests
 * Groups all UI/integration test classes for easy execution
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    // Database tests (require Android context)
    com.ash.simpledataentry.data.local.DataValueDaoTest::class,
    com.ash.simpledataentry.data.local.DataValueDraftDaoTest::class,
    
    // UI component tests
    com.ash.simpledataentry.presentation.login.LoginScreenTest::class,
    com.ash.simpledataentry.presentation.datasets.DatasetsScreenTest::class,
    com.ash.simpledataentry.presentation.dataEntry.ValidationResultDialogTest::class
)
class InstrumentationTestRunner