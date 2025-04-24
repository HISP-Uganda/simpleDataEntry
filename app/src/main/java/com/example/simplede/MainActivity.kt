package com.example.simplede

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.example.simplede.presentation.features.dataEntry.CreateNewEntryScreen
import com.example.simplede.presentation.features.dataEntry.DataEntryViewModel
import com.example.simplede.presentation.features.dataEntry.EditEntryScreen
import com.example.simplede.presentation.features.datasetInstances.DatasetInstancesScreen
import com.example.simplede.presentation.features.datasetInstances.DatasetInstancesViewModelFactory
import com.example.simplede.presentation.features.datasets.DatasetsScreen
import com.example.simplede.presentation.features.login.LoginScreen
import org.hisp.dhis.mobile.ui.designsystem.theme.DHIS2Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DHIS2Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "Login") {
                        composable("Login") { LoginScreen(navController) }
                        composable("Datasets") { DatasetsScreen(navController) }
                        composable(
                            route = "DatasetInstances/{datasetId}/{datasetName}",
                            arguments = listOf(
                                navArgument("datasetId") { type = NavType.StringType },
                                navArgument("datasetName") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val datasetId = backStackEntry.arguments?.getString("datasetId") ?: ""
                            val datasetName = backStackEntry.arguments?.getString("datasetName") ?: ""
                            DatasetInstancesScreen(
                                navController = navController,
                                datasetId = datasetId,
                                datasetName = datasetName,
                                viewModel = viewModel(
                                    factory = DatasetInstancesViewModelFactory(datasetId)
                                )
                            )
                        }
                        composable(
                            route = "CreateDataEntry/{datasetId}/{datasetName}",
                            arguments = listOf(
                                navArgument("datasetId") { type = NavType.StringType },
                                navArgument("datasetName") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val datasetId = backStackEntry.arguments?.getString("datasetId") ?: ""
                            val datasetName = backStackEntry.arguments?.getString("datasetName") ?: ""
                            val viewModel: DataEntryViewModel = viewModel()

                            // Initialize the new entry with the dataset ID and name
                            viewModel.initializeNewEntry(datasetId, datasetName)

                            CreateNewEntryScreen(
                                navController = navController,
                                datasetId = datasetId,
                                datasetName = datasetName,
                                viewModel = viewModel
                            )
                        }
                        composable(
                            route = "EditEntry/{datasetId}/{instanceId}/{datasetName}/{period}/{attributeOptionCombo}",
                            arguments = listOf(
                                navArgument("datasetId") { type = NavType.StringType },
                                navArgument("instanceId") { type = NavType.StringType },
                                navArgument("datasetName") { type = NavType.StringType },
                                navArgument("period") { type = NavType.StringType },
                                navArgument("attributeOptionCombo") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val datasetId = backStackEntry.arguments?.getString("datasetId") ?: ""
                            val instanceId = backStackEntry.arguments?.getString("instanceId") ?: ""
                            val datasetName = backStackEntry.arguments?.getString("datasetName") ?: ""
                            val period = backStackEntry.arguments?.getString("period") ?: ""
                            val attributeOptionCombo = backStackEntry.arguments?.getString("attributeOptionCombo") ?: ""

                            val viewModel: DataEntryViewModel = viewModel()
                            viewModel.loadExistingEntry(datasetId)

                            EditEntryScreen(
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    }
}