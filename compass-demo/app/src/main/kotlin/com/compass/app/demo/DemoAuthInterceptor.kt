package com.compass.app.demo

import com.squareup.moshi.Moshi
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Demo-only stand-in for the auth backend. Short-circuits `POST /v1/auth/login`
 * and returns a canned payload so the Boot → Login → Switch flow is runnable
 * without a server.
 *
 * Pick the variant by user id:
 *   - `kh.<anything>` (or any id starting with "kh") → variantId=kh, KH institutions
 *   - `vn.<anything>` (or any id starting with "vn") → variantId=vn, VN institutions
 *   - `<anything>.solo` → returns a single account (skips institution picker)
 *
 * In a non-demo build this interceptor would not be installed; the request
 * would go to the real MG-discovered API URL.
 */
@Singleton
class DemoAuthInterceptor @Inject constructor(
    private val moshi: Moshi,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        if (!path.endsWith("/v1/auth/login")) return chain.proceed(request)

        val rawBody = request.body?.let { body ->
            okio.Buffer().also { body.writeTo(it) }.readUtf8()
        }.orEmpty()

        val userId = USER_ID_REGEX.find(rawBody)?.groupValues?.get(1).orEmpty()
        val variantId = if (userId.startsWith("vn", ignoreCase = true)) "vn" else "kh"
        val singleAccount = userId.endsWith(".solo", ignoreCase = true)
        val displayName = userId.ifBlank { "Demo User" }

        val body = if (variantId == "vn") vnPayload(displayName, singleAccount) else khPayload(displayName, singleAccount)
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody(JSON))
            .build()
    }

    private fun khPayload(displayName: String, singleAccount: Boolean): String {
        val accounts = if (singleAccount) {
            listOf(account("acct-kh-personal", "Personal · Phnom Penh", "KH001", "Personal", "KHR"))
        } else {
            listOf(
                account("acct-kh-personal", "Personal · Phnom Penh", "KH001", "Personal", "KHR"),
                account("acct-kh-corp", "Compass Cambodia Ltd.", "KH017", "Corporate", "USD"),
                account("acct-kh-joint", "Joint · Family", "KH034", "Joint", "KHR"),
            )
        }
        return loginResponse(displayName, "kh", accounts)
    }

    private fun vnPayload(displayName: String, singleAccount: Boolean): String {
        val accounts = if (singleAccount) {
            listOf(account("acct-vn-personal", "Cá nhân · Hà Nội", "VN001", "Personal", "VND"))
        } else {
            listOf(
                account("acct-vn-personal", "Cá nhân · Hà Nội", "VN001", "Personal", "VND"),
                account("acct-vn-corp", "Compass Vietnam JSC", "VN023", "Corporate", "VND"),
            )
        }
        return loginResponse(displayName, "vn", accounts)
    }

    private fun account(id: String, displayName: String, code: String, type: String, currency: String): String =
        """{"accountId":"$id","displayName":"$displayName","institutionCode":"$code","accountType":"$type","currency":"$currency"}"""

    private fun loginResponse(displayName: String, variantId: String, accounts: List<String>): String = """
        {
          "userId": "${displayName.lowercase().filter { it.isLetterOrDigit() }}",
          "displayName": "$displayName",
          "accessToken": "demo-token-$variantId",
          "variantId": "$variantId",
          "accounts": [${accounts.joinToString(",")}]
        }
    """.trimIndent()

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val USER_ID_REGEX = """"userId"\s*:\s*"([^"]*)"""".toRegex()
    }
}
