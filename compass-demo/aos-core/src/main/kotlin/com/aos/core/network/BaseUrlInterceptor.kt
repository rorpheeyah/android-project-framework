package com.aos.core.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Rewrites every outgoing request's host/scheme to the URL held by [BaseUrlProvider].
 * Retrofit can be configured against a placeholder; the real host is stamped at call time.
 */
class BaseUrlInterceptor(private val baseUrlProvider: BaseUrlProvider) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val target = baseUrlProvider.get().toHttpUrl()
        val rewritten = original.url.newBuilder()
            .scheme(target.scheme)
            .host(target.host)
            .port(target.port)
            .build()
        return chain.proceed(original.newBuilder().url(rewritten).build())
    }
}
