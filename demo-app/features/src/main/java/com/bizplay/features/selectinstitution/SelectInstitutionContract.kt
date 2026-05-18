package com.bizplay.features.selectinstitution

import com.bizplay.core.mvi.UiEffect
import com.bizplay.core.mvi.UiEvent
import com.bizplay.core.mvi.UiState
import com.bizplay.core.session.AccountId
import com.bizplay.core.session.DepartmentAccount

data class SelectInstitutionUiState(
    val accounts: List<DepartmentAccount> = emptyList(),
    val selected: AccountId? = null,
    val mode: Mode = Mode.PostLogin,
) : UiState {
    val canConfirm: Boolean get() = selected != null && accounts.any { it.id == selected }

    /** Distinguishes the post-login picker (`CRTC_PATH == "C006"`) from the in-session switcher. */
    enum class Mode { PostLogin, InSessionSwitch }
}

sealed interface SelectInstitutionUiEvent : UiEvent {
    data class Pick(val accountId: AccountId) : SelectInstitutionUiEvent
    data object Confirm : SelectInstitutionUiEvent
}

sealed interface SelectInstitutionUiEffect : UiEffect {
    data object NavigateToHome : SelectInstitutionUiEffect
}
