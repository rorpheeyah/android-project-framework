package com.compass.core.runtime

/**
 * What MG returns at boot. Only the MG URL itself is baked into the binary;
 * everything else comes from here.
 */
data class RuntimeConfig(
    val apiBaseUrl: String,
    val maintenance: MaintenanceState,
    val forceUpdate: ForceUpdate?,
)

sealed interface MaintenanceState {
    data object Up : MaintenanceState
    data class Down(val message: String) : MaintenanceState
}

data class ForceUpdate(
    val minVersionCode: Int,
    val storeUrl: String,
    val message: String,
)
