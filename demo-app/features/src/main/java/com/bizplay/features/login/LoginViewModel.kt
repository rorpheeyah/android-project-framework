package com.bizplay.features.login

import androidx.lifecycle.viewModelScope
import com.bizplay.core.boot.BootCoordinator
import com.bizplay.core.model.LoginCredential
import com.bizplay.core.mvi.MviViewModel
import com.bizplay.core.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Logic-Blind: depends on the :core [AuthRepository] interface and the :core
 * [BootCoordinator] gateway. It does not know which backend is hit, which variant
 * the user belongs to, or what the LoggedInComponent looks like.
 *
 * Mirrors the existing `LoginViewModel` two-step flow:
 *   1. Submit credentials → AuthRepository.login()
 *   2. Hand the response to BootCoordinator.onLoginSuccess() to build the session
 *   3. Route to the institution picker if multiple accounts, otherwise straight home
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val coordinator: BootCoordinator,
) : MviViewModel<LoginUiState, LoginUiEvent, LoginUiEffect>(LoginUiState()) {

    override fun onEvent(event: LoginUiEvent) {
        when (event) {
            is LoginUiEvent.CompanyCodeChanged -> setState { copy(companyCode = event.value, errorMessage = null) }
            is LoginUiEvent.UserIdChanged -> setState { copy(userId = event.value, errorMessage = null) }
            is LoginUiEvent.PasswordChanged -> setState { copy(password = event.value, errorMessage = null) }
            LoginUiEvent.DismissError -> setState { copy(errorMessage = null) }
            LoginUiEvent.Submit -> submit()
        }
    }

    private fun submit() {
        val s = currentState()
        if (!s.canSubmit) return
        viewModelScope.launch {
            setState { copy(submitting = true, errorMessage = null) }

            // In production, the password is encrypted by the SecureKeypad (TransKey)
            // before this point. The demo passes it through unchanged so the offline
            // fake backend can match credentials.
            val credential = LoginCredential(
                userId = s.userId.trim(),
                encryptedPassword = s.password,
                companyCode = s.companyCode.trim(),
            )

            authRepository.login(credential)
                .onFailure { ex ->
                    setState { copy(submitting = false, errorMessage = ex.message ?: "Login failed.") }
                }
                .onSuccess { response ->
                    coordinator.onLoginSuccess(response)
                        .onFailure { ex ->
                            setState { copy(submitting = false, errorMessage = ex.message ?: "Session build failed.") }
                        }
                        .onSuccess {
                            setState { copy(submitting = false) }
                            if (response.requiresInstitutionSelection && response.accounts.size > 1) {
                                emitEffect(LoginUiEffect.NavigateToInstitutionPicker)
                            } else {
                                emitEffect(LoginUiEffect.NavigateToHome)
                            }
                        }
                }
        }
    }
}
