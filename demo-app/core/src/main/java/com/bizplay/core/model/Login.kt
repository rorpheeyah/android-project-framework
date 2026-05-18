package com.bizplay.core.model

import com.bizplay.core.session.DepartmentAccount
import com.bizplay.core.tenant.TenantContext
import com.bizplay.core.variant.VariantId

/**
 * Inputs to AuthRepository.login(). Plaintext password lives in memory only long enough
 * to be encrypted by SecureKeypad before the network call; never persisted.
 */
data class LoginCredential(
    val userId: String,
    val encryptedPassword: String,
    val companyCode: String,
)

/**
 * Outcome of a successful login. Carries everything BootCoordinator needs to build
 * the LoggedInComponent — variant, tenant, user session, and the institution list.
 *
 * Note: variant and tenant fields come from the server. The client never decides them.
 */
data class LoginResponse(
    val user: UserSession,
    val variantId: VariantId,
    val tenant: TenantContext,
    val accounts: List<DepartmentAccount>,
    /** Hint from the server that a pre-login institution picker is required (e.g. CRTC_PATH="C006"). */
    val requiresInstitutionSelection: Boolean,
)
