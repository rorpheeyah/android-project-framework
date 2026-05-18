package com.compass.features.main

import androidx.lifecycle.viewModelScope
import com.compass.core.model.Currency
import com.compass.core.model.Money
import com.compass.core.mvi.MviViewModel
import com.compass.core.policy.AmountFormatter
import com.compass.core.session.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Demonstrates that ViewModels resolve `:core` interfaces and the active
 * variant's policies through the same injection — no `if (variantId == …)`.
 *
 * The same line `amountFormatter.format(money)` produces "1,234,567 ៛" under
 * KH and "1.234.567 ₫" under VN, because the resolver picked the variant's
 * formatter at injection time.
 */
@HiltViewModel
internal class HomeViewModel @Inject constructor(
    private val session: Session,
    private val amountFormatter: AmountFormatter,
) : MviViewModel<HomeState, HomeEvent, Nothing>(initial = HomeState()) {

    init {
        refresh()
        viewModelScope.launch {
            session.activeAccountId.collect { refresh() }
        }
    }

    override fun onEvent(event: HomeEvent) = Unit

    private fun refresh() {
        val active = session.activeAccount
        val sample = Money(BigDecimal("1234567"), active.currency.takeIf { it != Currency.USD } ?: Currency.USD)
        setState {
            copy(
                userDisplayName = session.userSession.displayName,
                variantDisplayName = session.variantContext.displayName,
                activeAccountDisplayName = "${active.displayName} (${active.institutionCode})",
                sampleAmount = amountFormatter.format(sample),
            )
        }
    }
}
