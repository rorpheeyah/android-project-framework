package com.compass.core.policy

/**
 * Capability flags read by UI to gate variant-unique features. UI never reads
 * the variant id directly — it asks "does this variant support X?".
 */
interface VariantCapabilities {
    val supportsBiometricLogin: Boolean
    val supportsInstitutionPicker: Boolean
    val supportsOtpOnLogin: Boolean
}
