package com.bizplay.core.variant

/**
 * Region / regulator boundary. KR, KH, VN.
 * Bound once at login from [com.bizplay.core.model.LoginResponse] and never swapped in-session.
 * Never used for dispatch (`if (variantId == "kr")` is forbidden).
 */
@JvmInline
value class VariantId(val value: String) {
    companion object {
        val KR = VariantId("kr")
        val KH = VariantId("kh")
        val VN = VariantId("vn")
    }
}

/**
 * Immutable snapshot of the active variant. UI may read display fields (name, locale)
 * but must not branch on [id]. Capability gates go through [VariantCapabilities].
 */
data class VariantContext(
    val id: VariantId,
    val displayName: String,
    val defaultLocale: String,
    val currencyCode: String,
)
