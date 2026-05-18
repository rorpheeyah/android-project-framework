package com.compass.core.repository

import com.compass.core.model.LoginResponse

/**
 * `:features` calls this; `:data` implements it. No variant-specific shape
 * — the backend already demuxes per user.
 */
interface AuthRepository {

    suspend fun login(userId: String, password: String, institutionCode: String?): LoginResponse

    suspend fun logout()
}
