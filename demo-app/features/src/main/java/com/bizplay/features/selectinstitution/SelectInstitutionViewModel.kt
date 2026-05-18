package com.bizplay.features.selectinstitution

import androidx.lifecycle.SavedStateHandle
import com.bizplay.core.boot.BootCoordinator
import com.bizplay.core.mvi.MviViewModel
import com.bizplay.core.session.SessionHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

const val NAV_ARG_MODE = "mode"

/**
 * Backs both the post-login institution picker (mirrors the existing
 * `SelectUserInttIdActivity` shown when `CRTC_PATH == "C006"` or the response
 * contains multiple `USE_INTT_ID_REC` rows) and the in-session switcher
 * launched from Home. The two flows are distinguished by a nav argument.
 *
 * Post-login: confirming calls [BootCoordinator.finalizeLogin] which writes the
 * active account id and lets the navigation graph move on to Home.
 * In-session: confirming calls [com.bizplay.core.session.Session.switchAccount]
 * directly — no DI rebuild, just a StateFlow flip that downstream ViewModels
 * collect.
 */
@HiltViewModel
class SelectInstitutionViewModel @Inject constructor(
    private val sessionHolder: SessionHolder,
    private val coordinator: BootCoordinator,
    savedStateHandle: SavedStateHandle,
) : MviViewModel<SelectInstitutionUiState, SelectInstitutionUiEvent, SelectInstitutionUiEffect>(
    initial = run {
        val session = sessionHolder.current
        val modeName = savedStateHandle.get<String>(NAV_ARG_MODE)
            ?: SelectInstitutionUiState.Mode.PostLogin.name
        SelectInstitutionUiState(
            accounts = session.accounts,
            selected = session.activeAccountId.value,
            mode = SelectInstitutionUiState.Mode.valueOf(modeName),
        )
    },
) {

    override fun onEvent(event: SelectInstitutionUiEvent) {
        when (event) {
            is SelectInstitutionUiEvent.Pick -> setState { copy(selected = event.accountId) }
            SelectInstitutionUiEvent.Confirm -> confirm()
        }
    }

    private fun confirm() {
        val state = currentState()
        val target = state.selected ?: return
        when (state.mode) {
            SelectInstitutionUiState.Mode.PostLogin -> coordinator.finalizeLogin(target)
            SelectInstitutionUiState.Mode.InSessionSwitch -> sessionHolder.current.switchAccount(target)
        }
        emitEffect(SelectInstitutionUiEffect.NavigateToHome)
    }
}
