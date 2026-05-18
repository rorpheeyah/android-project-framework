package com.compass.features.boot

import com.compass.core.mvi.UiEffect
import com.compass.core.mvi.UiEvent
import com.compass.core.mvi.UiState
import com.compass.core.runtime.ForceUpdate
import com.compass.core.runtime.MaintenanceState

internal enum class BootPhase { Loading, Failed }

internal data class BootState(
    val phase: BootPhase = BootPhase.Loading,
    val errorMessage: String? = null,
) : UiState

internal sealed interface BootEvent : UiEvent {
    data object Retry : BootEvent
}

internal sealed interface BootEffect : UiEffect {
    data object Ready : BootEffect
    data class Maintenance(val state: MaintenanceState.Down) : BootEffect
    data class ForceUpdateRequired(val forceUpdate: ForceUpdate) : BootEffect
}
