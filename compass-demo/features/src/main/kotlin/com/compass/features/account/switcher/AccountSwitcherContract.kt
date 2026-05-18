package com.compass.features.account.switcher

import com.compass.core.mvi.UiEvent
import com.compass.core.mvi.UiState
import com.compass.core.session.AccountId
import com.compass.core.session.DepartmentAccount

internal data class AccountSwitcherState(
    val accounts: List<DepartmentAccount> = emptyList(),
    val activeId: AccountId? = null,
) : UiState

internal sealed interface AccountSwitcherEvent : UiEvent {
    data class SelectAccount(val id: AccountId) : AccountSwitcherEvent
}
