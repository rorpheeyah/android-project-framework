package com.bizplay.core.repository

import com.bizplay.core.model.LoginCredential
import com.bizplay.core.model.LoginResponse

/**
 * Singleton-scoped: callable **before** LoggedInComponent exists.
 * The :data module supplies [com.bizplay.data.repo.IpppAuthRepo] as the implementation.
 *
 * Variant-agnostic: there is exactly one backend, demuxed server-side by user + company code.
 */
interface AuthRepository {
    suspend fun login(credential: LoginCredential): Result<LoginResponse>

    /** Optional sign-out: revokes the access token on the server. */
    suspend fun logout(): Result<Unit>
}
