package com.bizplay.features.home

import com.bizplay.core.mvi.UiEffect
import com.bizplay.core.mvi.UiEvent
import com.bizplay.core.mvi.UiState
import com.bizplay.core.session.DepartmentAccount

/**
 * The home state is derived from [com.bizplay.core.session.Session] +
 * [com.bizplay.core.variant.VariantCapabilities] + [com.bizplay.core.tenant.TenantContext].
 * We snapshot the booleans and strings into UI state — never a raw [VariantId] —
 * so the UI cannot accidentally branch on variant id.
 */
data class HomeUiState(
    val userDisplayName: String = "",
    val variantName: String = "",
    val tenantName: String = "",
    val activeAccount: DepartmentAccount? = null,
    val accounts: List<DepartmentAccount> = emptyList(),
    val showsCorporateLogo: Boolean = false,
    val supportsHipassTracking: Boolean = false,
    val supportsMyDataAggregation: Boolean = false,
) : UiState

sealed interface HomeUiEvent : UiEvent {
    data object SwitchInstitution : HomeUiEvent
    data object Logout : HomeUiEvent
}

sealed interface HomeUiEffect : UiEffect {
    data object NavigateToInstitutionSwitcher : HomeUiEffect
    data object NavigateToLogin : HomeUiEffect
}
