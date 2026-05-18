package com.compass.features.auth.institution

import com.compass.core.mvi.UiEffect
import com.compass.core.mvi.UiEvent
import com.compass.core.mvi.UiState
import com.compass.core.session.AccountId
import com.compass.core.session.DepartmentAccount

internal data class InstitutionPickerState(
    val accounts: List<DepartmentAccount> = emptyList(),
    val selectedId: AccountId? = null,
) : UiState {
    val canConfirm: Boolean get() = selectedId != null
}

internal sealed interface InstitutionPickerEvent : UiEvent {
    data class Select(val id: AccountId) : InstitutionPickerEvent
    data object Confirm : InstitutionPickerEvent
}

internal sealed interface InstitutionPickerEffect : UiEffect {
    data class Confirmed(val activeAccountId: AccountId) : InstitutionPickerEffect
}
