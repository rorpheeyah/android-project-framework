package com.bizplay.features.login

import com.bizplay.core.mvi.UiEffect
import com.bizplay.core.mvi.UiEvent
import com.bizplay.core.mvi.UiState

data class LoginUiState(
    val companyCode: String = "",
    val userId: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    val errorMessage: String? = null,
) : UiState {
    val canSubmit: Boolean
        get() = !submitting && companyCode.isNotBlank() && userId.isNotBlank() && password.isNotBlank()
}

sealed interface LoginUiEvent : UiEvent {
    data class CompanyCodeChanged(val value: String) : LoginUiEvent
    data class UserIdChanged(val value: String) : LoginUiEvent
    data class PasswordChanged(val value: String) : LoginUiEvent
    data object Submit : LoginUiEvent
    data object DismissError : LoginUiEvent
}

sealed interface LoginUiEffect : UiEffect {
    /** Several accounts returned in [com.bizplay.core.model.LoginResponse] — show the picker. */
    data object NavigateToInstitutionPicker : LoginUiEffect

    /** Exactly one account or institution-selection was not required — proceed to home. */
    data object NavigateToHome : LoginUiEffect
}
