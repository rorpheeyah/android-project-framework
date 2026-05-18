package com.compass.app.variant

import com.compass.core.model.Currency
import com.compass.core.variant.VariantContext
import com.compass.core.variant.VariantId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Static, build-time list of variants this binary ships. Onboarding a new
 * variant adds one entry here, one `include(":variants-X")`, and one Hilt
 * module — and that's it. `:features` is untouched.
 *
 * The catalogue is consulted to turn a `variantId` into a `VariantContext`
 * (the immutable display-time descriptor read by UI). No dispatch happens
 * here — that's `VariantResolverModule`'s job.
 */
@Singleton
class VariantCatalogue @Inject constructor() {

    private val entries: List<VariantContext> = listOf(
        VariantContext(
            id = VariantId("kh"),
            displayName = "Cambodia",
            primaryCurrency = Currency.KHR,
        ),
        VariantContext(
            id = VariantId("vn"),
            displayName = "Vietnam",
            primaryCurrency = Currency.VND,
        ),
    )

    fun resolve(id: VariantId): VariantContext =
        entries.firstOrNull { it.id == id }
            ?: error("Unknown variant id '${id.value}'. Add it to VariantCatalogue.")

    /** Default variant used pre-login (for support hotline + capability flags). */
    fun defaultVariant(): VariantContext = entries.first()
}
