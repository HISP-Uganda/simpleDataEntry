package com.ash.simpledataentry.domain.useCase

import com.ash.simpledataentry.domain.model.Dhis2Config
import com.ash.simpledataentry.domain.repository.AuthRepository
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

class LoginUseCaseTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Mock
    private lateinit var authRepository: AuthRepository

    private lateinit var loginUseCase: LoginUseCase

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        loginUseCase = LoginUseCase(authRepository)
    }

    @Test
    fun `login should return success when repository login succeeds`() = runTest {
        // Arrange
        val config = TestDataBuilders.createTestDhis2Config(
            serverUrl = TEST_SERVER_URL,
            username = TEST_USERNAME,
            password = "testpass"
        )
        whenever(authRepository.login(config)).thenReturn(Result.success(Unit))

        // Act
        val result = loginUseCase(config)

        // Assert
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun `login should return failure when repository login fails`() = runTest {
        // Arrange
        val config = TestDataBuilders.createTestDhis2Config(
            serverUrl = TEST_SERVER_URL,
            username = TEST_USERNAME,
            password = "wrongpass"
        )
        val exception = RuntimeException("Authentication failed")
        whenever(authRepository.login(config)).thenReturn(Result.failure(exception))

        // Act
        val result = loginUseCase(config)

        // Assert
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isEqualTo(exception)
    }

    @Test
    fun `login should handle empty credentials`() = runTest {
        // Arrange
        val config = TestDataBuilders.createTestDhis2Config(
            serverUrl = "",
            username = "",
            password = ""
        )
        val exception = RuntimeException("Invalid credentials")
        whenever(authRepository.login(config)).thenReturn(Result.failure(exception))

        // Act
        val result = loginUseCase(config)

        // Assert
        assertThat(result.isFailure).isTrue()
    }
}