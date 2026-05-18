package com.bizplay.core.variant

/**
 * Per-variant feature gates. Each variant module supplies a concrete instance via Hilt
 * multibindings keyed by [com.bizplay.core.scope.VariantKey].
 *
 * UI reads these as plain booleans — there is no branching on variant id anywhere.
 */
interface VariantCapabilities {
    val supportsHipassTracking: Boolean
    val supportsMyDataAggregation: Boolean
    val supportsCorporateCardLink: Boolean
    val supportsBusinessTripBooking: Boolean
}
