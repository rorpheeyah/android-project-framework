package com.compass.variants.kh.capability

import com.compass.core.policy.VariantCapabilities
import javax.inject.Inject

internal class KhCapabilities @Inject constructor() : VariantCapabilities {
    override val supportsBiometricLogin: Boolean = true
    override val supportsInstitutionPicker: Boolean = true
    override val supportsOtpOnLogin: Boolean = true
}
