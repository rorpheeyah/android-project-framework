package com.compass.data.api.dto.auth

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class LoginRequestDto(
    val userId: String,
    val password: String,
    val institutionCode: String? = null,
)
