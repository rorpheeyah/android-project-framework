package com.bizplay.core.tenant

/**
 * Corporate-customer boundary inside a variant (POSCO, Lotte, NIA, default…).
 * Captured at login from LoginResponse. UI reads [TenantFlags] / [TenantParams] —
 * never branches on [TenantId].
 */
@JvmInline
value class TenantId(val value: String) {
    companion object {
        val DEFAULT = TenantId("default")
    }
}

/**
 * Named-boolean feature toggles. Replaces the existing
 * `DetailConfig.isNIA() / isPOSCO_ICT() / isWIPS() / …` chains with a static schema:
 * code reads `tenant.flags.hidesEmployeeId`, not `tenant.id == "nia"`.
 */
data class TenantFlags(
    val hidesEmployeeId: Boolean = false,
    val requiresApprovalForEveryReceipt: Boolean = false,
    val showsCorporateLogo: Boolean = true,
    val allowsPersonalCardLink: Boolean = true,
)

/**
 * Named typed parameters (numbers, strings, durations) that vary per tenant.
 * Owned by the client schema; values come from MgGate + login response.
 */
data class TenantParams(
    val maxReceiptAmountMinor: Long = Long.MAX_VALUE,
    val supportPhone: String = "",
    val supportEmail: String = "",
)

/**
 * Immutable snapshot of the tenant for the logged-in user. Bound once and dropped at logout.
 */
data class TenantContext(
    val id: TenantId,
    val displayName: String,
    val flags: TenantFlags,
    val params: TenantParams,
)
