package com.compass.features.account.switcher

import androidx.lifecycle.viewModelScope
import com.compass.core.mvi.MviViewModel
import com.compass.core.session.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * In-session institution-switch. Mirrors the post-login portion of BizplayIPPP's
 * institution selection (`JOIN_USE_INTT_ID`): once logged in, the user can flip
 * between the institutions their single login is enrolled in.
 *
 * Switching is a value flip on `Session.activeAccountId` — no DI rebuild, no
 * network call. `AccountIdInterceptor` stamps the new id on the next request.
 */
@HiltViewModel
internal class AccountSwitcherViewModel @Inject constructor(
    private val session: Session,
) : MviViewModel<AccountSwitcherState, AccountSwitcherEvent, Nothing>(
    initial = AccountSwitcherState(
        accounts = session.accounts,
        activeId = session.activeAccountId.value,
    ),
) {

    init {
        viewModelScope.launch {
            session.activeAccountId.collect { active ->
                setState { copy(activeId = active) }
            }
        }
    }

    override fun onEvent(event: AccountSwitcherEvent) {
        when (event) {
            is AccountSwitcherEvent.SelectAccount -> session.switchAccount(event.id)
        }
    }
}
