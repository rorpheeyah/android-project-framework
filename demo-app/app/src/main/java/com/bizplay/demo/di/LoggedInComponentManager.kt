package com.bizplay.demo.di

import com.bizplay.core.session.Session
import com.bizplay.core.tenant.TenantContext
import com.bizplay.core.variant.VariantContext
import dagger.hilt.EntryPoints
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Owns the lifetime of [LoggedInComponent]. Building it after login wires every
 * `@LoggedInScoped` provider; dropping it on logout makes all of those bindings
 * eligible for garbage collection — no per-class purge required.
 *
 * This is the single mutation point for "we have a session" vs "we don't" — the
 * rest of the app reads through [com.bizplay.core.session.SessionHolder] /
 * [com.bizplay.demo.di.LoggedInSessionHolder].
 */
@Singleton
class LoggedInComponentManager @Inject constructor(
    private val builderProvider: Provider<LoggedInComponent.Builder>,
) {
    @Volatile private var component: LoggedInComponent? = null

    fun build(session: Session, variant: VariantContext, tenant: TenantContext) {
        component = builderProvider.get()
            .bindSession(session)
            .bindVariantContext(variant)
            .bindTenantContext(tenant)
            .build()
    }

    fun drop() {
        component = null
    }

    fun entryPointOrNull(): LoggedInEntryPoint? =
        component?.let { EntryPoints.get(it, LoggedInEntryPoint::class.java) }

    fun requireEntryPoint(): LoggedInEntryPoint =
        entryPointOrNull() ?: error("No active LoggedInComponent — login must complete first.")
}
