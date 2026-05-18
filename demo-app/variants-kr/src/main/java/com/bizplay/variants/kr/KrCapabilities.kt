package com.bizplay.variants.kr

import com.bizplay.core.variant.VariantCapabilities

/**
 * KR-specific feature gates. UI reads these as booleans —
 * never `if (variant.id == "kr")`.
 */
internal class KrCapabilities : VariantCapabilities {
    override val supportsHipassTracking: Boolean = true       // KR-only: highway tolls
    override val supportsMyDataAggregation: Boolean = true    // KR MyData spec
    override val supportsCorporateCardLink: Boolean = true
    override val supportsBusinessTripBooking: Boolean = true
}
