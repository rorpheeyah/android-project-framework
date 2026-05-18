package com.compass.features.boot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.compass.core.runtime.ForceUpdate
import com.compass.core.runtime.MaintenanceState
import com.compass.design.components.CompassLoadingScreen
import com.compass.design.components.CompassRetryScreen

@Composable
fun BootScreen(
    onReady: () -> Unit,
    onMaintenance: (MaintenanceState.Down) -> Unit,
    onForceUpdate: (ForceUpdate) -> Unit,
) {
    val viewModel: BootViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                BootEffect.Ready -> onReady()
                is BootEffect.Maintenance -> onMaintenance(effect.state)
                is BootEffect.ForceUpdateRequired -> onForceUpdate(effect.forceUpdate)
            }
        }
    }

    when (state.phase) {
        BootPhase.Loading -> CompassLoadingScreen(message = "Checking for updates…")
        BootPhase.Failed -> CompassRetryScreen(
            message = state.errorMessage,
            onRetry = { viewModel.onEvent(BootEvent.Retry) },
        )
    }
}
