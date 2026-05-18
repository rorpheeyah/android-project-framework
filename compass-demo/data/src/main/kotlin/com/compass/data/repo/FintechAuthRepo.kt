package com.compass.data.repo

import com.compass.core.model.LoginResponse
import com.compass.core.repository.AuthRepository
import com.compass.core.scope.LoggedInScoped
import com.compass.data.api.FintechAuthApi
import com.compass.data.api.dto.auth.LoginRequestDto
import com.compass.data.repo.mapping.toDomain
import javax.inject.Inject

/**
 * One repo per `:core` interface, variant-agnostic. The active variant is
 * discovered server-side from the user's credentials and returned in the
 * login response — the client never has to "know" which bank it's talking to.
 */
@LoggedInScoped
internal class FintechAuthRepo @Inject constructor(
    private val api: FintechAuthApi,
) : AuthRepository {

    override suspend fun login(
        userId: String,
        password: String,
        institutionCode: String?,
    ): LoginResponse =
        api.login(LoginRequestDto(userId, password, institutionCode)).toDomain()

    override suspend fun logout() {
        runCatching { api.logout() }
    }
}
