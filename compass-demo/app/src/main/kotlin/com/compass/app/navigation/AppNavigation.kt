package com.compass.app.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.compass.app.boot.BootCoordinator
import com.compass.app.session.LoggedInComponentManager
import com.compass.app.session.LogoutHandler
import com.compass.core.runtime.ForceUpdate
import com.compass.core.runtime.MaintenanceState
import com.compass.core.session.AccountId
import com.compass.features.account.switcher.AccountSwitcherScreen
import com.compass.features.auth.institution.InstitutionPickerScreen
import com.compass.features.auth.login.LoginScreen
import com.compass.features.boot.BootScreen
import com.compass.features.boot.ForceUpdateGate
import com.compass.features.boot.MaintenanceGate
import com.compass.features.main.HomeScreen
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import android.content.Context

/**
 * Single navigation graph. The routes mirror the framework's boot phases:
 *
 *  Boot ──► Login ──► InstitutionPicker ──► Home ──► AccountSwitcher
 *    │
 *    ├──► Maintenance (hard stop)
 *    └──► ForceUpdate (hard stop, opens store)
 *
 * Logout pops back to Login by observing the LoggedInComponentManager state.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface AppNavigationEntryPoint {
    fun bootCoordinator(): BootCoordinator
    fun componentManager(): LoggedInComponentManager
    fun logoutHandler(): LogoutHandler
}

@Composable
internal fun AppNavigation() {
    val context = LocalContext.current
    val entryPoint = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppNavigationEntryPoint::class.java,
        )
    }
    val coordinator = entryPoint.bootCoordinator()
    val componentManager = entryPoint.componentManager()
    val logoutHandler = entryPoint.logoutHandler()

    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val loginFlow = remember { LoginFlowHolder() }
    val activeSession by componentManager.current.collectAsState()

    LaunchedEffect(activeSession) {
        if (activeSession == null && navController.currentDestination?.route !in setOf(
                Routes.Boot, Routes.Login, Routes.InstitutionPicker,
                Routes.Maintenance, Routes.ForceUpdate, null,
            )
        ) {
            navController.navigate(Routes.Login) {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.Boot) {

        composable(Routes.Boot) {
            BootScreen(
                onReady = {
                    navController.navigate(Routes.Login) {
                        popUpTo(Routes.Boot) { inclusive = true }
                    }
                },
                onMaintenance = {
                    navController.navigate(Routes.Maintenance) {
                        popUpTo(Routes.Boot) { inclusive = true }
                    }
                },
                onForceUpdate = {
                    navController.navigate(Routes.ForceUpdate) {
                        popUpTo(Routes.Boot) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.Maintenance) {
            val state = MaintenanceState.Down("Service is temporarily unavailable.")
            MaintenanceGate(state)
        }

        composable(Routes.ForceUpdate) {
            val forceUpdate = ForceUpdate(
                minVersionCode = 0,
                storeUrl = "https://play.google.com/store/apps/details?id=com.compass.app",
                message = "A new required update is available.",
            )
            ForceUpdateGate(
                forceUpdate = forceUpdate,
                onOpenStore = { url -> openExternal(context, url) },
            )
        }

        composable(Routes.Login) {
            LoginScreen(
                onLoginSucceeded = { response ->
                    loginFlow.pendingLogin = response
                    val onlyAccount = response.accounts.single().id
                    scope.launch {
                        coordinator.onLoginSuccess(response, onlyAccount)
                        loginFlow.pendingLogin = null
                        navController.navigate(Routes.Home) {
                            popUpTo(Routes.Login) { inclusive = true }
                        }
                    }
                },
                onPickInstitution = { response ->
                    loginFlow.pendingLogin = response
                    navController.navigate(Routes.InstitutionPicker)
                },
            )
        }

        composable(Routes.InstitutionPicker) {
            val pending = loginFlow.pendingLogin
            if (pending == null) {
                LaunchedEffect(Unit) {
                    navController.navigate(Routes.Login) {
                        popUpTo(Routes.InstitutionPicker) { inclusive = true }
                    }
                }
            } else {
                InstitutionPickerScreen(
                    loginResponse = pending,
                    onConfirm = { selected: AccountId ->
                        scope.launch {
                            coordinator.onLoginSuccess(pending, selected)
                            loginFlow.pendingLogin = null
                            navController.navigate(Routes.Home) {
                                popUpTo(Routes.Login) { inclusive = true }
                            }
                        }
                    },
                )
            }
        }

        composable(Routes.Home) {
            HomeScreen(
                onOpenSwitcher = { navController.navigate(Routes.AccountSwitcher) },
                onLogout = {
                    scope.launch { logoutHandler.logout() }
                },
            )
        }

        composable(Routes.AccountSwitcher) {
            AccountSwitcherScreen()
        }
    }
}

private fun openExternal(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(intent)
}
