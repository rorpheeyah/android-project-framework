package com.aos.core.network

import java.util.concurrent.atomic.AtomicReference

/**
 * Holds the runtime-discovered API base URL. Set once after boot's MG fetch.
 * `:aos-core` does not know what the URL points to.
 */
class BaseUrlProvider {

    private val ref = AtomicReference<String?>(null)

    fun set(url: String) {
        ref.set(url)
    }

    fun get(): String =
        ref.get() ?: error("BaseUrlProvider not initialised — boot must call set() before any API call")
}
