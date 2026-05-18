package com.compass.data.api

import com.compass.data.api.dto.auth.LoginRequestDto
import com.compass.data.api.dto.auth.LoginResponseDto
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Auth slice of the unified Fintech API. One Retrofit interface per feature area
 * (Auth, Transfer, Account, ...) for ergonomic grouping — but a single set of
 * DTOs and a single set of impls regardless of variant. The server demuxes per user.
 */
internal interface FintechAuthApi {

    @POST("/v1/auth/login")
    suspend fun login(@Body body: LoginRequestDto): LoginResponseDto

    @POST("/v1/auth/logout")
    suspend fun logout()
}
