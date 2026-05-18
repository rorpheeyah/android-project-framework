package com.compass.variants.vn.capability

import com.compass.core.policy.VariantCapabilities
import javax.inject.Inject

internal class VnCapabilities @Inject constructor() : VariantCapabilities {
    override val supportsBiometricLogin: Boolean = false
    override val supportsInstitutionPicker: Boolean = true
    override val supportsOtpOnLogin: Boolean = true
}
