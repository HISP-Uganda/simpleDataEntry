package com.ash.simpledataentry.navigation

import android.R.attr.onClick
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ash.simpledataentry.domain.repository.DataEntryRepository
import com.ash.simpledataentry.navigation.Screen.DatasetInstanceScreen
//import com.ash.simpledataentry.navigation.Screen.CreateNewEntryScreen
//import com.ash.simpledataentry.navigation.Screen.DatasetInstanceScreen
import com.ash.simpledataentry.navigation.Screen.DatasetsScreen
//import com.ash.simpledataentry.navigation.Screen.EditEntryScreen
import com.ash.simpledataentry.navigation.Screen.LoginScreen
import com.ash.simpledataentry.presentation.dataEntry.CreateNewEntryScreen
import com.ash.simpledataentry.presentation.dataEntry.DataEntryViewModel
import com.ash.simpledataentry.presentation.dataEntry.EditEntryScreen
import com.ash.simpledataentry.presentation.datasetInstances.DatasetInstancesScreen
import com.ash.simpledataentry.presentation.datasets.DatasetsScreen
import com.ash.simpledataentry.presentation.login.LoginScreen

sealed class Screen(val route: String) {

    data object LoginScreen : Screen("login")  // Added login screen
    data object DatasetsScreen : Screen("datasets")
    data class DatasetInstanceScreen(val datasetId: String, val datasetName: String) : Screen("instances")
//    data object CreateNewEntryScreen : Screen("createnewinstance")
//    data object EditEntryScreen : Screen("editinstance")

}




@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    startDestination: String = LoginScreen.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier
    ) {
        composable(LoginScreen.route) {
            LoginScreen(navController = navController)
        }
        composable(DatasetsScreen.route) {
            DatasetsScreen(navController = navController)
        }

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
                datasetName = datasetName
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
            val viewModel: DataEntryViewModel = hiltViewModel()



            CreateNewEntryScreen(
                navController = navController,
                datasetId = datasetId,
                datasetName = datasetName,
                viewModel = viewModel

            )
        }



        composable(
            route = "EditEntry/{datasetId}/{period}/{orgUnit}/{attributeOptionCombo}/{datasetName}",
            arguments = listOf(
                navArgument("datasetId") { type = NavType.StringType },
                navArgument("period") { type = NavType.StringType },
                navArgument("orgUnit") { type = NavType.StringType },
                navArgument("attributeOptionCombo") { type = NavType.StringType },
                navArgument("datasetName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val datasetId = backStackEntry.arguments?.getString("datasetId") ?: ""
            val period = backStackEntry.arguments?.getString("period") ?: ""
            val orgUnit = backStackEntry.arguments?.getString("orgUnit") ?: ""
            val attributeOptionCombo = backStackEntry.arguments?.getString("attributeOptionCombo") ?: ""
            val datasetName = backStackEntry.arguments?.getString("datasetName") ?: ""
            val viewModel: DataEntryViewModel = hiltViewModel()
            viewModel.loadDataValues(datasetId, datasetName, period, orgUnit, attributeOptionCombo, isEditMode = true)
            EditEntryScreen(
                viewModel = viewModel,
                navController = navController
            )
        }
    }

    }

