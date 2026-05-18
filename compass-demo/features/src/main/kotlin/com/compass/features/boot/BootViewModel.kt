package com.compass.features.boot

import androidx.lifecycle.viewModelScope
import com.compass.core.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class BootViewModel @Inject constructor(
    private val bootDriver: BootDriver,
) : MviViewModel<BootState, BootEvent, BootEffect>(initial = BootState()) {

    init { runBoot() }

    override fun onEvent(event: BootEvent) {
        when (event) {
            BootEvent.Retry -> {
                setState { copy(phase = BootPhase.Loading, errorMessage = null) }
                runBoot()
            }
        }
    }

    private fun runBoot() = viewModelScope.launch {
        runCatching { bootDriver.runBoot() }
            .onSuccess { result ->
                when (result) {
                    BootResult.Ready -> emitEffect(BootEffect.Ready)
                    is BootResult.Maintenance -> emitEffect(BootEffect.Maintenance(result.state))
                    is BootResult.ForceUpdateRequired -> emitEffect(BootEffect.ForceUpdateRequired(result.forceUpdate))
                }
            }
            .onFailure { e ->
                setState {
                    copy(
                        phase = BootPhase.Failed,
                        errorMessage = e.message ?: "Cannot reach servers.",
                    )
                }
            }
    }
}
