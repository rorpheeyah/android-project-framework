package com.compass.data.api.dto.auth

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class LoginResponseDto(
    val userId: String,
    val displayName: String,
    val accessToken: String,
    val variantId: String,
    val accounts: List<AccountDto>,
)

@JsonClass(generateAdapter = true)
internal data class AccountDto(
    val accountId: String,
    val displayName: String,
    val institutionCode: String,
    val accountType: String,
    val currency: String,
)
