package com.ash.simpledataentry.presentation.login

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ash.simpledataentry.ui.theme.SimpleDataEntryTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loginScreen_displaysAllRequiredFields() {
        // Arrange
        val initialState = LoginState()

        // Act
        composeTestRule.setContent {
            SimpleDataEntryTheme {
                LoginScreen(
                    state = initialState,
                    onServerUrlChanged = {},
                    onUsernameChanged = {},
                    onPasswordChanged = {},
                    onLoginClicked = {},
                    onNavigateToAccountSelection = {}
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Server URL").assertIsDisplayed()
        composeTestRule.onNodeWithText("Username").assertIsDisplayed()
        composeTestRule.onNodeWithText("Password").assertIsDisplayed()
        composeTestRule.onNodeWithText("Login").assertIsDisplayed()
    }

    @Test
    fun loginScreen_serverUrlFieldAcceptsInput() {
        // Arrange
        var serverUrl = ""
        val state = LoginState()

        // Act
        composeTestRule.setContent {
            SimpleDataEntryTheme {
                LoginScreen(
                    state = state,
                    onServerUrlChanged = { serverUrl = it },
                    onUsernameChanged = {},
                    onPasswordChanged = {},
                    onLoginClicked = {},
                    onNavigateToAccountSelection = {}
                )
            }
        }

        // Act
        composeTestRule.onNodeWithText("Server URL")
            .performTextInput("https://test.dhis2.org")

        // Assert
        assertThat(serverUrl).isEqualTo("https://test.dhis2.org")
    }

    @Test
    fun loginScreen_usernameFieldAcceptsInput() {
        // Arrange
        var username = ""
        val state = LoginState()

        // Act
        composeTestRule.setContent {
            SimpleDataEntryTheme {
                LoginScreen(
                    state = state,
                    onServerUrlChanged = {},
                    onUsernameChanged = { username = it },
                    onPasswordChanged = {},
                    onLoginClicked = {},
                    onNavigateToAccountSelection = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Username")
            .performTextInput("testuser")

        // Assert
        assertThat(username).isEqualTo("testuser")
    }

    @Test
    fun loginScreen_passwordFieldAcceptsInput() {
        // Arrange
        var password = ""
        val state = LoginState()

        // Act
        composeTestRule.setContent {
            SimpleDataEntryTheme {
                LoginScreen(
                    state = state,
                    onServerUrlChanged = {},
                    onUsernameChanged = {},
                    onPasswordChanged = { password = it },
                    onLoginClicked = {},
                    onNavigateToAccountSelection = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Password")
            .performTextInput("testpassword")

        // Assert
        assertThat(password).isEqualTo("testpassword")
    }

    @Test
    fun loginScreen_loginButtonTriggersCallback() {
        // Arrange
        var loginClicked = false
        val state = LoginState(
            serverUrl = "https://test.dhis2.org",
            username = "testuser",
            password = "testpass"
        )

        // Act
        composeTestRule.setContent {
            SimpleDataEntryTheme {
                LoginScreen(
                    state = state,
                    onServerUrlChanged = {},
                    onUsernameChanged = {},
                    onPasswordChanged = {},
                    onLoginClicked = { loginClicked = true },
                    onNavigateToAccountSelection = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Login").performClick()

        // Assert
        assertThat(loginClicked).isTrue()
    }

    @Test
    fun loginScreen_showsLoadingStateWhenLoading() {
        // Arrange
        val loadingState = LoginState(
            isLoading = true,
            serverUrl = "https://test.dhis2.org",
            username = "testuser",
            password = "testpass"
        )

        // Act
        composeTestRule.setContent {
            SimpleDataEntryTheme {
                LoginScreen(
                    state = loadingState,
                    onServerUrlChanged = {},
                    onUsernameChanged = {},
                    onPasswordChanged = {},
                    onLoginClicked = {},
                    onNavigateToAccountSelection = {}
                )
            }
        }

        // Assert
        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertIsDisplayed()
    }

    @Test
    fun loginScreen_showsErrorMessageWhenError() {
        // Arrange
        val errorState = LoginState(
            errorMessage = "Authentication failed. Please check your credentials."
        )

        // Act
        composeTestRule.setContent {
            SimpleDataEntryTheme {
                LoginScreen(
                    state = errorState,
                    onServerUrlChanged = {},
                    onUsernameChanged = {},
                    onPasswordChanged = {},
                    onLoginClicked = {},
                    onNavigateToAccountSelection = {}
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Authentication failed. Please check your credentials.")
            .assertIsDisplayed()
    }

    @Test
    fun loginScreen_disablesLoginButtonWhenFieldsEmpty() {
        // Arrange
        val emptyState = LoginState()

        // Act
        composeTestRule.setContent {
            SimpleDataEntryTheme {
                LoginScreen(
                    state = emptyState,
                    onServerUrlChanged = {},
                    onUsernameChanged = {},
                    onPasswordChanged = {},
                    onLoginClicked = {},
                    onNavigateToAccountSelection = {}
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Login").assertIsNotEnabled()
    }

    @Test
    fun loginScreen_enablesLoginButtonWhenAllFieldsFilled() {
        // Arrange
        val filledState = LoginState(
            serverUrl = "https://test.dhis2.org",
            username = "testuser",
            password = "testpass"
        )

        // Act
        composeTestRule.setContent {
            SimpleDataEntryTheme {
                LoginScreen(
                    state = filledState,
                    onServerUrlChanged = {},
                    onUsernameChanged = {},
                    onPasswordChanged = {},
                    onLoginClicked = {},
                    onNavigateToAccountSelection = {}
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Login").assertIsEnabled()
    }
}