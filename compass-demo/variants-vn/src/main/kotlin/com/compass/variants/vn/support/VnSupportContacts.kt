package com.compass.variants.vn.support

import com.compass.core.policy.SupportContacts
import javax.inject.Inject

internal class VnSupportContacts @Inject constructor() : SupportContacts {
    override val hotlineNumber: String = "+84 28 7300 0000"
    override val supportEmail: String = "support.vn@compass.bank"
}
