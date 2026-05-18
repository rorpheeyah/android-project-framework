package com.bizplay.data.api

import com.bizplay.data.api.dto.LoginRequestDto
import com.bizplay.data.api.dto.LoginResponseDto
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit surface for the auth feature area against the unified IPPP backend.
 *
 * Per framework rule #4: there is exactly **one** Retrofit interface family
 * (`Ippp*Api`), variant-agnostic. Per-variant or per-tenant API interfaces are
 * forbidden — the backend demuxes by user + company code.
 *
 * `internal` because the only intentionally public surface of :data is its Hilt module.
 */
internal interface IpppAuthApi {

    @POST("v1/auth/login")
    suspend fun login(@Body request: LoginRequestDto): LoginResponseDto

    @POST("v1/auth/logout")
    suspend fun logout(): Unit
}
