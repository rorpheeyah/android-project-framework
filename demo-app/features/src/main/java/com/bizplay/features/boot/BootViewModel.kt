package com.bizplay.features.boot

import androidx.lifecycle.viewModelScope
import com.bizplay.core.boot.BootCoordinator
import com.bizplay.core.boot.BootResult
import com.bizplay.core.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives BootScreen. Depends on the [BootCoordinator] *interface* only —
 * the :app module supplies the concrete implementation.
 *
 * Mirrors the existing `IntroViewModel.requestMG()` flow:
 * security self-checks → MgGate fetch → gate decision → next screen.
 */
@HiltViewModel
class BootViewModel @Inject constructor(
    private val coordinator: BootCoordinator,
) : MviViewModel<BootUiState, BootUiEvent, BootUiEffect>(BootUiState()) {

    init { onEvent(BootUiEvent.Start) }

    override fun onEvent(event: BootUiEvent) {
        when (event) {
            BootUiEvent.Start, BootUiEvent.Retry -> runBoot()
        }
    }

    private fun runBoot() {
        viewModelScope.launch {
            setState {
                BootUiState(phase = BootUiState.Phase.SecurityCheck, message = "Running security checks…")
            }
            setState {
                copy(phase = BootUiState.Phase.MgFetch, message = "Contacting MgGate…")
            }
            when (val result = coordinator.runBoot()) {
                is BootResult.Ready -> {
                    setState { BootUiState(phase = BootUiState.Phase.Ready) }
                    emitEffect(BootUiEffect.NavigateToLogin)
                }
                is BootResult.Maintenance -> setState {
                    BootUiState(phase = BootUiState.Phase.GateBlocked, maintenance = result)
                }
                is BootResult.Upgrade -> setState {
                    BootUiState(phase = BootUiState.Phase.GateBlocked, upgrade = result)
                }
                is BootResult.Unreachable -> setState {
                    BootUiState(phase = BootUiState.Phase.Failed, errorReason = result.reason)
                }
            }
        }
    }
}
