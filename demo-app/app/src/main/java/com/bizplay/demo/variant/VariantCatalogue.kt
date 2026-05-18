package com.bizplay.demo.variant

import com.bizplay.core.variant.VariantContext
import com.bizplay.core.variant.VariantId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Known variants for this build. Adding a new region is **strictly additive**:
 * one new module + one `include(":variants-X")` + one entry here.
 *
 * Per framework invariant #11, this list is the only place a variant id appears
 * outside the variant module itself. Everything else reads through [VariantContext].
 */
@Singleton
class VariantCatalogue @Inject constructor() {

    private val variants: Map<VariantId, VariantContext> = mapOf(
        VariantId.KR to VariantContext(
            id = VariantId.KR,
            displayName = "Korea",
            defaultLocale = "ko-KR",
            currencyCode = "KRW",
        ),
        // VariantId.KH and VariantId.VN would be added here once their modules ship.
    )

    fun resolve(id: VariantId): VariantContext =
        variants[id] ?: error("No VariantContext registered for $id — wire its variant module.")
}
