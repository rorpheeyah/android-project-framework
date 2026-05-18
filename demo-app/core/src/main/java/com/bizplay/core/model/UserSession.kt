package com.bizplay.core.model

/**
 * Identity + auth token for the logged-in user. One instance per active Session;
 * shared across all department accounts the user holds.
 */
data class UserSession(
    val userId: String,
    val displayName: String,
    val accessToken: String,
)
