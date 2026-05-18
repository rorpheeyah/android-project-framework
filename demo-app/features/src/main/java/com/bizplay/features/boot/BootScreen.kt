package com.bizplay.features.boot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bizplay.design.components.BizButton
import com.bizplay.design.components.BizLoadingIndicator
import com.bizplay.design.components.BizOutlinedButton

@Composable
fun BootScreen(
    onNavigateToLogin: () -> Unit,
    onOpenStore: (String) -> Unit,
    onExitApp: () -> Unit,
    viewModel: BootViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                BootUiEffect.NavigateToLogin -> onNavigateToLogin()
                is BootUiEffect.OpenStore -> onOpenStore(effect.url)
                BootUiEffect.ExitApp -> onExitApp()
            }
        }
    }

    BootContent(
        state = state,
        onRetry = { viewModel.onEvent(BootUiEvent.Retry) },
        onOpenStore = onOpenStore,
        onExitApp = onExitApp,
    )
}

@Composable
private fun BootContent(
    state: BootUiState,
    onRetry: () -> Unit,
    onOpenStore: (String) -> Unit,
    onExitApp: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state.phase) {
            BootUiState.Phase.SecurityCheck,
            BootUiState.Phase.MgFetch,
            BootUiState.Phase.Ready,
            -> BizLoadingIndicator(message = state.message.ifBlank { "Starting…" })

            BootUiState.Phase.GateBlocked -> GateBlockedColumn(
                title = if (state.upgrade != null) "Update required" else "Service unavailable",
                body = state.upgrade?.let {
                    "This version is no longer supported. " +
                        "Update to continue using the app."
                } ?: state.maintenance?.state?.message
                    ?: "The service is temporarily down. Please try again later.",
                primaryLabel = if (state.upgrade != null) "Open store" else "Close app",
                onPrimary = {
                    state.upgrade?.let { onOpenStore(it.forceUpdate.storeUrl) } ?: onExitApp()
                },
            )

            BootUiState.Phase.Failed -> GateBlockedColumn(
                title = "Cannot reach MgGate",
                body = state.errorReason ?: "Check your connection and try again.",
                primaryLabel = "Retry",
                onPrimary = onRetry,
            )
        }
    }
}

@Composable
private fun GateBlockedColumn(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
            BizButton(text = primaryLabel, onClick = onPrimary)
            BizOutlinedButton(text = "Dismiss", onClick = {})
        }
    }
}
