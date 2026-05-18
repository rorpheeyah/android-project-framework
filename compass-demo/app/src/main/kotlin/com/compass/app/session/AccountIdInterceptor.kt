package com.compass.app.session

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stamps the active account id on every authenticated request.
 *
 * Reads `Session.activeAccountId.value` at call time — so switching the active
 * account is a single value flip with no DI rebuild and no client recreation.
 */
@Singleton
class AccountIdInterceptor @Inject constructor(
    private val componentManager: LoggedInComponentManager,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val session = componentManager.current.value
        val request = if (session == null) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("X-Account-Id", session.activeAccountId.value.value)
                .build()
        }
        return chain.proceed(request)
    }
}
