package com.compass.features.auth.login

import androidx.lifecycle.viewModelScope
import com.compass.core.mvi.MviViewModel
import com.compass.core.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    preLoginPolicies: PreLoginPolicies,
) : MviViewModel<LoginState, LoginEvent, LoginEffect>(
    initial = LoginState(
        showInstitutionField = preLoginPolicies.capabilities.supportsInstitutionPicker,
        supportHotline = preLoginPolicies.supportContacts.hotlineNumber,
        supportEmail = preLoginPolicies.supportContacts.supportEmail,
    ),
) {

    override fun onEvent(event: LoginEvent) {
        when (event) {
            is LoginEvent.UserIdChanged -> setState { copy(userId = event.value, errorMessage = null) }
            is LoginEvent.PasswordChanged -> setState { copy(password = event.value, errorMessage = null) }
            is LoginEvent.InstitutionCodeChanged -> setState { copy(institutionCode = event.value) }
            LoginEvent.Submit -> submit()
            LoginEvent.ClearError -> setState { copy(errorMessage = null) }
        }
    }

    private fun submit() {
        val current = state.value
        if (!current.canSubmit) return

        setState { copy(isSubmitting = true, errorMessage = null) }

        viewModelScope.launch {
            runCatching {
                authRepository.login(
                    userId = current.userId.trim(),
                    password = current.password,
                    institutionCode = current.institutionCode.trim().takeIf { it.isNotBlank() },
                )
            }
                .onSuccess { response ->
                    setState { copy(isSubmitting = false) }
                    if (response.accounts.size > 1) {
                        emitEffect(LoginEffect.PickInstitution(response))
                    } else {
                        emitEffect(LoginEffect.LoginSucceeded(response))
                    }
                }
                .onFailure { e ->
                    setState {
                        copy(
                            isSubmitting = false,
                            errorMessage = e.message ?: "Login failed. Please try again.",
                        )
                    }
                }
        }
    }
}
