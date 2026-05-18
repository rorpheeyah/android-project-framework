package com.bizplay.data.network

import com.bizplay.aoscore.network.BaseUrlProvider
import com.bizplay.core.runtime.RuntimeConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton holder for the live [RuntimeConfig] from MgGate. Implements
 * [BaseUrlProvider] so [com.bizplay.aoscore.network.BaseUrlInterceptor] can
 * rewrite Retrofit URLs without :aos-core knowing what `RuntimeConfig` is.
 *
 * The store starts empty; calling [mainBaseUrl] before [commit] is a programming
 * error and intentionally throws — there is no fallback URL table by design.
 */
@Singleton
class RuntimeConfigStore @Inject constructor() : BaseUrlProvider {

    private val _config = MutableStateFlow<RuntimeConfig?>(null)
    val config: StateFlow<RuntimeConfig?> = _config.asStateFlow()

    fun commit(config: RuntimeConfig) {
        _config.value = config
    }

    override fun mainBaseUrl(): String =
        _config.value?.urls?.main
            ?: error("RuntimeConfig not yet loaded — MgGate must complete before any API call")
}
