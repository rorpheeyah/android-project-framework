package com.compass.core.session

import com.compass.core.model.Currency

enum class AccountType { Personal, Corporate, Joint }

/**
 * One row of [com.compass.core.model.LoginResponse.accounts].
 *
 * In BizplayIPPP terms this is the user's `USE_INTT_ID` / `JOIN_USE_INTT_ID`
 * — the institution the same login is enrolled in. Multiple of these can
 * exist under one login; the user picks one (or it's picked for them)
 * after primary auth.
 */
data class DepartmentAccount(
    val id: AccountId,
    val displayName: String,
    val institutionCode: String,
    val accountType: AccountType,
    val currency: Currency,
)
