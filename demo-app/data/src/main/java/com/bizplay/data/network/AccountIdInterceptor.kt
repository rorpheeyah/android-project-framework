package com.bizplay.data.network

import com.bizplay.core.session.SessionHolder
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Stamps every authenticated request with the **currently active** department account.
 * Mirrors the existing Bizplay `USE_INTT_ID` + `COMPANY_CD` request fields.
 *
 * Reads [com.bizplay.core.session.Session.activeAccountId] at call time — so
 * `session.switchAccount(...)` affects the very next request, without any DI
 * rebuild or component swap. Pre-login calls (auth, MG) skip stamping because
 * [SessionHolder.currentOrNull] returns null.
 */
class AccountIdInterceptor @Inject constructor(
    private val sessionHolder: SessionHolder,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val session = sessionHolder.currentOrNull ?: return chain.proceed(chain.request())
        val account = session.activeAccount
        val accountId = session.activeAccountId.value
        val authed = chain.request().newBuilder()
            .header(HEADER_USE_INTT_ID, accountId.value)
            .header(HEADER_COMPANY_CD, account.companyCode)
            .header(HEADER_AUTHORIZATION, "Bearer ${session.user.accessToken}")
            .build()
        return chain.proceed(authed)
    }

    companion object {
        const val HEADER_USE_INTT_ID = "X-USE-INTT-ID"
        const val HEADER_COMPANY_CD = "X-COMPANY-CD"
        const val HEADER_AUTHORIZATION = "Authorization"
    }
}
