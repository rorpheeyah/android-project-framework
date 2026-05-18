package com.compass.core.policy

import com.compass.core.model.Money

/**
 * Variant-specific number formatting (decimal separator, currency symbol position).
 * KH formats KHR with no decimals and trailing symbol; VN formats VND likewise; etc.
 */
interface AmountFormatter {
    fun format(money: Money): String
}
