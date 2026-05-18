package com.bizplay.demo.boot

import com.bizplay.aoscore.security.SecurityProvider
import com.bizplay.core.boot.BootCoordinator
import com.bizplay.core.boot.BootResult
import com.bizplay.core.model.LoginResponse
import com.bizplay.core.repository.AuthRepository
import com.bizplay.core.runtime.MaintenanceState
import com.bizplay.core.session.AccountId
import com.bizplay.core.session.Session
import com.bizplay.data.network.MgClient
import com.bizplay.data.network.RuntimeConfigStore
import com.bizplay.demo.BuildConfig
import com.bizplay.demo.di.LoggedInComponentManager
import com.bizplay.demo.variant.VariantCatalogue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The orchestrator. The seven boot phases from `docs/10-boot-phases.md`
 * happen here:
 *
 *   1. Cold start  → SecurityProvider.runSelfChecks
 *   2. MG fetch    → MgClient.fetch
 *   3. Gate        → maintenance / force-update check
 *   4. Login       → AuthRepository.login (driven by LoginViewModel)
 *   5. Build       → LoggedInComponentManager.build (onLoginSuccess)
 *   6. Enter main  → navigation graph routes to home
 *   7. Logout      → LoggedInComponentManager.drop (onLogout)
 *
 * This is the **only** class allowed to know about MgClient and LoggedInComponent
 * together. Per framework rule #8, the MgGate URL is sourced from BuildConfig —
 * the single hardcoded network constant in the binary.
 */
@Singleton
class BootCoordinatorImpl @Inject constructor(
    private val securityProvider: SecurityProvider,
    private val mgClient: MgClient,
    private val runtimeConfigStore: RuntimeConfigStore,
    private val authRepository: AuthRepository,
    private val componentManager: LoggedInComponentManager,
    private val variantCatalogue: VariantCatalogue,
) : BootCoordinator {

    init {
        // For the demo we just verify the baked URL is present. In production this
        // is also bound into the Retrofit factory used only for MgApi — every other
        // client reads its base URL out of RuntimeConfig.
        check(BuildConfig.MG_URL.isNotBlank()) { "MG_URL must be set at build time" }
    }

    override suspend fun runBoot(): BootResult {
        when (val check = securityProvider.runSelfChecks()) {
            SecurityProvider.SelfCheckResult.Ok -> Unit
            is SecurityProvider.SelfCheckResult.Threat ->
                return BootResult.Unreachable("Security check failed: ${check.reason}")
        }

        val config = mgClient.fetch().getOrElse {
            return BootResult.Unreachable(it.message ?: "MgGate unreachable")
        }
        runtimeConfigStore.commit(config)

        if (config.maintenance.status == MaintenanceState.Status.DOWN) {
            return BootResult.Maintenance(config.maintenance)
        }
        if (BuildConfig.VERSION_CODE < config.forceUpdate.minimumVersionCode) {
            return BootResult.Upgrade(config.forceUpdate)
        }
        return BootResult.Ready(config)
    }

    override suspend fun onLoginSuccess(response: LoginResponse): Result<Unit> = runCatching {
        val variantContext = variantCatalogue.resolve(response.variantId)
        val initialAccountId = response.accounts.first().id
        val session = Session(
            user = response.user,
            variant = variantContext,
            tenant = response.tenant,
            accounts = response.accounts,
            initialActiveAccountId = initialAccountId,
        )
        componentManager.build(session = session, variant = variantContext, tenant = response.tenant)
    }

    /**
     * Flips the active account id once the user has chosen from the institution
     * picker. Mirrors the existing Bizplay flow that persists USER_ID +
     * JOIN_USE_INTT_ID into MemoryPreferenceDelegator before continuing.
     */
    override fun finalizeLogin(activeAccountId: AccountId) {
        componentManager.entryPointOrNull()?.session()?.switchAccount(activeAccountId)
    }

    override suspend fun onLogout() {
        runCatching { authRepository.logout() }
        componentManager.drop()
    }
}
