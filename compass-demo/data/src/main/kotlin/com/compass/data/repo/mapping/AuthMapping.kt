package com.compass.data.repo.mapping

import com.compass.core.model.Currency
import com.compass.core.model.LoginResponse
import com.compass.core.model.UserSession
import com.compass.core.session.AccountId
import com.compass.core.session.AccountType
import com.compass.core.session.DepartmentAccount
import com.compass.core.variant.VariantId
import com.compass.data.api.dto.auth.AccountDto
import com.compass.data.api.dto.auth.LoginResponseDto

internal fun LoginResponseDto.toDomain(): LoginResponse = LoginResponse(
    userSession = UserSession(
        userId = userId,
        displayName = displayName,
        accessToken = accessToken,
    ),
    accounts = accounts.map(AccountDto::toDomain),
    variantId = VariantId(variantId),
)

private fun AccountDto.toDomain(): DepartmentAccount = DepartmentAccount(
    id = AccountId(accountId),
    displayName = displayName,
    institutionCode = institutionCode,
    accountType = parseAccountType(accountType),
    currency = parseCurrency(currency),
)

private fun parseAccountType(raw: String): AccountType = when (raw.uppercase()) {
    "PERSONAL" -> AccountType.Personal
    "CORPORATE" -> AccountType.Corporate
    "JOINT" -> AccountType.Joint
    else -> AccountType.Personal
}

private fun parseCurrency(raw: String): Currency = runCatching {
    Currency.valueOf(raw.uppercase())
}.getOrElse { Currency.USD }
