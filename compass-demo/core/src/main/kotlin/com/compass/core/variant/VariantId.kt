package com.compass.core.variant

@JvmInline
value class VariantId(val value: String) {
    init { require(value.isNotBlank()) { "VariantId must not be blank" } }
}
