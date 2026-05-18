package com.compass.core.session

@JvmInline
value class AccountId(val value: String) {
    init { require(value.isNotBlank()) { "AccountId must not be blank" } }
}
