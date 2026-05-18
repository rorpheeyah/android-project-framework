package com.compass.variants.kh.support

import com.compass.core.policy.SupportContacts
import javax.inject.Inject

internal class KhSupportContacts @Inject constructor() : SupportContacts {
    override val hotlineNumber: String = "+855 23 999 000"
    override val supportEmail: String = "support.kh@compass.bank"
}
