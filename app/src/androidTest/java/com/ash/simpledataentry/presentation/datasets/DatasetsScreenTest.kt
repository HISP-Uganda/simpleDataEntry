package com.ash.simpledataentry.presentation.datasets

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ash.simpledataentry.domain.model.Dataset
import com.ash.simpledataentry.ui.theme.SimpleDataEntryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatasetsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun datasetsScreen_displaysLoadingState() {
        // Arrange
        val loadingState = DatasetsState(
            isLoading = true,
            datasets = emptyList()
        )

        // Act
        composeTestRule.setContent {
            SimpleDataEntryTheme {
                DatasetsScreen(
                    state = loadingState,
                    onDatasetSelected = {},
                    onRefresh = {},
                    onLogout = {},
                    onNavigateToSettings = {},
                    onNavigateToAbout = {},
                    onNavigateToReportIssues = {}
                )
            }
        }

        // Assert
        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertIsDisplayed()
    }

    @Test
    fun datasetsScreen_displaysDatasetList() {
        // Arrange
        val datasets = listOf(
            Dataset(
                uid = "dataset1",
                displayName = "Immunization Dataset",
                organisationUnits = listOf("orgunit1"),
                periods = listOf("202401")
            ),
            Dataset(
                uid = "dataset2",
                displayName = "Malaria Dataset", 
                organisationUnits = listOf("orgunit1"),
                periods = listOf("202401")
            )
        )
        
        val datasetsState = DatasetsState(
            isLoading = false,
            datasets = datasets
        )

        // Act
        composeTestRule.setContent {
            SimpleDataEntryTheme {
                DatasetsScreen(
                    state = datasetsState,
                    onDatasetSelected = {},
                    onRefresh = {},
                    onLogout = {},
                    onNavigateToSettings = {},
                    onNavigateToAbout = {},
                    onNavigateToReportIssues = {}
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Immunization Dataset").assertIsDisplayed()
        composeTestRule.onNodeWithText("Malaria Dataset").assertIsDisplayed()
    }

    @Test
    fun datasetsScreen_datasetClickTriggersCallback() {
        // Arrange
        val datasets = listOf(
            Dataset(
                uid = "dataset1",
                displayName = "Test Dataset",
                organisationUnits = listOf("orgunit1"),
                periods = listOf("202401")
            )
        )
        
        var selectedDatasetUid: String? = null
        val datasetsState = DatasetsState(
            isLoading = false,
            datasets = datasets
        )

        // Act
        composeTestRule.setContent {
            SimpleDataEntryTheme {
                DatasetsScreen(
                    state = datasetsState,
                    onDatasetSelected = { selectedDatasetUid = it },
                    onRefresh = {},
                    onLogout = {},
                    onNavigateToSettings = {},
                    onNavigateToAbout = {},
                    onNavigateToReportIssues = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Test Dataset").performClick()

        // Assert
        assert(selectedDatasetUid == "dataset1")
    }

    @Test
    fun datasetsScreen_showsEmptyStateWhenNoDatasets() {
        // Arrange
        val emptyState = DatasetsState(
            isLoading = false,
            datasets = emptyList()
        )

        // Act
        composeTestRule.setContent {
            SimpleDataEntryTheme {
                DatasetsScreen(
                    state = emptyState,
                    onDatasetSelected = {},
                    onRefresh = {},
                    onLogout = {},
                    onNavigateToSettings = {},
                    onNavigateToAbout = {},
                    onNavigateToReportIssues = {}
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("No datasets available")
            .assertIsDisplayed()
    }

    @Test
    fun datasetsScreen_showsErrorState() {
        // Arrange
        val errorState = DatasetsState(
            isLoading = false,
            datasets = emptyList(),
            error = "Failed to load datasets. Please check your connection."
        )

        // Act
        composeTestRule.setContent {
            SimpleDataEntryTheme {
                DatasetsScreen(
                    state = errorState,
                    onDatasetSelected = {},
                    onRefresh = {},
                    onLogout = {},
                    onNavigateToSettings = {},
                    onNavigateToAbout = {},
                    onNavigateToReportIssues = {}
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Failed to load datasets. Please check your connection.")
            .assertIsDisplayed()
    }

    @Test
    fun datasetsScreen_refreshButtonTriggersCallback() {
        // Arrange
        var refreshCalled = false
        val state = DatasetsState(
            isLoading = false,
            datasets = emptyList()
        )

        // Act
        composeTestRule.setContent {
            SimpleDataEntryTheme {
                DatasetsScreen(
                    state = state,
                    onDatasetSelected = {},
                    onRefresh = { refreshCalled = true },
                    onLogout = {},
                    onNavigateToSettings = {},
                    onNavigateToAbout = {},
                    onNavigateToReportIssues = {}
                )
            }
        }

        // Find and click refresh button (this might be in a menu or as a separate button)
        composeTestRule.onNodeWithContentDescription("Refresh").performClick()

        // Assert
        assert(refreshCalled)
    }

    @Test
    fun datasetsScreen_logoutButtonTriggersCallback() {
        // Arrange
        var logoutCalled = false
        val state = DatasetsState(
            isLoading = false,
            datasets = emptyList()
        )

        // Act
        composeTestRule.setContent {
            SimpleDataEntryTheme {
                DatasetsScreen(
                    state = state,
                    onDatasetSelected = {},
                    onRefresh = {},
                    onLogout = { logoutCalled = true },
                    onNavigateToSettings = {},
                    onNavigateToAbout = {},
                    onNavigateToReportIssues = {}
                )
            }
        }

        // Open menu first (assuming logout is in overflow menu)
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("Logout").performClick()

        // Assert
        assert(logoutCalled)
    }

    @Test
    fun datasetsScreen_searchFunctionalityWorks() {
        // Arrange
        val datasets = listOf(
            Dataset(
                uid = "dataset1",
                displayName = "Immunization Dataset",
                organisationUnits = listOf("orgunit1"),
                periods = listOf("202401")
            ),
            Dataset(
                uid = "dataset2",
                displayName = "Malaria Dataset",
                organisationUnits = listOf("orgunit1"),
                periods = listOf("202401")
            )
        )
        
        val state = DatasetsState(
            isLoading = false,
            datasets = datasets
        )

        // Act
        composeTestRule.setContent {
            SimpleDataEntryTheme {
                DatasetsScreen(
                    state = state,
                    onDatasetSelected = {},
                    onRefresh = {},
                    onLogout = {},
                    onNavigateToSettings = {},
                    onNavigateToAbout = {},
                    onNavigateToReportIssues = {}
                )
            }
        }

        // Assuming there's a search functionality
        composeTestRule.onNodeWithText("Search datasets").performTextInput("Immunization")

        // Assert - only matching dataset should be visible
        composeTestRule.onNodeWithText("Immunization Dataset").assertIsDisplayed()
        composeTestRule.onNodeWithText("Malaria Dataset").assertDoesNotExist()
    }
}