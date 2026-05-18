package com.bizplay.data.api.dto

import com.bizplay.core.model.LoginResponse
import com.bizplay.core.model.UserSession
import com.bizplay.core.session.AccountId
import com.bizplay.core.session.DepartmentAccount
import com.bizplay.core.tenant.TenantContext
import com.bizplay.core.tenant.TenantFlags
import com.bizplay.core.tenant.TenantId
import com.bizplay.core.tenant.TenantParams
import com.bizplay.core.variant.VariantId
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class LoginRequestDto(
    val userId: String,
    val encryptedPassword: String,
    val companyCode: String,
    val encGb: String = "1",
    val moblCd: String = "0",
    val nationCd: String = "KR",
)

@JsonClass(generateAdapter = true)
internal data class LoginResponseDto(
    val accessToken: String,
    val userId: String,
    val userDisplayName: String,
    val variantId: String,
    val tenant: TenantDto,
    val accounts: List<AccountDto>,
    val defaultAccountId: String,
    /** Mirrors CRTC_PATH == "C006": when true, force the institution picker before continuing. */
    val requiresInstitutionSelection: Boolean,
)

@JsonClass(generateAdapter = true)
internal data class TenantDto(
    val id: String,
    val displayName: String,
    val flags: TenantFlagsDto,
    val params: TenantParamsDto,
)

@JsonClass(generateAdapter = true)
internal data class TenantFlagsDto(
    val hidesEmployeeId: Boolean = false,
    val requiresApprovalForEveryReceipt: Boolean = false,
    val showsCorporateLogo: Boolean = true,
    val allowsPersonalCardLink: Boolean = true,
)

@JsonClass(generateAdapter = true)
internal data class TenantParamsDto(
    val maxReceiptAmountMinor: Long = Long.MAX_VALUE,
    val supportPhone: String = "",
    val supportEmail: String = "",
)

@JsonClass(generateAdapter = true)
internal data class AccountDto(
    val id: String,
    val displayName: String,
    val companyCode: String,
    val divisionCode: String,
    val divisionName: String,
)

internal fun LoginResponseDto.toDomain(): LoginResponse {
    val accountList = accounts.map {
        DepartmentAccount(
            id = AccountId(it.id),
            displayName = it.displayName,
            companyCode = it.companyCode,
            divisionCode = it.divisionCode,
            divisionName = it.divisionName,
        )
    }
    return LoginResponse(
        user = UserSession(
            userId = userId,
            displayName = userDisplayName,
            accessToken = accessToken,
        ),
        variantId = VariantId(variantId),
        tenant = TenantContext(
            id = TenantId(tenant.id),
            displayName = tenant.displayName,
            flags = TenantFlags(
                hidesEmployeeId = tenant.flags.hidesEmployeeId,
                requiresApprovalForEveryReceipt = tenant.flags.requiresApprovalForEveryReceipt,
                showsCorporateLogo = tenant.flags.showsCorporateLogo,
                allowsPersonalCardLink = tenant.flags.allowsPersonalCardLink,
            ),
            params = TenantParams(
                maxReceiptAmountMinor = tenant.params.maxReceiptAmountMinor,
                supportPhone = tenant.params.supportPhone,
                supportEmail = tenant.params.supportEmail,
            ),
        ),
        accounts = accountList,
        requiresInstitutionSelection = requiresInstitutionSelection,
    )
}
