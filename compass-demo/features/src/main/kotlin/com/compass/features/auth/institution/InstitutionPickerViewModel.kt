package com.compass.features.auth.institution

import com.compass.core.mvi.MviViewModel
import com.compass.core.session.DepartmentAccount

/**
 * Pre-login institution picker. Mirrors BizplayIPPP's `SelectUserInttIdActivity`:
 * after primary auth returns multiple `USE_INTT_ID`s (institutions the same login
 * is enrolled in), the user chooses one before navigation enters the session.
 *
 * Not @HiltViewModel — receives the candidate accounts directly from nav args.
 * The orchestrator (`:app`) constructs it with the list from LoginResponse.
 */
internal class InstitutionPickerViewModel(
    accounts: List<DepartmentAccount>,
) : MviViewModel<InstitutionPickerState, InstitutionPickerEvent, InstitutionPickerEffect>(
    initial = InstitutionPickerState(
        accounts = accounts,
        selectedId = accounts.firstOrNull()?.id,
    ),
) {

    override fun onEvent(event: InstitutionPickerEvent) {
        when (event) {
            is InstitutionPickerEvent.Select -> setState { copy(selectedId = event.id) }
            InstitutionPickerEvent.Confirm -> {
                val active = state.value.selectedId ?: return
                emitEffect(InstitutionPickerEffect.Confirmed(active))
            }
        }
    }
}
