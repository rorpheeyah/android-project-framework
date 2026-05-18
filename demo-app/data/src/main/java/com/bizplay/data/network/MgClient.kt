package com.bizplay.data.network

import com.bizplay.core.runtime.ApiUrls
import com.bizplay.core.runtime.ForceUpdate
import com.bizplay.core.runtime.MaintenanceState
import com.bizplay.core.runtime.RuntimeConfig
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calls the hardcoded MgGate URL and returns a typed [RuntimeConfig].
 *
 * In a production wiring this delegates to a Retrofit-built [com.bizplay.data.api.MgApi].
 * For the offline demo we synthesize the same payload locally so BootScreen has
 * something to consume without a network connection.
 */
@Singleton
class MgClient @Inject constructor() {

    suspend fun fetch(): Result<RuntimeConfig> = runCatching {
        delay(750L) // simulate the MgGate round-trip
        DEMO_CONFIG
    }

    private companion object {
        // Mirrors the shape of the real /MgGate response. The values are intentionally
        // *not* baked into any other module — every consumer reads RuntimeConfig instead.
        private val DEMO_CONFIG = RuntimeConfig(
            urls = ApiUrls(main = "https://ippp-api.bizplay.co.kr/"),
            webRoutes = mapOf(
                "approval.form" to "https://ippp-web.bizplay.co.kr/approval",
                "receipt.detail" to "https://ippp-web.bizplay.co.kr/receipt",
            ),
            maintenance = MaintenanceState(status = MaintenanceState.Status.UP),
            forceUpdate = ForceUpdate(
                minimumVersionCode = 1,
                storeUrl = "market://details?id=com.bizplay.demo",
            ),
        )
    }
}
