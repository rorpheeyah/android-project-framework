package com.compass.features.auth.login

import com.compass.core.model.LoginResponse
import com.compass.core.mvi.UiEffect
import com.compass.core.mvi.UiEvent
import com.compass.core.mvi.UiState

internal data class LoginState(
    val userId: String = "",
    val password: String = "",
    val institutionCode: String = "",
    val showInstitutionField: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val supportHotline: String = "",
    val supportEmail: String = "",
) : UiState {
    val canSubmit: Boolean
        get() = userId.isNotBlank() && password.isNotBlank() && !isSubmitting
}

internal sealed interface LoginEvent : UiEvent {
    data class UserIdChanged(val value: String) : LoginEvent
    data class PasswordChanged(val value: String) : LoginEvent
    data class InstitutionCodeChanged(val value: String) : LoginEvent
    data object Submit : LoginEvent
    data object ClearError : LoginEvent
}

internal sealed interface LoginEffect : UiEffect {
    /** Primary auth returned multiple departments — pick one. */
    data class PickInstitution(val response: LoginResponse) : LoginEffect

    /** Primary auth returned a single department — go straight to main. */
    data class LoginSucceeded(val response: LoginResponse) : LoginEffect
}
