package com.compass.app.boot

import com.compass.app.BuildConfig
import com.compass.core.runtime.ForceUpdate
import com.compass.core.runtime.MaintenanceState
import com.compass.core.runtime.RuntimeConfig
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MG is the *only* hardcoded URL in the binary; everything else (main API URL,
 * maintenance state, version floor) comes from its response.
 *
 * For the demo there is no live server, so the fetch is faked. Production
 * implementation would be a Retrofit interface against `BuildConfig.MG_URL`.
 */
@Singleton
class MgClient @Inject constructor() {

    private val mgUrl: String = BuildConfig.MG_URL

    suspend fun fetchRuntimeConfig(): RuntimeConfig {
        delay(600)
        return RuntimeConfig(
            apiBaseUrl = "https://api.compass.bank/",
            maintenance = MaintenanceState.Up,
            forceUpdate = null,
        )
    }

    /** Convenience for telemetry: which MG was contacted. */
    fun mgUrl(): String = mgUrl
}
