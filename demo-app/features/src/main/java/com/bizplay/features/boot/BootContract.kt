package com.bizplay.features.boot

import com.bizplay.core.boot.BootResult
import com.bizplay.core.mvi.UiEffect
import com.bizplay.core.mvi.UiEvent
import com.bizplay.core.mvi.UiState

/** What the user sees on the boot/intro screen — never a Throwable or one-shot navigation flag. */
data class BootUiState(
    val phase: Phase = Phase.SecurityCheck,
    val message: String = "",
    val maintenance: BootResult.Maintenance? = null,
    val upgrade: BootResult.Upgrade? = null,
    val errorReason: String? = null,
) : UiState {
    enum class Phase { SecurityCheck, MgFetch, Ready, GateBlocked, Failed }
}

sealed interface BootUiEvent : UiEvent {
    data object Start : BootUiEvent
    data object Retry : BootUiEvent
}

/** One-shot side effects — navigation, store launch, app exit. */
sealed interface BootUiEffect : UiEffect {
    data object NavigateToLogin : BootUiEffect
    data class OpenStore(val url: String) : BootUiEffect
    data object ExitApp : BootUiEffect
}
