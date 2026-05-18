package com.bizplay.features.home

import androidx.lifecycle.viewModelScope
import com.bizplay.core.boot.BootCoordinator
import com.bizplay.core.mvi.MviViewModel
import com.bizplay.core.session.SessionHolder
import com.bizplay.core.variant.VariantCapabilities
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Re-emits UI state whenever the active account changes via [Session.activeAccountId].
 * That StateFlow is the single source of truth — when the user picks a new institution
 * from the in-session switcher, the very next outgoing request is stamped with the
 * new USE_INTT_ID and any feature ViewModel that collects the flow re-fetches data.
 *
 * No DI rebuild. No logout. Mirrors framework invariant #9.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionHolder: SessionHolder,
    private val capabilities: VariantCapabilities,
    private val coordinator: BootCoordinator,
) : MviViewModel<HomeUiState, HomeUiEvent, HomeUiEffect>(HomeUiState()) {

    init {
        val session = sessionHolder.current
        setState {
            HomeUiState(
                userDisplayName = session.user.displayName,
                variantName = session.variant.displayName,
                tenantName = session.tenant.displayName,
                accounts = session.accounts,
                activeAccount = session.activeAccount,
                showsCorporateLogo = session.tenant.flags.showsCorporateLogo,
                supportsHipassTracking = capabilities.supportsHipassTracking,
                supportsMyDataAggregation = capabilities.supportsMyDataAggregation,
            )
        }
        viewModelScope.launch {
            session.activeAccountId.collectLatest { _ ->
                setState { copy(activeAccount = session.activeAccount) }
            }
        }
    }

    override fun onEvent(event: HomeUiEvent) {
        when (event) {
            HomeUiEvent.SwitchInstitution -> emitEffect(HomeUiEffect.NavigateToInstitutionSwitcher)
            HomeUiEvent.Logout -> logout()
        }
    }

    private fun logout() {
        viewModelScope.launch {
            coordinator.onLogout()
            emitEffect(HomeUiEffect.NavigateToLogin)
        }
    }
}
