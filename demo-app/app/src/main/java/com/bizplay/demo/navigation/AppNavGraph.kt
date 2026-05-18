package com.bizplay.demo.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bizplay.features.boot.BootScreen
import com.bizplay.features.home.HomeScreen
import com.bizplay.features.login.LoginScreen
import com.bizplay.features.selectinstitution.SelectInstitutionScreen
import com.bizplay.features.selectinstitution.SelectInstitutionUiState

@Composable
fun AppNavGraph(navController: NavHostController = rememberNavController()) {

    NavHost(navController = navController, startDestination = Routes.BOOT) {

        composable(Routes.BOOT) {
            BootScreen(
                onNavigateToLogin = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.BOOT) { inclusive = true }
                    }
                },
                onOpenStore = { /* In a real app, launch ACTION_VIEW. Demo: no-op. */ },
                onExitApp = { /* Demo: no-op. Production would call ActivityCompat.finishAffinity. */ },
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onNavigateToInstitutionPicker = {
                    navController.navigate(
                        Routes.selectInstitution(SelectInstitutionUiState.Mode.PostLogin.name),
                    ) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Routes.SELECT_INSTITUTION,
            arguments = listOf(navArgument(Routes.ARG_MODE) { type = NavType.StringType }),
        ) {
            SelectInstitutionScreen(
                onNavigateToHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SELECT_INSTITUTION) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onSwitchInstitution = {
                    navController.navigate(
                        Routes.selectInstitution(SelectInstitutionUiState.Mode.InSessionSwitch.name),
                    )
                },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
    }
}
