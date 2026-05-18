package com.compass.variants.kh.format

import com.compass.core.model.Money
import com.compass.core.policy.AmountFormatter
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import javax.inject.Inject

/**
 * KH formatting convention: thousand-separator commas, no decimals, trailing
 * currency symbol. Example: `1,234,567 ៛` / `1,234.56 $`.
 */
internal class KhrAmountFormatter @Inject constructor() : AmountFormatter {

    private val noDecimals = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US))
    private val twoDecimals = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))

    override fun format(money: Money): String {
        val formatter = if (money.currency == com.compass.core.model.Currency.KHR) noDecimals else twoDecimals
        return "${formatter.format(money.amount)} ${money.currency.symbol}"
    }
}
