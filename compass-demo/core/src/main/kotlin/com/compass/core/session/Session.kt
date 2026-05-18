package com.compass.core.session

import com.compass.core.model.UserSession
import com.compass.core.variant.VariantContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Active logged-in session. Built once by SessionFactory, dropped on logout.
 *
 * Switching the active account is a value flip, not a DI rebuild:
 * the AccountIdInterceptor reads `activeAccountId.value` at each request.
 */
class Session(
    val userSession: UserSession,
    val variantContext: VariantContext,
    val accounts: List<DepartmentAccount>,
    initialActiveAccount: AccountId,
) {
    private val _activeAccountId = MutableStateFlow(initialActiveAccount)
    val activeAccountId: StateFlow<AccountId> = _activeAccountId.asStateFlow()

    fun switchAccount(target: AccountId) {
        require(accounts.any { it.id == target }) {
            "Account ${target.value} not in user's accounts"
        }
        _activeAccountId.value = target
    }

    val activeAccount: DepartmentAccount
        get() = accounts.first { it.id == activeAccountId.value }
}
