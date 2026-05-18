package com.bizplay.demo.di

import com.bizplay.core.scope.LoggedInScoped
import com.bizplay.core.session.Session
import com.bizplay.core.tenant.TenantContext
import com.bizplay.core.variant.VariantCapabilities
import com.bizplay.core.variant.VariantContext
import dagger.BindsInstance
import dagger.hilt.DefineComponent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Custom Hilt component tied to **session lifetime** rather than any Android
 * lifecycle. Built by [com.bizplay.demo.boot.BootCoordinatorImpl] from the login
 * response, dropped on logout.
 *
 * Per framework invariant #7: built once with the user's [VariantContext] /
 * [TenantContext] / [Session] and never swapped. Variant or tenant change in
 * production means logout-then-login. Institution change is lighter — see
 * [Session.switchAccount].
 */
@DefineComponent(parent = SingletonComponent::class)
@LoggedInScoped
interface LoggedInComponent {

    @DefineComponent.Builder
    interface Builder {
        fun bindSession(@BindsInstance session: Session): Builder
        fun bindVariantContext(@BindsInstance ctx: VariantContext): Builder
        fun bindTenantContext(@BindsInstance ctx: TenantContext): Builder
        fun build(): LoggedInComponent
    }
}

/**
 * Pulled by [com.bizplay.demo.di.LoggedInComponentManager] to read the bindings
 * out of the active [LoggedInComponent] for the ViewModel-side façade.
 * Repositories (Receipt, Approval, …) would expand this surface as features grow.
 */
@EntryPoint
@InstallIn(LoggedInComponent::class)
interface LoggedInEntryPoint {
    fun session(): Session
    fun variantContext(): VariantContext
    fun tenantContext(): TenantContext
    fun variantCapabilities(): VariantCapabilities
}
