package com.compass.app.navigation

import com.compass.core.model.LoginResponse

/**
 * Scratch state for the pre-session login flow. The LoginResponse returned
 * by primary auth is held here while the user picks an institution; the
 * orchestrator then builds the Session from it.
 *
 * Not Hilt-scoped; the navigation host owns it.
 */
internal class LoginFlowHolder {
    var pendingLogin: LoginResponse? = null
}
