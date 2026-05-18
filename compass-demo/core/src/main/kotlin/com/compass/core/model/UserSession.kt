package com.compass.core.model

/**
 * Identity + access token returned by the auth backend.
 * Stays valid for the lifetime of the LoggedInComponent.
 */
data class UserSession(
    val userId: String,
    val displayName: String,
    val accessToken: String,
)
