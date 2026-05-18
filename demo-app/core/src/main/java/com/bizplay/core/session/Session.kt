package com.bizplay.core.session

import com.bizplay.core.model.UserSession
import com.bizplay.core.tenant.TenantContext
import com.bizplay.core.variant.VariantContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Identifier of a single institution membership.
 * Maps to the existing Bizplay `USE_INTT_ID` field; stamped on every authenticated request
 * by AccountIdInterceptor.
 */
@JvmInline
value class AccountId(val value: String)

/**
 * One institution the logged-in user belongs to. All accounts share the same
 * auth token, variant, and tenant; only [id] / [companyCode] differ per request.
 */
data class DepartmentAccount(
    val id: AccountId,
    val displayName: String,
    val companyCode: String,
    val divisionCode: String,
    val divisionName: String,
)

/**
 * Multi-institution session model. The variant / tenant bind once at login;
 * the **active institution** can be switched in-session via [switchAccount]
 * — no DI rebuild, no purge.
 */
class Session(
    val user: UserSession,
    val variant: VariantContext,
    val tenant: TenantContext,
    val accounts: List<DepartmentAccount>,
    initialActiveAccountId: AccountId,
) {
    init {
        require(accounts.isNotEmpty()) { "Session must contain at least one DepartmentAccount" }
        require(accounts.any { it.id == initialActiveAccountId }) {
            "initialActiveAccountId=$initialActiveAccountId not in accounts"
        }
    }

    private val _activeAccountId = MutableStateFlow(initialActiveAccountId)
    val activeAccountId: StateFlow<AccountId> = _activeAccountId.asStateFlow()

    val activeAccount: DepartmentAccount
        get() = accounts.first { it.id == _activeAccountId.value }

    fun switchAccount(target: AccountId) {
        require(accounts.any { it.id == target }) { "Unknown account: $target" }
        _activeAccountId.value = target
    }
}
