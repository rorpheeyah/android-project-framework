package com.compass.features.auth.institution

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.compass.core.model.LoginResponse
import com.compass.core.session.AccountId
import com.compass.core.session.DepartmentAccount
import com.compass.design.components.CompassPrimaryButton

/**
 * Pre-login institution picker. Receives the full LoginResponse so it knows which
 * accounts are available; calls back with the selected AccountId for the orchestrator
 * to commit as `Session.activeAccountId`.
 */
@Composable
fun InstitutionPickerScreen(
    loginResponse: LoginResponse,
    onConfirm: (AccountId) -> Unit,
) {
    val viewModel = remember(loginResponse) {
        InstitutionPickerViewModel(loginResponse.accounts)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is InstitutionPickerEffect.Confirmed -> onConfirm(effect.activeAccountId)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Choose institution",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "Your login is enrolled in multiple institutions. Pick the one you'd like to use this session.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.accounts, key = { it.id.value }) { account ->
                InstitutionRow(
                    account = account,
                    selected = account.id == state.selectedId,
                    onSelect = { viewModel.onEvent(InstitutionPickerEvent.Select(account.id)) },
                )
            }
        }

        Spacer(Modifier.padding(top = 4.dp))

        CompassPrimaryButton(
            text = "Continue",
            onClick = { viewModel.onEvent(InstitutionPickerEvent.Confirm) },
            enabled = state.canConfirm,
        )
    }
}

@Composable
private fun InstitutionRow(
    account: DepartmentAccount,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect),
        colors = CardDefaults.cardColors(),
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(modifier = Modifier.weight(1f)) {
                Text(account.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${account.institutionCode} · ${account.accountType} · ${account.currency.isoCode}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
