package com.bizplay.data.repo

import com.bizplay.core.model.LoginCredential
import com.bizplay.core.model.LoginResponse
import com.bizplay.core.repository.AuthRepository
import com.bizplay.data.api.dto.LoginRequestDto
import com.bizplay.data.api.dto.toDomain
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton-scoped implementation of [AuthRepository]. Auth must work *before*
 * LoggedInComponent exists, so this repo cannot live inside that component.
 *
 * For the demo, we don't hit a real network — see [FakeAuthBackend]. The shape
 * (Retrofit API + DTO → domain mapper) matches what a production wiring would do.
 */
@Singleton
internal class IpppAuthRepo @Inject constructor(
    private val backend: FakeAuthBackend,
) : AuthRepository {

    override suspend fun login(credential: LoginCredential): Result<LoginResponse> = runCatching {
        val request = LoginRequestDto(
            userId = credential.userId,
            encryptedPassword = credential.encryptedPassword,
            companyCode = credential.companyCode,
        )
        backend.login(request).toDomain()
    }

    override suspend fun logout(): Result<Unit> = runCatching {
        backend.logout()
    }
}
