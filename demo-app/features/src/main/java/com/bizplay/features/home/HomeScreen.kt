package com.bizplay.features.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bizplay.design.components.BizButton
import com.bizplay.design.components.BizOutlinedButton
import com.bizplay.design.components.BizScaffold

@Composable
fun HomeScreen(
    onSwitchInstitution: () -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                HomeUiEffect.NavigateToInstitutionSwitcher -> onSwitchInstitution()
                HomeUiEffect.NavigateToLogin -> onLogout()
            }
        }
    }

    BizScaffold(title = "Bizplay · Home") { padding ->
        HomeContent(
            state = state,
            padding = padding,
            onSwitch = { viewModel.onEvent(HomeUiEvent.SwitchInstitution) },
            onLogout = { viewModel.onEvent(HomeUiEvent.Logout) },
        )
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    padding: PaddingValues,
    onSwitch: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Welcome, ${state.userDisplayName}",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Variant ${state.variantName} · Tenant ${state.tenantName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = "Active institution", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = state.activeAccount?.displayName ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = state.activeAccount?.let { "${it.divisionName} · ${it.companyCode}" }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BizOutlinedButton(
                    text = "Switch institution (${state.accounts.size})",
                    onClick = onSwitch,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.accounts.size > 1,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = "Variant capabilities", style = MaterialTheme.typography.labelMedium)
                Text(text = "Hi-Pass tracking: ${state.supportsHipassTracking.yesNo()}")
                Text(text = "MyData aggregation: ${state.supportsMyDataAggregation.yesNo()}")
                Text(
                    text = "UI reads these as booleans — never as `if (variantId == \"kr\")`.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        BizButton(
            text = "Log out",
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun Boolean.yesNo(): String = if (this) "yes" else "no"
