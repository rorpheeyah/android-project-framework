package com.bizplay.aoscore.network

/**
 * Product-agnostic source of the **current** API base URL. The :data module's
 * RuntimeConfigStore implements this; an OkHttp interceptor reads it for every
 * outgoing request so that an MgGate refresh propagates immediately.
 */
interface BaseUrlProvider {
    fun mainBaseUrl(): String
}
