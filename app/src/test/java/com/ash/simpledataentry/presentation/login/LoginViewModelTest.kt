package com.ash.simpledataentry.presentation.login

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.ash.simpledataentry.domain.model.Dhis2Config
import com.ash.simpledataentry.domain.useCase.LoginUseCase
import com.ash.simpledataentry.testutil.MainDispatcherRule
import com.ash.simpledataentry.testutil.TestConstants.TEST_SERVER_URL
import com.ash.simpledataentry.testutil.TestConstants.TEST_USERNAME
import com.ash.simpledataentry.testutil.TestDataBuilders
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var loginUseCase: LoginUseCase

    private lateinit var viewModel: LoginViewModel

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        viewModel = LoginViewModel(loginUseCase)
    }

    @Test
    fun `initial state should have default values`() = runTest {
        viewModel.state.test {
            val initialState = awaitItem()
            
            assertThat(initialState.serverUrl).isEmpty()
            assertThat(initialState.username).isEmpty()
            assertThat(initialState.password).isEmpty()
            assertThat(initialState.isLoading).isFalse()
            assertThat(initialState.errorMessage).isNull()
            assertThat(initialState.isLoggedIn).isFalse()
        }
    }

    @Test
    fun `updateServerUrl should update server URL in state`() = runTest {
        viewModel.state.test {
            val initialState = awaitItem()
            
            viewModel.updateServerUrl(TEST_SERVER_URL)
            
            val updatedState = awaitItem()
            assertThat(updatedState.serverUrl).isEqualTo(TEST_SERVER_URL)
        }
    }

    @Test
    fun `updateUsername should update username in state`() = runTest {
        viewModel.state.test {
            val initialState = awaitItem()
            
            viewModel.updateUsername(TEST_USERNAME)
            
            val updatedState = awaitItem()
            assertThat(updatedState.username).isEqualTo(TEST_USERNAME)
        }
    }

    @Test
    fun `updatePassword should update password in state`() = runTest {
        viewModel.state.test {
            val initialState = awaitItem()
            
            viewModel.updatePassword("testpass")
            
            val updatedState = awaitItem()
            assertThat(updatedState.password).isEqualTo("testpass")
        }
    }

    @Test
    fun `login should succeed with valid credentials`() = runTest {
        // Arrange
        val config = TestDataBuilders.createTestDhis2Config(
            serverUrl = TEST_SERVER_URL,
            username = TEST_USERNAME,
            password = "testpass"
        )
        whenever(loginUseCase.invoke(config)).thenReturn(Result.success(Unit))

        // Set up initial state
        viewModel.updateServerUrl(TEST_SERVER_URL)
        viewModel.updateUsername(TEST_USERNAME)
        viewModel.updatePassword("testpass")

        // Act & Assert
        viewModel.state.test {
            skipItems(3) // Skip initial and update states
            val readyState = awaitItem()

            viewModel.login()
            
            val loadingState = awaitItem()
            assertThat(loadingState.isLoading).isTrue()
            assertThat(loadingState.errorMessage).isNull()
            
            val successState = awaitItem()
            assertThat(successState.isLoading).isFalse()
            assertThat(successState.isLoggedIn).isTrue()
            assertThat(successState.errorMessage).isNull()
        }
    }

    @Test
    fun `login should fail with invalid credentials`() = runTest {
        // Arrange
        val config = TestDataBuilders.createTestDhis2Config(
            serverUrl = TEST_SERVER_URL,
            username = TEST_USERNAME,
            password = "wrongpass"
        )
        val exception = RuntimeException("Authentication failed")
        whenever(loginUseCase.invoke(config)).thenReturn(Result.failure(exception))

        // Set up initial state
        viewModel.updateServerUrl(TEST_SERVER_URL)
        viewModel.updateUsername(TEST_USERNAME)
        viewModel.updatePassword("wrongpass")

        // Act & Assert
        viewModel.state.test {
            skipItems(3) // Skip initial and update states
            val readyState = awaitItem()

            viewModel.login()
            
            val loadingState = awaitItem()
            assertThat(loadingState.isLoading).isTrue()
            
            val failureState = awaitItem()
            assertThat(failureState.isLoading).isFalse()
            assertThat(failureState.isLoggedIn).isFalse()
            assertThat(failureState.errorMessage).contains("Authentication failed")
        }
    }

    @Test
    fun `login should not proceed with empty fields`() = runTest {
        // Act & Assert - try to login with empty fields
        viewModel.state.test {
            val initialState = awaitItem()
            
            viewModel.login()
            
            // Should not emit loading state for empty fields
            expectNoEvents()
        }
    }

    @Test
    fun `clearError should reset error message`() = runTest {
        // Arrange - first cause an error
        val config = TestDataBuilders.createTestDhis2Config()
        whenever(loginUseCase.invoke(config)).thenReturn(Result.failure(RuntimeException("Error")))

        viewModel.updateServerUrl(TEST_SERVER_URL)
        viewModel.updateUsername(TEST_USERNAME)
        viewModel.updatePassword("pass")

        // Act & Assert
        viewModel.state.test {
            skipItems(3) // Skip initial and update states
            
            viewModel.login()
            skipItems(1) // Skip loading state
            val errorState = awaitItem()
            assertThat(errorState.errorMessage).isNotNull()

            viewModel.clearError()
            
            val clearedState = awaitItem()
            assertThat(clearedState.errorMessage).isNull()
        }
    }

    @Test
    fun `logout should reset login state`() = runTest {
        // Arrange - first login successfully
        val config = TestDataBuilders.createTestDhis2Config(
            serverUrl = TEST_SERVER_URL,
            username = TEST_USERNAME,
            password = "testpass"
        )
        whenever(loginUseCase.invoke(config)).thenReturn(Result.success(Unit))

        viewModel.updateServerUrl(TEST_SERVER_URL)
        viewModel.updateUsername(TEST_USERNAME)
        viewModel.updatePassword("testpass")

        // Act & Assert
        viewModel.state.test {
            skipItems(3) // Skip initial and update states
            
            viewModel.login()
            skipItems(1) // Skip loading state
            val loggedInState = awaitItem()
            assertThat(loggedInState.isLoggedIn).isTrue()

            viewModel.logout()
            
            val loggedOutState = awaitItem()
            assertThat(loggedOutState.isLoggedIn).isFalse()
            assertThat(loggedOutState.errorMessage).isNull()
        }
    }
}