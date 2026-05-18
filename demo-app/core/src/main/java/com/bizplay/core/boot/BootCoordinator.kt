package com.bizplay.core.boot

import com.bizplay.core.model.LoginResponse
import com.bizplay.core.session.AccountId

/**
 * Boundary between :features and :app for boot / session orchestration.
 *
 * BootViewModel calls [runBoot]. LoginViewModel calls [onLoginSuccess]. The
 * SelectInstitutionViewModel optionally calls [finalizeLogin] with the picked
 * account once the user has chosen one (mirrors `SelectUserInttIdActivity`
 * persisting USER_ID + JOIN_USE_INTT_ID before continuing).
 *
 * Implementation lives in `:app/boot/BootCoordinatorImpl.kt` and is the single
 * place that knows about [com.bizplay.data.network.MgClient] and
 * `LoggedInComponentManager`.
 */
interface BootCoordinator {

    suspend fun runBoot(): BootResult

    /**
     * Builds the LoggedInComponent from [response]. If the response indicates an
     * institution picker is required, the caller is responsible for showing it
     * and then calling [finalizeLogin] with the chosen account; otherwise the
     * default account is activated immediately.
     */
    suspend fun onLoginSuccess(response: LoginResponse): Result<Unit>

    /**
     * Switches the active department account before navigating to home.
     * Equivalent to writing USE_INTT_ID into the existing
     * MemoryPreferenceDelegator and proceeding with the next login phase.
     */
    fun finalizeLogin(activeAccountId: AccountId)

    suspend fun onLogout()
}
