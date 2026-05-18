package com.compass.app.boot

import com.aos.core.network.BaseUrlProvider
import com.compass.app.session.LoggedInComponentManager
import com.compass.app.session.SessionFactory
import com.compass.core.model.LoginResponse
import com.compass.core.runtime.MaintenanceState
import com.compass.core.runtime.RuntimeConfig
import com.compass.core.session.AccountId
import com.compass.features.boot.BootDriver
import com.compass.features.boot.BootResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the boot phases from `:app`'s side. Lives in SingletonComponent.
 *
 * Implements `BootDriver` (from `:features`) so the BootScreen can resolve it
 * through Hilt without `:features` knowing about `:app`.
 */
@Singleton
class BootCoordinator @Inject constructor(
    private val mgClient: MgClient,
    private val baseUrlProvider: BaseUrlProvider,
    private val sessionFactory: SessionFactory,
    private val componentManager: LoggedInComponentManager,
    private val runtimeConfigStore: RuntimeConfigStore,
) : BootDriver {

    override suspend fun runBoot(): BootResult {
        val config = mgClient.fetchRuntimeConfig()
        runtimeConfigStore.set(config)
        baseUrlProvider.set(config.apiBaseUrl)

        return when (val maintenance = config.maintenance) {
            is MaintenanceState.Down -> BootResult.Maintenance(maintenance)
            MaintenanceState.Up -> {
                val force = config.forceUpdate
                if (force != null && BUILD_VERSION_CODE < force.minVersionCode) {
                    BootResult.ForceUpdateRequired(force)
                } else {
                    BootResult.Ready
                }
            }
        }
    }

    /**
     * Called by login flow once primary auth + (optional) institution pick is done.
     * Builds the Session and the LoggedInComponent — a one-time event per session.
     */
    suspend fun onLoginSuccess(response: LoginResponse, activeAccountId: AccountId) {
        val session = sessionFactory.build(response, activeAccountId)
        componentManager.build(session)
    }

    companion object {
        /** Demo only; real builds would read [com.compass.app.BuildConfig.VERSION_CODE]. */
        private const val BUILD_VERSION_CODE = 1
    }
}

/** Process-lifetime cache of the latest RuntimeConfig. Survives logout. */
@Singleton
class RuntimeConfigStore @Inject constructor() {

    @Volatile private var current: RuntimeConfig? = null

    fun set(config: RuntimeConfig) { current = config }
    fun get(): RuntimeConfig = checkNotNull(current) { "RuntimeConfig not yet fetched" }
    fun getOrNull(): RuntimeConfig? = current
}
