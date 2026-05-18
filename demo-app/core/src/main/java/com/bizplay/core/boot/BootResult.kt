package com.bizplay.core.boot

import com.bizplay.core.runtime.ForceUpdate
import com.bizplay.core.runtime.MaintenanceState
import com.bizplay.core.runtime.RuntimeConfig

/**
 * Outcome of the cold-start boot sequence. Drives the gate Composables and the
 * navigation transition out of BootScreen.
 */
sealed interface BootResult {
    /** MgGate succeeded; gates passed; proceed to login (or auto-login if a session is cached). */
    data class Ready(val config: RuntimeConfig) : BootResult

    /** MgGate.maintenance.status == DOWN — show MaintenanceGate, no further requests. */
    data class Maintenance(val state: MaintenanceState) : BootResult

    /** App build is below RuntimeConfig.forceUpdate.minimumVersionCode — show ForceUpdateGate. */
    data class Upgrade(val forceUpdate: ForceUpdate) : BootResult

    /** Couldn't reach MgGate at all. Display a retry CTA — no fallback URL table by design. */
    data class Unreachable(val reason: String) : BootResult
}
