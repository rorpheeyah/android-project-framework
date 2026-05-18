package com.bizplay.features.selectinstitution

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bizplay.core.session.DepartmentAccount
import com.bizplay.design.components.BizButton
import com.bizplay.design.components.BizScaffold

@Composable
fun SelectInstitutionScreen(
    onNavigateToHome: () -> Unit,
    viewModel: SelectInstitutionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SelectInstitutionUiEffect.NavigateToHome -> onNavigateToHome()
            }
        }
    }

    val title = when (state.mode) {
        SelectInstitutionUiState.Mode.PostLogin -> "Choose your institution"
        SelectInstitutionUiState.Mode.InSessionSwitch -> "Switch institution"
    }

    BizScaffold(title = title) { padding ->
        SelectInstitutionContent(
            state = state,
            modifier = Modifier.padding(padding),
            onPick = { viewModel.onEvent(SelectInstitutionUiEvent.Pick(it.id)) },
            onConfirm = { viewModel.onEvent(SelectInstitutionUiEvent.Confirm) },
        )
    }
}

@Composable
private fun SelectInstitutionContent(
    state: SelectInstitutionUiState,
    onPick: (DepartmentAccount) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "You belong to ${state.accounts.size} institutions. " +
                "Pick the one to act as for this session — switch any time from Home.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = state.accounts, key = { it.id.value }) { account ->
                InstitutionRow(
                    account = account,
                    selected = account.id == state.selected,
                    onClick = { onPick(account) },
                )
            }
        }

        BizButton(
            text = "Confirm",
            onClick = onConfirm,
            enabled = state.canConfirm,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun InstitutionRow(
    account: DepartmentAccount,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = account.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${account.divisionName} · ${account.companyCode}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RadioButton(
                selected = selected,
                onClick = onClick,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}
