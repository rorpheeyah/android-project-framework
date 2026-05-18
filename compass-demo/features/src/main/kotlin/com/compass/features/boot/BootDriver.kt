package com.compass.features.boot

import com.compass.core.runtime.ForceUpdate
import com.compass.core.runtime.MaintenanceState

/**
 * `:features` consumes the boot result through this interface; the orchestrator
 * in `:app` provides the implementation (its `BootCoordinator`). Keeps `:features`
 * from depending on `:app`.
 */
interface BootDriver {

    /** Runs MG fetch + gate evaluation, returns the next destination. */
    suspend fun runBoot(): BootResult
}

sealed interface BootResult {
    data object Ready : BootResult
    data class Maintenance(val state: MaintenanceState.Down) : BootResult
    data class ForceUpdateRequired(val forceUpdate: ForceUpdate) : BootResult
}
