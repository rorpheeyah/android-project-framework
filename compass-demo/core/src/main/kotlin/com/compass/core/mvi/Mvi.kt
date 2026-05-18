package com.compass.core.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Marker interfaces for the MVI triple. Each screen owns a sealed-type tree
 * implementing these in its `*Contract.kt` file.
 */
interface UiState
interface UiEvent
interface UiEffect

/**
 * MVI base used by every `:features` ViewModel.
 *
 * - `state`        : current screen state, observed by the @Composable.
 * - `effects`      : one-shot side effects (navigation, snackbars) the UI consumes.
 * - `onEvent`      : the only way the UI tells the ViewModel something happened.
 */
abstract class MviViewModel<S : UiState, E : UiEvent, F : UiEffect>(initial: S) : ViewModel() {

    private val _state = MutableStateFlow(initial)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = Channel<F>(Channel.BUFFERED)
    val effects: Flow<F> = _effects.receiveAsFlow()

    abstract fun onEvent(event: E)

    protected fun setState(reducer: S.() -> S) {
        _state.value = _state.value.reducer()
    }

    protected fun emitEffect(effect: F) {
        viewModelScope.launch { _effects.send(effect) }
    }
}
