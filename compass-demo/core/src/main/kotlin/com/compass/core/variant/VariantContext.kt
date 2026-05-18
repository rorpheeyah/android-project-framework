package com.compass.core.variant

import com.compass.core.model.Currency

/**
 * Immutable display-time descriptor of the active variant. Read for currency,
 * display name, support contacts, etc. — never branched on for dispatch.
 */
data class VariantContext(
    val id: VariantId,
    val displayName: String,
    val primaryCurrency: Currency,
)
