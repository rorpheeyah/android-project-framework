package com.bizplay.aoscore.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Rewrites every outgoing Retrofit call to use the live base URL from MgGate.
 * Retrofit needs *some* base URL at construction time; we feed it a sentinel
 * (`http://placeholder.invalid/`) and overwrite scheme + host + port here.
 *
 * Lives in :aos-core because the rewriting logic is product-agnostic.
 */
class BaseUrlInterceptor(
    private val provider: BaseUrlProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val live = provider.mainBaseUrl().toHttpUrl()
        val rewritten = request.url.newBuilder()
            .scheme(live.scheme)
            .host(live.host)
            .port(live.port)
            .build()
        return chain.proceed(request.newBuilder().url(rewritten).build())
    }
}
