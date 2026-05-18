package com.bizplay.demo.di

import com.bizplay.core.session.Session
import com.bizplay.core.session.SessionHolder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SingletonComponent-scoped implementation of [SessionHolder]. Delegates to
 * [LoggedInComponentManager] every read, so logout/relogin transparently
 * rotate the underlying [Session].
 */
@Singleton
class LoggedInSessionHolder @Inject constructor(
    private val manager: LoggedInComponentManager,
) : SessionHolder {

    override val current: Session
        get() = manager.requireEntryPoint().session()

    override val currentOrNull: Session?
        get() = manager.entryPointOrNull()?.session()
}
