package com.bizplay.demo.di

import com.bizplay.core.session.Session
import com.bizplay.core.tenant.TenantContext
import com.bizplay.core.variant.VariantCapabilities
import com.bizplay.core.variant.VariantContext
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

/**
 * The bridge that lets `@HiltViewModel` consumers inject things bound inside
 * the custom [LoggedInComponent]. Each `@Provides` is a one-line façade that
 * delegates to the live LoggedInEntryPoint.
 *
 * Per framework doc 08, this is the "@Provides façade for ViewModel injection
 * (one per :core interface)" pattern. Adding a new post-login repository means
 * extending [LoggedInEntryPoint] and adding one provider here.
 */
@Module
@InstallIn(ViewModelComponent::class)
object LoggedInBindingsModule {

    @Provides
    fun session(manager: LoggedInComponentManager): Session =
        manager.requireEntryPoint().session()

    @Provides
    fun variantContext(manager: LoggedInComponentManager): VariantContext =
        manager.requireEntryPoint().variantContext()

    @Provides
    fun tenantContext(manager: LoggedInComponentManager): TenantContext =
        manager.requireEntryPoint().tenantContext()

    @Provides
    fun variantCapabilities(manager: LoggedInComponentManager): VariantCapabilities =
        manager.requireEntryPoint().variantCapabilities()
}
