package com.compass.app.session

import com.compass.core.session.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the lifetime of the active session. In the docs this builds a custom
 * Hilt `LoggedInComponent`; for the demo we keep the same shape using a plain
 * holder, which is enough to demonstrate the boundary:
 *
 * - `build(session)` is the one-time event at login completion.
 * - `current()` is what providers in [com.compass.app.di.LoggedInBindingsModule]
 *   call to fetch the Session for ViewModel injection.
 * - `drop()` is the one-time event at logout.
 *
 * The `current` flow lets the navigation graph reactively show logged-in vs
 * logged-out destinations without an explicit event bus.
 */
@Singleton
class LoggedInComponentManager @Inject constructor() {

    private val _current = MutableStateFlow<Session?>(null)
    val current: StateFlow<Session?> = _current.asStateFlow()

    private val mutex = Mutex()

    suspend fun build(session: Session): Session = mutex.withLock {
        check(_current.value == null) { "LoggedInComponent already built — call drop() first" }
        _current.value = session
        session
    }

    fun require(): Session =
        checkNotNull(_current.value) { "LoggedInComponent not built — login first" }

    suspend fun drop() = mutex.withLock {
        _current.value = null
    }
}
