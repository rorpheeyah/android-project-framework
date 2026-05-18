package com.compass.core.model

import java.math.BigDecimal

data class Money(val amount: BigDecimal, val currency: Currency) {
    companion object {
        fun zero(currency: Currency): Money = Money(BigDecimal.ZERO, currency)
    }
}
