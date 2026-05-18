package com.compass.features.auth.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.compass.core.model.LoginResponse
import com.compass.design.components.CompassPasswordField
import com.compass.design.components.CompassPrimaryButton
import com.compass.design.components.CompassTextField

@Composable
fun LoginScreen(
    onLoginSucceeded: (LoginResponse) -> Unit,
    onPickInstitution: (LoginResponse) -> Unit,
) {
    val viewModel: LoginViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LoginEffect.LoginSucceeded -> onLoginSucceeded(effect.response)
                is LoginEffect.PickInstitution -> onPickInstitution(effect.response)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(48.dp))

        Text(
            text = "Welcome back",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "Sign in to continue",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        CompassTextField(
            value = state.userId,
            onValueChange = { viewModel.onEvent(LoginEvent.UserIdChanged(it)) },
            label = "User ID",
            placeholder = "e.g. demo.user",
            enabled = !state.isSubmitting,
        )

        CompassPasswordField(
            value = state.password,
            onValueChange = { viewModel.onEvent(LoginEvent.PasswordChanged(it)) },
            label = "Password",
            enabled = !state.isSubmitting,
        )

        if (state.showInstitutionField) {
            CompassTextField(
                value = state.institutionCode,
                onValueChange = { viewModel.onEvent(LoginEvent.InstitutionCodeChanged(it)) },
                label = "Institution code (optional)",
                placeholder = "Leave blank to pick after login",
                enabled = !state.isSubmitting,
            )
        }

        state.errorMessage?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(8.dp))

        CompassPrimaryButton(
            text = "Sign In",
            onClick = { viewModel.onEvent(LoginEvent.Submit) },
            enabled = state.canSubmit,
            loading = state.isSubmitting,
        )

        Spacer(Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Need help?",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = state.supportHotline,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = state.supportEmail,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
