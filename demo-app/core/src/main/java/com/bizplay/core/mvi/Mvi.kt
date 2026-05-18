package com.bizplay.core.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

interface UiState

interface UiEvent

interface UiEffect

abstract class MviViewModel<S : UiState, E : UiEvent, F : UiEffect>(initial: S) : ViewModel() {

    private val _state = MutableStateFlow(initial)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<F>(extraBufferCapacity = 16)
    val effects: SharedFlow<F> = _effects.asSharedFlow()

    abstract fun onEvent(event: E)

    protected fun setState(reducer: S.() -> S) {
        _state.update(reducer)
    }

    protected fun currentState(): S = _state.value

    protected fun emitEffect(effect: F) {
        viewModelScope.launch { _effects.emit(effect) }
    }
}
