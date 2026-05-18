package com.bizplay.data.repo

import com.bizplay.data.api.dto.AccountDto
import com.bizplay.data.api.dto.LoginRequestDto
import com.bizplay.data.api.dto.LoginResponseDto
import com.bizplay.data.api.dto.TenantDto
import com.bizplay.data.api.dto.TenantFlagsDto
import com.bizplay.data.api.dto.TenantParamsDto
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline demo backend. Validates credentials against a fixed table and returns a
 * [LoginResponseDto] with multiple department accounts so the institution selector
 * has something to render.
 *
 * In production, this is replaced by a [retrofit2.Retrofit]-built `IpppAuthApi`.
 */
@Singleton
internal class FakeAuthBackend @Inject constructor() {

    suspend fun login(request: LoginRequestDto): LoginResponseDto {
        delay(600L) // simulate a slow network leg
        val record = USERS[request.userId]
            ?: throw IllegalStateException("Unknown user '${request.userId}'.")
        check(record.password == request.encryptedPassword) {
            "Incorrect password for '${request.userId}'."
        }
        check(record.companyCode == request.companyCode) {
            "Incorrect company code for '${request.userId}'."
        }
        return record.response
    }

    suspend fun logout() {
        delay(150L)
    }

    private data class UserRecord(
        val password: String,
        val companyCode: String,
        val response: LoginResponseDto,
    )

    private companion object {
        private val USERS: Map<String, UserRecord> = mapOf(
            "demo" to UserRecord(
                password = "demo1234",
                companyCode = "BIZPLAY",
                response = LoginResponseDto(
                    accessToken = "tok_demo_${System.currentTimeMillis()}",
                    userId = "demo",
                    userDisplayName = "Demo User",
                    variantId = "kr",
                    tenant = TenantDto(
                        id = "default",
                        displayName = "Bizplay Default Tenant",
                        flags = TenantFlagsDto(),
                        params = TenantParamsDto(
                            supportPhone = "+82 2 1234 5678",
                            supportEmail = "help@bizplay.co.kr",
                        ),
                    ),
                    accounts = listOf(
                        AccountDto(
                            id = "INST_001",
                            displayName = "Headquarters · Seoul",
                            companyCode = "BPC-HQ",
                            divisionCode = "HQ-FIN",
                            divisionName = "Finance",
                        ),
                        AccountDto(
                            id = "INST_002",
                            displayName = "R&D · Pangyo",
                            companyCode = "BPC-RND",
                            divisionCode = "RND-ENG",
                            divisionName = "Engineering",
                        ),
                        AccountDto(
                            id = "INST_003",
                            displayName = "Sales · Busan",
                            companyCode = "BPC-SAL",
                            divisionCode = "SAL-KSE",
                            divisionName = "South-East Sales",
                        ),
                    ),
                    defaultAccountId = "INST_001",
                    requiresInstitutionSelection = true,
                ),
            ),
            "single" to UserRecord(
                password = "single1234",
                companyCode = "BIZPLAY",
                response = LoginResponseDto(
                    accessToken = "tok_single_${System.currentTimeMillis()}",
                    userId = "single",
                    userDisplayName = "Solo Account User",
                    variantId = "kr",
                    tenant = TenantDto(
                        id = "default",
                        displayName = "Bizplay Default Tenant",
                        flags = TenantFlagsDto(),
                        params = TenantParamsDto(),
                    ),
                    accounts = listOf(
                        AccountDto(
                            id = "INST_900",
                            displayName = "Single Branch",
                            companyCode = "BPC-SOLO",
                            divisionCode = "SOLO-ALL",
                            divisionName = "General",
                        ),
                    ),
                    defaultAccountId = "INST_900",
                    requiresInstitutionSelection = false,
                ),
            ),
        )
    }
}
