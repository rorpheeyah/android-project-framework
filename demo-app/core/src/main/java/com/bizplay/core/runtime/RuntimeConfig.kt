package com.bizplay.core.runtime

/**
 * Immutable process-lifetime config returned by MgGate. The **only** URL baked
 * into the binary is the MgGate URL itself; every other endpoint flows through here.
 */
data class RuntimeConfig(
    val urls: ApiUrls,
    val webRoutes: Map<String, String>,
    val maintenance: MaintenanceState,
    val forceUpdate: ForceUpdate,
)

data class ApiUrls(
    val main: String,
    val auxiliary: String? = null,
)

data class MaintenanceState(
    val status: Status,
    val message: String? = null,
    val etaIso8601: String? = null,
) {
    enum class Status { UP, DOWN }
}

data class ForceUpdate(
    val minimumVersionCode: Int,
    val storeUrl: String,
)
