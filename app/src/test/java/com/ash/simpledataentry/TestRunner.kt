package com.ash.simpledataentry

import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Test suite runner for unit tests
 * Groups all unit test classes for easy execution
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    // Domain layer tests
    com.ash.simpledataentry.domain.useCase.DataEntryUseCasesTest::class,
    com.ash.simpledataentry.domain.useCase.LoginUseCaseTest::class,
    com.ash.simpledataentry.domain.useCase.DatasetInstancesUseCasesTest::class,
    com.ash.simpledataentry.domain.validation.ValidationServiceTest::class,
    
    // Presentation layer tests
    com.ash.simpledataentry.presentation.dataEntry.DataEntryViewModelTest::class,
    com.ash.simpledataentry.presentation.login.LoginViewModelTest::class,
    
    // Data layer tests
    com.ash.simpledataentry.data.repositoryImpl.DataEntryRepositoryImplTest::class,
    com.ash.simpledataentry.data.repositoryImpl.SavedAccountRepositoryTest::class
)
class TestRunner