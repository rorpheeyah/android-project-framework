package com.compass.app.session

import com.aos.core.storage.EncryptedPrefs
import com.compass.core.repository.AuthRepository
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tears the active session down.
 *
 * The framework's promise: after `logout()` returns, every `@LoggedInScoped`
 * instance is GC-eligible and every token-bearing cache is evicted. The
 * navigation pop happens in `AppNavigation` once it observes
 * `LoggedInComponentManager.current` go null.
 */
@Singleton
class LogoutHandler @Inject constructor(
    private val componentManager: LoggedInComponentManager,
    private val httpClient: OkHttpClient,
    private val encryptedPrefs: EncryptedPrefs,
    private val authRepository: AuthRepository,
) {

    suspend fun logout() {
        runCatching { authRepository.logout() }
        encryptedPrefs.clearAll()
        httpClient.cache?.evictAll()
        componentManager.drop()
    }
}
