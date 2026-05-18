package com.compass.features.main

import com.compass.core.mvi.UiEvent
import com.compass.core.mvi.UiState

internal data class HomeState(
    val userDisplayName: String = "",
    val variantDisplayName: String = "",
    val activeAccountDisplayName: String = "",
    val sampleAmount: String = "",
) : UiState

internal sealed interface HomeEvent : UiEvent
