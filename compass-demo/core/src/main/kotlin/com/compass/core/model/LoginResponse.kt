package com.compass.core.model

import com.compass.core.session.DepartmentAccount
import com.compass.core.variant.VariantId

/**
 * Domain-shaped login result. `:data` maps the wire DTO to this; `:features`
 * and `:app` only see this shape.
 */
data class LoginResponse(
    val userSession: UserSession,
    val accounts: List<DepartmentAccount>,
    val variantId: VariantId,
)
