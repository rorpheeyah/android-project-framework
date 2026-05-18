package com.bizplay.features.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bizplay.design.components.BizButton
import com.bizplay.design.components.BizScaffold
import com.bizplay.design.components.BizTextField

@Composable
fun LoginScreen(
    onNavigateToInstitutionPicker: () -> Unit,
    onNavigateToHome: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                LoginUiEffect.NavigateToInstitutionPicker -> onNavigateToInstitutionPicker()
                LoginUiEffect.NavigateToHome -> onNavigateToHome()
            }
        }
    }

    BizScaffold(title = "Sign in") { padding ->
        LoginContent(
            state = state,
            modifier = Modifier.padding(padding),
            onEvent = viewModel::onEvent,
        )
    }
}

@Composable
private fun LoginContent(
    state: LoginUiState,
    onEvent: (LoginUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Welcome to Bizplay",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Use one of the demo logins below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        BizTextField(
            value = state.companyCode,
            onValueChange = { onEvent(LoginUiEvent.CompanyCodeChanged(it)) },
            label = "Company code",
            modifier = Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Ascii,
            enabled = !state.submitting,
        )
        BizTextField(
            value = state.userId,
            onValueChange = { onEvent(LoginUiEvent.UserIdChanged(it)) },
            label = "User ID",
            modifier = Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Ascii,
            enabled = !state.submitting,
        )
        BizTextField(
            value = state.password,
            onValueChange = { onEvent(LoginUiEvent.PasswordChanged(it)) },
            label = "Password",
            modifier = Modifier.fillMaxWidth(),
            isPassword = true,
            enabled = !state.submitting,
            errorText = state.errorMessage,
        )

        BizButton(
            text = "Sign in",
            onClick = { onEvent(LoginUiEvent.Submit) },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canSubmit,
            loading = state.submitting,
        )

        Text(
            text = "Demo credentials:\n" +
                "  · Company: BIZPLAY  ·  ID: demo  ·  PW: demo1234   (3 institutions)\n" +
                "  · Company: BIZPLAY  ·  ID: single ·  PW: single1234 (1 institution, skips picker)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
