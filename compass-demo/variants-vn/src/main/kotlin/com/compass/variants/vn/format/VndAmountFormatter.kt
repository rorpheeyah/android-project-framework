package com.compass.variants.vn.format

import com.compass.core.model.Currency
import com.compass.core.model.Money
import com.compass.core.policy.AmountFormatter
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import javax.inject.Inject

/**
 * VN formatting convention: dot-separated thousands, no decimals on VND.
 * Example: `1.234.567 ₫`.
 */
internal class VndAmountFormatter @Inject constructor() : AmountFormatter {

    private val symbols = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }
    private val noDecimals = DecimalFormat("#,##0", symbols)
    private val twoDecimals = DecimalFormat("#,##0.00", symbols)

    override fun format(money: Money): String {
        val formatter = if (money.currency == Currency.VND) noDecimals else twoDecimals
        return "${formatter.format(money.amount)} ${money.currency.symbol}"
    }
}
